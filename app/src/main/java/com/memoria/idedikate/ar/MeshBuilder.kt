package com.memoria.idedikate.ar

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.inverse
import dev.romainguy.kotlin.math.normalize
import dev.romainguy.kotlin.math.transpose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Merges transformed shapes into a single triangle mesh, with triangles grouped into [partCount]
 * parts that each get their own material. A detailed offering (e.g. a lily) then renders as one
 * node with a handful of draw calls instead of dozens of primitive nodes.
 *
 * Pure data, so shapes can be built (and tested) without a Filament engine.
 */
class MeshBuilder(partCount: Int) {
    val positions = mutableListOf<Float3>()
    val normals = mutableListOf<Float3>()
    /** Triangle indices (counter-clockwise when seen from outside) for each part. */
    val parts: List<MutableList<Int>> = List(partCount) { mutableListOf() }

    /**
     * An ellipsoid: a sphere of diameter 1 resting on the origin (y from 0 to 1), transformed by
     * [transform]. Scaling it (width, length, thickness) and rotating it gives petals and leaves
     * that grow out of the point they're attached at.
     */
    fun ellipsoid(part: Int, transform: Mat4, stacks: Int = 8, slices: Int = 10) {
        val normalMatrix = normalMatrix(transform)
        val base = positions.size
        for (i in 0..stacks) {
            val phi = PI * i / stacks // 0 at the bottom pole
            for (j in 0..slices) {
                val theta = 2 * PI * j / slices
                val n = Float3(
                    (sin(phi) * cos(theta)).toFloat(),
                    (-cos(phi)).toFloat(),
                    (sin(phi) * sin(theta)).toFloat()
                )
                add(transform, normalMatrix, n * 0.5f + Float3(0f, 0.5f, 0f), n)
            }
        }
        grid(part, base, rings = stacks, segments = slices, flip = false)
    }

    /**
     * A tube of [radius] swept along [path] (for stems and stamens), or of [radii] (one per path
     * point) for tapered shapes like a flame. The path should never run parallel to the x axis,
     * which is used to orient the tube's cross-sections.
     */
    fun tube(part: Int, transform: Mat4, path: List<Float3>, radius: Float, sides: Int = 8, radii: List<Float>? = null) {
        require(path.size >= 2)
        require(radii == null || radii.size == path.size)
        val normalMatrix = normalMatrix(transform)
        val base = positions.size
        path.forEachIndexed { k, point ->
            val tangent = normalize(path[minOf(k + 1, path.lastIndex)] - path[maxOf(k - 1, 0)])
            val side = normalize(cross(tangent, Float3(1f, 0f, 0f)))
            val binormal = cross(tangent, side)
            for (j in 0..sides) {
                val theta = 2 * PI * j / sides
                val n = side * cos(theta).toFloat() + binormal * sin(theta).toFloat()
                add(transform, normalMatrix, point + n * (radii?.get(k) ?: radius), n)
            }
        }
        grid(part, base, rings = path.size - 1, segments = sides, flip = true)
    }

    private fun normalMatrix(transform: Mat4) = transpose(inverse(transform))

    private fun add(transform: Mat4, normalMatrix: Mat4, position: Float3, normal: Float3) {
        positions += (transform * Float4(position, 1f)).xyz
        normals += normalize((normalMatrix * Float4(normal, 0f)).xyz)
    }

    /** Stitches rings of (segments + 1) vertices into quads; [flip] reverses the winding. */
    private fun grid(part: Int, base: Int, rings: Int, segments: Int, flip: Boolean) {
        val indices = parts[part]
        for (i in 0 until rings) {
            for (j in 0 until segments) {
                val a = base + i * (segments + 1) + j
                val b = a + segments + 1
                if (flip) {
                    indices += listOf(a, a + 1, b, a + 1, b + 1, b)
                } else {
                    indices += listOf(a, b, a + 1, a + 1, b, b + 1)
                }
            }
        }
    }
}
