package io.github.barszczmm.dzienniczek.update

import io.github.barszczmm.dzienniczek.platform.appContext

actual fun getAppVersion(): String {
    return try {
        appContext?.let { context ->
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.2.0"
        } ?: "0.2.0"
    } catch (e: Exception) {
        "0.2.0"
    }
}
