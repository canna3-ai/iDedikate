package com.memoria.idedikate.ar

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node

/**
 * A blinking arrow floating above a memorial placed at its real location, pointing down at it so
 * it's easy to spot from afar. Only shown beyond [SHOW_BEYOND_M]; it grows with distance so it
 * stays about the same size on screen. Add it to the memorial's anchor node and call [update]
 * with the viewer's distance every frame.
 */
class LocatorBeacon(engine: Engine, materialLoader: MaterialLoader) : Node(engine) {

    private val arrow: Node

    init {
        val glow = with(MemorialItems) {
            materialLoader.createColorInstance(color = COLOR).apply { setEmissive(COLOR) }
        }
        arrow = with(MemorialItems) { geometryNode(engine, arrowMesh, arrowVertices, listOf(glow)) }
            .apply { isShadowCaster = false }
        addChildNode(arrow)
        isVisible = false
        onFrame = { frameTimeNanos ->
            val seconds = frameTimeNanos / 1_000_000_000.0
            arrow.isVisible = seconds % BLINK_SECONDS < BLINK_SECONDS * BLINK_ON_FRACTION
        }
    }

    fun update(distanceMeters: Double) {
        // A little hysteresis, so GPS jitter right at the threshold doesn't flicker it on and off
        isVisible = if (isVisible) distanceMeters > SHOW_BEYOND_M - HYSTERESIS_M
        else distanceMeters > SHOW_BEYOND_M + HYSTERESIS_M
        if (!isVisible) return
        val size = (distanceMeters * SIZE_PER_METER).toFloat()
        // Hover clear of the offerings, higher the bigger it is
        position = Position(0f, LIFT_M + size * 0.5f, 0f)
        scale = Scale(size)
    }

    companion object {
        /** Below this distance the memorial itself is easy enough to see. */
        const val SHOW_BEYOND_M = 5.0
        private const val HYSTERESIS_M = 1.0
        /** Arrow height per meter of distance: about 9° tall on screen. */
        private const val SIZE_PER_METER = 0.15
        private const val LIFT_M = 1f
        private const val BLINK_SECONDS = 1.0
        private const val BLINK_ON_FRACTION = 0.6
        private val COLOR = Color(1f, 0.78f, 0.2f)

        /** Arrow 1 tall with its tip at the origin, pointing down: a cone head and a shaft. */
        private val arrowMesh: MeshBuilder by lazy {
            MeshBuilder(1).apply {
                val headHeight = 0.45f
                val headRadius = 0.5f
                val shaftRadius = 0.22f
                // Profile from the tip up, closed at the top
                val profile = listOf(
                    0f to 0f,
                    headHeight to headRadius,
                    headHeight + 0.001f to shaftRadius,
                    1f to shaftRadius,
                    1.001f to 0f
                )
                tube(
                    0, Mat4.identity(),
                    path = profile.map { Float3(0f, it.first, 0f) },
                    radius = 0f,
                    sides = 16,
                    radii = profile.map { it.second }
                )
            }
        }
        private val arrowVertices by lazy { with(MemorialItems) { arrowMesh.toVertices() } }
    }
}
