package io.github.barszczmm.dzienniczek.notifications

import io.github.barszczmm.dzienniczek.platform.appContext

actual fun triggerMessageCheckNow() {
    appContext?.let { MessageCheckWorker.runNow(it) }
}
