package com.anutej.logseqpocket

import android.content.Context
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.importer.ImportCoordinator
import com.anutej.logseqpocket.data.importer.ImportService
import com.anutej.logseqpocket.data.repo.NotesRepository
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
    val importer by lazy { ImportCoordinator(appContext, appScope, importService) }
    val notes by lazy { NotesRepository(database) }
    val settings by lazy { SettingsRepository(appContext) }
    val cardCount: Flow<Int> get() = database.studyDao().observeCardCount()
}
