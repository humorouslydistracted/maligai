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

/** Minimum matcher score to treat handwriting as a catalog hit (not a new item). */
internal const val MIN_CATALOG_SCORE = 25

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
