package com.youngsu.scanpdf.sign

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.ui.Ui
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.dpf
import com.youngsu.scanpdf.ui.str

/**
 * 서명 만들기 창 — 손가락으로 쓰거나, 저장해 둔 서명을 고른다.
 * 확인을 누르면 잘라 낸 투명 배경 PNG 비트맵을 [onPicked] 로 돌려준다.
 */
object SignatureDialog {

    private val COLORS = listOf(
        Color.BLACK to R.string.sign_pen_black,
        Color.parseColor("#1A56DB") to R.string.sign_pen_blue,
        Color.parseColor("#D6273C") to R.string.sign_pen_red
    )

    fun show(activity: Activity, onPicked: (Bitmap) -> Unit) {
        val store = SignatureStore(activity)
        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }

        val card = Ui.column(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = activity.dpf(24f)
                setColor(activity.getColor(R.color.surface))
                setStroke(activity.dp(1f), activity.getColor(R.color.outline))
            }
            setPadding(activity.dp(18f), activity.dp(18f), activity.dp(18f), activity.dp(14f))
        }

        card.addView(Ui.text(activity, activity.str(R.string.sign_draw_title), 18f, activity.getColor(R.color.text_primary), bold = true))
        card.addView(Ui.text(activity, activity.str(R.string.sign_draw_hint), 12f, activity.getColor(R.color.text_secondary)).apply {
            setPadding(0, activity.dp(6f), 0, 0)
        })

        // 서명 패드
        val pad = SignaturePadView(activity).apply {
            background = Ui.rounded(activity, Color.WHITE, 14f, activity.getColor(R.color.outline), ripple = false)
        }
        card.addView(pad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(170f)).apply {
            topMargin = activity.dp(14f)
        })

        // 펜 색
        val colorRow = Ui.row(activity).apply { setPadding(0, activity.dp(12f), 0, 0) }
        val dots = ArrayList<View>()
        COLORS.forEachIndexed { index, (color, label) ->
            val dot = View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(activity.dp(2f), Color.TRANSPARENT)
                }
                isClickable = true
                isFocusable = true
                contentDescription = activity.getString(label)
                setOnClickListener {
                    pad.penColor = color
                    dots.forEachIndexed { i, view ->
                        (view.background as? GradientDrawable)?.setStroke(
                            activity.dp(2f),
                            if (i == index) activity.getColor(R.color.accent) else Color.TRANSPARENT
                        )
                    }
                }
            }
            dots.add(dot)
            colorRow.addView(dot, LinearLayout.LayoutParams(activity.dp(26f), activity.dp(26f)).apply {
                rightMargin = activity.dp(10f)
            })
        }
        colorRow.addView(View(activity), LinearLayout.LayoutParams(0, 0).apply { weight = 1f })
        colorRow.addView(Ui.text(activity, activity.str(R.string.sign_undo), 12.5f, activity.getColor(R.color.text_secondary), bold = true).apply {
            setPadding(activity.dp(10f), activity.dp(6f), activity.dp(10f), activity.dp(6f))
            setOnClickListener { pad.undo() }
        })
        colorRow.addView(Ui.text(activity, activity.str(R.string.sign_clear), 12.5f, activity.getColor(R.color.text_secondary), bold = true).apply {
            setPadding(activity.dp(10f), activity.dp(6f), activity.dp(4f), activity.dp(6f))
            setOnClickListener { pad.clear() }
        })
        card.addView(colorRow)
        (dots.firstOrNull()?.background as? GradientDrawable)?.setStroke(activity.dp(2f), activity.getColor(R.color.accent))

        // 저장해 둔 서명
        val saved = store.list()
        if (saved.isNotEmpty()) {
            card.addView(Ui.sectionTitle(activity, activity.str(R.string.sign_saved_section)).apply {
                setPadding(activity.dp(2f), activity.dp(14f), 0, activity.dp(8f))
            })
            val scroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
            val row = Ui.row(activity)
            saved.forEach { file ->
                val bitmap = store.load(file) ?: return@forEach
                val thumb = ImageView(activity).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(activity.dp(6f), activity.dp(4f), activity.dp(6f), activity.dp(4f))
                    background = Ui.rounded(activity, Color.WHITE, 10f, activity.getColor(R.color.outline))
                    setOnClickListener {
                        dialog.dismiss()
                        onPicked(bitmap)
                    }
                }
                row.addView(thumb, LinearLayout.LayoutParams(activity.dp(84f), activity.dp(46f)).apply {
                    rightMargin = activity.dp(8f)
                })
            }
            scroll.addView(row)
            card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // 버튼
        val buttons = Ui.row(activity).apply { setPadding(0, activity.dp(16f), 0, 0) }
        buttons.addView(
            Ui.button(activity, activity.str(R.string.cancel), primary = false) { dialog.dismiss() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; rightMargin = activity.dp(8f) }
        )
        val confirm = Ui.button(activity, activity.str(R.string.sign_use), primary = true) {
            val bitmap = pad.toTrimmedBitmap()
            if (bitmap != null) {
                store.save(bitmap)
                dialog.dismiss()
                onPicked(bitmap)
            }
        }
        buttons.addView(confirm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; leftMargin = activity.dp(8f) })
        card.addView(buttons)

        val outer = FrameLayout(activity).apply {
            setPadding(activity.dp(16f), activity.dp(20f), activity.dp(16f), activity.dp(20f))
            addView(ScrollView(activity).apply {
                addView(card, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog.setContentView(outer)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.7f)
            setGravity(Gravity.CENTER)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        // 화면이 좁으면 카드가 넘칠 수 있어 스크롤로 감쌌다 — 패드 높이는 고정
    }
}
