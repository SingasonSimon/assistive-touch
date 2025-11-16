package com.example.assistivetouch.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.assistivetouch.R
import com.example.assistivetouch.service.FloatingButtonService
import com.example.assistivetouch.service.MyAccessibilityService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var textStatus: TextView
    private lateinit var buttonOverlayPermission: MaterialCardView
    private lateinit var buttonAccessibility: MaterialCardView
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

        textStatus = findViewById(R.id.textStatus)
        buttonOverlayPermission = findViewById(R.id.buttonOverlayPermission)
        buttonAccessibility = findViewById(R.id.buttonAccessibility)
        buttonStartService = findViewById(R.id.buttonStartService)
        buttonSettings = findViewById(R.id.buttonSettings)

        buttonOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }

        buttonAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        buttonStartService.setOnClickListener {
            if (hasOverlayPermission() && MyAccessibilityService.isEnabled(this)) {
                startFloatingService()
            } else {
                // Encourage user to grant permissions first
                updateStatus()
            }
        }

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlayStatus = if (hasOverlayPermission()) {
            getString(R.string.status_granted)
        } else {
            getString(R.string.status_not_granted)
        }
        val accessibilityStatus = if (MyAccessibilityService.isEnabled(this)) {
            getString(R.string.status_granted)
        } else {
            getString(R.string.status_not_granted)
        }

        val statusText = getString(
            R.string.permission_status_overlay,
            overlayStatus
        ) + "\n" + getString(
            R.string.permission_status_accessibility,
            accessibilityStatus
        )

        textStatus.text = statusText

        buttonStartService.isEnabled = hasOverlayPermission() && MyAccessibilityService.isEnabled(this)
        buttonStartService.alpha = if (buttonStartService.isEnabled) 1f else 0.5f
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
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


