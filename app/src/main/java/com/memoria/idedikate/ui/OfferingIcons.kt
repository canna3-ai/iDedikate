package com.memoria.idedikate.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.memoria.idedikate.model.OfferingType

/**
 * Full-colour illustrations for the wallet's offering items, drawn on a 48x48 grid.
 * Render with `Icon(..., tint = Color.Unspecified)` so the colours are kept.
 */
object OfferingIcons {

    private val Granite = Color(0xFF455A64)
    private val StoneMid = Color(0xFF78909C)
    private val StoneDark = Color(0xFF546E7A)
    private val Silver = Color(0xFFCFD8DC)
    private val Petal = Color(0xFFF5F5F5)
    private val Gold = Color(0xFFD9A62E)
    private val JossRed = Color(0xFFC62828)
    private val StickBrown = Color(0xFF8D5B2E)
    private val Ember = Color(0xFFFF7043)
    private val Smoke = Color(0xFF9E9E9E)
    private val Wax = Color(0xFFFFF8E7)
    private val WaxShade = Color(0xFFEFE3C8)
    private val WaxOutline = Color(0xFFC9B68E)
    private val Wick = Color(0xFF4E342E)
    private val FlameOuter = Color(0xFFFF9800)
    private val FlameInner = Color(0xFFFFEB3B)
    private val FlameGlow = Color(0xFFFFE082)
    private val Holder = Color(0xFFB0BEC5)
    private val BloomPink = Color(0xFFF48FB1)
    private val BloomLavender = Color(0xFFB39DDB)
    private val Ribbon = Color(0xFF7986CB)
    private val Leaf = Color(0xFF388E3C)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).apply(block).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) = oval(cx, cy, r, r)

    private fun PathBuilder.oval(cx: Float, cy: Float, rx: Float, ry: Float) {
        moveTo(cx - rx, cy)
        arcTo(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + rx, y1 = cy)
        arcTo(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - rx, y1 = cy)
        close()
    }

    private fun PathBuilder.rect(left: Float, top: Float, right: Float, bottom: Float) {
        moveTo(left, top); lineTo(right, top); lineTo(right, bottom); lineTo(left, bottom); close()
    }

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right, y1 = top + r)
        lineTo(right, bottom - r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right - r, y1 = bottom)
        lineTo(left + r, bottom)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left, y1 = bottom - r)
        lineTo(left, top + r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left + r, y1 = top)
        close()
    }

    /**
     * Memorial plaque that suits any faith (or none): polished grey stone with a silver border,
     * a flower emblem and engraved lines for a name and dates, standing on a stone base.
     */
    val MemorialPlaque: ImageVector by lazy {
        icon("MemorialPlaque") {
            // Stone base and plinth
            path(fill = SolidColor(StoneDark)) { roundRect(9f, 40.5f, 39f, 45f, 1f) }
            path(fill = SolidColor(StoneMid)) { roundRect(12.5f, 37f, 35.5f, 41f, 0.8f) }
            // Polished slab with a lighter sheen on one side
            path(fill = SolidColor(Granite)) { roundRect(13f, 5f, 35f, 38f, 4f) }
            path(fill = SolidColor(Color.White), fillAlpha = 0.08f) { roundRect(13f, 5f, 21f, 38f, 4f) }
            // Silver border
            path(stroke = SolidColor(Silver), strokeLineWidth = 1.2f) { roundRect(15.5f, 7.5f, 32.5f, 35.5f, 2.5f) }
            // Flower emblem: five petals around a centre, with two leaves
            path(fill = SolidColor(Leaf)) {
                moveTo(24f, 17.5f); curveTo(21f, 18.5f, 19.5f, 17.5f, 19f, 16f); curveTo(21f, 15.5f, 23f, 16f, 24f, 17.5f); close()
                moveTo(24f, 17.5f); curveTo(27f, 18.5f, 28.5f, 17.5f, 29f, 16f); curveTo(27f, 15.5f, 25f, 16f, 24f, 17.5f); close()
            }
            path(fill = SolidColor(Petal)) {
                circle(24f, 10.8f, 1.5f); circle(26.4f, 12.5f, 1.5f); circle(25.5f, 15.2f, 1.5f)
                circle(22.5f, 15.2f, 1.5f); circle(21.6f, 12.5f, 1.5f)
            }
            path(fill = SolidColor(Gold)) { circle(24f, 13.2f, 1.1f) }
            // Engraved lines: name, dates, epitaph
            path(stroke = SolidColor(Silver), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
                moveTo(18.5f, 22f); lineTo(29.5f, 22f)
            }
            path(stroke = SolidColor(Silver), strokeLineWidth = 1.1f, strokeLineCap = StrokeCap.Round) {
                moveTo(20.5f, 26f); lineTo(27.5f, 26f)
            }
            path(stroke = SolidColor(Silver), strokeAlpha = 0.7f, strokeLineWidth = 0.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(19f, 30f); lineTo(29f, 30f)
                moveTo(20.5f, 32.5f); lineTo(27.5f, 32.5f)
            }
        }
    }

    /** A single joss stick with a glowing tip and curling smoke. */
    val IncenseStick: ImageVector by lazy {
        icon("IncenseStick") {
            // Smoke: two soft curls rising from the tip
            path(
                stroke = SolidColor(Smoke),
                strokeAlpha = 0.85f,
                strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(24f, 15f)
                curveTo(20f, 12f, 28f, 9.5f, 24f, 6.5f)
                curveTo(21.5f, 4.6f, 24.5f, 3f, 23f, 1.8f)
            }
            path(
                stroke = SolidColor(Smoke),
                strokeAlpha = 0.5f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(25f, 13f)
                curveTo(29f, 11f, 27f, 7f, 30f, 5f)
            }
            // Burning incense part of the stick
            path(stroke = SolidColor(StickBrown), strokeLineWidth = 3.2f, strokeLineCap = StrokeCap.Round) {
                moveTo(24f, 17f); lineTo(24f, 37f)
            }
            // Red handle
            path(stroke = SolidColor(JossRed), strokeLineWidth = 3.8f, strokeLineCap = StrokeCap.Round) {
                moveTo(24f, 37f); lineTo(24f, 45f)
            }
            // Glowing ember
            path(fill = SolidColor(Ember)) { circle(24f, 16.5f, 2.4f) }
        }
    }

    /** An ivory pillar candle with a warm flame and a soft glow. */
    val Candle: ImageVector by lazy {
        icon("Candle") {
            // Glow around the flame
            path(fill = SolidColor(FlameGlow), fillAlpha = 0.35f) { circle(24f, 11f, 8f) }
            // Candle body with a slightly darker side for roundness
            path(fill = SolidColor(Wax)) { rect(17f, 19f, 31f, 43f) }
            path(fill = SolidColor(WaxShade)) { rect(27f, 19f, 31f, 43f) }
            path(stroke = SolidColor(WaxOutline), strokeLineWidth = 0.8f) { rect(17f, 19f, 31f, 43f) }
            // Softened top rim
            path(fill = SolidColor(WaxShade)) { oval(24f, 19f, 7f, 1.6f) }
            path(fill = SolidColor(Wax)) { oval(24f, 19f, 5.8f, 1.1f) }
            // Wick
            path(stroke = SolidColor(Wick), strokeLineWidth = 1.2f, strokeLineCap = StrokeCap.Round) {
                moveTo(24f, 18.5f); lineTo(24f, 15.5f)
            }
            // Flame: outer orange teardrop, inner yellow core
            path(fill = SolidColor(FlameOuter)) {
                moveTo(24f, 4f)
                curveTo(28.5f, 9f, 28.5f, 15.5f, 24f, 16.5f)
                curveTo(19.5f, 15.5f, 19.5f, 9f, 24f, 4f)
                close()
            }
            path(fill = SolidColor(FlameInner)) {
                moveTo(24f, 8.5f)
                curveTo(26.2f, 11f, 26.2f, 14.5f, 24f, 15.3f)
                curveTo(21.8f, 14.5f, 21.8f, 11f, 24f, 8.5f)
                close()
            }
            // Holder
            path(fill = SolidColor(Holder)) { oval(24f, 43.5f, 11f, 2.5f) }
        }
    }

    /** A small bouquet: three blooms on green stems, tied with a ribbon. */
    val Flowers: ImageVector by lazy {
        icon("Flowers") {
            // Stems fanning out from the tie
            path(stroke = SolidColor(Leaf), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
                moveTo(24f, 44f); lineTo(24f, 18f)
                moveTo(24f, 36f); lineTo(15f, 21f)
                moveTo(24f, 36f); lineTo(33f, 21f)
            }
            // Leaves
            path(fill = SolidColor(Leaf)) {
                moveTo(24f, 32f); curveTo(19f, 31f, 17f, 28f, 17.5f, 26f); curveTo(20.5f, 26.5f, 23f, 29f, 24f, 32f); close()
                moveTo(24f, 32f); curveTo(29f, 31f, 31f, 28f, 30.5f, 26f); curveTo(27.5f, 26.5f, 25f, 29f, 24f, 32f); close()
            }
            // Blooms: five petals around a golden centre
            bloom(24f, 13f, Petal)
            bloom(14f, 18f, BloomPink)
            bloom(34f, 18f, BloomLavender)
            // Ribbon tie
            path(fill = SolidColor(Ribbon)) {
                moveTo(24f, 36f); lineTo(19f, 33.5f); lineTo(19.5f, 38.5f); close()
                moveTo(24f, 36f); lineTo(29f, 33.5f); lineTo(28.5f, 38.5f); close()
            }
            path(fill = SolidColor(Ribbon)) { circle(24f, 36f, 1.8f) }
        }
    }

    private fun ImageVector.Builder.bloom(cx: Float, cy: Float, color: Color) {
        path(fill = SolidColor(color)) {
            for (i in 0 until 5) {
                val angle = Math.toRadians(i * 72.0 - 90.0)
                circle(cx + 3.2f * kotlin.math.cos(angle).toFloat(), cy + 3.2f * kotlin.math.sin(angle).toFloat(), 2.6f)
            }
        }
        path(stroke = SolidColor(Color.Black), strokeAlpha = 0.08f, strokeLineWidth = 0.6f) { circle(cx, cy, 5.6f) }
        path(fill = SolidColor(Gold)) { circle(cx, cy, 1.8f) }
    }

    /** The illustration for each wallet offering. */
    fun forType(type: OfferingType): ImageVector = when (type) {
        OfferingType.PLAQUE -> MemorialPlaque
        OfferingType.INCENSE -> IncenseStick
        OfferingType.FLOWERS -> Flowers
        OfferingType.CANDLES -> Candle
    }
}
