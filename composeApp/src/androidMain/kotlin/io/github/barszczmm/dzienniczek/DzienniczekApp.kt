package io.github.barszczmm.dzienniczek

import android.app.Application
import io.github.barszczmm.dzienniczek.di.initKoin
import io.github.barszczmm.dzienniczek.notifications.MessageCheckWorker
import io.github.barszczmm.dzienniczek.notifications.MessageNotifier
import io.github.barszczmm.dzienniczek.platform.initAppContext
import io.github.barszczmm.dzienniczek.session.initAndroidDataStoreContext

class DzienniczekApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initAppContext(this)
        initAndroidDataStoreContext(this)
        initKoin()
        MessageNotifier.createChannel(this)
        MessageCheckWorker.schedule(this)
    }
}
