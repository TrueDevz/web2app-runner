package com.webtoapp.base

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val activity: MainActivity) {

    @JavascriptInterface
    fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun showNotification(title: String, message: String, targetUrl: String? = null) {
        activity.runOnUiThread {
            NotificationHelper(activity).showNotification(title, message, targetUrl)
        }
    }

    @JavascriptInterface
    fun showInterstitial() {
        activity.runOnUiThread {
            activity.showInterstitialAd()
        }
    }

    @JavascriptInterface
    fun requestPermissions() {
        activity.runOnUiThread {
            activity.requestRuntimePermissions()
        }
    }

    @JavascriptInterface
    fun openQRScanner() {
        activity.runOnUiThread {
            activity.launchQRScanner()
        }
    }

    @JavascriptInterface
    fun requestReview() {
        activity.runOnUiThread {
            activity.showInAppReviewPrompt()
        }
    }

    @JavascriptInterface
    fun checkForUpdate(latestVersionCode: Int, downloadUrl: String, releaseNotes: String = "") {
        activity.runOnUiThread {
            activity.checkForUpdate(latestVersionCode, downloadUrl, releaseNotes)
        }
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return activity.getAppVersionString()
    }

    @JavascriptInterface
    fun vibrate(milliseconds: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(milliseconds)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun share(text: String, url: String, title: String?) {
        activity.runOnUiThread {
            try {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    val shareBody = if (url.isNotEmpty()) "$text\n$url" else text
                    putExtra(Intent.EXTRA_TEXT, shareBody)
                    putExtra(Intent.EXTRA_SUBJECT, title ?: "Share")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, title ?: "Share via")
                activity.startActivity(shareIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun reloadPage() {
        activity.runOnUiThread {
            activity.reloadWebView()
        }
    }

    @JavascriptInterface
    fun authenticateBiometrics() {
        activity.runOnUiThread {
            activity.triggerBiometricAuth()
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        return "{\"manufacturer\":\"${Build.MANUFACTURER}\",\"model\":\"${Build.MODEL}\",\"osVersion\":\"${Build.VERSION.RELEASE}\",\"sdkInt\":${Build.VERSION.SDK_INT}}"
    }
}
