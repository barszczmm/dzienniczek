package io.github.barszczmm.dzienniczek.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.barszczmm.dzienniczek.di.initKoin
import io.github.barszczmm.dzienniczek.settings.AppSettings
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Checks Librus / eduVulcan for new messages and posts them as notifications.
 *
 * Instead of a fixed periodic job, each run schedules the next one after a random
 * delay (about 30 minutes), so requests don't hit the servers at a regular rhythm.
 * Runs only when enabled in the settings.
 */
class MessageCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        initKoin()
        val koin = KoinPlatform.getKoin()
        val isScheduledRun = tags.contains(TAG_SCHEDULED)
        val pollingEnabled = koin.get<AppSettings>().isMessagePollingEnabled()
        if (isScheduledRun && !pollingEnabled) return Result.success()

        try {
            val newMessages = koin.get<MessageChecker>().check()
            newMessages.forEach { MessageNotifier.show(applicationContext, it) }
        } finally {
            // This run is still active, so the next one is appended after it.
            if (isScheduledRun && pollingEnabled) scheduleNext(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    companion object {
        private const val SCHEDULED_WORK = "message-check-scheduled"
        private const val ONE_TIME_WORK = "message-check-now"
        private const val LEGACY_PERIODIC_WORK = "message-check"
        private const val TAG_SCHEDULED = "scheduled"

        /** Random delay between checks: 25–40 minutes (≈30 on average). */
        private const val MIN_DELAY_MINUTES = 25
        private const val MAX_DELAY_MINUTES = 40

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Schedules the next check after a random delay. [policy]: KEEP at app start (keep an
         * already waiting check), REPLACE when the setting is turned on, APPEND_OR_REPLACE
         * from the running worker.
         */
        fun scheduleNext(context: Context, policy: ExistingWorkPolicy) {
            val delayMinutes = Random.nextInt(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES + 1).toLong()
            val request = OneTimeWorkRequestBuilder<MessageCheckWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(TAG_SCHEDULED)
                .build()
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK)
            workManager.enqueueUniqueWork(
                SCHEDULED_WORK,
                policy,
                request
            )
        }

        fun cancel(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(SCHEDULED_WORK)
            workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<MessageCheckWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
