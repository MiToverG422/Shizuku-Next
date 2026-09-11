package moe.shizuku.manager.adb

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import rikka.core.ktx.unsafeLazy
import java.net.ConnectException

@TargetApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    companion object {

        const val notificationChannel = "adb_pairing"

        private const val tag = "AdbPairingService"

        private const val notificationId = 1
        private const val replyRequestId = 1
        private const val stopRequestId = 2
        private const val retryRequestId = 3
        private const val startAction = "start"
        private const val stopAction = "stop"
        private const val replyAction = "reply"
        private const val remoteInputResultKey = "paring_code"
        private const val portKey = "paring_code"
        internal const val SEARCH_TIMEOUT_MS = 150_000L

        fun startIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(startAction)
        }

        private fun stopIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(stopAction)
        }

        private fun replyIntent(context: Context, port: Int): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(replyAction).putExtra(portKey, port)
        }
    }

    private var adbMdns: AdbMdns? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pairingJob: Job? = null
    private var ending = false
    private val timeout = Runnable { finishSession() }

    private val observer = Observer<Int> { port ->
        Log.i(tag, "Pairing service port: $port")
        mainHandler.post {
            if (port > 0 && !ending) {
                // Ignore late discovery callbacks after timeout/destruction.
                getSystemService(NotificationManager::class.java).notify(notificationId, createInputNotification(port))
            }
        }
    }

    private var started = false

    override fun onCreate() {
        super.onCreate()

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                notificationChannel,
                getString(R.string.notification_channel_adb_pairing),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                setAllowBubbles(false)
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ending) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = when (intent?.action) {
            startAction -> {
                onStart()
            }
            replyAction -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(remoteInputResultKey) ?: ""
                val port = intent.getIntExtra(portKey, -1)
                if (port != -1) {
                    onInput(code.toString(), port)
                } else {
                    onStart()
                }
            }
            stopAction -> {
                finishSession()
                null
            }
            else -> {
                finishSession()
                return START_NOT_STICKY
            }
        }
        if (notification != null) {
            try {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
                mainHandler.removeCallbacks(timeout)
                mainHandler.postDelayed(timeout, SEARCH_TIMEOUT_MS)
                if (notification === searchingNotification) startSearch()
            } catch (e: Throwable) {
                Log.e(tag, "startForeground failed", e)

                finishSession()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) = finishSession()

    override fun onTimeout(startId: Int, fgsType: Int) = finishSession()

    private fun finishSession() {
        if (ending) return
        ending = true
        mainHandler.removeCallbacks(timeout)
        pairingJob?.cancel()
        // Stop the foreground service immediately; never wait for network cleanup.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        stopSearch()
    }

    private fun startSearch() {
        if (started) return
        started = true
        adbMdns = AdbMdns(this, AdbMdns.TLS_PAIRING, observer).apply { start() }
    }

    private fun stopSearch() {
        if (!started) return
        started = false
        runCatching { adbMdns?.stop() }.onFailure { Log.w(tag, "Discovery cleanup failed", it) }
        adbMdns = null
    }

    override fun onDestroy() {
        ending = true
        mainHandler.removeCallbacks(timeout)
        serviceScope.cancel()
        stopSearch()
        super.onDestroy()
    }

    private fun onStart(): Notification = searchingNotification

    private fun onInput(code: String, port: Int): Notification {
        if (pairingJob?.isActive == true) return workingNotification
        pairingJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
                    AdbPairingClient("127.0.0.1", port, code, key).use { it.start() }
                }
            }
            if (!ending) handleResult(result.getOrDefault(false), result.exceptionOrNull())
        }

        return workingNotification
    }

    private fun handleResult(success: Boolean, exception: Throwable?) {
        ending = true
        mainHandler.removeCallbacks(timeout)
        stopSearch()
        stopForeground(STOP_FOREGROUND_REMOVE)

        val title: String
        val text: String?

        if (success) {
            Log.i(tag, "Pair succeed")

            title = getString(R.string.notification_adb_pairing_succeed_title)
            text = getString(R.string.notification_adb_pairing_succeed_text)

            stopSearch()
        } else {
            title = getString(R.string.notification_adb_pairing_failed_title)

            text = when (exception) {
                is ConnectException -> {
                    getString(R.string.cannot_connect_port)
                }
                is AdbInvalidPairingCodeException -> {
                    getString(R.string.paring_code_is_wrong)
                }
                is AdbKeyException -> {
                    getString(R.string.adb_error_key_store)
                }
                else -> {
                    exception?.let { Log.getStackTraceString(it) }
                }
            }

            if (exception != null) {
                Log.w(tag, "Pair failed", exception)
            } else {
                Log.w(tag, "Pair failed")
            }
        }

        getSystemService(NotificationManager::class.java).notify(
            notificationId,
            Notification.Builder(this, notificationChannel)
                .setColor(getColor(R.color.notification))
                .setSmallIcon(R.drawable.ic_system_icon)
                .setContentTitle(title)
                .setContentText(text)
                /*.apply {
                    if (!success) {
                        addAction(retryNotificationAction)
                    }
                }*/
                .build()
        )
        stopSelf()
    }

    private val stopNotificationAction by unsafeLazy {
        val pendingIntent = PendingIntent.getService(
            this,
            stopRequestId,
            stopIntent(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.notification_adb_pairing_stop_searching),
            pendingIntent
        )
            .build()
    }

    private val retryNotificationAction by unsafeLazy {
        val pendingIntent = PendingIntent.getService(
            this,
            retryRequestId,
            startIntent(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.notification_adb_pairing_retry),
            pendingIntent
        )
            .build()
    }

    private val replyNotificationAction by unsafeLazy {
        val remoteInput = RemoteInput.Builder(remoteInputResultKey).run {
            setLabel(getString(R.string.dialog_adb_pairing_paring_code))
            build()
        }

        val pendingIntent = PendingIntent.getForegroundService(
            this,
            replyRequestId,
            replyIntent(this, -1),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        Notification.Action.Builder(
            null,
            getString(R.string.notification_adb_pairing_input_paring_code),
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun replyNotificationAction(port: Int): Notification.Action {
        // Ensure pending intent is created
        val action = replyNotificationAction

        PendingIntent.getForegroundService(
            this,
            replyRequestId,
            replyIntent(this, port),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return action
    }

    private val searchingNotification by unsafeLazy {
        Notification.Builder(this, notificationChannel)
            .setColor(getColor(R.color.notification))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(getString(R.string.notification_adb_pairing_searching_for_service_title))
            .addAction(stopNotificationAction)
            .build()
    }

    private fun createInputNotification(port: Int): Notification {
        return Notification.Builder(this, notificationChannel)
            .setColor(getColor(R.color.notification))
            .setContentTitle(getString(R.string.notification_adb_pairing_service_found_title))
            .setSmallIcon(R.drawable.ic_system_icon)
            .addAction(replyNotificationAction(port))
            .build()
    }

    private val workingNotification by unsafeLazy {
        Notification.Builder(this, notificationChannel)
            .setColor(getColor(R.color.notification))
            .setContentTitle(getString(R.string.notification_adb_pairing_working_title))
            .setSmallIcon(R.drawable.ic_system_icon)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
