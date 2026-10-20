package com.anutej.logseqpocket.data

import com.anutej.logseqpocket.Fixtures
import com.anutej.logseqpocket.data.json.ExportParser
import com.anutej.logseqpocket.data.json.ImportError
import com.anutej.logseqpocket.data.json.ImportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExportParserTest {
    @Test fun parsesTopLevelAndIgnoresUnknownFields() {
        val parsed = ExportParser().parse(Fixtures.stream(Fixtures.synthetic))
        val dto = parsed.dto
        assertEquals(1, dto.schemaVersion)
        assertEquals("Synthetic", dto.graph)
        assertEquals("synthetic-test-fixture", dto.source)
        assertEquals(8, dto.pageCount)
        assertEquals(8, dto.pages!!.size)
        assertEquals(64, parsed.sha256.length)
    }

    @Test fun tagsMayBeObjectsOrBareStrings() {
        val j3 = Fixtures.dto().pages!!.first { it.uuid == Fixtures.J3 }
        val stringTagged = j3.blocks!!.first { it.content == "string tag" }
        assertEquals("habit", stringTagged.tags?.single()?.name)
        assertEquals(null, stringTagged.tags?.single()?.uuid)
    }

    @Test fun hashChangesWithContent() {
        val a = ExportParser().parse(Fixtures.stream(Fixtures.synthetic)).sha256
        val b = ExportParser().parse(Fixtures.stream(Fixtures.synthetic.replace("Paris", "Lyon"))).sha256
        assertNotEquals(a, b)
    }

    @Test fun malformedJsonIsRejected() {
        expect<ImportError.MalformedJson> { ExportParser().parse(Fixtures.stream("{\"schemaVersion\": 1, \"pages\": [")) }
        expect<ImportError.MalformedJson> { ExportParser().parse(Fixtures.stream("<html>not json</html>")) }
        expect<ImportError.MalformedJson> { ExportParser().parse(Fixtures.stream("")) }
    }

    @Test fun oversizedInputIsRejected() {
        expect<ImportError.TooLarge> { ExportParser(maxBytes = 1000).parse(Fixtures.stream(Fixtures.synthetic)) }
    }

    @Test fun wrongFieldTypesAreRejectedNotCrashed() {
        expect<ImportError.MalformedJson> { ExportParser().parse(Fixtures.stream("""{"schemaVersion":1,"pages":"nope"}""")) }
    }

    private inline fun <reified E : ImportError> expect(block: () -> Unit) {
        try {
            block(); fail("expected ${E::class.simpleName}")
        } catch (e: ImportException) {
            assertTrue("got ${e.error::class.simpleName}", e.error is E)
        }
    }
}
