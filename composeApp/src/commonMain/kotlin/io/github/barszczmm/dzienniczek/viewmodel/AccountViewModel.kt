package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.session.ApiSession
import io.github.barszczmm.dzienniczek.session.SessionStorage
import io.github.barszczmm.dzienniczek.session.StudentSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AccountViewModel(
    private val session: ApiSession,
    private val sessionStorage: SessionStorage
) : ViewModel() {

    val studentSessions: StateFlow<List<StudentSession>> = session.studentSessions
    val activeStudent: StateFlow<StudentSession?> = session.activeStudent

    fun toggleStudentEnabled(studentId: String, enabled: Boolean) {
        session.toggleStudentEnabled(studentId, enabled)
        viewModelScope.launch {
            val current = session.studentSessions.value
            val activeId = session.activeStudent.value?.id
            sessionStorage.saveStudents(current, activeId)
        }
    }

    fun removeStudent(studentId: String, onAllRemoved: () -> Unit) {
        session.removeStudent(studentId)
        viewModelScope.launch {
            val current = session.studentSessions.value
            if (current.isEmpty()) {
                sessionStorage.clear()
                session.clear()
                onAllRemoved()
            } else {
                val activeId = session.activeStudent.value?.id
                sessionStorage.saveStudents(current, activeId)
            }
        }
    }

    fun logoutAll(onLogout: () -> Unit) {
        viewModelScope.launch {
            sessionStorage.clear()
            session.clear()
            onLogout()
        }
    }
}
