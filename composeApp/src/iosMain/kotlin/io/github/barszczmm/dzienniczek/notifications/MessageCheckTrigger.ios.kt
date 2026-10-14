package io.github.barszczmm.dzienniczek.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Background message checks are implemented only on Android.
actual fun triggerMessageCheckNow() {}

actual suspend fun setBackgroundMessageChecks(enabled: Boolean) {}

actual fun checkNowState(): Flow<CheckNowState> = flowOf(CheckNowState.Idle)
