package com.memoria.idedikate.ar

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Texture
import com.memoria.idedikate.model.MemorialOfferings
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class MemorialItemType {
    PLAQUE,
    INCENSE_STICK,
    /** Smoke rising from the tip of an incense stick. */
    INCENSE_SMOKE,
    INCENSE_POT,
    INCENSE_BOX,
    INCENSE_PAPER,
    CANDLE,
    /** Warm light cast by a group of candles. */
    CANDLE_LIGHT,
    FLOWER,
    /** Ribbon tying a bunch of flowers together. */
    BOUQUET_TIE
}

data class Offset(
    val x: Float,
    val y: Float,
    val z: Float,
    /** Rotation around the vertical axis, in degrees; 0 faces the front (+z). */
    val yaw: Float,
    /** Lean away from vertical toward the [yaw] direction, in degrees. */
    val tilt: Float = 0f,
    val scale: Float = 1f
)

/**
 * Procedural memorial offerings built from SceneView primitives and generated meshes.
 * Every builder returns a node whose origin is at the base center of the item.
 * Material instances are owned by [MaterialLoader] and released when it is destroyed.
 */
object MemorialItems {

    private const val PLAQUE_SPACING = 0.26f
    private const val PLAQUE_WIDTH = 0.22f
    private const val CANDLE_SPACING = 0.07f
    private const val POT_Z = 0.25f
    private const val POT_HEIGHT = 0.07f
    private const val POT_INNER_RADIUS = 0.035f
    private const val POT_RADIUS = 0.05f
    /** Room left between a candle standing in the pot and the incense sticks around it. */
    private const val POT_CANDLE_CLEARANCE = 0.004f
    private const val STICK_BASE_Y = 0.05f
    const val STICK_LENGTH = 0.25f
    /** Sticks at the rim of the pot lean out this far, like a real handful of joss sticks. */
    private const val STICK_SPLAY_DEG = 9f
    /** Past this many sticks, more smoke would look the same, so only some sticks get it. */
    const val MAX_SMOKE_STREAMS = 8

    /** Flowers per bunch: one in the middle and the rest fanned out around it. */
    const val BOUQUET_SIZE = 7
    /** Halfway between the front of the plaques and the back of the incense pot. */
    private const val BOUQUET_Z = 0.1f
    /** About the width of a bunch's fanned-out blooms, so neighbouring bunches just touch. */
    private const val BOUQUET_SPACING = 0.24f
    /** How far apart the stems start at the base of a bunch. */
    private const val BOUQUET_STEM_SPREAD = 0.006f
    private const val BOUQUET_FAN_DEG = 20f
    /** A lone lily stands slanted rather than bolt upright. */
    private const val SINGLE_FLOWER_TILT_DEG = 12f

    private const val CANDLE_HEIGHT = 0.12f
    private const val CANDLE_RADIUS = 0.015f
    /** Space between the candles at each side and the nearest other offering. */
    private const val SIDE_CANDLE_GAP = 0.04f
    private const val SIDE_CANDLE_Z = 0.15f
    private const val WICK_HEIGHT = 0.012f
    private const val CANDLE_LIGHT_Y = 0.22f

    /**
     * Arranges a memorial's offerings around its anchor: plaques in a row at the back, incense
     * sticks in a pot at the front, and bunches of lilies in a row between them, centered. Candles
     * flank everything else, split evenly left and right;
     * with an odd number, the odd one stands in the incense pot (or front and center without one).
     */
    fun layoutFor(offerings: MemorialOfferings): List<Pair<MemorialItemType, Offset>> = buildList {
        val plaqueCount = offerings.plaques
        repeat(plaqueCount) { i ->
            add(MemorialItemType.PLAQUE to Offset((i - (plaqueCount - 1) / 2f) * PLAQUE_SPACING, 0f, 0f, 0f))
        }

        val centerCandle = offerings.candles % 2 == 1
        addIncense(offerings.incenseSticks, candleInPot = centerCandle)
        addBouquets(offerings.flowers)
        addCandles(offerings.candles, inPot = centerCandle && offerings.incenseSticks > 0)
    }

    private fun MutableList<Pair<MemorialItemType, Offset>>.addIncense(sticks: Int, candleInPot: Boolean) {
        if (sticks <= 0) return
        add(MemorialItemType.INCENSE_POT to Offset(0f, 0f, POT_Z, 0f))
        val streams = min(sticks, MAX_SMOKE_STREAMS)
        val smokingSticks = List(streams) { it * sticks / streams }.toSet()
        // A candle in the pot takes the middle, so the sticks keep to the ring around it
        val innerRadius = if (candleInPot) CANDLE_RADIUS + POT_CANDLE_CLEARANCE else 0f
        // Sunflower spiral spreads any number of sticks evenly across the pot opening
        repeat(sticks) { i ->
            val spread = sqrt((i + 0.5f) / sticks)
            val radius = sqrt(innerRadius.pow(2) + (POT_INNER_RADIUS.pow(2) - innerRadius.pow(2)) * spread.pow(2))
            val angle = i * GOLDEN_ANGLE
            val x = radius * cos(angle)
            val z = POT_Z + radius * sin(angle)
            // Lean outward, away from the middle of the pot
            val yaw = degrees(PI.toFloat() / 2f - angle)
            val tilt = STICK_SPLAY_DEG * spread
            add(MemorialItemType.INCENSE_STICK to Offset(x, STICK_BASE_Y, z, yaw, tilt))
            if (i in smokingSticks) {
                val lean = STICK_LENGTH * sin(radians(tilt))
                val tip = Offset(
                    x + lean * sin(radians(yaw)),
                    STICK_BASE_Y + STICK_LENGTH * cos(radians(tilt)),
                    z + lean * cos(radians(yaw)),
                    0f
                )
                add(MemorialItemType.INCENSE_SMOKE to tip)
            }
        }
    }

    /**
     * Groups flowers into bunches of up to [BOUQUET_SIZE]: stems gathered at the base and tied
     * with a ribbon, blooms fanned out around a central one. The bunches stand in a centered row
     * between the plaques and the incense pot, the only gap deep enough for one row.
     */
    private fun MutableList<Pair<MemorialItemType, Offset>>.addBouquets(flowers: Int) {
        if (flowers <= 0) return
        val bouquets = ceil(flowers / BOUQUET_SIZE.toFloat()).toInt()
        repeat(bouquets) { b ->
            val cx = (b - (bouquets - 1) / 2f) * BOUQUET_SPACING
            val cz = BOUQUET_Z
            val count = min(BOUQUET_SIZE, flowers - b * BOUQUET_SIZE)
            if (count == 1) {
                add(MemorialItemType.FLOWER to Offset(cx, 0f, cz, 0f, SINGLE_FLOWER_TILT_DEG))
                return@repeat
            }
            add(MemorialItemType.BOUQUET_TIE to Offset(cx, 0f, cz, 0f))
            // The middle bloom stands tallest, leaning slightly toward the front
            add(MemorialItemType.FLOWER to Offset(cx, 0f, cz, 0f, tilt = 6f, scale = 1.05f))
            val around = count - 1
            repeat(around) { k ->
                // Offset by half a step so no bloom points straight at the back
                val yaw = (k + 0.5f) * 360f / around
                add(
                    MemorialItemType.FLOWER to Offset(
                        cx + BOUQUET_STEM_SPREAD * sin(radians(yaw)),
                        0f,
                        cz + BOUQUET_STEM_SPREAD * cos(radians(yaw)),
                        yaw,
                        tilt = BOUQUET_FAN_DEG + (k % 2) * 6f,
                        // Slightly different heights read as a natural bunch rather than a row
                        scale = if (k % 2 == 0) 0.95f else 0.88f
                    )
                )
            }
        }
    }

    /**
     * Candles go last so they can flank everything already placed. An odd one out stands in the
     * incense pot when [inPot], otherwise front and center; the rest split evenly left and right.
     */
    private fun MutableList<Pair<MemorialItemType, Offset>>.addCandles(count: Int, inPot: Boolean) {
        if (count <= 0) return
        val candles = mutableListOf<Offset>()
        if (count % 2 == 1) {
            // Standing on the ash in the pot, or on the ground where the pot would be
            candles += Offset(0f, if (inPot) POT_HEIGHT + 0.002f else 0f, POT_Z, 0f)
            add(MemorialItemType.CANDLE to candles.single())
        }
        val perSide = count / 2
        if (perSide > 0) {
            // Measured after the center candle, so the sides clear it too
            val left = minOf(0f, minOfOrNull { it.second.x - halfWidth(it.first) } ?: 0f)
            val right = maxOf(0f, maxOfOrNull { it.second.x + halfWidth(it.first) } ?: 0f)
            val columns = ceil(sqrt(perSide.toFloat())).toInt()
            repeat(perSide) { i ->
                // Each side's grid starts next to the other offerings and grows outward
                val dx = SIDE_CANDLE_GAP + CANDLE_RADIUS + (i % columns) * CANDLE_SPACING
                val z = SIDE_CANDLE_Z + (i / columns) * CANDLE_SPACING
                candles += Offset(left - dx, 0f, z, 0f)
                candles += Offset(right + dx, 0f, z, 0f)
            }
            candles.drop(count % 2).forEach { add(MemorialItemType.CANDLE to it) }
        }
        // One light over all the candles; a light per candle would be costly and look the same
        add(
            MemorialItemType.CANDLE_LIGHT to Offset(
                candles.map { it.x }.average().toFloat(),
                CANDLE_LIGHT_Y,
                candles.map { it.z }.average().toFloat(),
                0f
            )
        )
    }

    /** How far an item reaches sideways from its position, so candles beside it don't overlap it. */
    private fun halfWidth(type: MemorialItemType): Float = when (type) {
        MemorialItemType.PLAQUE -> PLAQUE_WIDTH / 2f
        MemorialItemType.INCENSE_POT -> POT_RADIUS + 0.004f
        // Blooms fan out from the stems
        MemorialItemType.FLOWER -> 0.12f
        MemorialItemType.BOUQUET_TIE -> 0.03f
        MemorialItemType.CANDLE -> CANDLE_RADIUS
        else -> 0f
    }

    private const val GOLDEN_ANGLE = 2.3999631f // radians

    private fun radians(degrees: Float) = degrees * PI.toFloat() / 180f
    private fun degrees(radians: Float) = radians * 180f / PI.toFloat()

    fun renderOfferings(
        engine: Engine,
        materialLoader: MaterialLoader,
        anchorNode: Node,
        items: List<Pair<MemorialItemType, Offset>>,
        photoTexture: Texture? = null
    ) {
        val materials = Materials(materialLoader)
        val candles = items.count { it.first == MemorialItemType.CANDLE }
        items.forEachIndexed { index, (type, offset) ->
            runCatching {
                val node = when (type) {
                    MemorialItemType.PLAQUE -> plaque(engine, materials, photoTexture)
                    MemorialItemType.INCENSE_STICK -> incenseStick(engine, materials)
                    MemorialItemType.INCENSE_SMOKE -> incenseSmoke(engine, materialLoader, seed = index)
                    MemorialItemType.INCENSE_POT -> incensePot(engine, materials)
                    MemorialItemType.INCENSE_BOX -> incenseBox(engine, materials)
                    MemorialItemType.INCENSE_PAPER -> incensePaper(engine, materials)
                    MemorialItemType.CANDLE -> candle(engine, materials, seed = index)
                    MemorialItemType.CANDLE_LIGHT -> candleLight(engine, candles)
                    MemorialItemType.FLOWER -> lily(engine, materials)
                    MemorialItemType.BOUQUET_TIE -> bouquetTie(engine, materials)
                }
                // Yaw on the outer node and tilt on the inner one, so the lean follows the yaw
                val placed = Node(engine).apply {
                    position = Position(offset.x, offset.y, offset.z)
                    rotation = Rotation(y = offset.yaw)
                }
                node.rotation = Rotation(x = offset.tilt)
                if (offset.scale != 1f) node.scale = Scale(offset.scale)
                placed.addChildNode(node)
                anchorNode.addChildNode(placed)
            }.onFailure { throwable ->
                Log.e("MemorialItems", "Failed to render $type", throwable)
            }
        }
    }

    /** Shares one material instance per look across every item of a memorial. */
    private class Materials(private val loader: MaterialLoader) {
        private val cache = HashMap<Any, MaterialInstance>()

        fun color(color: Color, roughness: Float = 0.6f, metallic: Float = 0f): MaterialInstance =
            cache.getOrPut(Triple(color, roughness, metallic)) {
                loader.createColorInstance(color = color, metallic = metallic, roughness = roughness)
            }

        /** Lights itself up regardless of the scene lighting, for flames and embers. */
        fun glow(color: Color, emissive: Color): MaterialInstance =
            cache.getOrPut(color to emissive) {
                loader.createColorInstance(color = color).apply { setEmissive(emissive) }
            }

        fun texture(texture: Texture): MaterialInstance = loader.createTextureInstance(texture)
    }

    internal fun MaterialInstance.setEmissive(color: Color) {
        // w = 0: not dimmed by the camera exposure, so it glows even in bright daylight AR
        if (material.hasParameter("emissive")) setParameter("emissive", color.red, color.green, color.blue, 0f)
    }

    internal fun geometryNode(engine: Engine, mesh: MeshBuilder, vertices: List<Geometry.Vertex>, materials: List<MaterialInstance>): GeometryNode {
        val geometry = Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES)
            .vertices(vertices)
            .primitivesIndices(mesh.parts)
            .build(engine)
        return GeometryNode(engine, geometry, materials, geometry.primitivesOffsets)
    }

    internal fun MeshBuilder.toVertices() = positions.indices.map { Geometry.Vertex(position = positions[it], normal = normals[it]) }

    // ---- Incense ----

    /** A joss stick, [STICK_LENGTH] long: red bamboo handle, incense coating, glowing tip. */
    private fun incenseStick(engine: Engine, materials: Materials): Node {
        val handleHeight = 0.05f
        val emberHeight = 0.004f
        val coatedHeight = STICK_LENGTH - handleHeight - emberHeight
        return Node(engine).apply {
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = 0.0014f,
                    height = handleHeight,
                    center = Position(0f, handleHeight / 2f, 0f),
                    materialInstance = materials.color(Color(0.72f, 0.12f, 0.1f), roughness = 0.5f)
                )
            )
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = 0.0022f,
                    height = coatedHeight,
                    center = Position(0f, handleHeight + coatedHeight / 2f, 0f),
                    materialInstance = materials.color(Color(0.42f, 0.26f, 0.15f), roughness = 0.95f)
                )
            )
            addChildNode(
                SphereNode(
                    engine = engine,
                    radius = 0.0024f,
                    center = Position(0f, STICK_LENGTH - emberHeight / 2f, 0f),
                    stacks = 6,
                    slices = 8,
                    materialInstance = materials.glow(Color(0.85f, 0.2f, 0.05f), emissive = Color(1f, 0.32f, 0.04f))
                )
            )
        }
    }

    private const val SMOKE_PUFFS = 10
    private const val SMOKE_HEIGHT = 0.24f
    private const val SMOKE_RISE_SECONDS = 4.0
    private const val SMOKE_ALPHA = 0.32f
    private val SMOKE_COLOR = Color(0.86f, 0.86f, 0.88f)

    /**
     * A thin stream of smoke: translucent puffs that leave the tip, swell and curl as they rise,
     * and fade away. Each puff gets its own material so it can fade independently.
     */
    private fun incenseSmoke(engine: Engine, materialLoader: MaterialLoader, seed: Int): Node {
        val puffs = List(SMOKE_PUFFS) {
            val material = materialLoader.createColorInstance(color = SMOKE_COLOR.copy(alpha = 0.01f), roughness = 1f)
            SphereNode(
                engine = engine,
                radius = 0.5f,
                center = Position(0f, 0f, 0f),
                stacks = 8,
                slices = 10,
                materialInstance = material
            ).apply { isShadowCaster = false } to material
        }
        // Each stream curls its own way
        val phase = seed * 0.618
        val swirl = seed * 1.7f
        return Node(engine).apply {
            puffs.forEach { (puff, _) -> addChildNode(puff) }
            onFrame = { frameTimeNanos ->
                val seconds = frameTimeNanos / 1_000_000_000.0
                val sway = (seconds % 1000.0).toFloat()
                puffs.forEachIndexed { i, (puff, material) ->
                    val cycle = seconds / SMOKE_RISE_SECONDS + i.toDouble() / SMOKE_PUFFS + phase
                    val p = (cycle - floor(cycle)).toFloat() // 0 at the tip, 1 fully risen
                    val curl = 0.014f * p
                    puff.position = Position(
                        sin(p * 7f + swirl + sway * 0.7f) * curl + 0.025f * p * p, // slowly drifts with a breeze
                        SMOKE_HEIGHT * p,
                        cos(p * 5f + swirl + sway * 0.5f) * curl
                    )
                    val size = 0.004f + 0.034f * p
                    puff.scale = Scale(size, size * 1.8f, size)
                    // Fade in just above the tip, then thin out as it spreads
                    val alpha = SMOKE_ALPHA * min(1f, p / 0.12f) * (1f - p).pow(1.3f)
                    // SceneView's color material names this parameter "color"; Filament aborts on unknown names
                    material.setColor(SMOKE_COLOR.copy(alpha = alpha))
                }
            }
        }
    }

    /** Brass pot filled with ash, which the sticks stand in. */
    private fun incensePot(engine: Engine, materials: Materials): Node {
        val radius = POT_RADIUS
        return Node(engine).apply {
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = radius,
                    height = POT_HEIGHT,
                    center = Position(0f, POT_HEIGHT / 2f, 0f),
                    materialInstance = materials.color(Color(0.8f, 0.65f, 0.28f), roughness = 0.35f, metallic = 0.8f)
                )
            )
            // Rim
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = radius + 0.004f,
                    height = 0.008f,
                    center = Position(0f, POT_HEIGHT - 0.004f, 0f),
                    materialInstance = materials.color(Color(0.72f, 0.56f, 0.22f), roughness = 0.35f, metallic = 0.8f)
                )
            )
            // Ash
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = radius - 0.004f,
                    height = 0.002f,
                    center = Position(0f, POT_HEIGHT + 0.001f, 0f),
                    materialInstance = materials.color(Color(0.62f, 0.6f, 0.56f), roughness = 1f)
                )
            )
        }
    }

    private fun incenseBox(engine: Engine, materials: Materials): Node {
        val size = Size(0.08f, 0.04f, 0.25f)
        return CubeNode(
            engine = engine,
            size = size,
            center = Position(0f, size.y / 2f, 0f),
            materialInstance = materials.color(Color(0.7f, 0.1f, 0.1f))
        )
    }

    private fun incensePaper(engine: Engine, materials: Materials): Node {
        val size = Size(0.15f, 0.002f, 0.15f)
        return CubeNode(
            engine = engine,
            size = size,
            center = Position(0f, size.y / 2f, 0f),
            materialInstance = materials.color(Color(0.9f, 0.8f, 0.2f))
        )
    }

    // ---- Plaque ----

    private fun plaque(engine: Engine, materials: Materials, photoTexture: Texture?): Node {
        val width = PLAQUE_WIDTH
        val height = 0.28f
        val depth = 0.015f

        val plaqueNode = CubeNode(
            engine = engine,
            size = Size(width, height, depth),
            center = Position(0f, height / 2f, 0f),
            materialInstance = materials.color(Color(0.1f, 0.1f, 0.1f), roughness = 0.3f) // Dark granite
        )

        if (photoTexture != null) {
            val photoDepth = 0.001f
            plaqueNode.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(width * 0.8f, height * 0.8f, photoDepth),
                    center = Position(0f, height / 2f, depth / 2f + photoDepth / 2f),
                    materialInstance = materials.texture(photoTexture)
                )
            )
        }
        return plaqueNode
    }

    // ---- Candles ----

    /** Teardrop flame of height and width 1, resting on the origin; scaled per use. */
    private val flameMesh: MeshBuilder by lazy {
        MeshBuilder(1).apply {
            val steps = 12
            val path = List(steps + 1) { Float3(0f, it / steps.toFloat(), 0f) }
            // Round at the bottom, widest a third of the way up, drawn to a point at the top
            val radii = List(steps + 1) { 0.5f * sin(PI.toFloat() * (it / steps.toFloat()).pow(0.55f)) }
            tube(0, Mat4.identity(), path, 0f, sides = 12, radii = radii)
        }
    }
    private val flameVertices by lazy { flameMesh.toVertices() }

    /** An ivory pillar candle with a flickering flame and a soft glow around it. */
    private fun candle(engine: Engine, materials: Materials, seed: Int): Node {
        val radius = CANDLE_RADIUS
        val flame = Node(engine).apply {
            position = Position(0f, CANDLE_HEIGHT + WICK_HEIGHT * 0.4f, 0f)
            // Outer flame: translucent orange
            addChildNode(
                geometryNode(
                    engine, flameMesh, flameVertices,
                    listOf(materials.glow(Color(1f, 0.55f, 0.12f, 0.55f), emissive = Color(1f, 0.45f, 0.08f)))
                ).apply { scale = Scale(0.012f, 0.034f, 0.012f) }
            )
            // Bright core
            addChildNode(
                geometryNode(
                    engine, flameMesh, flameVertices,
                    listOf(materials.glow(Color(1f, 0.96f, 0.8f), emissive = Color(1f, 0.9f, 0.62f)))
                ).apply {
                    position = Position(0f, 0.002f, 0f)
                    scale = Scale(0.0055f, 0.018f, 0.0055f)
                }
            )
            // Halo of light around the flame
            addChildNode(
                SphereNode(
                    engine = engine,
                    radius = 0.026f,
                    center = Position(0f, 0.016f, 0f),
                    stacks = 10,
                    slices = 12,
                    materialInstance = materials.glow(Color(1f, 0.8f, 0.45f, 0.12f), emissive = Color(0.6f, 0.38f, 0.1f))
                ).apply { isShadowCaster = false }
            )
        }
        val offset = seed * 2.3f
        flame.onFrame = { frameTimeNanos ->
            val t = ((frameTimeNanos / 1_000_000_000.0) % 1000.0).toFloat()
            val stretch = 1f + 0.1f * sin(t * 13f + offset) + 0.05f * sin(t * 29f + offset * 1.7f)
            flame.scale = Scale(1f / sqrt(stretch), stretch, 1f / sqrt(stretch))
            flame.rotation = Rotation(x = 4f * sin(t * 7f + offset), z = 4f * sin(t * 5.3f + offset * 2f))
        }

        return Node(engine).apply {
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = radius,
                    height = CANDLE_HEIGHT,
                    center = Position(0f, CANDLE_HEIGHT / 2f, 0f),
                    materialInstance = materials.color(Color(0.96f, 0.93f, 0.84f), roughness = 0.45f) // Ivory wax
                )
            )
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = 0.0008f,
                    height = WICK_HEIGHT,
                    center = Position(0f, CANDLE_HEIGHT + WICK_HEIGHT / 2f, 0f),
                    materialInstance = materials.color(Color(0.12f, 0.1f, 0.08f), roughness = 1f)
                )
            )
            addChildNode(flame)
        }
    }

    private const val CANDLE_LUMENS = 200f
    /** More candles brighten the light, up to this many. */
    private const val MAX_LIT_CANDLES = 6

    /** Warm, gently flickering point light for a group of [candles] candles. */
    private fun candleLight(engine: Engine, candles: Int): Node {
        val lumens = CANDLE_LUMENS * candles.coerceIn(1, MAX_LIT_CANDLES)
        val light = LightNode(engine = engine, type = LightManager.Type.POINT) {
            color(1f, 0.7f, 0.38f)
            intensity(lumens)
            falloff(0.9f)
            castShadows(false)
        }
        light.onFrame = { frameTimeNanos ->
            val t = ((frameTimeNanos / 1_000_000_000.0) % 1000.0).toFloat()
            light.intensity = lumens * (0.9f + 0.07f * sin(t * 11f) + 0.03f * sin(t * 23f))
        }
        return light
    }

    // ---- Flowers ----

    private val lilyVertices by lazy { LilyMesh.mesh.toVertices() }

    /** A white lily; see [LilyMesh]. */
    private fun lily(engine: Engine, materials: Materials): Node {
        val parts = List(LilyMesh.PART_COUNT) { part ->
            when (part) {
                LilyMesh.PART_PETAL -> materials.color(Color(0.98f, 0.98f, 0.95f), roughness = 0.55f)
                LilyMesh.PART_THROAT -> materials.color(Color(0.9f, 0.95f, 0.76f), roughness = 0.55f)
                LilyMesh.PART_GREEN -> materials.color(Color(0.2f, 0.47f, 0.16f), roughness = 0.6f)
                LilyMesh.PART_STAMEN -> materials.color(Color(0.8f, 0.86f, 0.55f), roughness = 0.6f)
                else -> materials.color(Color(0.78f, 0.32f, 0.07f), roughness = 0.95f) // pollen
            }
        }
        return geometryNode(engine, LilyMesh.mesh, lilyVertices, parts)
    }

    /** Satin ribbon and bow gathering the stems of a bunch of flowers. */
    private fun bouquetTie(engine: Engine, materials: Materials): Node {
        val satin = materials.color(Color(0.95f, 0.86f, 0.9f), roughness = 0.3f)
        val bandY = 0.045f
        return Node(engine).apply {
            addChildNode(
                CylinderNode(
                    engine = engine,
                    radius = 0.024f,
                    height = 0.02f,
                    center = Position(0f, bandY, 0f),
                    materialInstance = satin
                )
            )
            // Two loops of the bow at the front
            listOf(-1f, 1f).forEach { side ->
                addChildNode(
                    SphereNode(
                        engine = engine,
                        radius = 0.5f,
                        center = Position(0f, 0f, 0f),
                        stacks = 8,
                        slices = 10,
                        materialInstance = satin
                    ).apply {
                        position = Position(side * 0.012f, bandY + 0.003f, 0.026f)
                        rotation = Rotation(z = side * 25f)
                        scale = Scale(0.024f, 0.013f, 0.007f)
                    }
                )
            }
        }
    }
}
