package com.youngsu.scanpdf.pdf

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * 아주 작은 PDF 작성기.
 *
 * 스캔한 페이지는 결국 "JPEG 사진 한 장"이므로, JPEG 바이트를 그대로
 * PDF 이미지 스트림(DCTDecode)으로 넣는다. 사진을 다시 압축하거나
 * 무손실(Flate)로 풀어 넣지 않아 페이지당 수백 KB 수준으로 유지된다.
 *
 * android.* 를 전혀 쓰지 않는 순수 Kotlin 이라 JVM 에서 그대로 검증할 수 있다.
 * (tools/logic-check 에서 생성한 PDF 를 MuPDF/PyMuPDF 로 다시 열어 확인한다)
 */
object PdfWriter {

    /** PDF 한 페이지. 폭·높이는 포인트(pt, 1/72인치), [jpeg] 는 그대로 들어갈 JPEG 바이트. */
    data class Page(
        val widthPt: Float,
        val heightPt: Float,
        val jpeg: ByteArray
    )

    /** JPEG 헤더에서 읽어 낸 정보. */
    data class JpegInfo(
        val width: Int,
        val height: Int,
        val components: Int,
        val bitsPerComponent: Int
    )

    /**
     * 페이지들을 하나의 PDF 로 만든다.
     *
     * [pages] 가 비어 있으면 빈 PDF(0페이지)를 만든다. 호출하는 쪽에서 미리 막는 것이 좋다.
     */
    fun write(
        pages: List<Page>,
        title: String = "",
        producer: String = "ScanPDF",
        creationDate: String? = null
    ): ByteArray {
        val out = ByteArrayOutputStream(estimateSize(pages))
        val offsets = HashMap<Int, Int>()
        var nextObject = 1

        fun allocate(): Int = nextObject++

        fun beginObject(number: Int) {
            offsets[number] = out.size()
            out.writeAscii("$number 0 obj\n")
        }

        fun endObject() {
            out.writeAscii("endobj\n")
        }

        // 1: 카탈로그, 2: 페이지 트리
        val catalogNumber = allocate()
        val pagesNumber = allocate()

        val pageObjects = ArrayList<Triple<Int, Int, Int>>(pages.size) // page, content, image
        pages.forEach {
            pageObjects.add(Triple(allocate(), allocate(), allocate()))
        }
        val infoNumber = allocate()

        out.writeAscii("%PDF-1.7\n")
        // 바이너리 파일임을 알리는 관례적 주석 (뷰어가 텍스트로 오해하지 않게)
        out.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

        beginObject(catalogNumber)
        out.writeAscii("<< /Type /Catalog /Pages $pagesNumber 0 R >>\n")
        endObject()

        beginObject(pagesNumber)
        out.writeAscii("<< /Type /Pages /Count ${pages.size} /Kids [")
        pageObjects.forEachIndexed { index, (pageObj, _, _) ->
            if (index > 0) out.writeAscii(" ")
            out.writeAscii("$pageObj 0 R")
        }
        out.writeAscii("] >>\n")
        endObject()

        pages.forEachIndexed { index, page ->
            val (pageObj, contentObj, imageObj) = pageObjects[index]
            val info = readJpegInfo(page.jpeg) ?: JpegInfo(1, 1, 3, 8)
            val colorSpace = when (info.components) {
                1 -> "/DeviceGray"
                4 -> "/DeviceCMYK"
                else -> "/DeviceRGB"
            }
            val w = fmt(page.widthPt)
            val h = fmt(page.heightPt)

            beginObject(pageObj)
            out.writeAscii(
                "<< /Type /Page /Parent $pagesNumber 0 R /MediaBox [0 0 $w $h] " +
                    "/Resources << /XObject << /Im0 $imageObj 0 R >> >> " +
                    "/Contents $contentObj 0 R >>\n"
            )
            endObject()

            val content = "q\n$w 0 0 $h 0 0 cm\n/Im0 Do\nQ\n".toByteArray(Charsets.ISO_8859_1)
            beginObject(contentObj)
            out.writeAscii("<< /Length ${content.size} >>\nstream\n")
            out.write(content)
            out.writeAscii("endstream\n")
            endObject()

            beginObject(imageObj)
            out.writeAscii(
                "<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} " +
                    "/ColorSpace $colorSpace /BitsPerComponent ${info.bitsPerComponent} " +
                    "/Filter /DCTDecode /Interpolate true /Length ${page.jpeg.size} >>\nstream\n"
            )
            out.write(page.jpeg)
            out.writeAscii("\nendstream\n")
            endObject()
        }

        // 문서 정보
        val stamp = creationDate ?: pdfDateNow()
        beginObject(infoNumber)
        out.writeAscii(
            "<< /Title ${textString(title)} /Producer ${textString(producer)} " +
                "/Creator ${textString(producer)} /CreationDate (${escapeLiteral(stamp)}) >>\n"
        )
        endObject()

        val xrefOffset = out.size()
        val totalObjects = nextObject
        out.writeAscii("xref\n0 $totalObjects\n")
        out.writeAscii("0000000000 65535 f \n")
        for (number in 1 until totalObjects) {
            val offset = offsets[number] ?: 0
            out.writeAscii(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        val id = documentId(pages)
        out.writeAscii(
            "trailer\n<< /Size $totalObjects /Root $catalogNumber 0 R /Info $infoNumber 0 R " +
                "/ID [<$id> <$id>] >>\n"
        )
        out.writeAscii("startxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    /** JPEG 바이트에서 크기·성분 수를 읽는다. 알 수 없으면 null. */
    fun readJpegInfo(bytes: ByteArray): JpegInfo? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 3 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) {
                i++
                continue
            }
            var marker = bytes[i + 1].toInt() and 0xFF
            // 채움 바이트(0xFF)는 건너뛴다
            while (marker == 0xFF) {
                i++
                if (i + 1 >= bytes.size) return null
                marker = bytes[i + 1].toInt() and 0xFF
            }
            i += 2
            when (marker) {
                0xD8, 0xD9 -> Unit
                0x01, 0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7 -> Unit
                else -> {
                    if (i + 1 >= bytes.size) return null
                    val length = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
                    if (length < 2) return null
                    val isSof = marker in SOF_MARKERS
                    if (isSof) {
                        if (i + 7 >= bytes.size) return null
                        val precision = bytes[i + 2].toInt() and 0xFF
                        val height = ((bytes[i + 3].toInt() and 0xFF) shl 8) or (bytes[i + 4].toInt() and 0xFF)
                        val width = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                        val components = bytes[i + 7].toInt() and 0xFF
                        if (width <= 0 || height <= 0 || components !in 1..4) return null
                        return JpegInfo(width, height, components, precision.coerceIn(1, 16))
                    }
                    if (marker == 0xDA) return null // SOS 전에 SOF 가 없으면 이상한 파일
                    i += length
                }
            }
        }
        return null
    }

    /** A4 세로 기준 긴 변 842pt 에 맞춘 페이지 크기. 비율은 이미지 그대로 유지한다. */
    fun pageSizeFor(widthPx: Int, heightPx: Int): Pair<Float, Float> {
        val w = widthPx.coerceAtLeast(1).toFloat()
        val h = heightPx.coerceAtLeast(1).toFloat()
        val longSidePt = A4_LONG_PT
        return if (w >= h) {
            Pair(longSidePt, longSidePt * h / w)
        } else {
            Pair(longSidePt * w / h, longSidePt)
        }
    }

    /** PDF 날짜 문자열: D:YYYYMMDDHHmmSS+09'00' 형태 */
    fun pdfDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, tzOffsetMinutes: Int): String {
        val sign = if (tzOffsetMinutes < 0) '-' else '+'
        val abs = kotlin.math.abs(tzOffsetMinutes)
        return String.format(
            Locale.US,
            "D:%04d%02d%02d%02d%02d%02d%s%02d'%02d'",
            year, month, day, hour, minute, second, sign, abs / 60, abs % 60
        )
    }

    private fun pdfDateNow(): String {
        val cal = java.util.Calendar.getInstance()
        val zone = cal.timeZone.getOffset(cal.timeInMillis) / 60000
        return pdfDate(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
            zone
        )
    }

    /** 텍스트를 PDF 문자열로. ASCII 밖 글자는 UTF-16BE 16진 문자열로 쓴다. */
    private fun textString(value: String): String {
        if (value.isEmpty()) return "()"
        val ascii = value.all { it.code in 0x20..0x7E }
        if (ascii) return "(${escapeLiteral(value)})"
        val sb = StringBuilder("<FEFF")
        for (ch in value) {
            sb.append(String.format(Locale.US, "%04X", ch.code))
        }
        sb.append('>')
        return sb.toString()
    }

    private fun escapeLiteral(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            when (ch) {
                '\\', '(', ')', '<', '>' -> sb.append('\\').append(ch)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun documentId(pages: List<Page>): String {
        val digest = MessageDigest.getInstance("MD5")
        pages.forEach {
            digest.update(java.nio.ByteBuffer.allocate(8).putInt(it.jpeg.size).putInt(it.widthPt.toInt()).array())
            digest.update(it.jpeg, 0, minOf(it.jpeg.size, 4096))
        }
        return digest.digest().joinToString("") { String.format(Locale.US, "%02X", it) }
    }

    private fun estimateSize(pages: List<Page>): Int {
        var size = 4096
        pages.forEach { size += it.jpeg.size + 1024 }
        return size
    }

    private fun fmt(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString()
        else String.format(Locale.US, "%.2f", value)

    private fun ByteArrayOutputStream.writeAscii(text: String) {
        write(text.toByteArray(Charsets.ISO_8859_1))
    }

    /** SOF0~SOF15 (DHT·JPG·DAC 는 제외) */
    private val SOF_MARKERS = setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)

    const val A4_LONG_PT = 841.89f
}
