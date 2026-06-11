package com.hanif.smartstudy.ui.ads

import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.hanif.smartstudy.util.AdManager

/**
 * AdBannerView — AdMob initialized হলেই load করে, না হলে চুপ থাকে।
 * কোনো অবস্থায় crash করবে না।
 */
@Composable
fun AdBannerView(
    adUnitId : String   = AdManager.BANNER_HOME,
    modifier : Modifier = Modifier.fillMaxWidth()
) {
    val context = LocalContext.current

    // MobileAds initialized কিনা — state হিসেবে track করো
    var initialized by remember { mutableStateOf(AdInitTracker.isReady) }

    // Initialized না থাকলে 3 সেকেন্ড পর পর check করো
    LaunchedEffect(Unit) {
        if (!initialized) {
            repeat(10) {
                kotlinx.coroutines.delay(1000)
                if (AdInitTracker.isReady) {
                    initialized = true
                    return@repeat
                }
            }
        }
    }

    // Initialize না হলে কিছুই show করো না — crash করবে না
    if (!initialized) return

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            try {
                val adView = AdView(ctx)
                val adSize = try {
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, 320)
                } catch (e: Exception) {
                    AdSize.BANNER   // fallback
                }
                adView.setAdSize(adSize)
                adView.setAdUnitId(adUnitId)
                adView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adView.adListener = object : AdListener() {
                    override fun onAdFailedToLoad(err: LoadAdError) {
                        Log.w("AdBannerView", "Failed: ${err.message}")
                    }
                }
                try { adView.loadAd(AdManager.request()) }
                catch (e: Exception) { Log.e("AdBannerView", "loadAd: ${e.message}") }
                adView
            } catch (e: Exception) {
                Log.e("AdBannerView", "factory: ${e.message}")
                android.view.View(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                }
            }
        }
    )
}

/**
 * QuizBannerEvery10 — Quiz screen এ প্রতি ১০ প্রশ্নের পর sticky top banner দেখায়।
 *
 * LazyList এর firstVisibleItemIndex pass করো।
 * index 0-9  → কোনো ad নেই
 * index 10   → banner দেখায়  (প্রতি ১০ এর গুণিতকে)
 * index 20   → banner দেখায়
 * ইত্যাদি।
 */
@Composable
fun QuizBannerEvery10(
    currentQuestionIndex: Int,
    adUnitId: String = AdManager.BANNER_QUIZ_LIST
) {
    // প্রতি ১০ প্রশ্নের threshold — index 10, 20, 30 ...
    val showBanner = currentQuestionIndex > 0 && currentQuestionIndex % 10 == 0

    if (showBanner) {
        // key(currentQuestionIndex) দিলে threshold বদলালে নতুন ad load হয়
        key(currentQuestionIndex / 10) {
            AdBannerView(
                adUnitId = adUnitId,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * StickyBottomBannerView — Screen এর একদম নিচে সব সময় থাকে।
 * Study / QBank screen এর জন্য — user দীর্ঘ সময় থাকে।
 *
 * Box layout এ Alignment.BottomCenter এ রাখো।
 */
@Composable
fun StickyBottomBannerView(
    adUnitId: String = AdManager.BANNER_STUDY
) {
    AdBannerView(
        adUnitId = adUnitId,
        modifier = Modifier.fillMaxWidth()
    )
}

/** MobileAds initialize কবে হলো সেটা track করে */
object AdInitTracker {
    @Volatile var isReady: Boolean = false
}
