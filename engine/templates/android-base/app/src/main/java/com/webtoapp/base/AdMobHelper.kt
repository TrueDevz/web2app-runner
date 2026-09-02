package com.webtoapp.base

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AdMobHelper(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var lastInterstitialUnitId: String = ""
    private var isLoading = false

    fun initialize() {
        MobileAds.initialize(context) {}
    }

    fun loadBanner(container: FrameLayout, bannerUnitId: String) {
        if (bannerUnitId.isBlank()) return
        val adView = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = bannerUnitId
        }
        container.removeAllViews()
        container.addView(adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    fun loadInterstitial(interstitialUnitId: String) {
        if (interstitialUnitId.isBlank() || isLoading || interstitialAd != null) return
        lastInterstitialUnitId = interstitialUnitId
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            interstitialUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onAdDismissed: (() -> Unit)? = null): Boolean {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    if (lastInterstitialUnitId.isNotBlank()) {
                        loadInterstitial(lastInterstitialUnitId)
                    }
                    onAdDismissed?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    if (lastInterstitialUnitId.isNotBlank()) {
                        loadInterstitial(lastInterstitialUnitId)
                    }
                    onAdDismissed?.invoke()
                }
            }
            ad.show(activity)
            return true
        }
        return false
    }
}
