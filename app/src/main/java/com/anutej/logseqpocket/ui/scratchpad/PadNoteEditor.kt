package com.anutej.logseqpocket.ui.scratchpad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.domain.scratchpad.Focus
import com.anutej.logseqpocket.domain.scratchpad.PadDoc
import com.anutej.logseqpocket.domain.scratchpad.PadEditor
import com.anutej.logseqpocket.domain.scratchpad.PadLine
import com.anutej.logseqpocket.ui.theme.PocketTheme
import com.anutej.logseqpocket.ui.theme.noteBodyStyle

/** Invisible first character: when it disappears we know Backspace was pressed at column 0. */
private const val SENTINEL = '​'

/** Height of a checklist row; text lines get roughly the same height from their padding. */
private val CHECKBOX_ROW = 32.dp

/**
 * The Scratchpad as directly editable lines — used by the full screen, the Home card and the floating
 * sheet. Tapping a line edits it in place; tapping the empty space below continues at the end.
 * [onFocusedLine] reports which line has the cursor (null when none), e.g. to show [PadEditToolbar].
 */
@Composable
fun PadNoteEditor(
    doc: PadDoc,
    vm: ScratchpadViewModel,
    onFocusedLine: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    lines: List<PadLine> = doc.lines,
    textStyle: TextStyle = noteBodyStyle(PocketTheme.readingSize),
    tailHeight: Dp = 56.dp,
    placeholder: String? = null,
) {
    val focus by vm.focus.collectAsStateWithLifecycle()
    val requesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    var focusedId by remember { mutableStateOf<Long?>(null) }

    Column(modifier) {
        val showPlaceholder = placeholder != null && doc.isBlank && focusedId == null
        lines.forEach { line ->
            Box {
                PadLineRow(
                    line = line,
                    requester = requesters.getOrPut(line.id) { FocusRequester() },
                    focusRequest = focus?.takeIf { it.lineId == line.id },
                    textStyle = textStyle,
                    onFocusConsumed = vm::focusConsumed,
                    onFocusChange = { focused ->
                        if (focused) focusedId = line.id else if (focusedId == line.id) focusedId = null
                        onFocusedLine(focusedId)
                    },
                    onText = { text, cursor -> vm.changeText(line.id, text, cursor) },
                    onBackspaceAtStart = { vm.backspaceAtStart(line.id) },
                    onChecked = { vm.setChecked(line.id, it) },
                )
                if (showPlaceholder && line == lines.first()) {
                    Text(
                        placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 12.dp, top = 5.dp),
                    )
                }
            }
        }
        // Tapping below the last line puts the cursor at its end.
        Box(
            Modifier.fillMaxWidth().height(tailHeight).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { lines.lastOrNull()?.let { vm.focusLine(Focus(it.id, it.text.length)) } },
        )
    }
}

/** Compact strip shown only while typing: make the current line a checkbox, or put the keyboard away. */
@Composable
fun PadEditToolbar(focusedLine: Long?, vm: ScratchpadViewModel, modifier: Modifier = Modifier) {
    if (focusedLine == null) return
    val focusManager = LocalFocusManager.current
    Row(modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.toggleCheckbox(focusedLine) }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.CheckBox,
                "Toggle checkbox on this line (or type [] )",
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { focusManager.clearFocus() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.KeyboardHide, "Hide keyboard", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PadLineRow(
    line: PadLine,
    requester: FocusRequester,
    focusRequest: Focus?,
    textStyle: TextStyle,
    onFocusConsumed: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onText: (String, Int) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onChecked: (Boolean) -> Unit,
) {
    var value by remember(line.id) { mutableStateOf(TextFieldValue("$SENTINEL${line.text}", TextRange(line.text.length + 1))) }
    var hasFocus by remember { mutableStateOf(false) }

    // Adopt external changes (merges, widget, shortcuts) unless the user is typing in this line.
    LaunchedEffect(line.text) {
        if (!hasFocus && value.text.drop(1) != line.text) {
            value = TextFieldValue("$SENTINEL${line.text}", TextRange(line.text.length + 1))
        }
    }
    LaunchedEffect(focusRequest) {
        val f = focusRequest ?: return@LaunchedEffect
        val text = if (hasFocus) value.text.drop(1) else line.text
        val c = f.cursor.coerceIn(0, text.length)
        value = TextFieldValue("$SENTINEL$text", TextRange(c + 1))
        runCatching { requester.requestFocus() }
        onFocusConsumed()
    }

    val checked = line.checkbox && line.checked
    val style = textStyle.copy(
        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
    )

    Row(
        Modifier.fillMaxWidth().padding(start = if (line.checkbox) 4.dp else 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (line.checkbox) {
            // Compact box (32dp row instead of Material's 48dp) so checklist lines sit as close as text lines.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides CHECKBOX_ROW) {
                Checkbox(checked = line.checked, onCheckedChange = onChecked)
            }
            Spacer(Modifier.width(4.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = { new ->
                val old = value
                if (!new.text.startsWith(SENTINEL) && new.text == old.text.drop(1)) {
                    onBackspaceAtStart()
                    return@BasicTextField
                }
                val raw = new.text.removePrefix(SENTINEL.toString())
                val shift = if (new.text.startsWith(SENTINEL)) 1 else 0
                val cursor = (new.selection.end - shift).coerceIn(0, raw.length)
                if ('\n' in raw) {
                    val first = raw.substringBefore('\n')
                    value = TextFieldValue("$SENTINEL$first", TextRange(first.length + 1))
                    onText(raw, cursor)
                } else if (!line.checkbox && PadEditor.checkboxPrefixLength(raw) != null) {
                    // "[] " typed at the start: show the line without the prefix; the rule makes it a checkbox.
                    val p = PadEditor.checkboxPrefixLength(raw)!!
                    val rest = raw.substring(p)
                    value = TextFieldValue("$SENTINEL$rest", TextRange((cursor - p).coerceIn(0, rest.length) + 1))
                    onText(raw, cursor)
                } else {
                    val sel = TextRange(
                        (new.selection.start - shift).coerceIn(0, raw.length) + 1,
                        (new.selection.end - shift).coerceIn(0, raw.length) + 1,
                    )
                    value = new.copy(text = "$SENTINEL$raw", selection = sel)
                    if (raw != old.text.drop(1)) onText(raw, cursor)
                }
            },
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = if (line.checkbox) 0.dp else 5.dp)
                .focusRequester(requester)
                .onFocusChanged {
                    if (it.isFocused != hasFocus) {
                        hasFocus = it.isFocused
                        onFocusChange(it.isFocused)
                    }
                },
        )
    }
}
