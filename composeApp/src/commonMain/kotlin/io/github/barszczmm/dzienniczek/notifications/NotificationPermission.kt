package io.github.barszczmm.dzienniczek.notifications

import androidx.compose.runtime.Composable

/** Returns a function that asks for the permission to show notifications (when needed). */
@Composable
expect fun rememberNotificationPermissionRequest(): () -> Unit
