package moe.shizuku.manager.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import moe.shizuku.manager.ShizukuSettings
import rikka.shizuku.Shizuku

class KeepAliveRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isKeepAliveEnabled()) return

        val appContext = context.applicationContext
        KeepAliveNotificationHelper.update(appContext, Shizuku.pingBinder())
        KeepAliveWorker.schedule(appContext)
        KeepAliveWorker.runNow(appContext)
    }

    private fun isKeepAliveEnabled(): Boolean {
        return ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)
    }

    companion object {
        private const val REQUEST_CODE = 1003
        private const val RESTORE_DELAY_MS = 1500L

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KeepAliveRestoreReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            val triggerAt = SystemClock.elapsedRealtime() + RESTORE_DELAY_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        }
    }
}
