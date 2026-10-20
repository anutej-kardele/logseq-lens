package com.anutej.logseqpocket.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anutej.logseqpocket.data.sync.DEFAULT_SOURCE
import com.anutej.logseqpocket.data.sync.GitHubSourceConfig
import com.anutej.logseqpocket.ui.theme.ReadingSize
import com.anutej.logseqpocket.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val readingSize: ReadingSize = ReadingSize.Medium,
    val backgroundSync: Boolean = false,
    /** The repo/branch/file the GitHub sync reads. Not secret; the token is stored separately. */
    val githubSource: GitHubSourceConfig = DEFAULT_SOURCE,
    /** `.json` files last seen in the source's folder (for the graph switcher, also offline). */
    val githubFiles: List<String> = emptyList(),
)

class SettingsRepository(context: Context, private val onThemeChanged: () -> Unit = {}) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            readingSize = p[READING]?.let { runCatching { ReadingSize.valueOf(it) }.getOrNull() } ?: ReadingSize.Medium,
            backgroundSync = p[BACKGROUND_SYNC] ?: false,
            githubSource = GitHubSourceConfig(
                owner = p[GH_OWNER] ?: DEFAULT_SOURCE.owner,
                repo = p[GH_REPO] ?: DEFAULT_SOURCE.repo,
                branch = p[GH_BRANCH] ?: DEFAULT_SOURCE.branch,
                path = p[GH_PATH] ?: DEFAULT_SOURCE.path,
            ),
            githubFiles = p[GH_FILES]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
        onThemeChanged()
    }
    suspend fun setReadingSize(size: ReadingSize) = store.edit { it[READING] = size.name }
    suspend fun setBackgroundSync(enabled: Boolean) = store.edit { it[BACKGROUND_SYNC] = enabled }

    suspend fun setGitHubSource(source: GitHubSourceConfig) = store.edit {
        if (it[GH_OWNER] != source.owner || it[GH_REPO] != source.repo || it[GH_BRANCH] != source.branch ||
            (it[GH_PATH] ?: DEFAULT_SOURCE.path).substringBeforeLast('/', "") != source.folder
        ) {
            it.remove(GH_FILES) // the cached file list belonged to another folder
        }
        it[GH_OWNER] = source.owner
        it[GH_REPO] = source.repo
        it[GH_BRANCH] = source.branch
        it[GH_PATH] = source.path
    }

    suspend fun setGitHubFiles(files: List<String>) = store.edit { it[GH_FILES] = files.joinToString("\n") }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val READING = stringPreferencesKey("reading_size")
        val BACKGROUND_SYNC = booleanPreferencesKey("background_sync")
        val GH_OWNER = stringPreferencesKey("github_owner")
        val GH_REPO = stringPreferencesKey("github_repo")
        val GH_BRANCH = stringPreferencesKey("github_branch")
        val GH_PATH = stringPreferencesKey("github_path")
        val GH_FILES = stringPreferencesKey("github_files")
    }
}
