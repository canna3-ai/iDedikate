package com.memoria.idedikate.ar

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.scale
import dev.romainguy.kotlin.math.translation

/**
 * A white lily on a gently curving stem, as one mesh. Origin at the base of the stem, which rises
 * along +y; the bloom nods forward (+z) and opens into six recurved tepals around a pale throat,
 * with six orange-tipped stamens and a pistil.
 */
object LilyMesh {

    /** Mesh parts, each drawn with its own material (see [MemorialItems]). */
    const val PART_PETAL = 0
    const val PART_THROAT = 1
    const val PART_GREEN = 2
    const val PART_STAMEN = 3
    const val PART_POLLEN = 4
    const val PART_COUNT = 5

    const val STEM_HEIGHT = 0.24f
    private const val STEM_BEND = 0.035f // forward lean of the stem's top
    private const val STEM_RADIUS = 0.0024f
    /** How far the bloom turns from the stem toward the front. */
    private const val BLOOM_TILT_DEG = 55f

    private val X = Float3(1f, 0f, 0f)
    private val Y = Float3(0f, 1f, 0f)

    /** Built once; every lily in the scene gets its own GPU buffers from this data. */
    val mesh: MeshBuilder by lazy { build() }

    /** Point on the stem at [s] (0 base, 1 top): straight at first, then curving forward. */
    private fun stemPoint(s: Float) = Float3(0f, STEM_HEIGHT * s, STEM_BEND * s * s * s)

    private fun build(): MeshBuilder = MeshBuilder(PART_COUNT).apply {
        val identity = Mat4.identity()
        tube(PART_GREEN, identity, List(9) { stemPoint(it / 8f) }, STEM_RADIUS)

        // Two slender leaves partway up, on opposite sides
        leaf(0.35f, yaw = 70f)
        leaf(0.55f, yaw = -110f)

        val bloom = translation(stemPoint(1f)) * rotation(X, BLOOM_TILT_DEG)
        // Green cup the tepals grow from
        ellipsoid(PART_GREEN, bloom * translation(Float3(0f, -0.008f, 0f)) * scale(Float3(0.009f, 0.014f, 0.009f)))

        repeat(6) { i ->
            // Outer and inner whorls alternate; the inner tepals are a little broader
            val width = if (i % 2 == 0) 0.018f else 0.022f
            val around = bloom * rotation(Y, i * 60f)
            // Trumpet-shaped throat, flaring out, then curling back at the tip
            val throat = around * rotation(X, 22f)
            ellipsoid(PART_THROAT, throat * scale(Float3(width * 0.8f, 0.05f, 0.004f)))
            val flare = throat * translation(Float3(0f, 0.04f, 0f)) * rotation(X, 38f)
            ellipsoid(PART_PETAL, flare * scale(Float3(width, 0.045f, 0.0032f)))
            val tip = flare * translation(Float3(0f, 0.037f, 0f)) * rotation(X, 42f)
            ellipsoid(PART_PETAL, tip * scale(Float3(width * 0.6f, 0.022f, 0.0026f)), stacks = 6, slices = 8)

            // Stamens sit between the tepals, arching outward, each with a pollen-laden anther
            val stamen = bloom * rotation(Y, i * 60f + 30f)
            val filament = List(5) { k ->
                val s = k / 4f
                Float3(0f, 0.062f * s, 0.016f * s * s)
            }
            tube(PART_STAMEN, stamen, filament, 0.0007f, sides = 5)
            val anther = stamen * translation(filament.last()) * rotation(X, 90f) * translation(Float3(0f, -0.005f, 0f))
            ellipsoid(PART_POLLEN, anther * scale(Float3(0.0028f, 0.01f, 0.0022f)), stacks = 5, slices = 6)
        }

        // Pistil, a little longer than the stamens, with a three-lobed tip
        val pistil = List(5) { k -> Float3(0f, 0.072f * k / 4f, 0.004f * k / 4f) }
        tube(PART_STAMEN, bloom, pistil, 0.0011f, sides = 6)
        ellipsoid(PART_GREEN, bloom * translation(pistil.last() - Float3(0f, 0.002f, 0f)) * scale(Float3(0.005f, 0.004f, 0.005f)), stacks = 5, slices = 6)
    }

    private fun MeshBuilder.leaf(s: Float, yaw: Float) {
        val attach = translation(stemPoint(s)) * rotation(Y, yaw) * rotation(X, 32f)
        ellipsoid(PART_GREEN, attach * scale(Float3(0.011f, 0.085f, 0.0018f)), stacks = 8, slices = 8)
        // Leaves droop a little toward their tips
        val tip = attach * translation(Float3(0f, 0.07f, 0f)) * rotation(X, 25f)
        ellipsoid(PART_GREEN, tip * scale(Float3(0.006f, 0.03f, 0.0015f)), stacks = 5, slices = 6)
    }
}
