package com.maligai.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillItemDisplayTest {

    private fun item(
        unitLabel: String = "kg",
        quantity: Double = 2.0,
        unitPrice: Double = 50.0,
        lineTotal: Double = 100.0
    ) = BillItem(
        billId = 1,
        itemName = "Test",
        unitLabel = unitLabel,
        quantity = quantity,
        unitPrice = unitPrice,
        lineTotal = lineTotal
    )

    @Test
    fun showsBreakdownWhenTotalMatchesQtyTimesPrice() {
        assertTrue(item().showsQtyBreakdown())
    }

    @Test
    fun hidesBreakdownWhenUnitLabelBlank() {
        assertFalse(item(unitLabel = "").showsQtyBreakdown())
    }

    @Test
    fun hidesBreakdownWhenManualAmountOverridesCatalog() {
        assertFalse(item(lineTotal = 85.0).showsQtyBreakdown())
    }

    @Test
    fun showsBreakdownWithinRoundingTolerance() {
        assertTrue(item(lineTotal = 100.005).showsQtyBreakdown())
    }
}
