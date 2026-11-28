package com.youngsu.scanpdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.youngsu.scanpdf.data.PageRenderer
import com.youngsu.scanpdf.data.ScanPage
import java.io.File
import java.io.FileOutputStream

/**
 * PDF 만들기/합치기/압축.
 *
 * 읽기는 Android 의 [PdfRenderer] 로, 쓰기는 [PdfWriter] 로 한다.
 * 페이지는 JPEG 로 한 번만 압축해 넣기 때문에 사진 스캔본이 페이지당
 * 수백 KB 수준으로 유지되고, 어떤 PDF 뷰어에서도 열린다.
 */
object PdfEngine {

    data class CompressResult(
        val output: File,
        val beforeBytes: Long,
        val afterBytes: Long,
        val pages: Int
    ) {
        val savedPercent: Int get() = com.youngsu.scanpdf.util.ByteSize.savedPercent(beforeBytes, afterBytes)
        val improved: Boolean get() = afterBytes < beforeBytes
    }

    fun pageCount(file: File): Int = runCatching {
        openRenderer(file).use { it.pageCount }
    }.getOrDefault(0)

    fun openRenderer(file: File): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor)
    }

    /**
     * 편집한 페이지들로 PDF 를 만든다.
     * [onProgress] 는 (끝난 페이지 수, 전체 페이지 수) 로 불린다.
     */
    fun create(
        pages: List<ScanPage>,
        quality: PdfQuality,
        target: File,
        title: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int {
        require(pages.isNotEmpty()) { "페이지가 없습니다" }
        val specs = ArrayList<PdfWriter.Page>(pages.size)
        pages.forEachIndexed { index, page ->
            val bitmap = PageRenderer.render(page.file, page.rotation, page.quad, page.filter, quality.maxDim)
            try {
                val jpeg = PageRenderer.toJpeg(bitmap, quality.jpegQuality)
                val info = PdfWriter.readJpegInfo(jpeg) ?: throw IllegalStateException("페이지 이미지를 만들지 못했습니다")
                val (widthPt, heightPt) = PdfWriter.pageSizeFor(info.width, info.height)
                specs.add(PdfWriter.Page(widthPt, heightPt, jpeg))
            } finally {
                bitmap.recycle()
            }
            onProgress(index + 1, pages.size)
        }
        writeAtomically(target, PdfWriter.write(specs, title))
        return specs.size
    }

    /**
     * 여러 PDF 를 순서대로 하나로 합친다.
     * 원본 페이지 크기(pt)를 그대로 유지하고, 각 페이지를 약 216dpi 로 다시 그려 넣는다.
     */
    fun merge(
        sources: List<File>,
        target: File,
        title: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int {
        require(sources.size >= 2) { "PDF 가 2개 이상 필요합니다" }
        val total = sources.sumOf { file -> runCatching { pageCount(file) }.getOrDefault(0) }
        val specs = ArrayList<PdfWriter.Page>(total)
        var done = 0
        for (source in sources) {
            openRenderer(source).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val widthPt = page.width.toFloat()
                        val heightPt = page.height.toFloat()
                        val bitmap = renderPage(page, MergeSpec.RENDER_SCALE)
                        try {
                            val jpeg = PageRenderer.toJpeg(bitmap, MergeSpec.JPEG_QUALITY)
                            specs.add(PdfWriter.Page(widthPt, heightPt, jpeg))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    done++
                    onProgress(done, total)
                }
            }
        }
        if (specs.isEmpty()) throw IllegalStateException("합칠 페이지가 없습니다")
        writeAtomically(target, PdfWriter.write(specs, title))
        return specs.size
    }

    /**
     * PDF 를 다시 그려 용량을 줄인다. 결과가 원본보다 크면 [CompressResult.improved] 가 false.
     */
    fun compress(
        source: File,
        target: File,
        preset: CompressPreset,
        title: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): CompressResult {
        val specs = ArrayList<PdfWriter.Page>()
        openRenderer(source).use { renderer ->
            val total = renderer.pageCount
            for (index in 0 until total) {
                renderer.openPage(index).use { page ->
                    // 원본 페이지 크기(pt)를 그대로 유지한다 — 압축해도 종이 크기는 같다
                    val widthPt = page.width.toFloat()
                    val heightPt = page.height.toFloat()
                    val bitmap = renderPage(page, preset.renderScale, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    try {
                        val jpeg = PageRenderer.toJpeg(bitmap, preset.jpegQuality)
                        PdfWriter.readJpegInfo(jpeg)
                            ?: throw IllegalStateException("페이지 이미지를 만들지 못했습니다")
                        specs.add(PdfWriter.Page(widthPt, heightPt, jpeg))
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(index + 1, total)
            }
        }
        if (specs.isEmpty()) throw IllegalStateException("페이지가 없습니다")
        writeAtomically(target, PdfWriter.write(specs, title))
        return CompressResult(
            output = target,
            beforeBytes = source.length(),
            afterBytes = target.length(),
            pages = specs.size
        )
    }

    /**
     * 페이지를 골라 순서를 바꾸거나 일부만 남겨 새 PDF 로 다시 쓴다.
     *
     * [order] 는 원본 페이지 번호(0부터)의 목록이다. 예: [2, 0, 1] 은 셋째·첫째·둘째 쪽 순서,
     * [0, 2] 는 첫째·셋째 쪽만 남긴다(나누기). 원본 페이지 크기(pt)는 그대로 유지한다.
     */
    fun rewrite(
        source: File,
        order: List<Int>,
        target: File,
        title: String,
        quality: PdfQuality = PdfQuality.NORMAL,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int {
        require(order.isNotEmpty()) { "페이지가 없습니다" }
        val specs = ArrayList<PdfWriter.Page>(order.size)
        openRenderer(source).use { renderer ->
            order.forEachIndexed { position, index ->
                if (index !in 0 until renderer.pageCount) {
                    throw IllegalStateException("페이지 번호가 잘못되었습니다: ${index + 1}")
                }
                renderer.openPage(index).use { page ->
                    val bitmap = renderPage(page, qualityScale(page, quality))
                    try {
                        val jpeg = PageRenderer.toJpeg(bitmap, quality.jpegQuality)
                        specs.add(PdfWriter.Page(page.width.toFloat(), page.height.toFloat(), jpeg))
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(position + 1, order.size)
            }
        }
        writeAtomically(target, PdfWriter.write(specs, title))
        return specs.size
    }

    /**
     * 한 쪽 위에 서명·도장 그림을 얹어 새 PDF 로 저장한다.
     *
     * [rect] 는 그 쪽 안에서 서명이 놓일 자리(0~1 비율, 왼쪽 위 기준)다. 화면에서 끌고 키운
     * 위치를 그대로 옮길 수 있도록 비율로 받는다. 서명 그림의 가로세로 비율은 찌그러뜨리지 않는다.
     */
    fun sign(
        source: File,
        target: File,
        pageIndex: Int,
        signature: Bitmap,
        rect: android.graphics.RectF,
        title: String,
        quality: PdfQuality = PdfQuality.NORMAL,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int {
        val specs = ArrayList<PdfWriter.Page>()
        openRenderer(source).use { renderer ->
            if (renderer.pageCount == 0) throw IllegalStateException("페이지가 없습니다")
            val total = renderer.pageCount
            for (index in 0 until total) {
                renderer.openPage(index).use { page ->
                    val bitmap = renderPage(page, qualityScale(page, quality))
                    try {
                        if (index == pageIndex) {
                            val canvas = android.graphics.Canvas(bitmap)
                            val area = android.graphics.RectF(
                                rect.left * bitmap.width,
                                rect.top * bitmap.height,
                                rect.right * bitmap.width,
                                rect.bottom * bitmap.height
                            )
                            drawSignature(canvas, signature, area)
                        }
                        val jpeg = PageRenderer.toJpeg(bitmap, quality.jpegQuality)
                        specs.add(PdfWriter.Page(page.width.toFloat(), page.height.toFloat(), jpeg))
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(index + 1, total)
            }
        }
        writeAtomically(target, PdfWriter.write(specs, title))
        return specs.size
    }

    /** 서명 그림을 [area] 안에 비율을 지켜 가운데 놓는다. */
    private fun drawSignature(canvas: android.graphics.Canvas, signature: Bitmap, area: android.graphics.RectF) {
        if (signature.width <= 0 || signature.height <= 0) return
        val scale = minOf(area.width() / signature.width, area.height() / signature.height)
        val w = signature.width * scale
        val h = signature.height * scale
        val left = area.left + (area.width() - w) / 2f
        val top = area.top + (area.height() - h) / 2f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(signature, null, android.graphics.RectF(left, top, left + w, top + h), paint)
    }

    /** 설정한 화질의 긴 변 픽셀에 맞는 그리기 배율 */
    private fun qualityScale(page: PdfRenderer.Page, quality: PdfQuality): Float =
        quality.maxDim.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)

    private fun renderPage(
        page: PdfRenderer.Page,
        scale: Float,
        mode: Int = PdfRenderer.Page.RENDER_MODE_FOR_PRINT
    ): Bitmap {
        // 아주 큰 페이지라도 가로세로 비율은 그대로 두고 함께 줄인다
        val requestedW = page.width * scale
        val requestedH = page.height * scale
        val longSide = maxOf(requestedW, requestedH)
        val factor = if (longSide > MAX_SIDE) MAX_SIDE / longSide else 1f
        val width = (requestedW * factor).toInt().coerceAtLeast(1)
        val height = (requestedH * factor).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        page.render(bitmap, null, null, mode)
        return bitmap
    }

    /** 임시 파일에 다 쓴 뒤 한 번에 바꿔치기 — 중간에 실패해도 반쯤 쓴 PDF 가 남지 않는다. */
    fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use { stream ->
            stream.write(bytes)
            stream.flush()
            stream.fd.sync()
        }
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IllegalStateException("기존 파일을 바꿀 수 없습니다")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("파일을 저장할 수 없습니다")
        }
    }

    private const val MAX_SIDE = 5000
}
