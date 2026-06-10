package com.maligai.app

enum class ParseConfidence { HIGH, MEDIUM, LOW }

/**
 * Represents a parsed handwritten line from the billing canvas.
 * Format: `item [xQty] - amount` where xQty = x2, x250gm, x200ml, etc.
 *
 * [displayText] is stored verbatim (left of the last hyphen), shown as-is on
 * bill and receipt — never rewritten from the inventory catalog.
 * [lineTotal] is the numeric amount parsed from the right side.
 * [matchHint] is used internally for inventory matching only (may strip a
 * trailing weight token from displayText to improve match accuracy).
 * [parsedQuantity] / [parsedUnitLabel] are extracted from trailing qty+unit tokens.
 */
data class ParsedLine(
    val raw: String,
    val displayText: String,
    val lineTotal: Double?,
    val matchHint: String?,
    val confidence: ParseConfidence,
    val parsedQuantity: Double? = null,
    val parsedUnitLabel: String? = null
)

object LineParser {

    private val tamilDigitMap = digitRange('\u0BE6', '\u0BEF')
    private val devanagariDigitMap = digitRange('\u0966', '\u096F')
    private val bengaliDigitMap = digitRange('\u09E6', '\u09EF')
    private val teluguDigitMap = digitRange('\u0C66', '\u0C6F')
    private val kannadaDigitMap = digitRange('\u0CE6', '\u0CEF')
    private val malayalamDigitMap = digitRange('\u0D66', '\u0D6F')
    private val gujaratiDigitMap = digitRange('\u0AE6', '\u0AEF')

    private val latinUnits =
        """g|gm|grams?|kg|kgs?|ml|l(?:itr?e?s?)?|ltr"""

    /** x2, x250gm, ×200ml — explicit qty token (prefix or suffix on item side). */
    private val xUnitGroup = """($latinUnits)?"""
    private val xSuffixRegex = Regex(
        """[\s\u00A0]*[x×](\d[\d.,]*)$xUnitGroup\s*$""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val xPrefixRegex = Regex(
        """^[x×](\d[\d.,]*)$xUnitGroup\s+""",
        setOf(RegexOption.IGNORE_CASE)
    )

    /** Strips prefix ₹ / Rs / rs and comma separators; normalises script digits. */
    fun parseAmount(s: String, scriptTag: String = ScriptLanguages.DEFAULT_TAG): Double? {
        val normalized = normalizeDigits(s, scriptTag)
            .replace(Regex("""[₹\s]"""), "")
            .replace(Regex("""(?i)rs\.?"""), "")
            .replace(",", "")
            .trim()
        val d = normalized.toDoubleOrNull() ?: return null
        return if (d > 0) d else null
    }

    /**
     * Parses a full recognized line. Splits on the **last** hyphen so that
     * item names containing hyphens (e.g. "aarokya-paal") still work.
     */
    fun parse(raw: String, scriptTag: String = ScriptLanguages.DEFAULT_TAG): ParsedLine {
        val text = raw.trim()
        if (text.isEmpty()) {
            return ParsedLine(raw, "", null, null, ParseConfidence.LOW)
        }

        val lastHyphen = text.lastIndexOf('-')

        if (lastHyphen < 0) {
            return finalizeParsed(raw, text, null, scriptTag, ParseConfidence.MEDIUM)
        }

        val left = text.substring(0, lastHyphen).trim()
        val right = text.substring(lastHyphen + 1).trim()

        if (left.isEmpty()) {
            val amount = if (right.isNotEmpty()) parseAmount(right, scriptTag) else null
            return ParsedLine(raw, text, amount, null, ParseConfidence.LOW)
        }

        val amount = if (right.isNotEmpty()) parseAmount(right, scriptTag) else null
        val confidence = when {
            amount != null && amount in 1.0..10000.0 -> ParseConfidence.HIGH
            else -> ParseConfidence.MEDIUM
        }

        return finalizeParsed(raw, left, amount, scriptTag, confidence)
    }

    private fun finalizeParsed(
        raw: String,
        displayText: String,
        lineTotal: Double?,
        scriptTag: String,
        confidence: ParseConfidence
    ): ParsedLine {
        val extracted = extractTrailingQuantity(displayText, scriptTag)
        val matchHint = extracted?.nameHint?.takeIf { it.isNotBlank() }
            ?: computeMatchHint(displayText, scriptTag)
        return ParsedLine(
            raw = raw,
            displayText = displayText,
            lineTotal = lineTotal,
            matchHint = matchHint,
            confidence = confidence,
            parsedQuantity = extracted?.quantity,
            parsedUnitLabel = extracted?.unitLabel
        )
    }

    private data class ExtractedQuantity(
        val quantity: Double,
        val unitLabel: String,
        val nameHint: String
    )

    private fun extractTrailingQuantity(
        displayText: String,
        scriptTag: String = ScriptLanguages.DEFAULT_TAG
    ): ExtractedQuantity? {
        extractXQuantity(displayText, scriptTag)?.let { return it }
        val normalized = normalizeDigits(displayText, scriptTag)
        val withUnit = quantityWithUnitRegex(scriptTag)
        withUnit.find(normalized)?.let { match ->
            val qty = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
            if (qty <= 0) return null
            val unitLabel = normalizeUnitLabel(match.groupValues[2]) ?: return null
            val nameHint = displayText.substring(0, match.range.first).trim()
            if (nameHint.isBlank()) return null
            return ExtractedQuantity(qty, unitLabel, nameHint)
        }
        val countOnly = Regex("""[\s\u00A0]*(\d[\d.,]*)\s*$""")
        countOnly.find(normalized)?.let { match ->
            val qty = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
            if (qty <= 0) return null
            val nameHint = displayText.substring(0, match.range.first).trim()
            if (nameHint.isBlank()) return null
            return ExtractedQuantity(qty, "piece", nameHint)
        }
        return null
    }

    private fun extractXQuantity(
        displayText: String,
        scriptTag: String
    ): ExtractedQuantity? {
        val normalized = normalizeDigits(displayText, scriptTag)
        xSuffixRegex.find(normalized)?.let { match ->
            return xQuantityFromMatch(displayText, match, isPrefix = false)
        }
        xPrefixRegex.find(normalized)?.let { match ->
            return xQuantityFromMatch(displayText, match, isPrefix = true)
        }
        return null
    }

    private fun xQuantityFromMatch(
        displayText: String,
        match: MatchResult,
        isPrefix: Boolean
    ): ExtractedQuantity? {
        val qty = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (qty <= 0) return null
        val unitRaw = match.groupValues.getOrNull(2)?.trim().orEmpty()
        val unitLabel = if (unitRaw.isEmpty()) "piece" else normalizeUnitLabel(unitRaw) ?: return null
        val nameHint = if (isPrefix) {
            displayText.substring(match.range.last + 1).trim()
        } else {
            displayText.substring(0, match.range.first).trim()
        }
        if (nameHint.isBlank()) return null
        return ExtractedQuantity(qty, unitLabel, nameHint)
    }

    private fun quantityWithUnitRegex(scriptTag: String): Regex {
        val scriptUnits = unitPattern(scriptTag)
        val units = if (scriptUnits.isEmpty()) latinUnits else "$scriptUnits|$latinUnits"
        return Regex(
            """[\s\u00A0]*(\d[\d.,]*)\s*($units)\s*$""",
            setOf(RegexOption.IGNORE_CASE)
        )
    }

    private fun unitPattern(scriptTag: String): String = when (scriptTag) {
        "ta" -> """கி(?:லோ)?\.?|கிலோ|மி\.?லி|லிட்டர்"""
        "hi" -> """क(?:ि)?(?:\.?)?(?:लो|ग्रा)|किलो|ग्राम|ग्रा|ली|लिटर|ल"""
        "te" -> """క(?:ి)?(?:\.?)?(?:లో|గ్రా)|కిలో|గ్రామ|మి\.?లి|లీ|లిటర"""
        "kn" -> """ಕ(?:ಿ)?(?:\.?)?(?:ಲೋ|ಗ್ರಾ)|ಕಿಲೋ|ಗ್ರಾಮ|ಮಿ\.?ಲಿ|ಲೀ|ಲಿಟರ"""
        "ml" -> """ക(?:ി)?(?:\.?)?(?:ലോ|ഗ്രാ)|കിലോ|ഗ്രാം|മി\.?ലി|ലീ|ലിറ്റർ"""
        "bn" -> """ক(?:ি)?(?:\.?)?(?:লো|গ্রা)|কিলো|গ্রাম|মি\.?লি|লি|লিটার"""
        "gu" -> """ક(?:િ)?(?:\.?)?(?:લો|ગ્રા)|કિલો|ગ્રામ|મિ\.?લી|લી|લિટર"""
        else -> ""
    }

    private fun normalizeUnitLabel(raw: String): String? {
        val u = raw.trim()
        val lower = u.lowercase()
        if (u.contains("கி") || u.contains("கிலோ")) return "kg"
        if (u.contains("மி") && u.contains("லி")) return "ml"
        if (u.contains("லிட")) return "litre"
        return when {
            lower in listOf("kg", "kgs") -> "kg"
            Regex("""(?i)^g(?:ram)?s?|gm$""").matches(lower) -> "g"
            lower == "ml" -> "ml"
            Regex("""(?i)^l(?:tr|itre?s?)?$""").matches(lower) -> "litre"
            Regex("""(?i)^kgs?$""").matches(lower) -> "kg"
            else -> null
        }
    }

    fun unitTypeFromLabel(label: String?): String = when (label?.lowercase()) {
        "kg", "g" -> UnitType.WEIGHT
        "ml", "litre", "l" -> UnitType.VOLUME
        else -> UnitType.COUNT
    }

    /** Chip hint: `x2 · ₹40`, `x250g · ₹60`, etc. */
    fun formatParsedHint(parsed: ParsedLine): String {
        val parts = mutableListOf<String>()
        parsed.parsedQuantity?.let { q ->
            parts.add(formatXQuantityLabel(q, parsed.parsedUnitLabel))
        }
        parsed.lineTotal?.let { parts.add(formatRsHint(it)) }
        return parts.joinToString(" \u00B7 ")
    }

    private fun formatRsHint(d: Double): String =
        if (d == d.toLong().toDouble()) "\u20B9${d.toLong()}" else "\u20B9${"%.2f".format(d)}"

    private fun formatXQuantityLabel(qty: Double, unitLabel: String?): String {
        val num = if (qty == qty.toLong().toDouble()) qty.toLong().toString() else qty.toString()
        return when (unitLabel?.lowercase()) {
            null, "piece" -> "x$num"
            "g" -> "x${num}g"
            "kg" -> "x${num}kg"
            "ml" -> "x${num}ml"
            "litre", "l" -> "x${num}l"
            else -> "x$num$unitLabel"
        }
    }

    private fun computeMatchHint(displayText: String, scriptTag: String): String {
        val stripped = weightSuffixRegex(scriptTag).replace(displayText, "").trim()
        return if (stripped.isEmpty()) displayText else stripped
    }

    fun normalizeDigits(s: String, scriptTag: String): String {
        val map = digitMapFor(scriptTag)
        return s.map { map[it] ?: it }.joinToString("")
    }

    private fun digitMapFor(tag: String): Map<Char, Char> = when (tag) {
        "ta" -> tamilDigitMap
        "hi" -> devanagariDigitMap
        "bn" -> bengaliDigitMap
        "te" -> teluguDigitMap
        "kn" -> kannadaDigitMap
        "ml" -> malayalamDigitMap
        "gu" -> gujaratiDigitMap
        else -> tamilDigitMap + devanagariDigitMap + bengaliDigitMap +
            teluguDigitMap + kannadaDigitMap + malayalamDigitMap + gujaratiDigitMap
    }

    private fun digitRange(start: Char, end: Char): Map<Char, Char> =
        (start..end).mapIndexed { i, c -> c to ('0' + i) }.toMap()

    fun weightSuffixRegex(scriptTag: String): Regex = quantityWithUnitRegex(scriptTag)
}
