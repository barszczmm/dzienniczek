package io.github.barszczmm.dzienniczek.api.librus.models.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibrusNoticesResponse(
    // Missing (or null) when the student has no notes.
    @SerialName("Notes")
    val notices: List<LibrusNotice>? = null
)

@Serializable
data class LibrusNotice(
    @SerialName("Id") val id: Long,
    @SerialName("Text") val text: String? = null,
    @SerialName("Date") val date: String? = null,
    @SerialName("Positive") val positive: Int? = null,
    @SerialName("Category") val category: LibrusIdReference? = null,
    @SerialName("Teacher") val teacher: LibrusIdReference? = null
)

@Serializable
data class LibrusNoticeCategoriesResponse(
    @SerialName("Categories")
    val categories: List<LibrusNoticeCategory>? = null
)

@Serializable
data class LibrusNoticeCategory(
    @SerialName("Id") val id: Long,
    @SerialName("CategoryName") val name: String? = null
)

@Serializable
data class LibrusSchoolNoticesResponse(
    // Missing (or null) when there are no announcements.
    @SerialName("SchoolNotices")
    val schoolNotices: List<LibrusSchoolNotice>? = null
)

/** School announcement ("Ogłoszenia") from /SchoolNotices. */
@Serializable
data class LibrusSchoolNotice(
    @SerialName("Id") val id: String,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("Subject") val subject: String? = null,
    @SerialName("Content") val content: String? = null,
    @SerialName("AddedBy") val addedBy: LibrusIdReference? = null,
    @SerialName("CreationDate") val creationDate: String? = null,
    @SerialName("WasRead") val wasRead: Boolean? = null
)
