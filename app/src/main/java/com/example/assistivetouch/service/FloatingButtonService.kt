package com.example.assistivetouch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.os.IBinder
import android.view.MotionEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
// Blur API imports were used previously; currently disabled to keep panel crisp.
// import android.graphics.RenderEffect
// import android.graphics.Shader
import android.view.animation.OvershootInterpolator
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import android.hardware.camera2.CameraManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import com.example.assistivetouch.R
import com.example.assistivetouch.ui.MainActivity
import com.example.assistivetouch.ui.SettingsActivity

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelLayoutParams: WindowManager.LayoutParams? = null

    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var downTime: Long = 0L
    private var isLongPressed = false

    private val longPressRunnable = Runnable {
        isLongPressed = true
        handleLongPress()
    }

    private val prefs by lazy {
        getSharedPreferences("assistive_touch_prefs", Context.MODE_PRIVATE)
    }

    private var isTorchOn: Boolean = false

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SettingsActivity.ACTION_SETTINGS_CHANGED) {
                applyButtonAppearance()
                applyPanelTheme()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addFloatingButton()
        registerReceiver(
            settingsReceiver,
            IntentFilter(SettingsActivity.ACTION_SETTINGS_CHANGED)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        unregisterReceiver(settingsReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun addFloatingButton() {
        if (floatingView != null) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_AssistiveTouch)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.view_floating_button, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            // Restore last position if available, otherwise start at middle-right.
            val savedX = prefs.getInt(PREF_KEY_X, Int.MIN_VALUE)
            val savedY = prefs.getInt(PREF_KEY_Y, Int.MIN_VALUE)
            if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE) {
                x = savedX
                y = savedY
            } else {
                x = screenWidth / 2
                y = screenHeight / 2
            }
        }

        view.setOnTouchListener { _, event ->
            val paramsRef = layoutParams ?: params
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = SystemClock.elapsedRealtime()
                    initialX = paramsRef.x
                    initialY = paramsRef.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isLongPressed = false
                    floatingView?.removeCallbacks(longPressRunnable)
                    floatingView?.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    paramsRef.x = initialX + dx
                    paramsRef.y = initialY + dy
                    windowManager.updateViewLayout(view, paramsRef)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val upTime = SystemClock.elapsedRealtime()
                    val clickDuration = upTime - downTime
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                     floatingView?.removeCallbacks(longPressRunnable)
                    val isClick = clickDuration < CLICK_DURATION_THRESHOLD &&
                        kotlin.math.abs(dx) < MOVE_THRESHOLD &&
                        kotlin.math.abs(dy) < MOVE_THRESHOLD

                    if (isLongPressed) {
                        // Long press already handled.
                        true
                    } else if (isClick) {
                        togglePanel()
                    } else {
                        snapToEdge(paramsRef)
                    }
                    true
                }
                else -> false
            }
        }

        layoutParams = params
        windowManager.addView(view, params)
        floatingView = view
        applyButtonAppearance()
    }

    private fun togglePanel() {
        if (panelView != null) {
            removePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (panelView != null || floatingView == null) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_AssistiveTouch)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.view_action_panel, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // Center the panel on screen for clarity
        params.gravity = Gravity.CENTER

        val panelRoot = view.findViewById<View>(R.id.panelRoot)
        // Blur disabled for now – it was making content too hard to read.

        // Initial state for animation
        panelRoot.alpha = 0f
        panelRoot.scaleX = 0.9f
        panelRoot.scaleY = 0.9f

        // Wrap click actions; run action then minimize panel so system UI (e.g. screenshot) works as expected.
        fun wrapAndClose(action: () -> Unit): View.OnClickListener =
            View.OnClickListener {
                action()
                removePanel()
            }

        // Wire core buttons
        view.findViewById<View>(R.id.buttonHome).setOnClickListener(
            wrapAndClose { MyAccessibilityService.getInstance()?.performHomeAction() }
        )
        view.findViewById<View>(R.id.buttonBack).setOnClickListener(
            wrapAndClose { MyAccessibilityService.getInstance()?.performBackAction() }
        )
        view.findViewById<View>(R.id.buttonRecents).setOnClickListener(
            wrapAndClose { MyAccessibilityService.getInstance()?.performRecentsAction() }
        )
        view.findViewById<View>(R.id.buttonLock).setOnClickListener(
            wrapAndClose { MyAccessibilityService.getInstance()?.performLockScreenAction() }
        )

        // For screenshot, close the panel first and trigger screenshot slightly later
        val screenshotButton = view.findViewById<View>(R.id.buttonScreenshot)
        screenshotButton.setOnClickListener {
            removePanel()
            screenshotButton.postDelayed({
                MyAccessibilityService.getInstance()?.performScreenshotAction()
            }, 280L)
        }
        view.findViewById<View>(R.id.buttonNotifications).setOnClickListener(
            wrapAndClose { MyAccessibilityService.getInstance()?.openNotificationsPanel() }
        )

        // System toggles & advanced actions
        view.findViewById<View>(R.id.buttonWifi).setOnClickListener(
            wrapAndClose { openWifiPanel() }
        )
        view.findViewById<View>(R.id.buttonBluetooth).setOnClickListener(
            wrapAndClose { openBluetoothSettings() }
        )
        view.findViewById<View>(R.id.buttonFlashlight).setOnClickListener(
            wrapAndClose { toggleFlashlight() }
        )
        // Volume slider with icons + haptics
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val volumeSlider = view.findViewById<com.google.android.material.slider.Slider>(R.id.volumeSlider)
        volumeSlider.valueFrom = 0f
        volumeSlider.valueTo = maxVol.toFloat()
        volumeSlider.stepSize = 1f
        volumeSlider.value = currentVol.toFloat()

        // Colors for track and thumb
        val appleGreen = 0xFF32D74B.toInt()
        volumeSlider.trackActiveTintList = ColorStateList.valueOf(appleGreen)
        volumeSlider.trackInactiveTintList = ColorStateList.valueOf(0xFF555555.toInt())
        volumeSlider.thumbTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        volumeSlider.haloTintList = ColorStateList.valueOf(appleGreen)

        var lastVolume = currentVol

        volumeSlider.addOnChangeListener { slider, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val vol = value.toInt().coerceIn(0, maxVol)
            if (vol != lastVolume) {
                lastVolume = vol
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    vol,
                    0
                )
                slider.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }

        // Brightness slider
        val brightnessSlider = view.findViewById<com.google.android.material.slider.Slider>(R.id.brightnessSlider)
        val resolver = contentResolver
        val maxBrightness = 255
        val currentBrightness = try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            128
        }

        brightnessSlider.valueFrom = 0f
        brightnessSlider.valueTo = maxBrightness.toFloat()
        brightnessSlider.stepSize = 1f
        brightnessSlider.value = currentBrightness.toFloat()

        val yellow = 0xFFFFD43B.toInt()
        brightnessSlider.trackActiveTintList = ColorStateList.valueOf(yellow)
        brightnessSlider.trackInactiveTintList = ColorStateList.valueOf(0xFF555555.toInt())
        brightnessSlider.thumbTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        brightnessSlider.haloTintList = ColorStateList.valueOf(yellow)

        var lastBrightness = currentBrightness

        brightnessSlider.addOnChangeListener { slider, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val b = value.toInt().coerceIn(0, maxBrightness)
            if (b != lastBrightness) {
                lastBrightness = b
                if (Settings.System.canWrite(this)) {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        b
                    )
                } else {
                    Toast.makeText(
                        this,
                        "Allow modify system settings to change brightness.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                slider.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }

        windowManager.addView(view, params)
        panelView = view
        panelLayoutParams = params
        applyPanelTheme()

        // Animate in with spring-like overshoot
        panelRoot.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()
    }

    private fun applyButtonAppearance() {
        val core = floatingView?.findViewById<View>(R.id.floatingButtonCore) ?: return
        val sizeDp = prefs.getInt(SettingsActivity.KEY_BUTTON_SIZE_DP, 56)
        val alphaPercent = prefs.getInt(SettingsActivity.KEY_BUTTON_ALPHA, 100)
        val colorKey = prefs.getString(SettingsActivity.KEY_BUTTON_COLOR, SettingsActivity.COLOR_BLUE)

        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast((40 * density).toInt())

        core.layoutParams = core.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        core.alpha = (alphaPercent.coerceIn(30, 100) / 100f)

        val color = when (colorKey) {
            SettingsActivity.COLOR_RED -> 0xAAF44336.toInt()
            SettingsActivity.COLOR_GREEN -> 0xAA4CAF50.toInt()
            else -> 0xAA2196F3.toInt()
        }
        (core.background)?.setTint(color)

        floatingView?.requestLayout()
    }

    private fun applyPanelTheme() {
        // Keep the glassmorphic dark background defined in XML;
        // no dynamic override here so icons stay readable.
        val panelRoot = panelView ?: return
        panelRoot.alpha = 0.95f
    }

    private fun handleLongPress() {
        val actionKey =
            prefs.getString(SettingsActivity.KEY_LONG_PRESS_ACTION, SettingsActivity.ACTION_OPEN_SETTINGS)
        when (actionKey) {
            SettingsActivity.ACTION_LOCK_SCREEN -> {
                MyAccessibilityService.getInstance()?.performLockScreenAction()
            }
            SettingsActivity.ACTION_SCREENSHOT -> {
                MyAccessibilityService.getInstance()?.performScreenshotAction()
            }
            SettingsActivity.ACTION_OPEN_SETTINGS -> {
                val intent = Intent(this, SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }
    }

    private fun showSystemVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun openWifiPanel() {
        // Use Settings Panel where available; fall back to Wi-Fi settings.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun openDisplaySettings() {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun toggleFlashlight() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Camera permission required for torch", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Flashlight control not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showScreenRecordHint() {
        Toast.makeText(
            this,
            "Screen recording is not available on this device. Use Samsung's built‑in recorder if present in Quick Settings.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openPowerSettings() {
        Toast.makeText(
            this,
            "Android does not allow third‑party apps to open the power menu.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun removePanel() {
        val view = panelView ?: return
        val panelRoot = view.findViewById<View>(R.id.panelRoot)
        panelRoot.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(160)
            .withEndAction {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {
                }
                panelView = null
                panelLayoutParams = null
            }
            .start()
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // Determine nearest horizontal edge (left or right)
        val centerX = params.x + (floatingView?.width ?: 0) / 2
        params.x = if (centerX < screenWidth / 2) {
            0
        } else {
            screenWidth - (floatingView?.width ?: 0)
        }

        // Clamp vertically to screen bounds
        val maxY = screenHeight - (floatingView?.height ?: 0)
        if (params.y < 0) params.y = 0
        if (params.y > maxY) params.y = maxY

        windowManager.updateViewLayout(floatingView, params)

        prefs.edit()
            .putInt(PREF_KEY_X, params.x)
            .putInt(PREF_KEY_Y, params.y)
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Assistive Touch",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Assistive Touch is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "assistive_touch_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MOVE_THRESHOLD = 20
        private const val CLICK_DURATION_THRESHOLD = 200L
        private const val PREF_KEY_X = "floating_x"
        private const val PREF_KEY_Y = "floating_y"
        private const val LONG_PRESS_THRESHOLD = 500L
    }
}


