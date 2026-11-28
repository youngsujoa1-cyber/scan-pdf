package com.youngsu.scanpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 문서 보정 필터 — 그림자 제거·흑백·회색이 순수 Kotlin 으로 맞게 도는지 */
class FiltersTest {

    private fun gray(value: Int) = 0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
    private fun lumaOf(color: Int): Int = (color shr 16) and 0xFF

    /** 종이가 왼쪽은 밝고 오른쪽은 그림자로 어두운 사진 + 가운데 검은 글자 줄 */
    private fun shadowedPage(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // 오른쪽으로 갈수록 120 까지 어두워지는 그림자
                val shadow = (255 - (x * 135 / width)).coerceIn(110, 255)
                val text = y in (height / 2 - 2)..(height / 2 + 1) && x in 10..(width - 10)
                pixels[y * width + x] = gray(if (text) 45 else shadow)
            }
        }
        return pixels
    }

    @Test
    fun `shadow removal turns shadowed paper white and keeps text dark`() {
        val width = 200
        val height = 120
        val pixels = shadowedPage(width, height)
        Filters.apply(Filters.Kind.SHARP, pixels, width, height)

        // 그림자 쪽 종이(오른쪽 아래)가 흰색에 가까워진다
        val shadowPaper = pixels[(height - 6) * width + (width - 6)]
        assertTrue("shadowed paper should become light, was ${lumaOf(shadowPaper)}", lumaOf(shadowPaper) > 230)

        // 밝은 쪽 종이도 그대로 흰색
        val brightPaper = pixels[4 * width + 4]
        assertTrue("bright paper should stay light, was ${lumaOf(brightPaper)}", lumaOf(brightPaper) > 230)

        // 글자는 여전히 어둡다 (지워지지 않음)
        val text = pixels[(height / 2) * width + width / 2]
        assertTrue("text should stay dark, was ${lumaOf(text)}", lumaOf(text) < 120)
    }

    @Test
    fun `black and white filter produces only black or white`() {
        val width = 120
        val height = 80
        val pixels = shadowedPage(width, height)
        Filters.apply(Filters.Kind.BW, pixels, width, height)
        pixels.forEach { color ->
            val value = lumaOf(color)
            assertTrue("value $value should be 0 or 255", value == 0 || value == 255)
        }
    }

    @Test
    fun `grayscale keeps channel values equal`() {
        val pixels = intArrayOf(0xFF3366CC.toInt(), 0xFFAA3300.toInt(), 0xFFFFFFFF.toInt())
        Filters.apply(Filters.Kind.GRAY, pixels, 3, 1)
        pixels.forEach { color ->
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            assertEquals(r, g)
            assertEquals(g, b)
        }
    }

    @Test
    fun `original filter leaves pixels untouched`() {
        val pixels = intArrayOf(0xFF123456.toInt(), 0xFF00FF00.toInt())
        val copy = pixels.copyOf()
        Filters.apply(Filters.Kind.ORIGINAL, pixels, 2, 1)
        assertEquals(copy.toList(), pixels.toList())
    }

    @Test
    fun `levels finds black and white points of a histogram`() {
        val pixels = IntArray(100) { index -> gray(if (index < 90) 250 else 20) }
        val (black, white) = Filters.levels(pixels).let { it[0] to it[1] }
        assertTrue("white point should be near 250, was $white", white > 220)
        assertTrue("black point should be low, was $black", black < 60)
    }
}
