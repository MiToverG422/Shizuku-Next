package moe.shizuku.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.home.HomeActivity
import moe.shizuku.manager.utils.ServiceStarter
import moe.shizuku.manager.utils.ShizukuPidResolver
import rikka.shizuku.Shizuku

class KeepAliveService : Service() {

    companion object {
        private const val ACTION_START = "moe.shizuku.manager.action.KEEP_ALIVE_START"
        private const val ACTION_STOP = "moe.shizuku.manager.action.KEEP_ALIVE_STOP"
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1003
        private const val RESTART_COOLDOWN_MS = 20_000L

        fun start(context: android.content.Context) {
            val intent = Intent(context, KeepAliveService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pid = -1
    private var pidSinceElapsed = 0L
    private var lastKnownPid = -1
    private var lastRestartAttemptElapsed = 0L
    @Volatile
    private var restartInFlight = false

    private val updateTask = object : Runnable {
        override fun run() {
            val enabled = ShizukuSettings.getPreferences()
                .getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)
            if (!enabled) {
                stopSelf()
                return
            }

            val alive = Shizuku.pingBinder()
            if (!alive) {
                maybeRestartInBackground()
            }
            updateNotification(alive)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ShizukuSettings.getPreferences().edit()
                    .putBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)
                    .apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                } catch (_: ForegroundServiceStartNotAllowedException) {
                    stopSelf()
                    return START_NOT_STICKY
                } catch (_: SecurityException) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                handler.removeCallbacks(updateTask)
                handler.post(updateTask)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateTask)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_keep_alive),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun updateNotification(alive: Boolean = Shizuku.pingBinder()) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(alive))
    }

    private fun buildNotification(alive: Boolean = Shizuku.pingBinder()): Notification {
        val processInfo = if (alive) ShizukuPidResolver.resolveProcessInfo(this) else null
        val latestPid = processInfo?.pid ?: -1
        val now = SystemClock.elapsedRealtime()

        if (latestPid > 0) {
            if (latestPid != pid) {
                pid = latestPid
                pidSinceElapsed = processInfo?.runtimeSeconds?.let { now - it * 1000L } ?: now
            }
            lastKnownPid = latestPid
            val runtimeSec = processInfo?.runtimeSeconds
            if (runtimeSec != null && runtimeSec >= 0) {
                pidSinceElapsed = now - runtimeSec * 1000L
            }
        } else {
            if (!alive) {
                pid = -1
                pidSinceElapsed = 0L
                lastKnownPid = -1
            }
        }

        val displayPid = if (pid > 0) pid else lastKnownPid
        val content = if (displayPid > 0) {
            getString(
                R.string.keep_alive_notification_content_running,
                displayPid,
                formatDuration(now - pidSinceElapsed)
            )
        } else {
            getString(R.string.keep_alive_notification_content_waiting)
        }
        val title = if (displayPid > 0) {
            getString(R.string.keep_alive_notification_title_running)
        } else {
            getString(R.string.keep_alive_notification_title_not_running)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(getColor(R.color.notification))
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun maybeRestartInBackground() {
        val appInForeground = (applicationContext as? ShizukuApplication)?.isAppInForeground() == true
        if (appInForeground) return
        if (restartInFlight) return
        if (SystemClock.elapsedRealtime() - lastRestartAttemptElapsed < RESTART_COOLDOWN_MS) return
        restartInFlight = true
        lastRestartAttemptElapsed = SystemClock.elapsedRealtime()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServiceStarter.tryStartByLastMode()
            } finally {
                restartInFlight = false
            }
        }
    }

    private fun formatDuration(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val h = totalSeconds / 3600L
        val m = (totalSeconds % 3600L) / 60L
        val s = totalSeconds % 60L
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
