package io.github.barszczmm.dzienniczek.ui.model

import kotlinx.datetime.LocalDate

/** Kinds of attendance entries the app shows (presence itself is not shown). */
enum class AttendanceKind {
    ABSENT,
    ABSENT_EXCUSED,
    LATE,
    LATE_EXCUSED,
    /** Released / absent for school reasons. */
    RELEASED
}

/** One absence / late arrival on one lesson, common for Librus and eduVulcan. */
data class AttendanceEntry(
    val id: String,
    val date: LocalDate,
    /** Lesson in the day (1 = first lesson). */
    val lessonNumber: Int?,
    /** Lesson time, e.g. "09:10–09:55", when the journal provides it. */
    val time: String? = null,
    val subject: String,
    val kind: AttendanceKind,
    /** Name of the type as the journal calls it, e.g. "Nieobecność usprawiedliwiona". */
    val typeName: String,
    val teacher: String,
    /** 1 or 2 (school term). */
    val semester: Int
)
