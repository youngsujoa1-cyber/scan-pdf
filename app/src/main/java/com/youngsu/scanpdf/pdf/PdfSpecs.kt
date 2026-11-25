package com.youngsu.scanpdf.pdf

/** PDF 화질 프리셋 — 긴 변 픽셀과 JPEG 품질 */
enum class PdfQuality(val maxDim: Int, val jpegQuality: Int) {
    HIGH(2400, 92),
    NORMAL(1800, 85),
    SMALL(1400, 72);

    companion object {
        fun fromId(id: String?): PdfQuality =
            entries.firstOrNull { it.name == id } ?: NORMAL
    }
}

/** PDF 압축 강도 — 페이지를 다시 그릴 배율(1pt 당 픽셀)과 JPEG 품질 */
enum class CompressPreset(val renderScale: Float, val jpegQuality: Int) {
    LIGHT(3.0f, 88),
    NORMAL(2.2f, 78),
    STRONG(1.6f, 62);

    companion object {
        fun fromId(id: String?): CompressPreset =
            entries.firstOrNull { it.name == id } ?: NORMAL
    }
}

/** 병합할 때 원본 페이지를 다시 그리는 배율 (약 216dpi) */
object MergeSpec {
    const val RENDER_SCALE = 3.0f
    const val JPEG_QUALITY = 92
}
