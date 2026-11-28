package com.youngsu.scanpdf.sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 손가락 서명 패드.
 *
 * 획(stroke)마다 색과 굵기를 기억해 두었다가 그대로 다시 그린다 — 되돌리기·지우기가 쉽고,
 * 저장할 때는 잉크가 있는 부분만 잘라 투명 배경 PNG 로 만든다(페이지 위에 얹기 좋게).
 */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Stroke(val color: Int, val width: Float, val points: MutableList<PointF>)

    private val strokes = ArrayList<Stroke>()
    private var current: Stroke? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var penColor: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var penWidth: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        setBackgroundColor(Color.WHITE)
        if (penWidth <= 0f) penWidth = defaultWidth()
        isFocusable = true
    }

    private fun defaultWidth(): Float = (resources.displayMetrics.density * 3.2f).coerceAtLeast(2f)

    val isEmpty: Boolean get() = strokes.isEmpty()

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }
    }

    fun clear() {
        strokes.clear()
        current = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { drawStroke(canvas, it, 0f, 0f, 1f) }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke, dx: Float, dy: Float, scale: Float) {
        if (stroke.points.isEmpty()) return
        paint.color = stroke.color
        paint.strokeWidth = stroke.width * scale
        if (stroke.points.size == 1) {
            val p = stroke.points[0]
            canvas.drawCircle(p.x * scale + dx, p.y * scale + dy, paint.strokeWidth / 2f, paint)
            return
        }
        val path = Path()
        val first = stroke.points[0]
        path.moveTo(first.x * scale + dx, first.y * scale + dy)
        for (i in 1 until stroke.points.size) {
            val p = stroke.points[i]
            path.lineTo(p.x * scale + dx, p.y * scale + dy)
        }
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val stroke = Stroke(penColor, penWidth, mutableListOf(PointF(event.x, event.y)))
                strokes.add(stroke)
                current = stroke
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = current ?: return true
                // 너무 촘촘한 점은 건너뛴다 (저장 용량·그리기 속도)
                val last = stroke.points.last()
                if (Math.hypot((event.x - last.x).toDouble(), (event.y - last.y).toDouble()) > 1.2) {
                    stroke.points.add(PointF(event.x, event.y))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current = null
                invalidate()
            }
        }
        return true
    }

    /**
     * 잉크 부분만 잘라 낸 투명 배경 비트맵. 서명이 없으면 null.
     * [maxSize] 는 결과의 긴 변 픽셀 상한이다.
     */
    fun toTrimmedBitmap(paddingPx: Float = 10f, maxSize: Int = 900): Bitmap? {
        if (strokes.isEmpty()) return null
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        strokes.forEach { stroke ->
            val half = stroke.width / 2f
            stroke.points.forEach { p ->
                if (p.x - half < left) left = p.x - half
                if (p.y - half < top) top = p.y - half
                if (p.x + half > right) right = p.x + half
                if (p.y + half > bottom) bottom = p.y + half
            }
        }
        if (left > right || top > bottom) return null
        left -= paddingPx
        top -= paddingPx
        right += paddingPx
        bottom += paddingPx
        val width = (right - left).coerceAtLeast(8f)
        val height = (bottom - top).coerceAtLeast(8f)
        val scale = if (maxOf(width, height) > maxSize) maxSize / maxOf(width, height) else 1f
        val outWidth = (width * scale).toInt().coerceAtLeast(1)
        val outHeight = (height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        strokes.forEach { drawStroke(canvas, it, -left * scale, -top * scale, scale) }
        return bitmap
    }

    /** 잉크가 있는 영역(패드 좌표). 없으면 null */
    fun inkBounds(): RectF? {
        if (strokes.isEmpty()) return null
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        strokes.forEach { stroke ->
            stroke.points.forEach { p ->
                if (p.x < left) left = p.x
                if (p.y < top) top = p.y
                if (p.x > right) right = p.x
                if (p.y > bottom) bottom = p.y
            }
        }
        return if (left <= right) RectF(left, top, right, bottom) else null
    }
}
