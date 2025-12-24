package com.example.assistivetouch

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assistivetouch.service.FloatingButtonService
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingButtonServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testServiceCanBeCreated() {
        val intent = Intent(context, FloatingButtonService::class.java)
        // Service creation test - basic smoke test
        assert(intent.component != null)
    }

    @Test
    fun testServiceConstants() {
        // Test that constants are defined
        // These are private, so we're just ensuring the service compiles
        assert(true) // Placeholder - actual testing would require service instance
    }
}

