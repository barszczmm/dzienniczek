package io.github.barszczmm.dzienniczek.platform

import kotlinx.datetime.LocalDate

actual val isAddToCalendarSupported: Boolean = false

actual fun addToCalendar(title: String, description: String, date: LocalDate) {}
