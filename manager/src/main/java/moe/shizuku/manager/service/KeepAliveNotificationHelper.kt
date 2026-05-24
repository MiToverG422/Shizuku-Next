package moe.shizuku.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import moe.shizuku.manager.R
import moe.shizuku.manager.home.HomeActivity
import moe.shizuku.manager.utils.ShizukuPidResolver

object KeepAliveNotificationHelper {

    private const val CHANNEL_ID = "keep_alive"
    private const val NOTIFICATION_ID = 1003

    private var pid = -1
    private var pidSinceElapsed = 0L
    private var lastKnownPid = -1

    fun update(context: Context, alive: Boolean) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(context, alive))
    }

    fun cancel(context: Context) {
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

    private fun buildNotification(context: Context, alive: Boolean): Notification {
        val processInfo = if (alive) ShizukuPidResolver.resolveProcessInfo(context) else null
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
        } else if (!alive) {
            pid = -1
            pidSinceElapsed = 0L
            lastKnownPid = -1
        }

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

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(context.getColor(R.color.notification))
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun formatDuration(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val h = totalSeconds / 3600L
        val m = (totalSeconds % 3600L) / 60L
        val s = totalSeconds % 60L
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
