package com.anutej.logseqpocket.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anutej.logseqpocket.Fixtures
import com.anutej.logseqpocket.Fixtures.block
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.db.ReviewProgressEntity
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.importer.ImportService
import com.anutej.logseqpocket.data.importer.ImportSource
import com.anutej.logseqpocket.data.json.ImportError
import com.anutej.logseqpocket.data.repo.SearchRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ImportServiceTest {
    private lateinit var context: Context
    private lateinit var db: PocketDatabase
    private lateinit var service: ImportService
    private var now = 1_000L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PocketDatabase::class.java).build()
        service = ImportService(db, clock = { now })
    }

    @After fun tearDown() = db.close()

    private fun source(text: String, label: String = "test.json") =
        ImportSource(ImportOrigin.File, label) { Fixtures.stream(text) }

    private suspend fun importOk(text: String = Fixtures.synthetic): ImportOutcome.Imported {
        val r = service.import(source(text))
        assertTrue("expected success, got $r", r is ImportOutcome.Imported)
        return r as ImportOutcome.Imported
    }

    @Test fun importStoresPagesBlocksTagsAndCards() = runTest {
        val r = importOk()
        assertEquals(8, r.stats.pages)
        assertEquals(30, r.stats.blocks)
        assertEquals(5, r.stats.cards)
        val meta = db.notesDao().meta()!!
        assertEquals("Synthetic", meta.graph)
        assertEquals(1, meta.schemaVersion)
        assertEquals("65.22", meta.logseqSchema)
        assertEquals("test.json", meta.originLabel)
        val journals = db.notesDao().observeJournals().first()
        assertEquals(listOf(20260108, 20260107, 20260106, 20260105), journals.map { it.page.journalDay })
        assertEquals("09:00", journals.last().preview)
        val blocks = db.notesDao().observeBlocks(Fixtures.J1).first()
        assertEquals(block(1), blocks.first().uuid)
        assertEquals((0 until blocks.size).toList(), blocks.map { it.preorder })
    }

    @Test fun tagQueriesAndBacklinks() = runTest {
        importOk()
        val leet = db.notesDao().observeBlocksForTag(Fixtures.LEET).first()
        assertEquals(setOf(block(4), block(11), block(15), block(16)), leet.map { it.blockUuid }.toSet())
        val backlinks = db.notesDao().observeBacklinks(Fixtures.ALG_NOTES).first()
        assertEquals(listOf(block(3)), backlinks.map { it.blockUuid })
        val pagesTaggedAlgo = db.notesDao().observePagesForTag(Fixtures.ALGO).first()
        assertEquals(listOf(Fixtures.ALG_NOTES), pagesTaggedAlgo.map { it.page.uuid })
    }

    @Test fun malformedImportKeepsPreviousDataset() = runTest {
        importOk()
        val before = db.notesDao().meta()!!
        val r = service.import(source("{ definitely not json"))
        assertTrue((r as ImportOutcome.Failed).error is ImportError.MalformedJson)
        assertEquals(before, db.notesDao().meta())
        assertEquals(30, db.notesDao().blockCount())
    }

    @Test fun unsupportedSchemaKeepsPreviousDataset() = runTest {
        importOk()
        val r = service.import(source(Fixtures.edit { it["schemaVersion"] = JsonPrimitive(7) }))
        assertTrue((r as ImportOutcome.Failed).error is ImportError.UnsupportedSchema)
        assertEquals(8, db.notesDao().pageCount())
    }

    @Test fun unreadableSourceKeepsPreviousDataset() = runTest {
        importOk()
        val r = service.import(ImportSource(ImportOrigin.File, "x") { throw IOException("gone") })
        assertTrue((r as ImportOutcome.Failed).error is ImportError.Unreadable)
        assertEquals(8, db.notesDao().pageCount())
    }

    @Test fun identicalFileIsDetectedAsUnchanged() = runTest {
        importOk()
        now = 5_000L
        assertEquals(ImportOutcome.Unchanged, service.import(source(Fixtures.synthetic)))
        assertEquals(5_000L, db.notesDao().meta()!!.importedAt)
        assertTrue(service.import(source(Fixtures.synthetic), force = true) is ImportOutcome.Imported)
    }

    @Test fun newImportReplacesOldContentCompletely() = runTest {
        importOk()
        val smaller = Fixtures.edit(Fixtures.synthetic.replace("Algorithms Notes", "Renamed Notes")) {
            it["graph"] = JsonPrimitive("Second")
        }
        importOk(smaller)
        assertEquals("Second", db.notesDao().meta()!!.graph)
        val titles = db.notesDao().observePages().first().map { it.page.title }
        assertTrue("Renamed Notes" in titles)
        assertTrue("Algorithms Notes" !in titles)
        assertEquals(1, SearchRepository(db).search("Renamed").pages.size)
    }

    @Test fun fullTextSearchFindsBlocksAndPages() = runTest {
        importOk()
        val search = SearchRepository(db)
        val prefix = search.search("prefix")
        assertEquals(
            setOf(block(11), block(12), block(30)),
            prefix.blocks.map { it.blockUuid }.toSet(),
        )
        assertEquals(listOf(block(5)), search.search("complements").blocks.map { it.blockUuid })
        assertEquals(listOf(Fixtures.ALG_NOTES), search.search("algorithms").pages.map { it.page.uuid })
        // Symbol-heavy queries fall back to substring matching.
        assertEquals(listOf(block(6)), search.search("new HashMap<>()").blocks.map { it.blockUuid })
        assertTrue(search.search("   ").blocks.isEmpty())
        assertTrue(search.search("zzzz-nothing").blocks.isEmpty())
        // FTS special characters in user input never throw.
        search.search("\"unbalanced OR (")
        search.search("prefix*")
    }

    @Test fun reviewProgressSurvivesReimportWithShiftedUuids() = runTest {
        importOk()
        val card = db.studyDao().observeAllCards().first().first { it.blockUuid == block(4) }
        db.studyDao().upsertProgress(ReviewProgressEntity(card.cardKey, 3, 2, 1, "know", 42L, block(4), null))
        val shifted = Fixtures.synthetic.replace(block(4), block(600))
        importOk(shifted)
        val p = db.studyDao().progress(card.cardKey)
        assertNotNull(p)
        assertEquals(3, p!!.reviewCount)
        assertEquals(block(600), p.lastBlockUuid)
    }

    @Test fun progressReattachesWhenPromptIsEditedInPlace() = runTest {
        importOk()
        val oldKey = db.studyDao().observeAllCards().first().first { it.blockUuid == block(4) }.cardKey
        db.studyDao().upsertProgress(ReviewProgressEntity(oldKey, 2, 2, 0, "know", 7L, null, null))
        importOk(Fixtures.synthetic.replace("Paris", "Paris, France")) // refreshes identity hints
        val r = importOk(Fixtures.synthetic.replace("Two Sum - LC 1", "Two Sum (LC 1)").replace("Paris", "Paris, France"))
        assertEquals(1, r.stats.progressReattached)
        val newKey = db.studyDao().observeAllCards().first().first { it.blockUuid == block(4) }.cardKey
        assertNull(db.studyDao().progress(oldKey))
        assertEquals(2, db.studyDao().progress(newKey)!!.knowCount)
    }

    @Test fun progressForRemovedCardsIsKeptAsOrphan() = runTest {
        importOk()
        val key = db.studyDao().observeAllCards().first().first { it.blockUuid == block(13) }.cardKey
        db.studyDao().upsertProgress(ReviewProgressEntity(key, 1, 1, 0, "know", 1L, block(13), "x"))
        importOk(Fixtures.synthetic.replace("Capital of France", "Capital of Spain").replace("\"Paris\"", "\"Madrid\""))
        assertEquals(1, db.studyDao().observeOrphanCount().first())
        assertNotNull(db.studyDao().progress(key))
        importOk(Fixtures.synthetic) // the card comes back → progress re-attaches by key
        assertEquals(0, db.studyDao().observeOrphanCount().first())
    }

    @Test fun notesPersistOnDiskAcrossDatabaseReopen() = runTest {
        // Simulates closing the app and reopening it offline: data must come from local storage only.
        val name = "offline-test.db"
        context.deleteDatabase(name)
        val disk = Room.databaseBuilder(context, PocketDatabase::class.java, name).build()
        assertTrue(ImportService(disk).import(source(Fixtures.synthetic)) is ImportOutcome.Imported)
        disk.close()
        val reopened = Room.databaseBuilder(context, PocketDatabase::class.java, name).build()
        assertEquals("Synthetic", reopened.notesDao().meta()!!.graph)
        assertEquals(8, reopened.notesDao().pageCount())
        assertEquals(1, SearchRepository(reopened).search("Paris").blocks.size)
        reopened.close()
        context.deleteDatabase(name)
    }
}
