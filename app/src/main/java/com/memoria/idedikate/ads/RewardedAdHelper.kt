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

/**
 * @param adUnitId used in every build: rewards are credited by AdMob's server-side verification
 * callback (functions/index.js), which Google's sample ad units never send. Debug builds get test
 * ads on these units by registering the phone as a test device (see MainActivity).
 * @param configuredRewardAmount what one completed ad credits to the wallet. The Cloud Function
 * is the source of truth (it maps each ad unit to its reward); keep this, the function and the
 * ad unit's reward in AdMob in sync.
 */
enum class RewardAdType(val adUnitId: String, val configuredRewardAmount: Int) {
    REWARDED_TOKENS("ca-app-pub-7728928885479787/5204992073", configuredRewardAmount = 1),
    REWARDED_DISPLAY("ca-app-pub-7728928885479787/6326502052", configuredRewardAmount = 1),
    REWARDED_INCENSE("ca-app-pub-7728928885479787/6763731739", configuredRewardAmount = 5),
    REWARDED_FLOWERS("ca-app-pub-7728928885479787/2455539800", configuredRewardAmount = 2),
    REWARDED_CANDLES("ca-app-pub-7728928885479787/1912614324", configuredRewardAmount = 2)
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
                    Log.d(tag, "Failed to load ${type.name}: $adError")
                    loadingAds.remove(type)
                    loadedAds.remove(type)
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(tag, "Ad was loaded for ${type.name}.")
                    loadingAds.remove(type)

                    // The verification callback credits this user; without it the reward is lost
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                    val options = ServerSideVerificationOptions.Builder()
                        .setUserId(uid)
                        .setCustomData(type.name)
                        .build()
                    ad.setServerSideVerificationOptions(options)

                    loadedAds[type] = ad
                }
            })
    }

    /**
     * Shows the ad for [type]. Returns false (and starts loading) if no ad is ready yet.
     * [onRewarded] only means the ad was completed: AdMob then calls the Cloud Function, which
     * credits the wallet a moment later.
     */
    fun showAd(activity: Activity, type: RewardAdType, onRewarded: () -> Unit): Boolean {
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
                if (rewardItem.amount != type.configuredRewardAmount) {
                    Log.w(tag, "AdMob reward for ${type.name} is ${rewardItem.amount}, but the app credits ${type.configuredRewardAmount}. Update AdMob or configuredRewardAmount.")
                }
                Log.d(tag, "User earned the reward for ${type.name}; waiting for server-side verification")
                onRewarded()
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
