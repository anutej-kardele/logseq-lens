package com.anutej.logseqpocket.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.AppContainer
import com.anutej.logseqpocket.data.db.DatasetMetaEntity
import com.anutej.logseqpocket.data.db.PageSummary
import com.anutej.logseqpocket.data.importer.ImportState
import com.anutej.logseqpocket.domain.markdown.InlineParser
import com.anutej.logseqpocket.domain.markdown.plainText
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.ImportStatusBanner
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.components.SectionHeader
import com.anutej.logseqpocket.ui.nav.appContainer
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.sync.GitHubTokenDialog
import com.anutej.logseqpocket.ui.scratchpad.ScratchpadCard
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUi(
    val loading: Boolean = true,
    val meta: DatasetMetaEntity? = null,
    val recentJournals: List<PageSummary> = emptyList(),
    val recentlyUpdated: List<PageSummary> = emptyList(),
    val cardCount: Int = 0,
    val importState: ImportState = ImportState.Idle,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(
        c.notes.meta,
        c.notes.recentJournals(6),
        c.notes.recentlyUpdated(6),
        c.importer.state,
        c.cardCount,
    ) { meta, journals, updated, import, cards ->
        HomeUi(false, meta, journals, updated, cards, import)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun dismissImport() = c.importer.dismiss()
}

enum class Shortcut { Journals, Pages, Tags, Search, Cards }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPage: (String) -> Unit,
    onShortcut: (Shortcut) -> Unit,
    onSettings: () -> Unit,
    onScratchpad: () -> Unit,
) {
    val c = appContainer()
    val vm = pocketViewModel { HomeViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.importer.importUri(uri)
    }
    val pick = { picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
    val hasToken by c.tokens.hasToken.collectAsStateWithLifecycle(false)
    val pad by c.scratchpad.doc.collectAsStateWithLifecycle(null)
    var tokenDialog by remember { mutableStateOf(false) }
    val onSync: () -> Unit = { if (hasToken) c.importer.syncGitHub() else tokenDialog = true }
    if (tokenDialog) GitHubTokenDialog(onDismiss = { tokenDialog = false }, onSaved = { tokenDialog = false; c.importer.syncGitHub() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logseq Pocket") },
                actions = {
                    IconButton(onClick = onSync) { Icon(Icons.Outlined.Sync, "Sync from GitHub") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        if (ui.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ScratchpadCard(pad, onScratchpad) }
            if (ui.importState !is ImportState.Idle) {
                item { ImportStatusBanner(ui.importState, vm::dismissImport) }
            }
            val meta = ui.meta
            if (meta == null) {
                item {
                    EmptyState(
                        Icons.Outlined.FileOpen,
                        "No notes yet",
                        "Import the activity.json produced by your logseq-backup export. Everything stays on this phone and works offline.",
                        actionLabel = "Import notes",
                        onAction = pick,
                    )
                }
                item {
                    OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) { Text("Or sync from your private GitHub backup") }
                }
                return@LazyColumn
            }
            item { GraphCard(meta, ui.cardCount) }
            item { Shortcuts(onShortcut) }
            if (ui.recentJournals.isNotEmpty()) {
                item { SectionHeader("Recent journals", trailing = { TextLink("All") { onShortcut(Shortcut.Journals) } }) }
                items(ui.recentJournals, key = { "j:" + it.page.uuid }) { j -> JournalRow(j) { onOpenPage(j.page.uuid) } }
            }
            if (ui.recentlyUpdated.isNotEmpty()) {
                item { SectionHeader("Recently updated") }
                items(ui.recentlyUpdated, key = { "u:" + it.page.uuid }) { p ->
                    SimpleRow(p.page.title, "Updated ${Format.timestamp(p.page.updatedAt)}") { onOpenPage(p.page.uuid) }
                }
            } else if (!meta.hasTimestamps) {
                item {
                    Text(
                        "This export has no edit timestamps, so “recently updated” isn't available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun GraphCard(meta: DatasetMetaEntity, cards: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("GRAPH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(meta.graph, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusPill(Icons.Outlined.Lock, "Read-only")
                StatusPill(Icons.Outlined.CloudOff, "Works offline")
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Pages", meta.pageCount, Modifier.weight(1f))
                Stat("Blocks", meta.blockCount, Modifier.weight(1f))
                Stat("Flashcards", cards, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Last import ${Format.timestamp(meta.importedAt)}" + (meta.originLabel?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Column(modifier) {
        Text("$value", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Shortcuts(onShortcut: (Shortcut) -> Unit) {
    val items = listOf(
        Triple(Shortcut.Journals, Icons.Outlined.CalendarMonth, "Journals"),
        Triple(Shortcut.Pages, Icons.Outlined.Description, "Pages"),
        Triple(Shortcut.Tags, Icons.Outlined.Tag, "Tags"),
        Triple(Shortcut.Cards, Icons.Outlined.Style, "Cards"),
        Triple(Shortcut.Search, Icons.Outlined.Search, "Search"),
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (s, icon, label) ->
            Surface(
                Modifier.weight(1f).clickable { onShortcut(s) },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun JournalRow(j: PageSummary, onClick: () -> Unit) {
    val preview = j.preview?.let { plainText(InlineParser.parse(it)).replace('\n', ' ').trim() }
    SimpleRow(
        Format.journalLong(j.page.journalDay, j.page.title),
        preview?.takeIf { it.isNotEmpty() } ?: if (j.page.nonEmptyBlockCount == 0) "No content" else null,
        trailing = Format.relativeDay(j.page.journalDay),
        onClick = onClick,
    )
}

@Composable
fun SimpleRow(title: String, subtitle: String?, trailing: String? = null, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
