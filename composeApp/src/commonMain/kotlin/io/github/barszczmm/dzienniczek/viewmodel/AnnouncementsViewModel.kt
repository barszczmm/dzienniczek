package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import io.github.barszczmm.dzienniczek.api.librus.LibrusFeatureUnavailableException
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.api.hebe.models.Announcement
import io.github.barszczmm.dzienniczek.session.ApiSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AnnouncementsState(
    val isLoading: Boolean = false,
    val announcements: List<Announcement> = emptyList(),
    val error: String? = null
)

class AnnouncementsViewModel(
    private val session: ApiSession
) : ViewModel() {

    private val _state = MutableStateFlow(AnnouncementsState(isLoading = true))
    val state: StateFlow<AnnouncementsState> = _state

    init {
        viewModelScope.launch {
            session.activeStudent.collectLatest {
                load()
            }
        }
    }

    fun load() {
        val account = session.currentAccount
        val api = session.api
        if (account == null || api == null) {
            _state.value = AnnouncementsState(isLoading = false)
            return
        }

        viewModelScope.launch {
            _state.value = AnnouncementsState(isLoading = true)
            try {
                val announcements = api.getAnnouncements(
                    restUrl = account.unit.restUrl,
                    unitId = account.unit.id,
                    pupilId = account.pupil.id
                )
                _state.value = AnnouncementsState(announcements = announcements)
            } catch (e: LibrusFeatureUnavailableException) {
                // The school does not publish announcements in Librus – show an empty list.
                _state.value = AnnouncementsState()
            } catch (e: Exception) {
                _state.value = AnnouncementsState(error = e.message ?: "Błąd ładowania ogłoszeń")
            }
        }
    }
}
