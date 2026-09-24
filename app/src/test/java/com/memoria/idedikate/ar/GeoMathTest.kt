package com.memoria.idedikate.ar

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GeoMathTest {

    @Test
    fun `distance between two nearby points in Singapore`() {
        // 0.001 degrees of latitude is about 111 m everywhere
        assertEquals(111.2, GeoMath.distanceMeters(1.3521, 103.8198, 1.3531, 103.8198), 0.5)
    }

    @Test
    fun `bearings point the right way`() {
        assertEquals(0.0, GeoMath.bearingDegrees(1.35, 103.82, 1.36, 103.82), 0.01)
        assertEquals(90.0, GeoMath.bearingDegrees(1.35, 103.82, 1.35, 103.83), 0.01)
        assertEquals(180.0, GeoMath.bearingDegrees(1.36, 103.82, 1.35, 103.82), 0.01)
        assertEquals(270.0, GeoMath.bearingDegrees(1.35, 103.83, 1.35, 103.82), 0.01)
    }

    @Test
    fun `compass points and distance labels`() {
        assertEquals("N", GeoMath.compassPoint(3.0))
        assertEquals("NE", GeoMath.compassPoint(44.0))
        assertEquals("W", GeoMath.compassPoint(270.0))
        assertEquals("N", GeoMath.compassPoint(359.0))
        assertEquals("85 m", GeoMath.formatDistance(85.4))
        assertEquals("1.2 km", GeoMath.formatDistance(1234.0))
    }

    @Test
    fun `memorial front faces the viewer`() {
        // Rotate the memorial's +Z axis by the quaternion and compare with the direction to the viewer (EUS frame)
        for (bearing in listOf(0.0, 45.0, 90.0, 180.0, 270.0)) {
            val q = GeoMath.facingQuaternion(bearing)
            // Rotation about Y by angle phi, where q = (0, sin(phi/2), 0, cos(phi/2))
            val phi = 2 * kotlin.math.atan2(q.y.toDouble(), q.w.toDouble())
            val east = sin(phi)
            val south = cos(phi)
            assertEquals("east for $bearing", sin(Math.toRadians(bearing)), east, 1e-6)
            assertEquals("south for $bearing", -cos(Math.toRadians(bearing)), south, 1e-6)
        }
    }
}
