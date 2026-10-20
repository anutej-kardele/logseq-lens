package com.anutej.logseqpocket.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.anutej.logseqpocket.data.scratchpad.PadDocSerializer
import com.anutej.logseqpocket.data.scratchpad.ScratchpadStore
import com.anutej.logseqpocket.domain.scratchpad.PadDoc
import com.anutej.logseqpocket.domain.scratchpad.PadEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScratchpadStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private var changes = 0

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(tmp.root, "scratchpad.json")
    }

    @After fun tearDown() = scope.cancel()

    private fun store(s: CoroutineScope = scope) = ScratchpadStore(
        DataStoreFactory.create(
            serializer = PadDocSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { PadDoc() },
            scope = s,
            produceFile = { file },
        ),
        clock = { 42L },
        onChanged = { changes++ },
    )

    @Test fun startsWithOneEmptyLine() = runBlocking {
        val d = store().current()
        assertEquals(1, d.lines.size)
        assertTrue(d.isBlank)
    }

    @Test fun editsPersistAcrossReopen() = runBlocking {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store(first).update { PadEditor.changeText(it, it.lines[0].id, "buy milk\nwalk").doc }
        first.cancel()
        val reopened = store().current()
        assertEquals(listOf("buy milk", "walk"), reopened.lines.map { it.text })
        assertEquals(42L, reopened.updatedAt)
        assertTrue(file.readText().contains("buy milk"))
    }

    @Test fun concurrentWidgetTickAndEditorTypingBothSurvive() = runBlocking {
        val s = store()
        s.update { PadEditor.appendCheckbox(PadEditor.changeText(it, it.lines[0].id, "task").doc).doc }
        val boxId = s.current().lines.first { it.checkbox }.id
        listOf(
            async { s.update { PadEditor.setChecked(it, boxId, true) } },
            async { s.update { PadEditor.changeText(it, it.lines[0].id, "task list").doc } },
        ).awaitAll()
        val d = s.current()
        assertEquals("task list", d.lines[0].text)
        assertTrue(d.lines.first { it.id == boxId }.checked)
        assertTrue(changes >= 3)
    }

    @Test fun noOpUpdateDoesNotTouchTimestamp() = runBlocking {
        val s = store()
        val before = s.current()
        s.update { it }
        assertEquals(before, s.current())
    }

    @Test fun corruptFileFallsBackToEmptyNoteInsteadOfCrashing() = runBlocking {
        file.writeText("{ not json")
        assertEquals(PadDoc(), store().current())
    }
}
