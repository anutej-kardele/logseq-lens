package com.anutej.logseqpocket.domain

import com.anutej.logseqpocket.ui.util.Format
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FormatAgoTest {
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val now = at(2026, 10, 20, 15, 0)

    @Test fun recentTimes() {
        assertEquals("just now", Format.ago(now - 20_000, now))
        assertEquals("12 min ago", Format.ago(now - 12 * 60_000, now))
        assertEquals("3 h ago", Format.ago(at(2026, 10, 20, 11, 30), now))
    }

    @Test fun olderTimes() {
        assertEquals("yesterday", Format.ago(at(2026, 10, 19, 22, 0), now))
        assertEquals(java.time.LocalDate.of(2026, 10, 12).format(java.time.format.DateTimeFormatter.ofPattern("d MMM")),
            Format.ago(at(2026, 10, 12, 9, 0), now))
    }

    @Test fun futureClockSkewIsJustNow() {
        assertEquals("just now", Format.ago(now + 60_000, now))
    }
}
