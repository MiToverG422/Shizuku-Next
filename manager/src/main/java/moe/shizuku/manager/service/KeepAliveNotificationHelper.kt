package moe.shizuku.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.home.HomeActivity
import moe.shizuku.manager.utils.ShizukuPidResolver
import rikka.shizuku.Shizuku

object KeepAliveNotificationHelper {

    private const val CHANNEL_ID = "keep_alive"
    private const val NOTIFICATION_ID = 1003
    private const val TICK_INTERVAL_MS = 1000L
    private const val STATE_REFRESH_INTERVAL_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var pid = -1
    private var pidSinceElapsed = 0L
    private var lastKnownPid = -1
    private var lastStateRefreshElapsed = 0L
    private var tickerContext: Context? = null
    private var tickerRunning = false

    private val tickerTask = object : Runnable {
        override fun run() {
            val context = tickerContext ?: return
            if (!ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)) {
                cancel(context)
                return
            }

            val now = SystemClock.elapsedRealtime()
            if (now - lastStateRefreshElapsed >= STATE_REFRESH_INTERVAL_MS || pid <= 0) {
                refreshState(context, Shizuku.pingBinder())
            }
            notify(context)
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    fun update(context: Context, alive: Boolean) {
        ensureChannel(context)
        refreshState(context.applicationContext, alive)
        notify(context.applicationContext)
        startTicker(context)
    }

    fun startTicker(context: Context) {
        tickerContext = context.applicationContext
        ensureChannel(tickerContext!!)
        if (tickerRunning) return
        tickerRunning = true
        handler.removeCallbacks(tickerTask)
        handler.post(tickerTask)
    }

    fun cancel(context: Context) {
        tickerRunning = false
        tickerContext = null
        handler.removeCallbacks(tickerTask)
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_keep_alive),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun refreshState(context: Context, alive: Boolean) {
        val processInfo = if (alive) ShizukuPidResolver.resolveProcessInfo(context) else null
        val latestPid = processInfo?.pid ?: -1
        val now = SystemClock.elapsedRealtime()
        lastStateRefreshElapsed = now

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
        } else if (!alive) {
            pid = -1
            pidSinceElapsed = 0L
            lastKnownPid = -1
        }
    }

    private fun notify(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(context))
    }

    private fun buildNotification(context: Context): Notification {
        val now = SystemClock.elapsedRealtime()
        val displayPid = if (pid > 0) pid else lastKnownPid
        val title = if (displayPid > 0) {
            context.getString(R.string.keep_alive_notification_title_running)
        } else {
            context.getString(R.string.keep_alive_notification_title_not_running)
        }
        val content = if (displayPid > 0) {
            context.getString(
                R.string.keep_alive_notification_content_running,
                displayPid,
                formatDuration(now - pidSinceElapsed)
            )
        } else {
            context.getString(R.string.keep_alive_notification_content_waiting)
        }

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(context.getColor(R.color.notification))
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)

        if (displayPid > 0) {
            builder.setShowWhen(false)
        } else {
            builder.setShowWhen(false)
        }

        return builder.build()
    }

    private fun formatDuration(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val h = totalSeconds / 3600L
        val m = (totalSeconds % 3600L) / 60L
        val s = totalSeconds % 60L
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
