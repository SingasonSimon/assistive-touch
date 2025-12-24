package com.example.assistivetouch.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.assistivetouch.R
import com.example.assistivetouch.service.FloatingButtonService
import com.example.assistivetouch.service.MyAccessibilityService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var iconOverlayStatus: ImageView
    private lateinit var iconAccessibilityStatus: ImageView
    private lateinit var iconOverlayStatusLarge: ImageView
    private lateinit var iconAccessibilityStatusLarge: ImageView
    private lateinit var buttonOverlayPermission: MaterialCardView
    private lateinit var buttonAccessibility: MaterialCardView
    private lateinit var buttonWriteSettings: MaterialCardView
    private lateinit var buttonStartService: MaterialCardView
    private lateinit var buttonSettings: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        iconOverlayStatus = findViewById(R.id.iconOverlayStatus)
        iconAccessibilityStatus = findViewById(R.id.iconAccessibilityStatus)
        iconOverlayStatusLarge = findViewById(R.id.iconOverlayStatusLarge)
        iconAccessibilityStatusLarge = findViewById(R.id.iconAccessibilityStatusLarge)
        buttonOverlayPermission = findViewById(R.id.buttonOverlayPermission)
        buttonAccessibility = findViewById(R.id.buttonAccessibility)
        buttonWriteSettings = findViewById(R.id.buttonWriteSettings)
        buttonStartService = findViewById(R.id.buttonStartService)
        buttonSettings = findViewById(R.id.buttonSettings)

        buttonOverlayPermission.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            requestOverlayPermission()
        }

        buttonAccessibility.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openAccessibilitySettings()
        }

        buttonWriteSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openWriteSettings()
        }

        buttonStartService.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (hasOverlayPermission() && MyAccessibilityService.isEnabled(this)) {
                startFloatingService()
            } else {
                // Encourage user to grant permissions first
                updateStatus()
            }
        }

        buttonSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
    }

    private fun openWriteSettings() {
        if (Settings.System.canWrite(this)) {
            // Already granted - show notification
            Toast.makeText(
                this,
                "Write settings permission is already granted",
                Toast.LENGTH_SHORT
            ).show()
        }
        // Always open settings screen so user can verify/change
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings if specific intent fails
            Toast.makeText(
                this,
                "Please enable 'Modify system settings' permission in Settings",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val hasOverlay = hasOverlayPermission()
        val hasAccessibility = MyAccessibilityService.isEnabled(this)

        // Update status icons in permission card
        iconOverlayStatus.setImageResource(
            if (hasOverlay) R.drawable.ic_check_circle else R.drawable.ic_error_circle
        )
        iconOverlayStatus.setColorFilter(
            ContextCompat.getColor(this, if (hasOverlay) R.color.success else R.color.error)
        )
        
        iconAccessibilityStatus.setImageResource(
            if (hasAccessibility) R.drawable.ic_check_circle else R.drawable.ic_error_circle
        )
        iconAccessibilityStatus.setColorFilter(
            ContextCompat.getColor(this, if (hasAccessibility) R.color.success else R.color.error)
        )

        // Update status icons in action cards
        iconOverlayStatusLarge.setImageResource(
            if (hasOverlay) R.drawable.ic_check_circle else R.drawable.ic_error_circle
        )
        iconOverlayStatusLarge.setColorFilter(
            ContextCompat.getColor(this, if (hasOverlay) R.color.success else R.color.error)
        )
        iconOverlayStatusLarge.visibility = if (hasOverlay) View.VISIBLE else View.GONE
        
        iconAccessibilityStatusLarge.setImageResource(
            if (hasAccessibility) R.drawable.ic_check_circle else R.drawable.ic_error_circle
        )
        iconAccessibilityStatusLarge.setColorFilter(
            ContextCompat.getColor(this, if (hasAccessibility) R.color.success else R.color.error)
        )
        iconAccessibilityStatusLarge.visibility = if (hasAccessibility) View.VISIBLE else View.GONE

        // Enable/disable start service button
        buttonStartService.isEnabled = hasOverlay && hasAccessibility
        buttonStartService.alpha = if (buttonStartService.isEnabled) 1f else 0.6f
        
        // Animate status changes
        iconOverlayStatus.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150)
            .withEndAction {
                iconOverlayStatus.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()
        iconAccessibilityStatus.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150)
            .withEndAction {
                iconAccessibilityStatus.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                // Already granted - show notification
                Toast.makeText(
                    this,
                    "Draw over other apps permission is already granted",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Always open settings screen so user can verify/change
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general settings if specific intent fails
                Toast.makeText(
                    this,
                    "Please enable 'Display over other apps' permission in Settings",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        } else {
            // Android < M doesn't need this permission
            Toast.makeText(
                this,
                "Draw over other apps permission is not required on this Android version",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent as Intent)
        }
    }
}


