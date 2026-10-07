package io.github.szpontium.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.szpontium.di.initKoin
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit

/** Periodically checks Librus / eduVulcan for new messages and posts them as notifications. */
class MessageCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        initKoin()
        val checker = KoinPlatform.getKoin().get<MessageChecker>()
        val newMessages = checker.check()
        newMessages.forEach { MessageNotifier.show(applicationContext, it) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK = "message-check"
        private const val ONE_TIME_WORK = "message-check-now"

        /** Android does not allow periodic work more often than every 15 minutes. */
        private const val INTERVAL_MINUTES = 15L

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MessageCheckWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
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
