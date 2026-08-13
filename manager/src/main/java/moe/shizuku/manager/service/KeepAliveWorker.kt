package moe.shizuku.manager.service

import android.content.Context
import android.os.Build
import android.os.UserManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.ShizukuSettings
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

class KeepAliveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "keep_alive_periodic_work"
        private const val UNIQUE_IMMEDIATE_WORK = "keep_alive_immediate_work"

        fun schedule(context: Context) {
            if (!isUserUnlocked(context)) return

            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.NONE)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runNow(context: Context) {
            if (!isUserUnlocked(context)) return

            val request = OneTimeWorkRequestBuilder<KeepAliveWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            if (!isUserUnlocked(context)) return

            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            wm.cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
        }

        private fun isUserUnlocked(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
                context.getSystemService(UserManager::class.java)?.isUserUnlocked == true
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = ShizukuSettings.getPreferences()
        val keepAliveEnabled = prefs.getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)
        if (!keepAliveEnabled) {
            KeepAliveNotificationHelper.cancel(applicationContext)
            return@withContext Result.success()
        }

        val alive = Shizuku.pingBinder()
        KeepAliveNotificationHelper.update(applicationContext, alive)
        Result.success()
    }
}
