package com.memoria.idedikate.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.memoria.idedikate.BuildConfig

@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    // Debug builds use Google's sample banner unit, which always fills
    adUnitId: String = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/6300978111" else "ca-app-pub-7728928885479787/1984855801"
) {
    AndroidView(
        // Reserve the banner's height before an ad loads, so the screen above doesn't shift when it arrives
        modifier = modifier.fillMaxWidth().height(AdSize.BANNER.height.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() }
    )
}
