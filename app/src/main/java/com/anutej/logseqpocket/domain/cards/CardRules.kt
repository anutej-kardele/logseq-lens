package com.anutej.logseqpocket.domain.cards

/**
 * How Logseq marks flashcards: a block tagged with the built-in `Card` class is a card (its
 * content is the prompt, its child blocks the answer). Blocks tagged `Cards` are deck/query
 * blocks, not cards.
 */
object CardRules {
    const val CARD_TAG = "card"
    const val DECK_TAG = "cards"

    fun isCardTag(name: String): Boolean = name.trim().equals(CARD_TAG, ignoreCase = true)
    fun isDeckTag(name: String): Boolean = name.trim().equals(DECK_TAG, ignoreCase = true)
}
