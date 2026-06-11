package com.maligai.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maligai.app.localization.AppStrings
import com.maligai.app.localization.LocalAppLocale
import com.maligai.app.localization.StringKey
import com.maligai.app.localization.string
import java.util.Locale
import kotlin.math.abs

data class QuantityOption(val label: String, val value: Double)

object QuantityPresets {

    fun quickOptions(unitType: String, localeTag: String = "en"): List<QuantityOption> = when (unitType) {
        UnitType.WEIGHT -> listOf(
            QuantityOption(formatQuantityDisplay(0.1, UnitType.WEIGHT, localeTag), 0.1),
            QuantityOption(formatQuantityDisplay(0.2, UnitType.WEIGHT, localeTag), 0.2),
            QuantityOption(formatQuantityDisplay(0.25, UnitType.WEIGHT, localeTag), 0.25),
            QuantityOption(formatQuantityDisplay(0.5, UnitType.WEIGHT, localeTag), 0.5),
            QuantityOption(formatQuantityDisplay(0.75, UnitType.WEIGHT, localeTag), 0.75),
            QuantityOption(formatQuantityDisplay(1.0, UnitType.WEIGHT, localeTag), 1.0)
        )
        UnitType.VOLUME -> listOf(
            QuantityOption(formatQuantityDisplay(0.1, UnitType.VOLUME, localeTag), 0.1),
            QuantityOption(formatQuantityDisplay(0.2, UnitType.VOLUME, localeTag), 0.2),
            QuantityOption(formatQuantityDisplay(0.25, UnitType.VOLUME, localeTag), 0.25),
            QuantityOption(formatQuantityDisplay(0.5, UnitType.VOLUME, localeTag), 0.5),
            QuantityOption(formatQuantityDisplay(1.0, UnitType.VOLUME, localeTag), 1.0)
        )
        else -> listOf(
            QuantityOption("0.25", 0.25),
            QuantityOption("0.5", 0.5),
            QuantityOption("0.75", 0.75),
            QuantityOption("1", 1.0),
            QuantityOption("2", 2.0),
            QuantityOption("3", 3.0),
            QuantityOption("4", 4.0),
            QuantityOption("5", 5.0)
        )
    }

    fun scrollOptions(unitType: String, localeTag: String = "en"): List<QuantityOption> = when (unitType) {
        UnitType.WEIGHT -> buildWeightScroll(localeTag)
        UnitType.VOLUME -> buildVolumeScroll(localeTag)
        else -> (6..100).map { QuantityOption(it.toString(), it.toDouble()) }
    }

    /** Quick presets followed by extended range, deduped by value — single horizontal swipe. */
    fun allOptions(unitType: String, localeTag: String = "en"): List<QuantityOption> {
        val seen = linkedSetOf<Double>()
        val merged = mutableListOf<QuantityOption>()
        for (opt in quickOptions(unitType, localeTag) + scrollOptions(unitType, localeTag)) {
            if (seen.add(opt.value)) merged.add(opt)
        }
        return merged
    }

    private fun buildWeightScroll(localeTag: String): List<QuantityOption> {
        val values = linkedSetOf<Double>()
        var v = 1.25
        while (v <= 5.0 + 0.001) {
            values.add((v * 100).toInt() / 100.0)
            v += 0.25
        }
        v = 5.5
        while (v <= 10.0 + 0.001) {
            values.add((v * 10).toInt() / 10.0)
            v += 0.5
        }
        v = 11.0
        while (v <= 50.0 + 0.001) {
            values.add(v.toInt().toDouble())
            v += 1.0
        }
        return values.map { qty ->
            QuantityOption(formatQuantityDisplay(qty, UnitType.WEIGHT, localeTag), qty)
        }
    }

    private fun buildVolumeScroll(localeTag: String): List<QuantityOption> {
        val values = linkedSetOf<Double>()
        var v = 1.25
        while (v <= 5.0 + 0.001) {
            values.add((v * 100).toInt() / 100.0)
            v += 0.25
        }
        v = 6.0
        while (v <= 20.0 + 0.001) {
            values.add(v.toInt().toDouble())
            v += 1.0
        }
        return values.map { qty ->
            QuantityOption(formatQuantityDisplay(qty, UnitType.VOLUME, localeTag), qty)
        }
    }
}

fun formatQuantityDisplay(qty: Double, unitType: String, localeTag: String = "en"): String {
    if (qty <= 0) return ""
    return when (unitType) {
        UnitType.WEIGHT -> formatWeightDisplay(qty, localeTag)
        UnitType.VOLUME -> formatVolumeDisplay(qty, localeTag)
        else -> if (qty == qty.toLong().toDouble()) qty.toLong().toString()
        else String.format(Locale.US, "%.2f", qty)
    }
}

private fun formatWeightDisplay(kg: Double, localeTag: String): String {
    val kgLabel = AppStrings.defaultUnitLabel(UnitType.WEIGHT, localeTag)
    if (kg < 1.0) {
        val g = (kg * 1000).toInt()
        return if (g % 1000 == 0) "${g / 1000} $kgLabel" else "$g g"
    }
    return if (kg == kg.toLong().toDouble()) "${kg.toLong()} $kgLabel"
    else String.format(Locale.US, "%.2f %s", kg, kgLabel)
}

private fun formatVolumeDisplay(litre: Double, localeTag: String): String {
    val litreLabel = AppStrings.defaultUnitLabel(UnitType.VOLUME, localeTag)
    if (litre < 1.0) {
        val ml = (litre * 1000).toInt()
        return if (ml % 1000 == 0) "${ml / 1000} $litreLabel" else "$ml ml"
    }
    return if (litre == litre.toLong().toDouble()) "${litre.toLong()} $litreLabel"
    else String.format(Locale.US, "%.2f %s", litre, litreLabel)
}

fun parseQuantityInput(text: String, unitType: String): Double? {
    val raw = text.trim()
    if (raw.isEmpty()) return null

    when (unitType) {
        UnitType.WEIGHT -> return parseWeightInput(raw)
        UnitType.VOLUME -> return parseVolumeInput(raw)
        else -> return parseCountInput(raw)
    }
}

private fun parseWeightInput(raw: String): Double? {
    val lower = raw.lowercase(Locale.US).replace(" ", "")
    val kgMatch = Regex("""^(\d+(?:\.\d+)?)(kg|kgs|kilo|kilos?)$""").matchEntire(lower)
    if (kgMatch != null) {
        return kgMatch.groupValues[1].toDoubleOrNull()?.takeIf { it > 0 }
    }
    val gMatch = Regex("""^(\d+(?:\.\d+)?)(g|gm|gms|gram|grams?)$""").matchEntire(lower)
    if (gMatch != null) {
        val grams = gMatch.groupValues[1].toDoubleOrNull() ?: return null
        if (grams <= 0) return null
        return grams / 1000.0
    }
    val plain = raw.toDoubleOrNull() ?: return null
    return plain.takeIf { it > 0 }
}

private fun parseVolumeInput(raw: String): Double? {
    val lower = raw.lowercase(Locale.US).replace(" ", "")
    val lMatch = Regex("""^(\d+(?:\.\d+)?)(l|ltr|litre|litres?|liter|liters?)$""").matchEntire(lower)
    if (lMatch != null) {
        return lMatch.groupValues[1].toDoubleOrNull()?.takeIf { it > 0 }
    }
    val mlMatch = Regex("""^(\d+(?:\.\d+)?)(ml|millilitre|millilitres?)$""").matchEntire(lower)
    if (mlMatch != null) {
        val ml = mlMatch.groupValues[1].toDoubleOrNull() ?: return null
        if (ml <= 0) return null
        return ml / 1000.0
    }
    val plain = raw.toDoubleOrNull() ?: return null
    return plain.takeIf { it > 0 }
}

private fun parseCountInput(raw: String): Double? {
    val fractionMap = mapOf(
        "¼" to 0.25, "1/4" to 0.25,
        "½" to 0.5, "1/2" to 0.5,
        "¾" to 0.75, "3/4" to 0.75
    )
    fractionMap[raw]?.let { return it }
    val frac = Regex("""^(\d+)\s*/\s*(\d+)$""").matchEntire(raw)
    if (frac != null) {
        val num = frac.groupValues[1].toDoubleOrNull() ?: return null
        val den = frac.groupValues[2].toDoubleOrNull() ?: return null
        if (den <= 0) return null
        val v = num / den
        return v.takeIf { it > 0 }
    }
    val v = raw.toDoubleOrNull() ?: return null
    return v.takeIf { it > 0 }
}

fun customQuantityHint(unitType: String, localeTag: String = "en"): String = when (unitType) {
    UnitType.WEIGHT -> AppStrings.get(StringKey.CustomQtyHintWeight, localeTag)
    UnitType.VOLUME -> AppStrings.get(StringKey.CustomQtyHintVolume, localeTag)
    else -> AppStrings.get(StringKey.CustomQtyHintCount, localeTag)
}

@Composable
fun QuantityPicker(
    unitType: String,
    unitLabel: String,
    selectedQty: Double,
    onQtyChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    showSelectedLine: Boolean = true,
    compact: Boolean = false,
    enabled: Boolean = true,
    resetSignal: Int = 0
) {
    val localeTag = LocalAppLocale.current
    val options = remember(unitType, localeTag) { QuantityPresets.allOptions(unitType, localeTag) }
    var customInput by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            customInput = ""
            customError = null
        }
    }

    fun applyQty(qty: Double) {
        customInput = ""
        customError = null
        onQtyChange(qty)
    }

    val gap = if (compact) 4.dp else 8.dp

    Column(modifier.alpha(if (enabled) 1f else 0.45f)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            options.forEach { opt ->
                val selected = selectedQty > 0 && abs(opt.value - selectedQty) < 0.001
                OutlinedButton(
                    onClick = { applyQty(opt.value) },
                    enabled = enabled,
                    contentPadding = PaddingValues(
                        horizontal = if (compact) 6.dp else 8.dp,
                        vertical = if (compact) 2.dp else 4.dp
                    )
                ) {
                    Text(
                        opt.label,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(gap))
        if (showSelectedLine) {
            Text(
                string(
                    StringKey.SelectedQuantity,
                    formatQuantityDisplay(selectedQty, unitType, localeTag),
                    unitLabel
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(gap))
        }
        OutlinedTextField(
            value = customInput,
            onValueChange = { input ->
                if (!enabled) return@OutlinedTextField
                customInput = input
                val parsed = parseQuantityInput(input, unitType)
                if (input.isBlank()) {
                    customError = null
                } else if (parsed == null) {
                    customError = AppStrings.get(
                        StringKey.InvalidFormatHint,
                        localeTag,
                        customQuantityHint(unitType, localeTag)
                    )
                } else {
                    customError = null
                    onQtyChange(parsed)
                }
            },
            label = { Text(string(StringKey.CustomQuantity)) },
            placeholder = { Text(customQuantityHint(unitType, localeTag)) },
            singleLine = true,
            readOnly = !enabled,
            enabled = enabled,
            isError = customError != null,
            supportingText = customError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
