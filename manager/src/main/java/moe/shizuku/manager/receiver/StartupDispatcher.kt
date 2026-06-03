package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.KEEP_START_ON_BOOT
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.service.KeepAliveWorker
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object StartupDispatcher {

    const val SOURCE_BROADCAST = "broadcast"

    fun startIfNeeded(context: Context, source: String) {
        if (!ShizukuSettings.getPreferences().getBoolean(KEEP_START_ON_BOOT, false)) return
        if (UserHandleCompat.myUserId() > 0) return

        if (source == SOURCE_BROADCAST &&
            ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)
        ) {
            KeepAliveWorker.schedule(context)
            KeepAliveWorker.runNow(context)
        }

        if (Shizuku.pingBinder()) return

        val mode = ShizukuSettings.getPreferences()
            .getString(ShizukuSettings.STARTUP_MODE, ShizukuSettings.StartupMode.NONE)
            ?: ShizukuSettings.StartupMode.NONE

        if (mode == ShizukuSettings.StartupMode.NONE) return
        if (mode == ShizukuSettings.StartupMode.BROADCAST && source != SOURCE_BROADCAST) return

        if (mode == ShizukuSettings.StartupMode.SCRIPT) {
            rootStart()
            return
        }

        if (ShizukuSettings.getLastLaunchMode() == LaunchMethod.ROOT) {
            rootStart()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
            && ShizukuSettings.getLastLaunchMode() == LaunchMethod.ADB
        ) {
            adbStart(context)
        } else {
            Log.w(AppConstants.TAG, "Boot auto start: keep-alive only (no root/adb path available), mode=$mode source=$source")
        }
    }

    private fun rootStart() {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return
        }
        Shell.cmd(Starter.internalCommand).exec()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun adbStart(context: Context) {
        val cr = context.contentResolver
        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
        Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
        CoroutineScope(Dispatchers.IO).launch {
            val latch = CountDownLatch(1)
            val adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { port ->
                if (port <= 0) return@AdbMdns
                try {
                    val keystore = PreferenceAdbKeyStore(ShizukuSettings.getPreferences())
                    val key = AdbKey(keystore, "shizuku")
                    val client = AdbClient("127.0.0.1", port, key)
                    client.connect()
                    client.shellCommand(Starter.internalCommand, null)
                    client.close()
                } catch (_: Exception) {
                }
                latch.countDown()
            }
            if (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1) {
                adbMdns.start()
                latch.await(3, TimeUnit.SECONDS)
                adbMdns.stop()
            }
        }
    }
}
