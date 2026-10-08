package io.github.barszczmm.dzienniczek.api.librus.models.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibrusGradesResponse(
    @SerialName("Grades")
    val grades: List<LibrusGrade>
)

@Serializable
data class LibrusGrade(
    @SerialName("Id") val id: Long,
    @SerialName("Grade") val grade: String,
    @SerialName("Date") val date: String? = null,
    @SerialName("AddDate") val addDate: String? = null,
    @SerialName("Semester") val semester: Int,
    @SerialName("IsConstituent") val isConstituent: Boolean = false,
    @SerialName("IsSemester") val isSemester: Boolean = false,
    @SerialName("IsSemesterProposition") val isSemesterProposition: Boolean = false,
    @SerialName("IsFinal") val isFinal: Boolean = false,
    @SerialName("IsFinalProposition") val isFinalProposition: Boolean = false,
    @SerialName("Subject") val subject: LibrusIdNameReference,
    @SerialName("Category") val category: LibrusIdReference,
    @SerialName("AddedBy") val addedBy: LibrusIdReference? = null,
    @SerialName("Comments") val comments: List<LibrusIdReference>? = null
)

@Serializable
data class LibrusGradeCategoriesResponse(
    @SerialName("Categories")
    val categories: List<LibrusGradeCategory>
)

@Serializable
data class LibrusGradeCategory(
    @SerialName("Id") val id: Long,
    @SerialName("Name") val name: String,
    @SerialName("Weight") val weight: Float? = null,
    @SerialName("CountToTheAverage") val countToTheAverage: Boolean = false,
    @SerialName("Color") val color: LibrusColorReference? = null
)

@Serializable
data class LibrusIdReference(
    @SerialName("Id") val id: Long
)

@Serializable
data class LibrusIdNameReference(
    @SerialName("Id") val id: Long,
    @SerialName("Name") val name: String? = null
)

@Serializable
data class LibrusColorReference(
    @SerialName("Id") val id: Int? = null,
    @SerialName("RGB") val rgb: String? = null
)

// ---------------------------------------------------------------------------
// Descriptive grades (early education, e.g. classes 1–3)
// ---------------------------------------------------------------------------

/** /BaseTextGrades – free-text descriptive grades. */
@Serializable
data class LibrusTextGradesResponse(
    @SerialName("Grades") val grades: List<LibrusTextGrade>? = null
)

@Serializable
data class LibrusTextGrade(
    @SerialName("Id") val id: Long,
    @SerialName("Grade") val grade: String? = null,
    @SerialName("Semester") val semester: Int? = null,
    @SerialName("AddDate") val addDate: String? = null,
    @SerialName("Subject") val subject: LibrusIdReference? = null,
    @SerialName("AddedBy") val addedBy: LibrusIdReference? = null,
    @SerialName("Category") val category: LibrusIdReference? = null,
    @SerialName("Skill") val skill: LibrusIdReference? = null
)

/** /DescriptiveGrades – symbol grades (A, B, C… or a colour scale) for skills. */
@Serializable
data class LibrusDescriptiveGradesResponse(
    @SerialName("Grades") val grades: List<LibrusDescriptiveGrade>? = null
)

@Serializable
data class LibrusDescriptiveGrade(
    @SerialName("Id") val id: Long,
    @SerialName("Map") val map: String? = null,
    /** Position on the school's scale (e.g. 3 = highest/blue, 2 = yellow…). */
    @SerialName("Grade") val scaleIndex: Int? = null,
    @SerialName("RealGradeValue") val realGradeValue: String? = null,
    @SerialName("Phrase") val phrase: String? = null,
    @SerialName("Semester") val semester: Int? = null,
    @SerialName("AddDate") val addDate: String? = null,
    @SerialName("Subject") val subject: LibrusIdReference? = null,
    @SerialName("AddedBy") val addedBy: LibrusIdReference? = null,
    @SerialName("Skill") val skill: LibrusIdReference? = null,
    @SerialName("Comments") val comments: List<LibrusIdReference>? = null
)

/** Teacher's comment of a descriptive grade – often holds the letter (A, B…). */
@Serializable
data class LibrusGradeComment(
    @SerialName("Id") val id: Long,
    @SerialName("Text") val text: String? = null
)

@Serializable
data class LibrusGradeCommentsResponse(
    @SerialName("Comments") val comments: List<LibrusGradeComment>? = null,
    @SerialName("Comment") val comment: LibrusGradeComment? = null
)

/** Categories of text grades (/TextGrades/Categories) and skills (/DescriptiveTextGrades/Skills). */
@Serializable
data class LibrusNamedColorItem(
    @SerialName("Id") val id: Long,
    @SerialName("Name") val name: String? = null,
    @SerialName("Color") val color: LibrusColorReference? = null
)

@Serializable
data class LibrusTextGradeCategoriesResponse(
    @SerialName("Categories") val categories: List<LibrusNamedColorItem>? = null
)

@Serializable
data class LibrusSkillsResponse(
    @SerialName("Skills") val skills: List<LibrusNamedColorItem>? = null
)
