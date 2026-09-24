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
    fun `places exactly the chosen offerings plus two candles`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 1, incenseSticks = 3, fruit = 1, food = 2))

        assertEquals(1, layout.count(MemorialItemType.PLAQUE))
        assertEquals(3, layout.count(MemorialItemType.INCENSE_STICK))
        assertEquals(1, layout.count(MemorialItemType.INCENSE_POT))
        assertEquals(1, layout.count(MemorialItemType.FRUIT_OFFERING))
        assertEquals(2, layout.count(MemorialItemType.FOOD_OFFERING))
        assertEquals(2, layout.count(MemorialItemType.CANDLE))
    }

    @Test
    fun `no incense means no pot`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 1))

        assertEquals(0, layout.count(MemorialItemType.INCENSE_POT))
        assertEquals(0, layout.count(MemorialItemType.INCENSE_STICK))
    }

    @Test
    fun `empty memorial still gets candles`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings())

        assertEquals(listOf(MemorialItemType.CANDLE, MemorialItemType.CANDLE), layout.map { it.first })
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
    fun `plaques are centred and candles sit outside them`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(plaques = 3))
        val plaqueX = layout.offsets(MemorialItemType.PLAQUE).map { it.x }
        val candleX = layout.offsets(MemorialItemType.CANDLE).map { it.x }

        assertEquals(0f, plaqueX.sum(), 1e-4f)
        assertTrue(candleX.min() < plaqueX.min() && candleX.max() > plaqueX.max())
    }

    @Test
    fun `fruit on the left, food on the right, no two plates overlapping`() {
        val layout = MemorialItems.layoutFor(MemorialOfferings(fruit = 10, food = 10))
        val fruit = layout.offsets(MemorialItemType.FRUIT_OFFERING)
        val food = layout.offsets(MemorialItemType.FOOD_OFFERING)

        assertTrue(fruit.all { it.x < 0f })
        assertTrue(food.all { it.x > 0f })
        val plates = fruit + food
        assertEquals(plates.size, plates.map { it.x to it.z }.distinct().size)
    }

    @Test
    fun `firestore round trip keeps quantities and clamps bad values`() {
        val offerings = MemorialOfferings(plaques = 1, incenseSticks = 5, fruit = 2, food = 2)
        assertEquals(offerings, MemorialOfferings.fromFirestore(offerings.toFirestore()))

        val tampered = mapOf("plaques" to 500L, "incenseSticks" to -3L, "fruit" to "x")
        assertEquals(MemorialOfferings(plaques = 99), MemorialOfferings.fromFirestore(tampered))
    }
}
