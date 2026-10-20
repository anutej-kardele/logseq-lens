package com.anutej.logseqpocket.data

import com.anutej.logseqpocket.data.importer.GraphMapper
import com.anutej.logseqpocket.data.json.ExportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The README's "try it without GitHub" file must always import cleanly. */
class DemoGraphTest {
    @Test fun demoGraphImports() {
        val file = File("../docs/demo/demo-graph.json")
        val bundle = file.inputStream().use { GraphMapper.map(ExportParser().parse(it).dto) }
        assertEquals("Demo", bundle.graph)
        assertTrue(bundle.warnings.isEmpty())
        assertEquals(6, bundle.cards.size)
        assertEquals(setOf("leetcode", "algo-pattern"), bundle.decks.map { d -> bundle.tags.first { it.tagKey == d.tagKey }.name }.toSet())
    }
}
