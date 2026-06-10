package com.maligai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineParserTest {

    // ---------------------------------------------------------------- happy paths

    @Test
    fun `basic Tamil item with amount`() {
        val result = LineParser.parse("பால் - 30")
        assertEquals("பால்", result.displayText)
        assertEquals(30.0, result.lineTotal!!, 0.001)
        assertEquals(ParseConfidence.HIGH, result.confidence)
    }

    @Test
    fun `Tamil item with weight and amount`() {
        val result = LineParser.parse("அரிசி 1கி - 120")
        assertEquals("அரிசி 1கி", result.displayText)
        assertEquals(120.0, result.lineTotal!!, 0.001)
        assertEquals(ParseConfidence.HIGH, result.confidence)
        // matchHint should strip the weight token
        assertEquals("அரிசி", result.matchHint)
    }

    @Test
    fun `English item with amount`() {
        val result = LineParser.parse("aarokya paal - 45")
        assertEquals("aarokya paal", result.displayText)
        assertEquals(45.0, result.lineTotal!!, 0.001)
        assertEquals(ParseConfidence.HIGH, result.confidence)
    }

    @Test
    fun `mixed Tamil and English item`() {
        val result = LineParser.parse("aarokya பால் - 50")
        assertEquals("aarokya பால்", result.displayText)
        assertEquals(50.0, result.lineTotal!!, 0.001)
        assertEquals(ParseConfidence.HIGH, result.confidence)
    }

    @Test
    fun `fixed price item no weight`() {
        val result = LineParser.parse("அரிசி - 30")
        assertEquals("அரிசி", result.displayText)
        assertEquals(30.0, result.lineTotal!!, 0.001)
        assertEquals(ParseConfidence.HIGH, result.confidence)
    }

    // ---------------------------------------------------------------- edge cases

    @Test
    fun `no hyphen returns MEDIUM confidence`() {
        val result = LineParser.parse("சர்க்கரை")
        assertEquals("சர்க்கரை", result.displayText)
        assertNull(result.lineTotal)
        assertEquals(ParseConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun `hyphen with no amount`() {
        val result = LineParser.parse("அரிசி 1கி -")
        assertEquals("அரிசி 1கி", result.displayText)
        assertNull(result.lineTotal)
        assertEquals(ParseConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun `amount only is LOW confidence`() {
        val result = LineParser.parse("- 50")
        assertNull(result.matchHint)
        assertEquals(ParseConfidence.LOW, result.confidence)
        assertEquals(50.0, result.lineTotal!!, 0.001)
    }

    @Test
    fun `empty string is LOW`() {
        val result = LineParser.parse("  ")
        assertEquals(ParseConfidence.LOW, result.confidence)
        assertNull(result.lineTotal)
    }

    @Test
    fun `item name with hyphen uses last hyphen for split`() {
        val result = LineParser.parse("aarokya-paal - 45")
        assertEquals("aarokya-paal", result.displayText)
        assertEquals(45.0, result.lineTotal!!, 0.001)
    }

    // ---------------------------------------------------------------- amount parsing

    @Test
    fun `parseAmount strips rupee symbol`() {
        assertEquals(120.0, LineParser.parseAmount("₹120")!!, 0.001)
    }

    @Test
    fun `parseAmount strips Rs prefix`() {
        assertEquals(55.0, LineParser.parseAmount("Rs 55")!!, 0.001)
        assertEquals(55.0, LineParser.parseAmount("rs.55")!!, 0.001)
    }

    @Test
    fun `parseAmount handles comma separators`() {
        assertEquals(1200.0, LineParser.parseAmount("1,200")!!, 0.001)
    }

    @Test
    fun `parseAmount normalises Tamil digits`() {
        // ௧௨௦ = 120
        assertEquals(120.0, LineParser.parseAmount("\u0BE7\u0BE8\u0BE6")!!, 0.001)
    }

    @Test
    fun `parseAmount rejects zero or empty`() {
        assertNull(LineParser.parseAmount("0"))
        assertNull(LineParser.parseAmount(""))
        assertNull(LineParser.parseAmount("abc"))
    }

    // ---------------------------------------------------------------- amount range

    @Test
    fun `amount 5 is valid HIGH confidence`() {
        val result = LineParser.parse("உப்பு - 5")
        assertEquals(ParseConfidence.HIGH, result.confidence)
    }

    @Test
    fun `amount 10000 is valid HIGH confidence`() {
        val result = LineParser.parse("gas cylinder - 10000")
        assertEquals(ParseConfidence.HIGH, result.confidence)
    }

    @Test
    fun `amount above 10000 is MEDIUM confidence`() {
        val result = LineParser.parse("gold - 50000")
        assertEquals(ParseConfidence.MEDIUM, result.confidence)
    }

    // ---------------------------------------------------------------- displayText verbatim

    @Test
    fun `displayText not rewritten to catalog name`() {
        val result = LineParser.parse("aarokya paal - 45")
        // Display must be exactly as written, not normalised
        assertEquals("aarokya paal", result.displayText)
    }

    @Test
    fun `displayText preserves weight token`() {
        val result = LineParser.parse("அரிசி 1கி - 120")
        // 1கி stays in displayText
        assertTrue(result.displayText.contains("1கி"))
    }

    @Test
    fun `matchHint strips kg weight`() {
        val result = LineParser.parse("rice 1kg - 80")
        assertEquals("rice 1kg", result.displayText)
        assertEquals("rice", result.matchHint)
    }

    @Test
    fun `matchHint strips gram weight`() {
        val result = LineParser.parse("pepper 100g - 25")
        assertEquals("pepper 100g", result.displayText)
        assertEquals("pepper", result.matchHint)
    }

    @Test
    fun `Tamil weight only extracts quantity`() {
        val result = LineParser.parse("அரிசி 1கி")
        assertEquals("அரிசி 1கி", result.displayText)
        assertNull(result.lineTotal)
        assertEquals(1.0, result.parsedQuantity!!, 0.001)
        assertEquals("kg", result.parsedUnitLabel)
        assertEquals("அரிசி", result.matchHint)
    }

    @Test
    fun `English weight and amount extracts quantity`() {
        val result = LineParser.parse("rice 2kg - 80")
        assertEquals(80.0, result.lineTotal!!, 0.001)
        assertEquals(2.0, result.parsedQuantity!!, 0.001)
        assertEquals("kg", result.parsedUnitLabel)
        assertEquals("rice", result.matchHint)
    }

    @Test
    fun `bare count suffix extracts piece quantity`() {
        val result = LineParser.parse("பால் 3")
        assertEquals(3.0, result.parsedQuantity!!, 0.001)
        assertEquals("piece", result.parsedUnitLabel)
        assertEquals("பால்", result.matchHint)
    }

    // ---------------------------------------------------------------- x-token quantity

    @Test
    fun `x suffix piece count`() {
        val result = LineParser.parse("maggi x2")
        assertEquals("maggi x2", result.displayText)
        assertNull(result.lineTotal)
        assertEquals(2.0, result.parsedQuantity!!, 0.001)
        assertEquals("piece", result.parsedUnitLabel)
        assertEquals("maggi", result.matchHint)
    }

    @Test
    fun `x prefix piece count`() {
        val result = LineParser.parse("x2 maggi")
        assertEquals(2.0, result.parsedQuantity!!, 0.001)
        assertEquals("piece", result.parsedUnitLabel)
        assertEquals("maggi", result.matchHint)
    }

    @Test
    fun `x suffix with amount`() {
        val result = LineParser.parse("maggi x2 - 40")
        assertEquals("maggi x2", result.displayText)
        assertEquals(40.0, result.lineTotal!!, 0.001)
        assertEquals(2.0, result.parsedQuantity!!, 0.001)
        assertEquals("piece", result.parsedUnitLabel)
        assertEquals("maggi", result.matchHint)
    }

    @Test
    fun `x suffix weight gm`() {
        val result = LineParser.parse("dal x250gm")
        assertEquals(250.0, result.parsedQuantity!!, 0.001)
        assertEquals("g", result.parsedUnitLabel)
        assertEquals("dal", result.matchHint)
    }

    @Test
    fun `x prefix volume ml`() {
        val result = LineParser.parse("x200ml oil")
        assertEquals(200.0, result.parsedQuantity!!, 0.001)
        assertEquals("ml", result.parsedUnitLabel)
        assertEquals("oil", result.matchHint)
    }

    @Test
    fun `x suffix weight with amount`() {
        val result = LineParser.parse("dal x250gm - 60")
        assertEquals(60.0, result.lineTotal!!, 0.001)
        assertEquals(250.0, result.parsedQuantity!!, 0.001)
        assertEquals("g", result.parsedUnitLabel)
    }

    @Test
    fun `unicode multiply sign as x`() {
        val result = LineParser.parse("maggi ×2")
        assertEquals(2.0, result.parsedQuantity!!, 0.001)
        assertEquals("maggi", result.matchHint)
    }
}
