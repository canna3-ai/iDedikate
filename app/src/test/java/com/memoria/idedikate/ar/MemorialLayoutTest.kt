package com.memoria.idedikate.ar

import com.memoria.idedikate.model.MemorialOfferings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class MemorialLayoutTest {

    private fun List<Pair<MemorialItemType, Offset>>.count(type: MemorialItemType) = count { it.first == type }
    private fun List<Pair<MemorialItemType, Offset>>.offsets(type: MemorialItemType) = filter { it.first == type }.map { it.second }

    @Test
    fun `places exactly the chosen offerings`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 1, incenseSticks = 3, flowers = 2, candles = 4))

        assertEquals(1, layout.count(MemorialItemType.PLAQUE))
        assertEquals(3, layout.count(MemorialItemType.INCENSE_STICK))
        assertEquals(1, layout.count(MemorialItemType.INCENSE_POT))
        assertEquals(2, layout.count(MemorialItemType.FLOWER))
        assertEquals(4, layout.count(MemorialItemType.CANDLE))
    }

    @Test
    fun `no incense means no pot`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 1))

        assertEquals(0, layout.count(MemorialItemType.INCENSE_POT))
        assertEquals(0, layout.count(MemorialItemType.INCENSE_STICK))
    }

    @Test
    fun `empty memorial places nothing`() {
        assertTrue(MemorialItems.layoutFor(MemorialOfferings()).isEmpty())
    }

    @Test
    fun `incense sticks stay inside the pot opening`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(incenseSticks = 99))
        val pot = layout.offsets(MemorialItemType.INCENSE_POT).single()

        layout.offsets(MemorialItemType.INCENSE_STICK).forEach { stick ->
            assertTrue(hypot(stick.x - pot.x, stick.z - pot.z) <= 0.035f + 1e-4f)
        }
    }

    @Test
    fun `plaques are centred`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 3))

        assertEquals(0f, layout.offsets(MemorialItemType.PLAQUE).sumOf { it.x.toDouble() }.toFloat(), 1e-4f)
    }

    @Test
    fun `flowers on the left, candles on the right, none overlapping`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(flowers = 10, candles = 10))
        val flowers = layout.offsets(MemorialItemType.FLOWER)
        val candles = layout.offsets(MemorialItemType.CANDLE)

        assertTrue(flowers.all { it.x < 0f })
        assertTrue(candles.all { it.x > 0f })
        val all = flowers + candles
        assertEquals(all.size, all.map { it.x to it.z }.distinct().size)
    }

    @Test
    fun `firestore round trip keeps quantities and clamps bad values`() {
        val offerings = MemorialOfferings(plaques = 1, incenseSticks = 5, flowers = 2, candles = 2)
        assertEquals(offerings, MemorialOfferings.fromFirestore(offerings.toFirestore()))

        val tampered = mapOf("plaques" to 500L, "incenseSticks" to -3L, "flowers" to "x")
        assertEquals(MemorialOfferings(plaques = 99), MemorialOfferings.fromFirestore(tampered))
    }

    @Test
    fun `memorials saved before the rename read fruit as flowers and food as candles`() {
        val legacy = mapOf("plaques" to 1L, "incenseSticks" to 2L, "fruit" to 3L, "food" to 4L)

        assertEquals(
            MemorialOfferings(plaques = 1, incenseSticks = 2, flowers = 3, candles = 4),
            MemorialOfferings.fromFirestore(legacy)
        )
    }
}
