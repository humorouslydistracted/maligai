package com.maligai.app.localization

data class UiLocale(
    val tag: String,
    val nativeName: String,
    val englishName: String
)

object UiLocales {
    const val DEFAULT_TAG = "en"

    private val all = listOf(
        UiLocale("en", "English", "English"),
        UiLocale("ta", "\u0ba4\u0bae\u0bbf\u0bb4\u0bcd", "Tamil"),
        UiLocale("hi", "\u0939\u093f\u0928\u094d\u0926\u0940", "Hindi"),
        UiLocale("te", "\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41", "Telugu"),
        UiLocale("kn", "\u0c95\u0ca8\u0ccd\u0ca8\u0ca1", "Kannada"),
        UiLocale("ml", "\u0d2e\u0d32\u0d2f\u0d3e\u0d33\u0d02", "Malayalam"),
        UiLocale("bn", "\u09ac\u09be\u0982\u09b2\u09be", "Bengali"),
        UiLocale("gu", "\u0a97\u0ac1\u0a9c\u0ab0\u0abe\u0aa4\u0ac0", "Gujarati")
    )

    fun supported(): List<UiLocale> = all

    fun byTag(tag: String): UiLocale? = all.find { it.tag == tag }

    fun nativeNameForTag(tag: String): String =
        byTag(resolveTag(tag))?.nativeName ?: tag.uppercase()

    /** Marathi UI falls back to Hindi strings. */
    fun resolveTag(tag: String): String = when (tag) {
        "mr" -> "hi"
        else -> tag
    }

    fun isSupported(tag: String): Boolean =
        byTag(tag) != null || tag == "mr"

    fun defaultForDevice(): String {
        val device = java.util.Locale.getDefault().language
        return if (isSupported(device)) device else DEFAULT_TAG
    }
}
