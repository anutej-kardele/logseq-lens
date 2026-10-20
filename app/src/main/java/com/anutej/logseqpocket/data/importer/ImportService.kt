package com.anutej.logseqpocket.data.importer

import android.util.Log
import androidx.room.withTransaction
import com.anutej.logseqpocket.data.db.CardEntity
import com.anutej.logseqpocket.data.db.DatasetMetaEntity
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.db.ReviewProgressEntity
import com.anutej.logseqpocket.data.json.ExportParser
import com.anutej.logseqpocket.data.json.ImportError
import com.anutej.logseqpocket.data.json.ImportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream

enum class ImportOrigin { File, GitHub }

class ImportSource(
    val origin: ImportOrigin,
    val label: String?,
    val etag: String? = null,
    val open: () -> InputStream,
)

data class ImportStats(val pages: Int, val blocks: Int, val tags: Int, val cards: Int, val progressReattached: Int)

sealed interface ImportOutcome {
    data class Imported(val stats: ImportStats, val warnings: List<String>) : ImportOutcome
    /** The file's content hash matches the current dataset; nothing was rebuilt. */
    data object Unchanged : ImportOutcome
    data class Failed(val error: ImportError) : ImportOutcome
}

/**
 * Parses, validates and stores an export. The existing dataset is replaced in a single Room
 * transaction only after the new data is fully parsed and mapped, so any failure leaves the last
 * working notes untouched. Review progress lives in separate tables and is never cleared here.
 */
class ImportService(
    private val db: PocketDatabase,
    private val parser: ExportParser = ExportParser(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun import(source: ImportSource, force: Boolean = false): ImportOutcome = withContext(Dispatchers.IO) {
        try {
            val parsed = source.open().use { parser.parse(it) }
            val current = db.notesDao().meta()
            if (!force && current != null && current.contentSha256 == parsed.sha256) {
                db.importDao().touchMeta(clock(), source.etag)
                return@withContext ImportOutcome.Unchanged
            }
            val bundle = GraphMapper.map(parsed.dto)
            var reattached = 0
            try {
                db.withTransaction {
                    val dao = db.importDao()
                    dao.clearContent()
                    dao.insertPages(bundle.pages)
                    dao.insertBlocks(bundle.blocks)
                    dao.insertTags(bundle.tags)
                    dao.insertBlockTags(bundle.blockTags)
                    dao.insertPageTags(bundle.pageTags)
                    dao.insertRefs(bundle.refs)
                    dao.insertCards(bundle.cards)
                    dao.insertCardCategories(bundle.cardCategories)
                    dao.insertDecks(bundle.decks)
                    reattached = reconcileProgress(bundle.cards)
                    dao.upsertMeta(
                        DatasetMetaEntity(
                            graph = bundle.graph,
                            schemaVersion = bundle.schemaVersion,
                            source = bundle.source,
                            logseqSchema = bundle.logseqSchema,
                            pageCount = bundle.pages.size,
                            blockCount = bundle.blocks.size,
                            declaredPageCount = bundle.declaredPageCount,
                            declaredBlockCount = bundle.declaredBlockCount,
                            excludedSystemViewBlocks = bundle.excludedSystemViewBlocks,
                            importedAt = clock(),
                            origin = source.origin.name,
                            originLabel = source.label,
                            contentSha256 = parsed.sha256,
                            byteCount = parsed.byteCount,
                            etag = source.etag,
                            warnings = Json.encodeToString(bundle.warnings),
                            hasTimestamps = bundle.hasTimestamps,
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Import transaction rolled back: ${e.javaClass.simpleName}")
                return@withContext ImportOutcome.Failed(ImportError.Database(e.javaClass.simpleName))
            }
            Log.i(TAG, "Imported ${bundle.pages.size} pages, ${bundle.blocks.size} blocks, ${bundle.cards.size} cards")
            ImportOutcome.Imported(
                ImportStats(bundle.pages.size, bundle.blocks.size, bundle.tags.size, bundle.cards.size, reattached),
                bundle.warnings,
            )
        } catch (e: ImportException) {
            Log.w(TAG, "Import rejected: ${e.error.javaClass.simpleName}")
            ImportOutcome.Failed(e.error)
        } catch (e: IOException) {
            ImportOutcome.Failed(ImportError.Unreadable(e.message ?: e.javaClass.simpleName))
        } catch (e: SecurityException) {
            ImportOutcome.Failed(ImportError.Unreadable("permission to read the file was denied"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.w(TAG, "Import failed unexpectedly: ${e.javaClass.simpleName}")
            ImportOutcome.Failed(ImportError.Invalid("unexpected ${e.javaClass.simpleName}"))
        }
    }

    /**
     * Refreshes identity hints for cards that kept their key, and re-attaches progress whose key
     * disappeared to a new card with the same synthetic block UUID *and* identical answer (the
     * prompt text was edited in place). Unmatched progress is kept as an orphan, never deleted.
     */
    private suspend fun reconcileProgress(cards: List<CardEntity>): Int {
        val dao = db.studyDao()
        val existing = dao.allProgress()
        if (existing.isEmpty()) return 0
        val byKey = existing.associateBy { it.cardKey }
        val newKeys = cards.mapTo(HashSet()) { it.cardKey }
        val orphansByHint = existing
            .filter { it.cardKey !in newKeys && it.lastBlockUuid != null && it.lastAnswerHash != null }
            .associateBy { it.lastBlockUuid + "\u0000" + it.lastAnswerHash }
            .toMutableMap()
        val updates = ArrayList<ReviewProgressEntity>()
        val removed = ArrayList<String>()
        var reattached = 0
        for (card in cards) {
            val own = byKey[card.cardKey]
            if (own != null) {
                updates += own.copy(lastBlockUuid = card.blockUuid, lastAnswerHash = card.answerHash)
                continue
            }
            if (card.answerBlockCount == 0) continue
            val orphan = orphansByHint.remove(card.blockUuid + "\u0000" + card.answerHash) ?: continue
            removed += orphan.cardKey
            updates += orphan.copy(cardKey = card.cardKey, lastBlockUuid = card.blockUuid, lastAnswerHash = card.answerHash)
            reattached++
        }
        if (removed.isNotEmpty()) dao.deleteProgress(removed)
        if (updates.isNotEmpty()) dao.upsertProgress(updates)
        return reattached
    }

    private companion object {
        const val TAG = "ImportService"
    }
}
