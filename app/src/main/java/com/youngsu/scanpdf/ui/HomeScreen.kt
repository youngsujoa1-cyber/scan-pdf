package com.youngsu.scanpdf.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.data.PdfLibrary
import com.youngsu.scanpdf.util.ByteSize
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str

/** 홈 — 카메라 스캔과 빠른 작업, 최근 PDF */
class HomeScreen(activity: MainActivity) : Screen(activity) {

    private val scroll: NestedScrollView
    private val content: LinearLayout
    private val recentContainer: LinearLayout
    private val recentHeader: View
    private val library = PdfLibrary(activity)

    init {
        content = Ui.column(activity).apply {
            setPadding(activity.dp(20f), activity.dp(10f), activity.dp(20f), activity.dp(28f))
        }
        scroll = NestedScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        content.addView(buildHeader(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(Ui.spacing(activity, 18f))
        content.addView(buildHero(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(buildGrid(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(18f)
        })

        recentHeader = buildRecentHeader()
        content.addView(recentHeader, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        recentContainer = Ui.column(activity)
        content.addView(recentContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(10f)
        })

        content.addView(PdfRow.note(activity, str(R.string.docs_storage_note)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(22f)
        })
    }

    override val view: View get() = scroll

    override fun setSystemInsets(topInset: Int) {
        scroll.setPadding(0, topInset + activity.dp(6f), 0, 0)
    }

    override fun refresh() = refreshRecent()

    // ------------------------------------------------------------------ UI

    private fun buildHeader(): View {
        val row = Ui.row(activity)
        val mark = FrameLayout(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                activity.getColor(R.color.accent),
                activity.getColor(R.color.emerald)
            )).apply { cornerRadius = activity.dpf(13f) }
            addView(Ui.icon(activity, R.drawable.ic_pdf, 21f, Color.WHITE).apply {
                layoutParams = FrameLayout.LayoutParams(activity.dp(21f), activity.dp(21f), Gravity.CENTER)
            })
        }
        row.addView(mark, LinearLayout.LayoutParams(activity.dp(40f), activity.dp(40f)))
        val texts = Ui.column(activity).apply {
            setPadding(activity.dp(12f), 0, 0, 0)
        }
        texts.addView(Ui.text(activity, str(R.string.app_name), 21f, activity.getColor(R.color.text_primary), bold = true, letterSpacing = -0.01f))
        texts.addView(Ui.text(activity, str(R.string.home_subtitle), 12f, activity.getColor(R.color.text_secondary)).apply {
            setPadding(0, activity.dp(3f), 0, 0)
        })
        row.addView(texts)
        row.addView(View(activity), LinearLayout.LayoutParams(0, 0).apply { weight = 1f })
        return row
    }

    private fun buildHero(): View {
        val card = FrameLayout(activity).apply {
            background = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_hero)
            val mask = GradientDrawable().apply { cornerRadius = activity.dpf(26f) }
            foreground = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask)
            isClickable = true
            isFocusable = true
            setOnClickListener { activity.startScan() }
        }
        card.addView(View(activity).apply {
            background = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_hero_glow)
        }, FrameLayout.LayoutParams(activity.dp(230f), activity.dp(230f), Gravity.TOP or Gravity.END).apply {
            topMargin = activity.dp(-70f)
            rightMargin = activity.dp(-60f)
        })

        val column = Ui.column(activity).apply {
            setPadding(activity.dp(20f), activity.dp(19f), activity.dp(20f), activity.dp(18f))
        }
        val badge = Ui.row(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        badge.addView(Ui.icon(activity, R.drawable.ic_scan, 13f, 0xFFD8E6FF.toInt()))
        badge.addView(Ui.text(activity, str(R.string.tab_scan).uppercase(), 10.5f, 0xFFCBDCF8.toInt(), bold = true, letterSpacing = 0.12f).apply {
            setPadding(activity.dp(6f), 0, 0, 0)
        })
        column.addView(badge)

        column.addView(Ui.text(activity, str(R.string.hero_title), 22f, Color.WHITE, bold = true, letterSpacing = -0.02f).apply {
            setPadding(0, activity.dp(9f), 0, 0)
        })
        column.addView(Ui.text(activity, str(R.string.hero_sub), 12.5f, 0xFFBFD2EC.toInt(), maxLines = 2).apply {
            setPadding(0, activity.dp(6f), 0, 0)
            maxWidth = activity.dp(210f)
        })

        val actionRow = Ui.row(activity).apply {
            setPadding(0, activity.dp(16f), 0, 0)
        }
        val start = Ui.text(activity, str(R.string.hero_action), 13.5f, activity.getColor(R.color.on_accent), bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = activity.dpf(999f)
                setColor(activity.getColor(R.color.accent))
            }
            setPadding(activity.dp(20f), activity.dp(10f), activity.dp(20f), activity.dp(10f))
        }
        actionRow.addView(start)
        actionRow.addView(View(activity), LinearLayout.LayoutParams(0, 0).apply { weight = 1f })
        val cameraCircle = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FFFFFF)
                setStroke(activity.dp(1f), 0x66FFFFFF)
            }
            addView(Ui.icon(activity, R.drawable.ic_camera, 22f, Color.WHITE).apply {
                layoutParams = FrameLayout.LayoutParams(activity.dp(22f), activity.dp(22f), Gravity.CENTER)
            })
        }
        actionRow.addView(cameraCircle, LinearLayout.LayoutParams(activity.dp(44f), activity.dp(44f)))
        column.addView(actionRow)

        card.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun buildGrid(): View {
        val wrap = Ui.column(activity)
        val row1 = Ui.row(activity)
        row1.addView(tile(R.drawable.ic_gallery, R.string.action_photos, R.string.action_photos_sub) { activity.startPhotoImport() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; rightMargin = activity.dp(6f) })
        row1.addView(tile(R.drawable.ic_merge, R.string.action_merge, R.string.action_merge_sub) { activity.startMerge() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; leftMargin = activity.dp(6f) })
        wrap.addView(row1)

        val row2 = Ui.row(activity)
        row2.addView(tile(R.drawable.ic_compress, R.string.action_compress, R.string.action_compress_sub) { activity.startCompress() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; rightMargin = activity.dp(6f) })
        row2.addView(tile(R.drawable.ic_open, R.string.action_open, R.string.action_open_sub) { goToDocs() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; leftMargin = activity.dp(6f) })
        wrap.addView(row2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(12f)
        })

        val row3 = Ui.row(activity)
        row3.addView(tile(R.drawable.ic_ocr, R.string.action_ocr, R.string.action_ocr_sub) { activity.startOcr() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; rightMargin = activity.dp(6f) })
        row3.addView(tile(R.drawable.ic_sign, R.string.action_sign, R.string.action_sign_sub) { activity.startSign() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; leftMargin = activity.dp(6f) })
        wrap.addView(row3, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(12f)
        })

        val row4 = Ui.row(activity)
        row4.addView(tile(R.drawable.ic_pages, R.string.action_organize, R.string.action_organize_sub) { activity.startOrganize() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        wrap.addView(row4, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(12f)
        })
        return wrap
    }

    private fun tile(iconRes: Int, titleRes: Int, subRes: Int, action: () -> Unit): View {
        val tile = Ui.column(activity).apply {
            background = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_tile)
            setPadding(activity.dp(15f), activity.dp(15f), activity.dp(15f), activity.dp(14f))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val icon = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = activity.dpf(12f)
                setColor(activity.getColor(R.color.accent_dim))
            }
            addView(Ui.icon(activity, iconRes, 20f, activity.getColor(R.color.accent)).apply {
                layoutParams = FrameLayout.LayoutParams(activity.dp(20f), activity.dp(20f), Gravity.CENTER)
            })
        }
        tile.addView(icon, LinearLayout.LayoutParams(activity.dp(38f), activity.dp(38f)))
        tile.addView(Ui.text(activity, str(titleRes), 14f, activity.getColor(R.color.text_primary), bold = true, maxLines = 1).apply {
            setPadding(0, activity.dp(11f), 0, 0)
        })
        tile.addView(Ui.text(activity, str(subRes), 11f, activity.getColor(R.color.text_tertiary), maxLines = 1).apply {
            setPadding(0, activity.dp(3f), 0, 0)
        })
        return tile
    }

    private fun buildRecentHeader(): View {
        val row = Ui.row(activity)
        row.addView(Ui.sectionTitle(activity, str(R.string.recent_title)))
        row.addView(View(activity), LinearLayout.LayoutParams(0, 0).apply { weight = 1f })
        row.addView(Ui.text(activity, str(R.string.see_all), 12f, activity.getColor(R.color.accent), bold = true).apply {
            setPadding(activity.dp(10f), activity.dp(6f), 0, activity.dp(6f))
            setOnClickListener { goToDocs() }
        })
        return row
    }

    // -------------------------------------------------------------- recent

    private fun refreshRecent() {
        recentContainer.removeAllViews()
        val items = library.recent(3)
        if (items.isEmpty()) {
            recentHeader.visibility = View.GONE
            recentContainer.addView(buildEmptyState(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            recentHeader.visibility = View.VISIBLE
            items.forEach { item ->
                recentContainer.addView(
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

    private fun buildEmptyState(): View {
        val column = Ui.column(activity).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_dashed)
            setPadding(activity.dp(22f), activity.dp(26f), activity.dp(22f), activity.dp(24f))
        }
        column.addView(Ui.icon(activity, R.drawable.ic_pdf, 26f, activity.getColor(R.color.text_tertiary)))
        column.addView(Ui.text(activity, str(R.string.empty_recent), 13f, activity.getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(12f), 0, 0)
            maxLines = 2
        })
        column.addView(Ui.button(activity, str(R.string.empty_recent_action), primary = true, iconRes = R.drawable.ic_camera) {
            activity.startScan()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(16f)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        return column
    }
}
