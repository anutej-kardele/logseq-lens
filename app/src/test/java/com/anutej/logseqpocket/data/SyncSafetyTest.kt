package com.anutej.logseqpocket.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anutej.logseqpocket.Fixtures
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.importer.ImportService
import com.anutej.logseqpocket.data.importer.ImportSource
import com.anutej.logseqpocket.data.json.ImportError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSafetyTest {
    private lateinit var db: PocketDatabase
    private lateinit var service: ImportService

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PocketDatabase::class.java).build()
        service = ImportService(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun import(text: String, force: Boolean = false) =
        service.import(ImportSource(ImportOrigin.GitHub, "test", null) { Fixtures.stream(text) }, force)

    private suspend fun snapshot(): List<Any> {
        val pages = db.notesDao().observePages().first().map { it.page.uuid to it.page.title }.sortedBy { it.first }
        return listOf(db.notesDao().meta()!!.contentSha256, pages, db.notesDao().blockCount(),
            db.notesDao().observeTags().first().map { it.tagKey }.sorted(),
            db.studyDao().observeAllCards().first().map { it.cardKey }.sorted())
    }

    // Test E — a sync whose export has a duplicated page identity leaves the previous notes untouched.
    @Test fun failedSyncKeepsPreviousNotes() = runTest {
        assertTrue(import(Fixtures.synthetic) is ImportOutcome.Imported)
        val before = snapshot()
        val broken = Fixtures.edit { root ->
            val dup = Json.parseToJsonElement("""{"uuid":"${Fixtures.ALGO}","kind":"page","title":"Untitled page (10000000)",
                "blocks":[{"uuid":"74000000-0000-4000-8000-000000000001","content":"x","order":"00000000"}]}""")
            root["pages"] = JsonArray(root.getValue("pages").jsonArray + dup)
        }
        val r = import(broken) as ImportOutcome.Failed
        assertTrue(r.error is ImportError.Invalid)
        assertEquals(before, snapshot())
    }

    // Test F — syncing the same valid export repeatedly gives identical, duplicate-free results.
    @Test fun repeatedSyncIsDeterministic() = runTest {
        assertTrue(import(Fixtures.synthetic) is ImportOutcome.Imported)
        val first = snapshot()
        assertEquals(ImportOutcome.Unchanged, import(Fixtures.synthetic))
        assertTrue(import(Fixtures.synthetic, force = true) is ImportOutcome.Imported)
        assertTrue(import(Fixtures.synthetic, force = true) is ImportOutcome.Imported)
        assertEquals(first, snapshot())
        assertEquals(8, db.notesDao().pageCount())
        assertEquals(30, db.notesDao().blockCount())
    }
}
