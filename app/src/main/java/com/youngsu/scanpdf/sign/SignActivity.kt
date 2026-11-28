package com.youngsu.scanpdf.sign

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.ads.AdManager
import com.youngsu.scanpdf.data.AppPrefs
import com.youngsu.scanpdf.data.PdfItem
import com.youngsu.scanpdf.data.PdfLibrary
import com.youngsu.scanpdf.pdf.PdfEngine
import com.youngsu.scanpdf.ui.Dialogs
import com.youngsu.scanpdf.ui.Ui
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str
import com.youngsu.scanpdf.util.ByteSize
import com.youngsu.scanpdf.util.FileNames
import com.youngsu.scanpdf.viewer.PdfViewerActivity
import java.io.File
import java.util.concurrent.Executors

/**
 * PDF 전자 서명 — 한 쪽 위에 서명을 얹어 새 PDF 로 저장한다.
 *
 *  · 서명은 손가락으로 새로 쓰거나, 저장해 둔 서명을 골라 쓴다.
 *  · 얹은 서명은 끌어서 옮기고 두 손가락으로 키울 수 있다.
 *  · 원본은 그대로 두고 "이름 (서명)" 사본을 만든다.
 */
class SignActivity : AppCompatActivity() {

    private lateinit var overlay: SignatureOverlayView
    private lateinit var titleView: TextView
    private lateinit var headerSub: TextView
    private lateinit var pageLabel: TextView
    private lateinit var hintView: TextView
    private lateinit var pageNav: LinearLayout
    private lateinit var saveButton: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var library: PdfLibrary
    private lateinit var item: PdfItem
    private var pageIndex = 0
    private var pageCount = 0
    private var signature: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val file = File(intent.getStringExtra(EXTRA_PATH) ?: return finish())
        if (!file.exists()) {
            finish()
            return
        }
        library = PdfLibrary(this)
        item = library.item(file)
        pageIndex = intent.getIntExtra(EXTRA_PAGE, 0)
        pageCount = item.pages

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
        }
        setContentView(root)

        // ------------------------------------------------------------- 상단 바
        val topBar = Ui.row(this).apply { setPadding(dp(8f), dp(6f), dp(10f), dp(6f)) }
        val back = Ui.icon(this, R.drawable.ic_back, 20f, getColor(R.color.text_primary)).apply {
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
            setOnClickListener { confirmLeave() }
        }
        topBar.addView(back, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        val titles = Ui.column(this).apply {
            setPadding(dp(6f), 0, dp(6f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        }
        titleView = Ui.text(this, str(R.string.sign_title), 16f, getColor(R.color.text_primary), bold = true, maxLines = 1)
        headerSub = Ui.text(this, item.name, 11.5f, getColor(R.color.text_secondary), maxLines = 1)
        titles.addView(titleView)
        titles.addView(headerSub)
        topBar.addView(titles)
        saveButton = Ui.text(this, str(R.string.save_confirm), 13.5f, getColor(R.color.on_accent), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
            background = Ui.rounded(this@SignActivity, getColor(R.color.accent), 999f)
            setOnClickListener { savePdf() }
        }
        topBar.addView(saveButton)
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --------------------------------------------------------------- 쪽
        overlay = SignatureOverlayView(this).apply {
            setBackgroundColor(getColor(R.color.bg_deep))
            onChange = { updateHint() }
        }
        root.addView(overlay, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        pageNav = Ui.row(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6f), 0, dp(2f))
        }
        pageNav.addView(navButton(R.drawable.ic_chevron_left) { turnPage(-1) })
        pageLabel = Ui.text(this, "", 12f, getColor(R.color.text_secondary), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12f), 0, dp(12f), 0)
        }
        pageNav.addView(pageLabel)
        pageNav.addView(navButton(R.drawable.ic_chevron_right) { turnPage(1) })
        root.addView(pageNav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ------------------------------------------------------------- 아래 도구
        hintView = Ui.text(this, str(R.string.sign_hint_drag), 11.5f, getColor(R.color.text_tertiary)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16f), 0, dp(16f), 0)
        }
        root.addView(hintView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val tools = Ui.row(this).apply { setPadding(dp(16f), dp(10f), dp(16f), dp(16f)) }
        tools.addView(
            Ui.button(this, str(R.string.sign_draw_action), primary = true, iconRes = R.drawable.ic_edit) { pickSignature() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; rightMargin = dp(8f) }
        )
        tools.addView(
            Ui.button(this, str(R.string.sign_remove), primary = false, iconRes = R.drawable.ic_delete) { clearSignature() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; leftMargin = dp(8f) }
        )
        root.addView(tools, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(4f))
            pageNav.updatePadding(bottom = 0)
            tools.updatePadding(bottom = bars.bottom + dp(12f))
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmLeave()
        })

        updatePageNav()
        loadPage()
    }

    // --------------------------------------------------------------- page

    private fun navButton(iconRes: Int, action: () -> Unit): View =
        Ui.icon(this, iconRes, 18f, getColor(R.color.text_primary)).apply {
            setPadding(dp(9f), dp(9f), dp(9f), dp(9f))
            background = Ui.rounded(this@SignActivity, getColor(R.color.surface_high), 999f)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f))
        }

    private fun updatePageNav() {
        pageNav.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        pageLabel.text = if (pageCount > 1) str(R.string.sign_page_of, pageIndex + 1, pageCount) else item.name
        headerSub.text = item.name
    }

    private fun turnPage(step: Int) {
        if (pageCount <= 1) return
        val target = (pageIndex + step).coerceIn(0, pageCount - 1)
        if (target == pageIndex) return
        pageIndex = target
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmLeave()
        })

        updatePageNav()
        loadPage()
    }

    private fun loadPage() {
        val file = item.file
        val index = pageIndex
        executor.execute {
            val bitmap = runCatching {
                PdfEngine.openRenderer(file).use { renderer ->
                    if (renderer.pageCount == 0) return@use null
                    renderer.openPage(index.coerceIn(0, renderer.pageCount - 1)).use { page ->
                        val targetWidth = 1400
                        val scale = targetWidth.toFloat() / page.width
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(result)
                        canvas.drawColor(Color.WHITE)
                        page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        result
                    }
                }
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (bitmap == null) {
                    Dialogs.toast(this, str(R.string.open_error))
                    return@runOnUiThread
                }
                overlay.pageBitmap = bitmap
                signature?.let { overlay.signature = it }
                updateHint()
            }
        }
    }

    // ----------------------------------------------------------- signature

    private fun pickSignature() {
        SignatureDialog.show(this) { bitmap ->
            signature = bitmap
            overlay.signature = bitmap
            updateHint()
        }
    }

    private fun clearSignature() {
        signature = null
        overlay.signature = null
        updateHint()
    }

    private fun updateHint() {
        hintView.text = if (overlay.signature == null) str(R.string.sign_hint_empty) else str(R.string.sign_hint_drag)
    }

    // ---------------------------------------------------------------- save

    private fun savePdf() {
        val sign = signature
        if (sign == null) {
            Dialogs.toast(this, str(R.string.sign_need_signature))
            return
        }
        val base = FileNames.sanitize(item.name + " (" + str(R.string.sign_suffix) + ")") ?: "signed"
        val target = library.newFile(base)
        val rect = RectF(overlay.placeFrac)
        val quality = AppPrefs.get(this).quality
        val index = pageIndex
        val progress = Dialogs.Progress(this, str(R.string.save_saving))
        progress.show()
        saveButton.isEnabled = false
        saveButton.alpha = 0.6f

        executor.execute {
            try {
                val pages = PdfEngine.sign(
                    source = item.file,
                    target = target,
                    pageIndex = index,
                    signature = sign,
                    rect = rect,
                    title = target.name.removeSuffix(".pdf"),
                    quality = quality
                ) { done, total -> progress.setMessage(str(R.string.save_progress, done, total)) }
                progress.dismiss()
                val newItem = library.item(target)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    AdManager.showInterstitialIfReady(this) {
                        showResult(newItem, pages)
                    }
                }
            } catch (t: Throwable) {
                progress.dismiss()
                runCatching { target.delete() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    saveButton.isEnabled = true
                    saveButton.alpha = 1f
                    Dialogs.toast(this, str(R.string.save_error, t.message ?: ""))
                }
            }
        }
    }

    private fun showResult(newItem: PdfItem, pages: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sign_done_title)
            .setMessage(str(R.string.save_done_body, newItem.name, pages, ByteSize.format(newItem.sizeBytes)))
            .setPositiveButton(R.string.open) { _, _ ->
                startActivity(PdfViewerActivity.intent(this, newItem.file))
                finish()
            }
            .setNeutralButton(R.string.share) { _, _ ->
                runCatching {
                    startActivity(Intent.createChooser(library.shareIntent(newItem), str(R.string.share)))
                }.onFailure { Dialogs.toast(this, str(R.string.share_no_app)) }
            }
            .setNegativeButton(R.string.close) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun confirmLeave() {
        if (signature != null) {
            Dialogs.confirm(this, str(R.string.sign_leave_title), str(R.string.sign_leave_body), str(R.string.sign_leave_confirm)) { finish() }
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        signature?.recycle()
        signature = null
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_PAGE = "page"

        fun intent(context: Context, file: File, page: Int = 0): Intent =
            Intent(context, SignActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_PAGE, page)
    }
}
