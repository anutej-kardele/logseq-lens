package com.anutej.logseqpocket.ui.scratchpad

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.anutej.logseqpocket.widget.ScratchpadWidgetReceiver
import com.anutej.logseqpocket.data.scratchpad.ScratchpadStore
import com.anutej.logseqpocket.domain.scratchpad.Edit
import com.anutej.logseqpocket.domain.scratchpad.Focus
import com.anutej.logseqpocket.domain.scratchpad.PadDoc
import com.anutej.logseqpocket.domain.scratchpad.PadEditor
import com.anutej.logseqpocket.domain.scratchpad.PadLine
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.theme.PocketTheme
import com.anutej.logseqpocket.ui.theme.noteBodyStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScratchpadViewModel(private val store: ScratchpadStore) : ViewModel() {
    val doc: StateFlow<PadDoc?> = store.doc.map<PadDoc, PadDoc?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _focus = MutableStateFlow<Focus?>(null)
    val focus: StateFlow<Focus?> = _focus.asStateFlow()

    fun changeText(id: Long, text: String, cursor: Int) = edit { PadEditor.changeText(it, id, text, cursor) }
    fun backspaceAtStart(id: Long) = edit { PadEditor.backspaceAtStart(it, id) }
    fun appendCheckbox() = edit { PadEditor.appendCheckbox(it) }
    fun toggleCheckbox(id: Long) = edit { Edit(PadEditor.toggleCheckbox(it, id), null) }
    fun setChecked(id: Long, checked: Boolean) = edit { Edit(PadEditor.setChecked(it, id, checked)) }
    fun clearChecked() = edit { Edit(PadEditor.clearChecked(it)) }
    fun focusLine(f: Focus) { _focus.value = f }
    fun focusConsumed() { _focus.value = null }

    // DataStore applies updates in call order, so each edit sees the result of the previous one.
    private fun edit(op: (PadDoc) -> Edit) {
        viewModelScope.launch {
            var focus: Focus? = null
            store.update { op(it).also { e -> focus = e.focus }.doc }
            focus?.let { _focus.value = it }
        }
    }
}

/** Invisible first character: when it disappears we know Backspace was pressed at column 0. */
private const val SENTINEL = '​'

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScratchpadScreen(startNewItem: Boolean, onBack: () -> Unit) {
    val vm = pocketViewModel { ScratchpadViewModel(it.scratchpad) }
    val doc by vm.doc.collectAsStateWithLifecycle()
    val focus by vm.focus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<Long?>(null) }
    var newItemHandled by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val requesters = remember { mutableStateMapOf<Long, FocusRequester>() }

    LaunchedEffect(doc != null) {
        if (doc != null && startNewItem && !newItemHandled) { newItemHandled = true; vm.appendCheckbox() }
    }
    LaunchedEffect(focus, doc?.lines?.size) {
        val f = focus ?: return@LaunchedEffect
        val index = doc?.lines?.indexOfFirst { it.id == f.lineId } ?: -1
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scratchpad") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Clear checked items") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                            enabled = doc?.lines?.any { it.checkbox && it.checked } == true,
                            onClick = { menu = false; vm.clearChecked() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add widget to home screen") },
                            leadingIcon = { Icon(Icons.Outlined.Widgets, null) },
                            onClick = {
                                menu = false
                                scope.launch {
                                    runCatching {
                                        GlanceAppWidgetManager(context).requestPinGlanceAppWidget(ScratchpadWidgetReceiver::class.java)
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share as text") },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = {
                                menu = false
                                val text = doc?.let(PadEditor::toPlainText).orEmpty()
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(send, "Share Scratchpad"))
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.navigationBarsPadding().imePadding()) {
                Column {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { focusedId?.let(vm::toggleCheckbox) }, enabled = focusedId != null) {
                            Icon(Icons.Outlined.CheckBox, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Checkbox")
                        }
                        TextButton(onClick = vm::appendCheckbox) {
                            Icon(Icons.Outlined.AddTask, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("New item")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Only on this phone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val d = doc
        if (d == null) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 8.dp),
        ) {
            itemsIndexed(d.lines, key = { _, l -> l.id }) { _, line ->
                val requester = requesters.getOrPut(line.id) { FocusRequester() }
                PadLineRow(
                    line = line,
                    requester = requester,
                    focusRequest = focus?.takeIf { it.lineId == line.id },
                    onFocusConsumed = vm::focusConsumed,
                    onFocused = { focusedId = line.id },
                    onText = { text, cursor -> vm.changeText(line.id, text, cursor) },
                    onBackspaceAtStart = { vm.backspaceAtStart(line.id) },
                    onChecked = { vm.setChecked(line.id, it) },
                )
            }
            item(key = "tail") {
                // Tapping below the last line puts the cursor at its end.
                Box(
                    Modifier.fillMaxWidth().height(240.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { d.lines.lastOrNull()?.let { vm.focusLine(Focus(it.id, it.text.length)) } },
                )
            }
        }
    }
}

@Composable
private fun PadLineRow(
    line: PadLine,
    requester: FocusRequester,
    focusRequest: Focus?,
    onFocusConsumed: () -> Unit,
    onFocused: () -> Unit,
    onText: (String, Int) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onChecked: (Boolean) -> Unit,
) {
    var value by remember(line.id) { mutableStateOf(TextFieldValue("$SENTINEL${line.text}", TextRange(line.text.length + 1))) }
    var hasFocus by remember { mutableStateOf(false) }

    // Adopt external changes (merges, widget, other screens) unless the user is typing in this line.
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

    val base = noteBodyStyle(PocketTheme.readingSize)
    val checked = line.checkbox && line.checked
    val style = base.copy(
        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
    )

    Row(
        Modifier.fillMaxWidth().padding(start = if (line.checkbox) 4.dp else 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (line.checkbox) Checkbox(checked = line.checked, onCheckedChange = onChecked)
        BasicTextField(
            value = value,
            onValueChange = { new ->
                val old = value
                when {
                    !new.text.startsWith(SENTINEL) && new.text.length == old.text.length - 1 &&
                        new.text == old.text.drop(1) -> onBackspaceAtStart()
                    else -> {
                        val raw = new.text.removePrefix(SENTINEL.toString())
                        val shift = if (new.text.startsWith(SENTINEL)) 1 else 0
                        val cursor = (new.selection.end - shift).coerceIn(0, raw.length)
                        if ('\n' in raw) {
                            val first = raw.substringBefore('\n')
                            value = TextFieldValue("$SENTINEL$first", TextRange(first.length + 1))
                            onText(raw, cursor)
                        } else {
                            val sel = TextRange(
                                (new.selection.start - shift).coerceIn(0, raw.length) + 1,
                                (new.selection.end - shift).coerceIn(0, raw.length) + 1,
                            )
                            value = new.copy(text = "$SENTINEL$raw", selection = sel)
                            if (raw != old.text.drop(1)) onText(raw, cursor)
                        }
                    }
                }
            },
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = if (line.checkbox) 0.dp else 8.dp)
                .focusRequester(requester)
                .onFocusChanged { hasFocus = it.isFocused; if (it.isFocused) onFocused() },
        )
    }
}
