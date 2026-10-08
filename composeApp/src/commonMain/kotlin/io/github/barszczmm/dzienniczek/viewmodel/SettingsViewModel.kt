package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.notifications.setBackgroundMessageChecks
import io.github.barszczmm.dzienniczek.notifications.triggerMessageCheckNow
import io.github.barszczmm.dzienniczek.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AppSettings
) : ViewModel() {

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
