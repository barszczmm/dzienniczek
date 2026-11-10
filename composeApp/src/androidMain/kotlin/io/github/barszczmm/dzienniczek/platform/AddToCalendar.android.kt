package io.github.barszczmm.dzienniczek.platform

import android.content.Intent
import android.provider.CalendarContract
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

actual val isAddToCalendarSupported: Boolean = true

/**
 * Uses the calendar app's insert screen, so no calendar permission is needed and the user
 * picks the calendar and confirms. All-day events are expressed in UTC midnights.
 */
actual fun addToCalendar(title: String, description: String, date: LocalDate) {
    val context = appContext ?: return
    val begin = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.Events.DESCRIPTION, description)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // No calendar app installed: nothing to do.
    runCatching { context.startActivity(intent) }
}
