package com.youngsu.scanpdf.pdf

/**
 * 문서 사진 보정 필터.
 *
 * Bitmap 없이 ARGB 픽셀 배열(IntArray)만 다루는 순수 Kotlin 이라 JVM 에서 그대로 검증한다.
 * 화면 미리보기와 최종 PDF 모두 같은 함수를 거치므로 보이는 그대로 저장된다.
 */
object Filters {

    enum class Kind { ORIGINAL, DOCUMENT, BW, GRAY }

    /** [kind] 에 따라 [pixels] 를 제자리에서 바꾼다. */
    fun apply(kind: Kind, pixels: IntArray, width: Int, height: Int) {
        when (kind) {
            Kind.ORIGINAL -> return
            Kind.GRAY -> grayscale(pixels, width, height, stretch = true)
            Kind.DOCUMENT -> document(pixels, width, height)
            Kind.BW -> adaptiveThreshold(pixels, width, height)
        }
    }

    /** 밝기(luma). Rec.601 정수 근사. */
    fun luma(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (77 * r + 150 * g + 29 * b) shr 8
    }

    /**
     * 밝기 히스토그램의 [lowPercent]~[highPercent] 구간을 0~255 로 늘릴 때 쓸
     * 검정·흰색 기준점을 구한다. (종이 배경의 회색을 흰색으로, 글자를 검게)
     */
    fun levels(pixels: IntArray, lowPercent: Float = 0.5f, highPercent: Float = 99.6f): IntArray {
        val histogram = IntArray(256)
        for (color in pixels) {
            histogram[luma(color)]++
        }
        val total = pixels.size
        if (total == 0) return intArrayOf(0, 255)
        val lowTarget = (total * lowPercent / 100f).toInt()
        val highTarget = (total * highPercent / 100f).toInt()
        var black = 0
        var sum = 0
        for (i in 0..255) {
            sum += histogram[i]
            if (sum >= lowTarget) {
                black = i
                break
            }
        }
        var white = 255
        sum = 0
        for (i in 255 downTo 0) {
            sum += histogram[i]
            if (sum >= total - highTarget) {
                white = i
                break
            }
        }
        if (white - black < 24) {
            black = (black - 8).coerceAtLeast(0)
            white = (white + 8).coerceAtMost(255)
        }
        return intArrayOf(black, white)
    }

    private fun grayscale(pixels: IntArray, width: Int, height: Int, stretch: Boolean) {
        val (black, white) = if (stretch) levels(pixels) else intArrayOf(0, 255)
        val span = (white - black).coerceAtLeast(1)
        for (i in pixels.indices) {
            val y = luma(pixels[i])
            val v = (((y - black) * 255) / span).coerceIn(0, 255)
            pixels[i] = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
        }
    }

    /**
     * 문서 필터: 회색지 배경을 흰색으로 끌어올리고, 글자 경계를 살짝 세운다.
     * 색은 거의 중립으로 두되 완전 흑백은 아니어서 스캔본처럼 자연스럽다.
     */
    private fun document(pixels: IntArray, width: Int, height: Int) {
        val (black, white) = levels(pixels)
        val span = (white - black).coerceAtLeast(1)
        val gray = IntArray(pixels.size)
        var sum = 0L
        for (i in pixels.indices) {
            val y = luma(pixels[i])
            var v = ((y - black) * 255) / span
            if (v < 0) v = 0
            if (v > 255) v = 255
            // 아주 밝은 종이 영역은 순백으로 붙여 얼룩을 지운다
            if (v >= 244) v = 255
            gray[i] = v
            sum += v
        }
        // 종이가 어둡게 남은 사진은 전체를 한 단계 더 밝힌다
        val mean = (sum / pixels.size.coerceAtLeast(1)).toInt()
        val lift = when {
            mean < 120 -> 10
            mean < 160 -> 6
            else -> 0
        }

        // 3x3 언샤프(중심 5, 상하좌우 -1)로 글자 경계를 세운다
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                val c = gray[index]
                var acc = c * 5
                acc -= if (y > 0) gray[index - width] else c
                acc -= if (y < height - 1) gray[index + width] else c
                acc -= if (x > 0) gray[index - 1] else c
                acc -= if (x < width - 1) gray[index + 1] else c
                var v = acc // 5c - (이웃 4개) 이므로 그대로 쓰면 밝기가 유지된다
                if (c >= 250) v = 255
                v = (v + lift).coerceIn(0, 255)
                pixels[index] = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
            }
        }
    }

    /**
     * 국소 평균을 이용한 적응 임계값 이진화(흑백).
     * 조명이 고르지 않은 사진에서도 글자가 살아남는다. 메모리는 O(width).
     */
    private fun adaptiveThreshold(pixels: IntArray, width: Int, height: Int) {
        if (width == 0 || height == 0) return
        val radius = (minOf(width, height) / 24).coerceIn(6, 24)
        val columnSums = IntArray(width)
        val means = IntArray(pixels.size)

        // 세로 방향으로 (2r+1) 줄 합을 굴려 가며 각 픽셀의 국소 평균을 구한다
        for (y in 0 until height) {
            val addRow = y + radius
            val subRow = y - radius - 1
            if (addRow < height) {
                val offset = addRow * width
                for (x in 0 until width) columnSums[x] += luma(pixels[offset + x])
            }
            if (subRow >= 0) {
                val offset = subRow * width
                for (x in 0 until width) columnSums[x] -= luma(pixels[offset + x])
            }
            val rowCount = (minOf(y + radius, height - 1) - maxOf(y - radius, 0) + 1)
            var window = 0
            for (x in 0..minOf(radius * 2, width - 1)) {
                window += columnSums[minOf(x, width - 1)]
            }
            for (x in 0 until width) {
                val left = x - radius - 1
                val right = x + radius
                if (left >= 0) window -= columnSums[left]
                if (right < width) window += columnSums[right]
                val count = rowCount * (minOf(right, width - 1) - maxOf(left + 1, 0) + 1)
                means[y * width + x] = if (count > 0) window / count else 255
            }
        }

        for (i in pixels.indices) {
            val y = luma(pixels[i])
            val mean = means[i]
            // 평균보다 확실히 어두운 픽셀만 글자로 본다 (약간의 바이어스로 회색 얼룩 제거)
            val value = if (y < mean - 8) 0 else 255
            pixels[i] = 0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }
    }
}
