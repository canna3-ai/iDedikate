package com.memoria.idedikate.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.launch

/** Remembers whether this install has seen the first-run guide. */
object Onboarding {
    private const val PREFS = "onboarding"
    private const val KEY_SEEN = "guide_seen"

    fun isSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_SEEN, true) }
    }
}

private class GuidePage(
    val title: String,
    val body: String,
    /** Full-color offering artwork, shown side by side. */
    val illustrations: List<ImageVector> = emptyList(),
    /** Single-color Material icon, used when there's no artwork. */
    val icon: ImageVector? = null
)

private val guidePages = listOf(
    GuidePage(
        title = "Remember them where it matters",
        body = "iDedikate lets you leave a virtual memorial for someone you love at a place that " +
            "meant something: their home, a favorite park, or their resting place. Visit it any " +
            "time through your camera, in augmented reality.",
        illustrations = listOf(OfferingIcons.MemorialPlaque)
    ),
    GuidePage(
        title = "Place a memorial on the map",
        body = "On the Map tab, move the crosshair to a meaningful spot (or long-press it) and tap " +
            "\"Place memorial\". Each memorial uses 1 token. Keep it private, share it with family " +
            "by email, or make it public.",
        icon = Icons.Default.AddLocationAlt
    ),
    GuidePage(
        title = "Leave offerings",
        body = "Add a memorial plaque, incense, lilies and candles to your memorial. You start with " +
            "free tokens, and you can earn more offerings in the Wallet by watching a short video.",
        illustrations = listOf(OfferingIcons.IncenseStick, OfferingIcons.Flowers, OfferingIcons.Candle)
    ),
    GuidePage(
        title = "Visit in AR",
        body = "Tap \"View in AR\" on any memorial. Stand near its spot to see it where you placed " +
            "it, or set it down on any flat surface wherever you are.",
        icon = Icons.Default.ViewInAr
    )
)

/** First-run walkthrough explaining what iDedikate is for and how to use it. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState { guidePages.size }
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == guidePages.lastIndex

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            // Fixed height, so the page doesn't jump when Skip disappears on the last page
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLastPage) {
                    TextButton(onClick = onFinish) { Text("Skip") }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                GuidePageContent(guidePages[index])
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                guidePages.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (selected) 10.dp else 8.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) { Text("Back") }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    if (isLastPage) onFinish()
                    else coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }) {
                    Text(if (isLastPage) "Get started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun GuidePageContent(page: GuidePage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        ) {
            if (page.icon != null) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(88.dp)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val size = if (page.illustrations.size > 1) 52.dp else 104.dp
                    page.illustrations.forEach {
                        Icon(it, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(size))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenPreview() {
    OnboardingScreen(onFinish = {})
}
