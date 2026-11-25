import com.youngsu.scanpdf.pdf.CompressPreset
import com.youngsu.scanpdf.pdf.Filters
import com.youngsu.scanpdf.pdf.PdfQuality
import com.youngsu.scanpdf.pdf.PdfWriter
import com.youngsu.scanpdf.pdf.ScanGeometry
import com.youngsu.scanpdf.util.ByteSize
import com.youngsu.scanpdf.util.FileNames
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * 순수 로직 검증 (안드로이드 없이 JVM 에서 실행)
 *
 * 검증 대상
 *  - PdfWriter: JPEG→PDF 조립, xref 오프셋, 페이지 크기, JPEG 헤더 해석
 *  - Filters: 문서/흑백/그레이 필터의 픽셀 결과
 *  - ScanGeometry: 회전·자르기 좌표 변환
 *  - FileNames / ByteSize: 파일 이름·용량 표기
 *
 * 만들어진 PDF 는 tools/logic-check/verify.py (PyMuPDF)로 다시 열어 확인한다.
 */
var failures = 0

fun expect(name: String, actual: Any?, expected: Any?) {
    if (actual == expected) println("PASS  $name = $actual")
    else {
        failures++
        println("FAIL  $name = $actual (expected $expected)")
    }
}

fun expectNear(name: String, actual: Double, expected: Double, tolerance: Double = 0.01) {
    if (kotlin.math.abs(actual - expected) <= tolerance) println("PASS  $name = $actual")
    else {
        failures++
        println("FAIL  $name = $actual (expected $expected ± $tolerance)")
    }
}

fun main() {
    val outDir = File("tools/logic-check/out").apply { mkdirs() }
    Locale.setDefault(Locale.KOREA)

    geometryChecks()
    filterChecks()
    nameChecks()
    val pages = pdfChecks(outDir)

    println()
    if (failures == 0) println("ALL CHECKS PASSED (pages written: $pages)")
    else println("$failures CHECK(S) FAILED")
    if (failures > 0) kotlin.system.exitProcess(1)
}

// ---------------------------------------------------------------- geometry

fun geometryChecks() {
    val full = ScanGeometry.fullQuad()
    val rotated = ScanGeometry.rotate(full, 90)
    // 정사각형 전체 영역은 90° 돌려도 그대로여야 한다
    expect("rotate90 full == full", rotated.joinToString(","), full.joinToString(","))
    // tl(0,0) -> (1,0) 이 우상, tr(1,0) -> (1,1) 이 우하 … 시작점이 좌상(0,0)=원래 bl 로 재정렬된다
    expect("rotate360 == full", ScanGeometry.rotate(full, 360).joinToString(","), full.joinToString(","))

    val quad = floatArrayOf(0.1f, 0.2f, 0.9f, 0.15f, 0.85f, 0.8f, 0.15f, 0.9f)
    for (deg in intArrayOf(0, 90, 180, 270)) {
        val round = ScanGeometry.unrotate(ScanGeometry.rotate(quad, deg), deg)
        var maxDiff = 0f
        for (i in 0 until 8) maxDiff = maxOf(maxDiff, kotlin.math.abs(round[i] - quad[i]))
        expectNear("rotate/unrotate $deg°", maxDiff.toDouble(), 0.0, 0.0001)
    }

    // 모서리 순서가 뒤섞여 들어와도 좌상부터 정렬
    val shuffled = floatArrayOf(0.85f, 0.8f, 0.15f, 0.9f, 0.1f, 0.2f, 0.9f, 0.15f)
    val ordered = ScanGeometry.reorder(shuffled)
    expect("reorder starts at top-left", "${ordered[0]},${ordered[1]}", "0.1,0.2")
    expect("reorder keeps next clockwise", "${ordered[2]},${ordered[3]}", "0.9,0.15")

    val half = floatArrayOf(0f, 0f, 0.5f, 0f, 0.5f, 1f, 0f, 1f)
    val size = ScanGeometry.croppedSize(half, 1000, 2000)
    expect("croppedSize half", "${size.first}x${size.second}", "500x2000")

    expect("isFull(null)", ScanGeometry.isFull(null), true)
    expect("isFull(full)", ScanGeometry.isFull(ScanGeometry.fullQuad()), true)
    expect("isFull(cropped)", ScanGeometry.isFull(half), false)
    expect("clamp keeps inside", ScanGeometry.clamp(floatArrayOf(-0.2f, 0.5f, 1.2f, 0f, 1f, 1f, 0f, 1f))!![0], 0f)

    // 자르기 화면의 사진 자리 계산 — 1.0.2 에서 사진이 안 보이고 핸들이 (0,0) 에 몰렸던 문제
    val landscape = ScanGeometry.fitRect(1600, 1200, 1080, 1560)
    expect("fitRect landscape", landscape.joinToString(","), "0.0,375.0,1080.0,1185.0")
    val portrait = ScanGeometry.fitRect(1200, 1600, 1080, 1560)
    expect("fitRect portrait", portrait.joinToString(","), "0.0,60.0,1080.0,1500.0")
    // 화면 크기가 아직 0 이면 빈 사각형 — 이 상태로 그리면 사진도 핸들도 사라진다
    val notLaidOut = ScanGeometry.fitRect(1600, 1200, 0, 0)
    expect("fitRect before layout is empty", notLaidOut.joinToString(","), "0.0,0.0,0.0,0.0")

    // 핸들 네 점이 서로 다른 자리에 찍히는지 (모두 (0,0) 이면 1.0.2 증상)
    val handles = ScanGeometry.project(ScanGeometry.fullQuad(), landscape)
    expect("handles tl", "${handles[0]},${handles[1]}", "0.0,375.0")
    expect("handles br", "${handles[4]},${handles[5]}", "1080.0,1185.0")
    var distinct = 0
    for (i in 0 until 4) {
        for (j in i + 1 until 4) {
            if (handles[i * 2] != handles[j * 2] || handles[i * 2 + 1] != handles[j * 2 + 1]) distinct++
        }
    }
    expect("handles are 4 distinct points", distinct, 6)
    // 잘라 둔 사각형도 사진 안 좌표로 그대로 옮겨진다
    val quarter = ScanGeometry.project(floatArrayOf(0f, 0f, 0.5f, 0f, 0.5f, 0.5f, 0f, 0.5f), landscape)
    expect("project quarter", "${quarter[4]},${quarter[5]}", "540.0,780.0")
}

// ----------------------------------------------------------------- filters

fun filterChecks() {
    // 회색 배경 + 진한 글자 줄이 있는 가짜 문서
    val w = 240
    val h = 320
    val source = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val base = 176 // 살짝 회색인 종이
            val isText = (y % 24 in 10..14) && x in 24..(w - 24)
            val v = if (isText) 60 else base
            source[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    // 흑백: 결과가 0 아니면 255
    val bw = source.copyOf()
    Filters.apply(Filters.Kind.BW, bw, w, h)
    val bwValues = bw.map { it and 0xFF }.toSet()
    expect("BW only 0/255", bwValues.sorted().toString(), "[0, 255]")
    // 글자 줄은 검게, 종이는 흰색으로
    val textPixel = bw[12 * w + 100] and 0xFF
    val paperPixel = bw[4 * w + 100] and 0xFF
    expect("BW text black", textPixel, 0)
    expect("BW paper white", paperPixel, 255)

    // 그레이: 채도 제거
    val colored = IntArray(4) { (0xFF shl 24) or (200 shl 16) or (40 shl 8) or 30 }
    val gray = colored.copyOf()
    Filters.apply(Filters.Kind.GRAY, gray, 2, 2)
    val g0 = gray[0]
    expect("GRAY r==g==b", (((g0 shr 16) and 0xFF) == (g0 and 0xFF)) && (((g0 shr 8) and 0xFF) == (g0 and 0xFF)), true)

    // 문서 필터: 대비 상승 (종이 밝아지고 글자 어두워짐)
    val doc = source.copyOf()
    Filters.apply(Filters.Kind.DOCUMENT, doc, w, h)
    val docPaper = Filters.luma(doc[4 * w + 100])
    val docText = Filters.luma(doc[12 * w + 100])
    expect("DOCUMENT paper bright", docPaper >= 235, true)
    expect("DOCUMENT text dark", docText <= 90, true)
    expect("DOCUMENT contrast grows", (docPaper - docText) > (176 - 60), true)

    // 원본은 그대로
    val untouched = source.copyOf()
    Filters.apply(Filters.Kind.ORIGINAL, untouched, w, h)
    expect("ORIGINAL unchanged", untouched.contentEquals(source), true)

    expect("luma white", Filters.luma(0xFFFFFFFF.toInt()), 255)
    expect("luma black", Filters.luma(0xFF000000.toInt()), 0)
}

// ------------------------------------------------------------------ names

fun nameChecks() {
    expect("sanitize trims pdf", FileNames.sanitize("  계약서.pdf  "), "계약서")
    expect("sanitize removes slash", FileNames.sanitize("a/b:c*"), "a b c")
    expect("sanitize empty", FileNames.sanitize("   "), null)
    expect("sanitize dots", FileNames.sanitize("..."), null)

    val taken = setOf("스캔.pdf", "스캔 (2).pdf")
    expect("unique skips taken", FileNames.unique("스캔") { it in taken }, "스캔 (3)")
    expect("unique free", FileNames.unique("새문서") { false }, "새문서")

    expect("byte size bytes", ByteSize.format(900), "900 B")
    expect("byte size kb", ByteSize.format(2048), "2 KB")
    expect("byte size mb", ByteSize.format(5_452_595), "5.2 MB")
    expect("saved percent", ByteSize.savedPercent(1000, 250), 75)
    expect("saved percent no gain", ByteSize.savedPercent(1000, 1000), 0)
}

// -------------------------------------------------------------------- pdf

/** 여러 장의 PDF 를 만들어 두고, 구조 검사 결과를 돌려준다. */
fun pdfChecks(outDir: File): Int {
    val portrait = jpegOf(drawDocument(1240, 1754, shade = 176), 88)
    val landscape = jpegOf(drawDocument(1754, 1240, shade = 205), 88)
    val photoish = jpegOf(drawDocument(1500, 2000, shade = 150, noisy = true), 70)

    val info = PdfWriter.readJpegInfo(portrait)
    expect("jpeg info width", info?.width, 1240)
    expect("jpeg info height", info?.height, 1754)
    expect("jpeg components", info?.components, 3)

    val (wPt, hPt) = PdfWriter.pageSizeFor(1240, 1754)
    expectNear("page long side = A4", hPt.toDouble(), 841.89, 0.01)
    expectNear("page aspect kept", (wPt / hPt).toDouble(), 1240.0 / 1754.0, 0.001)

    val (lw, lh) = PdfWriter.pageSizeFor(1754, 1240)
    expectNear("landscape long side", lw.toDouble(), 841.89, 0.01)
    expectNear("landscape aspect", (lh / lw).toDouble(), 1240.0 / 1754.0, 0.001)

    val pages = listOf(
        PdfWriter.Page(wPt, hPt, portrait),
        PdfWriter.Page(lw, lh, landscape),
        PdfWriter.Page(wPt, hPt, photoish)
    )
    val pdf = PdfWriter.write(pages, title = "스캔 문서 테스트", producer = "ScanPDF", creationDate = "D:20261125043000+09'00'")
    val file = File(outDir, "created.pdf")
    file.writeBytes(pdf)

    // 헤더/푸터
    expect("pdf header", String(pdf, 0, 8, Charsets.ISO_8859_1), "%PDF-1.7")
    expect("pdf ends with EOF", String(pdf, pdf.size - 6, 5, Charsets.ISO_8859_1), "%%EOF")

    // 페이지 수(/Type /Page) — 최상위 페이지 객체 3개 + 트리 노드
    val text = String(pdf, Charsets.ISO_8859_1)
    expect("/Type /Page count", Regex("/Type /Page[^s]").findAll(text).count(), 3)
    expect("pages tree count", text.contains("/Count 3"), true)

    // xref 오프셋이 실제 객체 시작을 가리키는지 전부 확인
    val xrefIndex = text.lastIndexOf("\nxref") + 1
    val startxref = text.substringAfterLast("startxref").trim().lines().first().trim().toInt()
    expect("startxref points at xref", startxref, xrefIndex)
    val lines = text.substring(xrefIndex).lines()
    val size = lines[1].trim().split(" ").last().toInt()
    var offsetProblems = 0
    for (number in 1 until size) {
        val entry = lines[2 + number]
        val offset = entry.substring(0, 10).toInt()
        val header = "$number 0 obj"
        val actual = String(pdf, offset, minOf(header.length, pdf.size - offset), Charsets.ISO_8859_1)
        if (actual != header) {
            offsetProblems++
            println("   xref mismatch for object $number: '$actual' != '$header'")
        }
    }
    expect("xref offsets valid", offsetProblems, 0)

    // 이미지 스트림 길이 = 원본 JPEG 길이 (재압축 없이 그대로 들어감)
    expect("DCTDecode used 3 times", Regex("/DCTDecode").findAll(text).count(), 3)
    expect("stream length kept", text.contains("/Length ${portrait.size}"), true)

    // 두 번째 페이지는 가로
    expect("landscape MediaBox present", text.contains("/MediaBox [0 0 841.89 595.18]"), true)

    // 압축 프리셋이 화질/배율을 낮추는지 (숫자 검증)
    expect("preset order", "${CompressPreset.LIGHT.renderScale > CompressPreset.NORMAL.renderScale && CompressPreset.NORMAL.jpegQuality > CompressPreset.STRONG.jpegQuality}", "true")
    expect("quality order", "${PdfQuality.HIGH.maxDim > PdfQuality.NORMAL.maxDim && PdfQuality.NORMAL.maxDim > PdfQuality.SMALL.maxDim}", "true")

    // 흑백 필터를 적용한 페이지 PDF 도 하나 만들어 둔다
    val bwJpeg = run {
        val img = drawDocument(1240, 1754, shade = 180)
        val pixels = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, pixels, 0, img.width)
        Filters.apply(Filters.Kind.BW, pixels, img.width, img.height)
        img.setRGB(0, 0, img.width, img.height, pixels, 0, img.width)
        jpegOf(img, 80)
    }
    val bwPage = PdfWriter.Page(wPt, hPt, bwJpeg)
    File(outDir, "bw.pdf").writeBytes(PdfWriter.write(listOf(bwPage, bwPage), title = "흑백 문서"))
    expect("bw pdf smaller than color", File(outDir, "bw.pdf").length() < File(outDir, "created.pdf").length(), true)

    // JPEG 가 아닌 바이트는 거부
    expect("non-jpeg rejected", PdfWriter.readJpegInfo(ByteArray(10)), null)
    return 3
}

// ------------------------------------------------------------- image tools

fun drawDocument(width: Int, height: Int, shade: Int, noisy: Boolean = false): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(shade, shade, shade)
    g.fillRect(0, 0, width, height)
    // 제목
    g.color = Color(28, 32, 40)
    val margin = width / 12
    g.fillRect(margin, height / 10, width / 2, height / 60)
    // 본문 줄
    g.color = Color(70, 74, 82)
    var y = height / 6
    var line = 0
    while (y < height * 9 / 10) {
        val lineWidth = width - margin * 2 - if (line % 5 == 4) width / 3 else 0
        g.fillRect(margin, y, lineWidth, height / 120)
        y += height / 40
        line++
    }
    // 접힌 모서리 느낌의 사선
    g.color = Color(210, 214, 220)
    g.fillPolygon(intArrayOf(width - margin, width - margin, width - margin * 2), intArrayOf(height / 12, height / 12 + margin, height / 12), 3)
    if (noisy) {
        val random = java.util.Random(7)
        for (i in 0 until (width * height / 40)) {
            val x = random.nextInt(width)
            val yy = random.nextInt(height)
            val c = image.getRGB(x, yy)
            val delta = random.nextInt(28) - 14
            val r = (((c shr 16) and 0xFF) + delta).coerceIn(0, 255)
            val gg = (((c shr 8) and 0xFF) + delta).coerceIn(0, 255)
            val b = ((c and 0xFF) + delta).coerceIn(0, 255)
            image.setRGB(x, yy, (r shl 16) or (gg shl 8) or b)
        }
    }
    g.dispose()
    return image
}

fun jpegOf(image: BufferedImage, quality: Int): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val params = writer.defaultWriteParam.apply {
        compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
        compressionQuality = quality / 100f
    }
    val stream = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(stream).use { output ->
        writer.output = output
        writer.write(null, javax.imageio.IIOImage(image, null, null), params)
    }
    writer.dispose()
    return stream.toByteArray()
}
