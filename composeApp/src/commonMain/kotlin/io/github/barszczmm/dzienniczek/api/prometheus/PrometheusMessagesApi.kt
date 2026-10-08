package io.github.barszczmm.dzienniczek.api.prometheus

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.github.barszczmm.dzienniczek.api.prometheus.models.PrometheusMailbox
import io.github.barszczmm.dzienniczek.api.prometheus.models.PrometheusMessage
import io.github.barszczmm.dzienniczek.api.prometheus.models.PrometheusMessageDetails
import io.github.barszczmm.dzienniczek.api.prometheus.models.PrometheusReplyDetails
import io.github.barszczmm.dzienniczek.api.prometheus.models.PrometheusSendMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parametersOf
import io.ktor.serialization.kotlinx.json.json as ktorJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.thauvin.erik.urlencoder.UrlEncoderUtil
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PrometheusMessagesApi(
    val tenant: String,
    val login: String? = null,
    val password: String? = null,
    private var initialCookies: List<Cookie>? = null
) {
    private val ssoBaseUrl = "https://dziennik-logowanie.vulcan.net.pl"
    private val messagesBaseUrl = "https://wiadomosci.eduvulcan.pl"

    private var antiForgeryToken: String = ""
    private var appGuid: String = ""
    private var isInitialized: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }
    private val cookieStorage = AcceptAllCookiesStorage()

    private val httpClient = HttpClient {
        followRedirects = true

        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
        install(HttpCookies) {
            storage = cookieStorage
        }
        install(ContentNegotiation) {
            ktorJson(json)
        }
        install(UserAgent) {
            agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
        }
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
            }
        }
    }

    suspend fun initialize() {
        if (isInitialized) return

        var currentCookies = initialCookies
        if (currentCookies == null) {
            if (login != null && password != null) {
                val helper = PrometheusLoginHelper()
                val result = helper.login(login, password, "Dzienniczek")
                currentCookies = result.cookies
            } else {
                throw IllegalStateException("Brak ciasteczek i brak danych logowania")
            }
        }

        // Load initial cookies across all EduVulcan domains
        currentCookies.forEach { cookie ->
            val domain = cookie.domain?.removePrefix(".") ?: "eduvulcan.pl"
            cookieStorage.addCookie(Url("https://$domain"), cookie)
            cookieStorage.addCookie(Url("https://eduvulcan.pl"), cookie)
            cookieStorage.addCookie(Url("https://dziennik-logowanie.vulcan.net.pl"), cookie)
            cookieStorage.addCookie(Url("https://wiadomosci.eduvulcan.pl"), cookie)
        }

        val prometheusEncoded = UrlEncoderUtil.encode("https://eduvulcan.pl")
        val ssoEncoded = UrlEncoderUtil.encode(ssoBaseUrl)
        val studentEncoded = UrlEncoderUtil.encode("https://uczen.eduvulcan.pl")

        // First SSO flow to authenticate the client in the SSO domain
        val firstAuthUrl = "https://eduvulcan.pl/fs/ls?wa=wsignin1.0&wtrealm=$ssoEncoded%2F${tenant}%2FFs%2FLs%3Fwa%3Dwsignin1.0%26wtrealm%3D$studentEncoded%2F${tenant}%2FAccount%2FLogin%3FreturnUrl%3D$prometheusEncoded%26wctx%3Dauth%3DstudentEV%26nslo%3D1&wctx=nslo%3D1"
        authorizePrometheus(firstAuthUrl)

        // Second SSO flow to authenticate the client in the messages domain
        val authorizeUrl = "$ssoBaseUrl/$tenant/Fs/Ls?wa=wsignin1.0&wtrealm=$messagesBaseUrl/$tenant/Account/Login?returnUrl=/$tenant/App&wctx=auth=studentEV&nslo=1"
        authorizePrometheus(authorizeUrl)

        // Fetch tokens from App
        val appScript = Ksoup.parse(httpClient.get("$messagesBaseUrl/$tenant/App").bodyAsText())
            .select("script").firstOrNull()?.html() ?: ""

        antiForgeryToken = Regex("antiForgeryToken: '(.*?)'").find(appScript)?.groupValues?.get(1) ?: ""
        appGuid = Regex("appGuid: '(.*?)'").find(appScript)?.groupValues?.get(1) ?: ""

        isInitialized = true
    }

    private suspend fun authorizePrometheus(url: String) {
        val response1 = httpClient.get(url)
        val document = Ksoup.parse(response1.bodyAsText())
        val res1 = findAndSubmitForm(document) ?: return
        val doc2 = Ksoup.parse(res1.bodyAsText())
        if (doc2.forms().isNotEmpty()) {
            findAndSubmitForm(doc2)
        }
    }

    private suspend fun findAndSubmitForm(document: Document): HttpResponse? {
        val form = document.forms().firstOrNull() ?: return null
        val action = form.attr("action").ifBlank { return null }
        val fields = form.children().select("input[type=\"hidden\"]")
            .associate { it.attr("name") to listOf(it.value()) }

        return httpClient.submitForm(
            url = action,
            formParameters = parametersOf(fields)
        )
    }

    suspend fun getMailboxes(): List<PrometheusMailbox> {
        initialize()
        val response = httpClient.get("$messagesBaseUrl/$tenant/api/Skrzynki") {
            header("X-V-AppGuid", appGuid)
            header("X-V-RequestVerificationToken", antiForgeryToken)
            contentType(ContentType.Application.Json)
        }
        return response.body()
    }

    suspend fun getReceivedMessages(mailboxKey: String, pageSize: Int = 50, lastMessageId: Int = 0): List<PrometheusMessage> {
        return fetchMessages("/api/OdebraneSkrzynka", mailboxKey, pageSize, lastMessageId)
    }

    suspend fun getSentMessages(mailboxKey: String, pageSize: Int = 50, lastMessageId: Int = 0): List<PrometheusMessage> {
        return fetchMessages("/api/WyslaneSkrzynka", mailboxKey, pageSize, lastMessageId)
    }

    suspend fun getDeletedMessages(mailboxKey: String, pageSize: Int = 50, lastMessageId: Int = 0): List<PrometheusMessage> {
        return fetchMessages("/api/UsunieteSkrzynka", mailboxKey, pageSize, lastMessageId)
    }

    private suspend fun fetchMessages(endpoint: String, mailboxKey: String, pageSize: Int, lastMessageId: Int): List<PrometheusMessage> {
        initialize()
        val response = httpClient.get("$messagesBaseUrl/$tenant$endpoint") {
            parameter("globalKeySkrzynka", mailboxKey)
            parameter("idLastWiadomosc", lastMessageId)
            parameter("pageSize", pageSize)
            header("X-V-AppGuid", appGuid)
            header("X-V-RequestVerificationToken", antiForgeryToken)
            contentType(ContentType.Application.Json)
        }
        return response.body()
    }

    suspend fun getMessageDetails(apiGlobalKey: String): PrometheusMessageDetails {
        initialize()
        val response = httpClient.get("$messagesBaseUrl/$tenant/api/WiadomoscSzczegoly") {
            parameter("apiGlobalKey", apiGlobalKey)
            header("X-V-AppGuid", appGuid)
            header("X-V-RequestVerificationToken", antiForgeryToken)
            contentType(ContentType.Application.Json)
        }
        return response.body()
    }

    suspend fun markMessageAsRead(apiGlobalKey: String) {
        initialize()
        httpClient.put("$messagesBaseUrl/$tenant/api/WiadomoscSzczegoly") {
            header("X-V-AppGuid", appGuid)
            header("X-V-RequestVerificationToken", antiForgeryToken)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("apiGlobalKey", apiGlobalKey)
            })
        }
    }

    suspend fun sendMessage(message: PrometheusSendMessage) {
        initialize()
        httpClient.post("$messagesBaseUrl/$tenant/api/WiadomoscNowa") {
            header("X-V-AppGuid", appGuid)
            header("X-V-RequestVerificationToken", antiForgeryToken)
            contentType(ContentType.Application.Json)
            setBody(message)
        }
    }

    /**
     * Replies to a received message: reads the mailboxes from api/WiadomoscOdpowiedzPrzekaz
     * and sends a new message to the original sender (as the web app and Wulkanowy do).
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun reply(apiGlobalKey: String, text: String) {
        initialize()
        val details: PrometheusReplyDetails = httpClient.get("$messagesBaseUrl/$tenant/api/WiadomoscOdpowiedzPrzekaz") {
            parameter("apiGlobalKey", apiGlobalKey)
            header("X-V-AppGuid", appGuid)
            header("X-V-RequestVerificationToken", antiForgeryToken)
            contentType(ContentType.Application.Json)
        }.also { check(it.status.isSuccess()) { "eduVulcan: nie udało się przygotować odpowiedzi (${it.status.value})" } }
            .body()
        check(details.uzytkownikSkrzynkaGlobalKey.isNotBlank() && details.nadawcaSkrzynkaGlobalKey.isNotBlank()) {
            "eduVulcan: brak danych skrzynki nadawcy"
        }

        val subject = details.temat.trim().let { if (it.startsWith("RE:", ignoreCase = true)) it else "RE: $it" }
        val quoted = details.tresc.takeIf { it.isNotBlank() }?.let {
            "<br><br>-----<br>${details.nadawcaSkrzynkaNazwa}, ${details.data}:<br>$it"
        } ?: ""
        // Built by hand so that every field (also the empty attachment list) is sent,
        // matching the request of the eduVulcan web app.
        val message = buildJsonObject {
            put("globalKey", Uuid.random().toString())
            put("watekGlobalKey", Uuid.random().toString())
            put("nadawcaSkrzynkaGlobalKey", details.uzytkownikSkrzynkaGlobalKey)
            putJsonArray("adresaciSkrzynkiGlobalKeys") { add(details.nadawcaSkrzynkaGlobalKey) }
            put("tytul", subject)
            put("tresc", textToHtml(text) + quoted)
            putJsonArray("zalaczniki") { }
        }
        val response = httpClient.post("$messagesBaseUrl/$tenant/api/WiadomoscNowa") {
            header("X-V-AppGuid", appGuid)
            header("X-V-RequestVerificationToken", antiForgeryToken)
            contentType(ContentType.Application.Json)
            setBody(message)
        }
        check(response.status.isSuccess()) {
            "eduVulcan: wysyłanie nie powiodło się (${response.status.value}) ${response.bodyAsText().take(200)}"
        }
    }

    private fun textToHtml(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\r\n", "\n").replace("\n", "<br>")
}
