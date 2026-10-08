package io.github.barszczmm.dzienniczek.api.librus

import io.github.barszczmm.dzienniczek.api.hebe.DzienniczekApi
import io.github.barszczmm.dzienniczek.api.hebe.DzienniczekHttpClient
import io.github.barszczmm.dzienniczek.api.hebe.credentials.ICredential
import io.github.barszczmm.dzienniczek.api.hebe.models.*
import io.github.barszczmm.dzienniczek.api.librus.models.LibrusSynergiaAccount
import io.github.barszczmm.dzienniczek.api.librus.models.api.*
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

private object LibrusDummyCredential : ICredential {
    override val type: String = "librus"
    override var restUrl: String? = "librus"
    override val certificate: String = ""
    override val privateKey: String = ""
    override val fingerprint: String = ""
    override val notificationToken: String? = null
    override val deviceId: String = "librus"
    override val deviceOs: String = "Android"
    override val deviceModel: String = "LibrusClient"
}

class DzienniczekLibrusAdapterApi(
    val librusApi: DzienniczekLibrusApi,
    var currentSynergiaAccount: LibrusSynergiaAccount,
    httpClient: HttpClient
) : DzienniczekApi(
    credential = LibrusDummyCredential,
    dzienniczekHttpClient = DzienniczekHttpClient(
        credential = LibrusDummyCredential,
        identity = io.github.barszczmm.dzienniczek.api.hebe.hebeIdentity,
        httpClient = httpClient
    )
) {
    private var cachedSubjects: List<LibrusSubject>? = null
    private var cachedUsers: List<LibrusUser>? = null
    private var cachedClassrooms: List<LibrusClassroom>? = null
    private var cachedGradeCategories: List<LibrusGradeCategory>? = null
    private var cachedEventCategories: List<LibrusIdNameReference>? = null
    private var cachedNoticeCategories: List<LibrusNoticeCategory>? = null

    private suspend fun getSubjects(): List<LibrusSubject> {
        return cachedSubjects ?: librusApi.getSubjects().also { cachedSubjects = it }
    }

    private suspend fun getUsers(): List<LibrusUser> {
        return cachedUsers ?: librusApi.getUsers().also { cachedUsers = it }
    }

    private suspend fun getClassrooms(): List<LibrusClassroom> {
        return cachedClassrooms ?: librusApi.getClassrooms().also { cachedClassrooms = it }
    }

    private suspend fun getGradeCategories(): List<LibrusGradeCategory> {
        return cachedGradeCategories ?: librusApi.getGradeCategories().also { cachedGradeCategories = it }
    }

    private suspend fun getEventCategories(): List<LibrusIdNameReference> {
        return cachedEventCategories ?: librusApi.getEventCategories().also { cachedEventCategories = it }
    }

    private suspend fun getNoticeCategories(): List<LibrusNoticeCategory> {
        return cachedNoticeCategories ?: librusApi.getNoticeCategories().also { cachedNoticeCategories = it }
    }

    override suspend fun getLuckyNumber(
        restUrl: String,
        pupilId: Int,
        constituentUnitId: Int,
        day: LocalDate
    ): LuckyNumber? {
        val number = runCatching { librusApi.getLuckyNumber() }.getOrDefault(0)
        return if (number > 0) LuckyNumber(day = day, number = number) else null
    }

    override suspend fun getGrades(
        restUrl: String,
        unitId: Int,
        pupilId: Int,
        periodId: Int,
        lastSyncDate: LocalDateTime,
        lastId: Int,
        pageSize: Int
    ): List<Grade> {
        val grades = librusApi.getGrades()
        val categories = getGradeCategories()
        val subjects = getSubjects()

        // Descriptive grades used in early education. Many schools don't use them
        // (or deny access), so failures here must not break the regular grades.
        val textGrades = runCatching { librusApi.getTextGrades() }.getOrDefault(emptyList())
        val descriptiveGrades = runCatching { librusApi.getDescriptiveGrades() }.getOrDefault(emptyList())
        val textCategories = if (textGrades.isEmpty()) emptyList()
            else runCatching { librusApi.getTextGradeCategories() }.getOrDefault(emptyList())
        val skills = if (textGrades.isEmpty() && descriptiveGrades.isEmpty()) emptyList()
            else runCatching { librusApi.getDescriptiveSkills() }.getOrDefault(emptyList())

        return LibrusMapper.mapGrades(grades, categories, subjects) +
            LibrusMapper.mapTextGrades(textGrades, textCategories, skills, subjects) +
            LibrusMapper.mapDescriptiveGrades(descriptiveGrades, skills, subjects)
    }

    override suspend fun getGradesAverages(
        restUrl: String,
        unitId: Int,
        pupilId: Int,
        periodId: Int,
        lastId: Int,
        pageSize: Int
    ): List<GradeAverage> {
        val averages = runCatching { librusApi.getAverages() }.getOrDefault(emptyMap())
        val subjects = getSubjects()
        return LibrusMapper.mapAverages(averages, subjects)
    }

    override suspend fun getSchedule(
        restUrl: String,
        pupilId: Int,
        dateFrom: LocalDate,
        dateTo: LocalDate,
        lastId: Int,
        pageSize: Int,
        lastSyncDate: LocalDateTime
    ): List<Schedule> {
        val timetable = librusApi.getTimetable(dateFrom)
        val subjects = getSubjects()
        val users = getUsers()
        val classrooms = getClassrooms()
        return LibrusMapper.mapSchedule(timetable, subjects, users, classrooms)
    }

    override suspend fun getExams(
        restUrl: String,
        pupilId: Int,
        dateFrom: LocalDate,
        dateTo: LocalDate,
        lastSyncDate: LocalDateTime,
        lastId: Int,
        pageSize: Int
    ): List<Exam> {
        val events = librusApi.getEvents()
        val subjects = getSubjects()
        val categories = getEventCategories()
        val users = getUsers()
        return LibrusMapper.mapExams(events, subjects, categories, users)
    }

    override suspend fun getHomework(
        restUrl: String,
        pupilId: Int,
        dateFrom: LocalDate,
        dateTo: LocalDate,
        lastSyncDate: LocalDateTime,
        lastId: Int,
        pageSize: Int
    ): List<Homework> {
        val homework = librusApi.getHomework()
        val subjects = getSubjects()
        val users = getUsers()
        return LibrusMapper.mapHomework(homework, subjects, users)
    }

    override suspend fun getNotes(
        restUrl: String,
        pupilId: Int,
        lastId: Int,
        pageSize: Int,
        lastSyncDate: LocalDateTime
    ): List<Note> {
        val notices = librusApi.getNotices()
        val categories = runCatching { getNoticeCategories() }.getOrDefault(emptyList())
        val users = runCatching { getUsers() }.getOrDefault(emptyList())
        return LibrusMapper.mapNotices(notices, categories, users)
    }

    override suspend fun getAnnouncements(
        restUrl: String,
        unitId: Int,
        pupilId: Int,
        view: Int,
        lastId: Int,
        pageSize: Int
    ): List<Announcement> {
        val notices = librusApi.getSchoolNotices()
        val users = runCatching { getUsers() }.getOrDefault(emptyList())
        return LibrusMapper.mapAnnouncements(notices, users)
    }
}
