package com.youngsu.scanpdf.pdf

/**
 * 문서 사진 보정 필터.
 *
 * Bitmap 없이 ARGB 픽셀 배열(IntArray)만 다루는 순수 Kotlin 이라 JVM 에서 그대로 검증한다.
 * 화면 미리보기와 최종 PDF 모두 같은 함수를 거치므로 보이는 그대로 저장된다.
 */
object Filters {

    enum class Kind { ORIGINAL, DOCUMENT, BW, GRAY, SHARP }

    /** [kind] 에 따라 [pixels] 를 제자리에서 바꾼다. */
    fun apply(kind: Kind, pixels: IntArray, width: Int, height: Int) {
        when (kind) {
            Kind.ORIGINAL -> return
            Kind.GRAY -> grayscale(pixels, width, height, stretch = true)
            Kind.DOCUMENT -> document(pixels, width, height)
            Kind.BW -> adaptiveThreshold(pixels, width, height)
            Kind.SHARP -> shadowRemoval(pixels, width, height)
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
     * 손 그림자·조명 얼룩을 지우고 종이를 흰색으로 만드는 '선명 문서' 필터.
     *
     * 1) 화면을 작은 타일로 나눠 타일마다 '종이 밝기'(밝은 쪽 90퍼센타일)를 구한다.
     *    손가락 그림자나 스탠드 조명 때문에 종이가 어둡게 찍힌 부분은 이 값이 낮다.
     * 2) 픽셀마다 주변 타일 값을 부드럽게 섞어(쌍선형) 배경 밝기를 구하고,
     *    그 값이 255 가 되도록 각 채널을 나눠 준다 → 그림자가 사라지고 종이가 하얘진다.
     * 3) 글자 경계를 살짝 세워(언샤프) 스캔본처럼 또렷하게 만든다.
     *
     * 색은 채널별로 같은 배율을 곱해 유지되므로 도장·형광펜 색이 살아남는다.
     */
    fun shadowRemoval(pixels: IntArray, width: Int, height: Int) {
        if (width < 8 || height < 8) return
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) gray[i] = luma(pixels[i])

        val tile = (minOf(width, height) / 14).coerceIn(16, 48)
        val cols = (width + tile - 1) / tile
        val rows = (height + tile - 1) / tile
        val background = FloatArray(cols * rows)
        val histogram = IntArray(256)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                histogram.fill(0)
                val x0 = col * tile
                val y0 = row * tile
                val x1 = minOf(width, x0 + tile)
                val y1 = minOf(height, y0 + tile)
                var count = 0
                for (y in y0 until y1) {
                    val offset = y * width
                    for (x in x0 until x1) {
                        histogram[gray[offset + x]]++
                        count++
                    }
                }
                val target = (count * 0.9f).toInt().coerceAtLeast(1)
                var sum = 0
                var value = 255
                for (level in 0..255) {
                    sum += histogram[level]
                    if (sum >= target) {
                        value = level
                        break
                    }
                }
                background[row * cols + col] = value.toFloat()
            }
        }

        // 종이 밝기의 하한 — 아주 어두운 타일(그림자 한가운데)도 이보다 낮게 잡지 않는다
        val darkestPaper = ((levels(gray)[1] * 0.55f)).coerceAtLeast(40f)

        val lift = FloatArray(pixels.size)
        for (y in 0 until height) {
            val fy = ((y + 0.5f) / tile - 0.5f).coerceIn(0f, (rows - 1).toFloat())
            val r0 = fy.toInt()
            val r1 = minOf(r0 + 1, rows - 1)
            val ty = fy - r0
            for (x in 0 until width) {
                val fx = ((x + 0.5f) / tile - 0.5f).coerceIn(0f, (cols - 1).toFloat())
                val c0 = fx.toInt()
                val c1 = minOf(c0 + 1, cols - 1)
                val tx = fx - c0
                val top = background[r0 * cols + c0] * (1 - tx) + background[r0 * cols + c1] * tx
                val bottom = background[r1 * cols + c0] * (1 - tx) + background[r1 * cols + c1] * tx
                val paper = (top * (1 - ty) + bottom * ty).coerceAtLeast(darkestPaper)
                lift[y * width + x] = 250f / paper
            }
        }

        for (i in pixels.indices) {
            val color = pixels[i]
            val gain = lift[i]
            var r = (((color shr 16) and 0xFF) * gain).toInt()
            var g = (((color shr 8) and 0xFF) * gain).toInt()
            var b = ((color and 0xFF) * gain).toInt()
            if (r > 255) r = 255
            if (g > 255) g = 255
            if (b > 255) b = 255
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }

        // 글자 경계를 살짝 세운다 (밝기만 대상으로, 색은 그대로 둔다)
        sharpen(pixels, width, height, amount = 0.55f)
    }

    /** 3x3 언샤프 — 밝기 차이만큼 각 채널을 밀어 준다. */
    private fun sharpen(pixels: IntArray, width: Int, height: Int, amount: Float) {
        val source = IntArray(pixels.size)
        for (i in pixels.indices) source[i] = luma(pixels[i])
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                val center = source[index]
                var around = 0
                var count = 0
                if (y > 0) { around += source[index - width]; count++ }
                if (y < height - 1) { around += source[index + width]; count++ }
                if (x > 0) { around += source[index - 1]; count++ }
                if (x < width - 1) { around += source[index + 1]; count++ }
                if (count == 0) continue
                val delta = ((center - around / count.toFloat()) * amount).toInt()
                if (delta == 0) continue
                val color = pixels[index]
                var r = ((color shr 16) and 0xFF) + delta
                var g = ((color shr 8) and 0xFF) + delta
                var b = (color and 0xFF) + delta
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                pixels[index] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
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
