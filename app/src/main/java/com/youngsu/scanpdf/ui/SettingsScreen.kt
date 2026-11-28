package com.youngsu.scanpdf.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.scanpdf.BuildConfig
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.ads.AdConfig
import com.youngsu.scanpdf.data.AppLanguage
import com.youngsu.scanpdf.data.AppPrefs
import com.youngsu.scanpdf.pdf.PdfQuality
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str

/** 설정 — 화질, 개인정보, 저장 위치, 앱 정보 */
class SettingsScreen(activity: MainActivity) : Screen(activity) {

    private val scroll: NestedScrollView
    private val content: LinearLayout
    private val qualityRows = LinkedHashMap<PdfQuality, LinearLayout>()
    private lateinit var languageValue: TextView
    private lateinit var languageFlag: ImageView
    private val prefs = AppPrefs.get(activity)

    init {
        content = Ui.column(activity).apply {
            setPadding(activity.dp(20f), activity.dp(12f), activity.dp(20f), activity.dp(30f))
        }
        scroll = NestedScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.addView(Ui.text(activity, str(R.string.settings_title), 24f, activity.getColor(R.color.text_primary), bold = true, letterSpacing = -0.02f))

        content.addView(section(str(R.string.settings_quality)))
        content.addView(qualityCard(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(section(str(R.string.settings_language)))
        content.addView(languageCard(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(Ui.text(activity, str(R.string.settings_language_note), 11f, activity.getColor(R.color.text_tertiary)).apply {
            setPadding(activity.dp(6f), activity.dp(8f), activity.dp(6f), 0)
        })

        content.addView(section(str(R.string.settings_privacy)))
        content.addView(infoCard(
            iconRes = R.drawable.ic_privacy,
            title = str(R.string.settings_privacy),
            body = str(R.string.settings_privacy_body)
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(section(str(R.string.settings_storage)))
        content.addView(infoCard(
            iconRes = R.drawable.ic_storage,
            title = str(R.string.settings_storage),
            body = str(R.string.settings_storage_body)
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(section(str(R.string.settings_ads)))
        content.addView(infoCard(
            iconRes = R.drawable.ic_info,
            title = if (AdConfig.isTestMode()) str(R.string.settings_ads_test) else str(R.string.settings_ads_live),
            body = str(R.string.settings_ads_body),
            titleColor = activity.getColor(if (AdConfig.isTestMode()) R.color.warn else R.color.emerald)
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(section(str(R.string.settings_guide)))
        content.addView(guideCard(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(section(str(R.string.settings_about)))
        content.addView(infoCard(
            iconRes = R.drawable.ic_file,
            title = str(R.string.app_name) + " · " + str(R.string.settings_version, BuildConfig.VERSION_NAME),
            body = BuildConfig.APPLICATION_ID
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(Ui.text(activity, "© 2026 Topster", 10.5f, activity.getColor(R.color.text_tertiary)).apply {
            setPadding(activity.dp(4f), activity.dp(14f), 0, 0)
        })
    }

    override val view: View get() = scroll

    override fun setSystemInsets(topInset: Int) {
        scroll.setPadding(0, topInset + activity.dp(6f), 0, 0)
    }

    override fun refresh() {
        qualityRows.forEach { (quality, row) ->
            paintQualityRow(row, quality == prefs.quality)
        }
        if (::languageValue.isInitialized) {
            languageValue.text = languageLabel()
            val tag = AppLanguage.currentTag()
            val lang = AppLanguage.langOf(tag)
            languageFlag.visibility = if (lang == null) View.GONE else View.VISIBLE
            if (lang != null) languageFlag.setImageResource(lang.flag)
        }
    }

    // ------------------------------------------------------------ 언어

    /** 언어 카드 — 누르면 기기 언어 따르기 / 한국어 / English / 日本語 / 中文 / Español 중 고른다 */
    private fun languageCard(): View {
        val row = Ui.row(activity).apply {
            background = Ui.rounded(activity, activity.getColor(R.color.surface_alt), 20f, activity.getColor(R.color.outline))
            setPadding(activity.dp(14f), activity.dp(14f), activity.dp(14f), activity.dp(14f))
            isClickable = true
            isFocusable = true
            setOnClickListener { showLanguagePicker() }
        }
        val iconWrap = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = activity.dpf(11f)
                setColor(activity.getColor(R.color.surface_high))
            }
            addView(Ui.icon(activity, R.drawable.ic_language, 18f, activity.getColor(R.color.text_secondary)).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(18f), activity.dp(18f)).apply { gravity = Gravity.CENTER }
            })
        }
        row.addView(iconWrap, LinearLayout.LayoutParams(activity.dp(36f), activity.dp(36f)).apply {
            rightMargin = activity.dp(12f)
        })
        val texts = Ui.column(activity)
        texts.addView(Ui.text(activity, str(R.string.language_title), 13.5f, activity.getColor(R.color.text_primary), bold = true))
        languageValue = Ui.text(activity, languageLabel(), 12f, activity.getColor(R.color.text_secondary)).apply {
            setPadding(0, activity.dp(4f), 0, 0)
        }
        texts.addView(languageValue)
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f })
        // 지금 고른 언어의 원형 국기
        languageFlag = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.flag_ko)
            visibility = View.GONE
        }
        row.addView(languageFlag, LinearLayout.LayoutParams(activity.dp(22f), activity.dp(22f)).apply {
            rightMargin = activity.dp(8f)
        })
        row.addView(Ui.icon(activity, R.drawable.ic_chevron_right, 18f, activity.getColor(R.color.text_tertiary)))
        return row
    }

    /** 선택 목록에 보여 줄 언어 이름 — 각 언어는 그 나라 말로 적는다 */
    private fun languageLabel(tag: String = AppLanguage.currentTag()): String =
        if (tag.isEmpty()) str(R.string.language_system) else AppLanguage.langOf(tag)?.nativeName ?: "English"

    /**
     * 언어 고르기 — 13개(기기 언어 따르기 + 12개 언어)를 원형 국기와 함께 보여 준다.
     * 고르면 그 자리에서 앱 전체가 새 언어로 다시 그려진다.
     */
    private fun showLanguagePicker() {
        val list = Ui.column(activity).apply {
            setPadding(activity.dp(8f), activity.dp(6f), activity.dp(8f), activity.dp(6f))
        }
        var dialogRef: androidx.appcompat.app.AlertDialog? = null

        fun rowFor(tag: String, flagRes: Int?, name: String) {
            val selected = tag == AppLanguage.currentTag()
            val row = Ui.row(activity).apply {
                setPadding(activity.dp(10f), activity.dp(11f), activity.dp(10f), activity.dp(11f))
                background = Ui.rounded(
                    activity,
                    activity.getColor(if (selected) R.color.accent_dim else android.graphics.Color.TRANSPARENT),
                    14f
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialogRef?.dismiss()
                    AppLanguage.apply(tag)
                }
            }
            if (flagRes != null) {
                row.addView(ImageView(activity).apply {
                    setImageResource(flagRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(activity.dp(22f), activity.dp(22f)).apply {
                    rightMargin = activity.dp(12f)
                })
            } else {
                row.addView(Ui.icon(activity, R.drawable.ic_language, 20f, activity.getColor(R.color.text_secondary)), LinearLayout.LayoutParams(activity.dp(22f), activity.dp(22f)).apply {
                    rightMargin = activity.dp(12f)
                })
            }
            row.addView(Ui.text(activity, name, 14f, activity.getColor(R.color.text_primary), bold = selected),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f })
            if (selected) {
                row.addView(Ui.icon(activity, R.drawable.ic_check, 17f, activity.getColor(R.color.accent)))
            }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        rowFor("", null, str(R.string.language_system))
        AppLanguage.LANGS.forEach { lang -> rowFor(lang.tag, lang.flag, lang.nativeName) }

        val scroll = ScrollView(activity).apply {
            addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setPadding(activity.dp(6f), 0, activity.dp(6f), 0)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.language_title)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialogRef = dialog
        dialog.show()
        // 긴 목록이 화면을 넘지 않게 높이를 제한한다
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.72f).toInt()
        )
    }

    private fun section(title: String): View =
        Ui.sectionTitle(activity, title).apply {
            setPadding(activity.dp(4f), activity.dp(24f), 0, activity.dp(10f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    private fun qualityCard(): View {
        val card = Ui.column(activity).apply {
            background = Ui.rounded(activity, activity.getColor(R.color.surface_alt), 20f, activity.getColor(R.color.outline), ripple = false)
            setPadding(activity.dp(6f), activity.dp(6f), activity.dp(6f), activity.dp(6f))
        }
        val options = listOf(
            Triple(PdfQuality.HIGH, R.string.quality_high, R.string.quality_high_sub),
            Triple(PdfQuality.NORMAL, R.string.quality_normal, R.string.quality_normal_sub),
            Triple(PdfQuality.SMALL, R.string.quality_small, R.string.quality_small_sub)
        )
        options.forEachIndexed { index, (quality, title, sub) ->
            val row = Ui.row(activity).apply {
                setPadding(activity.dp(12f), activity.dp(13f), activity.dp(12f), activity.dp(13f))
                background = Ui.rounded(activity, Color.TRANSPARENT, 15f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.quality = quality
                    refresh()
                }
            }
            val dot = View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(activity.dp(1.6f), activity.getColor(R.color.text_tertiary))
                }
            }
            row.addView(dot, LinearLayout.LayoutParams(activity.dp(18f), activity.dp(18f)).apply {
                rightMargin = activity.dp(12f)
            })
            val texts = Ui.column(activity)
            texts.addView(Ui.text(activity, str(title), 14.5f, activity.getColor(R.color.text_primary), bold = true))
            texts.addView(Ui.text(activity, str(sub), 11.5f, activity.getColor(R.color.text_tertiary)).apply {
                setPadding(0, activity.dp(3f), 0, 0)
            })
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f })
            row.addView(Ui.icon(activity, R.drawable.ic_check, 18f, activity.getColor(R.color.accent)).apply {
                visibility = View.INVISIBLE
            })
            qualityRows[quality] = row
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (index != options.lastIndex) {
                card.addView(View(activity).apply { setBackgroundColor(activity.getColor(R.color.divider)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(1f)).apply {
                        leftMargin = activity.dp(42f)
                    })
            }
        }
        refresh()
        return card
    }

    /** 선택된 화질 행 표시 */
    private fun paintQualityRow(row: LinearLayout, selected: Boolean) {
        val dot = row.getChildAt(0)
        (dot.background as? GradientDrawable)?.let { shape ->
            shape.setColor(if (selected) activity.getColor(R.color.accent) else Color.TRANSPARENT)
            shape.setStroke(activity.dp(1.6f), if (selected) activity.getColor(R.color.accent) else activity.getColor(R.color.text_tertiary))
        }
        dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
            width = activity.dp(if (selected) 12f else 18f)
            height = activity.dp(if (selected) 12f else 18f)
            rightMargin = activity.dp(if (selected) 15f else 12f)
        }
        dot.requestLayout()
        val check = row.getChildAt(row.childCount - 1)
        check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        row.background = if (selected)
            Ui.rounded(activity, activity.getColor(R.color.accent_dim), 15f, activity.getColor(R.color.accent_soft), ripple = true)
        else Ui.rounded(activity, Color.TRANSPARENT, 15f)
    }

    private fun infoCard(
        iconRes: Int,
        title: String,
        body: String,
        titleColor: Int = activity.getColor(R.color.text_primary)
    ): View {
        val row = Ui.row(activity).apply {
            background = Ui.rounded(activity, activity.getColor(R.color.surface_alt), 20f, activity.getColor(R.color.outline), ripple = false)
            setPadding(activity.dp(14f), activity.dp(14f), activity.dp(14f), activity.dp(14f))
            gravity = Gravity.TOP
        }
        val iconWrap = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = activity.dpf(11f)
                setColor(activity.getColor(R.color.surface_high))
            }
            addView(Ui.icon(activity, iconRes, 18f, activity.getColor(R.color.text_secondary)).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(18f), activity.dp(18f)).apply { gravity = Gravity.CENTER }
            })
        }
        row.addView(iconWrap, LinearLayout.LayoutParams(activity.dp(36f), activity.dp(36f)).apply {
            rightMargin = activity.dp(12f)
        })
        val texts = Ui.column(activity)
        texts.addView(Ui.text(activity, title, 13.5f, titleColor, bold = true))
        texts.addView(Ui.text(activity, body, 12f, activity.getColor(R.color.text_secondary)).apply {
            setPadding(0, activity.dp(4f), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f })
        return row
    }

    private fun guideCard(): View {
        val column = Ui.column(activity).apply {
            background = Ui.rounded(activity, activity.getColor(R.color.surface_alt), 20f, activity.getColor(R.color.outline), ripple = false)
            setPadding(activity.dp(16f), activity.dp(6f), activity.dp(16f), activity.dp(10f))
        }
        listOf(R.string.guide_1, R.string.guide_2, R.string.guide_3, R.string.guide_4, R.string.guide_5).forEach { res ->
            val row = Ui.row(activity).apply {
                gravity = Gravity.TOP
                setPadding(0, activity.dp(9f), 0, activity.dp(9f))
            }
            val bullet = View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(activity.getColor(R.color.accent))
                }
            }
            row.addView(bullet, LinearLayout.LayoutParams(activity.dp(5f), activity.dp(5f)).apply {
                rightMargin = activity.dp(10f)
                topMargin = activity.dp(7f)
            })
            row.addView(Ui.text(activity, str(res), 12.5f, activity.getColor(R.color.text_secondary)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f })
            column.addView(row)
        }
        return column
    }
}
