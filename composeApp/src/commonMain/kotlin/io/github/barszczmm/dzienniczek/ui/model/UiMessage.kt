package io.github.barszczmm.dzienniczek.ui.model

import kotlinx.datetime.LocalDateTime

data class UiMessage(
    val id: String,
    val title: String,
    val senderOrRecipient: String,
    val date: LocalDateTime?,
    val isUnread: Boolean,
    val hasAttachments: Boolean,
    val content: String? = null
)
