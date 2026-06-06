package moe.shizuku.manager.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import moe.shizuku.manager.ShizukuSettings
import rikka.shizuku.Shizuku

class KeepAliveTaskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isKeepAliveEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        KeepAliveNotificationHelper.startTicker(this)
        KeepAliveWorker.schedule(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isKeepAliveEnabled()) {
            val appContext = applicationContext
            KeepAliveNotificationHelper.update(appContext, Shizuku.pingBinder())
            KeepAliveRestoreReceiver.schedule(appContext)
            KeepAliveWorker.schedule(appContext)
            KeepAliveWorker.runNow(appContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun isKeepAliveEnabled(): Boolean {
        return ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)
    }

    companion object {
        fun start(context: Context) {
            if (!ShizukuSettings.getPreferences()
                    .getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)
            ) {
                return
            }

            runCatching {
                context.applicationContext.startService(
                    Intent(context.applicationContext, KeepAliveTaskService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, KeepAliveTaskService::class.java)
                )
            }
        }
    }
}
