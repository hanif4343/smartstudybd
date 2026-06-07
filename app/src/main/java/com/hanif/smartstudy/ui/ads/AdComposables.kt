package com.hanif.smartstudy.ui.ads

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.hanif.smartstudy.util.AdManager

// ─────────────────────────────────────────────────────────
// AdBannerView — Real AdMob Banner (320×50 / Adaptive)
// ─────────────────────────────────────────────────────────

@Composable
fun AdBannerView(
    adUnitId : String   = AdManager.BANNER_HOME,
    modifier : Modifier = Modifier.fillMaxWidth()
) {
    val context = LocalContext.current

    Box(
        modifier          = modifier,
        contentAlignment  = Alignment.Center
    ) {
        AndroidView(
            modifier = modifier,
            factory  = {
                AdView(context).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, 320))
                    setAdUnitId(adUnitId)
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(err: LoadAdError) {
                            // Silent fail — view হবে না
                        }
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    loadAd(AdManager.request())
                }
            }
        )
    }
}
