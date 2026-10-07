package io.github.szpontium

import android.app.Application
import io.github.szpontium.di.initKoin
import io.github.szpontium.notifications.MessageCheckWorker
import io.github.szpontium.notifications.MessageNotifier
import io.github.szpontium.platform.initAppContext
import io.github.szpontium.session.initAndroidDataStoreContext

class SzpontApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initAppContext(this)
        initAndroidDataStoreContext(this)
        initKoin()
        MessageNotifier.createChannel(this)
        MessageCheckWorker.schedule(this)
    }
}
