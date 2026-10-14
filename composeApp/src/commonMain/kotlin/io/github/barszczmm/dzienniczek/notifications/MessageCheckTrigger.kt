package io.github.barszczmm.dzienniczek.notifications

import kotlinx.coroutines.flow.Flow

/** Runs the new-message check right away (no-op where not supported). */
expect fun triggerMessageCheckNow()

/** State of the check started with [triggerMessageCheckNow]. */
sealed interface CheckNowState {
    data object Idle : CheckNowState
    /** Queued, e.g. waiting for a network connection. */
    data object Waiting : CheckNowState
    data object Running : CheckNowState
    /** [found] = number of notifications shown. */
    data class Done(val found: Int) : CheckNowState
}

expect fun checkNowState(): Flow<CheckNowState>

/** Starts or stops the background new-message checks (no-op where not supported). */
expect suspend fun setBackgroundMessageChecks(enabled: Boolean)
