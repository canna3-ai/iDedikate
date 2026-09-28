package com.memoria.idedikate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.memoria.idedikate.R

/**
 * Headings: Cormorant Garamond, a classical serif reminiscent of engraved lettering.
 * Its regular weight is too thin on screen, so normal-weight text uses the Medium cut.
 * Licenses for both fonts are in assets/licenses.
 */
val CormorantGaramond = FontFamily(
    Font(R.font.cormorant_garamond_medium, FontWeight.Normal),
    Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
    Font(R.font.cormorant_garamond_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_garamond_bold, FontWeight.Bold)
)

/** Body and UI text: Lato, which stays legible at small sizes. */
val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold)
)

private val Default = Typography()

// "lnum": Cormorant defaults to old-style numerals, where 0 reads like the letter o in counts
private fun TextStyle.heading() = copy(fontFamily = CormorantGaramond, fontFeatureSettings = "lnum")
private fun TextStyle.body() = copy(fontFamily = Lato)

val Typography = Typography(
    displayLarge = Default.displayLarge.heading(),
    displayMedium = Default.displayMedium.heading(),
    displaySmall = Default.displaySmall.heading(),
    headlineLarge = Default.headlineLarge.heading(),
    headlineMedium = Default.headlineMedium.heading(),
    headlineSmall = Default.headlineSmall.heading(),
    // Cormorant has a small x-height, so its titles run a little larger than Material's defaults
    titleLarge = Default.titleLarge.heading().copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.heading().copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    // Small titles label controls (dialog sections, radio options), so they stay in the body font
    titleSmall = Default.titleSmall.body(),
    bodyLarge = Default.bodyLarge.body(),
    bodyMedium = Default.bodyMedium.body(),
    bodySmall = Default.bodySmall.body(),
    labelLarge = Default.labelLarge.body(),
    labelMedium = Default.labelMedium.body(),
    labelSmall = Default.labelSmall.body()
)
