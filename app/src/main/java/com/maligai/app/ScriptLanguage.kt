package com.maligai.app

import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier

/**
 * A regional script supported at setup. English ("en") is always loaded separately;
 * the user picks exactly one entry from [pickerOptions].
 */
data class ScriptLanguage(
    val id: String,
    val displayName: String,
    val mlKitTag: String,
    val nativeSample: String,
    val canvasHint: String,
    val sharedModelNote: String? = null
)

object ScriptLanguages {

    const val DEFAULT_TAG = "ta"
    const val EN_TAG = "en"

    private val all = listOf(
        ScriptLanguage(
            id = "ta",
            displayName = "Tamil",
            mlKitTag = "ta",
            nativeSample = "\u0b85\u0bb0\u0bbf\u0b9a\u0bbf",
            canvasHint = "\u0baa\u0bca\u0bb0\u0bc1\u0bb3\u0bcd  x2 / x250gm  -  \u0ba4\u0bca\u0b95\u0bc8"
        ),
        ScriptLanguage(
            id = "hi",
            displayName = "Hindi",
            mlKitTag = "hi",
            nativeSample = "\u091a\u093e\u0935\u0932",
            canvasHint = "\u0938\u093e\u092e\u093e\u0928  x2 / x250gm  -  \u0930\u093e\u0936\u093f"
        ),
        ScriptLanguage(
            id = "te",
            displayName = "Telugu",
            mlKitTag = "te",
            nativeSample = "\u0c05\u0c30\u0c3f\u0c38\u0c3f",
            canvasHint = "\u0c35\u0c38\u0c4d\u0c24\u0c41\u0c35\u0c41  x2 / x250gm  -  \u0c24\u0c15\u0c4d\u0c15\u0c41\u0c35"
        ),
        ScriptLanguage(
            id = "kn",
            displayName = "Kannada",
            mlKitTag = "kn",
            nativeSample = "\u0c85\u0c95\u0ccd\u0c95\u0cbf",
            canvasHint = "\u0cb5\u0cb8\u0ccd\u0ca4\u0cc1  x2 / x250gm  -  \u0cb0\u0cbe\u0cb6\u0cbf"
        ),
        ScriptLanguage(
            id = "ml",
            displayName = "Malayalam",
            mlKitTag = "ml",
            nativeSample = "\u0d05\u0d30\u0d3f\u0d38\u0d3f",
            canvasHint = "\u0d38\u0d3e\u0d27\u0d28\u0d02  x2 / x250gm  -  \u0d24\u0d15\u0d15\u0d4d\u0d15\u0d41\u0d35"
        ),
        ScriptLanguage(
            id = "bn",
            displayName = "Bengali",
            mlKitTag = "bn",
            nativeSample = "\u099a\u09be\u09b2",
            canvasHint = "\u099c\u09bf\u09a8\u09bf\u09b7  x2 / x250gm  -  \u099f\u09be\u0995\u09be"
        ),
        ScriptLanguage(
            id = "mr",
            displayName = "Marathi",
            mlKitTag = "hi",
            nativeSample = "\u0924\u093e\u0902\u0926\u0942\u0933",
            canvasHint = "\u0935\u0938\u094d\u0924\u0942  x2 / x250gm  -  \u0930\u0915\u094d\u0915\u092e",
            sharedModelNote = "Uses Hindi / Devanagari recognition model"
        ),
        ScriptLanguage(
            id = "gu",
            displayName = "Gujarati",
            mlKitTag = "gu",
            nativeSample = "\u0a9a\u0acb\u0a95\u0ab2\u0ac0",
            canvasHint = "\u0ab5\u0ab8\u0acd\u0aa4\u0ac1  x2 / x250gm  -  \u0ab0\u0a95\u0aae"
        )
    )

    /** Languages shown in the setup picker (unsupported ML Kit tags are hidden). */
    fun pickerOptions(): List<ScriptLanguage> =
        all.filter { isMlKitSupported(it.mlKitTag) }

    fun byPickerId(id: String): ScriptLanguage? = all.find { it.id == id }

    fun byMlKitTag(tag: String): ScriptLanguage? =
        all.find { it.mlKitTag == tag } ?: all.find { it.id == tag }

    fun displayNameForTag(tag: String): String =
        byMlKitTag(tag)?.displayName ?: tag.uppercase()

    fun isMlKitSupported(tag: String): Boolean =
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) != null

    /**
     * Bitmap receipt printing can render native scripts for every ML Kit regional language.
     * English-only shops keep text-mode receipts (Latin / ISO-8859-1).
     */
    fun supportsLocalScriptReceipt(tag: String): Boolean =
        tag != EN_TAG && isMlKitSupported(tag)

    /** Default receipt name mode for a handwriting script tag. */
    fun defaultReceiptNameMode(tag: String): String =
        if (supportsLocalScriptReceipt(tag)) ReceiptNameMode.LOCAL_IMAGE
        else ReceiptNameMode.ENGLISH

    fun defaultCanvasHint(): String = "item x2 / x250gm  -  amount"
}

fun AppSettings.usesLocalScriptReceipt(): Boolean =
    receiptNameMode == ReceiptNameMode.LOCAL_IMAGE ||
        receiptNameMode == ReceiptNameMode.TAMIL_IMAGE

fun AppSettings.shouldPrintLocalScriptReceipt(): Boolean =
    usesLocalScriptReceipt() && ScriptLanguages.supportsLocalScriptReceipt(primaryScriptTag)
