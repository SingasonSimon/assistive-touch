package com.example.assistivetouch.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.assistivetouch.service.ScreenRecordingService

class ScreenRecordPermissionActivity : AppCompatActivity() {

    private val projectionRequestCode = 8111

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            Toast.makeText(this, "Screen recording is unavailable on this device.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        startActivityForResult(projectionManager.createScreenCaptureIntent(), projectionRequestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != projectionRequestCode) {
            finish()
            return
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_START_RECORDING
                putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            Toast.makeText(this, "Screen recording permission was denied.", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
