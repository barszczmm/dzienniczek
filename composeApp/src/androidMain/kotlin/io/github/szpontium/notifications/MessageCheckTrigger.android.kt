package io.github.szpontium.notifications

import io.github.szpontium.platform.appContext

actual fun triggerMessageCheckNow() {
    appContext?.let { MessageCheckWorker.runNow(it) }
}
