package moe.shizuku.manager.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Process as AndroidProcess
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object ShizukuPidResolver {

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
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val m = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true

            val process = m.invoke(
                null,
                arrayOf(
                    "sh",
                    "-c",
                    "pid=${'$'}(" +
                        "pidof shizuku_server 2>/dev/null || " +
                        "pidof moe.shizuku.privileged.api 2>/dev/null || " +
                        "pgrep -f 'moe.shizuku.privileged.api|shizuku_server' | head -n1 || " +
                        "ps -A 2>/dev/null | grep -E 'moe\\.shizuku\\.privileged\\.api|shizuku_server' | grep -v grep | head -n1 | awk '{print ${'$'}2}'" +
                    "); " +
                        "if [ -z \"${'$'}pid\" ]; then exit 1; fi; " +
                        "hz=${'$'}(getconf CLK_TCK 2>/dev/null || echo 100); " +
                        "up=${'$'}(cut -d' ' -f1 /proc/uptime); " +
                        "st=${'$'}(awk '{print ${'$'}22}' /proc/${'$'}pid/stat 2>/dev/null); " +
                        "if [ -z \"${'$'}st\" ]; then echo \"${'$'}pid\"; exit 0; fi; " +
                        "rt=${'$'}(awk -v up=\"${'$'}up\" -v st=\"${'$'}st\" -v hz=\"${'$'}hz\" 'BEGIN{printf \"%.0f\", up - (st/hz)}'); " +
                        "echo \"${'$'}pid ${'$'}rt\""
                ),
                null,
                null
            ) ?: return null

            val inputStream = runCatching {
                process.javaClass.getMethod("getInputStream").invoke(process) as InputStream
            }.getOrNull() ?: return null

            val text = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }.trim()
            runCatching { process.javaClass.getMethod("destroy").invoke(process) }

            val parts = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            val pid = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val runtime = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it >= 0 }
            ProcessInfo(pid, runtime)
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveFromActivityManager(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java) ?: return -1
        val processes = am.runningAppProcesses ?: return -1
        val myPid = AndroidProcess.myPid()

        for (p in processes) {
            if (p.pid == myPid) continue
            val name = p.processName ?: continue
            if (name == "shizuku_server") return p.pid
            if (name.contains("moe.shizuku.privileged.api", ignoreCase = true)) return p.pid
            if (p.pkgList?.contains("moe.shizuku.privileged.api") == true) return p.pid
        }
        return -1
    }
}

