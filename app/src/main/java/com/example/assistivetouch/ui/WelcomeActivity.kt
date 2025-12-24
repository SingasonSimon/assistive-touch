package com.example.assistivetouch.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import com.example.assistivetouch.R
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {

    private lateinit var buttonGetStarted: MaterialButton
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences("assistive_touch_prefs", MODE_PRIVATE)
        
        // Check if welcome was already shown
        if (!shouldShowWelcome(this)) {
            // Skip welcome and go directly to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        setContentView(R.layout.activity_welcome)
        
        buttonGetStarted = findViewById(R.id.buttonGetStarted)
        
        buttonGetStarted.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // Mark welcome as shown
            prefs.edit().putBoolean("welcome_shown", true).apply()
            // Navigate to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            finish()
        }
    }

    companion object {
        fun shouldShowWelcome(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences("assistive_touch_prefs", android.content.Context.MODE_PRIVATE)
            return !prefs.getBoolean("welcome_shown", false)
        }
    }
}

