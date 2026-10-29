package unloop.launcher.subscription.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import unloop.launcher.subscription.SubscriptionRepository
import java.util.concurrent.TimeUnit

class SubscriptionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repository = SubscriptionRepository(applicationContext)
            val result = repository.synchronizeEntitlement(forceServerCheck = true)
            if (result.isSuccess) {
                Result.success()
            } else {
                // If offline or network issue, retry with exponential backoff
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "unloop_subscription_sync_periodic"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            // Run periodic reconciliation once every 24 hours when connected and battery not low
            val syncRequest = PeriodicWorkRequestBuilder<SubscriptionSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
