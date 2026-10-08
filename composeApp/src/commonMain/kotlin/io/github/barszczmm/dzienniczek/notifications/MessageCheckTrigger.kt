package io.github.barszczmm.dzienniczek.notifications

/** Runs the new-message check right away (no-op where not supported). */
expect fun triggerMessageCheckNow()

/** Starts or stops the background new-message checks (no-op where not supported). */
expect fun setBackgroundMessageChecks(enabled: Boolean)
