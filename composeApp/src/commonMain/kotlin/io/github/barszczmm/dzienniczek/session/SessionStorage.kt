package io.github.barszczmm.dzienniczek.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.barszczmm.dzienniczek.api.hebe.credentials.RsaCredential
import io.github.barszczmm.dzienniczek.api.hebe.models.Account
import io.github.barszczmm.dzienniczek.api.librus.models.LibrusSynergiaAccount
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * Persists all logged-in students (eduVulcan and Librus) as one list, so accounts from
 * both journals can be used at the same time.
 */
class SessionStorage(
    private val dataStore: DataStore<Preferences>,
    private val httpClient: HttpClient
) {
    private val multiStudentsKey = stringPreferencesKey("session_multi_students")
    private val activeStudentIdKey = stringPreferencesKey("session_active_student_id")

    // Legacy single-account keys, migrated into the student list on restore.
    private val credentialKey = stringPreferencesKey("session_credential")
    private val accountsKey = stringPreferencesKey("session_accounts")

    suspend fun saveStudents(
        sessions: List<StudentSession>,
        activeStudentId: String? = null
    ) {
        val storedSessions = sessions.map { it.toStored() }
        dataStore.edit { prefs ->
            prefs[multiStudentsKey] = json.encodeToString(ListSerializer(StoredStudentSession.serializer()), storedSessions)
            if (activeStudentId != null) {
                prefs[activeStudentIdKey] = activeStudentId
            }
        }
    }

    suspend fun setActiveStudentId(activeStudentId: String) {
        dataStore.edit { prefs ->
            prefs[activeStudentIdKey] = activeStudentId
        }
    }

    /** Adds Vulcan students registered with a Hebe credential to the stored list. */
    suspend fun save(
        apiType: String,
        credential: RsaCredential,
        accounts: List<Account>,
        pLogin: String? = null,
        pPassword: String? = null,
        pTenant: String? = null
    ) {
        val restUrl = credential.restUrl ?: ""
        val newSessions = accounts.map { account ->
            StudentSession(
                id = StudentSession.generateId(account),
                account = account,
                credential = credential,
                restUrl = restUrl,
                prometheusLogin = pLogin,
                prometheusPassword = pPassword,
                prometheusTenant = pTenant,
                isEnabled = true,
                httpClient = httpClient
            )
        }

        val mergedMap = loadStoredSessions().associateBy { it.id }.toMutableMap()
        newSessions.forEach { mergedMap[it.id] = it }
        saveStudents(mergedMap.values.toList(), newSessions.firstOrNull()?.id)
    }

    suspend fun restore(session: ApiSession): Boolean {
        val prefs = dataStore.data.first()
        val sessions = loadStoredSessions().toMutableList()

        // Migrate an account saved by older versions (one Librus or Vulcan login).
        val migrated = migrateLegacyCredential(prefs)
        if (migrated.isNotEmpty()) {
            migrated.forEach { m -> if (sessions.none { it.id == m.id }) sessions += m }
            saveStudents(sessions, prefs[activeStudentIdKey] ?: sessions.firstOrNull()?.id)
            dataStore.edit {
                it.remove(credentialKey)
                it.remove(accountsKey)
            }
        }

        if (sessions.isEmpty()) return false
        session.setStudentSessions(sessions, prefs[activeStudentIdKey])
        return true
    }

    /** All stored students (eduVulcan and Librus), used by the background message checker. */
    suspend fun loadStudents(): List<StudentSession> = loadStoredSessions()

    /** Saves new Librus tokens of one student without touching the rest of the stored list. */
    suspend fun updateLibrusTokens(studentId: String, portalToken: String, apiToken: String) {
        dataStore.edit { prefs ->
            val multiJson = prefs[multiStudentsKey] ?: return@edit
            val stored = runCatching {
                json.decodeFromString(ListSerializer(StoredStudentSession.serializer()), multiJson)
            }.getOrNull() ?: return@edit
            val updated = stored.map { s ->
                if (s.id == studentId && s.librus != null) {
                    s.copy(librus = s.librus.copy(portalToken = portalToken, apiToken = apiToken))
                } else s
            }
            prefs[multiStudentsKey] = json.encodeToString(ListSerializer(StoredStudentSession.serializer()), updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun loadStoredSessions(): List<StudentSession> {
        val multiJson = dataStore.data.first()[multiStudentsKey]
        if (multiJson.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(StoredStudentSession.serializer()), multiJson)
                .mapNotNull { stored ->
                    runCatching { StudentSession.fromStored(stored, httpClient) }.getOrNull()
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun migrateLegacyCredential(prefs: Preferences): List<StudentSession> {
        val credentialJson = prefs[credentialKey] ?: return emptyList()
        val accountsJson = prefs[accountsKey] ?: return emptyList()
        return try {
            val stored = json.decodeFromString<StoredCredential>(credentialJson)
            val accounts = json.decodeFromString(ListSerializer(Account.serializer()), accountsJson)

            if (stored.apiType == "librus") {
                val email = stored.librusEmail ?: return emptyList()
                val password = stored.librusPassword ?: return emptyList()
                val portalToken = stored.librusPortalToken ?: return emptyList()
                val apiToken = stored.librusApiToken ?: return emptyList()
                val synergiaAccounts: List<LibrusSynergiaAccount> =
                    stored.librusAccountsJson?.let { json.decodeFromString(it) } ?: emptyList()

                synergiaAccounts.mapIndexedNotNull { index, synergia ->
                    val account = accounts.getOrNull(index) ?: return@mapIndexedNotNull null
                    StudentSession(
                        id = StudentSession.librusId(synergia),
                        account = account,
                        credential = null,
                        restUrl = "librus",
                        isEnabled = true,
                        httpClient = httpClient,
                        librus = LibrusStudentCredential(
                            email = email,
                            password = password,
                            portalToken = portalToken,
                            apiToken = if (index == 0) apiToken else synergia.accessToken ?: apiToken,
                            synergiaAccount = synergia
                        )
                    )
                }
            } else {
                if (stored.prometheusLogin == null || stored.prometheusPassword == null || stored.prometheusTenant == null) {
                    return emptyList()
                }
                val credential = RsaCredential(
                    type = stored.type,
                    restUrl = stored.restUrl,
                    certificate = stored.certificate,
                    privateKey = stored.privateKey,
                    fingerprint = stored.fingerprint,
                    notificationToken = stored.notificationToken,
                    deviceId = stored.deviceId,
                    deviceOs = stored.deviceOs,
                    deviceModel = stored.deviceModel
                )
                val restUrl = credential.restUrl ?: ""
                accounts.map { account ->
                    StudentSession(
                        id = StudentSession.generateId(account),
                        account = account,
                        credential = credential,
                        restUrl = restUrl,
                        prometheusLogin = stored.prometheusLogin,
                        prometheusPassword = stored.prometheusPassword,
                        prometheusTenant = stored.prometheusTenant,
                        isEnabled = true,
                        httpClient = httpClient
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
