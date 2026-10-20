package com.anutej.logseqpocket.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.anutej.logseqpocket.data.sync.GitHubSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileNotFoundException

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val label: String) : ImportState
    data class Finished(val outcome: ImportOutcome, val at: Long) : ImportState
}

/** Runs imports in the application scope so they survive navigation and configuration changes. */
class ImportCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: ImportService,
    private val github: GitHubSync,
) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun importUri(uri: Uri, force: Boolean = false) {
        val label = displayName(uri) ?: "selected file"
        run(label, force) {
            ImportSource(ImportOrigin.File, label) {
                context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("file is unavailable")
            }
        }
    }

    fun run(label: String, force: Boolean = false, source: () -> ImportSource) =
        launch(label) { service.import(source(), force) }

    /** Manual "Sync now" from the private GitHub backup. */
    fun syncGitHub(force: Boolean = false) = launch("from GitHub") { github.sync(force) }

    private fun launch(label: String, work: suspend () -> ImportOutcome) {
        if (_state.value is ImportState.Running) return
        _state.value = ImportState.Running(label)
        scope.launch {
            val outcome = work()
            _state.value = ImportState.Finished(outcome, System.currentTimeMillis())
        }
    }

    fun dismiss() {
        if (_state.value is ImportState.Finished) _state.value = ImportState.Idle
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
