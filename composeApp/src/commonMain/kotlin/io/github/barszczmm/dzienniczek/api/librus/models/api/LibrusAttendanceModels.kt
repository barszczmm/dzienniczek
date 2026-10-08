package io.github.barszczmm.dzienniczek.api.librus.models.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibrusAttendancesResponse(
    @SerialName("Attendances") val attendances: List<LibrusAttendance>? = null
)

@Serializable
data class LibrusAttendance(
    // Librus sometimes returns ids like "t12345" – kept as text.
    @SerialName("Id") val id: kotlinx.serialization.json.JsonPrimitive,
    @SerialName("Lesson") val lesson: LibrusIdReference? = null,
    @SerialName("Date") val date: String? = null,
    @SerialName("AddDate") val addDate: String? = null,
    @SerialName("LessonNo") val lessonNo: Int? = null,
    @SerialName("Semester") val semester: Int? = null,
    @SerialName("Type") val type: LibrusIdReference? = null,
    @SerialName("AddedBy") val addedBy: LibrusIdReference? = null
)

@Serializable
data class LibrusAttendanceTypesResponse(
    @SerialName("Types") val types: List<LibrusAttendanceType>? = null
)

@Serializable
data class LibrusAttendanceType(
    @SerialName("Id") val id: Long,
    @SerialName("Name") val name: String? = null,
    @SerialName("Short") val short: String? = null,
    @SerialName("Standard") val standard: Boolean? = null,
    /** 1 = absent, 2 = late, 3 = excused absence, 4 = released, 100 = present. */
    @SerialName("StandardType") val standardType: LibrusIdReference? = null
)

@Serializable
data class LibrusLessonsResponse(
    @SerialName("Lessons") val lessons: List<LibrusLessonRef>? = null
)

/** /Lessons – which subject and teacher a lesson (class + subject) is. */
@Serializable
data class LibrusLessonRef(
    @SerialName("Id") val id: Long,
    @SerialName("Teacher") val teacher: LibrusIdReference? = null,
    @SerialName("Subject") val subject: LibrusIdReference? = null
)
