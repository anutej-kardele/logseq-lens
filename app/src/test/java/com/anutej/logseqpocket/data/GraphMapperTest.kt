package com.anutej.logseqpocket.data

import com.anutej.logseqpocket.Fixtures
import com.anutej.logseqpocket.Fixtures.block
import com.anutej.logseqpocket.data.importer.GraphMapper
import com.anutej.logseqpocket.data.json.ImportError
import com.anutej.logseqpocket.data.json.ImportException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GraphMapperTest {
    private val bundle by lazy { GraphMapper.map(Fixtures.dto()) }

    @Test fun countsMatchExport() {
        assertEquals(8, bundle.pages.size)
        assertEquals(30, bundle.blocks.size)
        assertTrue(bundle.warnings.isEmpty())
    }

    @Test fun recursiveHierarchyIsFlattenedInOutlineOrder() {
        val j1 = bundle.blocks.filter { it.pageUuid == Fixtures.J1 }.sortedBy { it.preorder }
        // Depth-first order: 09:00, its two children, then the Two Sum card and its subtree…
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).map(::block), j1.take(10).map { it.uuid })
        val complexity = j1.first { it.uuid == block(8) }
        assertEquals(2, complexity.depth)
        assertEquals(block(7), complexity.parentUuid)
        assertEquals(3, bundle.blocks.first { it.uuid == block(4) }.childCount)
        assertEquals(listOf(0, 1, 2), j1.filter { it.parentUuid == block(4) }.map { it.siblingIndex })
    }

    @Test fun deepNestingAcrossThreeLevels() {
        val alg = bundle.blocks.filter { it.pageUuid == Fixtures.ALG_NOTES }.sortedBy { it.preorder }
        assertEquals(listOf(0, 1, 2, 0, 0), alg.map { it.depth })
        assertNull(alg[0].parentUuid)
    }

    @Test fun siblingsAreSortedByOrderKey() {
        val j3 = bundle.blocks.filter { it.pageUuid == Fixtures.J3 }.sortedBy { it.preorder }
        assertEquals(listOf("first: prefix sums are great", "second", "string tag"), j3.map { it.content })
    }

    @Test fun contentIsPreservedExactly() {
        val code = bundle.blocks.first { it.uuid == block(6) }.content
        assertTrue(code.startsWith("```java\nint[] twoSum("))
        assertTrue(code.contains("    Map<Integer, Integer> seen = new HashMap<>();\n"))
        assertEquals("", bundle.blocks.first { it.uuid == block(19) }.content)
        assertEquals("<script>alert(1)</script> stays literal", bundle.blocks.first { it.uuid == block(54) }.content)
    }

    @Test fun journalDaysFromIntStringAndTitle() {
        val byUuid = bundle.pages.associateBy { it.uuid }
        assertEquals(20260105, byUuid.getValue(Fixtures.J1).journalDay)
        assertEquals(20260107, byUuid.getValue(Fixtures.J3).journalDay) // given as a string
        assertEquals(20260108, byUuid.getValue(Fixtures.J4).journalDay) // derived from the title
        assertNull(byUuid.getValue(Fixtures.ALG_NOTES).journalDay)
        assertEquals("journal", byUuid.getValue(Fixtures.J2).kind)
        assertEquals(0, byUuid.getValue(Fixtures.J2).blockCount)
    }

    @Test fun journalDayParsingRejectsImpossibleDates() {
        assertNull(GraphMapper.parseJournalDay(JsonPrimitive(20261340)))
        assertNull(GraphMapper.parseJournalDay(JsonPrimitive("soon")))
        assertEquals(20240229, GraphMapper.parseJournalDay(JsonPrimitive(20240229)))
        assertNull(GraphMapper.parseJournalDay(JsonPrimitive(20230229)))
    }

    @Test fun timestampsAreMillisSecondsOrIso() {
        assertEquals(1790018397264, GraphMapper.parseTimestamp(JsonPrimitive(1790018397264)))
        assertEquals(1700000000000, GraphMapper.parseTimestamp(JsonPrimitive(1700000000)))
        assertEquals(0L + 1_700_000_000_000, GraphMapper.parseTimestamp(JsonPrimitive("2023-11-14T22:13:20Z")))
        assertNull(GraphMapper.parseTimestamp(null))
        assertTrue(!bundle.hasTimestamps)
    }

    @Test fun tagsIncludeBuiltInsWithoutPagesAndKeepOriginalNames() {
        val tags = bundle.tags.associateBy { it.name }
        assertEquals(setOf("algo-pattern", "leetcode", "habit", "Card", "Cards"), tags.keys)
        assertNull(tags.getValue("Card").pageUuid)
        assertEquals(Fixtures.LEET, tags.getValue("leetcode").pageUuid)
        // Bare-string tag "habit" merges with the habit class instead of duplicating it.
        assertEquals(2, tags.getValue("habit").blockCount)
        assertEquals(1, tags.getValue("algo-pattern").pageCount)
    }

    @Test fun inlineReferencesAreExtractedAndResolved() {
        val refs = bundle.refs.groupBy { it.blockUuid }
        val b3 = refs.getValue(block(3))
        assertEquals(Fixtures.ALG_NOTES, b3.first { it.target == "Algorithms Notes" }.targetPageUuid)
        assertNull(b3.first { it.target == "Missing Page" }.targetPageUuid)
        assertEquals(Fixtures.HABIT, refs.getValue(block(18)).single().targetPageUuid)
        val blockRef = refs.getValue(block(52)).single()
        assertEquals("block", blockRef.kind)
        assertEquals(Fixtures.ALG_NOTES, blockRef.targetPageUuid)
    }

    @Test fun unsupportedSchemaVersionIsRejected() {
        val v2 = Fixtures.edit { it["schemaVersion"] = JsonPrimitive(2) }
        val e = expect<ImportError.UnsupportedSchema> { GraphMapper.map(Fixtures.dto(v2)) }
        assertEquals(2, (e as ImportError.UnsupportedSchema).found)
        assertTrue(e.userMessage.contains("newer"))
    }

    @Test fun missingRequiredDataIsRejected() {
        expect<ImportError.Invalid> { GraphMapper.map(Fixtures.dto(Fixtures.edit { it.remove("schemaVersion") })) }
        expect<ImportError.Invalid> { GraphMapper.map(Fixtures.dto(Fixtures.edit { it.remove("graph") })) }
        expect<ImportError.Invalid> { GraphMapper.map(Fixtures.dto(Fixtures.edit { it["pages"] = JsonArray(emptyList()) })) }
        expect<ImportError.Invalid> {
            GraphMapper.map(Fixtures.dto(Fixtures.editPage(pageUuid = Fixtures.J4) { it.remove("uuid") }))
        }
    }

    @Test fun duplicateBlockUuidsAreRejected() {
        val dup = Fixtures.synthetic.replace(block(17), block(14))
        expect<ImportError.Invalid> { GraphMapper.map(Fixtures.dto(dup)) }
    }

    @Test fun declaredCountMismatchIsOnlyAWarning() {
        val b = GraphMapper.map(Fixtures.dto(Fixtures.edit { it["blockCount"] = JsonPrimitive(999) }))
        assertEquals(1, b.warnings.size)
        assertEquals(30, b.blocks.size)
    }

    @Test fun excessiveDepthIsRejected() {
        val text = Fixtures.editPage(pageUuid = Fixtures.J2) {
            it["blocks"] = JsonArray(listOf(Json.parseToJsonElement(nested(GraphMapper.MAX_DEPTH + 5))))
        }
        expect<ImportError.Invalid> { GraphMapper.map(Fixtures.dto(text)) }
    }

    @Test fun pathologicalNestingFailsCleanlyInTheParser() {
        val raw = Fixtures.synthetic.replaceFirst("\"blocks\": []", "\"blocks\": [" + nested(400) + "]")
        expect<ImportError.MalformedJson> { Fixtures.dto(raw) }
    }

    private fun nested(levels: Int): String {
        var node = """{"uuid":"${block(999)}","content":"leaf"}"""
        repeat(levels) { i ->
            node = """{"uuid":"${"50000000-0000-4000-8000-%012x".format(i)}","content":"n$i","children":[$node]}"""
        }
        return node
    }

    @Test fun pageWithoutTitleFallsBackToName() {
        val text = Fixtures.editPage(pageUuid = Fixtures.ALG_NOTES) { it.remove("title") }
        val p = GraphMapper.map(Fixtures.dto(text)).pages.first { it.uuid == Fixtures.ALG_NOTES }
        assertEquals("Algorithms Notes", p.title)
    }

    private inline fun <reified E : ImportError> expect(block: () -> Unit): ImportError {
        try {
            block(); fail("expected ${E::class.simpleName}")
        } catch (e: ImportException) {
            assertTrue("got ${e.error::class.simpleName}: ${e.error.userMessage}", e.error is E)
            return e.error
        }
        error("unreachable")
    }
}
