package com.anutej.logseqpocket.domain

import com.anutej.logseqpocket.domain.scratchpad.Focus
import com.anutej.logseqpocket.domain.scratchpad.PadDoc
import com.anutej.logseqpocket.domain.scratchpad.PadEditor
import com.anutej.logseqpocket.domain.scratchpad.PadLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadEditorTest {
    private fun doc(vararg lines: PadLine) = PadDoc(lines = lines.toList(), nextId = 100)

    @Test fun typingUpdatesOnlyThatLine() {
        val e = PadEditor.changeText(doc(PadLine(1, "a"), PadLine(2, "b")), 2, "bc")
        assertEquals(listOf("a", "bc"), e.doc.lines.map { it.text })
    }

    @Test fun enterSplitsLineAtCursor() {
        // "groceries" with Enter typed after "gro" → "gro\nceries", caret right after the newline.
        val e = PadEditor.changeText(doc(PadLine(1, "groceries")), 1, "gro\nceries", cursor = 4)
        assertEquals(listOf("gro", "ceries"), e.doc.lines.map { it.text })
        assertEquals(Focus(100, 0), e.focus)
        assertEquals(101, e.doc.nextId)
    }

    @Test fun enterOnCheckboxCreatesUncheckedCheckbox() {
        val e = PadEditor.changeText(doc(PadLine(1, "milk", checkbox = true, checked = true)), 1, "milk\n", cursor = 5)
        val new = e.doc.lines[1]
        assertTrue(new.checkbox)
        assertFalse(new.checked)
        assertEquals(Focus(new.id, 0), e.focus)
        assertTrue(e.doc.lines[0].checked) // the original keeps its state
    }

    @Test fun enterOnEmptyCheckboxLeavesTheList() {
        val e = PadEditor.changeText(doc(PadLine(1, "milk", checkbox = true), PadLine(2, "", checkbox = true)), 2, "\n", cursor = 1)
        assertEquals(2, e.doc.lines.size)
        assertFalse(e.doc.lines[1].checkbox)
        assertEquals(Focus(2, 0), e.focus)
    }

    @Test fun pastingMultipleLinesCreatesLinesAndFocusesTheLast() {
        val e = PadEditor.changeText(doc(PadLine(1, "")), 1, "one\ntwo\nthree", cursor = 13)
        assertEquals(listOf("one", "two", "three"), e.doc.lines.map { it.text })
        assertEquals(Focus(e.doc.lines[2].id, 5), e.focus)
    }

    @Test fun backspaceAtStartOfCheckboxRemovesTheBoxFirst() {
        val e = PadEditor.backspaceAtStart(doc(PadLine(1, "a"), PadLine(2, "task", checkbox = true, checked = true)), 2)
        assertEquals(PadLine(2, "task"), e.doc.lines[1])
    }

    @Test fun backspaceAtStartOfTextMergesWithPreviousLine() {
        val e = PadEditor.backspaceAtStart(doc(PadLine(1, "hello "), PadLine(2, "world")), 2)
        assertEquals(listOf("hello world"), e.doc.lines.map { it.text })
        assertEquals(Focus(1, 6), e.focus)
    }

    @Test fun backspaceOnFirstLineDoesNothing() {
        val d = doc(PadLine(1, "x"))
        assertEquals(d, PadEditor.backspaceAtStart(d, 1).doc)
    }

    @Test fun toggleCheckboxBothWaysAndCheckingTextLineIsIgnored() {
        var d = doc(PadLine(1, "a"))
        d = PadEditor.setChecked(d, 1, true)
        assertFalse(d.lines[0].checked)
        d = PadEditor.toggleCheckbox(d, 1)
        assertTrue(d.lines[0].checkbox)
        d = PadEditor.setChecked(d, 1, true)
        assertTrue(d.lines[0].checked)
        d = PadEditor.toggleCheckbox(d, 1)
        assertEquals(PadLine(1, "a"), d.lines[0])
    }

    @Test fun appendCheckboxReusesTrailingEmptyLine() {
        val e1 = PadEditor.appendCheckbox(doc(PadLine(1, "a"), PadLine(2, "")))
        assertEquals(2, e1.doc.lines.size)
        assertTrue(e1.doc.lines[1].checkbox)
        val e2 = PadEditor.appendCheckbox(doc(PadLine(1, "a")))
        assertEquals(2, e2.doc.lines.size)
        assertEquals(Focus(100, 0), e2.focus)
    }

    @Test fun clearCheckedKeepsAtLeastOneLine() {
        val d = doc(PadLine(1, "x", true, true), PadLine(2, "y", true, false), PadLine(3, "z"))
        assertEquals(listOf(2L, 3L), PadEditor.clearChecked(d).lines.map { it.id })
        assertEquals(1, PadEditor.clearChecked(doc(PadLine(1, "x", true, true))).lines.size)
    }

    @Test fun plainTextExportUsesTaskSyntax() {
        val d = doc(PadLine(1, "Ideas"), PadLine(2, "call mom", true, false), PadLine(3, "pay rent", true, true), PadLine(4, ""))
        assertEquals("Ideas\n- [ ] call mom\n- [x] pay rent", PadEditor.toPlainText(d))
    }

    @Test fun unknownLineIdIsANoOp() {
        val d = doc(PadLine(1, "a"))
        assertEquals(d, PadEditor.changeText(d, 99, "zzz").doc)
        assertEquals(d, PadEditor.backspaceAtStart(d, 99).doc)
    }
}
