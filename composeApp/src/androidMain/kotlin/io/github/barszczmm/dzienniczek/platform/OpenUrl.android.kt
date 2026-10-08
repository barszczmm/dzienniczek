package io.github.barszczmm.dzienniczek.platform

import android.content.Intent
import android.net.Uri

actual fun openUrl(url: String) {
    val context = appContext ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
