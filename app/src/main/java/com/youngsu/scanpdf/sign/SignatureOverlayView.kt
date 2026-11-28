package com.youngsu.scanpdf.sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * PDF 쪽 위에 서명을 얹어 끌고 키우는 화면.
 *
 * 서명 자리는 쪽 안에서의 비율(0~1)로 기억하기 때문에, 화면에서 본 위치 그대로
 * 최종 PDF(훨씬 큰 해상도)에 같은 자리로 들어간다.
 */
class SignatureOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var pageBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var signature: Bitmap? = null
        set(value) {
            field = value
            if (value != null) {
                // 처음 얹을 때는 오른쪽 아래 서명 자리 크기로 (쪽 비율에 맞춰 높이를 계산)
                val w = 0.34f
                val ratio = imageRatio().coerceAtLeast(0.2f)
                val h = w * (value.height.toFloat() / value.width.coerceAtLeast(1)) / ratio
                placeFrac.set(0.60f, 0.78f - h, 0.60f + w, 0.78f)
                if (placeFrac.bottom > 0.97f) placeFrac.offset(0f, 0.97f - placeFrac.bottom)
            }
            invalidate()
        }

    /** 서명이 놓인 자리 (0~1 비율) */
    val placeFrac = RectF(0.60f, 0.66f, 0.94f, 0.78f)

    var onChange: (() -> Unit)? = null

    private val imageRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#803D8BFF")
        strokeWidth = resources.displayMetrics.density * 1.4f
    }
    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val centerX = placeFrac.centerX()
            val centerY = placeFrac.centerY()
            val factor = detector.scaleFactor.coerceIn(0.85f, 1.18f)
            var halfW = (placeFrac.width() / 2f) * factor
            var halfH = (placeFrac.height() / 2f) * factor
            halfW = halfW.coerceIn(0.04f, 0.9f)
            halfH = halfH.coerceIn(0.02f, 0.9f)
            placeFrac.set(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH)
            clamp()
            invalidate()
            onChange?.invoke()
            return true
        }
    })

    private fun imageRatio(): Float {
        val bitmap = pageBitmap ?: return 1f
        return bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutImage()
    }

    private fun layoutImage() {
        val bitmap = pageBitmap ?: return
        val pad = resources.displayMetrics.density * 6f
        val availableW = width - pad * 2
        val availableH = height - pad * 2
        if (availableW <= 0 || availableH <= 0) return
        val scale = minOf(availableW / bitmap.width, availableH / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        imageRect.set(left, top, left + w, top + h)
    }

    private fun destRect(): RectF = RectF(
        imageRect.left + placeFrac.left * imageRect.width(),
        imageRect.top + placeFrac.top * imageRect.height(),
        imageRect.left + placeFrac.right * imageRect.width(),
        imageRect.top + placeFrac.bottom * imageRect.height()
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageRect.isEmpty) layoutImage()
        val page = pageBitmap ?: return
        canvas.drawBitmap(page, null, imageRect, paint)
        val sign = signature ?: return
        val dest = destRect()
        // 서명 그림 비율 유지 (찌그러지지 않게 가운데 맞춤)
        val scale = minOf(dest.width() / sign.width, dest.height() / sign.height)
        val w = sign.width * scale
        val h = sign.height * scale
        val left = dest.left + (dest.width() - w) / 2f
        val top = dest.top + (dest.height() - h) / 2f
        canvas.drawBitmap(sign, null, RectF(left, top, left + w, top + h), paint)
        canvas.drawRect(RectF(left, top, left + w, top + h), border)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dest = destRect()
                // 조금 넉넉하게 잡아 손가락으로 잡기 쉽게
                val slack = resources.displayMetrics.density * 14f
                dest.inset(-slack, -slack)
                dragging = signature != null && dest.contains(event.x, event.y)
                lastX = event.x
                lastY = event.y
                if (dragging) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && !scaleDetector.isInProgress && imageRect.width() > 0f) {
                    val dx = (event.x - lastX) / imageRect.width()
                    val dy = (event.y - lastY) / imageRect.height()
                    placeFrac.offset(dx, dy)
                    clamp()
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                    onChange?.invoke()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    /** 서명이 쪽 밖으로 나가지 않게 */
    private fun clamp() {
        val w = placeFrac.width()
        val h = placeFrac.height()
        if (placeFrac.left < 0f) placeFrac.offset(-placeFrac.left, 0f)
        if (placeFrac.top < 0f) placeFrac.offset(0f, -placeFrac.top)
        if (placeFrac.right > 1f) placeFrac.offset(1f - placeFrac.right, 0f)
        if (placeFrac.bottom > 1f) placeFrac.offset(0f, 1f - placeFrac.bottom)
        if (placeFrac.width() != w || placeFrac.height() != h) {
            placeFrac.right = placeFrac.left + w
            placeFrac.bottom = placeFrac.top + h
        }
    }
}
