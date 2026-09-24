package com.memoria.idedikate.ar

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.memoria.idedikate.ads.RewardAdType
import com.memoria.idedikate.model.MemorialOfferings
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class MemorialItemType(val rewardAdType: RewardAdType) {
    PLAQUE(RewardAdType.REWARDED_DISPLAY),
    INCENSE_STICK(RewardAdType.REWARDED_INCENSE),
    INCENSE_POT(RewardAdType.REWARDED_INCENSE),
    INCENSE_BOX(RewardAdType.REWARDED_INCENSE),
    INCENSE_PAPER(RewardAdType.REWARDED_INCENSE),
    FRUIT_OFFERING(RewardAdType.REWARDED_FRUITS),
    FOOD_OFFERING(RewardAdType.REWARDED_FOOD),
    CANDLE(RewardAdType.REWARDED_DISPLAY),
    FLOWER(RewardAdType.REWARDED_DISPLAY)
}

data class Offset(
    val x: Float,
    val y: Float,
    val z: Float,
    /** Rotation around the vertical axis, in degrees. */
    val yaw: Float
)

/**
 * Procedural memorial offerings built from SceneView primitives.
 * Every builder returns a node whose origin is at the base centre of the item.
 * Material instances are owned by [MaterialLoader] and released when it is destroyed.
 */
object MemorialItems {

    private const val PLAQUE_SPACING = 0.26f
    private const val PLATE_SPACING = 0.18f
    private const val POT_Z = 0.25f
    private const val POT_INNER_RADIUS = 0.035f

    /**
     * Arranges a memorial's offerings around its anchor: plaques in a row at the back flanked by
     * two candles, incense sticks in a pot at the front, fruit plates to the left and food bowls
     * to the right (each in a compact grid, so large quantities stay close together).
     */
    fun layoutFor(offerings: MemorialOfferings): List<Pair<MemorialItemType, Offset>> = buildList {
        val plaqueCount = offerings.plaques
        repeat(plaqueCount) { i ->
            add(MemorialItemType.PLAQUE to Offset((i - (plaqueCount - 1) / 2f) * PLAQUE_SPACING, 0f, 0f, 0f))
        }

        // Candles are decoration, not wallet items, so every memorial gets a pair
        val candleX = (plaqueCount - 1).coerceAtLeast(0) / 2f * PLAQUE_SPACING + 0.2f
        add(MemorialItemType.CANDLE to Offset(-candleX, 0f, 0.1f, 0f))
        add(MemorialItemType.CANDLE to Offset(candleX, 0f, 0.1f, 0f))

        val sticks = offerings.incenseSticks
        if (sticks > 0) {
            add(MemorialItemType.INCENSE_POT to Offset(0f, 0f, POT_Z, 0f))
            // Sunflower spiral spreads any number of sticks evenly across the pot opening
            repeat(sticks) { i ->
                val radius = POT_INNER_RADIUS * sqrt((i + 0.5f) / sticks)
                val angle = i * GOLDEN_ANGLE
                add(MemorialItemType.INCENSE_STICK to Offset(radius * cos(angle), 0.05f, POT_Z + radius * sin(angle), 0f))
            }
        }

        addGrid(MemorialItemType.FRUIT_OFFERING, offerings.fruit, xStart = -0.3f, xDirection = -1f)
        addGrid(MemorialItemType.FOOD_OFFERING, offerings.food, xStart = 0.3f, xDirection = 1f)
    }

    private fun MutableList<Pair<MemorialItemType, Offset>>.addGrid(
        type: MemorialItemType,
        count: Int,
        xStart: Float,
        xDirection: Float
    ) {
        if (count <= 0) return
        val columns = ceil(sqrt(count.toFloat())).toInt()
        repeat(count) { i ->
            val column = i % columns
            val row = i / columns
            add(type to Offset(xStart + xDirection * column * PLATE_SPACING, 0f, 0.2f + row * PLATE_SPACING, 0f))
        }
    }

    private const val GOLDEN_ANGLE = 2.3999631f // radians

    fun renderOfferings(
        engine: Engine,
        materialLoader: MaterialLoader,
        anchorNode: Node,
        items: List<Pair<MemorialItemType, Offset>>,
        photoTexture: Texture? = null
    ) {
        items.forEach { (type, offset) ->
            runCatching {
                val node = when (type) {
                    MemorialItemType.PLAQUE -> plaque(engine, materialLoader, photoTexture)
                    MemorialItemType.INCENSE_STICK -> incenseStick(engine, materialLoader)
                    MemorialItemType.INCENSE_POT -> incensePot(engine, materialLoader)
                    MemorialItemType.INCENSE_BOX -> incenseBox(engine, materialLoader)
                    MemorialItemType.INCENSE_PAPER -> incensePaper(engine, materialLoader)
                    MemorialItemType.FRUIT_OFFERING -> fruitOffering(engine, materialLoader)
                    MemorialItemType.FOOD_OFFERING -> foodOffering(engine, materialLoader)
                    MemorialItemType.CANDLE -> candle(engine, materialLoader)
                    MemorialItemType.FLOWER -> flower(engine, materialLoader)
                }
                node.position = Position(offset.x, offset.y, offset.z)
                node.rotation = Rotation(y = offset.yaw)
                anchorNode.addChildNode(node)
            }.onFailure { throwable ->
                Log.e("MemorialItems", "Failed to render $type", throwable)
            }
        }
    }

    /** Builds a single incense stick, ~25cm, origin at base centre. */
    fun incenseStick(engine: Engine, materialLoader: MaterialLoader): Node {
        val tan = materialLoader.createColorInstance(Color(0.55f, 0.35f, 0.18f))
        val brown = materialLoader.createColorInstance(Color(0.38f, 0.20f, 0.10f))

        val mainStickRadius = 0.0015f
        val mainStickHeight = 0.22f // top part
        val baseStickHeight = 0.03f // bottom part

        return Node(engine).apply {
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = mainStickRadius,
                    height = mainStickHeight,
                    center = Position(0f, mainStickHeight / 2f + baseStickHeight, 0f),
                    materialInstance = tan
                )
            )
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = mainStickRadius,
                    height = baseStickHeight,
                    center = Position(0f, baseStickHeight / 2f, 0f),
                    materialInstance = brown
                )
            )
        }
    }

    fun incensePot(engine: Engine, materialLoader: MaterialLoader): Node {
        val potMaterial = materialLoader.createColorInstance(Color(0.8f, 0.7f, 0.3f)) // Golden/brass color
        val radius = 0.05f
        val height = 0.07f

        return CylinderNode(
            engine = engine,
            radius = radius,
            height = height,
            center = Position(0f, height / 2f, 0f),
            materialInstance = potMaterial
        )
    }

    fun plaque(engine: Engine, materialLoader: MaterialLoader, photoTexture: Texture?): Node {
        val plaqueMaterial = materialLoader.createColorInstance(Color(0.1f, 0.1f, 0.1f)) // Dark granite
        val width = 0.22f
        val height = 0.28f
        val depth = 0.015f

        val plaqueNode = CubeNode(
            engine = engine,
            size = Size(width, height, depth),
            center = Position(0f, height / 2f, 0f),
            materialInstance = plaqueMaterial
        )

        if (photoTexture != null) {
            val photoMaterial = materialLoader.createTextureInstance(photoTexture)
            val photoWidth = width * 0.8f
            val photoHeight = height * 0.8f
            val photoDepth = 0.001f

            plaqueNode.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(photoWidth, photoHeight, photoDepth),
                    center = Position(0f, height / 2f, depth / 2f + photoDepth / 2f),
                    materialInstance = photoMaterial
                )
            )
        }
        return plaqueNode
    }

    fun incenseBox(engine: Engine, materialLoader: MaterialLoader): Node {
        val material = materialLoader.createColorInstance(Color(0.7f, 0.1f, 0.1f))
        val width = 0.08f
        val height = 0.04f
        val depth = 0.25f

        return CubeNode(
            engine = engine,
            size = Size(width, height, depth),
            center = Position(0f, height / 2f, 0f),
            materialInstance = material
        )
    }

    fun incensePaper(engine: Engine, materialLoader: MaterialLoader): Node {
        val material = materialLoader.createColorInstance(Color(0.9f, 0.8f, 0.2f))
        val width = 0.15f
        val height = 0.002f
        val depth = 0.15f

        return CubeNode(
            engine = engine,
            size = Size(width, height, depth),
            center = Position(0f, height / 2f, 0f),
            materialInstance = material
        )
    }

    fun fruitOffering(engine: Engine, materialLoader: MaterialLoader): Node {
        val material = materialLoader.createColorInstance(Color(1.0f, 0.5f, 0.0f))
        val radius = 0.04f

        return Node(engine).apply {
            // Central, right and left fruit
            listOf(
                Position(0f, radius, 0f),
                Position(radius * 1.1f, radius, radius * 0.5f),
                Position(-radius * 1.1f, radius, radius * 0.5f)
            ).forEach { center ->
                addChildNode(SphereNode(engine = engine, radius = radius, center = center, materialInstance = material))
            }
        }
    }

    fun foodOffering(engine: Engine, materialLoader: MaterialLoader): Node {
        val bowlMat = materialLoader.createColorInstance(Color(0.9f, 0.9f, 0.9f))
        val foodMat = materialLoader.createColorInstance(Color(0.6f, 0.3f, 0.1f))

        val bowlRadius = 0.06f
        val bowlHeight = 0.04f
        val foodRadius = 0.05f

        return Node(engine).apply {
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = bowlRadius,
                    height = bowlHeight,
                    center = Position(0f, bowlHeight / 2f, 0f),
                    materialInstance = bowlMat
                )
            )
            addChildNode(
                SphereNode(
                    engine = engine,
                    radius = foodRadius,
                    center = Position(0f, bowlHeight + foodRadius / 2f, 0f),
                    materialInstance = foodMat
                )
            )
        }
    }

    fun candle(engine: Engine, materialLoader: MaterialLoader): Node {
        val candleMat = materialLoader.createColorInstance(Color(0.8f, 0.1f, 0.1f))
        val flameMat = materialLoader.createColorInstance(Color(1.0f, 0.9f, 0.0f))

        val radius = 0.015f
        val height = 0.15f
        val flameRadius = 0.005f

        return Node(engine).apply {
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = radius,
                    height = height,
                    center = Position(0f, height / 2f, 0f),
                    materialInstance = candleMat
                )
            )
            addChildNode(
                SphereNode(
                    engine = engine,
                    radius = flameRadius,
                    center = Position(0f, height + flameRadius, 0f),
                    materialInstance = flameMat
                )
            )
        }
    }

    fun flower(engine: Engine, materialLoader: MaterialLoader): Node {
        val stemMat = materialLoader.createColorInstance(Color(0.1f, 0.6f, 0.1f))
        val petalMat = materialLoader.createColorInstance(Color(1.0f, 0.4f, 0.7f))

        val stemRadius = 0.003f
        val stemHeight = 0.2f
        val headRadius = 0.03f

        return Node(engine).apply {
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = stemRadius,
                    height = stemHeight,
                    center = Position(0f, stemHeight / 2f, 0f),
                    materialInstance = stemMat
                )
            )
            addChildNode(
                SphereNode(
                    engine = engine,
                    radius = headRadius,
                    center = Position(0f, stemHeight + headRadius, 0f),
                    materialInstance = petalMat
                )
            )
        }
    }
}
