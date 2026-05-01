package moe.shizuku.manager.utils

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.starter.Starter

object ServiceStarter {

    suspend fun tryStartByLastMode(): Boolean = withContext(Dispatchers.IO) {
        val primary = ShizukuSettings.getLastLaunchMode()
        val fallback = when (primary) {
            ShizukuSettings.LaunchMethod.ROOT -> ShizukuSettings.LaunchMethod.ADB
            ShizukuSettings.LaunchMethod.ADB -> ShizukuSettings.LaunchMethod.ROOT
            else -> null
        }

        var started = runSilentStart(primary)
        if (!started && fallback != null) {
            started = runSilentStart(fallback)
        }
        started
    }

    private fun runSilentStart(@ShizukuSettings.LaunchMethod mode: Int): Boolean {
        return when (mode) {
            ShizukuSettings.LaunchMethod.ROOT -> {
                try {
                    if (!Shell.getShell().isRoot) {
                        Shell.getCachedShell()?.close()
                        return false
                    }
                    Shell.cmd(Starter.internalCommand).exec()
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            ShizukuSettings.LaunchMethod.ADB -> {
                try {
                    val port = EnvironmentUtils.getAdbTcpPort()
                    if (port <= 0) return false
                    val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
                    AdbClient("127.0.0.1", port, key).use { client ->
                        client.connect()
                        client.shellCommand(Starter.internalCommand, null)
                    }
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            else -> false
        }
    }
}

