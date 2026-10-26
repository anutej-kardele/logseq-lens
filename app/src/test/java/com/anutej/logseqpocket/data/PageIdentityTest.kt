package com.anutej.logseqpocket.data

import com.anutej.logseqpocket.Fixtures
import com.anutej.logseqpocket.data.importer.GraphMapper
import com.anutej.logseqpocket.data.json.ImportError
import com.anutej.logseqpocket.data.json.ImportException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Page identity rules (regression for "duplicate page uuid" after the UUID-only page converter change). */
class PageIdentityTest {
    private val untitled = "70000000-0000-4000-8000-0000000000aa"

    private fun withExtraPages(vararg pages: String): String = Fixtures.edit { root ->
        val extra = pages.map { Json.parseToJsonElement(it) }
        root["pages"] = JsonArray(root.getValue("pages").jsonArray + extra)
        root.remove("pageCount"); root.remove("blockCount")
    }

    private fun page(uuid: String, kind: String, title: String?, blocks: String = "[]"): String =
        """{"uuid":"$uuid","kind":"$kind",${title?.let { "\"title\":\"$it\",\"name\":\"${it.lowercase()}\"," } ?: ""}"blocks":$blocks}"""

    private fun rejected(text: String): ImportError {
        try {
            GraphMapper.map(Fixtures.dto(text)); fail("expected rejection")
        } catch (e: ImportException) {
            return e.error
        }
        error("unreachable")
    }

    // Test A — UUID-only page: valid UUID, no title, one nested block → imported, content preserved.
    @Test fun uuidOnlyPageIsImportedWithItsBlocks() {
        val blocks = """[{"uuid":"71000000-0000-4000-8000-000000000001","content":"parent text","order":"00000000",
            "children":[{"uuid":"71000000-0000-4000-8000-000000000002","content":"nested text","order":"00000000"}]}]"""
        val b = GraphMapper.map(Fixtures.dto(withExtraPages(page(untitled, "page", null, blocks))))
        val p = b.pages.single { it.uuid == untitled }
        assertEquals("Untitled page (70000000)", p.title)
        assertEquals(listOf("parent text", "nested text"), b.blocks.filter { it.pageUuid == untitled }.sortedBy { it.preorder }.map { it.content })
        assertEquals(1, b.blocks.single { it.content == "nested text" }.depth)
        assertTrue(b.warnings.any { it.contains("no title") })
    }

    // Test B — distinct pages with different UUIDs (even with the same title) → both imported.
    @Test fun distinctUuidsAreDistinctPages() {
        val b = GraphMapper.map(Fixtures.dto(withExtraPages(
            page("72000000-0000-4000-8000-000000000001", "page", "Reading list"),
            page("72000000-0000-4000-8000-000000000002", "page", "Reading list"),
        )))
        assertEquals(2, b.pages.count { it.title == "Reading list" })
        assertEquals(10, b.pages.size)
    }

    // Test C — the same UUID on two page records → rejected with an actionable message.
    @Test fun genuinelyDuplicatedPageIdentityIsRejected() {
        val err = rejected(withExtraPages(page(Fixtures.ALG_NOTES, "page", "Algorithms Notes copy")))
        assertTrue(err is ImportError.Invalid)
        assertTrue(err.userMessage, err.userMessage.contains(Fixtures.ALG_NOTES))
        assertTrue(err.userMessage, err.userMessage.contains("#8 (page) and #9 (page)"))
    }

    // Test D — class/tag vs regular page identity.
    @Test fun tagAndRegularPageWithDifferentUuidsCoexist() {
        // A regular page that happens to be titled like the tag keeps its own identity; the tag stays a tag.
        val b = GraphMapper.map(Fixtures.dto(withExtraPages(page("73000000-0000-4000-8000-000000000001", "page", "algo-pattern"))))
        assertEquals(setOf("tag", "page"), b.pages.filter { it.titleLower == "algo-pattern" }.map { it.kind }.toSet())
        assertEquals(Fixtures.ALGO, b.tags.single { it.name == "algo-pattern" }.tagKey)
    }

    @Test fun classContentEmittedAsSecondPageWithTheClassUuidIsRejected() {
        // Exactly the broken export: the class "algo-pattern" (tag) plus an "Untitled page" record reusing its UUID.
        val blocks = """[{"uuid":"74000000-0000-4000-8000-000000000001","content":"class page note","order":"00000000"}]"""
        val err = rejected(withExtraPages(page(Fixtures.ALGO, "page", "Untitled page (10000000)", blocks)))
        assertTrue(err.userMessage, err.userMessage.contains("#1 (tag) and #9 (page) share uuid ${Fixtures.ALGO}"))
    }
}
