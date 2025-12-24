package com.example.assistivetouch

import android.content.Context
import android.content.SharedPreferences
import com.example.assistivetouch.ui.SettingsActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SettingsActivityTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        `when`(context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)
        `when`(editor.putInt(anyString(), anyInt())).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
    }

    @Test
    fun testDefaultValues() {
        assert(SettingsActivity.COLOR_BLUE == "blue")
        assert(SettingsActivity.COLOR_RED == "red")
        assert(SettingsActivity.COLOR_GREEN == "green")
        assert(SettingsActivity.THEME_LIGHT == "light")
        assert(SettingsActivity.THEME_DARK == "dark")
        assert(SettingsActivity.ACTION_OPEN_SETTINGS == "open_settings")
        assert(SettingsActivity.ACTION_LOCK_SCREEN == "lock_screen")
        assert(SettingsActivity.ACTION_SCREENSHOT == "screenshot")
    }

    @Test
    fun testPreferenceKeys() {
        assert(SettingsActivity.KEY_BUTTON_SIZE_DP == "button_size_dp")
        assert(SettingsActivity.KEY_BUTTON_ALPHA == "button_alpha")
        assert(SettingsActivity.KEY_BUTTON_COLOR == "button_color")
        assert(SettingsActivity.KEY_PANEL_THEME == "panel_theme")
        assert(SettingsActivity.KEY_LONG_PRESS_ACTION == "long_press_action")
    }

    @Test
    fun testSettingsChangedAction() {
        val expectedAction = "com.example.assistivetouch.ACTION_SETTINGS_CHANGED"
        assert(SettingsActivity.ACTION_SETTINGS_CHANGED == expectedAction)
    }
}

