package io.github.barszczmm.dzienniczek

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.barszczmm.dzienniczek.notifications.MessageNotifier
import io.github.barszczmm.dzienniczek.notifications.NotificationNavigation
import io.github.barszczmm.dzienniczek.session.initAndroidDataStoreContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidDataStoreContext(this)
        // Not again after a configuration change: the same intent is delivered once more then.
        if (savedInstanceState == null) openNotificationTarget(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotificationTarget(intent)
    }

    private fun openNotificationTarget(intent: Intent?) {
        MessageNotifier.targetFrom(intent)?.let(NotificationNavigation::open)
    }
}
