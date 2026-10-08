package io.github.barszczmm.dzienniczek.platform

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens [url] in a browser Custom Tab (it shares the browser's sign-ins). A plain VIEW
 * intent made the eduVulcan web app land on its dashboard instead of the requested page.
 */
actual fun openUrl(url: String) {
    val context = appContext ?: return
    val uri = Uri.parse(url)
    runCatching {
        val customTab = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTab.launchUrl(context, uri)
    }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
