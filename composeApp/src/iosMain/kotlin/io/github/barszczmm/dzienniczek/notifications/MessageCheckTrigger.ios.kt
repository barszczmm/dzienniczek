package io.github.barszczmm.dzienniczek.notifications

// Background message checks are implemented only on Android.
actual fun triggerMessageCheckNow() {}

actual fun setBackgroundMessageChecks(enabled: Boolean) {}
