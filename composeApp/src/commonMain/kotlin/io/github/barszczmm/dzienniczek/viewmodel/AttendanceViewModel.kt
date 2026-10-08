package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.api.hebe.models.semesterOf
import io.github.barszczmm.dzienniczek.data.AttendanceRepository
import io.github.barszczmm.dzienniczek.session.ApiSession
import io.github.barszczmm.dzienniczek.ui.model.AttendanceEntry
import io.github.barszczmm.dzienniczek.ui.model.AttendanceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Which entries are listed – set by tapping the summary tiles. */
enum class AttendanceFilter { ALL, UNEXCUSED, EXCUSED, LATE }

data class AttendanceState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val all: List<AttendanceEntry> = emptyList(),
    /** 0 = whole year, 1 / 2 = school term. */
    val semester: Int = 0,
    val filter: AttendanceFilter = AttendanceFilter.ALL
) {
    private val inSemester: List<AttendanceEntry>
        get() = if (semester == 0) all else all.filter { it.semester == semester }

    val unexcused: Int get() = inSemester.count { it.kind == AttendanceKind.ABSENT }
    val excused: Int get() = inSemester.count { it.kind == AttendanceKind.ABSENT_EXCUSED || it.kind == AttendanceKind.RELEASED }
    val late: Int get() = inSemester.count { it.kind == AttendanceKind.LATE || it.kind == AttendanceKind.LATE_EXCUSED }

    val visible: List<AttendanceEntry>
        get() = inSemester.filter {
            when (filter) {
                AttendanceFilter.ALL -> true
                AttendanceFilter.UNEXCUSED -> it.kind == AttendanceKind.ABSENT
                AttendanceFilter.EXCUSED -> it.kind == AttendanceKind.ABSENT_EXCUSED || it.kind == AttendanceKind.RELEASED
                AttendanceFilter.LATE -> it.kind == AttendanceKind.LATE || it.kind == AttendanceKind.LATE_EXCUSED
            }
        }
}

class AttendanceViewModel(
    private val session: ApiSession,
    private val repository: AttendanceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceState(isLoading = true))
    val state: StateFlow<AttendanceState> = _state

    init {
        viewModelScope.launch {
            session.activeStudent.collectLatest { load(forceRefresh = false) }
        }
    }

    fun load(forceRefresh: Boolean = true) {
        val account = session.currentAccount
        viewModelScope.launch {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val currentSemester = account?.semesterOf(today) ?: 1
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                semester = if (_state.value.all.isEmpty()) currentSemester else _state.value.semester
            )
            try {
                val entries = repository.get(forceRefresh)
                _state.value = _state.value.copy(isLoading = false, all = entries)
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Błąd ładowania frekwencji"
                )
            }
        }
    }

    fun setSemester(semester: Int) {
        _state.value = _state.value.copy(semester = semester)
    }

    /** Tapping a summary tile filters the list; tapping the active one again shows everything. */
    fun setFilter(filter: AttendanceFilter) {
        _state.value = _state.value.copy(
            filter = if (_state.value.filter == filter) AttendanceFilter.ALL else filter
        )
    }
}
