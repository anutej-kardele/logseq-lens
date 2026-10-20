package com.anutej.logseqpocket.ui.scratchpad

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.scratchpad.ScratchpadStore
import com.anutej.logseqpocket.domain.scratchpad.Edit
import com.anutej.logseqpocket.domain.scratchpad.Focus
import com.anutej.logseqpocket.domain.scratchpad.PadDoc
import com.anutej.logseqpocket.domain.scratchpad.PadEditor
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.widget.ScratchpadWidgetReceiver
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

    /** Puts the cursor at the end of [lineId] (or of the last line) once the note has loaded. */
    fun focusWhenLoaded(lineId: Long?) {
        viewModelScope.launch {
            val d = store.current()
            val line = d.lines.firstOrNull { it.id == lineId } ?: d.lines.last()
            _focus.value = Focus(line.id, line.text.length)
        }
    }

    // DataStore applies updates in call order, so each edit sees the result of the previous one.
    private fun edit(op: (PadDoc) -> Edit) {
        viewModelScope.launch {
            var focus: Focus? = null
            store.update { op(it).also { e -> focus = e.focus }.doc }
            focus?.let { _focus.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScratchpadScreen(startNewItem: Boolean, onBack: () -> Unit) {
    val vm = pocketViewModel(key = "scratchpad-screen") { ScratchpadViewModel(it.scratchpad) }
    val doc by vm.doc.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var focusedLine by remember { mutableStateOf<Long?>(null) }
    var newItemHandled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(doc != null) {
        if (doc != null && startNewItem && !newItemHandled) { newItemHandled = true; vm.appendCheckbox() }
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
            if (focusedLine != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.navigationBarsPadding().imePadding()) {
                    PadEditToolbar(focusedLine, vm, Modifier.padding(horizontal = 4.dp))
                }
            }
        },
    ) { padding ->
        val d = doc
        if (d == null) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            PadNoteEditor(
                doc = d,
                vm = vm,
                onFocusedLine = { focusedLine = it },
                tailHeight = 320.dp,
                placeholder = "Thoughts, to-dos… (type [] for a checkbox)",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
