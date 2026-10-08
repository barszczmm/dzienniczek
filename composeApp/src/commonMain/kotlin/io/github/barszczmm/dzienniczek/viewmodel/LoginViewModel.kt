package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.api.hebe.DzienniczekHebeCeApi
import io.github.barszczmm.dzienniczek.api.hebe.credentials.RsaCredential
import io.github.barszczmm.dzienniczek.api.hebe.models.Account
import io.github.barszczmm.dzienniczek.api.librus.LibrusLoginHelper
import io.github.barszczmm.dzienniczek.api.librus.LibrusMapper
import io.github.barszczmm.dzienniczek.api.librus.DzienniczekLibrusApi
import io.github.barszczmm.dzienniczek.api.prometheus.PrometheusLoginHelper
import io.github.barszczmm.dzienniczek.navigation.CandidateStudent
import io.github.barszczmm.dzienniczek.session.ApiSession
import io.github.barszczmm.dzienniczek.session.SessionStorage
import io.github.barszczmm.dzienniczek.session.LibrusStudentCredential
import io.github.barszczmm.dzienniczek.session.StudentSession
import io.github.barszczmm.dzienniczek.session.toStoredRsaCredential
import io.ktor.client.HttpClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

sealed interface LoginEvent {
    data object Success : LoginEvent
    data class SelectStudents(val candidates: List<CandidateStudent>) : LoginEvent
    data class Error(val message: String) : LoginEvent
}

class LoginViewModel(
    private val session: ApiSession,
    private val httpClient: HttpClient,
    private val sessionStorage: SessionStorage
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _events = Channel<LoginEvent>()
    val events = _events.receiveAsFlow()

    fun loginWithEduVulcan(login: String, password: String) {
        if (login.isBlank() || password.isBlank()) {
            viewModelScope.launch {
                _events.send(LoginEvent.Error("Login i hasło nie mogą być puste"))
            }
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val helper = PrometheusLoginHelper()
                val result = helper.login(
                    login = login.trim(),
                    password = password,
                    deviceModel = "Android"
                )

                val candidates = mutableListOf<CandidateStudent>()
                val tokensByTenant = if (result.tokensByTenant.isNotEmpty()) {
                    result.tokensByTenant
                } else {
                    val defaultTenant = result.tenantTokens.keys.firstOrNull() ?: "eduvulcan"
                    mapOf(defaultTenant to result.tokens)
                }

                for ((tenant, tokens) in tokensByTenant) {
                    if (tokens.isEmpty()) continue
                    val credential = RsaCredential.createNew(
                        deviceOs = "Android",
                        deviceModel = "Android"
                    )
                    val api = DzienniczekHebeCeApi(credential, httpClient)
                    val restUrl = api.registerByJwt(tokens, tenant)
                    val accounts = api.getAccounts()

                    for (account in accounts) {
                        val studentId = StudentSession.generateId(account)
                        candidates.add(
                            CandidateStudent(
                                id = studentId,
                                pupilFirstName = account.pupil.firstName,
                                pupilSurname = account.pupil.surname,
                                schoolName = account.unit.displayName.ifBlank { account.unit.name },
                                classDisplay = account.classDisplay ?: "",
                                tenant = tenant,
                                restUrl = restUrl,
                                accountJson = json.encodeToString(Account.serializer(), account),
                                credentialJson = json.encodeToString(credential.toStoredRsaCredential()),
                                pLogin = login.trim(),
                                pPassword = password,
                                isSelected = true
                            )
                        )
                    }
                }

                if (candidates.isEmpty()) {
                    _events.send(LoginEvent.Error("Brak kont uczniów na tym koncie EduVulcan"))
                } else {
                    _events.send(LoginEvent.SelectStudents(candidates))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _events.send(LoginEvent.Error(e.message ?: "Błąd logowania"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loginWithLibrus(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            viewModelScope.launch {
                _events.send(LoginEvent.Error("E-mail i hasło nie mogą być puste"))
            }
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val loginHelper = LibrusLoginHelper()
                val tokenResponse = loginHelper.login(email.trim(), password)
                val portalToken = tokenResponse.accessToken

                val librusApi = DzienniczekLibrusApi(
                    httpClient = httpClient,
                    portalAccessToken = portalToken
                )

                val synergiaAccounts = librusApi.getSynergiaAccounts()
                if (synergiaAccounts.isEmpty()) {
                    _events.send(LoginEvent.Error("Brak powiązanych kont Synergia na tym koncie Librus"))
                    return@launch
                }

                // One student session per Synergia account (child), each with its own API token.
                val newSessions = synergiaAccounts.map { synergia ->
                    val studentApi = DzienniczekLibrusApi(
                        httpClient = httpClient,
                        portalAccessToken = portalToken
                    )
                    val apiToken = runCatching { studentApi.getFreshApiToken(synergia.login) }.getOrNull()
                        ?: synergia.accessToken
                        ?: error("Nie udało się pobrać tokenu dla konta ${synergia.studentName}")
                    studentApi.apiAccessToken = apiToken
                    val me = studentApi.getMe()
                    StudentSession(
                        id = StudentSession.librusId(synergia),
                        account = LibrusMapper.toHebeAccount(synergia, me),
                        credential = null,
                        restUrl = "librus",
                        isEnabled = true,
                        httpClient = httpClient,
                        librus = LibrusStudentCredential(
                            email = email.trim(),
                            password = password,
                            portalToken = portalToken,
                            apiToken = apiToken,
                            synergiaAccount = synergia
                        )
                    )
                }

                // Keep already logged-in (e.g. eduVulcan) students and switch to the new one.
                val selectId = newSessions.first().id
                session.addStudentSessions(newSessions, selectId = selectId)
                sessionStorage.saveStudents(session.studentSessions.value, selectId)

                _events.send(LoginEvent.Success)
            } catch (e: Exception) {
                e.printStackTrace()
                _events.send(LoginEvent.Error(e.message ?: "Błąd logowania do Librusa"))
            } finally {
                _isLoading.value = false
            }
        }
    }
}
