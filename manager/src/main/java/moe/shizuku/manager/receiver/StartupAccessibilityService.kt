package moe.shizuku.manager.receiver

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class StartupAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        StartupDispatcher.startIfNeeded(this, StartupDispatcher.SOURCE_ACCESSIBILITY)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
