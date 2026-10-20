package com.anutej.logseqpocket.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.BuildConfigInfo
import com.anutej.logseqpocket.data.repo.AppSettings
import com.anutej.logseqpocket.ui.components.ImportStatusBanner
import com.anutej.logseqpocket.ui.components.MessageBanner
import com.anutej.logseqpocket.ui.components.SectionHeader
import com.anutej.logseqpocket.ui.nav.appContainer
import com.anutej.logseqpocket.ui.sync.GitHubSyncSection
import com.anutej.logseqpocket.ui.theme.ReadingSize
import com.anutej.logseqpocket.ui.theme.ThemeMode
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private enum class Confirm { ClearNotes, ResetProgress, ClearOrphans }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val meta by c.notes.meta.collectAsStateWithLifecycle(null)
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val importState by c.importer.state.collectAsStateWithLifecycle()
    val overview by c.study.overview.collectAsStateWithLifecycle(null)
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var forceNext by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.importer.importUri(uri, force = forceNext)
    }
    fun pick(force: Boolean) { forceNext = force; picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                MessageBanner(
                    "Read-only. This app never edits, deletes or pushes anything to Logseq or GitHub.",
                    icon = Icons.Outlined.Lock,
                )
            }
            if (importState !is com.anutej.logseqpocket.data.importer.ImportState.Idle) {
                item { ImportStatusBanner(importState, c.importer::dismiss) }
            }
            message?.let { m -> item { MessageBanner(m, onDismiss = { message = null }) } }

            item { SectionHeader("Local dataset") }
            item {
                val m = meta
                InfoCard(
                    if (m == null) listOf("Status" to "No notes imported")
                    else listOfNotNull(
                        "Graph" to m.graph,
                        "Pages" to "${m.pageCount}" + (m.declaredPageCount?.takeIf { it != m.pageCount }?.let { " (export says $it)" } ?: ""),
                        "Blocks" to "${m.blockCount}" + (m.declaredBlockCount?.takeIf { it != m.blockCount }?.let { " (export says $it)" } ?: ""),
                        "Flashcards" to "${overview?.decks?.firstOrNull()?.size ?: 0}",
                        "Excluded system views" to (m.excludedSystemViewBlocks?.toString() ?: "—"),
                        "Export format" to "schemaVersion ${m.schemaVersion} · ${m.source ?: "unknown source"}",
                        "Logseq DB schema" to (m.logseqSchema ?: "—"),
                        "Last import" to Format.timestamp(m.importedAt),
                        "Imported from" to (m.originLabel ?: m.origin),
                        "Size" to "%.1f KB".format(m.byteCount / 1024.0),
                        runCatching { Json.decodeFromString<List<String>>(m.warnings) }.getOrNull()?.takeIf { it.isNotEmpty() }
                            ?.let { "Warnings" to it.joinToString("; ") },
                    )
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pick(false) }) { Text("Import JSON") }
                    OutlinedButton(onClick = { pick(true) }, enabled = meta != null) { Text("Force re-import") }
                }
            }
            item { SectionHeader("Sync") }
            item { GitHubSyncSection() }

            item { SectionHeader("Appearance") }
            item {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { c.settings.setTheme(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.name) }
                    }
                }
            }
            item {
                Text("Reading size", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    ReadingSize.entries.forEachIndexed { i, size ->
                        SegmentedButton(
                            selected = settings.readingSize == size,
                            onClick = { scope.launch { c.settings.setReadingSize(size) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ReadingSize.entries.size),
                        ) { Text(size.name) }
                    }
                }
            }

            item { SectionHeader("Review progress") }
            item {
                Text(
                    "Know / Review again results are stored only on this phone." +
                        ((overview?.orphanProgress ?: 0).takeIf { it > 0 }?.let { " $it saved results belong to cards not in the current export (kept in case they return)." } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if ((overview?.orphanProgress ?: 0) > 0) OutlinedButton(onClick = { confirm = Confirm.ClearOrphans }) { Text("Remove orphaned") }
                    OutlinedButton(onClick = { confirm = Confirm.ResetProgress }) { Text("Reset all progress") }
                }
            }

            item { SectionHeader("Danger zone") }
            item {
                OutlinedButton(onClick = { confirm = Confirm.ClearNotes }, enabled = meta != null) { Text("Clear local notes…") }
            }
            item {
                Text(
                    "Logseq (personal reader) ${BuildConfigInfo.versionName} · no analytics, no tracking, no backend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }

    confirm?.let { which ->
        val (title, body) = when (which) {
            Confirm.ClearNotes -> "Clear local notes?" to
                "Removes the imported notes from this phone. Your Logseq graph and GitHub backup are not affected. Review progress is kept."
            Confirm.ResetProgress -> "Reset review progress?" to "All Know / Review again history and saved sessions on this phone will be deleted."
            Confirm.ClearOrphans -> "Remove orphaned progress?" to "Deletes saved results for cards that are no longer in the export."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        when (which) {
                            Confirm.ClearNotes -> { c.clearNotes(); message = "Local notes cleared." }
                            Confirm.ResetProgress -> { c.study.resetAllProgress(); message = "Review progress reset." }
                            Confirm.ClearOrphans -> { c.study.deleteOrphans(); message = "Orphaned progress removed." }
                        }
                    }
                }) { Text("Confirm", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (k, v) ->
                Row {
                    Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
                    Text(v, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.58f))
                }
            }
        }
    }
}
