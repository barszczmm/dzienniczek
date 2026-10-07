package io.github.barszczmm.dzienniczek.di

import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools

/**
 * Starts the global Koin context once per process. Called from the Android Application
 * (so background workers can use it without any UI) and from [io.github.barszczmm.dzienniczek.App].
 */
fun initKoin() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        startKoin { modules(appModule) }
    }
}
