package io.github.barszczmm.dzienniczek.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.barszczmm.dzienniczek.session.ApiSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import io.github.barszczmm.dzienniczek.util.displayMailboxName
import io.github.barszczmm.dzienniczek.util.formatJournalDateTime
import io.github.barszczmm.dzienniczek.util.parseJournalDateTime

data class MessageDetailsState(
    val isLoading: Boolean = false,
    val content: String? = null,
    val sender: String? = null,
    val subject: String? = null,
    val date: String? = null,
    val error: String? = null
)

data class ReplyState(
    val isSending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null
)

class MessageDetailsViewModel(
    private val session: ApiSession
) : ViewModel() {

    private val _state = MutableStateFlow(MessageDetailsState())
    val state: StateFlow<MessageDetailsState> = _state

    private var loadedId: String? = null

    private val _reply = MutableStateFlow(ReplyState())
    val reply: StateFlow<ReplyState> = _reply

    /** Replying works for Librus (Synergia web) and eduVulcan web messages, not for Hebe-only accounts. */
    fun canReply(isHebe: Boolean): Boolean =
        session.librusApi != null || (!isHebe && session.prometheusMessagesApi != null)

    fun sendReply(id: String, isHebe: Boolean, text: String) {
        if (text.isBlank() || _reply.value.isSending) return
        viewModelScope.launch {
            _reply.value = ReplyState(isSending = true)
            try {
                val librusApi = session.librusApi
                val prometheusApi = session.prometheusMessagesApi
                when {
                    librusApi != null -> librusApi.replySynergiaMessage(id, text.trim())
                    !isHebe && prometheusApi != null -> prometheusApi.reply(id, text.trim())
                    else -> throw IllegalStateException("Odpowiadanie nie jest dostępne dla tego konta")
                }
                _reply.value = ReplyState(sent = true)
            } catch (e: Exception) {
                e.printStackTrace()
                _reply.value = ReplyState(error = e.message ?: "Nie udało się wysłać odpowiedzi")
            }
        }
    }

    fun resetReply() {
        _reply.value = ReplyState()
    }

    fun loadMessage(id: String, isHebe: Boolean, hebeContent: String?) {
        if (loadedId == id) return
        loadedId = id

        viewModelScope.launch {
            if (session.currentAccount?.unit?.restUrl == "librus" || session.librusApi != null) {
                _state.value = _state.value.copy(isLoading = true, error = null)
                try {
                    val librusApi = session.librusApi
                        ?: (session.api as? io.github.barszczmm.dzienniczek.api.librus.DzienniczekLibrusAdapterApi)?.librusApi
                        ?: throw IllegalStateException("Brak sesji Librus")
                    val details = librusApi.getSynergiaMessageDetails(id)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        content = details.content.ifBlank { "Brak treści wiadomości." },
                        sender = details.sender,
                        subject = details.subject,
                        date = parseJournalDateTime(details.date)?.let { formatJournalDateTime(it) } ?: details.date
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    loadedId = null
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Nie udało się pobrać wiadomości"
                    )
                }
                return@launch
            }

            if (isHebe) {
                // Hebe already provides the content from the list
                _state.value = _state.value.copy(
                    isLoading = false,
                    content = hebeContent ?: "Brak treści wiadomości."
                )
                val account = session.currentAccount
                val api = session.api
                if (account != null && api != null) {
                    val box = account.messageBox?.globalKey ?: ""
                    runCatching { api.markMessageAsRead(account.unit.restUrl, box, id, pupilId = account.pupil.id) }
                }
            } else {
                _state.value = _state.value.copy(isLoading = true, error = null)
                try {
                    val prometheusApi = session.prometheusMessagesApi
                        ?: throw IllegalStateException("Prometheus API not initialized")
                    
                    val details = prometheusApi.getMessageDetails(apiGlobalKey = id)
                    runCatching { prometheusApi.markMessageAsRead(apiGlobalKey = id) }
                    
                    _state.value = _state.value.copy(
                        isLoading = false,
                        content = details.tresc,
                        sender = displayMailboxName(details.nadawca) ?: details.nadawca,
                        subject = details.temat,
                        date = parseJournalDateTime(details.data)?.let { formatJournalDateTime(it) } ?: details.data
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Wystąpił błąd podczas pobierania wiadomości"
                    )
                }
            }
        }
    }
}
