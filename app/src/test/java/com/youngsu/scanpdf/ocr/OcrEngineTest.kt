package com.youngsu.scanpdf.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** OCR 결과 다듬기와 문자 판별 — 화면에 보여 줄 글자를 정리하는 규칙 */
class OcrEngineTest {

    @Test
    fun `tidy trims trailing spaces and repeated blank lines`() {
        val raw = "  영수증   \r\n 결제 금액 12,000원   \n\n\n\n 승인 번호 3391  \n"
        val tidy = OcrEngine.tidy(raw)
        assertEquals("영수증\n 결제 금액 12,000원\n\n 승인 번호 3391", tidy)
    }

    @Test
    fun `tidy keeps normal line breaks`() {
        assertEquals("a\nb\nc", OcrEngine.tidy("a\nb\nc"))
    }

    @Test
    fun `useful count ignores spaces and punctuation`() {
        assertEquals(5, OcrEngine.usefulCount("A b, c!\n12"))
        assertEquals(0, OcrEngine.usefulCount(" ,.!? "))
    }

    @Test
    fun `script count tells hangul apart from latin`() {
        val korean = "안녕하세요 Hello"
        assertTrue(OcrEngine.scriptCount(korean, OcrEngine.Script.KOREAN) >= 5)
        assertEquals(5, OcrEngine.scriptCount(korean, OcrEngine.Script.LATIN))

        val japanese = "こんにちは世界"
        assertTrue(OcrEngine.scriptCount(japanese, OcrEngine.Script.JAPANESE) >= 5)
        assertTrue(OcrEngine.scriptCount(japanese, OcrEngine.Script.CHINESE) >= 2)
    }

    @Test
    fun `result reports emptiness for blank text`() {
        assertTrue(OcrEngine.Result("   ", OcrEngine.Script.AUTO, OcrEngine.Script.LATIN).isEmpty)
        assertTrue(!OcrEngine.Result("영수증", OcrEngine.Script.AUTO, OcrEngine.Script.KOREAN).isEmpty)
    }
}
