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
    fun `plaques are centered`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 3))

        assertEquals(0f, layout.offsets(MemorialItemType.PLAQUE).sumOf { it.x.toDouble() }.toFloat(), 1e-4f)
    }

    @Test
    fun `an even number of candles splits left and right of everything else`() {
        listOf(2, 4, 10).forEach { count ->
            val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 3, incenseSticks = 5, flowers = 10, candles = count))
            val candles = layout.offsets(MemorialItemType.CANDLE)
            val others = layout.filter { it.first != MemorialItemType.CANDLE && it.first != MemorialItemType.CANDLE_LIGHT }
                .map { it.second.x }

            assertEquals(count, candles.size)
            assertEquals(count / 2, candles.count { it.x < others.min() })
            assertEquals(count / 2, candles.count { it.x > others.max() })
            assertEquals(candles.size, candles.map { it.x to it.z }.distinct().size)
        }
    }

    @Test
    fun `two candles mirror each other`() {
        val (left, right) = MemorialItems.layoutFor(MemorialOfferings(plaques = 1, candles = 2))
            .offsets(MemorialItemType.CANDLE).sortedBy { it.x }

        assertEquals(-left.x, right.x, 1e-4f)
        assertEquals(left.z, right.z, 1e-4f)
        // Clear of the plaque's edges
        assertTrue(right.x > 0.11f)
    }

    @Test
    fun `an odd candle stands in the incense pot, clear of the sticks`() {
        listOf(1, 3, 5).forEach { count ->
            val layout = MemorialItems.layoutFor(MemorialOfferings(incenseSticks = 20, candles = count))
            val pot = layout.offsets(MemorialItemType.INCENSE_POT).single()
            val inPot = layout.offsets(MemorialItemType.CANDLE).filter { hypot(it.x - pot.x, it.z - pot.z) < 1e-4f }

            assertEquals(1, inPot.size)
            assertTrue(inPot.single().y > 0f) // on the ash, not the ground
            layout.offsets(MemorialItemType.INCENSE_STICK).forEach { stick ->
                val r = hypot(stick.x - pot.x, stick.z - pot.z)
                assertTrue(r >= 0.015f && r <= 0.035f + 1e-4f)
            }
            assertEquals((count - 1) / 2, layout.offsets(MemorialItemType.CANDLE).count { it.x < -0.05f })
            assertEquals((count - 1) / 2, layout.offsets(MemorialItemType.CANDLE).count { it.x > 0.05f })
        }
    }

    @Test
    fun `without incense an odd candle stands front and center`() {
        val candles = MemorialItems.layoutFor(MemorialOfferings(plaques = 1, flowers = 3, candles = 3))
            .offsets(MemorialItemType.CANDLE)
        val center = candles.single { it.x == 0f }

        assertEquals(0f, center.y)
        assertTrue(center.z > 0f)
        assertEquals(1, candles.count { it.x < 0f })
        assertEquals(1, candles.count { it.x > 0f })
    }

    @Test
    fun `flowers stand centered between the plaque and the pot`() {
        listOf(1, 7, 10, 99).forEach { count ->
            val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 1, incenseSticks = 5, flowers = count))
            val plaque = layout.offsets(MemorialItemType.PLAQUE).single()
            val pot = layout.offsets(MemorialItemType.INCENSE_POT).single()
            val bunches = layout.filter { it.first == MemorialItemType.FLOWER || it.first == MemorialItemType.BOUQUET_TIE }

            bunches.forEach { (_, offset) ->
                // In front of the plaque's face, behind the pot's back edge
                assertTrue(offset.z > plaque.z + 0.05f && offset.z < pot.z - 0.1f)
            }
            val xs = bunches.map { it.second.x }
            assertEquals(0f, (xs.min() + xs.max()) / 2f, 0.01f)
        }
    }

    @Test
    fun `flowers are gathered into tied bunches`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(flowers = 10))

        assertEquals(10, layout.count(MemorialItemType.FLOWER))
        // 7 + 3: each bunch of more than one flower gets a ribbon
        val ties = layout.offsets(MemorialItemType.BOUQUET_TIE)
        assertEquals(2, ties.size)
        // Every stem starts right at its bunch's ribbon, and fans out from there
        val flowers = layout.offsets(MemorialItemType.FLOWER)
        flowers.forEach { flower ->
            assertTrue(ties.any { hypot(flower.x - it.x, flower.z - it.z) < 0.01f })
        }
        assertTrue(flowers.all { it.tilt > 0f })
    }

    @Test
    fun `a single flower stands slanted, without a ribbon`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(flowers = 1))

        assertEquals(0, layout.count(MemorialItemType.BOUQUET_TIE))
        assertTrue(layout.offsets(MemorialItemType.FLOWER).single().tilt > 0f)
    }

    @Test
    fun `smoke rises from the tips of some sticks`() {
        val few = MemorialItems.layoutFor(MemorialOfferings(incenseSticks = 3))
        assertEquals(3, few.count(MemorialItemType.INCENSE_SMOKE))

        val many = MemorialItems.layoutFor(MemorialOfferings(incenseSticks = 99))
        val smoke = many.offsets(MemorialItemType.INCENSE_SMOKE)
        assertEquals(MemorialItems.MAX_SMOKE_STREAMS, smoke.size)
        val sticks = many.offsets(MemorialItemType.INCENSE_STICK)
        smoke.forEach { puff ->
            // Directly above some stick's base, allowing for its outward lean
            assertTrue(sticks.any { hypot(puff.x - it.x, puff.z - it.z) < 0.05f })
            assertTrue(puff.y > sticks.first().y + MemorialItems.STICK_LENGTH * 0.95f)
        }
    }

    @Test
    fun `candles share one light`() {
        assertEquals(1, MemorialItems.layoutFor(MemorialOfferings(candles = 12)).count(MemorialItemType.CANDLE_LIGHT))
        assertEquals(0, MemorialItems.layoutFor(MemorialOfferings(plaques = 1)).count(MemorialItemType.CANDLE_LIGHT))
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
