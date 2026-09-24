package com.memoria.idedikate.ar

import dev.romainguy.kotlin.math.Quaternion
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Small geodesy helpers for placing memorials at their real-world location. */
object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in metres (haversine). */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing from point 1 to point 2, in degrees clockwise from north (0..360). */
    fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLng = Math.toRadians(lng2 - lng1)
        val y = sin(dLng) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** e.g. "NE" for 45 degrees. */
    fun compassPoint(bearing: Double): String =
        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((bearing % 360 + 360) % 360 / 45.0).roundToInt() % 8]

    /** e.g. "85 m" or "1.2 km". */
    fun formatDistance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m" else "%.1f km".format(meters / 1000)

    /**
     * Rotation in ARCore's East-Up-South frame that turns a memorial's front (its local +Z, where
     * the offerings sit) towards [bearingToViewer], the compass bearing from the memorial to the
     * viewer. A rotation of phi about Up sends +Z to (sin phi, 0, cos phi) in EUS, while that
     * bearing points to (sin b, 0, -cos b), so phi = 180 - b.
     */
    fun facingQuaternion(bearingToViewer: Double): Quaternion {
        val halfAngle = Math.toRadians(180.0 - bearingToViewer) / 2
        return Quaternion(0f, sin(halfAngle).toFloat(), 0f, cos(halfAngle).toFloat())
    }
}
