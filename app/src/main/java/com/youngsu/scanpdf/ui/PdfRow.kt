package com.youngsu.scanpdf.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.data.PdfItem
import com.youngsu.scanpdf.data.PdfLibrary
import com.youngsu.scanpdf.util.ByteSize
import java.text.DateFormat
import java.util.Calendar
import java.util.concurrent.Executors

/** 목록 한 줄(썸네일 + 이름 + 정보 + 더보기)을 만든다. */
object PdfRow {

    private val executor = Executors.newFixedThreadPool(2)

    fun build(
        activity: Activity,
        item: PdfItem,
        @DrawableRes leadingIcon: Int? = null,
        onClick: () -> Unit,
        onMore: (() -> Unit)?
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = Ui.rounded(activity, activity.getColor(R.color.surface_alt), 18f, activity.getColor(R.color.outline))
            setPadding(activity.dp(12f), activity.dp(12f), activity.dp(8f), activity.dp(12f))
            setOnClickListener { onClick() }
        }

        val thumb = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(46f), activity.dp(60f))
            background = Ui.rounded(activity, activity.getColor(R.color.surface_high), 8f, activity.getColor(R.color.outline_soft), ripple = false)
        }
        val placeholder = Ui.icon(
            activity,
            leadingIcon ?: R.drawable.ic_pdf,
            20f,
            activity.getColor(R.color.text_tertiary)
        ).apply { layoutParams = FrameLayout.LayoutParams(activity.dp(20f), activity.dp(20f), Gravity.CENTER) }
        thumb.addView(placeholder)

        val image = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            setPadding(activity.dp(2f), activity.dp(2f), activity.dp(2f), activity.dp(2f))
        }
        thumb.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(thumb)

        val texts = Ui.column(activity).apply {
            setPadding(activity.dp(13f), 0, activity.dp(6f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        }
        val name = Ui.text(activity, item.name, 15f, activity.getColor(R.color.text_primary), bold = true, maxLines = 1)
        val meta = Ui.text(activity, describe(activity, item), 11.8f, activity.getColor(R.color.text_secondary), maxLines = 1).apply {
            setPadding(0, activity.dp(4f), 0, 0)
        }
        texts.addView(name)
        texts.addView(meta)
        row.addView(texts)

        if (onMore != null) {
            val more = ImageView(activity).apply {
                setImageResource(R.drawable.ic_more)
                imageTintList = android.content.res.ColorStateList.valueOf(activity.getColor(R.color.text_tertiary))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(activity.dp(10f), activity.dp(10f), activity.dp(10f), activity.dp(10f))
                isClickable = true
                background = Ui.rounded(activity, Color.TRANSPARENT, 999f)
                setOnClickListener { onMore() }
            }
            row.addView(more, LinearLayout.LayoutParams(activity.dp(40f), activity.dp(40f)))
        }

        loadThumbnail(activity, item, image, placeholder)
        return row
    }

    fun describe(activity: Activity, item: PdfItem): String {
        val pages = if (item.pages == 1) activity.getString(R.string.page_single)
        else activity.getString(R.string.item_meta, item.pages, ByteSize.format(item.sizeBytes), relativeTime(activity, item.modified))
        return if (item.pages == 1) {
            activity.getString(R.string.item_meta, 1, ByteSize.format(item.sizeBytes), relativeTime(activity, item.modified))
        } else pages
    }

    fun relativeTime(activity: Activity, millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val time = android.text.format.DateFormat.getTimeFormat(activity).format(then.time)
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) else -99
        return when {
            dayDiff == 0 -> activity.getString(R.string.date_today, time)
            dayDiff == 1 -> activity.getString(R.string.date_yesterday, time)
            else -> android.text.format.DateFormat.getMediumDateFormat(activity).format(then.time) + " " + time
        }
    }

    private fun loadThumbnail(activity: Activity, item: PdfItem, image: ImageView, placeholder: View) {
        executor.execute {
            val bitmap: Bitmap? = PdfLibrary.Thumbnails.load(item.file, 160)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@runOnUiThread
                }
                if (bitmap != null && !bitmap.isRecycled) {
                    image.setImageBitmap(bitmap)
                    image.visibility = View.VISIBLE
                    placeholder.visibility = View.GONE
                }
            }
        }
    }

    /** 그룹 안내 문구 한 줄 */
    fun note(activity: Activity, textValue: String): TextView =
        TextView(activity).apply {
            text = textValue
            textSize = 12f
            setTextColor(activity.getColor(R.color.text_tertiary))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            val drawable = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_privacy)
            if (drawable != null) {
                drawable.setBounds(0, 0, activity.dp(13f), activity.dp(13f))
                drawable.setTint(activity.getColor(R.color.text_tertiary))
                setCompoundDrawables(drawable, null, null, null)
                compoundDrawablePadding = activity.dp(6f)
            }
        }

    fun viewIntentFor(activity: Activity, item: PdfItem): Intent =
        PdfLibrary(activity).viewIntent(item)
}
