package com.example.assistivetouch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.assistivetouch.R
import com.example.assistivetouch.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordingService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputUri: Uri? = null
    private var outputFileDescriptor: android.os.ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording(intent)
            ACTION_STOP_RECORDING -> stopRecording(showToast = true)
            else -> {
                // ignore
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecording(showToast = false)
        super.onDestroy()
    }

    private fun startRecording(intent: Intent) {
        if (isRecording) {
            Toast.makeText(this, "Screen recording is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode == Int.MIN_VALUE || resultData == null) {
            Toast.makeText(this, "Unable to start screen recording.", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            Toast.makeText(this, "Screen recording is unavailable on this device.", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val densityDpi = displayMetrics.densityDpi

        try {
            val output = createOutputLocation()
            outputUri = output

            val fileDescriptor = contentResolver.openFileDescriptor(output, "w")
                ?: throw IllegalStateException("Could not open output file")
            outputFileDescriptor = fileDescriptor

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(6_000_000)
                setVideoFrameRate(30)
                setVideoSize(width, height)
                setOutputFile(requireNotNull(outputFileDescriptor).fileDescriptor)
                prepare()
            }

            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            val display = projection.createVirtualDisplay(
                "AssistiveTouchScreenRecord",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            mediaRecorder = recorder
            mediaProjection = projection
            virtualDisplay = display

            startForeground(NOTIFICATION_ID, buildNotification(isRunning = true))
            recorder.start()
            isRecording = true
            Toast.makeText(this, "Screen recording started.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            releaseResources()
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun stopRecording(showToast: Boolean) {
        if (!isRecording) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
            // ignore
        }

        releaseResources()
        isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (showToast) {
            val message = outputUri?.toString()?.let { "Recording saved: $it" } ?: "Screen recording stopped."
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        outputUri = null
        stopSelf()
    }

    private fun releaseResources() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null

        try {
            outputFileDescriptor?.close()
        } catch (_: Exception) {
        }
        outputFileDescriptor = null
    }

    private fun createOutputLocation(): Uri {
        val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "assistive_touch_recording_$now.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AssistiveTouch")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create video output")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        }

        return uri
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isRunning: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            pendingIntentFlags()
        )

        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            pendingIntentFlags()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (isRunning) "Screen recording is running" else "Screen recording")
            .setContentIntent(openPendingIntent)
            .setOngoing(isRunning)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    companion object {
        const val ACTION_START_RECORDING = "com.example.assistivetouch.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.assistivetouch.action.STOP_RECORDING"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "assistive_touch_screen_recording"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        var isRecording: Boolean = false
            private set

        fun launchPermissionFlow(context: Context) {
            val intent = Intent(context, com.example.assistivetouch.ui.ScreenRecordPermissionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        fun requestStop(context: Context) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
