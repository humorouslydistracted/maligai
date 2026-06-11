package com.maligai.app

import com.maligai.app.localization.AppStrings
import com.maligai.app.localization.StringKey
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStringsTest {

    @Test
    fun `every locale contains all string keys`() {
        val missingLocales = AppStrings.allLocales().filterNot { localeTag ->
            AppStrings.hasAllKeys(localeTag)
        }

        assertTrue(
            "Locales missing keys for ${StringKey.entries.size} StringKey values: $missingLocales",
            missingLocales.isEmpty()
        )
    }
}
