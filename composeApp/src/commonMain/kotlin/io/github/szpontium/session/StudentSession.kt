package io.github.szpontium.session

import io.github.szpontium.api.hebe.SzpontApi
import io.github.szpontium.api.hebe.SzpontHebeCeApi
import io.github.szpontium.api.hebe.credentials.RsaCredential
import io.github.szpontium.api.hebe.models.Account
import io.github.szpontium.api.librus.LibrusTokenRefresher
import io.github.szpontium.api.librus.SzpontLibrusAdapterApi
import io.github.szpontium.api.librus.SzpontLibrusApi
import io.github.szpontium.api.librus.models.LibrusSynergiaAccount
import io.github.szpontium.api.prometheus.PrometheusMessagesApi
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable

@Serializable
data class StoredRsaCredential(
    val type: String,
    val restUrl: String?,
    val certificate: String,
    val privateKey: String,
    val fingerprint: String,
    val notificationToken: String?,
    val deviceId: String,
    val deviceOs: String,
    val deviceModel: String
) {
    fun toRsaCredential(): RsaCredential {
        return RsaCredential(
            type = type,
            restUrl = restUrl,
            certificate = certificate,
            privateKey = privateKey,
            fingerprint = fingerprint,
            notificationToken = notificationToken,
            deviceId = deviceId,
            deviceOs = deviceOs,
            deviceModel = deviceModel
        )
    }
}

fun RsaCredential.toStoredRsaCredential(): StoredRsaCredential {
    return StoredRsaCredential(
        type = type,
        restUrl = restUrl,
        certificate = certificate,
        privateKey = privateKey,
        fingerprint = fingerprint,
        notificationToken = notificationToken,
        deviceId = deviceId,
        deviceOs = deviceOs,
        deviceModel = deviceModel
    )
}

/** Login data of a Librus (Konto LIBRUS) student – one per Synergia account. */
@Serializable
data class LibrusStudentCredential(
    val email: String,
    val password: String,
    val portalToken: String,
    val apiToken: String,
    val synergiaAccount: LibrusSynergiaAccount
)

@Serializable
data class StoredStudentSession(
    val id: String,
    val account: Account,
    val credential: StoredRsaCredential? = null,
    val restUrl: String,
    val prometheusLogin: String? = null,
    val prometheusPassword: String? = null,
    val prometheusTenant: String? = null,
    val isEnabled: Boolean = true,
    val librus: LibrusStudentCredential? = null
)

/**
 * One student shown in the app. Backed either by eduVulcan / Vulcan (Hebe [credential])
 * or by Librus ([librus]), so accounts from both journals can be used side by side.
 */
class StudentSession(
    val id: String,
    val account: Account,
    val credential: RsaCredential?,
    val restUrl: String,
    val prometheusLogin: String? = null,
    val prometheusPassword: String? = null,
    val prometheusTenant: String? = null,
    var isEnabled: Boolean = true,
    httpClient: HttpClient,
    librus: LibrusStudentCredential? = null
) {
    /** Librus login data; updated when tokens are refreshed automatically. */
    var librus: LibrusStudentCredential? = librus
        private set

    val isLibrus: Boolean get() = librus != null

    val librusApi: SzpontLibrusApi?
    val api: SzpontApi

    init {
        if (librus != null) {
            val lApi = SzpontLibrusApi(
                httpClient = httpClient,
                portalAccessToken = librus.portalToken,
                apiAccessToken = librus.apiToken,
                tokenRefresher = LibrusTokenRefresher(
                    email = librus.email,
                    password = librus.password,
                    synergiaLogin = librus.synergiaAccount.login
                ) { portalToken, apiToken ->
                    this.librus = this.librus?.copy(portalToken = portalToken, apiToken = apiToken)
                    SessionEvents.notifyCredentialsChanged()
                }
            )
            librusApi = lApi
            api = SzpontLibrusAdapterApi(
                librusApi = lApi,
                currentSynergiaAccount = librus.synergiaAccount,
                httpClient = httpClient
            )
        } else {
            val hebeCredential = requireNotNull(credential) { "eduVulcan student session without credential" }
            hebeCredential.restUrl = restUrl
            librusApi = null
            api = SzpontHebeCeApi(hebeCredential, httpClient)
        }
    }

    val prometheusMessagesApi: PrometheusMessagesApi? = if (prometheusLogin != null && prometheusPassword != null && prometheusTenant != null) {
        PrometheusMessagesApi(
            tenant = prometheusTenant,
            login = prometheusLogin,
            password = prometheusPassword
        )
    } else null

    var prometheusMailboxKey: String? = null

    fun toStored(): StoredStudentSession {
        return StoredStudentSession(
            id = id,
            account = account,
            credential = credential?.toStoredRsaCredential(),
            restUrl = restUrl,
            prometheusLogin = prometheusLogin,
            prometheusPassword = prometheusPassword,
            prometheusTenant = prometheusTenant,
            isEnabled = isEnabled,
            librus = librus
        )
    }

    companion object {
        fun generateId(account: Account): String {
            return "${account.pupil.id}_${account.unit.id}_${account.journal?.id ?: 0}_${account.constituentUnit.id}"
        }

        fun librusId(synergiaAccount: LibrusSynergiaAccount): String = "librus_${synergiaAccount.login}"

        fun fromStored(s: StoredStudentSession, httpClient: HttpClient): StudentSession =
            StudentSession(
                id = s.id,
                account = s.account,
                credential = s.credential?.toRsaCredential(),
                restUrl = s.restUrl,
                prometheusLogin = s.prometheusLogin,
                prometheusPassword = s.prometheusPassword,
                prometheusTenant = s.prometheusTenant,
                isEnabled = s.isEnabled,
                httpClient = httpClient,
                librus = s.librus
            )
    }
}
