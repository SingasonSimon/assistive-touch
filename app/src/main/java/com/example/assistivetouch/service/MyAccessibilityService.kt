package com.example.assistivetouch.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to specific events for the MVP.
    }

    override fun onInterrupt() {
        // No-op
    }

    fun performHomeAction() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performBackAction() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performRecentsAction() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun performLockScreenAction() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun performScreenshotAction() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    fun openNotificationsPanel() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    companion object {
        @Volatile
        private var instance: MyAccessibilityService? = null

        fun getInstance(): MyAccessibilityService? = instance

        fun isEnabled(context: Context): Boolean {
            val enabledServices =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
            val expectedId = "${context.packageName}/${MyAccessibilityService::class.java.name}"
            return enabledServices.split(':').any { it.equals(expectedId, ignoreCase = true) }
        }
    }
}


