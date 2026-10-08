package io.github.barszczmm.dzienniczek.api.librus

import io.github.barszczmm.dzienniczek.api.librus.models.LibrusAccountsResponse
import io.github.barszczmm.dzienniczek.api.librus.models.LibrusMeResponse
import io.github.barszczmm.dzienniczek.api.librus.models.LibrusSynergiaAccount
import io.github.barszczmm.dzienniczek.api.librus.models.LibrusTokenResponse
import io.github.barszczmm.dzienniczek.api.librus.models.api.*
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.http.Parameters
import io.ktor.client.request.forms.submitForm
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Re-authenticates a Librus student when its API token expires: first with a fresh API
 * token from the portal token, and when that one expired too, by logging in again with
 * the stored e-mail and password. [onRefreshed] receives the new portal and API tokens.
 */
class LibrusTokenRefresher(
    val email: String,
    val password: String,
    val synergiaLogin: String,
    val onRefreshed: suspend (portalToken: String, apiToken: String) -> Unit = { _, _ -> }
)

class DzienniczekLibrusApi(
    private val httpClient: HttpClient,
    var portalAccessToken: String? = null,
    var apiAccessToken: String? = null,
    var tokenRefresher: LibrusTokenRefresher? = null
) {
    private val refreshMutex = Mutex()

    private suspend fun apiRequest(post: Boolean, url: String): HttpResponse {
        val token = apiAccessToken
        return if (post) {
            httpClient.post(url) { header("Authorization", "Bearer $token") }
        } else {
            httpClient.get(url) { header("Authorization", "Bearer $token") }
        }
    }

    /** Request to api.librus.pl; on an expired token refreshes it once and retries. */
    private suspend fun apiCall(post: Boolean, url: String): String {
        val tokenUsed = apiAccessToken
        val response = apiRequest(post, url)
        val body = response.bodyAsText()
        if (!isAuthError(response.status, body) || tokenRefresher == null) return body

        refreshTokens(tokenUsed)
        return apiRequest(post, url).bodyAsText()
    }

    private suspend fun apiGet(url: String): String = apiCall(post = false, url = url)

    private suspend fun apiPost(url: String): String = apiCall(post = true, url = url)

    private suspend fun portalGet(url: String): String =
        httpClient.get(url) {
            header("Authorization", "Bearer $portalAccessToken")
            header("X-Requested-With", LibrusConstants.HEADER)
        }.bodyAsText()

    private fun isAuthError(status: HttpStatusCode, body: String): Boolean =
        status == HttpStatusCode.Unauthorized ||
            (status == HttpStatusCode.Forbidden && "Token" in body) ||
            "TokenIsExpired" in body ||
            "Access token is invalid" in body

    /**
     * Gets a new API token. [expiredToken] is the token that just failed: when another
     * coroutine already replaced it, nothing has to be done.
     */
    suspend fun refreshTokens(expiredToken: String? = apiAccessToken) = refreshMutex.withLock {
        val refresher = tokenRefresher ?: return@withLock
        if (apiAccessToken != expiredToken) return@withLock

        val fromPortal = runCatching { getFreshApiToken(refresher.synergiaLogin) }.getOrNull()
        val newApiToken = fromPortal ?: run {
            val newPortalToken = LibrusLoginHelper().login(refresher.email, refresher.password).accessToken
            portalAccessToken = newPortalToken
            getFreshApiToken(refresher.synergiaLogin)
        }
        apiAccessToken = newApiToken
        refresher.onRefreshed(portalAccessToken ?: "", newApiToken)
    }

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    suspend fun getMe(): LibrusMeResponse {
        val responseText = apiGet("${LibrusConstants.API_URL}/Me")
        return json.decodeFromString(responseText)
    }

    /**
     * Gets accounts associated with the portal account.
     * Uses Portal API (portal.librus.pl/api)
     */
    suspend fun getSynergiaAccounts(): List<LibrusSynergiaAccount> {
        val responseText = portalGet("https://portal.librus.pl/api/v3/SynergiaAccounts")
        return json.decodeFromString<LibrusAccountsResponse>(responseText).accounts
    }

    /**
     * Exchanges portal token for API token for a specific synergia account.
     */
    suspend fun getFreshApiToken(accountLogin: String): String {
        val responseText = portalGet("https://portal.librus.pl/api/v3/SynergiaAccounts/fresh/$accountLogin")
        val obj = json.parseToJsonElement(responseText).jsonObject
        return obj["accessToken"]?.jsonPrimitive?.content ?: error("Failed to get fresh API token")
    }

    suspend fun getLuckyNumber(): Int {
        val responseText = apiGet("${LibrusConstants.API_URL}/LuckyNumbers")
        val obj = json.parseToJsonElement(responseText).jsonObject
        return obj["LuckyNumber"]?.jsonObject?.get("LuckyNumber")?.jsonPrimitive?.int ?: 0
    }

    suspend fun getAutoLoginToken(): String {
        val responseText = apiPost("${LibrusConstants.API_URL}/AutoLoginToken")
        val obj = json.parseToJsonElement(responseText).jsonObject
        return obj["Token"]?.jsonPrimitive?.content ?: error("Failed to get auto login token")
    }

    // ------------------------------------------------------------------
    // Messages from the Synergia web interface (free, unlike the mobile API)
    // ------------------------------------------------------------------

    /** Separate client with a cookie jar – Synergia pages work on a session cookie. */
    private val synergiaClient: HttpClient by lazy {
        HttpClient {
            followRedirects = true
            install(HttpCookies)
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
        }
    }
    private var synergiaLoggedIn = false
    private val synergiaMutex = Mutex()

    private fun messagesFolder(tab: io.github.barszczmm.dzienniczek.viewmodel.MessageTab): String = when (tab) {
        io.github.barszczmm.dzienniczek.viewmodel.MessageTab.RECEIVED -> "5"
        io.github.barszczmm.dzienniczek.viewmodel.MessageTab.SENT -> "6"
        io.github.barszczmm.dzienniczek.viewmodel.MessageTab.DELETED -> "7"
    }

    /** Opens a Synergia web session using a one-time token from the API. */
    private suspend fun synergiaLogin(token: String? = null) {
        val loginToken = token ?: getAutoLoginToken()
        synergiaClient.get("https://synergia.librus.pl/loguj/token/$loginToken/przenies/uczen/widok/wiadomosci/5").bodyAsText()
        synergiaLoggedIn = true
    }

    /** GETs a Synergia page; logs in (again) when there is no session or it expired. */
    private suspend fun synergiaPage(url: String, isValid: (String) -> Boolean): String = synergiaMutex.withLock {
        if (!synergiaLoggedIn) synergiaLogin()
        var html = synergiaClient.get(url).bodyAsText()
        if (!isValid(html)) {
            synergiaLogin()
            html = synergiaClient.get(url).bodyAsText()
        }
        html
    }

    /** Message list of the given folder, with sender, date, unread and attachment flags. */
    suspend fun getSynergiaMessages(tab: io.github.barszczmm.dzienniczek.viewmodel.MessageTab): List<io.github.barszczmm.dzienniczek.ui.model.UiMessage> {
        val html = synergiaPage("https://synergia.librus.pl/wiadomosci/${messagesFolder(tab)}") { "decorated stretch" in it }
        return parseSynergiaMessageList(html)
    }

    /** Variant used with a token obtained by the caller (background checker). */
    suspend fun getSynergiaMessages(token: String, tab: io.github.barszczmm.dzienniczek.viewmodel.MessageTab): List<io.github.barszczmm.dzienniczek.ui.model.UiMessage> {
        synergiaMutex.withLock { synergiaLogin(token) }
        return getSynergiaMessages(tab)
    }

    private fun parseSynergiaMessageList(html: String): List<io.github.barszczmm.dzienniczek.ui.model.UiMessage> {
        val doc = Ksoup.parse(html)
        val messages = mutableListOf<io.github.barszczmm.dzienniczek.ui.model.UiMessage>()

        doc.select(".decorated.stretch tbody > tr").forEach { tr ->
            val cells = tr.select("td")
            if (cells.size < 5) return@forEach

            val link = cells[3].select("a").first() ?: return@forEach
            val url = link.attr("href")
            // URL looks like /wiadomosci/1/5/12345/f0
            val id = "/([0-9]+)/f".toRegex().find(url)?.groupValues?.get(1)
                ?: "/([0-9]+)/".toRegex().findAll(url).lastOrNull()?.groupValues?.get(1)
                ?: url.substringAfterLast("/")
            val subject = link.text().trim()
            val sender = cells[2].text().substringBefore("(").trim()
            val dateStr = cells[4].text().trim()
            val isUnread = tr.hasClass("unread") || cells.any { it.attr("style").contains("bold") }
            val hasAttachment = cells[1].select("img").isNotEmpty()

            messages.add(
                io.github.barszczmm.dzienniczek.ui.model.UiMessage(
                    id = id,
                    title = subject,
                    senderOrRecipient = sender.ifBlank { "Nieznany" },
                    date = parseSynergiaDate(dateStr),
                    isUnread = isUnread,
                    hasAttachments = hasAttachment
                )
            )
        }
        return messages
    }

    private fun parseSynergiaDate(text: String): LocalDateTime? = try {
        val parts = text.trim().split(" ")
        val d = LocalDate.parse(parts[0])
        val t = LocalTime.parse(parts.getOrNull(1) ?: "00:00")
        LocalDateTime(d.year, d.month, d.day, t.hour, t.minute)
    } catch (e: Exception) {
        null
    }

    /** Full message from Synergia: subject, sender, date and plain-text content. */
    suspend fun getSynergiaMessageDetails(id: String): LibrusWebMessage {
        val isMessagePage: (String) -> Boolean = { "container-message-content" in it }
        var html = synergiaPage("https://synergia.librus.pl/wiadomosci/1/5/$id/f0", isMessagePage)
        if (!isMessagePage(html)) {
            html = synergiaPage("https://synergia.librus.pl/wiadomosci/1/6/$id/f0", isMessagePage)
        }
        val doc = Ksoup.parse(html)

        // Header rows: "Nadawca" / "Odbiorca", "Temat", "Wysłano"
        val header = mutableMapOf<String, String>()
        doc.select("tr").forEach { tr ->
            val cells = tr.select("td, th")
            if (cells.size >= 2) {
                val label = cells[0].text().trim().removeSuffix(":").lowercase()
                if (label in listOf("nadawca", "odbiorca", "temat", "wysłano", "data wysłania")) {
                    header.putIfAbsent(label, cells[1].text().trim())
                }
            }
        }

        val contentHtml = doc.select(".container-message-content").html()
        return LibrusWebMessage(
            subject = header["temat"],
            sender = (header["nadawca"] ?: header["odbiorca"])?.substringBefore("(")?.trim(),
            date = header["wysłano"] ?: header["data wysłania"],
            content = htmlToText(contentHtml)
        )
    }

    /**
     * Replies to a received message through the Synergia web interface, the same way a
     * browser does: opens the message, follows its "Odpowiedz" action, fills the reply
     * form (keeping all fields Synergia pre-fills, like recipient, subject and quote) and
     * submits it with the "Wyślij" button.
     */
    suspend fun replySynergiaMessage(id: String, text: String) {
        val messageUrl = "https://synergia.librus.pl/wiadomosci/1/5/$id/f0"
        val messageHtml = synergiaPage(messageUrl) { "container-message-content" in it }
        val messageDoc = Ksoup.parse(messageHtml)

        // 1. Open the reply form – a link/button, or a form on the message page.
        val replyControl = messageDoc.select("a, input, button").firstOrNull { el ->
            val label = (el.text() + " " + el.attr("value") + " " + el.attr("title")).lowercase()
            "odpowiedz" in label
        } ?: throw IllegalStateException("Librus: nie znaleziono przycisku „Odpowiedz”")

        val ownForm = replyControl.closest("form")
        val linkUrl = replyControl.attr("href").takeIf { it.isNotBlank() && !it.startsWith("javascript") }
            ?: Regex("""['"](/wiadomosci/[^'"]+)['"]""").find(replyControl.attr("onclick"))?.groupValues?.get(1)
        val (replyPageUrl, replyHtml) = synergiaMutex.withLock {
            if (linkUrl != null) {
                val url = resolveSynergiaUrl(messageUrl, linkUrl)
                url to synergiaClient.get(url).bodyAsText()
            } else if (ownForm != null) {
                val params = formParameters(ownForm, submitter = replyControl)
                val url = resolveSynergiaUrl(messageUrl, ownForm.attr("action"))
                url to synergiaClient.submitForm(url, params).bodyAsText()
            } else {
                throw IllegalStateException("Librus: nie udało się otworzyć formularza odpowiedzi")
            }
        }

        // 2. Fill and submit the reply form.
        val replyDoc = Ksoup.parse(replyHtml)
        val form = replyDoc.select("form").firstOrNull { it.selectFirst("textarea") != null }
            ?: throw IllegalStateException("Librus: nie znaleziono formularza odpowiedzi")
        val textarea = form.selectFirst("textarea")!!
        val quoted = textarea.wholeText().trim()
        val body = if (quoted.isBlank()) text else text.trimEnd() + "\n\n" + quoted
        val sendButton = form.select("input[type=submit], button[type=submit], button:not([type]), input[type=button]")
            .firstOrNull { el -> ("wyślij" in (el.text() + " " + el.attr("value")).lowercase()) }
        val params = formParameters(form, submitter = sendButton, overrides = mapOf(textarea.attr("name") to body))

        val result = synergiaMutex.withLock {
            synergiaClient.submitForm(resolveSynergiaUrl(replyPageUrl, form.attr("action")), params).bodyAsText()
        }
        val resultText = Ksoup.parse(result).text().lowercase()
        val sent = "wysłan" in resultText && "nie została wysłana" !in resultText
        if (!sent) {
            val error = Ksoup.parse(result).select(".red, .error, .alert, .warning").text().take(200)
            throw IllegalStateException("Librus: wiadomość prawdopodobnie nie została wysłana. ${error.ifBlank { "" }}".trim())
        }
    }

    /** All values a browser would submit for [form] (hidden/text inputs, checked boxes, selects, textareas). */
    private fun formParameters(
        form: com.fleeksoft.ksoup.nodes.Element,
        submitter: com.fleeksoft.ksoup.nodes.Element?,
        overrides: Map<String, String> = emptyMap()
    ): Parameters = Parameters.build {
        form.select("input, textarea, select").forEach { el ->
            val name = el.attr("name")
            if (name.isBlank() || el.hasAttr("disabled") || name in overrides) return@forEach
            when (el.tagName().lowercase()) {
                "textarea" -> append(name, el.wholeText())
                "select" -> {
                    val selected = el.select("option[selected]").toList().ifEmpty { el.select("option").toList().take(1) }
                    selected.forEach { append(name, it.attr("value").ifBlank { it.text() }) }
                }
                else -> when (el.attr("type").lowercase()) {
                    "submit", "button", "image", "reset", "file" -> Unit
                    "checkbox", "radio" -> if (el.hasAttr("checked")) append(name, el.attr("value").ifBlank { "on" })
                    else -> append(name, el.attr("value"))
                }
            }
        }
        overrides.forEach { (k, v) -> if (k.isNotBlank()) append(k, v) }
        submitter?.attr("name")?.takeIf { it.isNotBlank() }?.let { append(it, submitter.attr("value")) }
    }

    private fun resolveSynergiaUrl(base: String, href: String): String = when {
        href.isBlank() -> base
        href.startsWith("http") -> href
        href.startsWith("/") -> "https://synergia.librus.pl$href"
        else -> base.substringBeforeLast("/") + "/" + href
    }

    /** Plain-text content of a message (used by the background checker). */
    suspend fun getSynergiaMessageContent(id: String): String = getSynergiaMessageDetails(id).content

    private fun htmlToText(html: String): String {
        if (html.isBlank()) return ""
        val withBreaks = html
            .replace(Regex("\\s*[\\r\\n]+\\s*"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</div>"), "\n")
        return Ksoup.parse(withBreaks).body().wholeText()
            .replace('\u00A0', ' ')
            .lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    suspend fun getGrades(): List<LibrusGrade> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Grades")
        return json.decodeFromString<LibrusGradesResponse>(responseText).grades
    }

    suspend fun getGradeCategories(): List<LibrusGradeCategory> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Grades/Categories")
        return json.decodeFromString<LibrusGradeCategoriesResponse>(responseText).categories
    }

    suspend fun getAverages(): Map<String, String> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Grades/Averages")
        return try {
            val obj = json.parseToJsonElement(responseText).jsonObject
            val averages = obj["Averages"]?.jsonObject ?: return emptyMap()
            averages.mapValues { it.value.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getHomework(): List<LibrusHomeWorkAssignment> {
        val responseText = apiGet("${LibrusConstants.API_URL}/HomeWorkAssignments")
        return json.decodeFromString<LibrusHomeWorkAssignmentsResponse>(responseText).assignments ?: emptyList()
    }

    suspend fun getEvents(): List<LibrusEvent> {
        val responseText = apiGet("${LibrusConstants.API_URL}/HomeWorks")
        return json.decodeFromString<LibrusHomeWorksResponse>(responseText).homeWorks ?: emptyList()
    }

    suspend fun getEventCategories(): List<LibrusIdNameReference> {
        val responseText = apiGet("${LibrusConstants.API_URL}/HomeWorks/Categories")
        return json.decodeFromString<LibrusHomeWorksCategoriesResponse>(responseText).categories
    }

    suspend fun getTimetable(weekStart: LocalDate): Map<String, List<List<LibrusLesson>>> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Timetables?weekStart=$weekStart")
        return json.decodeFromString<LibrusTimetableResponse>(responseText).timetable
    }

    suspend fun getMessages(): List<LibrusMessage> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Messages")
        return json.decodeFromString<LibrusMessagesResponse>(responseText).messages ?: emptyList()
    }

    suspend fun getMessageContent(id: Int): String {
        val responseText = apiGet("${LibrusConstants.API_URL}/Messages/$id")
        val obj = json.parseToJsonElement(responseText).jsonObject
        return obj["Message"]?.jsonObject?.get("Content")?.jsonPrimitive?.content ?: ""
    }

    /** Librus returns errors as {"Status":"Error","Code":"...","Message":"..."}. */
    private fun throwIfApiError(responseText: String) {
        val obj = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull() ?: return
        val status = obj["Status"]?.jsonPrimitive?.content
        if (status.equals("Error", ignoreCase = true) || obj.containsKey("Code") && obj.containsKey("Message")) {
            val code = obj["Code"]?.jsonPrimitive?.content
            val message = obj["Message"]?.jsonPrimitive?.content ?: code
            // The school has this module switched off / the account has no access to it.
            if (code in listOf("AccessDeny", "NotesIsNotActive", "Request is denied") ||
                message?.contains("not have access", ignoreCase = true) == true
            ) {
                throw LibrusFeatureUnavailableException(message ?: "AccessDeny")
            }
            throw IllegalStateException("Librus: ${message ?: "błąd API"}")
        }
    }

    /** Raw JSON of an API resource (relative to api.librus.pl/2.0), for diagnostics. */
    suspend fun rawGet(resource: String): String = apiGet("${LibrusConstants.API_URL}/$resource")

    suspend fun getAttendances(): List<LibrusAttendance> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Attendances")
        return json.decodeFromString<LibrusAttendancesResponse>(responseText).attendances
            ?: run { throwIfApiError(responseText); emptyList() }
    }

    suspend fun getAttendanceTypes(): List<LibrusAttendanceType> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Attendances/Types")
        return json.decodeFromString<LibrusAttendanceTypesResponse>(responseText).types.orEmpty()
    }

    suspend fun getLessonRefs(): List<LibrusLessonRef> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Lessons")
        return json.decodeFromString<LibrusLessonsResponse>(responseText).lessons.orEmpty()
    }

    suspend fun getTextGrades(): List<LibrusTextGrade> {
        val responseText = apiGet("${LibrusConstants.API_URL}/BaseTextGrades")
        return json.decodeFromString<LibrusTextGradesResponse>(responseText).grades
            ?: run { throwIfApiError(responseText); emptyList() }
    }

    suspend fun getDescriptiveGrades(): List<LibrusDescriptiveGrade> {
        val responseText = apiGet("${LibrusConstants.API_URL}/DescriptiveGrades")
        return json.decodeFromString<LibrusDescriptiveGradesResponse>(responseText).grades
            ?: run { throwIfApiError(responseText); emptyList() }
    }

    /** Skills of /DescriptiveGrades (the grades link to DescriptiveGrades/Skills/{id}). */
    suspend fun getDescriptiveGradeSkills(): List<LibrusNamedColorItem> {
        val responseText = apiGet("${LibrusConstants.API_URL}/DescriptiveGrades/Skills")
        return json.decodeFromString<LibrusSkillsResponse>(responseText).skills.orEmpty()
    }

    /** All comments of descriptive grades; falls back to fetching the given ids one by one. */
    suspend fun getDescriptiveGradeComments(ids: List<Long>): List<LibrusGradeComment> {
        if (ids.isEmpty()) return emptyList()
        val bulk = runCatching {
            json.decodeFromString<LibrusGradeCommentsResponse>(
                apiGet("${LibrusConstants.API_URL}/DescriptiveGrades/Comments")
            ).comments
        }.getOrNull()
        if (!bulk.isNullOrEmpty()) return bulk
        // Some accounts only allow single comments; ids are joined with commas like in the API.
        return ids.chunked(50).flatMap { chunk ->
            runCatching {
                val response = json.decodeFromString<LibrusGradeCommentsResponse>(
                    apiGet("${LibrusConstants.API_URL}/DescriptiveGrades/Comments/${chunk.joinToString(",")}")
                )
                response.comments ?: listOfNotNull(response.comment)
            }.getOrDefault(emptyList())
        }
    }

    suspend fun getTextGradeCategories(): List<LibrusNamedColorItem> {
        val responseText = apiGet("${LibrusConstants.API_URL}/TextGrades/Categories")
        return json.decodeFromString<LibrusTextGradeCategoriesResponse>(responseText).categories.orEmpty()
    }

    suspend fun getDescriptiveSkills(): List<LibrusNamedColorItem> {
        val responseText = apiGet("${LibrusConstants.API_URL}/DescriptiveTextGrades/Skills")
        return json.decodeFromString<LibrusSkillsResponse>(responseText).skills.orEmpty()
    }

    suspend fun getNotices(): List<LibrusNotice> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Notes")
        val notices = json.decodeFromString<LibrusNoticesResponse>(responseText).notices
        return notices ?: run { throwIfApiError(responseText); emptyList() }
    }

    suspend fun getSchoolNotices(): List<LibrusSchoolNotice> {
        val responseText = apiGet("${LibrusConstants.API_URL}/SchoolNotices")
        val notices = json.decodeFromString<LibrusSchoolNoticesResponse>(responseText).schoolNotices
        return notices ?: run { throwIfApiError(responseText); emptyList() }
    }

    suspend fun getNoticeCategories(): List<LibrusNoticeCategory> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Notes/Categories")
        return json.decodeFromString<LibrusNoticeCategoriesResponse>(responseText).categories.orEmpty()
    }

    suspend fun getSubjects(): List<LibrusSubject> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Subjects")
        return json.decodeFromString<LibrusSubjectsResponse>(responseText).subjects
    }

    suspend fun getUsers(): List<LibrusUser> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Users")
        return json.decodeFromString<LibrusUsersResponse>(responseText).users
    }

    suspend fun getClassrooms(): List<LibrusClassroom> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Classrooms")
        return json.decodeFromString<LibrusClassroomsResponse>(responseText).classrooms
    }
}

/** A message read from the Synergia web interface. */
data class LibrusWebMessage(
    val subject: String?,
    val sender: String?,
    val date: String?,
    val content: String
)

/** Thrown when the school does not make a module (e.g. notes) available in Librus. */
class LibrusFeatureUnavailableException(message: String) : Exception(message)
