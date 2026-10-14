package io.github.barszczmm.dzienniczek.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.barszczmm.dzienniczek.di.initKoin
import io.github.barszczmm.dzienniczek.settings.AppSettings
import io.github.barszczmm.dzienniczek.settings.BackgroundCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit

/**
 * Checks Librus / eduVulcan for new messages and the other kinds chosen in the settings
 * (grades, exams, …) and posts them as notifications.
 *
 * Instead of a fixed periodic job, each run schedules the next one after a random
 * delay (about an hour, only during the day – see [BackgroundSchedule]), so requests
 * don't hit the servers at a regular rhythm. Runs only when enabled in the settings.
 */
class MessageCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        initKoin()
        val koin = KoinPlatform.getKoin()
        val isScheduledRun = tags.contains(TAG_SCHEDULED)
        val settings = koin.get<AppSettings>()
        val pollingEnabled = settings.isMessagePollingEnabled()
        if (isScheduledRun && !pollingEnabled) return Result.success()
        // A scheduled run delayed into the night (e.g. by battery saving) only plans the next one.
        if (isScheduledRun && !BackgroundSchedule.isActive()) {
            scheduleNext(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
            return Result.success()
        }

        var found = 0
        try {
            val checks = settings.backgroundChecks.first()
            if (BackgroundCheck.MESSAGES in checks) {
                val messages = koin.get<MessageChecker>().check()
                messages.forEach { MessageNotifier.show(applicationContext, it) }
                found += messages.size
            }
            val updates = koin.get<JournalChecker>().check(checks)
            updates.forEach { MessageNotifier.show(applicationContext, it) }
            found += updates.size
        } finally {
            // This run is still active, so the next one is appended after it.
            if (isScheduledRun && pollingEnabled) scheduleNext(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success(workDataOf(KEY_FOUND to found))
    }

    companion object {
        private const val SCHEDULED_WORK = "message-check-scheduled"
        private const val ONE_TIME_WORK = "message-check-now"
        private const val LEGACY_PERIODIC_WORK = "message-check"
        private const val TAG_SCHEDULED = "scheduled"
        private const val KEY_FOUND = "found"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Schedules the next check after a random delay. [policy]: KEEP at app start (keep an
         * already waiting check), REPLACE when the setting is turned on, APPEND_OR_REPLACE
         * from the running worker.
         */
        fun scheduleNext(context: Context, policy: ExistingWorkPolicy) {
            val delayMinutes = BackgroundSchedule.nextDelayMinutes()
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

        /** Progress of the check started with [runNow], for the settings screen. */
        fun runNowState(context: Context): Flow<CheckNowState> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK).map { infos ->
                val info = infos.lastOrNull() ?: return@map CheckNowState.Idle
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> CheckNowState.Waiting
                    WorkInfo.State.RUNNING -> CheckNowState.Running
                    WorkInfo.State.SUCCEEDED -> CheckNowState.Done(info.outputData.getInt(KEY_FOUND, 0))
                    else -> CheckNowState.Idle
                }
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
