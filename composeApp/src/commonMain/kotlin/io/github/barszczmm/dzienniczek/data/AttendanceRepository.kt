package io.github.barszczmm.dzienniczek.data

import io.github.barszczmm.dzienniczek.api.hebe.models.schoolYearStart
import io.github.barszczmm.dzienniczek.session.ApiSession
import io.github.barszczmm.dzienniczek.ui.model.AttendanceEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Absences / late arrivals of the active student for the current school year.
 * Kept in memory per student, so the start screen and the attendance screen share one
 * download (fetching a whole year of lessons from eduVulcan is not cheap).
 */
class AttendanceRepository(private val session: ApiSession) {
    private val cache = mutableMapOf<String, List<AttendanceEntry>>()
    private val mutex = Mutex()

    suspend fun get(forceRefresh: Boolean = false): List<AttendanceEntry> = mutex.withLock {
        val student = session.activeStudent.value ?: return@withLock emptyList()
        if (!forceRefresh) cache[student.id]?.let { return@withLock it }
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val entries = student.api.getAttendance(student.account, schoolYearStart(today), today)
            .sortedWith(compareByDescending<AttendanceEntry> { it.date }.thenBy { it.lessonNumber ?: 0 })
        cache[student.id] = entries
        entries
    }
}
