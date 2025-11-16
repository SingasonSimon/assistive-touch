package com.example.assistivetouch.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.assistivetouch.R

class SettingsActivity : AppCompatActivity() {

    private lateinit var sizeSeekBar: SeekBar
    private lateinit var alphaSeekBar: SeekBar
    private lateinit var colorGroup: RadioGroup
    private lateinit var themeGroup: RadioGroup
    private lateinit var longPressGroup: RadioGroup
    private lateinit var applyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sizeSeekBar = findViewById(R.id.seekSize)
        alphaSeekBar = findViewById(R.id.seekAlpha)
        colorGroup = findViewById(R.id.groupColor)
        themeGroup = findViewById(R.id.groupTheme)
        longPressGroup = findViewById(R.id.groupLongPress)
        applyButton = findViewById(R.id.buttonApplySettings)

        loadPrefs()

        applyButton.setOnClickListener {
            savePrefs()
            // Notify service to refresh
            sendBroadcast(android.content.Intent(ACTION_SETTINGS_CHANGED))
            finish()
        }
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val size = prefs.getInt(KEY_BUTTON_SIZE_DP, 56)
        val alpha = prefs.getInt(KEY_BUTTON_ALPHA, 100)
        val color = prefs.getString(KEY_BUTTON_COLOR, COLOR_BLUE) ?: COLOR_BLUE
        val theme = prefs.getString(KEY_PANEL_THEME, THEME_LIGHT) ?: THEME_LIGHT
        val longPress = prefs.getString(KEY_LONG_PRESS_ACTION, ACTION_OPEN_SETTINGS) ?: ACTION_OPEN_SETTINGS

        sizeSeekBar.progress = size
        alphaSeekBar.progress = alpha

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
        editor.putInt(KEY_BUTTON_SIZE_DP, sizeSeekBar.progress)
        editor.putInt(KEY_BUTTON_ALPHA, alphaSeekBar.progress)

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


