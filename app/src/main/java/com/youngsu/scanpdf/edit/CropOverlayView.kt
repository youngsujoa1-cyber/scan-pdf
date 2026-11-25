package com.youngsu.scanpdf.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.youngsu.scanpdf.pdf.ScanGeometry

/**
 * 자르기 화면 — 사진 위에 4점 사각형을 그리고 모서리를 끌어 조정한다.
 * 좌표는 이미지 기준 0~1 정규화 값이라 화면 크기와 무관하다.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            // 사진은 화면 크기가 정해진 뒤에(비동기 디코드) 들어오므로,
            // 여기서 자리(imageRect)를 다시 계산하지 않으면 비어 있는 사각형이 남아
            // 사진이 안 보이고 모서리 핸들이 (0,0) 에 몰린다 (1.0.2 버그).
            updateImageRect()
            invalidate()
        }

    /** 좌상 → 우상 → 우하 → 좌하 */
    var quad: FloatArray = ScanGeometry.fullQuad()
        set(value) {
            field = value.copyOf()
            invalidate()
        }

    var onQuadChanged: ((FloatArray) -> Unit)? = null

    private var dragging = -1
    private val imageRect = RectF()
    private val path = Path()
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.parseColor("#3D8BFF")
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.parseColor("#FF3D8BFF")
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.parseColor("#55FFFFFF")
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#99000000")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateImageRect()
    }

    private fun updateImageRect() {
        val image = bitmap ?: return
        if (width == 0 || height == 0) return
        val rect = ScanGeometry.fitRect(image.width, image.height, width, height)
        imageRect.set(rect[0], rect[1], rect[2], rect[3])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) updateImageRect()
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return
        canvas.drawBitmap(image, null, imageRect, Paint(Paint.FILTER_BITMAP_FLAG))

        val pts = toScreen(quad)

        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.moveTo(pts[0], pts[1])
        path.lineTo(pts[2], pts[3])
        path.lineTo(pts[4], pts[5])
        path.lineTo(pts[6], pts[7])
        path.close()
        canvas.drawPath(path, dimPaint)

        // 안내 격자
        for (i in 1..2) {
            val t = i / 3f
            canvas.drawLine(
                lerp(pts[0], pts[2], t), lerp(pts[1], pts[3], t),
                lerp(pts[6], pts[4], t), lerp(pts[7], pts[5], t), gridPaint
            )
            canvas.drawLine(
                lerp(pts[0], pts[6], t), lerp(pts[1], pts[7], t),
                lerp(pts[2], pts[4], t), lerp(pts[3], pts[5], t), gridPaint
            )
        }

        path.reset()
        path.moveTo(pts[0], pts[1])
        path.lineTo(pts[2], pts[3])
        path.lineTo(pts[4], pts[5])
        path.lineTo(pts[6], pts[7])
        path.close()
        canvas.drawPath(path, linePaint)

        for (i in 0 until 4) {
            canvas.drawCircle(pts[i * 2], pts[i * 2 + 1], dp(11f), handlePaint)
            canvas.drawCircle(pts[i * 2], pts[i * 2 + 1], dp(11f), handleRingPaint)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun toScreen(quad: FloatArray): FloatArray {
        return ScanGeometry.project(
            quad,
            floatArrayOf(imageRect.left, imageRect.top, imageRect.right, imageRect.bottom)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null || imageRect.width() <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = nearestCorner(event.x, event.y)
                if (dragging >= 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging >= 0) {
                    moveCorner(dragging, event.x, event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging >= 0) {
                    dragging = -1
                    performClick()
                    return true
                }
            }
        }
        return dragging >= 0
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun nearestCorner(x: Float, y: Float): Int {
        val pts = toScreen(quad)
        var best = -1
        var bestDistance = dp(34f)
        for (i in 0 until 4) {
            val dx = x - pts[i * 2]
            val dy = y - pts[i * 2 + 1]
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    private fun moveCorner(index: Int, x: Float, y: Float) {
        if (imageRect.width() <= 0f) return
        val nx = ((x - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
        val ny = ((y - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
        val q = quad.copyOf()
        q[index * 2] = nx
        q[index * 2 + 1] = ny
        enforceOrder(q, index)
        quad = q
        onQuadChanged?.invoke(quad)
    }

    /** 이웃한 모서리를 넘어가지 않게 최소 간격을 둔다 */
    private fun enforceOrder(q: FloatArray, index: Int) {
        val gap = 0.04f
        when (index) {
            0 -> { // 좌상: 우상보다 왼쪽, 좌하보다 위
                q[0] = q[0].coerceAtMost(q[2] - gap)
                q[1] = q[1].coerceAtMost(q[7] - gap)
            }
            1 -> { // 우상: 좌상보다 오른쪽, 우하보다 위
                q[2] = q[2].coerceAtLeast(q[0] + gap)
                q[3] = q[3].coerceAtMost(q[5] - gap)
            }
            2 -> { // 우하: 좌하보다 오른쪽, 우상보다 아래
                q[4] = q[4].coerceAtLeast(q[6] + gap)
                q[5] = q[5].coerceAtLeast(q[3] + gap)
            }
            else -> { // 좌하: 우하보다 왼쪽, 좌상보다 아래
                q[6] = q[6].coerceAtMost(q[4] - gap)
                q[7] = q[7].coerceAtLeast(q[1] + gap)
            }
        }
        for (i in 0 until 8) q[i] = q[i].coerceIn(0f, 1f)
    }

    fun resetToFull() {
        quad = ScanGeometry.fullQuad()
        onQuadChanged?.invoke(quad)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
