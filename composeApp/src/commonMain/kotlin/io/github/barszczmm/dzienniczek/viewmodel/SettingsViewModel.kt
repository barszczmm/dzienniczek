package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.notifications.setBackgroundMessageChecks
import io.github.barszczmm.dzienniczek.notifications.triggerMessageCheckNow
import io.github.barszczmm.dzienniczek.platform.shareText
import io.github.barszczmm.dzienniczek.session.ApiSession
import io.github.barszczmm.dzienniczek.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
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
                val text = buildString {
                    appendLine("Dzienniczek – dane diagnostyczne Librus")
                    for (resource in DIAGNOSTIC_RESOURCES) {
                        appendLine()
                        appendLine("===== $resource =====")
                        val body = runCatching { api.rawGet(resource) }
                            .getOrElse { "BŁĄD: ${it.message}" }
                        appendLine(if (body.length > MAX_CHARS) body.take(MAX_CHARS) + "\n…(obcięto)" else body)
                    }
                }
                shareText("Dzienniczek – diagnostyka Librus", text)
            } finally {
                _diagnosticsRunning.value = false
            }
        }
    }

    private companion object {
        const val MAX_CHARS = 6000
        val DIAGNOSTIC_RESOURCES = listOf(
            "Root",
            "DescriptiveGrades",
            "BaseTextGrades",
            "DescriptiveTextGrades/Skills",
            "TextGrades/Categories",
            "Grades/Categories",
            "Grades/Scales",
            "Colors",
            "Grades"
        )
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
