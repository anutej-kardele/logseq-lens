package com.anutej.logseqpocket.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.repo.AppSettings
import com.anutej.logseqpocket.data.sync.GitHubSource
import com.anutej.logseqpocket.data.sync.SyncWorker
import com.anutej.logseqpocket.ui.nav.appContainer
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.launch

/** One-time token entry. The token is saved encrypted and never displayed again. */
@Composable
fun GitHubTokenDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    val trimmed = token.trim()
    val classic = trimmed.startsWith("ghp_")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect GitHub (read-only)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Paste a fine-grained personal access token with access to only ${GitHubSource.OWNER}/${GitHubSource.REPO} " +
                        "and the permission Contents: Read-only. It's stored encrypted on this phone and used only to download " +
                        GitHubSource.PATH + ".",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (classic) {
                    Text(
                        "This looks like a classic token, which can't be limited to one repository or to read-only. " +
                            "A fine-grained token (github_pat_…) is strongly recommended.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = trimmed.length >= 20 && !trimmed.contains(' '), onClick = {
                scope.launch {
                    c.tokens.save(trimmed)
                    token = ""
                    onSaved()
                }
            }) { Text("Save & sync") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Settings block: fixed source, token status, Sync now, optional background checks. */
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

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Private GitHub backup", style = MaterialTheme.typography.titleSmall)
            Text(
                "${GitHubSource.OWNER}/${GitHubSource.REPO} · ${GitHubSource.BRANCH}\n${GitHubSource.PATH}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (hasToken) "Token saved ✓ (encrypted on this device)" else "No token saved",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasToken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            meta?.takeIf { it.origin == ImportOrigin.GitHub.name }?.let {
                Text("Last synced ${Format.timestamp(it.importedAt)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { if (hasToken) c.importer.syncGitHub() else dialog = true }) { Text("Sync now") }
                if (hasToken) {
                    OutlinedButton(onClick = { dialog = true }) { Text("Replace token") }
                } else {
                    OutlinedButton(onClick = { dialog = true }) { Text("Add token") }
                }
            }
            if (hasToken) {
                TextButton(onClick = { confirmRemove = true }, modifier = Modifier.padding(0.dp)) {
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

    if (dialog) {
        GitHubTokenDialog(onDismiss = { dialog = false }, onSaved = { dialog = false; c.importer.syncGitHub() })
    }
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
