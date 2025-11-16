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
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import android.hardware.camera2.CameraManager
import android.widget.ImageButton
import android.widget.LinearLayout
import com.example.assistivetouch.prefs.FavoritesManager
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

        val inflater = LayoutInflater.from(this)
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

        val inflater = LayoutInflater.from(this)
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

        val buttonParams = layoutParams
        if (buttonParams != null && floatingView != null) {
            params.gravity = Gravity.TOP or Gravity.START
            params.x = buttonParams.x + (floatingView!!.width)
            params.y = buttonParams.y
        } else {
            params.gravity = Gravity.CENTER
        }

        // Wire core buttons
        view.findViewById<View>(R.id.buttonHome).setOnClickListener {
            MyAccessibilityService.getInstance()?.performHomeAction()
        }
        view.findViewById<View>(R.id.buttonBack).setOnClickListener {
            MyAccessibilityService.getInstance()?.performBackAction()
        }
        view.findViewById<View>(R.id.buttonRecents).setOnClickListener {
            MyAccessibilityService.getInstance()?.performRecentsAction()
        }
        view.findViewById<View>(R.id.buttonLock).setOnClickListener {
            MyAccessibilityService.getInstance()?.performLockScreenAction()
        }
        view.findViewById<View>(R.id.buttonScreenshot).setOnClickListener {
            MyAccessibilityService.getInstance()?.performScreenshotAction()
        }
        view.findViewById<View>(R.id.buttonVolume).setOnClickListener {
            showSystemVolume()
        }
        view.findViewById<View>(R.id.buttonNotifications).setOnClickListener {
            MyAccessibilityService.getInstance()?.openNotificationsPanel()
        }

        // System toggles & advanced actions
        view.findViewById<View>(R.id.buttonWifi).setOnClickListener {
            openWifiPanel()
        }
        view.findViewById<View>(R.id.buttonBluetooth).setOnClickListener {
            openBluetoothSettings()
        }
        view.findViewById<View>(R.id.buttonRotation).setOnClickListener {
            openDisplaySettings()
        }
        view.findViewById<View>(R.id.buttonFlashlight).setOnClickListener {
            toggleFlashlight()
        }
        view.findViewById<View>(R.id.buttonScreenRecord).setOnClickListener {
            showScreenRecordHint()
        }
        view.findViewById<View>(R.id.buttonPowerMenu).setOnClickListener {
            openPowerSettings()
        }

        // Favorites container
        populateFavorites(view)

        windowManager.addView(view, params)
        panelView = view
        panelLayoutParams = params
        applyPanelTheme()
    }

    private fun populateFavorites(panel: View) {
        val container = panel.findViewById<LinearLayout>(R.id.favoritesContainer)
        container.removeAllViews()

        val favoritePkgs = FavoritesManager.getFavoritePackages(this)
        if (favoritePkgs.isEmpty()) return

        val pm = packageManager
        favoritePkgs.forEach { pkg ->
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                val icon = pm.getApplicationIcon(pkg)
                val button = ImageButton(this).apply {
                    setImageDrawable(icon)
                    layoutParams = LinearLayout.LayoutParams(
                        96,
                        96
                    ).apply {
                        rightMargin = 8
                    }
                    background = null
                    setOnClickListener {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                }
                container.addView(button)
            }
        }
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
        val panelRoot = panelView ?: return
        val themeKey = prefs.getString(SettingsActivity.KEY_PANEL_THEME, SettingsActivity.THEME_LIGHT)
        val backgroundColor = when (themeKey) {
            SettingsActivity.THEME_DARK -> 0xCC212121.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        panelRoot.setBackgroundColor(backgroundColor)
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
            // Query current state is not trivial; just try toggling on for now.
            cameraManager.setTorchMode(cameraId, true)
        } catch (e: Exception) {
            Toast.makeText(this, "Flashlight control not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showScreenRecordHint() {
        Toast.makeText(
            this,
            "Use your device's built-in screen recorder from Quick Settings.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openPowerSettings() {
        // There is no public API for power menu; open general settings as best-effort.
        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun removePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        panelLayoutParams = null
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


