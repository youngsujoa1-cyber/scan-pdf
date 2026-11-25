package com.youngsu.scanpdf.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.data.PdfItem
import com.youngsu.scanpdf.data.PdfLibrary
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str
import com.youngsu.scanpdf.util.ByteSize

/** 내 PDF — 앱이 만든 PDF 전체 목록 */
class DocsScreen(activity: MainActivity) : Screen(activity) {

    private val scroll: NestedScrollView
    private val content: LinearLayout
    private val listContainer: LinearLayout
    private val countView: android.widget.TextView
    private val library = PdfLibrary(activity)

    init {
        content = Ui.column(activity).apply {
            setPadding(activity.dp(20f), activity.dp(12f), activity.dp(20f), activity.dp(28f))
        }
        content.addView(Ui.text(activity, str(R.string.docs_title), 24f, activity.getColor(R.color.text_primary), bold = true, letterSpacing = -0.02f))
        countView = Ui.text(activity, "", 12f, activity.getColor(R.color.text_secondary)).apply {
            setPadding(0, activity.dp(5f), 0, 0)
        }
        content.addView(countView)

        listContainer = Ui.column(activity)
        content.addView(listContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(16f)
        })

        content.addView(PdfRow.note(activity, str(R.string.docs_storage_note)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(16f)
        })

        scroll = NestedScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    override val view: View get() = scroll

    override fun setSystemInsets(topInset: Int) {
        scroll.setPadding(0, topInset + activity.dp(6f), 0, 0)
    }

    override fun refresh() {
        val items = library.list()
        countView.text = if (items.isEmpty()) str(R.string.docs_storage_note)
        else str(R.string.docs_count, items.size, ByteSize.format(items.sumOf { it.sizeBytes }))

        listContainer.removeAllViews()
        if (items.isEmpty()) {
            listContainer.addView(buildEmpty())
        } else {
            items.forEach { item ->
                listContainer.addView(
                    PdfRow.build(activity, item, onClick = { openViewer(item) }, onMore = {
                        DocumentActions.menu(activity, item) { refresh() }
                    }),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = activity.dp(9f)
                    }
                )
            }
        }
    }

    private fun buildEmpty(): View {
        val column = Ui.column(activity).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_dashed)
            setPadding(activity.dp(22f), activity.dp(30f), activity.dp(22f), activity.dp(26f))
        }
        column.addView(Ui.icon(activity, R.drawable.ic_docs, 28f, activity.getColor(R.color.text_tertiary)))
        column.addView(Ui.text(activity, str(R.string.docs_empty_title), 15f, activity.getColor(R.color.text_primary), bold = true).apply {
            setPadding(0, activity.dp(12f), 0, 0)
        })
        column.addView(Ui.text(activity, str(R.string.docs_empty_body), 12.5f, activity.getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(6f), 0, 0)
        })
        column.addView(Ui.button(activity, str(R.string.empty_recent_action), primary = true, iconRes = R.drawable.ic_camera) {
            activity.startScan()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(18f)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        return column
    }
}
