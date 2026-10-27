package moe.shizuku.manager.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Process as AndroidProcess
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object ShizukuPidResolver {

    // Runtime.exec starts this shell as a direct child of the Shizuku server.
    // Its parent is authoritative, unlike pidof/pgrep which can select the manager
    // or another user's server. Read raw proc data without depending on awk/pgrep.
    internal val PROCESS_INFO_COMMAND = """
        pid=${'$'}PPID
        printf '%s\n' "${'$'}pid"
        cat "/proc/${'$'}pid/stat" 2>/dev/null || printf '\n'
        cat /proc/uptime 2>/dev/null || printf '\n'
    """.trimIndent()

    data class ProcessInfo(
        val pid: Int,
        val runtimeSeconds: Long? = null
    )

    fun resolveProcessInfo(context: Context): ProcessInfo {
        if (!Shizuku.pingBinder()) return ProcessInfo(-1, null)

        val remoteInfo = resolveFromRemoteCommand()
        if (remoteInfo != null && remoteInfo.pid > 0) return remoteInfo

        val pid = resolveFromActivityManager(context)
        return ProcessInfo(pid, null)
    }

    fun resolve(context: Context): Int {
        return resolveProcessInfo(context).pid
    }

    private fun resolveFromRemoteCommand(): ProcessInfo? {
        return try {
            // Reference the AIDL API directly so R8 can retain and rename it safely.
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder()) ?: return null
            val ticksPerSecond = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(-1)
            val process = service.newProcess(arrayOf("sh", "-c", PROCESS_INFO_COMMAND), null, null)
                ?: return null
            readRemoteProcess(process, ticksPerSecond)
        } catch (e: Exception) {
            Logger.LOGGER.w("Unable to read Shizuku process information", e)
            null
        }
    }

    internal fun readRemoteProcess(process: IRemoteProcess, ticksPerSecond: Long): ProcessInfo? {
        try {
            // Output is only a PID and two proc lines, well below the pipe capacity.
            // Wait before reading so a stuck command cannot block the caller forever.
            if (!process.waitForTimeout(3, TimeUnit.SECONDS.name)) return null
            val fd = process.inputStream ?: return null
            val text = ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
            return parseProcessInfo(text, ticksPerSecond)
        } finally {
            runCatching { process.destroy() }
        }
    }

    internal fun parseProcessInfo(text: String, ticksPerSecond: Long): ProcessInfo? {
        val lines = text.lineSequence().toList()
        val pid = lines.firstOrNull()?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val unknownRuntime = ProcessInfo(pid)
        if (ticksPerSecond <= 0) return unknownRuntime
        val stat = lines.getOrNull(1) ?: return unknownRuntime
        if (stat.substringBefore(' ').toIntOrNull() != pid) return unknownRuntime
        // comm (field 2) may itself contain spaces and parentheses. Field 22 is
        // index 19 after the closing ") ", not word 22 in the original line.
        val endOfName = stat.lastIndexOf(") ")
        if (endOfName < 0) return unknownRuntime
        val fields = stat.substring(endOfName + 2).trim().split(Regex("\\s+"))
        val startTicks = fields.getOrNull(19)?.toLongOrNull()?.takeIf { it >= 0 } ?: return unknownRuntime
        val uptime = lines.getOrNull(2)?.trim()?.substringBefore(' ')?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0 } ?: return unknownRuntime
        val runtime = uptime - startTicks.toDouble() / ticksPerSecond
        return ProcessInfo(pid, runtime.takeIf { it >= 0 }?.toLong())
    }

    private fun resolveFromActivityManager(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java) ?: return -1
        val processes = am.runningAppProcesses ?: return -1
        val myPid = AndroidProcess.myPid()

        for (p in processes) {
            if (p.pid == myPid) continue
            val name = p.processName ?: continue
            if (name == "shizuku_server") return p.pid
        }
        return -1
    }
}

