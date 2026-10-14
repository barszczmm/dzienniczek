package io.github.barszczmm.dzienniczek.notifications

import io.github.barszczmm.dzienniczek.session.SessionStorage
import io.github.barszczmm.dzienniczek.session.StudentSession
import io.github.barszczmm.dzienniczek.settings.BackgroundCheck
import io.github.barszczmm.dzienniczek.ui.model.AttendanceEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** A new grade, exam, homework, note, announcement or attendance entry. [text] may contain HTML. */
data class JournalUpdate(
    val key: String,
    val check: BackgroundCheck,
    val studentName: String,
    val title: String,
    val text: String
)

/**
 * Looks for new grades, exams, homework, notes, announcements and attendance entries of
 * every logged-in student (Librus and eduVulcan).
 *
 * Like [MessageChecker], the first check of a kind only remembers what is there, so
 * turning a kind on doesn't produce notifications about old entries.
 */
class JournalChecker(
    private val seenItems: SeenItems,
    private val sessionStorage: SessionStorage
) {
    private val mutex = Mutex()

    suspend fun check(checks: Set<BackgroundCheck>, pacer: RequestPacer = RequestPacer()): List<JournalUpdate> = mutex.withLock {
        val kinds = checks - BackgroundCheck.MESSAGES
        if (kinds.isEmpty()) return@withLock emptyList()

        val result = mutableListOf<JournalUpdate>()
        val students = sessionStorage.loadStudents().filter { it.isEnabled }
        // Every student × kind in a new random order on each check, so the requests
        // don't follow the same pattern every time.
        val steps = students.flatMap { student -> kinds.map { kind -> student to kind } }.shuffled()
        for ((student, kind) in steps) {
            pacer.pause()
            runCatching { result += checkStudent(student, kind) }.onFailure { it.printStackTrace() }
        }
        // Librus refreshes expired tokens on the fly – keep the new ones.
        for (student in students) {
            val librus = student.librus ?: continue
            runCatching { sessionStorage.updateLibrusTokens(student.id, librus.portalToken, librus.apiToken) }
        }
        result
    }

    private suspend fun checkStudent(student: StudentSession, kind: BackgroundCheck): List<JournalUpdate> {
        val account = student.account
        val api = student.api
        val restUrl = account.unit.restUrl
        val pupilId = account.pupil.id
        val name = "${account.pupil.firstName} ${account.pupil.surname}".trim()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val items: List<Item> = when (kind) {
            BackgroundCheck.MESSAGES -> emptyList()

            BackgroundCheck.GRADES -> {
                val period = account.periods?.firstOrNull { it.current == true }
                    ?: account.periods?.lastOrNull()
                    ?: return emptyList()
                api.getGrades(restUrl = restUrl, unitId = account.unit.id, pupilId = pupilId, periodId = period.id)
                    .map { grade ->
                        Item(
                            id = grade.key,
                            title = "Nowa ocena: ${grade.content} – ${grade.column.subject.name}",
                            text = listOfNotNull(
                                grade.column.name.ifBlank { null },
                                grade.column.weight.takeIf { it > 0 }?.let { "waga $it" },
                                grade.creator.displayName.ifBlank { null },
                                grade.comment.ifBlank { null }
                            ).joinToString(" · ")
                        )
                    }
            }

            BackgroundCheck.EXAMS ->
                api.getExams(restUrl = restUrl, pupilId = pupilId, dateFrom = today, dateTo = today.plus(AHEAD_DAYS, DateTimeUnit.DAY))
                    .map { exam ->
                        Item(
                            id = exam.key,
                            title = "${exam.type}: ${exam.subject.name}, ${exam.deadline.short()}",
                            text = exam.content
                        )
                    }

            BackgroundCheck.HOMEWORK ->
                api.getHomework(restUrl = restUrl, pupilId = pupilId, dateFrom = today, dateTo = today.plus(AHEAD_DAYS, DateTimeUnit.DAY))
                    .map { hw ->
                        Item(
                            id = hw.key,
                            title = "Zadanie domowe: ${hw.subject.name}, na ${hw.deadline.short()}",
                            text = hw.content
                        )
                    }

            BackgroundCheck.NOTES ->
                api.getNotes(restUrl = restUrl, pupilId = pupilId)
                    .map { note ->
                        Item(
                            id = note.key,
                            title = (if (note.positive) "Nowa pochwała" else "Nowa uwaga") +
                                (note.category?.name?.let { ": $it" } ?: ""),
                            text = note.content
                        )
                    }

            BackgroundCheck.ANNOUNCEMENTS ->
                api.getAnnouncements(restUrl = restUrl, unitId = account.unit.id, pupilId = pupilId)
                    .map { announcement ->
                        Item(
                            id = announcement.id.toString(),
                            title = "Ogłoszenie: ${announcement.title}",
                            text = announcement.content
                        )
                    }

            // Only recent days: downloading the whole year of lessons is slow on eduVulcan.
            BackgroundCheck.ATTENDANCE ->
                api.getAttendance(account, today.minus(ATTENDANCE_BACK_DAYS, DateTimeUnit.DAY), today)
                    .map { entry -> Item(id = entry.id, title = entry.title(), text = entry.details()) }
        }

        val sourceKey = "${kind.name.lowercase()}:${student.id}"
        val newIds = seenItems.diffAndRemember(sourceKey, items.map { it.id })
        val fresh = items.filter { it.id in newIds }
        if (fresh.isEmpty()) return emptyList()

        // Several new entries at once (e.g. a whole day of absences) go into one notification.
        if (fresh.size > MAX_SEPARATE) {
            return listOf(
                JournalUpdate(
                    key = "$sourceKey:${fresh.joinToString(",") { it.id }.hashCode()}",
                    check = kind,
                    studentName = name,
                    title = "${kind.label}: ${newEntries(fresh.size)}",
                    text = fresh.joinToString("\n") { it.title }
                )
            )
        }
        return fresh.map {
            JournalUpdate(key = "$sourceKey:${it.id}", check = kind, studentName = name, title = it.title, text = it.text)
        }
    }

    private data class Item(val id: String, val title: String, val text: String)

    private fun LocalDate.short(): String = "$day.${month.number}"

    /** "4 nowe wpisy", "5 nowych wpisów", "22 nowe wpisy". */
    private fun newEntries(n: Int): String =
        if (n % 10 in 2..4 && n % 100 !in 12..14) "$n nowe wpisy" else "$n nowych wpisów"

    private fun AttendanceEntry.title(): String =
        "${typeName.ifBlank { "Frekwencja" }}: ${subject.ifBlank { "lekcja" }}, ${date.short()}"

    private fun AttendanceEntry.details(): String = listOfNotNull(
        lessonNumber?.let { "Lekcja $it" + (time?.let { t -> " ($t)" } ?: "") },
        teacher.ifBlank { null }
    ).joinToString(" · ")

    private companion object {
        /** How far ahead new exams and homework are looked for. */
        const val AHEAD_DAYS = 60
        const val ATTENDANCE_BACK_DAYS = 14
        const val MAX_SEPARATE = 3
    }
}
