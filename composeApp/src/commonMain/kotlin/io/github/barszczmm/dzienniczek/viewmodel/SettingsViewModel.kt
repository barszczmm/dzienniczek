package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.notifications.setBackgroundMessageChecks
import io.github.barszczmm.dzienniczek.notifications.triggerMessageCheckNow
import io.github.barszczmm.dzienniczek.platform.shareText
import io.github.barszczmm.dzienniczek.session.ApiSession
import io.github.barszczmm.dzienniczek.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AppSettings,
    private val session: ApiSession
) : ViewModel() {

    private val _diagnosticsRunning = MutableStateFlow(false)
    val diagnosticsRunning: StateFlow<Boolean> = _diagnosticsRunning

    /** True when the active student is a Librus account (diagnostics are Librus-only). */
    val isLibrusActive: Boolean
        get() = session.activeStudent.value?.isLibrus == true

    /**
     * Collects raw responses of the Librus grade-related API resources for the active
     * student and opens the share sheet, so the data structure can be inspected.
     */
    fun exportLibrusDiagnostics() {
        val api = session.librusApi ?: return
        viewModelScope.launch {
            _diagnosticsRunning.value = true
            try {
                val out = StringBuilder("Dzienniczek – dane diagnostyczne Librus (v2)\n")
                suspend fun fetch(resource: String): String =
                    runCatching { api.rawGet(resource) }.getOrElse { "BŁĄD: ${it.message}" }

                fun section(title: String, body: String, limit: Int = 2500) {
                    out.append("\n===== ").append(title).append(" =====\n")
                    out.append(if (body.length > limit) body.take(limit) + "\n…(obcięto)" else body).append('\n')
                }

                fun resourcesOf(body: String): List<String> = runCatching {
                    diagJson.parseToJsonElement(body).jsonObject["Resources"]?.jsonObject
                        ?.mapNotNull { (_, v) -> v.jsonObject["Url"]?.jsonPrimitive?.content }
                        ?.map { it.substringAfter("/2.0/") }
                        .orEmpty()
                }.getOrDefault(emptyList())

                // 1. Full list of API resources.
                section("Root – zasoby", resourcesOf(fetch("Root")).joinToString("\n"), 20_000)

                // 2. Descriptive grades: first grades + every sub-resource the API advertises.
                val descriptive = fetch("DescriptiveGrades")
                section("DescriptiveGrades", descriptive, 3000)
                val subResources = resourcesOf(descriptive).filter { it != "Root" }
                section("DescriptiveGrades – podzasoby", subResources.joinToString("\n"))
                for (resource in subResources) {
                    section(resource, fetch(resource))
                }

                // 3. One comment of a descriptive grade (its text explains the grade).
                val commentUrl = Regex("DescriptiveGrades/Comments/\\d+").find(descriptive.replace("\\/", "/"))?.value
                if (commentUrl != null) section(commentUrl, fetch(commentUrl))

                // 4. Likely scale/definition resources (some may not exist).
                for (resource in listOf(
                    "DescriptiveGrades/Skills",
                    "DescriptiveGrades/Scales",
                    "DescriptiveGrades/Types",
                    "DescriptiveGrades/Text",
                    "Grades/Types",
                    "Grades/Comments"
                )) {
                    if (resource !in subResources) section(resource, fetch(resource), 1500)
                }

                shareText("Dzienniczek – diagnostyka Librus", out.toString())
            } finally {
                _diagnosticsRunning.value = false
            }
        }
    }

    private companion object {
        val diagJson = Json { ignoreUnknownKeys = true }
    }

    val messagePolling: StateFlow<Boolean> = settings.messagePolling
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setMessagePolling(enabled: Boolean) {
        viewModelScope.launch {
            settings.setMessagePolling(enabled)
            setBackgroundMessageChecks(enabled)
        }
    }

    fun checkNow() = triggerMessageCheckNow()
}
