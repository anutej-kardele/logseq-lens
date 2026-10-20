package com.anutej.logseqpocket.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anutej.logseqpocket.Fixtures
import com.anutej.logseqpocket.Fixtures.block
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.importer.ImportService
import com.anutej.logseqpocket.data.importer.ImportSource
import com.anutej.logseqpocket.data.repo.NotesRepository
import com.anutej.logseqpocket.data.repo.StudyRepository
import com.anutej.logseqpocket.domain.cards.ReviewResult
import com.anutej.logseqpocket.domain.cards.StudySession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyRepositoryTest {
    private lateinit var db: PocketDatabase
    private lateinit var study: StudyRepository

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketDatabase::class.java).build()
        study = StudyRepository(db, clock = { 99L })
        importText(Fixtures.synthetic)
    }

    @After fun tearDown() = db.close()

    private suspend fun importText(text: String) {
        val r = ImportService(db).import(ImportSource(ImportOrigin.File, "t") { Fixtures.stream(text) })
        assertTrue(r is ImportOutcome.Imported)
    }

    @Test fun decksAreDerivedFromTagsWithCounts() = runTest {
        val decks = study.overview.first().decks.associateBy { it.key }
        assertEquals(5, decks.getValue(StudyRepository.ALL).size)
        val leet = decks.getValue(StudyRepository.TAG_PREFIX + Fixtures.LEET)
        assertEquals("LeetCode Deck", leet.title)
        assertEquals("leetcode", leet.tagName)
        assertEquals(3, leet.size) // Two Sum, Prefix Sum Pair (both decks), Unanswered prompt
        assertEquals(2, decks.getValue(StudyRepository.TAG_PREFIX + Fixtures.ALGO).size)
        assertEquals(1, decks.getValue(StudyRepository.OTHER).size)
    }

    @Test fun recordingReviewsUpdatesProgress() = runTest {
        val card = study.cards().values.first { it.blockUuid == block(4) }
        study.record(card, ReviewResult.Again, card.answerHash)
        study.record(card, ReviewResult.Know, card.answerHash)
        val p = study.progress(card.cardKey)!!
        assertEquals(2, p.reviewCount)
        assertEquals(1, p.knowCount)
        assertEquals(1, p.againCount)
        assertEquals("Know", p.lastResult)
        assertEquals(99L, p.lastReviewedAt)
        val all = study.overview.first().decks.first()
        assertEquals(1, all.known)
        assertEquals(1, all.reviewed)
    }

    @Test fun progressSurvivesRefreshThroughTheRepository() = runTest {
        val card = study.cards().values.first { it.blockUuid == block(9) }
        study.record(card, ReviewResult.Know, card.answerHash)
        importText(Fixtures.synthetic.replace(block(9), block(900)).replace("\"graph\": \"Synthetic\"", "\"graph\": \"Refreshed\""))
        assertEquals("Refreshed", NotesRepository(db).meta.first()!!.graph)
        val again = study.cards().values.first { it.blockUuid == block(900) }
        assertEquals(card.cardKey, again.cardKey)
        assertEquals(1, study.progress(again.cardKey)!!.knowCount)
    }

    @Test fun sessionsPersistAndRestore() = runTest {
        val keys = study.cards().keys.toList()
        val s = StudySession.start(StudyRepository.ALL, keys, shuffle = true).mark(ReviewResult.Again)
        study.saveSession(s)
        val restored = study.loadSession(StudyRepository.ALL)!!
        assertEquals(s.order, restored.order)
        assertEquals(1, restored.position)
        assertEquals(ReviewResult.Again, restored.results[s.order[0]])
        assertTrue(restored.shuffled)
    }

    @Test fun cardAnswerSubtreePreservesCodeAndNesting() = runTest {
        val notes = NotesRepository(db)
        val card = study.cards().values.first { it.blockUuid == block(4) }
        val front = notes.blocksByUuid(listOf(card.blockUuid)).getValue(card.blockUuid)
        val answer = notes.subtree(front)
        assertEquals(listOf(5, 6, 7, 8).map(::block), answer.map { it.uuid })
        assertEquals(listOf(1, 1, 1, 2), answer.map { it.depth })
        assertTrue(answer[1].content.contains("    Map<Integer, Integer> seen = new HashMap<>();"))
        val noAnswer = study.cards().values.first { it.blockUuid == block(15) }
        assertEquals(0, notes.subtree(notes.blocksByUuid(listOf(noAnswer.blockUuid)).getValue(noAnswer.blockUuid)).size)
    }
}
