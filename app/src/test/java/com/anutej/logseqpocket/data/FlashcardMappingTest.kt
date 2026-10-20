package com.anutej.logseqpocket.data

import com.anutej.logseqpocket.Fixtures
import com.anutej.logseqpocket.Fixtures.block
import com.anutej.logseqpocket.data.importer.GraphMapper
import com.anutej.logseqpocket.domain.cards.CardIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardMappingTest {
    private val bundle by lazy { GraphMapper.map(Fixtures.dto()) }
    private fun cardFor(n: Int) = bundle.cards.single { it.blockUuid == block(n) }
    private fun categoriesOf(n: Int) = bundle.cardCategories.filter { it.cardKey == cardFor(n).cardKey }.map { it.tagKey }.toSet()

    @Test fun identifiesOnlyBlocksTaggedCard() {
        assertEquals(listOf(4, 9, 11, 13, 15).map(::block), bundle.cards.map { it.blockUuid })
        // #leetcode without #Card is not a card; deck (#Cards) blocks are not cards.
        assertFalse(bundle.cards.any { it.blockUuid == block(16) })
        assertFalse(bundle.cards.any { it.blockUuid == block(20) })
    }

    @Test fun classifiesByTagIntersection() {
        assertEquals(setOf(Fixtures.LEET), categoriesOf(4))
        assertEquals(setOf(Fixtures.ALGO), categoriesOf(9))
        assertEquals(setOf(Fixtures.LEET, Fixtures.ALGO), categoriesOf(11)) // belongs to both decks
        assertEquals(emptySet<String>(), categoriesOf(13)) // "Other"
    }

    @Test fun deckBlocksProvideCategoryTitles() {
        val decks = bundle.decks.associate { it.tagKey to it.title }
        assertEquals("LeetCode Deck", decks[Fixtures.LEET])
        assertEquals("Pattern Deck", decks[Fixtures.ALGO])
    }

    @Test fun answersComeFromTheChildSubtree() {
        assertEquals(4, cardFor(4).answerBlockCount) // two children + "Complexity" + its child
        assertEquals(1, cardFor(13).answerBlockCount)
        assertEquals(0, cardFor(15).answerBlockCount) // no answer: nothing is fabricated
    }

    @Test fun cardKeysSurviveBlockInsertionAboveTheCard() {
        // CLI UUIDs are positional: inserting a block shifts them. Keys must not change.
        val shifted = Fixtures.editPage(pageUuid = Fixtures.J1) { page ->
            val blocks = page.getValue("blocks").jsonArray
            val extra = Json.parseToJsonElement("""{"uuid":"${block(500)}","content":"inserted","order":"00000000"}""")
            page["blocks"] = JsonArray(listOf(extra) + blocks)
        }.replace(block(4), block(600)) // simulate the card getting a new synthetic UUID
        val after = GraphMapper.map(Fixtures.dto(shifted))
        val before = bundle.cards.map { it.cardKey }.toSet()
        assertEquals(before, after.cards.map { it.cardKey }.toSet())
    }

    @Test fun editedPromptGetsNewKeyButSameAnswerHash() {
        val edited = GraphMapper.map(Fixtures.dto(Fixtures.synthetic.replace("Two Sum - LC 1", "Two Sum - LC 1 (easy)")))
        val old = cardFor(4)
        val new = edited.cards.single { it.blockUuid == block(4) }
        assertNotEquals(old.cardKey, new.cardKey)
        assertEquals(old.answerHash, new.answerHash)
    }

    @Test fun identicalPromptsOnOnePageStayDistinct() {
        val id = CardIdentity()
        val a = id.keyFor("journal:20260105", "Same prompt")
        val b = id.keyFor("journal:20260105", "Same  prompt ")
        val c = CardIdentity().keyFor("journal:20260106", "Same prompt")
        assertNotEquals(a, b)
        assertTrue(b.endsWith("#2"))
        assertNotEquals(a, c)
    }

    @Test fun malformedCardDataDoesNotBreakImport() {
        // A card whose tag ref has no uuid/name and a card with null content.
        val text = Fixtures.editPage(pageUuid = Fixtures.J2) {
            it["blocks"] = Json.parseToJsonElement(
                """[{"uuid":"${block(700)}","content":null,"tags":[{},{"name":"Card"}]},
                    {"uuid":"${block(701)}","tags":[{"name":"card"},{"name":"leetcode"}],"children":[{"uuid":"${block(702)}"}]}]"""
            )
        }
        val b = GraphMapper.map(Fixtures.dto(text))
        val c700 = b.cards.single { it.blockUuid == block(700) }
        assertEquals(0, c700.answerBlockCount)
        val c701 = b.cards.single { it.blockUuid == block(701) }
        assertEquals(setOf(Fixtures.LEET), b.cardCategories.filter { it.cardKey == c701.cardKey }.map { it.tagKey }.toSet())
        assertEquals(0, c701.answerBlockCount) // child exists but is empty
    }
}
