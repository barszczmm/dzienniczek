package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import io.github.barszczmm.dzienniczek.api.librus.LibrusFeatureUnavailableException
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.api.hebe.models.Note
import io.github.barszczmm.dzienniczek.session.ApiSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class NotesState(
    val isLoading: Boolean = false,
    val notes: List<Note> = emptyList(),
    val error: String? = null,
    /** Set when the school does not publish notes, shown as an info instead of an error. */
    val unavailableInfo: String? = null
)

class NotesViewModel(
    private val session: ApiSession
) : ViewModel() {

    private val _state = MutableStateFlow(NotesState(isLoading = true))
    val state: StateFlow<NotesState> = _state

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
            _state.value = NotesState(isLoading = false)
            return
        }

        viewModelScope.launch {
            _state.value = NotesState(isLoading = true)
            try {
                val notes = api.getNotes(
                    restUrl = account.unit.restUrl,
                    pupilId = account.pupil.id
                )
                _state.value = NotesState(
                    notes = notes.sortedByDescending { it.dateValid }
                )
            } catch (e: LibrusFeatureUnavailableException) {
                _state.value = NotesState(unavailableInfo = "Szkoła nie udostępnia uwag w Librusie dla tego konta.")
            } catch (e: Exception) {
                _state.value = NotesState(error = e.message ?: "Błąd ładowania uwag")
            }
        }
    }
}
