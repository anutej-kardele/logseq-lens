package com.anutej.logseqpocket.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class PageSummary(
    @Embedded val page: PageEntity,
    val preview: String?,
)

data class BlockTagRow(val blockUuid: String, val tagKey: String, val name: String, val pageUuid: String?, val position: Int)

/** A block shown outside its page (tag listings, backlinks, search, flashcards). */
data class BlockHit(
    val blockUuid: String,
    val pageUuid: String,
    val pageTitle: String,
    val pageKind: String,
    val journalDay: Int?,
    val content: String,
    val depth: Int,
    val preorder: Int,
)

@Dao
interface ImportDao {
    @Query("DELETE FROM dataset_meta") suspend fun clearMeta()
    @Query("DELETE FROM pages") suspend fun clearPages()
    @Query("DELETE FROM blocks") suspend fun clearBlocks()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM block_tags") suspend fun clearBlockTags()
    @Query("DELETE FROM page_tags") suspend fun clearPageTags()
    @Query("DELETE FROM block_refs") suspend fun clearRefs()
    @Query("DELETE FROM cards") suspend fun clearCards()
    @Query("DELETE FROM card_categories") suspend fun clearCardCategories()
    @Query("DELETE FROM decks") suspend fun clearDecks()

    @Insert suspend fun insertPages(rows: List<PageEntity>)
    @Insert suspend fun insertBlocks(rows: List<BlockEntity>)
    @Insert suspend fun insertTags(rows: List<TagEntity>)
    @Insert suspend fun insertBlockTags(rows: List<BlockTagEntity>)
    @Insert suspend fun insertPageTags(rows: List<PageTagEntity>)
    @Insert suspend fun insertRefs(rows: List<BlockRefEntity>)
    @Insert suspend fun insertCards(rows: List<CardEntity>)
    @Insert suspend fun insertCardCategories(rows: List<CardCategoryEntity>)
    @Insert suspend fun insertDecks(rows: List<DeckEntity>)
    @Upsert suspend fun upsertMeta(meta: DatasetMetaEntity)

    @Query("UPDATE dataset_meta SET importedAt = :checkedAt, etag = COALESCE(:etag, etag)")
    suspend fun touchMeta(checkedAt: Long, etag: String?)

    suspend fun clearContent() {
        clearMeta(); clearRefs(); clearBlockTags(); clearPageTags(); clearCardCategories(); clearCards()
        clearDecks(); clearTags(); clearBlocks(); clearPages()
    }
}

@Dao
interface NotesDao {
    @Query("SELECT * FROM dataset_meta WHERE id = 0")
    fun observeMeta(): Flow<DatasetMetaEntity?>

    @Query("SELECT * FROM dataset_meta WHERE id = 0")
    suspend fun meta(): DatasetMetaEntity?

    @Query(
        """SELECT p.*, (SELECT b.content FROM blocks b WHERE b.pageUuid = p.uuid AND TRIM(b.content) != ''
           ORDER BY b.preorder LIMIT 1) AS preview
           FROM pages p WHERE p.kind = 'journal' ORDER BY p.journalDay DESC, p.titleLower DESC"""
    )
    fun observeJournals(): Flow<List<PageSummary>>

    @Query(
        """SELECT p.*, (SELECT b.content FROM blocks b WHERE b.pageUuid = p.uuid AND TRIM(b.content) != ''
           ORDER BY b.preorder LIMIT 1) AS preview
           FROM pages p WHERE p.kind = 'journal' AND p.nonEmptyBlockCount > 0
           ORDER BY p.journalDay DESC LIMIT :limit"""
    )
    fun observeRecentJournals(limit: Int): Flow<List<PageSummary>>

    @Query(
        """SELECT p.*, NULL AS preview FROM pages p WHERE p.updatedAt IS NOT NULL
           ORDER BY p.updatedAt DESC LIMIT :limit"""
    )
    fun observeRecentlyUpdated(limit: Int): Flow<List<PageSummary>>

    @Query(
        """SELECT p.*, (SELECT b.content FROM blocks b WHERE b.pageUuid = p.uuid AND TRIM(b.content) != ''
           ORDER BY b.preorder LIMIT 1) AS preview
           FROM pages p ORDER BY p.titleLower"""
    )
    fun observePages(): Flow<List<PageSummary>>

    @Query("SELECT * FROM pages WHERE uuid = :uuid")
    fun observePage(uuid: String): Flow<PageEntity?>

    @Query("SELECT * FROM blocks WHERE pageUuid = :pageUuid ORDER BY preorder")
    fun observeBlocks(pageUuid: String): Flow<List<BlockEntity>>

    @Query(
        """SELECT bt.blockUuid, bt.tagKey, t.name, t.pageUuid, bt.position FROM block_tags bt
           JOIN tags t ON t.tagKey = bt.tagKey JOIN blocks b ON b.uuid = bt.blockUuid
           WHERE b.pageUuid = :pageUuid ORDER BY bt.position"""
    )
    fun observeBlockTagsForPage(pageUuid: String): Flow<List<BlockTagRow>>

    @Query("SELECT t.* FROM page_tags pt JOIN tags t ON t.tagKey = pt.tagKey WHERE pt.pageUuid = :pageUuid ORDER BY t.nameLower")
    fun observePageTags(pageUuid: String): Flow<List<TagEntity>>

    @Query(
        """SELECT b.uuid AS blockUuid, b.pageUuid, p.title AS pageTitle, p.kind AS pageKind, p.journalDay,
           b.content, b.depth, b.preorder
           FROM block_refs r JOIN blocks b ON b.uuid = r.blockUuid JOIN pages p ON p.uuid = b.pageUuid
           WHERE r.targetPageUuid = :pageUuid AND b.pageUuid != :pageUuid
           GROUP BY b.uuid ORDER BY p.journalDay DESC, p.titleLower, b.preorder"""
    )
    fun observeBacklinks(pageUuid: String): Flow<List<BlockHit>>

    @Query("SELECT uuid, title, kind FROM pages")
    suspend fun pageIndex(): List<PageIndexRow>

    @Query("SELECT * FROM tags")
    suspend fun allTags(): List<TagEntity>

    @Query("SELECT * FROM blocks WHERE uuid IN (:uuids)")
    suspend fun blocksByUuid(uuids: List<String>): List<BlockEntity>

    @Query(
        """SELECT bt.blockUuid, bt.tagKey, t.name, t.pageUuid, bt.position FROM block_tags bt
           JOIN tags t ON t.tagKey = bt.tagKey WHERE bt.blockUuid IN (:uuids) ORDER BY bt.position"""
    )
    suspend fun tagsForBlocks(uuids: List<String>): List<BlockTagRow>

    @Query("SELECT pageUuid FROM blocks WHERE uuid = :uuid")
    suspend fun pageOfBlock(uuid: String): String?

    // ---- Tags ----
    @Query("SELECT * FROM tags ORDER BY nameLower")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE tagKey = :tagKey")
    fun observeTag(tagKey: String): Flow<TagEntity?>

    @Query(
        """SELECT b.uuid AS blockUuid, b.pageUuid, p.title AS pageTitle, p.kind AS pageKind, p.journalDay,
           b.content, b.depth, b.preorder
           FROM block_tags bt JOIN blocks b ON b.uuid = bt.blockUuid JOIN pages p ON p.uuid = b.pageUuid
           WHERE bt.tagKey = :tagKey ORDER BY p.journalDay DESC, p.titleLower, b.preorder"""
    )
    fun observeBlocksForTag(tagKey: String): Flow<List<BlockHit>>

    @Query(
        """SELECT p.*, NULL AS preview FROM page_tags pt JOIN pages p ON p.uuid = pt.pageUuid
           WHERE pt.tagKey = :tagKey ORDER BY p.titleLower"""
    )
    fun observePagesForTag(tagKey: String): Flow<List<PageSummary>>

    /** Blocks after [preorder] on a page; callers take while depth is greater to get a subtree. */
    @Query("SELECT * FROM blocks WHERE pageUuid = :pageUuid AND preorder > :preorder ORDER BY preorder")
    suspend fun blocksAfter(pageUuid: String, preorder: Int): List<BlockEntity>

    @Query("SELECT COUNT(*) FROM pages") suspend fun pageCount(): Int
    @Query("SELECT COUNT(*) FROM blocks") suspend fun blockCount(): Int
}

data class PageIndexRow(val uuid: String, val title: String, val kind: String)

data class SearchRow(
    val blockUuid: String,
    val pageUuid: String,
    val pageTitle: String,
    val pageKind: String,
    val journalDay: Int?,
    val content: String,
)

@Dao
interface SearchDao {
    @Query(
        """SELECT b.uuid AS blockUuid, b.pageUuid, p.title AS pageTitle, p.kind AS pageKind, p.journalDay, b.content
           FROM blocks_fts JOIN blocks b ON b.rowid = blocks_fts.rowid JOIN pages p ON p.uuid = b.pageUuid
           WHERE blocks_fts MATCH :match
           ORDER BY p.journalDay DESC, p.titleLower, b.preorder LIMIT :limit"""
    )
    suspend fun searchBlocksFts(match: String, limit: Int): List<SearchRow>

    /** Substring fallback for queries FTS can't tokenize (symbols like `<<` or `dp[i]`). */
    @Query(
        """SELECT b.uuid AS blockUuid, b.pageUuid, p.title AS pageTitle, p.kind AS pageKind, p.journalDay, b.content
           FROM blocks b JOIN pages p ON p.uuid = b.pageUuid
           WHERE b.content LIKE '%' || :needle || '%' ESCAPE '\'
           ORDER BY p.journalDay DESC, p.titleLower, b.preorder LIMIT :limit"""
    )
    suspend fun searchBlocksLike(needle: String, limit: Int): List<SearchRow>

    @Query(
        """SELECT p.*, NULL AS preview FROM pages p
           WHERE p.titleLower LIKE '%' || :needle || '%' ESCAPE '\'
           ORDER BY CASE WHEN p.titleLower LIKE :needle || '%' ESCAPE '\' THEN 0 ELSE 1 END, p.titleLower LIMIT :limit"""
    )
    suspend fun searchPages(needle: String, limit: Int): List<PageSummary>
}

data class CardRow(
    val cardKey: String,
    val blockUuid: String,
    val pageUuid: String,
    val pageTitle: String,
    val pageKind: String,
    val journalDay: Int?,
    val content: String,
    val propertiesJson: String,
    val preorder: Int,
    val depth: Int,
    val childCount: Int,
    val answerBlockCount: Int,
    val answerHash: String,
    val sortIndex: Int,
)

data class CategoryCount(val tagKey: String, val name: String, val deckTitle: String?, val cardCount: Int)

data class ProgressSummary(val reviewed: Int, val known: Int, val again: Int)

@Dao
interface StudyDao {
    @Query(
        """SELECT c.cardKey, c.blockUuid, c.pageUuid, p.title AS pageTitle, p.kind AS pageKind, p.journalDay,
           b.content, b.propertiesJson, b.preorder, b.depth, b.childCount, c.answerBlockCount, c.answerHash, c.sortIndex
           FROM cards c JOIN blocks b ON b.uuid = c.blockUuid JOIN pages p ON p.uuid = c.pageUuid
           ORDER BY c.sortIndex"""
    )
    fun observeAllCards(): Flow<List<CardRow>>

    @Query("SELECT cardKey, tagKey FROM card_categories")
    fun observeCardCategories(): Flow<List<CardCategoryEntity>>

    @Query(
        """SELECT t.tagKey, t.name, d.title AS deckTitle, COUNT(cc.cardKey) AS cardCount
           FROM card_categories cc JOIN tags t ON t.tagKey = cc.tagKey LEFT JOIN decks d ON d.tagKey = t.tagKey
           GROUP BY t.tagKey ORDER BY COUNT(cc.cardKey) DESC, t.nameLower"""
    )
    fun observeCategories(): Flow<List<CategoryCount>>

    @Query("SELECT * FROM review_progress")
    fun observeProgress(): Flow<List<ReviewProgressEntity>>

    @Query("SELECT * FROM review_progress")
    suspend fun allProgress(): List<ReviewProgressEntity>

    @Query("SELECT * FROM review_progress WHERE cardKey = :key")
    suspend fun progress(key: String): ReviewProgressEntity?

    @Upsert suspend fun upsertProgress(row: ReviewProgressEntity)
    @Upsert suspend fun upsertProgress(rows: List<ReviewProgressEntity>)

    @Query("DELETE FROM review_progress WHERE cardKey IN (:keys)")
    suspend fun deleteProgress(keys: List<String>)

    @Query("DELETE FROM review_progress")
    suspend fun clearProgress()

    @Query("SELECT COUNT(*) FROM review_progress WHERE cardKey NOT IN (SELECT cardKey FROM cards)")
    fun observeOrphanCount(): Flow<Int>

    @Query("DELETE FROM review_progress WHERE cardKey NOT IN (SELECT cardKey FROM cards)")
    suspend fun deleteOrphans()

    @Query("SELECT * FROM review_sessions WHERE deckKey = :deckKey")
    suspend fun session(deckKey: String): ReviewSessionEntity?

    @Upsert suspend fun upsertSession(row: ReviewSessionEntity)

    @Query("DELETE FROM review_sessions WHERE deckKey = :deckKey")
    suspend fun deleteSession(deckKey: String)

    @Query("DELETE FROM review_sessions")
    suspend fun clearSessions()
}
