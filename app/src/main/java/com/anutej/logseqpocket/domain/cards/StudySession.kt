package com.anutej.logseqpocket.domain.cards

import kotlin.random.Random

enum class ReviewResult { Know, Again }

/**
 * Pure, persistable state of one review pass over a deck. Card content is looked up separately
 * by key, so a session survives re-imports (keys that disappeared are dropped on restore).
 */
data class StudySession(
    val deckKey: String,
    val order: List<String>,
    val position: Int = 0,
    val results: Map<String, ReviewResult> = emptyMap(),
    val shuffled: Boolean = false,
    val againOnly: Boolean = false,
    val startedAt: Long = 0L,
) {
    val total: Int get() = order.size
    val current: String? get() = order.getOrNull(position)
    val answered: Int get() = order.count { it in results }
    val knownCount: Int get() = order.count { results[it] == ReviewResult.Know }
    val againCount: Int get() = order.count { results[it] == ReviewResult.Again }
    val isFinished: Boolean get() = order.isNotEmpty() && position >= order.size
    val againKeys: List<String> get() = order.filter { results[it] == ReviewResult.Again }

    fun next(): StudySession = copy(position = (position + 1).coerceAtMost(order.size))
    fun previous(): StudySession = copy(position = (position - 1).coerceAtLeast(0))

    /** Records a result for the current card and moves on. */
    fun mark(result: ReviewResult): StudySession {
        val key = current ?: return this
        return copy(results = results + (key to result), position = position + 1)
    }

    fun restart(): StudySession = copy(position = 0, results = emptyMap())

    /** Keeps only cards still present after a re-import. */
    fun retain(existing: Set<String>): StudySession {
        val kept = order.filter { it in existing }
        if (kept.size == order.size) return this
        val before = order.take(position).count { it in existing }
        return copy(order = kept, position = before, results = results.filterKeys { it in existing })
    }

    companion object {
        fun start(
            deckKey: String,
            cardKeys: List<String>,
            shuffle: Boolean,
            againOnly: Boolean = false,
            now: Long = System.currentTimeMillis(),
            random: Random = Random.Default,
        ): StudySession = StudySession(
            deckKey = deckKey,
            order = if (shuffle) cardKeys.shuffled(random) else cardKeys,
            shuffled = shuffle,
            againOnly = againOnly,
            startedAt = now,
        )
    }
}
