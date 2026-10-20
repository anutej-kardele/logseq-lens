package com.anutej.logseqpocket

import android.content.Context
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.importer.ImportCoordinator
import com.anutej.logseqpocket.data.importer.ImportService
import androidx.room.withTransaction
import com.anutej.logseqpocket.data.repo.NotesRepository
import com.anutej.logseqpocket.data.sync.GitHubSync
import com.anutej.logseqpocket.data.sync.KeystoreTokenStore
import com.anutej.logseqpocket.data.sync.TokenStore
import com.anutej.logseqpocket.data.repo.SearchRepository
import com.anutej.logseqpocket.data.repo.StudyRepository
import com.anutej.logseqpocket.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/** Manual dependency wiring; one instance per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: PocketDatabase by lazy { PocketDatabase.create(appContext) }
    val importService by lazy { ImportService(database) }
    val tokens: TokenStore by lazy { KeystoreTokenStore(appContext) }
    val githubSync by lazy { GitHubSync(database, importService, tokens) }
    val importer by lazy { ImportCoordinator(appContext, appScope, importService, githubSync) }
    val notes by lazy { NotesRepository(database) }
    val settings by lazy { SettingsRepository(appContext) }
    val search by lazy { SearchRepository(database) }
    val study by lazy { StudyRepository(database) }
    val cardCount: Flow<Int> get() = database.studyDao().observeCardCount()

    /** Removes imported notes (not review progress) after user confirmation. */
    suspend fun clearNotes() = database.withTransaction { database.importDao().clearContent() }
}
