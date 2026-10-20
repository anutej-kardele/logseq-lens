package com.anutej.logseqpocket.data.repo

import androidx.room.withTransaction
import com.anutej.logseqpocket.data.db.BlockEntity
import com.anutej.logseqpocket.data.db.CardRow
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.db.ReviewProgressEntity
import com.anutej.logseqpocket.data.db.ReviewSessionEntity
import com.anutej.logseqpocket.domain.cards.ReviewResult
import com.anutej.logseqpocket.domain.cards.StudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A flashcard category. Keys: [ALL], [OTHER], or `tag:<tagKey>`. */
data class Deck(
    val key: String,
    val title: String,
    val tagName: String?,
    val cardKeys: List<String>,
    val known: Int,
    val again: Int,
    val reviewed: Int,
) {
    val size: Int get() = cardKeys.size
    val unseen: Int get() = size - reviewed
}

data class DeckOverview(val decks: List<Deck>, val orphanProgress: Int)

class StudyRepository(private val db: PocketDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.studyDao()

    val overview: Flow<DeckOverview> = combine(
        dao.observeAllCards(),
        dao.observeCardCategories(),
        dao.observeCategories(),
        dao.observeProgress(),
        dao.observeOrphanCount(),
    ) { cards, cardCats, categories, progress, orphans ->
        DeckOverview(buildDecks(cards, cardCats.groupBy({ it.tagKey }, { it.cardKey }), categories, progress), orphans)
    }

    private fun buildDecks(
        cards: List<CardRow>,
        byTag: Map<String, List<String>>,
        categories: List<com.anutej.logseqpocket.data.db.CategoryCount>,
        progress: List<ReviewProgressEntity>,
    ): List<Deck> {
        val prog = progress.associateBy { it.cardKey }
        val order = cards.map { it.cardKey }
        val categorised = byTag.values.flatten().toSet()
        fun deck(key: String, title: String, tagName: String?, keys: List<String>): Deck {
            val ps = keys.mapNotNull { prog[it] }
            return Deck(
                key, title, tagName, keys,
                known = ps.count { it.lastResult == ReviewResult.Know.name },
                again = ps.count { it.lastResult == ReviewResult.Again.name },
                reviewed = ps.count { it.reviewCount > 0 },
            )
        }
        val result = ArrayList<Deck>()
        result += deck(ALL, "All flashcards", null, order)
        for (c in categories) {
            val keys = byTag[c.tagKey].orEmpty().toSet()
            result += deck(TAG_PREFIX + c.tagKey, c.deckTitle ?: c.name, c.name, order.filter { it in keys })
        }
        val other = order.filter { it !in categorised }
        if (other.isNotEmpty()) result += deck(OTHER, "Other flashcards", null, other)
        return result
    }

    suspend fun deck(key: String): Deck? = overview.first().decks.firstOrNull { it.key == key }

    suspend fun cards(): Map<String, CardRow> = dao.observeAllCards().first().associateBy { it.cardKey }

    suspend fun record(card: CardRow, result: ReviewResult, answerHash: String?) {
        db.withTransaction {
            val old = dao.progress(card.cardKey)
            dao.upsertProgress(
                ReviewProgressEntity(
                    cardKey = card.cardKey,
                    reviewCount = (old?.reviewCount ?: 0) + 1,
                    knowCount = (old?.knowCount ?: 0) + if (result == ReviewResult.Know) 1 else 0,
                    againCount = (old?.againCount ?: 0) + if (result == ReviewResult.Again) 1 else 0,
                    lastResult = result.name,
                    lastReviewedAt = clock(),
                    lastBlockUuid = card.blockUuid,
                    lastAnswerHash = answerHash ?: old?.lastAnswerHash,
                )
            )
        }
    }

    suspend fun progress(key: String): ReviewProgressEntity? = dao.progress(key)

    suspend fun loadSession(deckKey: String): StudySession? {
        val row = dao.session(deckKey) ?: return null
        return runCatching {
            StudySession(
                deckKey = row.deckKey,
                order = json.decodeFromString(keysSer, row.cardKeysJson),
                position = row.position,
                results = json.decodeFromString(resultsSer, row.resultsJson)
                    .mapNotNull { (k, v) -> runCatching { k to ReviewResult.valueOf(v) }.getOrNull() }.toMap(),
                shuffled = row.shuffled,
                againOnly = row.againOnly,
                startedAt = row.startedAt,
            )
        }.getOrNull()
    }

    suspend fun saveSession(s: StudySession) = dao.upsertSession(
        ReviewSessionEntity(
            deckKey = s.deckKey,
            cardKeysJson = json.encodeToString(keysSer, s.order),
            position = s.position,
            resultsJson = json.encodeToString(resultsSer, s.results.mapValues { it.value.name }),
            shuffled = s.shuffled,
            againOnly = s.againOnly,
            startedAt = s.startedAt,
            updatedAt = clock(),
        )
    )

    suspend fun clearSession(deckKey: String) = dao.deleteSession(deckKey)

    suspend fun deleteOrphans() = dao.deleteOrphans()

    suspend fun resetAllProgress() = db.withTransaction { dao.clearProgress(); dao.clearSessions() }

    companion object {
        const val ALL = "all"
        const val OTHER = "other"
        const val TAG_PREFIX = "tag:"
        private val json = Json { ignoreUnknownKeys = true }
        private val keysSer = ListSerializer(String.serializer())
        private val resultsSer = MapSerializer(String.serializer(), String.serializer())
    }
}

/** Front and back of one card, reconstructed from the outline. */
data class CardContent(val card: CardRow, val block: BlockEntity?, val answer: List<BlockEntity>)
