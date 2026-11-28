package com.youngsu.scanpdf.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.LocaleList
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.data.AppLanguage
import com.youngsu.scanpdf.data.AppPrefs
import java.util.Locale

/**
 * 첫 실행 언어 선택 — 앱을 처음 열면 이 카드가 먼저 뜬다.
 *
 * 구름 날씨 레이더의 첫 실행 카드와 같은 방식이다.
 *  · 6개 언어를 2열 카드로 보여 주고, 고른 언어로 카드 문구가 곧바로 바뀌어 미리 보인다.
 *  · [시작하기]를 누르면 AppPrefs.languageOnboarded = true 로 저장하고 앱 언어를 적용한다.
 *    (언어가 바뀌면 액티비티가 다시 만들어지므로 저장을 먼저 한다)
 *  · 닫아도(뒤로 가기) 다시 묻지 않는다. 이후 설정 → 앱 언어에서 언제든 바꿀 수 있다.
 */
object LanguageOnboarding {

    fun show(activity: Activity) {
        val prefs = AppPrefs.get(activity)
        var tag = AppLanguage.currentTag()

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(false)
            setOnCancelListener { prefs.languageOnboarded = true }
        }

        val card = Ui.column(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = activity.dpf(24f)
                setColor(activity.getColor(R.color.surface))
                setStroke(activity.dp(1f), activity.getColor(R.color.outline))
            }
            setPadding(activity.dp(20f), activity.dp(22f), activity.dp(20f), activity.dp(18f))
        }

        // ------------------------------------------------------------ 머리말
        val badge = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = activity.dpf(14f)
                setColor(activity.getColor(R.color.accent_dim))
                setStroke(activity.dp(1f), activity.getColor(R.color.accent_soft))
            }
            addView(Ui.icon(activity, R.drawable.ic_language, 20f, activity.getColor(R.color.accent)).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(20f), activity.dp(20f)).apply { gravity = Gravity.CENTER }
            })
        }
        card.addView(badge, LinearLayout.LayoutParams(activity.dp(44f), activity.dp(44f)))

        val title = Ui.text(activity, "", 21f, activity.getColor(R.color.text_primary), bold = true, letterSpacing = -0.01f)
        card.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = activity.dp(14f) })

        val sub = Ui.text(activity, "", 12.5f, activity.getColor(R.color.text_secondary))
        card.addView(sub, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = activity.dp(7f) })

        // ------------------------------------------------------- 언어 선택 카드
        var render: () -> Unit = {}
        val chips = LinkedHashMap<String, Chip>()
        var index = 0
        while (index < AppLanguage.TAGS.size) {
            val line = Ui.row(activity)
            for (column in 0 until 2) {
                val code = AppLanguage.TAGS.getOrNull(index + column) ?: break
                // 원형 국기 + 그 나라 말 이름
                val chip = Ui.row(activity).apply {
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setPadding(activity.dp(8f), activity.dp(6f), activity.dp(8f), activity.dp(6f))
                    setOnClickListener {
                        if (tag != code) {
                            tag = code
                            render()
                        }
                    }
                }
                val flag = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val lang = AppLanguage.langOf(code)
                    if (lang != null) setImageResource(lang.flag)
                    else setImageResource(R.drawable.ic_language)
                    imageTintList = if (lang == null) android.content.res.ColorStateList.valueOf(activity.getColor(R.color.text_secondary)) else null
                }
                chip.addView(flag, LinearLayout.LayoutParams(activity.dp(18f), activity.dp(18f)).apply {
                    rightMargin = activity.dp(7f)
                })
                val label = Ui.text(activity, "", 12.5f, activity.getColor(R.color.text_primary), maxLines = 1)
                chip.addView(label)
                chips[code] = Chip(chip, label)
                line.addView(chip, LinearLayout.LayoutParams(0, activity.dp(50f)).apply {
                    weight = 1f
                    if (column == 0) rightMargin = activity.dp(8f)
                })
            }
            card.addView(line, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = activity.dp(8f) })
            index += 2
        }

        // ------------------------------------------------------------ 시작 버튼
        val start = Ui.text(activity, "", 15f, activity.getColor(R.color.on_accent), bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = activity.dp(52f)
            setPadding(activity.dp(14f), activity.dp(14f), activity.dp(14f), activity.dp(14f))
            background = GradientDrawable().apply {
                cornerRadius = activity.dpf(16f)
                setColor(activity.getColor(R.color.accent))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // 다시 그려지기 전에 먼저 저장해야 다음 실행부터 안 뜬다
                prefs.languageOnboarded = true
                dialog.dismiss()
                AppLanguage.apply(tag)
            }
        }
        card.addView(start, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = activity.dp(18f) })

        val note = Ui.text(activity, "", 10.5f, activity.getColor(R.color.text_tertiary)).apply {
            gravity = Gravity.CENTER
        }
        card.addView(note, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = activity.dp(10f) })

        // ------------------------------------------------------- 고른 언어로 그리기
        render = {
            val ctx = localized(activity, tag)
            title.text = ctx.getString(R.string.onboarding_language_title)
            sub.text = ctx.getString(R.string.onboarding_language_sub)
            note.text = ctx.getString(R.string.onboarding_language_note)
            start.text = ctx.getString(R.string.onboarding_start)
            AppLanguage.TAGS.forEach { code ->
                val chip = chips[code] ?: return@forEach
                val selected = code == tag
                val native = AppLanguage.langOf(code)?.nativeName ?: ctx.getString(R.string.language_system)
                chip.label.text = native
                chip.label.typeface = if (selected)
                    Typeface.create("sans-serif-medium", Typeface.BOLD)
                else Typeface.create("sans-serif", Typeface.NORMAL)
                chip.label.setTextColor(activity.getColor(if (selected) R.color.on_accent else R.color.text_primary))
                chip.root.background = GradientDrawable().apply {
                    cornerRadius = activity.dpf(14f)
                    setColor(activity.getColor(if (selected) R.color.accent else R.color.surface_high))
                    setStroke(activity.dp(1f), activity.getColor(if (selected) R.color.accent_strong else R.color.outline))
                }
            }
        }
        render()

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            clipToPadding = false
            addView(card, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        // 화면 가장자리에서 조금 띄워 둔다
        val outer = FrameLayout(activity).apply {
            setPadding(activity.dp(18f), activity.dp(24f), activity.dp(18f), activity.dp(24f))
            addView(scroll, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        dialog.setContentView(outer)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.72f)
                setGravity(Gravity.CENTER)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
        val metrics = activity.resources.displayMetrics
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.72f)
            setGravity(Gravity.CENTER)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (metrics.heightPixels * 0.86f).toInt())
        }
    }

    private class Chip(val root: LinearLayout, val label: TextView)

    /**
     * 고른 언어의 문구를 읽기 위한 컨텍스트 — 앱을 다시 그리지 않고도 카드가 미리 바뀐다.
     *
     * 여기서의 로케일은 문구를 미리 읽어 보는 임시 값일 뿐이고 앱 언어는 [AppLanguage] 가 바꾼다.
     * (lint 의 AppBundleLocaleChanges 는 언어 분할 번들을 쓰는 앱을 위한 경고라 해당 없음)
     */
    @SuppressLint("AppBundleLocaleChanges")
    private fun localized(activity: Activity, tag: String): Context {
        if (tag.isEmpty()) return activity
        return runCatching {
            val locale = Locale.forLanguageTag(tag)
            val config = Configuration(activity.resources.configuration)
            config.setLocale(locale)
            config.setLocales(LocaleList(locale))
            activity.createConfigurationContext(config)
        }.getOrDefault(activity)
    }
}
