package com.example.assistivetouch.ui

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.assistivetouch.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    private lateinit var sizeSlider: Slider
    private lateinit var alphaSlider: Slider
    private lateinit var colorGroup: RadioGroup
    private lateinit var themeGroup: RadioGroup
    private lateinit var longPressGroup: RadioGroup
    private lateinit var applyButton: Button
    private lateinit var textSizeValue: TextView
    private lateinit var textAlphaValue: TextView
    private lateinit var previewButton: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sizeSlider = findViewById(R.id.seekSize)
        alphaSlider = findViewById(R.id.seekAlpha)
        colorGroup = findViewById(R.id.groupColor)
        themeGroup = findViewById(R.id.groupTheme)
        longPressGroup = findViewById(R.id.groupLongPress)
        applyButton = findViewById(R.id.buttonApplySettings)
        textSizeValue = findViewById(R.id.textSizeValue)
        textAlphaValue = findViewById(R.id.textAlphaValue)
        previewButton = findViewById(R.id.previewButton)

        loadPrefs()
        updatePreview()

        sizeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val sizeDp = value.toInt() + 40
                textSizeValue.text = "${sizeDp}dp"
                updatePreview()
            }
        }
        sizeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        })

        alphaSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val alpha = value.toInt()
                textAlphaValue.text = "$alpha%"
                updatePreview()
            }
        }
        alphaSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        })

        colorGroup.setOnCheckedChangeListener { _, _ -> updatePreview() }
        themeGroup.setOnCheckedChangeListener { _, _ -> updatePreview() }

        applyButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            savePrefs()
            // Notify service to refresh
            sendBroadcast(android.content.Intent(ACTION_SETTINGS_CHANGED))
            finish()
            overridePendingTransition(R.anim.slide_out_left, R.anim.fade_in)
        }
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val size = prefs.getInt(KEY_BUTTON_SIZE_DP, 56).coerceIn(40, 96)
        val alpha = prefs.getInt(KEY_BUTTON_ALPHA, 100).coerceIn(30, 100)
        val color = prefs.getString(KEY_BUTTON_COLOR, COLOR_BLUE) ?: COLOR_BLUE
        val theme = prefs.getString(KEY_PANEL_THEME, THEME_LIGHT) ?: THEME_LIGHT
        val longPress = prefs.getString(KEY_LONG_PRESS_ACTION, ACTION_OPEN_SETTINGS) ?: ACTION_OPEN_SETTINGS

        // Map size (40-96dp) to slider value (0-56)
        sizeSlider.value = (size - 40).toFloat()
        alphaSlider.value = alpha.toFloat()
        
        // Update value labels
        textSizeValue.text = "${size}dp"
        textAlphaValue.text = "$alpha%"

        colorGroup.check(
            when (color) {
                COLOR_RED -> R.id.radioColorRed
                COLOR_GREEN -> R.id.radioColorGreen
                else -> R.id.radioColorBlue
            }
        )

        themeGroup.check(
            when (theme) {
                THEME_DARK -> R.id.radioThemeDark
                else -> R.id.radioThemeLight
            }
        )

        longPressGroup.check(
            when (longPress) {
                ACTION_LOCK_SCREEN -> R.id.radioLongPressLock
                ACTION_SCREENSHOT -> R.id.radioLongPressScreenshot
                else -> R.id.radioLongPressSettings
            }
        )
    }

    private fun savePrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        // Map slider value (0-56) to size (40-96dp)
        editor.putInt(KEY_BUTTON_SIZE_DP, sizeSlider.value.toInt() + 40)
        editor.putInt(KEY_BUTTON_ALPHA, alphaSlider.value.toInt())

        val color = when (colorGroup.checkedRadioButtonId) {
            R.id.radioColorRed -> COLOR_RED
            R.id.radioColorGreen -> COLOR_GREEN
            else -> COLOR_BLUE
        }
        editor.putString(KEY_BUTTON_COLOR, color)

        val theme = when (themeGroup.checkedRadioButtonId) {
            R.id.radioThemeDark -> THEME_DARK
            else -> THEME_LIGHT
        }
        editor.putString(KEY_PANEL_THEME, theme)

        val longPress = when (longPressGroup.checkedRadioButtonId) {
            R.id.radioLongPressLock -> ACTION_LOCK_SCREEN
            R.id.radioLongPressScreenshot -> ACTION_SCREENSHOT
            else -> ACTION_OPEN_SETTINGS
        }
        editor.putString(KEY_LONG_PRESS_ACTION, longPress)

        editor.apply()
    }

    private fun updatePreview() {
        val sizeDp = sizeSlider.value.toInt() + 40
        val alphaPercent = alphaSlider.value.toInt()
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        previewButton.layoutParams = previewButton.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        previewButton.alpha = (alphaPercent.coerceIn(30, 100) / 100f)

        val color = when (colorGroup.checkedRadioButtonId) {
            R.id.radioColorRed -> ContextCompat.getColor(this, R.color.floating_button_red)
            R.id.radioColorGreen -> ContextCompat.getColor(this, R.color.floating_button_green)
            else -> ContextCompat.getColor(this, R.color.floating_button_blue)
        }
        previewButton.setCardBackgroundColor(color)

        previewButton.requestLayout()
    }

    companion object {
        const val PREFS_NAME = "assistive_touch_prefs"
        const val KEY_BUTTON_SIZE_DP = "button_size_dp"
        const val KEY_BUTTON_ALPHA = "button_alpha"
        const val KEY_BUTTON_COLOR = "button_color"
        const val KEY_PANEL_THEME = "panel_theme"
        const val KEY_LONG_PRESS_ACTION = "long_press_action"

        const val COLOR_BLUE = "blue"
        const val COLOR_RED = "red"
        const val COLOR_GREEN = "green"

        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val ACTION_OPEN_SETTINGS = "open_settings"
        const val ACTION_LOCK_SCREEN = "lock_screen"
        const val ACTION_SCREENSHOT = "screenshot"

        const val ACTION_SETTINGS_CHANGED = "com.example.assistivetouch.ACTION_SETTINGS_CHANGED"
    }
}


