package com.youngsu.scanpdf.pdf

/**
 * 자르기 사각형(4점) 좌표 계산.
 *
 * 좌표는 이미지 기준 0~1 로 정규화되어 있고, 점 순서는 항상
 * 좌상 → 우상 → 우하 → 좌하 이다. 화면 회전(90° 단위)과 자르기가
 * 섞여도 같은 규칙을 유지하도록 변환을 여기 한 곳에 모았다.
 */
object ScanGeometry {

    /** 이미지 전체를 덮는 사각형 */
    fun fullQuad(): FloatArray = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

    /** 정규화 좌표를 0~1 안으로 가둔다. */
    fun clamp(quad: FloatArray?): FloatArray? {
        if (quad == null || quad.size != 8) return null
        return FloatArray(8) { quad[it].coerceIn(0f, 1f) }
    }

    /**
     * 이미지를 시계 방향 [degrees]°(90 단위) 돌렸을 때, 같은 내용 위치가
     * 회전된 이미지에서 갖는 좌표를 돌려준다.
     *
     * (정규화 좌표는 이미지가 정사각형이 아니어도 그대로 쓸 수 있다)
     */
    fun rotate(quad: FloatArray, degrees: Int): FloatArray {
        val step = ((degrees % 360) + 360) % 360
        if (step == 0) return quad.copyOf()
        val mapped = FloatArray(8)
        for (i in 0 until 4) {
            val x = quad[i * 2]
            val y = quad[i * 2 + 1]
            when (step) {
                90 -> {
                    mapped[i * 2] = 1f - y
                    mapped[i * 2 + 1] = x
                }
                180 -> {
                    mapped[i * 2] = 1f - x
                    mapped[i * 2 + 1] = 1f - y
                }
                270 -> {
                    mapped[i * 2] = y
                    mapped[i * 2 + 1] = 1f - x
                }
                else -> {
                    mapped[i * 2] = x
                    mapped[i * 2 + 1] = y
                }
            }
        }
        return reorder(mapped)
    }

    /** 회전된 공간의 사각형을 원본 이미지 공간으로 되돌린다. */
    fun unrotate(quad: FloatArray, degrees: Int): FloatArray =
        rotate(quad, ((360 - (((degrees % 360) + 360) % 360)) % 360))

    /**
     * 임의 순서의 4점을 좌상 → 우상 → 우하 → 좌하 순서로 정렬한다.
     * 회전은 점의 회전 방향(CW/CCW)을 바꾸지 않으므로 시작점만 다시 잡으면 된다.
     */
    fun reorder(points: FloatArray): FloatArray {
        if (points.size != 8) return points
        var cx = 0f
        var cy = 0f
        for (i in 0 until 4) {
            cx += points[i * 2] / 4f
            cy += points[i * 2 + 1] / 4f
        }
        // 중심에서 왼쪽 위에 있는 점을 시작점으로 삼는다. (없으면 x+y 가 가장 작은 점)
        var start = -1
        var best = Float.MAX_VALUE
        for (i in 0 until 4) {
            val x = points[i * 2]
            val y = points[i * 2 + 1]
            val inTopLeft = x <= cx && y <= cy
            val score = x + y - if (inTopLeft) 2f else 0f
            if (score < best) {
                best = score
                start = i
            }
        }
        if (start < 0) start = 0
        val out = FloatArray(8)
        for (k in 0 until 4) {
            val i = (start + k) % 4
            out[k * 2] = points[i * 2]
            out[k * 2 + 1] = points[i * 2 + 1]
        }
        return out
    }

    /** 자른 결과의 픽셀 크기 — 마주 보는 변 길이의 최댓값. */
    fun croppedSize(quad: FloatArray, widthPx: Int, heightPx: Int): Pair<Int, Int> {
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val xs = FloatArray(4) { quad[it * 2] * w }
        val ys = FloatArray(4) { quad[it * 2 + 1] * h }
        fun dist(a: Int, b: Int): Float {
            val dx = xs[a] - xs[b]
            val dy = ys[a] - ys[b]
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
        val outW = kotlin.math.max(dist(0, 1), dist(3, 2)).toInt().coerceAtLeast(1)
        val outH = kotlin.math.max(dist(0, 3), dist(1, 2)).toInt().coerceAtLeast(1)
        return Pair(outW, outH)
    }

    /**
     * 사진을 화면 영역 안에 꽉 차게(FIT_CENTER) 놓았을 때 사진이 차지하는 자리.
     * 돌려주는 값은 left, top, right, bottom 이다.
     *
     * 화면이나 사진 크기가 아직 0 이면 비어 있는 사각형(0,0,0,0)을 돌려준다.
     */
    fun fitRect(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int): FloatArray {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return floatArrayOf(0f, 0f, 0f, 0f)
        }
        val scale = kotlin.math.min(
            viewWidth.toFloat() / imageWidth,
            viewHeight.toFloat() / imageHeight
        )
        val drawW = imageWidth * scale
        val drawH = imageHeight * scale
        val left = (viewWidth - drawW) / 2f
        val top = (viewHeight - drawH) / 2f
        return floatArrayOf(left, top, left + drawW, top + drawH)
    }

    /**
     * 이미지 기준 0~1 좌표를 [fitRect] 가 돌려준 화면 사각형 위의 좌표로 옮긴다.
     * 사각형이 비어 있으면 모든 점이 (0,0) 으로 몰리므로 호출 쪽에서 먼저 확인해야 한다.
     */
    fun project(quad: FloatArray, rect: FloatArray): FloatArray {
        val out = FloatArray(8)
        val w = rect[2] - rect[0]
        val h = rect[3] - rect[1]
        for (i in 0 until 4) {
            out[i * 2] = rect[0] + quad[i * 2] * w
            out[i * 2 + 1] = rect[1] + quad[i * 2 + 1] * h
        }
        return out
    }

    /** 사각형이 이미지 전체와 (거의) 같은지 — 자르기를 하지 않은 상태 */
    fun isFull(quad: FloatArray?): Boolean {
        if (quad == null || quad.size != 8) return true
        val full = fullQuad()
        for (i in 0 until 8) {
            if (kotlin.math.abs(quad[i] - full[i]) > 0.01f) return false
        }
        return true
    }
}
