package com.anutej.logseqpocket.ui.util

import com.anutej.logseqpocket.data.importer.GraphMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object Format {
    private val long = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    private val dayMonth = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun date(day: Int?): LocalDate? = day?.let(GraphMapper::dayToDate)

    fun journalLong(day: Int?, fallback: String): String = date(day)?.format(long) ?: fallback
    fun journalShort(day: Int?, fallback: String): String = date(day)?.format(dayMonth) ?: fallback
    fun monthHeader(day: Int?): String = date(day)?.format(month) ?: "Undated"

    fun relativeDay(day: Int?, today: LocalDate = LocalDate.now()): String? {
        val d = date(day) ?: return null
        return when (ChronoUnit.DAYS.between(d, today)) {
            0L -> "Today"
            1L -> "Yesterday"
            in 2..6 -> "${ChronoUnit.DAYS.between(d, today)} days ago"
            else -> null
        }
    }

    fun timestamp(ms: Long?): String =
        ms?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateTime) } ?: "—"

    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    /** "just now", "12 min ago", "3 h ago", "yesterday", or a short date — for sync freshness. */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - ms).coerceAtLeast(0)
        val minutes = diff / 60_000
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 && day == today -> "${minutes / 60} h ago"
            day == today.minusDays(1) -> "yesterday"
            else -> day.format(shortDate)
        }
    }

    fun count(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"
}
