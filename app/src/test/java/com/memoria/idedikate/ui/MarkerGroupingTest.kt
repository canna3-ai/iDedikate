package com.memoria.idedikate.ui

import com.memoria.idedikate.model.MemorialItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerGroupingTest {

    private fun memorial(id: String, lat: Double, lng: Double, createdAt: Long = 1) =
        MemorialItem(id = id, latitude = lat, longitude = lng, createdAtMillis = createdAt)

    @Test
    fun `memorials at the same spot share one marker, newest first`() {
        val groups = groupOverlapping(
            listOf(memorial("old", 1.35, 103.82, createdAt = 100), memorial("new", 1.35, 103.82, createdAt = 200)),
            zoom = 21f, markerScale = 1.6f
        )

        assertEquals(1, groups.size)
        assertEquals(listOf("new", "old"), groups[0].memorials.map { it.id })
        assertTrue(groups[0].isSameSpot)
    }

    @Test
    fun `a memorial still being placed counts as the newest`() {
        val groups = groupOverlapping(
            listOf(memorial("placed", 1.35, 103.82, createdAt = 200), memorial("placing", 1.35, 103.82, createdAt = 0)),
            zoom = 17f, markerScale = 1.6f
        )

        assertEquals("placing", groups.single().representative.id)
    }

    @Test
    fun `nearby memorials separate once zoomed in`() {
        // About 150 m apart east-west
        val memorials = listOf(memorial("a", 1.35, 103.8200), memorial("b", 1.35, 103.82135))

        val zoomedOut = groupOverlapping(memorials, zoom = 12f, markerScale = 0.3f)
        val zoomedIn = groupOverlapping(memorials, zoom = 18f, markerScale = 1.6f)

        assertEquals(1, zoomedOut.size)
        assertFalse(zoomedOut[0].isSameSpot)
        assertEquals(2, zoomedIn.size)
    }

    @Test
    fun `distant memorials keep their own markers`() {
        val groups = groupOverlapping(
            listOf(memorial("a", 1.35, 103.82), memorial("b", 1.45, 103.92)),
            zoom = 15f, markerScale = 1f
        )

        assertEquals(2, groups.size)
    }
}
