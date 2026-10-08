package io.github.barszczmm.dzienniczek.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Parses the date formats the journals use: "2026-10-08T07:36:42", "2026-10-08 07:36:42",
 * with milliseconds and/or a time zone ("…Z", "…+02:00"), or just "2026-10-08".
 * Dates with a time zone are converted to the phone's local time.
 */
fun parseJournalDateTime(text: String?): LocalDateTime? {
    val t = text?.trim()?.replace(' ', 'T') ?: return null
    if (t.isEmpty()) return null
    if (Regex("(Z|[+-]\\d{2}:?\\d{2})$").containsMatchIn(t) && t.length > 10) {
        runCatching { return Instant.parse(t).toLocalDateTime(TimeZone.currentSystemDefault()) }
    }
    runCatching { return LocalDateTime.parse(t.take(19)) }
    runCatching { return LocalDateTime.parse(t.take(16)) }
    runCatching { return LocalDate.parse(t.take(10)).let { LocalDateTime(it.year, it.month, it.day, 0, 0) } }
    return null
}

/** "8.10.2026, 07:36" (time omitted when it is midnight, i.e. unknown). */
fun formatJournalDateTime(dt: LocalDateTime): String {
    val date = "${dt.date.day}.${dt.date.month.number}.${dt.date.year}"
    return if (dt.hour == 0 && dt.minute == 0) date
    else "$date, ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

/**
 * eduVulcan mailbox names look like "Leśniak Anna [AL] - P - (SP1)"; shows just
 * "Leśniak Anna". Several recipients separated by ";" or "," are shortened one by one.
 */
fun displayMailboxName(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw.split(';').joinToString(", ") { part ->
        io.github.barszczmm.dzienniczek.api.prometheus.models.VulcanMailboxName.parse(part)?.name
            ?.takeIf { it.isNotBlank() } ?: part.trim()
    }
}
