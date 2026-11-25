package com.youngsu.scanpdf.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.youngsu.scanpdf.pdf.Filters
import com.youngsu.scanpdf.pdf.ScanGeometry
import java.io.File

/** 편집 중인 한 페이지의 사진과 보정 상태 */
class ScanPage(
    val file: File,
    var rotation: Int = 0,
    var quad: FloatArray? = null,
    var filter: Filters.Kind = Filters.Kind.DOCUMENT
) {
    val id: Long = System.nanoTime()
}

/**
 * 사진 → 화면/PDF 용 비트맵.
 *
 * 같은 순서로 항상 처리한다: EXIF 회전 → 자르기(원근 보정) → 화면 회전 → 필터.
 * 미리보기와 최종 PDF 가 이 함수를 함께 쓰므로 "보이는 그대로" 저장된다.
 */
object PageRenderer {

    fun decode(file: File, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("이미지를 읽을 수 없습니다: ${file.name}")
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalStateException("이미지를 읽을 수 없습니다: ${file.name}")
        return applyExifRotation(decoded, file)
    }

    /** EXIF 방향 정보를 실제 픽셀에 반영한다. */
    fun applyExifRotation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) }
            .getOrElse { bitmap }
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    /** 90° 단위 회전 */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val step = ((degrees % 360) + 360) % 360
        if (step == 0) return bitmap
        val matrix = Matrix().apply { postRotate(step.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** 4점 사각형을 직사각형으로 펴 준다(원근 보정). */
    fun warp(bitmap: Bitmap, quad: FloatArray?): Bitmap {
        if (quad == null || ScanGeometry.isFull(quad)) return bitmap
        val safe = ScanGeometry.clamp(quad) ?: return bitmap
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val src = floatArrayOf(
            safe[0] * w, safe[1] * h,
            safe[2] * w, safe[3] * h,
            safe[4] * w, safe[5] * h,
            safe[6] * w, safe[7] * h
        )
        val cropped = ScanGeometry.croppedSize(safe, bitmap.width, bitmap.height)
        var outW = cropped.first.coerceAtLeast(1)
        var outH = cropped.second.coerceAtLeast(1)
        // 비율을 유지한 채 너무 큰 결과만 줄인다
        val longSide = maxOf(outW, outH)
        if (longSide > MAX_SIDE) {
            val factor = MAX_SIDE.toFloat() / longSide
            outW = (outW * factor).toInt().coerceAtLeast(1)
            outH = (outH * factor).toInt().coerceAtLeast(1)
        }
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return bitmap
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(
            bitmap,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return output
    }

    /**
     * 페이지를 주어진 긴 변 픽셀 크기로 그린다.
     * [maxDim] 이 원본보다 크면 원본 크기를 넘기지 않는다.
     */
    fun render(source: File, rotation: Int, quad: FloatArray?, filter: Filters.Kind, maxDim: Int): Bitmap {
        // 자르기·회전 여유를 두고 조금 크게 읽는다
        var bitmap = decode(source, (maxDim * 1.15f).toInt().coerceAtLeast(320))
        // 디코더가 2배까지 크게 줄 수 있어, 작업 전에 한 번 줄여 메모리를 아낀다
        val prescaled = scaleDown(bitmap, (maxDim * 1.25f).toInt())
        if (prescaled !== bitmap) {
            bitmap.recycle()
            bitmap = prescaled
        }
        var current = bitmap

        val warped = warp(current, quad)
        if (warped !== current) {
            current.recycle()
            current = warped
        }
        bitmap = current

        val rotated = rotate(current, rotation)
        if (rotated !== current) {
            current.recycle()
            current = rotated
        }

        val scaled = scaleDown(current, maxDim)
        if (scaled !== current) {
            current.recycle()
            current = scaled
        }

        if (filter != Filters.Kind.ORIGINAL) {
            val pixels = IntArray(current.width * current.height)
            current.getPixels(pixels, 0, current.width, 0, 0, current.width, current.height)
            Filters.apply(filter, pixels, current.width, current.height)
            current.setPixels(pixels, 0, current.width, 0, 0, current.width, current.height)
        }
        return current
    }

    fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longSide
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        return scaled
    }

    /** JPEG 로 압축한 바이트 */
    fun toJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = java.io.ByteArrayOutputStream(bitmap.byteCount / 3)
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)) {
            throw IllegalStateException("JPEG 로 저장하지 못했습니다")
        }
        return stream.toByteArray()
    }

    private const val MAX_SIDE = 6000
}
