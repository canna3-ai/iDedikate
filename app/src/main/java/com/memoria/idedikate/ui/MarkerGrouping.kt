package com.memoria.idedikate.ui

import com.memoria.idedikate.ar.GeoMath
import com.memoria.idedikate.model.MemorialItem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

/** Memorials whose map markers would sit on top of each other, newest first. */
class MemorialGroup(val memorials: List<MemorialItem>) {
    /** The newest memorial; its marker stands for the whole group, at its exact spot. */
    val representative: MemorialItem get() = memorials.first()
    val size: Int get() = memorials.size

    /** Every memorial is at practically the same spot, so zooming in won't separate them. */
    val isSameSpot: Boolean = memorials.all {
        GeoMath.distanceMeters(it.latitude, it.longitude, representative.latitude, representative.longitude) < SAME_SPOT_M
    }
}

/**
 * Groups memorials whose markers would largely cover each other at [zoom], given markers drawn
 * at [markerScale] (see MapScreen). Markers that only touch at the edges stay separate.
 */
fun groupOverlapping(memorials: List<MemorialItem>, zoom: Float, markerScale: Float): List<MemorialGroup> {
    val groups = mutableListOf<MutableList<MemorialItem>>()
    // A just-placed memorial has no server time yet; it's the newest
    val newestFirst = memorials.sortedByDescending { if (it.createdAtMillis == 0L) Long.MAX_VALUE else it.createdAtMillis }
    for (memorial in newestFirst) {
        val group = groups.firstOrNull { overlaps(it.first(), memorial, zoom, markerScale) }
        if (group != null) group += memorial else groups += mutableListOf(memorial)
    }
    return groups.map(::MemorialGroup)
}

private fun overlaps(a: MemorialItem, b: MemorialItem, zoom: Float, markerScale: Float): Boolean {
    val metersPerDp = metersPerDp(a.latitude, zoom)
    val eastMeters = abs(b.longitude - a.longitude) * METERS_PER_DEGREE * cos(Math.toRadians(a.latitude))
    val northMeters = abs(b.latitude - a.latitude) * METERS_PER_DEGREE
    // Centers closer than three quarters of a marker: they would overlap by more than a quarter
    return eastMeters < MARKER_WIDTH_DP * markerScale * OVERLAP_FRACTION * metersPerDp &&
        northMeters < MARKER_HEIGHT_DP * markerScale * OVERLAP_FRACTION * metersPerDp
}

/** Ground distance one dp of map covers (the map scales tiles to dpi, so the world is 256 dp wide at zoom 0). */
private fun metersPerDp(latitude: Double, zoom: Float): Double =
    EQUATOR_METERS_PER_DP_AT_ZOOM_0 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom.toDouble())

private const val EQUATOR_METERS_PER_DP_AT_ZOOM_0 = 156_543.03392
private const val METERS_PER_DEGREE = 111_320.0
/** Typical memorial marker size at scale 1 (a pill of a few offering icons, plus its pointer). */
private const val MARKER_WIDTH_DP = 140.0
private const val MARKER_HEIGHT_DP = 55.0
private const val OVERLAP_FRACTION = 0.75
private const val SAME_SPOT_M = 1.0
