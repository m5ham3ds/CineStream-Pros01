package com.example.utils

import android.content.Context
import com.example.data.repository.UserSecurityManager
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.Ad

object AdManager {
    private var startAppAd: StartAppAd? = null

    fun showInterstitial(context: Context) {
        // Honor Admin Panel isPremium status and forcedAdsOverride
        if (UserSecurityManager.isVip() || UserSecurityManager.getForcedAdsRequired() == 0) {
            return
        }

        if (startAppAd == null) {
            startAppAd = StartAppAd(context)
        }
        
        if (startAppAd?.isReady == true) {
            startAppAd?.showAd()
            startAppAd?.loadAd() // Load next ad
        } else {
            startAppAd?.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    // Preload for next time, but show now since user clicked
                    startAppAd?.showAd()
                    startAppAd?.loadAd()
                }
                override fun onFailedToReceiveAd(ad: Ad?) {}
            })
        }
    }
}
