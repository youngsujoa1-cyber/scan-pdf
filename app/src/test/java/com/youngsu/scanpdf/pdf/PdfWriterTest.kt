package com.youngsu.scanpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PDF 작성기 — 페이지 수·교차 참조(xref) 오프셋이 맞는 파일을 만드는지 */
class PdfWriterTest {

    private fun fakeJpeg(size: Int): ByteArray {
        // SOI + APP0(JFIF) + SOF0(1x1, 3성분) + EOI 만 갖춘 최소 머리
        val header = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08, 0x00, 0x01, 0x00, 0x01, 0x03,
            0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
            0xFF.toByte(), 0xD9.toByte()
        )
        return if (size <= header.size) header else header + ByteArray(size - header.size) { 0x20 }
    }

    @Test
    fun `jpeg header is read back`() {
        val info = PdfWriter.readJpegInfo(fakeJpeg(64))
        assertEquals(1, info?.width)
        assertEquals(1, info?.height)
        assertEquals(3, info?.components)
    }

    @Test
    fun `written pdf has one page object per page`() {
        val pages = listOf(
            PdfWriter.Page(595f, 842f, fakeJpeg(300)),
            PdfWriter.Page(842f, 595f, fakeJpeg(400)),
            PdfWriter.Page(595f, 842f, fakeJpeg(500))
        )
        val bytes = PdfWriter.write(pages, title = "테스트 문서")
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.7"))
        assertTrue(text.contains("/Count 3"))
        assertTrue(text.contains("/MediaBox [0 0 595 842]"))
        assertTrue(text.contains("/MediaBox [0 0 842 595]"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("startxref"))
        // 한글 제목은 UTF-16BE 16진 문자열로 들어간다
        assertTrue(text.contains("<FEFF"))
    }

    @Test
    fun `xref offsets point at their objects`() {
        val bytes = PdfWriter.write(listOf(PdfWriter.Page(595f, 842f, fakeJpeg(200))))
        val text = String(bytes, Charsets.ISO_8859_1)
        val startxref = text.substringAfterLast("startxref").trim().lineSequence().first().toInt()
        val xref = text.substring(startxref)
        assertTrue(xref.startsWith("xref"))
        val offsets = Regex("(\\d{10}) 00000 n").findAll(xref).map { it.groupValues[1].toInt() }.toList()
        assertTrue(offsets.isNotEmpty())
        offsets.forEach { offset ->
            val head = text.substring(offset, offset + 20)
            assertTrue("object at $offset should start an object, was $head", Regex("^\\d+ 0 obj").containsMatchIn(head))
        }
    }

    @Test
    fun `page size keeps the image aspect ratio inside A4`() {
        val portrait = PdfWriter.pageSizeFor(1000, 1400)
        assertEquals(PdfWriter.A4_LONG_PT, portrait.second, 0.01f)
        assertEquals(PdfWriter.A4_LONG_PT * 1000 / 1400, portrait.first, 0.5f)

        val landscape = PdfWriter.pageSizeFor(2000, 1000)
        assertEquals(PdfWriter.A4_LONG_PT, landscape.first, 0.01f)
        assertEquals(PdfWriter.A4_LONG_PT / 2f, landscape.second, 0.5f)
    }

    @Test
    fun `pdf date uses the given time zone`() {
        assertEquals("D:20261129011305+09'00'", PdfWriter.pdfDate(2026, 11, 29, 1, 13, 5, 540))
        assertEquals("D:20261128161305-08'00'", PdfWriter.pdfDate(2026, 11, 28, 16, 13, 5, -480))
    }
}
