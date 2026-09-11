package moe.shizuku.manager.adb

import android.app.Application
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdbPairingServiceTest {
    @Test fun android14TimeoutStopsServiceImmediately() {
        val controller = Robolectric.buildService(AdbPairingService::class.java).create()
        val service = controller.get()
        service.onTimeout(1)
        assertTrue(shadowOf(service).isStoppedBySelf)
        service.onTimeout(1) // Cleanup is idempotent.
        controller.destroy()
    }

    @Test @Config(sdk = [35]) fun android15TimeoutAlsoStopsService() {
        val controller = Robolectric.buildService(AdbPairingService::class.java).create()
        val service = controller.get()
        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test @Config(sdk = [30, 34]) fun watchdogStopsSearchWithoutDependingOnSystemCallback() {
        val controller = Robolectric.buildService(AdbPairingService::class.java).create()
        val service = controller.get()
        assertEquals(Service.START_NOT_STICKY,
            service.onStartCommand(AdbPairingService.startIntent(service), 0, 1))
        assertFalse(shadowOf(service).isStoppedBySelf)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(AdbPairingService.SEARCH_TIMEOUT_MS + 1))
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test fun destroyRemovesWatchdog() {
        val controller = Robolectric.buildService(AdbPairingService::class.java).create()
        val service = controller.get()
        service.onStartCommand(AdbPairingService.startIntent(service), 0, 1)
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(AdbPairingService.SEARCH_TIMEOUT_MS + 1))
        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test fun stopActionDoesNotRequestIntentRedelivery() {
        val controller = Robolectric.buildService(AdbPairingService::class.java).create()
        val service = controller.get()
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent().setAction("stop"), 0, 1))
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
}
