package com.memoria.idedikate.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.firebase.auth.FirebaseAuth

enum class RewardAdType(val adUnitId: String) {
    REWARDED_TOKENS("ca-app-pub-7728928885479787/5204992073"),
    REWARDED_DISPLAY("ca-app-pub-7728928885479787/6326502052"),
    REWARDED_INCENSE("ca-app-pub-7728928885479787/6763731739"),
    REWARDED_FRUITS("ca-app-pub-7728928885479787/2455539800"),
    REWARDED_FOOD("ca-app-pub-7728928885479787/1912614324")
}

class RewardedAdHelper(context: Context) {
    // Application context so the helper never leaks an Activity
    private val context = context.applicationContext
    private val loadedAds = mutableMapOf<RewardAdType, RewardedAd>()
    private val loadingAds = mutableSetOf<RewardAdType>()
    private val tag = "RewardedAdHelper"

    init {
        // Pre-load all types initially to have them ready
        RewardAdType.entries.forEach { loadAd(it) }
    }

    fun loadAd(type: RewardAdType) {
        if (loadedAds.containsKey(type) || !loadingAds.add(type)) return // Already loaded or loading

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            type.adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(tag, "Failed to load ${type.name}: " + adError.toString())
                    loadingAds.remove(type)
                    loadedAds.remove(type)
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(tag, "Ad was loaded for ${type.name}.")
                    loadingAds.remove(type)

                    // Setup Server-Side Verification options
                    FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                        val options = ServerSideVerificationOptions.Builder()
                            .setUserId(uid)
                            .setCustomData(type.name)
                            .build()
                        ad.setServerSideVerificationOptions(options)
                    }

                    loadedAds[type] = ad
                }
            })
    }

    /** Shows the ad for [type]. Returns false (and starts loading) if no ad is ready yet. */
    fun showAd(activity: Activity, type: RewardAdType, onRewarded: (Int, String) -> Unit): Boolean {
        // Rewarded ads are single-use; take it out now so a double tap can't show it twice
        val rewardedAd = loadedAds.remove(type)
        if (rewardedAd != null) {
            rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.d(tag, "Ad showed fullscreen content.")
                }

                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    Log.d(tag, "Ad failed to show fullscreen content: ${e.message}")
                    loadAd(type)
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(tag, "Ad was dismissed.")
                    loadAd(type) // Pre-load the next ad
                }
            }

            rewardedAd.show(activity) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardItemType = rewardItem.type
                Log.d(tag, "User earned the reward. Amount: $rewardAmount, Type: $rewardItemType")
                onRewarded(rewardAmount, rewardItemType)
            }
            return true
        } else {
            Log.d(tag, "The rewarded ad for ${type.name} wasn't ready yet.")
            // Try loading again just in case
            loadAd(type)
            return false
        }
    }
}
