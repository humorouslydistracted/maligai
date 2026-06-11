package com.maligai.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptLanguageReceiptTest {

    @Test
    fun `supports local script receipt for all regional ML Kit languages`() {
        listOf("ta", "hi", "te", "kn", "ml", "bn", "gu").forEach { tag ->
            assertTrue("Expected bitmap receipts for $tag", ScriptLanguages.supportsLocalScriptReceipt(tag))
        }
    }

    @Test
    fun `does not use bitmap receipts for English handwriting tag`() {
        assertFalse(ScriptLanguages.supportsLocalScriptReceipt(ScriptLanguages.EN_TAG))
    }

    @Test
    fun `default receipt mode is local image for regional scripts`() {
        assertTrue(ScriptLanguages.defaultReceiptNameMode("ta") == ReceiptNameMode.LOCAL_IMAGE)
        assertTrue(ScriptLanguages.defaultReceiptNameMode("hi") == ReceiptNameMode.LOCAL_IMAGE)
        assertTrue(ScriptLanguages.defaultReceiptNameMode("en") == ReceiptNameMode.ENGLISH)
    }

    @Test
    fun `should print local script when mode and language match`() {
        val settings = AppSettings(
            primaryScriptTag = "hi",
            receiptNameMode = ReceiptNameMode.LOCAL_IMAGE
        )
        assertTrue(settings.shouldPrintLocalScriptReceipt())
    }

    @Test
    fun `should not print local script when English names selected`() {
        val settings = AppSettings(
            primaryScriptTag = "hi",
            receiptNameMode = ReceiptNameMode.ENGLISH
        )
        assertFalse(settings.shouldPrintLocalScriptReceipt())
    }
}
