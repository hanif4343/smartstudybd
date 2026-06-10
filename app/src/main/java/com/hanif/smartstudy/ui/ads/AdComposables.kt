package com.hanif.smartstudy.ui.ads

import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.hanif.smartstudy.util.AdManager

@Composable
fun AdBannerView(
    adUnitId : String   = AdManager.BANNER_HOME,
    modifier : Modifier = Modifier.fillMaxWidth()
) {
    val context = LocalContext.current
    // AdMob init না হলে silently skip — crash করবে না
    var adReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            // MobileAds initialize check
            adReady = true
        } catch (e: Exception) {
            Log.e("AdBannerView", "Ad not ready: ${e.message}")
            adReady = false
        }
    }

    if (!adReady) return

    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = modifier,
            factory  = { ctx ->
                try {
                    AdView(ctx).apply {
                        setAdSize(
                            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, 320)
                        )
                        setAdUnitId(adUnitId)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(err: LoadAdError) {
                                Log.w("AdBannerView", "Failed: ${err.message}")
                            }
                        }
                        loadAd(AdManager.request())
                    }
                } catch (e: Exception) {
                    Log.e("AdBannerView", "factory error: ${e.message}")
                    // Blank view fallback — crash করবে না
                    android.view.View(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(0, 0)
                    }
                }
            }
        )
    }
}
