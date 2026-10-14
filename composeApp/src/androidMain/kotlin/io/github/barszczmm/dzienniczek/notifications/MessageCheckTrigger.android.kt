package io.github.barszczmm.dzienniczek.notifications

import androidx.work.ExistingWorkPolicy
import io.github.barszczmm.dzienniczek.platform.appContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual fun triggerMessageCheckNow() {
    appContext?.let { MessageCheckWorker.runNow(it) }
}

actual fun checkNowState(): Flow<CheckNowState> =
    appContext?.let { MessageCheckWorker.runNowState(it) } ?: flowOf(CheckNowState.Idle)

actual suspend fun setBackgroundMessageChecks(enabled: Boolean) {
    val context = appContext ?: return
    if (enabled) MessageCheckWorker.scheduleNext(context, ExistingWorkPolicy.REPLACE) else MessageCheckWorker.cancel(context)
}
