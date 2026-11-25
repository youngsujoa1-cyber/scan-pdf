package com.youngsu.scanpdf.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 카메라 화면 위에 문서 프레임을 그린다.
 * 프레임 바깥은 살짝 어둡게, 네 모서리는 밝은 브래킷으로 표시한다.
 */
class DocumentGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val frame = RectF()
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val marginX = w * 0.075f
        val marginY = h * 0.5f - (w - marginX * 2) * 0.68f
        frame.set(marginX, marginY.coerceAtLeast(h * 0.13f), w - marginX, h - marginY.coerceAtLeast(h * 0.13f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (frame.width() <= 0f) return

        // 프레임 바깥 어둡게
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRoundRect(frame, dp(14f), dp(14f), Path.Direction.CW)
        canvas.drawPath(path, dimPaint)

        canvas.drawRoundRect(frame, dp(14f), dp(14f), framePaint)

        val len = dp(26f)
        val r = dp(14f)
        val left = frame.left
        val top = frame.top
        val right = frame.right
        val bottom = frame.bottom

        // 좌상
        canvas.drawLine(left, top + r + len, left, top + r, cornerPaint)
        canvas.drawLine(left + r, top, left + r + len, top, cornerPaint)
        // 우상
        canvas.drawLine(right, top + r + len, right, top + r, cornerPaint)
        canvas.drawLine(right - r, top, right - r - len, top, cornerPaint)
        // 좌하
        canvas.drawLine(left, bottom - r - len, left, bottom - r, cornerPaint)
        canvas.drawLine(left + r, bottom, left + r + len, bottom, cornerPaint)
        // 우하
        canvas.drawLine(right, bottom - r - len, right, bottom - r, cornerPaint)
        canvas.drawLine(right - r, bottom, right - r - len, bottom, cornerPaint)

        // 가운데 짧은 기준선
        canvas.drawLine(width / 2f, top + r + dp(10f), width / 2f, top + r + dp(22f), framePaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
