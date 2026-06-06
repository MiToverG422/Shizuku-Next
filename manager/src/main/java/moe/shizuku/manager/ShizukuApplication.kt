package moe.shizuku.manager

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.service.KeepAliveNotificationHelper
import moe.shizuku.manager.service.KeepAliveTaskService
import moe.shizuku.manager.service.KeepAliveWorker
import moe.shizuku.manager.utils.ServiceStarter
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.core.util.BuildUtils.atLeast30
import rikka.material.app.LocaleDelegate
import rikka.shizuku.Shizuku

lateinit var application: ShizukuApplication

class ShizukuApplication : Application(), Configuration.Provider {

    private val backgroundMonitorHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var restartInFlight = false
    private var lastRestartAttemptElapsed = 0L
    private val backgroundMonitorTask = object : Runnable {
        override fun run() {
            if (!isBackgroundRestartEnabled() || isAppInForeground()) return
            if (!Shizuku.pingBinder()) {
                maybeRestartInBackground()
            }
            backgroundMonitorHandler.postDelayed(this, 8_000L)
        }
    }

    @Volatile
    private var startedActivityCount = 0

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    fun isAppInForeground(): Boolean = startedActivityCount > 0

    private fun isBackgroundRestartEnabled(): Boolean {
        return ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.AUTO_RESTART_IN_BACKGROUND, false)
    }

    private fun scheduleBackgroundMonitor() {
        backgroundMonitorHandler.removeCallbacks(backgroundMonitorTask)
        if (isBackgroundRestartEnabled()) {
            backgroundMonitorHandler.postDelayed(backgroundMonitorTask, 5_000L)
        }
    }

    private fun maybeRestartInBackground() {
        if (!isBackgroundRestartEnabled() || isAppInForeground()) return
        if (restartInFlight) return
        if (SystemClock.elapsedRealtime() - lastRestartAttemptElapsed < 20_000L) return
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

    companion object {

        init {
            logd("ShizukuApplication", "init")

            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }
    }

    private fun init(context: Context?) {
        ShizukuSettings.initialize(context)
        LocaleDelegate.defaultLocale = ShizukuSettings.getLocale()
        AppCompatDelegate.setDefaultNightMode(ShizukuSettings.getNightMode())
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivityCount++
                backgroundMonitorHandler.removeCallbacks(backgroundMonitorTask)
            }
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) {
                if (startedActivityCount > 0) startedActivityCount--
                if (startedActivityCount == 0) {
                    scheduleBackgroundMonitor()
                }
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        if (ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)) {
            KeepAliveNotificationHelper.startTicker(this)
            KeepAliveTaskService.start(this)
            KeepAliveWorker.schedule(this)
            KeepAliveWorker.runNow(this)
        }
    }

}
