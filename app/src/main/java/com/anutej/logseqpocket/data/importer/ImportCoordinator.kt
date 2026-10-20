package com.anutej.logseqpocket.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.anutej.logseqpocket.data.repo.SettingsRepository
import com.anutej.logseqpocket.data.sync.GitHubSourceConfig
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.sync.GitHubSync
import com.anutej.logseqpocket.data.sync.sourceFromKey
import com.anutej.logseqpocket.data.sync.isGraphExportCandidate
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
    private val settings: SettingsRepository,
    private val db: PocketDatabase,
) {
    private companion object {
        // Builds before the configurable source labelled GitHub imports like this.
        const val LEGACY_LABEL = "GitHub anutej-kardele/logseq-backup@master"
        val LEGACY_SOURCE = GitHubSourceConfig("anutej-kardele", "logseq-backup", "master", "data/mobile/activity.json")
    }

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

    /** The last source someone tried to connect to, so a failed attempt doesn't have to be retyped. */
    @Volatile var draftSource: GitHubSourceConfig? = null

    /** "Sync now" / switching graphs: downloads [candidate] (default: the active source). */
    fun syncGitHub(candidate: GitHubSourceConfig? = null, force: Boolean = false) =
        launch("from GitHub") { syncGitHubNow(candidate, force) }

    /**
     * A new source becomes the active one only after its file downloaded and validated; on any failure the
     * previous graph *and* the previous source stay as they were.
     */
    suspend fun syncGitHubNow(candidate: GitHubSourceConfig? = null, force: Boolean = false): ImportOutcome {
        val active = settings.current().githubSource
        val source = candidate ?: active
        val outcome = github.sync(source, force)
        if (outcome is ImportOutcome.Imported || outcome is ImportOutcome.Unchanged) {
            settings.setGitHubSource(source) // always persisted, so the source survives default changes
            if (draftSource == source) draftSource = null
            refreshGitHubFilesNow(source)
        }
        return outcome
    }

    /**
     * Upgrades from builds that kept the source implicit: if none is stored but the current notes came from
     * GitHub, adopt that source ("owner/repo@branch:path" in the dataset's originLabel).
     */
    suspend fun adoptSourceFromDataset() {
        if (settings.hasStoredGitHubSource()) return
        val meta = db.notesDao().meta() ?: return
        if (meta.origin != ImportOrigin.GitHub.name) return
        val label = meta.originLabel
        val source = sourceFromKey(label)
            ?: if (label == LEGACY_LABEL) LEGACY_SOURCE else null
        source?.let { settings.setGitHubSource(it) }
    }

    /** Best-effort refresh of the switcher's file list for [source]'s folder. */
    suspend fun refreshGitHubFilesNow(source: GitHubSourceConfig? = null) {
        val s = source ?: settings.current().githubSource
        github.listJsonFiles(s.owner, s.repo, s.branch, s.folder).onSuccess { files ->
            settings.setGitHubFiles(if (s.path in files || !isGraphExportCandidate(s.path)) files else (files + s.path).sorted())
        }
    }

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
