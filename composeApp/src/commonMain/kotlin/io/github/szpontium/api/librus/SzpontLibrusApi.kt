package io.github.szpontium.api.librus

import io.github.szpontium.api.librus.models.LibrusAccountsResponse
import io.github.szpontium.api.librus.models.LibrusMeResponse
import io.github.szpontium.api.librus.models.LibrusSynergiaAccount
import io.github.szpontium.api.librus.models.LibrusTokenResponse
import io.github.szpontium.api.librus.models.api.*
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
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

class SzpontLibrusApi(
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

    suspend fun getSynergiaMessages(token: String, tab: io.github.szpontium.viewmodel.MessageTab): List<io.github.szpontium.ui.model.UiMessage> {
        val folder = when (tab) {
            io.github.szpontium.viewmodel.MessageTab.RECEIVED -> "5"
            io.github.szpontium.viewmodel.MessageTab.SENT -> "6"
            io.github.szpontium.viewmodel.MessageTab.DELETED -> "7"
        }
        val loginUrl = "https://synergia.librus.pl/loguj/token/$token/przenies/uczen/widok/wiadomosci/$folder"
        
        // This request will set cookies and follow redirects
        val loginResponse = httpClient.get(loginUrl)
        val html = loginResponse.bodyAsText()
        
        // If the login redirect doesn't lead us directly to the list, try fetching it explicitly
        val finalHtml = if (!html.contains("decorated stretch")) {
             httpClient.get("https://synergia.librus.pl/wiadomosci/$folder").bodyAsText()
        } else html

        val doc = Ksoup.parse(finalHtml)
        val messages = mutableListOf<io.github.szpontium.ui.model.UiMessage>()
        
        doc.select(".decorated.stretch tbody > tr").forEach { tr ->
            val cells = tr.select("td")
            if (cells.size < 5) return@forEach
            
            val link = cells[3].select("a").first() ?: return@forEach
            val url = link.attr("href")
            // URL might be /wiadomosci/1/5/12345/f0 or similar
            val id = "/([0-9]+)/".toRegex().find(url)?.groupValues?.get(1) ?: url.substringAfterLast("/")
            val subject = link.text().trim()
            val sender = cells[2].text().substringBefore("(").trim()
            val dateStr = cells[4].text().trim()
            val isRead = !tr.hasClass("unread") && cells[2].attr("style").isBlank()
            val hasAttachment = cells[1].select("img").isNotEmpty()
            
            val date = try {
                val parts = dateStr.split(" ")
                val d = LocalDate.parse(parts[0])
                val t = LocalTime.parse(parts[1])
                LocalDateTime(d.year, d.month, d.day, t.hour, t.minute)
            } catch (e: Exception) {
                null
            }
            
            messages.add(
                io.github.szpontium.ui.model.UiMessage(
                    id = id,
                    title = subject,
                    senderOrRecipient = sender,
                    date = date,
                    isUnread = !isRead,
                    hasAttachments = hasAttachment
                )
            )
        }
        
        return messages
    }

    suspend fun getSynergiaMessageContent(id: String): String {
        // Try received messages first, then sent if it fails or returns empty
        val receivedUrl = "https://synergia.librus.pl/wiadomosci/1/5/$id/f0"
        val sentUrl = "https://synergia.librus.pl/wiadomosci/1/6/$id/f0"
        
        var response = httpClient.get(receivedUrl).bodyAsText()
        var doc = Ksoup.parse(response)
        var content = doc.select(".container-message-content").html().trim()
        
        if (content.isBlank()) {
            response = httpClient.get(sentUrl).bodyAsText()
            doc = Ksoup.parse(response)
            content = doc.select(".container-message-content").html().trim()
        }

        // Strip HTML tags for simple view, or keep if we want rich text
        return content.replace("<br>", "\n").replace("<[^>]*>".toRegex(), "").trim()
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

    suspend fun getNotices(): List<LibrusNotice> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Notes")
        return json.decodeFromString<LibrusNoticesResponse>(responseText).notices
    }

    suspend fun getNoticeCategories(): List<LibrusNoticeCategory> {
        val responseText = apiGet("${LibrusConstants.API_URL}/Notes/Categories")
        return json.decodeFromString<LibrusNoticeCategoriesResponse>(responseText).categories
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
