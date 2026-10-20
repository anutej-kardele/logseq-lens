package com.anutej.logseqpocket.domain.scratchpad

import kotlinx.serialization.Serializable

/** One line of the Scratchpad: plain text, or a checkbox item. */
@Serializable
data class PadLine(
    val id: Long,
    val text: String = "",
    val checkbox: Boolean = false,
    val checked: Boolean = false,
)

/** The single, local-only Scratchpad note. Never synced anywhere. */
@Serializable
data class PadDoc(
    val version: Int = 1,
    val lines: List<PadLine> = listOf(PadLine(1)),
    val nextId: Long = 2,
    val updatedAt: Long = 0,
) {
    val isBlank: Boolean get() = lines.all { it.text.isBlank() && !it.checkbox }
    val openItems: Int get() = lines.count { it.checkbox && !it.checked }
}

/** Where the cursor should go after an edit. */
data class Focus(val lineId: Long, val cursor: Int)

data class Edit(val doc: PadDoc, val focus: Focus? = null)

/**
 * Pure editing rules, shared by the app editor and the widget, modelled on Samsung/Apple Notes:
 * Enter on a checkbox line starts another checkbox; Enter on an *empty* checkbox line turns it back
 * into text; Backspace at the start of a checkbox removes the box, otherwise joins with the line above.
 */
object PadEditor {
    private val CHECKBOX_PREFIX = Regex("""^(?:[-*] )?\[( |x|X)?] """)

    /** Length of a leading "[] " / "[ ] " / "- [ ] " / "[x] " that turns a text line into a checkbox, else null. */
    fun checkboxPrefixLength(text: String): Int? = CHECKBOX_PREFIX.find(text)?.value?.length

    /**
     * Applies new text for [id]. Newlines (typed Enter or pasted text) split the line; [cursor] is the
     * caret position within [newText], used to decide where focus lands.
     */
    fun changeText(doc: PadDoc, id: Long, newText: String, cursor: Int = newText.length): Edit {
        val idx = doc.lines.indexOfFirst { it.id == id }
        if (idx < 0) return Edit(doc)
        val line = doc.lines[idx]
        if ('\n' !in newText) {
            // Typing "[] " (or "[ ] ", "- [ ] ", "[x] ") at the start of a text line makes it a checkbox.
            if (!line.checkbox) {
                CHECKBOX_PREFIX.find(newText)?.let { m ->
                    val checked = m.groupValues[1].equals("x", ignoreCase = true)
                    val rest = newText.substring(m.range.last + 1)
                    val box = line.copy(text = rest, checkbox = true, checked = checked)
                    // No focus change: the text field already shows the line without the prefix, and a
                    // late cursor move would land in the middle of whatever is typed next.
                    return Edit(doc.withLines(doc.lines.replaceAt(idx, box)))
                }
            }
            return Edit(doc.withLines(doc.lines.replaceAt(idx, line.copy(text = newText))))
        }
        // Enter on an empty checkbox line leaves the list instead of adding another empty box.
        if (line.checkbox && line.text.isEmpty() && newText == "\n") {
            return Edit(doc.withLines(doc.lines.replaceAt(idx, line.copy(checkbox = false, checked = false))), Focus(id, 0))
        }
        val parts = newText.split('\n')
        var nextId = doc.nextId
        val created = parts.drop(1).map { PadLine(nextId++, it, checkbox = line.checkbox, checked = false) }
        val updated = buildList {
            addAll(doc.lines.subList(0, idx))
            add(line.copy(text = parts[0]))
            addAll(created)
            addAll(doc.lines.subList(idx + 1, doc.lines.size))
        }
        // Map the caret (an offset in newText) to a line + column.
        var remaining = cursor.coerceIn(0, newText.length)
        var focusIndex = 0
        for ((i, p) in parts.withIndex()) {
            if (remaining <= p.length) { focusIndex = i; break }
            remaining -= p.length + 1
            focusIndex = i
        }
        val focusId = if (focusIndex == 0) id else created[focusIndex - 1].id
        return Edit(doc.copy(lines = updated, nextId = nextId), Focus(focusId, remaining.coerceAtLeast(0)))
    }

    /** Backspace with the caret at column 0. */
    fun backspaceAtStart(doc: PadDoc, id: Long): Edit {
        val idx = doc.lines.indexOfFirst { it.id == id }
        if (idx < 0) return Edit(doc)
        val line = doc.lines[idx]
        if (line.checkbox) {
            return Edit(doc.withLines(doc.lines.replaceAt(idx, line.copy(checkbox = false, checked = false))), Focus(id, 0))
        }
        if (idx == 0) return Edit(doc, Focus(id, 0))
        val prev = doc.lines[idx - 1]
        val merged = prev.copy(text = prev.text + line.text)
        val lines = doc.lines.toMutableList().apply { set(idx - 1, merged); removeAt(idx) }
        return Edit(doc.withLines(lines), Focus(prev.id, prev.text.length))
    }

    /** Turns a line into a checkbox item or back into text. */
    fun toggleCheckbox(doc: PadDoc, id: Long): PadDoc = doc.mapLine(id) {
        if (it.checkbox) it.copy(checkbox = false, checked = false) else it.copy(checkbox = true)
    }

    fun setChecked(doc: PadDoc, id: Long, checked: Boolean): PadDoc = doc.mapLine(id) {
        if (it.checkbox) it.copy(checked = checked) else it
    }

    /** Appends a new empty checkbox (used by the widget's "+" and the editor). */
    fun appendCheckbox(doc: PadDoc): Edit {
        val last = doc.lines.lastOrNull()
        // Reuse a trailing empty line rather than stacking blanks.
        if (last != null && last.text.isEmpty()) {
            return Edit(doc.mapLine(last.id) { it.copy(checkbox = true, checked = false) }, Focus(last.id, 0))
        }
        val line = PadLine(doc.nextId, checkbox = true)
        return Edit(doc.copy(lines = doc.lines + line, nextId = doc.nextId + 1), Focus(line.id, 0))
    }

    /** Removes ticked items; always leaves at least one line. */
    fun clearChecked(doc: PadDoc): PadDoc {
        val kept = doc.lines.filterNot { it.checkbox && it.checked }
        return if (kept.isEmpty()) doc.copy(lines = listOf(PadLine(doc.nextId)), nextId = doc.nextId + 1) else doc.withLines(kept)
    }

    /** Plain-text export for sharing (Markdown task syntax, which Logseq also understands). */
    fun toPlainText(doc: PadDoc): String = doc.lines.joinToString("\n") {
        when {
            !it.checkbox -> it.text
            it.checked -> "- [x] ${it.text}"
            else -> "- [ ] ${it.text}"
        }
    }.trimEnd()

    private fun PadDoc.withLines(lines: List<PadLine>) = copy(lines = lines.ifEmpty { listOf(PadLine(nextId)) })

    private fun PadDoc.mapLine(id: Long, f: (PadLine) -> PadLine) = copy(lines = lines.map { if (it.id == id) f(it) else it })

    private fun <T> List<T>.replaceAt(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }
}
