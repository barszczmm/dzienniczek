package io.github.barszczmm.dzienniczek.notifications

import androidx.work.ExistingWorkPolicy
import io.github.barszczmm.dzienniczek.platform.appContext

actual fun triggerMessageCheckNow() {
    appContext?.let { MessageCheckWorker.runNow(it) }
}

actual fun setBackgroundMessageChecks(enabled: Boolean) {
    val context = appContext ?: return
    if (enabled) MessageCheckWorker.scheduleNext(context, ExistingWorkPolicy.REPLACE) else MessageCheckWorker.cancel(context)
}
