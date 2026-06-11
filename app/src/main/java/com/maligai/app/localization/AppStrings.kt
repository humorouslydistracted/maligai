package com.maligai.app.localization

import android.util.Log
import com.maligai.app.PeriodTab
import com.maligai.app.UnitType
import com.maligai.app.localization.strings.localeStringsBn
import com.maligai.app.localization.strings.localeStringsEn
import com.maligai.app.localization.strings.localeStringsGu
import com.maligai.app.localization.strings.localeStringsHi
import com.maligai.app.localization.strings.localeStringsKn
import com.maligai.app.localization.strings.localeStringsMl
import com.maligai.app.localization.strings.localeStringsTa
import com.maligai.app.localization.strings.localeStringsTe
import java.util.Locale

object AppStrings {

    private const val TAG = "AppStrings"

    private val tables: Map<String, Map<StringKey, String>> = mapOf(
        "en" to localeStringsEn,
        "ta" to localeStringsTa,
        "hi" to localeStringsHi,
        "te" to localeStringsTe,
        "kn" to localeStringsKn,
        "ml" to localeStringsMl,
        "bn" to localeStringsBn,
        "gu" to localeStringsGu
    )

    fun get(key: StringKey, localeTag: String, vararg formatArgs: Any?): String {
        val resolved = UiLocales.resolveTag(localeTag)
        val template = tables[resolved]?.get(key)
            ?: tables[UiLocales.DEFAULT_TAG]?.get(key)
            ?: run {
                Log.w(TAG, "Missing string key $key for locale $resolved")
                key.name
            }
        return if (formatArgs.isEmpty()) template
        else String.format(Locale.US, template, *formatArgs)
    }

    fun periodTabLabel(tab: PeriodTab, localeTag: String): String = when (tab) {
        PeriodTab.TODAY -> get(StringKey.PeriodToday, localeTag)
        PeriodTab.WEEK -> get(StringKey.PeriodWeek, localeTag)
        PeriodTab.MONTH -> get(StringKey.PeriodMonth, localeTag)
        PeriodTab.ALL -> get(StringKey.PeriodAll, localeTag)
    }

    fun unitTypeDisplayLabel(type: String, localeTag: String): String = when (type) {
        UnitType.WEIGHT -> get(StringKey.UnitWeight, localeTag)
        UnitType.VOLUME -> get(StringKey.UnitVolume, localeTag)
        UnitType.COUNT -> get(StringKey.UnitPiece, localeTag)
        else -> type
    }

    fun defaultUnitLabel(type: String, localeTag: String): String = when (type) {
        UnitType.WEIGHT -> get(StringKey.UnitKg, localeTag)
        UnitType.VOLUME -> get(StringKey.UnitLitre, localeTag)
        else -> get(StringKey.UnitPiece, localeTag)
    }

    fun securityQuestions(localeTag: String): List<String> = listOf(
        get(StringKey.SecQ_MaidenName, localeTag),
        get(StringKey.SecQ_BestFriend, localeTag),
        get(StringKey.SecQ_FavoriteSport, localeTag),
        get(StringKey.SecQ_BornCity, localeTag),
        get(StringKey.SecQ_PetName, localeTag)
    )

    fun allLocales(): Set<String> = tables.keys

    fun hasAllKeys(localeTag: String): Boolean {
        val resolved = UiLocales.resolveTag(localeTag)
        val en = tables[UiLocales.DEFAULT_TAG] ?: return false
        val locale = tables[resolved] ?: return false
        return StringKey.entries.all { key ->
            locale.containsKey(key) || en.containsKey(key)
        }
    }
}
