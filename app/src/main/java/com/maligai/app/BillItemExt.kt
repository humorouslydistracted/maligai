package com.maligai.app

import kotlin.math.abs

/** True when two money totals are equal within [tolerance]. */
fun totalsMatch(a: Double, b: Double, tolerance: Double = 0.01): Boolean =
    abs(a - b) <= tolerance

/** True when the line total matches qty × unit price (catalog-priced line). */
fun BillItem.showsQtyBreakdown(): Boolean {
    if (unitLabel.isBlank()) return false
    return abs(lineTotal - quantity * unitPrice) < 0.01
}

/** Derives catalog quantity from amount-only entry (e.g. ₹20 at ₹50/kg → 0.4 kg). */
fun catalogQuantityFromAmount(lineTotal: Double, catalogPricePerUnit: Double): Double? {
    if (lineTotal <= 0 || catalogPricePerUnit <= 0) return null
    return lineTotal / catalogPricePerUnit
}

/** Friendly qty label for UI: "400 g", "200 ml", "2 piece". */
fun BillItem.displayQuantityLabel(): String = when (unitType) {
    UnitType.WEIGHT, UnitType.VOLUME -> formatQuantityDisplay(quantity, unitType)
    else -> "${formatQty(quantity)} $unitLabel".trim()
}

/** Subtitle for bill lines: "400 g × ₹50", or null when no qty breakdown. */
fun BillItem.qtyBreakdownText(): String? {
    if (!showsQtyBreakdown()) return null
    return "${displayQuantityLabel()} \u00D7 ${formatRs(unitPrice)}"
}

/** Compact qty label for thermal receipt (no currency). */
fun BillItem.receiptQuantityLabel(): String = when (unitType) {
    UnitType.WEIGHT, UnitType.VOLUME -> formatQuantityDisplay(quantity, unitType)
    else -> "${formatQty(quantity)}${unitLabel.take(3)}"
}

/** Build a catalog bill line; [amountOnly] derives qty from catalog unit price when true. */
internal fun buildCatalogBillItem(
    billId: Long,
    item: MenuItem,
    lineTotal: Double,
    quantity: Double,
    amountOnly: Boolean = false
): BillItem {
    val catalogPrice = item.pricePerUnit
    return when {
        amountOnly && catalogPrice > 0 -> BillItem(
            billId = billId,
            itemName = item.nameLocal,
            itemNameLatin = item.nameLatin,
            unitType = item.unitType,
            unitLabel = item.unitLabel,
            quantity = lineTotal / catalogPrice,
            unitPrice = catalogPrice,
            lineTotal = lineTotal
        )
        amountOnly -> BillItem(
            billId = billId,
            itemName = item.nameLocal,
            itemNameLatin = item.nameLatin,
            unitType = UnitType.COUNT,
            unitLabel = "",
            quantity = 1.0,
            unitPrice = lineTotal,
            lineTotal = lineTotal
        )
        else -> {
            val qty = quantity.coerceAtLeast(1.0)
            BillItem(
                billId = billId,
                itemName = item.nameLocal,
                itemNameLatin = item.nameLatin,
                unitType = item.unitType,
                unitLabel = item.unitLabel,
                quantity = qty,
                unitPrice = lineTotal / qty,
                lineTotal = lineTotal
            )
        }
    }
}

/** Build a handwritten/new bill line; [amountOnly] derives qty from [unitPrice] when true. */
internal fun buildHandwrittenBillItem(
    billId: Long,
    nameLocal: String,
    nameLatin: String,
    unitType: String,
    unitLabel: String,
    unitPrice: Double,
    quantity: Double,
    lineTotal: Double,
    amountOnly: Boolean = false
): BillItem {
    val label = unitLabel.trim().ifBlank { defaultUnitLabel(unitType) }
    return when {
        amountOnly && unitPrice > 0 -> BillItem(
            billId = billId,
            itemName = nameLocal,
            itemNameLatin = nameLatin,
            unitType = unitType,
            unitLabel = label,
            quantity = lineTotal / unitPrice,
            unitPrice = unitPrice,
            lineTotal = lineTotal
        )
        amountOnly -> BillItem(
            billId = billId,
            itemName = nameLocal,
            itemNameLatin = nameLatin,
            unitType = UnitType.COUNT,
            unitLabel = "",
            quantity = 1.0,
            unitPrice = lineTotal,
            lineTotal = lineTotal
        )
        label.isNotBlank() && unitType != UnitType.COUNT -> {
            val qty = quantity.coerceAtLeast(1.0)
            BillItem(
                billId = billId,
                itemName = nameLocal,
                itemNameLatin = nameLatin,
                unitType = unitType,
                unitLabel = label,
                quantity = qty,
                unitPrice = lineTotal / qty,
                lineTotal = lineTotal
            )
        }
        else -> BillItem(
            billId = billId,
            itemName = nameLocal,
            itemNameLatin = nameLatin,
            unitType = UnitType.COUNT,
            unitLabel = "",
            quantity = 1.0,
            unitPrice = lineTotal,
            lineTotal = lineTotal
        )
    }
}

internal fun defaultUnitLabel(type: String): String = when (type) {
    UnitType.WEIGHT -> "kg"
    UnitType.VOLUME -> "litre"
    else -> "piece"
}

internal fun unitTypeDisplayLabel(type: String): String = when (type) {
    UnitType.WEIGHT -> "Weight"
    UnitType.VOLUME -> "Volume"
    UnitType.COUNT -> "Piece"
    else -> type
}

/** @see unitTypeDisplayLabel */
internal fun unitTypeShortLabel(type: String): String = unitTypeDisplayLabel(type)

internal fun billNumberFromName(name: String): Int? =
    name.removePrefix("Bill ").trim().toIntOrNull()

internal fun startOfLocalDayMillis(now: Long = System.currentTimeMillis()): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = now
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

internal fun isMostlyLatin(text: String): Boolean =
    text.isNotBlank() && text.all { it.isWhitespace() || it.code < 128 }

/** Minimum matcher score to treat handwriting as a catalog hit (not a new item).
 *  Set to 30 so that weak single-signal matches (prefix-only: +10, token-overlap-only: +15)
 *  are filtered out; contains-match (+30) and above still surface as suggestions. */
internal const val MIN_CATALOG_SCORE = 30

/** Latin handwriting must match [MenuItem.nameLatin]; regional script uses any strong score. */
internal fun isStrongCatalogMatch(matchText: String, scored: Matcher.ScoredMatch): Boolean {
    if (scored.score < MIN_CATALOG_SCORE) return false
    if (!isMostlyLatin(matchText)) return true
    val en = scored.item.nameLatin.trim()
    if (en.isBlank()) return scored.score >= 100
    return en.equals(matchText, ignoreCase = true) ||
        en.contains(matchText, ignoreCase = true) ||
        matchText.contains(en, ignoreCase = true) ||
        scored.score >= 50
}

enum class AddItemResult {
    Success,
    DuplicateCatalog
}
