package io.github.barszczmm.dzienniczek

import android.app.Application
import androidx.work.ExistingWorkPolicy
import io.github.barszczmm.dzienniczek.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import io.github.barszczmm.dzienniczek.di.initKoin
import io.github.barszczmm.dzienniczek.notifications.MessageCheckWorker
import io.github.barszczmm.dzienniczek.notifications.MessageNotifier
import io.github.barszczmm.dzienniczek.platform.initAppContext
import io.github.barszczmm.dzienniczek.session.initAndroidDataStoreContext

class DzienniczekApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initAppContext(this)
        initAndroidDataStoreContext(this)
        initKoin()
        MessageNotifier.createChannel(this)
        restoreBackgroundMessageChecks()
    }

    /** Keeps the background message checks in line with the setting (off by default). */
    private fun restoreBackgroundMessageChecks() {
        appScope.launch {
            val enabled = KoinPlatform.getKoin().get<AppSettings>().isMessagePollingEnabled()
            if (enabled) {
                MessageCheckWorker.scheduleNext(this@DzienniczekApp, ExistingWorkPolicy.KEEP)
            } else {
                MessageCheckWorker.cancel(this@DzienniczekApp)
            }
        }
    }
}
