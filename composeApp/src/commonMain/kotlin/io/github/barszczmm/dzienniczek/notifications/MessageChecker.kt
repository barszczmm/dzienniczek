package io.github.barszczmm.dzienniczek.notifications

import io.github.barszczmm.dzienniczek.api.librus.LibrusTokenRefresher
import io.github.barszczmm.dzienniczek.api.librus.DzienniczekLibrusApi
import io.github.barszczmm.dzienniczek.api.prometheus.PrometheusMessagesApi
import io.github.barszczmm.dzienniczek.api.prometheus.models.VulcanMailboxName
import io.github.barszczmm.dzienniczek.session.SessionStorage
import io.github.barszczmm.dzienniczek.session.StudentSession
import io.github.barszczmm.dzienniczek.viewmodel.MessageTab
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A message that was not seen in any previous check. [body] may contain HTML when [isHtml] is true.
 * [studentId] and [messageId] let a tap on the notification open this message for the right student;
 * [isHebe] marks messages read through Hebe, whose content comes with the list.
 */
data class NewMessage(
    val key: String,
    val source: String,
    val studentId: String?,
    val messageId: String,
    val isHebe: Boolean,
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
    private val seenItems: SeenItems,
    private val sessionStorage: SessionStorage
) {
    private val mutex = Mutex()

    /** Pauses between the requests of the current check (guarded by [mutex]). */
    private var pacer = RequestPacer()

    suspend fun check(pacer: RequestPacer = RequestPacer()): List<NewMessage> = mutex.withLock {
        this.pacer = pacer
        val result = mutableListOf<NewMessage>()
        // Journals, accounts and mailboxes are walked in a new random order on each check.
        listOf<suspend () -> Unit>({ checkLibrus(result) }, { checkVulcan(result) })
            .shuffled()
            .forEach { step -> runCatching { step() }.onFailure { it.printStackTrace() } }
        result
    }

    // ---------------------------------------------------------------- Librus

    private suspend fun checkLibrus(out: MutableList<NewMessage>) {
        val students = sessionStorage.loadStudents()
            .filter { it.isEnabled && it.isLibrus }
            .distinctBy { it.librus!!.synergiaAccount.login }
            .shuffled()
        if (students.isEmpty()) return

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
            for (student in students) {
                pacer.pause()
                runCatching {
                    val credential = student.librus!!
                    val account = credential.synergiaAccount
                    val api = DzienniczekLibrusApi(
                        httpClient = client,
                        portalAccessToken = credential.portalToken,
                        apiAccessToken = credential.apiToken,
                        tokenRefresher = LibrusTokenRefresher(
                            email = credential.email,
                            password = credential.password,
                            synergiaLogin = account.login
                        ) { portalToken, apiToken ->
                            sessionStorage.updateLibrusTokens(student.id, portalToken, apiToken)
                        }
                    )

                    val autoLoginToken = api.getAutoLoginToken()
                    val messages = api.getSynergiaMessages(autoLoginToken, MessageTab.RECEIVED)

                    val sourceKey = "librus:${account.login}"
                    val newIds = diffAndRemember(sourceKey, messages.map { it.id })
                    messages.filter { it.id in newIds }.forEach { msg ->
                        pacer.pause()
                        val content = runCatching { api.getSynergiaMessageContent(msg.id) }.getOrDefault("")
                        out += NewMessage(
                            key = "$sourceKey:${msg.id}",
                            source = "Librus",
                            studentId = student.id,
                            messageId = msg.id,
                            isHebe = false,
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
        val students = sessionStorage.loadStudents().filter { it.isEnabled && !it.isLibrus }
        if (students.isEmpty()) return

        // One web login usually covers several children (one mailbox per child),
        // so group by login and walk all mailboxes of that login.
        val webLogins = students
            .filter { it.prometheusLogin != null && it.prometheusPassword != null && it.prometheusTenant != null }
            .groupBy { "${it.prometheusTenant}|${it.prometheusLogin}" }
            .values
            .shuffled()

        for (loginStudents in webLogins) {
            val student = loginStudents.first()
            pacer.pause()
            runCatching {
                checkPrometheus(
                    tenant = student.prometheusTenant!!,
                    login = student.prometheusLogin!!,
                    password = student.prometheusPassword!!,
                    students = loginStudents,
                    out = out
                )
            }.onFailure { it.printStackTrace() }
        }

        // Students without stored web credentials: fall back to Hebe.
        val hebeStudents = students
            .filter { it.prometheusLogin == null }
            .distinctBy { it.account.messageBox?.globalKey ?: it.id }
            .shuffled()
        for (student in hebeStudents) {
            pacer.pause()
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
                        studentId = student.id,
                        messageId = msg.id,
                        isHebe = true,
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
        students: List<StudentSession>,
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

        val mailboxes = withApi { it.getMailboxes() }.shuffled()
        for (mailbox in mailboxes) {
            pacer.pause()
            runCatching {
                val messages = withApi { it.getReceivedMessages(mailboxKey = mailbox.globalKey) }
                val sourceKey = "vulcan:${mailbox.globalKey}"
                val newIds = diffAndRemember(sourceKey, messages.map { it.apiGlobalKey })
                val studentName = VulcanMailboxName.parse(mailbox.nazwa)?.studentName ?: mailbox.nazwa
                // Same matching as the messages screen: the mailbox name contains the child's name.
                val studentId = students.firstOrNull { s ->
                    val firstName = s.account.pupil.firstName.trim()
                    val surname = s.account.pupil.surname.trim()
                    firstName.isNotEmpty() && studentName.contains(firstName, ignoreCase = true) &&
                        surname.isNotEmpty() && studentName.contains(surname, ignoreCase = true)
                }?.id ?: students.singleOrNull()?.id
                messages.filter { it.apiGlobalKey in newIds }.forEach { msg ->
                    pacer.pause()
                    val details = runCatching { withApi { it.getMessageDetails(msg.apiGlobalKey) } }.getOrNull()
                    out += NewMessage(
                        key = "$sourceKey:${msg.apiGlobalKey}",
                        source = "eduVulcan",
                        studentId = studentId,
                        messageId = msg.apiGlobalKey,
                        isHebe = false,
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

    private suspend fun diffAndRemember(sourceKey: String, currentIds: List<String>): Set<String> =
        seenItems.diffAndRemember(sourceKey, currentIds)

    private companion object {
        /** Kept for the lifetime of the process so we don't log in to eduVulcan on every check. */
        val prometheusCache = mutableMapOf<String, PrometheusMessagesApi>()
    }
}
