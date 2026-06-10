package com.maligai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityPickerTest {

    @Test
    fun parseWeightGramsAndKg() {
        assertEquals(0.25, parseQuantityInput("250g", UnitType.WEIGHT)!!, 0.001)
        assertEquals(0.25, parseQuantityInput("250 g", UnitType.WEIGHT)!!, 0.001)
        assertEquals(1.0, parseQuantityInput("1kg", UnitType.WEIGHT)!!, 0.001)
        assertEquals(1.5, parseQuantityInput("1.5kg", UnitType.WEIGHT)!!, 0.001)
    }

    @Test
    fun parseVolumeMlAndLitre() {
        assertEquals(0.5, parseQuantityInput("500ml", UnitType.VOLUME)!!, 0.001)
        assertEquals(1.0, parseQuantityInput("1L", UnitType.VOLUME)!!, 0.001)
        assertEquals(1.5, parseQuantityInput("1.5 litre", UnitType.VOLUME)!!, 0.001)
    }

    @Test
    fun parseCountFractionsAndIntegers() {
        assertEquals(0.25, parseQuantityInput("1/4", UnitType.COUNT)!!, 0.001)
        assertEquals(0.5, parseQuantityInput("½", UnitType.COUNT)!!, 0.001)
        assertEquals(10.0, parseQuantityInput("10", UnitType.COUNT)!!, 0.001)
    }

    @Test
    fun rejectInvalidInput() {
        assertNull(parseQuantityInput("", UnitType.WEIGHT))
        assertNull(parseQuantityInput("-1kg", UnitType.WEIGHT))
        assertNull(parseQuantityInput("abc", UnitType.COUNT))
        assertNull(parseQuantityInput("1/0", UnitType.COUNT))
    }

    @Test
    fun formatWeightDisplay() {
        assertEquals("250 g", formatQuantityDisplay(0.25, UnitType.WEIGHT))
        assertEquals("1 kg", formatQuantityDisplay(1.0, UnitType.WEIGHT))
    }

    @Test
    fun formatVolumeDisplay() {
        assertEquals("500 ml", formatQuantityDisplay(0.5, UnitType.VOLUME))
        assertEquals("2 L", formatQuantityDisplay(2.0, UnitType.VOLUME))
    }

    @Test
    fun weightScrollReaches50kg() {
        val scroll = QuantityPresets.scrollOptions(UnitType.WEIGHT)
        assertTrue(scroll.any { it.value == 50.0 })
        assertTrue(scroll.any { it.value == 1.25 })
    }

    @Test
    fun countScrollReaches100() {
        val scroll = QuantityPresets.scrollOptions(UnitType.COUNT)
        assertEquals(100.0, scroll.last().value, 0.001)
        assertEquals(6.0, scroll.first().value, 0.001)
    }
}
