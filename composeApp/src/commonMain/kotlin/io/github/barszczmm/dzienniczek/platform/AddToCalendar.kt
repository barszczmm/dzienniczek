package io.github.barszczmm.dzienniczek.platform

import kotlinx.datetime.LocalDate

/** Whether [addToCalendar] does anything on this platform (the UI hides the button otherwise). */
expect val isAddToCalendarSupported: Boolean

/** Opens the system "new event" screen with an all-day event on [date], prefilled with [title] and [description]. */
expect fun addToCalendar(title: String, description: String, date: LocalDate)
