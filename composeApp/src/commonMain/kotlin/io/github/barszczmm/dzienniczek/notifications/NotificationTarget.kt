package io.github.barszczmm.dzienniczek.notifications

import io.github.barszczmm.dzienniczek.settings.BackgroundCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a tapped notification leads: the student it is about and the screen of [check].
 * For messages [messageId] opens the message itself ([hebeContent] is its content on Hebe accounts).
 */
data class NotificationTarget(
    val studentId: String?,
    val check: BackgroundCheck,
    val messageId: String? = null,
    val isHebe: Boolean = false,
    val hebeContent: String? = null
)

/** Process-wide hand-off of a tapped notification from the platform code to the UI. */
object NotificationNavigation {
    private val _pending = MutableStateFlow<NotificationTarget?>(null)
    val pending: StateFlow<NotificationTarget?> = _pending.asStateFlow()

    fun open(target: NotificationTarget) {
        _pending.value = target
    }

    fun consume(target: NotificationTarget) {
        _pending.compareAndSet(target, null)
    }
}
