package com.anutej.logseqpocket.domain.cards

import java.security.MessageDigest

/**
 * Stable flashcard keys that survive re-imports.
 *
 * CLI-derived block UUIDs encode the block's tree position, so inserting a block above a card
 * changes its UUID. Instead a card is keyed by its page plus its normalised prompt text. Two
 * identical prompts on the same page get `#2`, `#3`… suffixes in document order so distinct cards
 * are never merged. One instance must be used per import (it tracks duplicates).
 */
class CardIdentity {
    private val seen = HashMap<String, Int>()

    fun keyFor(pageKey: String, front: String): String {
        val base = sha256(pageKey + "\n" + normalize(front))
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        return if (n == 1) base else "$base#$n"
    }

    companion object {
        fun pageKey(journalDay: Int?, pageName: String): String =
            journalDay?.let { "journal:$it" } ?: ("page:" + pageName.trim().lowercase())

        fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

        /** Hash of an answer subtree as (relative depth, content) pairs in outline order. */
        fun answerHash(blocks: List<Pair<Int, String>>): String =
            sha256(blocks.joinToString("\n") { (d, c) -> "$d\u0000${normalize(c)}" })

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
