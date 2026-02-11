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
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
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
                // Reapply theme if panel is visible
                if (panelView != null) {
                    panelView?.post {
                        applyPanelTheme()
                    }
                }
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
        try {
            floatingView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // View may have already been removed
        }
        floatingView = null
        try {
            panelView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // View may have already been removed
        }
        panelView = null
        try {
            unregisterReceiver(settingsReceiver)
        } catch (e: Exception) {
            // Receiver may have already been unregistered
        }
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            // Restore last position if available, otherwise start centered.
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

        view.setOnTouchListener { v, event ->
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
                    // Haptic feedback and scale animation
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    val core = v.findViewById<View>(R.id.floatingButtonCore)
                    core?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(100)?.start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    paramsRef.x = initialX + dx
                    paramsRef.y = initialY + dy
                    // Cancel long press if moving
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        floatingView?.removeCallbacks(longPressRunnable)
                    }
                    try {
                        windowManager.updateViewLayout(v, paramsRef)
                    } catch (e: Exception) {
                        // View may have been removed
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val upTime = SystemClock.elapsedRealtime()
                    val clickDuration = upTime - downTime
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    floatingView?.removeCallbacks(longPressRunnable)
                    val isClick = clickDuration < CLICK_DURATION_THRESHOLD &&
                        kotlin.math.abs(dx) < MOVE_THRESHOLD &&
                        kotlin.math.abs(dy) < MOVE_THRESHOLD

                    // Restore scale
                    val core = v.findViewById<View>(R.id.floatingButtonCore)
                    core?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
                    
                    if (isLongPressed) {
                        // Long press already handled.
                        true
                    } else if (isClick) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
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
        try {
            windowManager.addView(view, params)
            floatingView = view
            applyButtonAppearance()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to show floating button: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
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
        val view = inflater.inflate(R.layout.view_action_panel_wrapper, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val params = WindowManager.LayoutParams(
            screenWidth,
            screenHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        // Full screen overlay
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        val panelRoot = view.findViewById<View>(R.id.panelRoot)
        val backdrop = view.findViewById<View>(R.id.panelBackdrop)

        // Set up backdrop click to close panel
        backdrop?.setOnClickListener {
            removePanel()
        }
        
        // Prevent panel from closing when clicking inside it
        panelRoot.setOnClickListener {
            // Do nothing - prevent click from propagating to backdrop
        }

        // Initial state for animation
        panelRoot.alpha = 0f
        panelRoot.scaleX = 0.85f
        panelRoot.scaleY = 0.85f
        view.alpha = 0f

        // Wrap click actions; run action then minimize panel so system UI (e.g. screenshot) works as expected.
        fun wrapAndClose(action: () -> Unit): View.OnClickListener =
            View.OnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                // Animate button press
                view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()
                action()
                removePanel()
            }

        // Wire core buttons
        view.findViewById<View>(R.id.buttonHome).setOnClickListener(
            wrapAndClose { 
                MyAccessibilityService.getInstance()?.performHomeAction() 
                    ?: Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }
        )
        view.findViewById<View>(R.id.buttonBack).setOnClickListener(
            wrapAndClose { 
                MyAccessibilityService.getInstance()?.performBackAction()
                    ?: Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }
        )
        view.findViewById<View>(R.id.buttonRecents).setOnClickListener(
            wrapAndClose { 
                MyAccessibilityService.getInstance()?.performRecentsAction()
                    ?: Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }
        )
        view.findViewById<View>(R.id.buttonLock).setOnClickListener(
            wrapAndClose { 
                MyAccessibilityService.getInstance()?.performLockScreenAction()
                    ?: Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }
        )

        // For screenshot, close the panel first and trigger screenshot slightly later
        val screenshotButton = view.findViewById<View>(R.id.buttonScreenshot)
        screenshotButton.setOnClickListener {
            removePanel()
            screenshotButton.postDelayed({
                MyAccessibilityService.getInstance()?.performScreenshotAction()
                    ?: Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }, 280L)
        }

        val recordButton = view.findViewById<View>(R.id.buttonRecord)
        recordButton.setOnClickListener {
            removePanel()
            recordButton.postDelayed({
                if (ScreenRecordingService.isRecording) {
                    ScreenRecordingService.requestStop(this)
                } else {
                    ScreenRecordingService.launchPermissionFlow(this)
                }
            }, 220L)
        }

        view.findViewById<View>(R.id.buttonNotifications).setOnClickListener(
            wrapAndClose { 
                MyAccessibilityService.getInstance()?.openNotificationsPanel()
                    ?: Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }
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

        // Brightness slider colors - warm yellow/orange
        val brightnessColor = 0xFFFF9800.toInt()
        brightnessSlider.trackActiveTintList = ColorStateList.valueOf(brightnessColor)
        brightnessSlider.trackInactiveTintList = ColorStateList.valueOf(0xFFE0E0E0.toInt())
        brightnessSlider.thumbTintList = ColorStateList.valueOf(brightnessColor)
        brightnessSlider.haloTintList = ColorStateList.valueOf(brightnessColor)

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
                        "Allow modifying system settings to change brightness.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                slider.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }

        try {
            windowManager.addView(view, params)
            panelView = view
            panelLayoutParams = params
            // Apply theme after view is added
            view.post {
                applyPanelTheme()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to show panel: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        // Animate in with spring-like overshoot
        view.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        panelRoot.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun applyButtonAppearance() {
        val core = floatingView?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.floatingButtonCore) ?: return
        val sizeDp = prefs.getInt(SettingsActivity.KEY_BUTTON_SIZE_DP, 56)
        val alphaPercent = prefs.getInt(SettingsActivity.KEY_BUTTON_ALPHA, 100)
        val colorKey = prefs.getString(SettingsActivity.KEY_BUTTON_COLOR, SettingsActivity.COLOR_BLUE)

        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast((40 * density).toInt())

        core.layoutParams = core.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        floatingView?.alpha = (alphaPercent.coerceIn(30, 100) / 100f)

        val color = when (colorKey) {
            SettingsActivity.COLOR_RED -> ContextCompat.getColor(this, R.color.floating_button_red)
            SettingsActivity.COLOR_GREEN -> ContextCompat.getColor(this, R.color.floating_button_green)
            else -> ContextCompat.getColor(this, R.color.floating_button_blue)
        }
        core.setCardBackgroundColor(color)

        floatingView?.requestLayout()
    }

    private fun applyPanelTheme() {
        val panelRoot = panelView?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.panelRoot) ?: return
        val theme = prefs.getString(SettingsActivity.KEY_PANEL_THEME, SettingsActivity.THEME_LIGHT) ?: SettingsActivity.THEME_LIGHT
        
        if (theme == SettingsActivity.THEME_DARK) {
            // Dark theme
            panelRoot.setCardBackgroundColor(0xE0212121.toInt())
            // Update icon colors to light
            updatePanelIconColors(0xFFFFFFFF.toInt())
            // Update slider container backgrounds
            updatePanelSliderContainers(0xFF2C2C2C.toInt(), 0xFFFFFFFF.toInt())
        } else {
            // Light theme
            panelRoot.setCardBackgroundColor(0xF5FFFFFF.toInt())
            // Update icon colors to dark
            updatePanelIconColors(0xFF1C1B1F.toInt())
            // Update slider container backgrounds
            updatePanelSliderContainers(0xFFF0F0F0.toInt(), 0xFF666666.toInt())
        }
    }
    
    private fun updatePanelIconColors(color: Int) {
        panelView?.let { view ->
            val buttonIds = listOf(
                R.id.buttonHome, R.id.buttonBack, R.id.buttonRecents,
                R.id.buttonLock, R.id.buttonScreenshot, R.id.buttonRecord, R.id.buttonFlashlight,
                R.id.buttonNotifications, R.id.buttonWifi, R.id.buttonBluetooth
            )
            buttonIds.forEach { buttonId ->
                val button = view.findViewById<View>(buttonId) as? android.view.ViewGroup
                button?.let {
                    // Find ImageView child
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        if (child is android.widget.ImageView) {
                            child.setColorFilter(color)
                        }
                    }
                }
            }
        }
    }
    
    private fun updatePanelSliderContainers(bgColor: Int, iconColor: Int) {
        panelView?.let { view ->
            // Update volume slider container
            view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.volumeSliderContainer)?.setCardBackgroundColor(bgColor)
            
            // Update brightness slider container
            view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.brightnessSliderContainer)?.setCardBackgroundColor(bgColor)
            
            // Update volume icons
            view.findViewById<android.widget.ImageView>(R.id.iconVolumeDown)?.setColorFilter(iconColor)
            view.findViewById<android.widget.ImageView>(R.id.iconVolumeUp)?.setColorFilter(iconColor)
            
            // Update brightness icons
            view.findViewById<android.widget.ImageView>(R.id.iconBrightnessLow)?.setColorFilter(iconColor)
            view.findViewById<android.widget.ImageView>(R.id.iconBrightnessHigh)?.setColorFilter(iconColor)
        }
    }

    private fun handleLongPress() {
        val actionKey =
            prefs.getString(SettingsActivity.KEY_LONG_PRESS_ACTION, SettingsActivity.ACTION_OPEN_SETTINGS)
        when (actionKey) {
            SettingsActivity.ACTION_LOCK_SCREEN -> {
                MyAccessibilityService.getInstance()?.performLockScreenAction()
                    ?: Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }
            SettingsActivity.ACTION_SCREENSHOT -> {
                MyAccessibilityService.getInstance()?.performScreenshotAction()
                    ?: Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }
            SettingsActivity.ACTION_OPEN_SETTINGS -> {
                try {
                    val intent = Intent(this, SettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show()
                }
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
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: run {
                    Toast.makeText(this, "Camera service not available", Toast.LENGTH_SHORT).show()
                    return
                }
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                Toast.makeText(this, "No camera available", Toast.LENGTH_SHORT).show()
                return
            }
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Camera permission required for torch", Toast.LENGTH_SHORT).show()
        } catch (e: android.hardware.camera2.CameraAccessException) {
            Toast.makeText(this, "Camera access error: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Flashlight control not supported: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
        
        view.animate()
            .alpha(0f)
            .setDuration(150)
            .start()
        
        panelRoot.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(200)
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
        val targetX = if (centerX < screenWidth / 2) {
            0
        } else {
            screenWidth - (floatingView?.width ?: 0)
        }

        // Clamp vertically to screen bounds
        val maxY = screenHeight - (floatingView?.height ?: 0)
        val targetY = params.y.coerceIn(0, maxY)
        
        val startX = params.x
        params.x = targetX
        params.y = targetY

        try {
            // Animate to edge
            val deltaX = (startX - targetX).toFloat()
            floatingView?.translationX = deltaX
            windowManager.updateViewLayout(floatingView, params)
            floatingView?.animate()?.translationX(0f)?.setDuration(200)?.start()
        } catch (e: Exception) {
            // View may have been removed
            return
        }

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


