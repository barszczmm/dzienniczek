package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.api.hebe.models.Account
import io.github.barszczmm.dzienniczek.session.ApiSession
import io.github.barszczmm.dzienniczek.session.SessionStorage
import io.github.barszczmm.dzienniczek.session.StudentSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val session: ApiSession,
    private val sessionStorage: SessionStorage
) : ViewModel() {

    val activeStudent: StateFlow<StudentSession?> = session.activeStudent
    val studentSessions: StateFlow<List<StudentSession>> = session.studentSessions

    val currentAccount: Account?
        get() = session.currentAccount

    fun selectStudent(studentId: String) {
        session.selectStudent(studentId)
        viewModelScope.launch {
            sessionStorage.setActiveStudentId(studentId)
        }
    }
}
