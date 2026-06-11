package com.hanif.smartstudy.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

// ─────────────────────────────────────────────────────────
// AdManager — সব AdMob Unit ID এক জায়গায়
//
// ⚠️  Production Release এ TEST ID গুলো নিজের আসল ID দিয়ে replace করো
//
// Test IDs reference: https://developers.google.com/admob/android/test-ads
// ─────────────────────────────────────────────────────────

object AdManager {

    private const val TAG = "AdManager"

    // ── Google Official Test IDs ─────────────────────────────
    // এগুলো Google এর নিজের test ad unit — কখনো block হয় না
    const val BANNER_TEST_ID       = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_TEST_ID     = "ca-app-pub-3940256099942544/5224354917"
    const val NATIVE_TEST_ID       = "ca-app-pub-3940256099942544/2247696110"

    // ── Production IDs (পরে এখানে নিজের ID বসাবে) ──────────
    // const val BANNER_HOME_ID              = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
    // const val BANNER_QUIZ_ID              = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
    // const val BANNER_QBANK_SUBJECT_ID     = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
    // const val BANNER_STUDY_ID             = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
    // const val INTERSTITIAL_RESULT_ID      = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
    // const val INTERSTITIAL_CHALLENGE_ID   = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
    // const val REWARDED_XP_BONUS_ID        = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
    // const val REWARDED_DAILY_LOGIN_ID     = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"

    // ── Active IDs (test phase এ test ID, release এ production ID) ──
    val BANNER_HOME              get() = BANNER_TEST_ID
    val BANNER_QUIZ_LIST         get() = BANNER_TEST_ID
    val BANNER_QBANK_SUBJECT     get() = BANNER_TEST_ID   // QBank subject list
    val BANNER_STUDY             get() = BANNER_TEST_ID   // Study screen bottom
    val BANNER_WEEKEND           get() = BANNER_TEST_ID
    val INTERSTITIAL_RESULT      get() = INTERSTITIAL_TEST_ID
    val INTERSTITIAL_CHALLENGE   get() = INTERSTITIAL_TEST_ID   // Challenge শেষে
    val REWARDED_XP_BONUS        get() = REWARDED_TEST_ID
    val REWARDED_DAILY_LOGIN     get() = REWARDED_TEST_ID       // Daily login bonus
    val NATIVE_HOME              get() = NATIVE_TEST_ID

    // ── Standard AdRequest ───────────────────────────────────
    fun request(): AdRequest = AdRequest.Builder().build()

    // ── Interstitial load helper ─────────────────────────────
    fun loadInterstitial(
        context  : Context,
        adUnitId : String = INTERSTITIAL_RESULT,
        onLoaded : (InterstitialAd) -> Unit,
        onFailed : () -> Unit = {}
    ) {
        InterstitialAd.load(
            context,
            adUnitId,
            request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial loaded")
                    onLoaded(ad)
                }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    Log.e(TAG, "Interstitial failed: ${err.message}")
                    onFailed()
                }
            }
        )
    }

    // ── Rewarded load helper ─────────────────────────────────
    fun loadRewarded(
        context  : Context,
        onLoaded : (RewardedAd) -> Unit,
        onFailed : () -> Unit = {}
    ) {
        RewardedAd.load(
            context,
            REWARDED_XP_BONUS,
            request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded loaded")
                    onLoaded(ad)
                }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    Log.e(TAG, "Rewarded failed: ${err.message}")
                    onFailed()
                }
            }
        )
    }

    // ── Show Interstitial ────────────────────────────────────
    fun showInterstitial(
        activity   : Activity,
        ad         : InterstitialAd?,
        onDismissed: () -> Unit = {}
    ) {
        if (ad == null) { onDismissed(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { onDismissed() }
            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                Log.e(TAG, "Interstitial show failed: ${err.message}")
                onDismissed()
            }
        }
        ad.show(activity)
    }

    // ── Show Rewarded ────────────────────────────────────────
    fun showRewarded(
        activity   : Activity,
        ad         : RewardedAd?,
        onRewarded : (Int) -> Unit,   // Int = reward amount
        onDismissed: () -> Unit = {}
    ) {
        if (ad == null) { onDismissed(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { onDismissed() }
            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                Log.e(TAG, "Rewarded show failed: ${err.message}")
                onDismissed()
            }
        }
        ad.show(activity) { reward ->
            Log.d(TAG, "User rewarded: ${reward.amount} ${reward.type}")
            onRewarded(reward.amount)
        }
    }
}
