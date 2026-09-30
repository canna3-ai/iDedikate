package com.memoria.idedikate.ar

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.dot
import dev.romainguy.kotlin.math.length
import dev.romainguy.kotlin.math.rotation
import dev.romainguy.kotlin.math.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshBuilderTest {

    /** Every non-degenerate triangle must face the same way as its vertex normals, or it gets culled. */
    private fun assertFacesOutward(mesh: MeshBuilder) {
        mesh.parts.flatten().chunked(3).forEach { (a, b, c) ->
            val face = cross(mesh.positions[b] - mesh.positions[a], mesh.positions[c] - mesh.positions[a])
            if (length(face) < 1e-12f) return@forEach // collapsed at a pole
            val normal = mesh.normals[a] + mesh.normals[b] + mesh.normals[c]
            assertTrue("triangle $a,$b,$c faces inward", dot(face, normal) > 0f)
        }
    }

    @Test
    fun `ellipsoid rests on the origin and faces outward`() {
        val mesh = MeshBuilder(1).apply { ellipsoid(0, scale(Float3(0.1f, 0.4f, 0.2f))) }

        assertEquals(0f, mesh.positions.minOf { it.y }, 1e-5f)
        assertEquals(0.4f, mesh.positions.maxOf { it.y }, 1e-5f)
        assertEquals(0.05f, mesh.positions.maxOf { it.x }, 1e-5f)
        assertFacesOutward(mesh)
    }

    @Test
    fun `tube faces outward, including tapered ones`() {
        val path = List(5) { Float3(0f, it * 0.1f, it * it * 0.01f) }
        val mesh = MeshBuilder(1).apply {
            tube(0, Mat4.identity(), path, 0.01f)
            tube(0, Mat4.identity(), path, 0f, radii = listOf(0f, 0.02f, 0.03f, 0.01f, 0f))
        }
        assertFacesOutward(mesh)
    }

    @Test
    fun `rotating about x tilts up toward the front`() {
        // The lily and incense layouts rely on this convention (and on angles being degrees)
        val tipped = rotation(Float3(1f, 0f, 0f), 90f) * Float4(0f, 1f, 0f, 0f)
        assertEquals(1f, tipped.z, 1e-5f)
        assertEquals(0f, tipped.y, 1e-5f)
    }

    @Test
    fun `lily stands about a stem tall with its bloom opening forward`() {
        val mesh = LilyMesh.mesh
        val top = mesh.positions.maxOf { it.y }
        assertTrue("top at $top", top > LilyMesh.STEM_HEIGHT && top < LilyMesh.STEM_HEIGHT + 0.12f)
        assertEquals(0f, mesh.positions.minOf { it.y }, 0.01f)
        // Bloom nods toward the front, so it reaches further forward than back
        assertTrue(mesh.positions.maxOf { it.z } > -mesh.positions.minOf { it.z })
        // A real lily bloom is roughly 12-18 cm across
        val bloom = mesh.parts[LilyMesh.PART_PETAL].map { mesh.positions[it] }
        val width = bloom.maxOf { it.x } - bloom.minOf { it.x }
        assertTrue("bloom $width m wide", width in 0.1f..0.2f)
        mesh.parts.forEach { assertTrue(it.isNotEmpty()) }
        assertFacesOutward(mesh)
    }
}
