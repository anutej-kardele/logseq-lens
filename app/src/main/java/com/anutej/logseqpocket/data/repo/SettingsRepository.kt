package com.anutej.logseqpocket.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anutej.logseqpocket.ui.theme.ReadingSize
import com.anutej.logseqpocket.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(val themeMode: ThemeMode = ThemeMode.System, val readingSize: ReadingSize = ReadingSize.Medium)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            readingSize = p[READING]?.let { runCatching { ReadingSize.valueOf(it) }.getOrNull() } ?: ReadingSize.Medium,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setReadingSize(size: ReadingSize) = store.edit { it[READING] = size.name }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val READING = stringPreferencesKey("reading_size")
    }
}
