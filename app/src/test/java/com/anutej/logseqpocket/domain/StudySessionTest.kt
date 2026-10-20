package com.anutej.logseqpocket.domain

import com.anutej.logseqpocket.domain.cards.ReviewResult
import com.anutej.logseqpocket.domain.cards.StudySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StudySessionTest {
    private val keys = listOf("a", "b", "c", "d")

    @Test fun walksThroughCardsAndRecordsResults() {
        var s = StudySession.start("all", keys, shuffle = false)
        assertEquals("a", s.current)
        s = s.mark(ReviewResult.Know).mark(ReviewResult.Again)
        assertEquals("c", s.current)
        assertEquals(2, s.answered)
        assertEquals(1, s.knownCount)
        assertEquals(listOf("b"), s.againKeys)
        s = s.previous()
        assertEquals("b", s.current)
        s = s.mark(ReviewResult.Know) // changing an answer replaces it
        assertEquals(0, s.againCount)
        s = s.next().next()
        assertTrue(s.isFinished)
        assertNull(s.current)
        assertEquals(s, s.next()) // can't run past the end
    }

    @Test fun previousStopsAtStart() {
        val s = StudySession.start("all", keys, shuffle = false)
        assertEquals(0, s.previous().position)
    }

    @Test fun shuffleIsAPermutation() {
        val s = StudySession.start("all", keys, shuffle = true, random = Random(7))
        assertEquals(keys.toSet(), s.order.toSet())
        assertNotEquals(keys, StudySession.start("all", (1..30).map { "$it" }, shuffle = true, random = Random(1)).order)
    }

    @Test fun restartClearsResults() {
        val s = StudySession.start("all", keys, shuffle = false).mark(ReviewResult.Again).restart()
        assertEquals(0, s.position)
        assertEquals(0, s.answered)
    }

    @Test fun retainDropsCardsRemovedByReimport() {
        val s = StudySession.start("all", keys, shuffle = false).mark(ReviewResult.Know).mark(ReviewResult.Again)
        val r = s.retain(setOf("b", "c", "d"))
        assertEquals(listOf("b", "c", "d"), r.order)
        assertEquals("c", r.current)
        assertFalse("a" in r.results)
        assertEquals(1, r.againCount)
    }
}
