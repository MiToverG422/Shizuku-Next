package moe.shizuku.manager.utils

import android.app.Application
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShizukuPidResolverTest {
    @get:Rule val temp = TemporaryFolder()

    private fun output(pid: Int = 42, name: String = "main", startTicks: Long = 12300,
                       uptime: String = "200.75 100.00"): String {
        val fields = listOf("S") + List(18) { "0" } + startTicks.toString() + "0"
        return "$pid\n$pid ($name) ${fields.joinToString(" ")}\n$uptime\n"
    }

    @Test fun readsPidAndServerUptimeWithActualKernelTickRate() {
        assertEquals(ShizukuPidResolver.ProcessInfo(42, 77),
            ShizukuPidResolver.parseProcessInfo(output(), 100))
        assertEquals(ShizukuPidResolver.ProcessInfo(42, 188),
            ShizukuPidResolver.parseProcessInfo(output(), 1000))
    }

    @Test fun processNamesWithSpacesAndParenthesesDoNotShiftStartTime() {
        assertEquals(77L,
            ShizukuPidResolver.parseProcessInfo(output(name = "server (worker) name)"), 100)?.runtimeSeconds)
    }

    @Test fun unreadableStatOrClockRetainsKnownServerPidWithoutInventingUptime() {
        assertEquals(ShizukuPidResolver.ProcessInfo(42), ShizukuPidResolver.parseProcessInfo("42\n\n200.00\n", 100))
        assertEquals(ShizukuPidResolver.ProcessInfo(42), ShizukuPidResolver.parseProcessInfo(output(), -1))
        assertEquals(ShizukuPidResolver.ProcessInfo(42), ShizukuPidResolver.parseProcessInfo(output(uptime = "NaN"), 100))
        assertNull(ShizukuPidResolver.parseProcessInfo(output(startTicks = 999999), 100)?.runtimeSeconds)
    }

    @Test fun rejectsInvalidPidAndMismatchedProcData() {
        assertNull(ShizukuPidResolver.parseProcessInfo("\n", 100))
        assertNull(ShizukuPidResolver.parseProcessInfo("0\n", 100))
        assertNull(ShizukuPidResolver.parseProcessInfo("42 99\n", 100))
        assertEquals(ShizukuPidResolver.ProcessInfo(42),
            ShizukuPidResolver.parseProcessInfo(output().replace("42 (main)", "99 (main)"), 100))
    }

    @Test fun readsTypedRemoteProcessAndAlwaysDestroysIt() {
        val file = temp.newFile().apply { writeText(output()) }
        val process = FakeRemoteProcess(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        assertEquals(ShizukuPidResolver.ProcessInfo(42, 77), ShizukuPidResolver.readRemoteProcess(process, 100))
        assertTrue(process.destroyed)
    }

    @Test fun timedOutCommandIsDestroyedWithoutAttemptingToRead() {
        val process = FakeRemoteProcess(finished = false)
        assertNull(ShizukuPidResolver.readRemoteProcess(process, 100))
        assertFalse(process.readAttempted)
        assertTrue(process.destroyed)
    }

    @Test fun streamFailureStillDestroysRemoteProcess() {
        val process = FakeRemoteProcess()
        assertTrue(runCatching { ShizukuPidResolver.readRemoteProcess(process, 100) }.isFailure)
        assertTrue(process.destroyed)
    }

    private class FakeRemoteProcess(private val input: ParcelFileDescriptor? = null,
                                    private val finished: Boolean = true) : IRemoteProcess.Stub() {
        var destroyed = false
        var readAttempted = false
        override fun getInputStream(): ParcelFileDescriptor {
            readAttempted = true
            return checkNotNull(input)
        }
        override fun getOutputStream(): ParcelFileDescriptor? = null
        override fun getErrorStream(): ParcelFileDescriptor? = null
        override fun waitFor() = 0
        override fun exitValue() = 0
        override fun alive() = false
        override fun destroy() { destroyed = true }
        override fun waitForTimeout(timeout: Long, unit: String) = finished
    }
}
