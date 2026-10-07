package io.github.szpontium.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.szpontium.api.librus.LibrusLoginHelper
import io.github.szpontium.api.librus.SzpontLibrusApi
import io.github.szpontium.api.prometheus.PrometheusMessagesApi
import io.github.szpontium.api.prometheus.models.VulcanMailboxName
import io.github.szpontium.session.SessionStorage
import io.github.szpontium.viewmodel.MessageTab
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A message that was not seen in any previous check. [body] may contain HTML when [isHtml] is true. */
data class NewMessage(
    val key: String,
    val source: String,
    val studentName: String?,
    val sender: String,
    val subject: String,
    val body: String,
    val isHtml: Boolean
)

/**
 * Polls received messages of every logged-in account (Librus and eduVulcan) and returns
 * the ones that were not present during the previous check, with their full content.
 *
 * Librus messages are read through the free Synergia web interface, eduVulcan messages
 * through wiadomosci.eduvulcan.pl (or Hebe when no web credentials are stored).
 *
 * The first time a mailbox is seen its current messages are only remembered, so the
 * user does not get a flood of notifications for old messages.
 */
class MessageChecker(
    private val dataStore: DataStore<Preferences>,
    private val sessionStorage: SessionStorage
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val idListSerializer = ListSerializer(String.serializer())
    private val mutex = Mutex()

    suspend fun check(): List<NewMessage> = mutex.withLock {
        val result = mutableListOf<NewMessage>()
        runCatching { checkLibrus(result) }.onFailure { it.printStackTrace() }
        runCatching { checkVulcan(result) }.onFailure { it.printStackTrace() }
        result
    }

    // ---------------------------------------------------------------- Librus

    private suspend fun checkLibrus(out: MutableList<NewMessage>) {
        val credential = sessionStorage.loadLibrusCredential() ?: return

        // Dedicated client with a cookie jar: Synergia web pages need the session cookie
        // set by the auto-login redirect.
        val client = HttpClient {
            followRedirects = true
            install(HttpCookies)
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
        }
        try {
            val api = SzpontLibrusApi(client, portalAccessToken = credential.librusPortalToken)

            val accounts = try {
                api.getSynergiaAccounts()
            } catch (e: Exception) {
                // Portal token expired: log in again with the stored e-mail and password.
                val email = credential.librusEmail ?: throw e
                val password = credential.librusPassword ?: throw e
                val newToken = LibrusLoginHelper().login(email, password).accessToken
                sessionStorage.updateLibrusPortalToken(newToken)
                api.portalAccessToken = newToken
                api.getSynergiaAccounts()
            }

            for (account in accounts) {
                runCatching {
                    api.apiAccessToken = runCatching { api.getFreshApiToken(account.login) }
                        .getOrNull() ?: account.accessToken
                    val autoLoginToken = api.getAutoLoginToken()
                    val messages = api.getSynergiaMessages(autoLoginToken, MessageTab.RECEIVED)

                    val sourceKey = "librus:${account.login}"
                    val newIds = diffAndRemember(sourceKey, messages.map { it.id })
                    messages.filter { it.id in newIds }.forEach { msg ->
                        val content = runCatching { api.getSynergiaMessageContent(msg.id) }.getOrDefault("")
                        out += NewMessage(
                            key = "$sourceKey:${msg.id}",
                            source = "Librus",
                            studentName = account.studentName,
                            sender = msg.senderOrRecipient,
                            subject = msg.title,
                            body = content.ifBlank { "(nie udało się pobrać treści)" },
                            isHtml = false
                        )
                    }
                }.onFailure { it.printStackTrace() }
            }
        } finally {
            client.close()
        }
    }

    // -------------------------------------------------------------- eduVulcan

    private suspend fun checkVulcan(out: MutableList<NewMessage>) {
        val students = sessionStorage.loadVulcanStudents().filter { it.isEnabled }
        if (students.isEmpty()) return

        // One web login usually covers several children (one mailbox per child),
        // so group by login and walk all mailboxes of that login.
        val webLogins = students
            .filter { it.prometheusLogin != null && it.prometheusPassword != null && it.prometheusTenant != null }
            .distinctBy { "${it.prometheusTenant}|${it.prometheusLogin}" }

        for (student in webLogins) {
            runCatching {
                checkPrometheus(
                    tenant = student.prometheusTenant!!,
                    login = student.prometheusLogin!!,
                    password = student.prometheusPassword!!,
                    out = out
                )
            }.onFailure { it.printStackTrace() }
        }

        // Students without stored web credentials: fall back to Hebe.
        val hebeStudents = students
            .filter { it.prometheusLogin == null }
            .distinctBy { it.account.messageBox?.globalKey ?: it.id }
        for (student in hebeStudents) {
            runCatching {
                val account = student.account
                val box = account.messageBox?.globalKey ?: return@runCatching
                val messages = student.api.getReceivedMessages(
                    restUrl = account.unit.restUrl,
                    box = box,
                    pupilId = account.pupil.id
                )
                val sourceKey = "hebe:$box"
                val newIds = diffAndRemember(sourceKey, messages.map { it.id })
                messages.filter { it.id in newIds }.forEach { msg ->
                    out += NewMessage(
                        key = "$sourceKey:${msg.id}",
                        source = "eduVulcan",
                        studentName = "${account.pupil.firstName} ${account.pupil.surname}".trim(),
                        sender = msg.sender.name,
                        subject = msg.subject,
                        body = msg.content,
                        isHtml = true
                    )
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    private suspend fun checkPrometheus(
        tenant: String,
        login: String,
        password: String,
        out: MutableList<NewMessage>
    ) {
        val cacheKey = "$tenant|$login"
        suspend fun <T> withApi(block: suspend (PrometheusMessagesApi) -> T): T {
            val cached = prometheusCache[cacheKey]
            if (cached != null) {
                try {
                    return block(cached)
                } catch (e: Exception) {
                    // Session probably expired – drop it and log in again once.
                    prometheusCache.remove(cacheKey)
                }
            }
            val fresh = PrometheusMessagesApi(tenant = tenant, login = login, password = password)
            val result = block(fresh)
            prometheusCache[cacheKey] = fresh
            return result
        }

        val mailboxes = withApi { it.getMailboxes() }
        for (mailbox in mailboxes) {
            runCatching {
                val messages = withApi { it.getReceivedMessages(mailboxKey = mailbox.globalKey) }
                val sourceKey = "vulcan:${mailbox.globalKey}"
                val newIds = diffAndRemember(sourceKey, messages.map { it.apiGlobalKey })
                val studentName = VulcanMailboxName.parse(mailbox.nazwa)?.studentName ?: mailbox.nazwa
                messages.filter { it.apiGlobalKey in newIds }.forEach { msg ->
                    val details = runCatching { withApi { it.getMessageDetails(msg.apiGlobalKey) } }.getOrNull()
                    out += NewMessage(
                        key = "$sourceKey:${msg.apiGlobalKey}",
                        source = "eduVulcan",
                        studentName = studentName,
                        sender = details?.nadawca?.ifBlank { null } ?: msg.korespondenci ?: "Nieznany",
                        subject = details?.temat?.ifBlank { null } ?: msg.temat,
                        body = details?.tresc?.ifBlank { null } ?: "(nie udało się pobrać treści)",
                        isHtml = details != null
                    )
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    // ------------------------------------------------------------ seen state

    /**
     * Returns ids from [currentIds] that were not stored for [sourceKey] before and stores
     * the current list. Returns an empty set the first time a source is seen.
     */
    private suspend fun diffAndRemember(sourceKey: String, currentIds: List<String>): Set<String> {
        val prefKey = stringPreferencesKey("notif_seen_$sourceKey")
        val previous: List<String>? = dataStore.data.first()[prefKey]?.let {
            runCatching { json.decodeFromString(idListSerializer, it) }.getOrNull()
        }
        // Keep old ids too, so a message that drops off the first page is not reported again.
        val merged = (currentIds + (previous ?: emptyList())).distinct().take(MAX_REMEMBERED_IDS)
        dataStore.edit { it[prefKey] = json.encodeToString(idListSerializer, merged) }

        if (previous == null) return emptySet()
        val previousSet = previous.toSet()
        return currentIds.filterNot { it in previousSet }.toSet()
    }

    private companion object {
        const val MAX_REMEMBERED_IDS = 500

        /** Kept for the lifetime of the process so we don't log in to eduVulcan on every check. */
        val prometheusCache = mutableMapOf<String, PrometheusMessagesApi>()
    }
}
