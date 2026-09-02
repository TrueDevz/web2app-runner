package com.webtoapp.base

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.webtoapp.base.databinding.ActivitySplashBinding
import org.json.JSONObject

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var splashDurationMs = 2000L
    private var enableOnboarding = false
    private var isFreeUser = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSplashConfig()

        // Fullscreen splash
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (isFreeUser) {
            binding.watermarkText.visibility = android.view.View.VISIBLE
        }

        // Animate elements
        binding.logoImage.alpha = 0f
        binding.logoImage.scaleX = 0.8f
        binding.logoImage.scaleY = 0.8f
        binding.appNameText.alpha = 0f

        binding.logoImage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .start()

        binding.appNameText.animate()
            .alpha(1f)
            .setDuration(1000)
            .setStartDelay(200)
            .start()

        Handler(Looper.getMainLooper()).postDelayed({
            proceedToNextScreen()
        }, splashDurationMs)
    }

    private fun loadSplashConfig() {
        try {
            val jsonString = assets.open("app_config.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val appName = json.optString("appName", getString(R.string.app_name))
            splashDurationMs = json.optLong("splashDurationMs", 2000L)
            enableOnboarding = json.optBoolean("enableOnboarding", false)
            isFreeUser = json.optBoolean("isFreeUser", false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun proceedToNextScreen() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)

        val nextIntent = if (enableOnboarding && !hasSeenOnboarding) {
            Intent(this, OnboardingActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }

        startActivity(nextIntent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
