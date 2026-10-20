package com.anutej.logseqpocket.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileOpen
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.AppContainer
import com.anutej.logseqpocket.data.db.DatasetMetaEntity
import com.anutej.logseqpocket.data.db.PageSummary
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.importer.ImportState
import com.anutej.logseqpocket.domain.markdown.InlineParser
import com.anutej.logseqpocket.domain.markdown.plainText
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.ImportStatusBanner
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.components.SectionHeader
import com.anutej.logseqpocket.ui.nav.appContainer
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.sync.ConnectGitHubDialog
import com.anutej.logseqpocket.data.repo.AppSettings
import com.anutej.logseqpocket.data.sync.isGraphExportCandidate
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.anutej.logseqpocket.ui.scratchpad.HOME_CARD_FRACTION
import com.anutej.logseqpocket.ui.scratchpad.ScratchpadCard
import com.anutej.logseqpocket.ui.scratchpad.ScratchpadViewModel
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn

data class HomeUi(
    val loading: Boolean = true,
    val meta: DatasetMetaEntity? = null,
    val recentJournals: List<PageSummary> = emptyList(),
    val recentlyUpdated: List<PageSummary> = emptyList(),
    val importState: ImportState = ImportState.Idle,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(
        c.notes.meta,
        c.notes.recentJournals(6),
        c.notes.recentlyUpdated(6),
        c.importer.state,
    ) { meta, journals, updated, import ->
        HomeUi(false, meta, journals, updated, import)
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
    val scope = rememberCoroutineScope()
    val padVm = pocketViewModel(key = "home-scratchpad") { ScratchpadViewModel(it.scratchpad) }
    val pad by padVm.doc.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var connectDialog by remember { mutableStateOf(false) }
    val onSync: () -> Unit = { if (hasToken) c.importer.syncGitHub() else connectDialog = true }
    if (connectDialog) ConnectGitHubDialog(onDismiss = { connectDialog = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    HomeTitle(
                        meta = ui.meta,
                        importState = ui.importState,
                        settings = settings,
                        hasToken = hasToken,
                        onOpenSwitcher = { scope.launch { c.importer.refreshGitHubFilesNow() } },
                        onSwitch = { path -> c.importer.syncGitHub(settings.githubSource.copy(path = path)) },
                        onChangeSource = { connectDialog = true },
                    )
                },
                actions = {
                    IconButton(onClick = onSync) { Icon(Icons.Outlined.Sync, "Sync from GitHub") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        if (ui.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        // The Scratchpad card may use ~35% of the content area (between top bar and bottom tabs).
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardMax = (maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()) * HOME_CARD_FRACTION
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "scratchpad") { ScratchpadCard(pad, padVm, onScratchpad, maxHeight = cardMax) }
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
                    OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) { Text("Or connect your GitHub backup repo") }
                }
                return@LazyColumn
            }
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

/**
 * Graph name plus one line of freshness ("Synced from GitHub · 2 h ago"). Counts and details live in
 * Settings; the launcher already says "Logseq".
 */
@Composable
private fun HomeTitle(
    meta: DatasetMetaEntity?,
    importState: ImportState,
    settings: AppSettings,
    hasToken: Boolean,
    onOpenSwitcher: () -> Unit,
    onSwitch: (path: String) -> Unit,
    onChangeSource: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val status = when {
        importState is ImportState.Running -> "Syncing…"
        meta == null -> "No notes yet"
        meta.origin == ImportOrigin.GitHub.name -> "Synced from GitHub · ${Format.ago(meta.importedAt, now)}"
        else -> "Imported from file · ${Format.ago(meta.importedAt, now)}"
    }
    val source = settings.githubSource
    // Filtered again here so lists cached by older versions drop manifest/config files too.
    val files = settings.githubFiles.filter(::isGraphExportCandidate).ifEmpty { listOf(source.path) }
    Column(Modifier.clickable(enabled = hasToken) { menu = true; onOpenSwitcher() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meta?.graph ?: "Logseq", maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hasToken) Icon(Icons.Outlined.ArrowDropDown, "Switch graph")
        }
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color = if (importState is ImportState.Running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Graph switcher: the .json exports in the source folder. Picking one downloads it and replaces the
        // current graph (only after it validates); flashcard progress comes back when you switch back.
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(
                "${source.repoSlug} · ${source.branch}" + (source.folder.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            files.forEach { f ->
                val current = f == source.path
                DropdownMenuItem(
                    text = { Text(f.substringAfterLast('/').removeSuffix(".json")) },
                    leadingIcon = { if (current) Icon(Icons.Outlined.Check, "Current") else Spacer(Modifier.size(24.dp)) },
                    onClick = { menu = false; if (!current) onSwitch(f) },
                )
            }
            Text(
                "Switching downloads that file (needs internet) and replaces the notes on this phone.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).width(260.dp),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Change source…") },
                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                onClick = { menu = false; onChangeSource() },
            )
        }
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
