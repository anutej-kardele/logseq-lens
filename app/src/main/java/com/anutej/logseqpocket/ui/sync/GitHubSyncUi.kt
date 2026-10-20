package com.anutej.logseqpocket.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.json.ImportException
import com.anutej.logseqpocket.data.repo.AppSettings
import com.anutej.logseqpocket.data.sync.DEFAULT_SOURCE
import com.anutej.logseqpocket.data.sync.GitHubSourceConfig
import com.anutej.logseqpocket.data.sync.GitHubSync
import com.anutej.logseqpocket.data.sync.SyncWorker
import com.anutej.logseqpocket.data.sync.parseGitHubLink
import com.anutej.logseqpocket.ui.nav.appContainer
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One form for everything GitHub needs: where the export is (paste any GitHub link — the clone link is
 * enough) and the read-only token. Missing branch/file are discovered from the repo. The new source only
 * becomes active once its file downloads and validates.
 */
@Composable
fun ConnectGitHubDialog(onDismiss: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val hasToken by c.tokens.hasToken.collectAsStateWithLifecycle(false)
    val start = c.importer.draftSource ?: settings?.githubSource ?: return

    var link by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf(start.owner) }
    var repo by rememberSaveable { mutableStateOf(start.repo) }
    var branch by rememberSaveable { mutableStateOf(start.branch) }
    var path by rememberSaveable { mutableStateOf(start.path) }
    var folderHint by rememberSaveable { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    val tokenReady = token.isNotBlank() || hasToken
    val candidate = GitHubSourceConfig(owner.trim(), repo.trim(), branch.trim(), path.trim())

    // Look up branches and .json files whenever the repo/branch changes and a token is available.
    LaunchedEffect(owner, repo, branch, folderHint, tokenReady) {
        lookupError = null
        if (!tokenReady || candidate.copy(branch = "x", path = "x.json").validate() != null) return@LaunchedEffect
        delay(500)
        loading = true
        try {
            val sync = c.githubSync
            val tok = token.ifBlank { null }
            if (branch.isBlank()) {
                sync.defaultBranch(owner, repo, tok)
                    .onSuccess { branch = it }
                    .onFailure { lookupError = (it as? ImportException)?.error?.userMessage }
                return@LaunchedEffect // effect restarts with the detected branch
            }
            sync.listBranches(owner, repo, tok).onSuccess { branches = it }
            val found = folderHint?.let { sync.listJsonFiles(owner, repo, branch, it, tok) }
                ?: sync.findJsonFiles(owner, repo, branch, tok)
            found.onSuccess { list ->
                files = list
                if (list.isEmpty()) lookupError = "No .json files found on $branch."
                else if (path.isBlank() || path !in list) {
                    path = list.singleOrNull()
                        ?: list.firstOrNull { it.startsWith(GitHubSync.PREFERRED_FOLDER) }
                        ?: list.first()
                }
            }.onFailure { lookupError = (it as? ImportException)?.error?.userMessage }
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect GitHub (read-only)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste your repo link (the clone link is enough) and a fine-grained token with Contents: Read-only " +
                        "on that repo. Nothing is ever written to GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { v ->
                        link = v
                        parseGitHubLink(v)?.let { p ->
                            owner = p.owner
                            repo = p.repo
                            branch = p.branch.orEmpty()
                            if (p.isFolder) { folderHint = p.path.orEmpty(); path = "" } else { folderHint = null; path = p.path.orEmpty() }
                        }
                    },
                    label = { Text("GitHub link") },
                    placeholder = { Text("https://github.com/you/logseq-backup.git") },
                    singleLine = true,
                    isError = link.isNotBlank() && parseGitHubLink(link) == null,
                    supportingText = if (link.isNotBlank() && parseGitHubLink(link) == null) {
                        { Text("Not a GitHub repo, file or folder link") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(owner, { owner = it }, label = { Text("Owner") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(repo, { repo = it }, label = { Text("Repository") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                SuggestField("Branch", branch, { branch = it }, branches)
                SuggestField("File", path, { path = it; folderHint = null }, files)
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(if (hasToken) "Token (saved ✓ — leave empty to keep)" else "Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (token.trim().startsWith("ghp_")) {
                    Text(
                        "This looks like a classic token, which can't be limited to one repository or read-only. " +
                            "A fine-grained token (github_pat_…) is strongly recommended.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        loading -> {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("  Looking up the repo…", style = MaterialTheme.typography.labelSmall)
                        }
                        lookupError != null -> Text(lookupError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        !tokenReady && (branch.isBlank() || path.isBlank()) -> Text(
                            "Add the token and the branch and export file are found automatically.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        candidate.validate() != null && (owner.isNotBlank() || path.isNotBlank()) ->
                            Text(candidate.validate()!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = {
                    link = ""; folderHint = null
                    owner = DEFAULT_SOURCE.owner; repo = DEFAULT_SOURCE.repo
                    branch = DEFAULT_SOURCE.branch; path = DEFAULT_SOURCE.path
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Clear form") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = candidate.validate() == null && tokenReady && (token.isBlank() || token.trim().length >= 20),
                onClick = {
                    scope.launch {
                        if (token.isNotBlank()) c.tokens.save(token.trim())
                        token = ""
                        c.importer.draftSource = candidate
                        c.importer.syncGitHub(candidate)
                        onDismiss()
                    }
                },
            ) { Text("Connect & sync") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Editable text field with a dropdown of suggestions fetched from GitHub. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestField(label: String, value: String, onValue: (String) -> Unit, options: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && options.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { if (options.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && options.isNotEmpty(), onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onValue(o); expanded = false })
            }
        }
    }
}

/** Settings block: the active source, Sync now, Edit source, token removal, optional background checks. */
@Composable
fun GitHubSyncSection() {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasToken by c.tokens.hasToken.collectAsStateWithLifecycle(false)
    val meta by c.notes.meta.collectAsStateWithLifecycle(null)
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var dialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val source = settings.githubSource

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GitHub source", style = MaterialTheme.typography.titleSmall)
            Text(
                if (source.isConfigured) "${source.repoSlug} · ${source.branch.ifBlank { "default branch" }}\n${source.path}"
                else "Not connected yet — tap Connect and paste your backup repo's link.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (hasToken) "Token saved ✓ (encrypted on this device)" else "No token saved",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasToken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            meta?.takeIf { it.origin == ImportOrigin.GitHub.name && it.originLabel == source.key }?.let {
                Text("Last synced ${Format.timestamp(it.importedAt)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ready = hasToken && source.isConfigured
                FilledTonalButton(onClick = { if (ready) c.importer.syncGitHub() else dialog = true }) { Text("Sync now") }
                OutlinedButton(onClick = { dialog = true }) { Text(if (ready) "Edit source" else "Connect") }
            }
            if (hasToken) {
                TextButton(onClick = { confirmRemove = true }) {
                    Text("Remove token", color = MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Background check", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "About twice a day on Wi-Fi when the battery isn't low. Android decides the exact timing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.backgroundSync, onCheckedChange = { on ->
                        scope.launch {
                            c.settings.setBackgroundSync(on)
                            SyncWorker.schedule(context, on)
                        }
                    })
                }
            }
        }
    }

    if (dialog) ConnectGitHubDialog(onDismiss = { dialog = false })
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove GitHub token?") },
            text = { Text("Sync will stop until you add a token again. Your local notes stay on this phone. Revoke the token on GitHub too if you no longer need it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        c.tokens.clear()
                        c.settings.setBackgroundSync(false)
                        SyncWorker.schedule(context, false)
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}
