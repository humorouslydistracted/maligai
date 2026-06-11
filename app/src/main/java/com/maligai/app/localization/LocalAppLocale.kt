package com.maligai.app.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

val LocalAppLocale = compositionLocalOf { UiLocales.DEFAULT_TAG }

@Composable
fun ProvideAppLocale(localeTag: String, content: @Composable () -> Unit) {
    val resolved = UiLocales.resolveTag(localeTag)
    CompositionLocalProvider(LocalAppLocale provides resolved, content = content)
}

@Composable
fun string(key: StringKey, vararg formatArgs: Any?): String {
    val tag = LocalAppLocale.current
    return remember(tag, key, formatArgs.toList()) {
        AppStrings.get(key, tag, *formatArgs)
    }
}
