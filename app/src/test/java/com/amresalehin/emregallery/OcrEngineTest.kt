package com.amresalehin.emregallery

import com.amresalehin.emregallery.ocr.OcrEngine
import com.amresalehin.emregallery.ui.OcrState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrEngineTest {

    @Test
    fun testOcrEngineDefaultsAndFromId() {
        assertEquals(OcrEngine.TESSERACT, OcrEngine.fromId("tesseract"))
        assertEquals(OcrEngine.TESSERACT, OcrEngine.fromId("TESSERACT"))
        assertEquals(OcrEngine.GOOGLE_ML_KIT, OcrEngine.fromId("google_mlkit"))
        assertEquals(OcrEngine.GOOGLE_ML_KIT, OcrEngine.fromId("GOOGLE_MLKIT"))
        // Unknown defaults to Tesseract
        assertEquals(OcrEngine.TESSERACT, OcrEngine.fromId("unknown_engine"))
        assertEquals(OcrEngine.TESSERACT, OcrEngine.fromId(null))
    }

    @Test
    fun testOcrStateEngineField() {
        val defaultState = OcrState()
        assertEquals("tesseract", defaultState.engine)

        val mlKitState = defaultState.copy(engine = "google_mlkit")
        assertEquals("google_mlkit", mlKitState.engine)
        assertTrue(mlKitState.engine.equals("google_mlkit", ignoreCase = true))
    }

    @Test
    fun testEngineProperties() {
        assertEquals("Google ML Kit", OcrEngine.GOOGLE_ML_KIT.title)
        assertEquals("ML Kit", OcrEngine.GOOGLE_ML_KIT.shortName)
        assertTrue(OcrEngine.GOOGLE_ML_KIT.description.contains("on-device", ignoreCase = true))

        assertEquals("Tesseract", OcrEngine.TESSERACT.title)
        assertEquals("Tesseract", OcrEngine.TESSERACT.shortName)
    }
}
