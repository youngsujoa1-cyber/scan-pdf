package com.youngsu.scanpdf.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.pdf.PdfEngine
import com.youngsu.scanpdf.ui.Dialogs
import com.youngsu.scanpdf.ui.Ui
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str
import java.io.File
import java.util.concurrent.Executors

/**
 * 글자 인식(OCR) 화면 — PDF 한 쪽이나 사진에서 글자를 뽑아 보여 주고 복사·공유한다.
 *
 * 사진은 기기에서만 읽고(ML Kit 온디바이스 모델) 어디로도 보내지 않는다.
 */
class OcrActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var preview: ImageView
    private lateinit var chipsRow: LinearLayout
    private lateinit var resultView: TextView
    private lateinit var resultScroll: ScrollView
    private lateinit var placeholder: LinearLayout
    private lateinit var spinner: ProgressBar
    private lateinit var copyButton: View
    private lateinit var shareButton: View
    private lateinit var pageNav: LinearLayout
    private lateinit var pageLabel: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private var bitmap: Bitmap? = null
    private var pdfFile: File? = null
    private var pageIndex = 0
    private var pageCount = 0
    private var script = OcrEngine.Script.AUTO
    private var recognized = ""
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val path = intent.getStringExtra(EXTRA_PATH)
        val uri: Uri? = intent.getStringExtra(EXTRA_URI)?.let { Uri.parse(it) }
        if (path == null && uri == null) {
            finish()
            return
        }
        pdfFile = path?.let { File(it) }
        pageIndex = intent.getIntExtra(EXTRA_PAGE, 0)
        pageCount = pdfFile?.let { PdfEngine.pageCount(it) } ?: 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
        }
        setContentView(root)

        // ------------------------------------------------------------- 상단 바
        val topBar = Ui.row(this).apply {
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        }
        val back = Ui.icon(this, R.drawable.ic_back, 20f, getColor(R.color.text_primary)).apply {
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
            setOnClickListener { finish() }
        }
        topBar.addView(back, LinearLayout.LayoutParams(dp(44f), dp(44f)))

        val titles = Ui.column(this).apply {
            setPadding(dp(6f), 0, dp(6f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        }
        titleView = Ui.text(this, str(R.string.ocr_title), 16f, getColor(R.color.text_primary), bold = true, maxLines = 1)
        subtitleView = Ui.text(this, "", 11.5f, getColor(R.color.text_secondary), maxLines = 1)
        titles.addView(titleView)
        titles.addView(subtitleView)
        topBar.addView(titles)

        shareButton = Ui.icon(this, R.drawable.ic_share, 19f, getColor(R.color.text_tertiary)).apply {
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
            alpha = 0.5f
            setOnClickListener { shareText() }
        }
        topBar.addView(shareButton, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // -------------------------------------------------------------- 본문
        val content = Ui.column(this).apply {
            setPadding(dp(16f), dp(4f), dp(16f), dp(10f))
        }

        // 미리보기 + 쪽 넘기기
        val previewCard = FrameLayout(this).apply {
            background = Ui.rounded(this@OcrActivity, getColor(R.color.surface_alt), 18f, getColor(R.color.outline), ripple = false)
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        previewCard.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(previewCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130f)))

        pageNav = Ui.row(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
        }
        val prev = navButton(R.drawable.ic_chevron_left) { turnPage(-1) }
        val next = navButton(R.drawable.ic_chevron_right) { turnPage(1) }
        pageLabel = Ui.text(this, "", 12f, getColor(R.color.text_secondary), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12f), 0, dp(12f), 0)
        }
        pageNav.addView(prev)
        pageNav.addView(pageLabel)
        pageNav.addView(next)
        content.addView(pageNav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 읽을 언어
        content.addView(Ui.sectionTitle(this, str(R.string.ocr_language_section)).apply {
            setPadding(dp(4f), dp(14f), 0, dp(8f))
        })
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        chipsRow = Ui.row(this)
        chipScroll.addView(chipsRow)
        content.addView(chipScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        buildChips()

        // 결과
        val resultCard = FrameLayout(this).apply {
            background = Ui.rounded(this@OcrActivity, getColor(R.color.surface), 18f, getColor(R.color.outline), ripple = false)
        }
        resultScroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
        }
        resultView = Ui.text(this, "", 13.5f, getColor(R.color.text_primary)).apply {
            setPadding(dp(16f), dp(14f), dp(16f), dp(16f))
            setTextIsSelectable(true)
            setLineSpacing(dp(4f).toFloat(), 1f)
        }
        resultScroll.addView(resultView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        resultCard.addView(resultScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        placeholder = Ui.column(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(20f), dp(24f), dp(20f))
        }
        placeholder.addView(Ui.text(this, str(R.string.ocr_reading), 13f, getColor(R.color.text_secondary)).apply { gravity = Gravity.CENTER })
        spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
            layoutParams = LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { topMargin = dp(14f) }
        }
        placeholder.addView(spinner)
        resultCard.addView(placeholder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        content.addView(resultCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply {
            weight = 1f
            topMargin = dp(14f)
        })
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        // ------------------------------------------------------------- 아래 버튼
        val buttons = Ui.row(this).apply {
            setPadding(dp(16f), dp(6f), dp(16f), dp(16f))
        }
        copyButton = Ui.button(this, str(R.string.ocr_copy_all), primary = true, iconRes = R.drawable.ic_file) { copyText() }.apply {
            isEnabled = false
            alpha = 0.5f
        }
        shareButton.alpha = 0.5f
        buttons.addView(copyButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; rightMargin = dp(8f) })
        buttons.addView(
            Ui.button(this, str(R.string.share), primary = false, iconRes = R.drawable.ic_share) { shareText() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; leftMargin = dp(8f) }
        )
        root.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(4f))
            buttons.updatePadding(bottom = bars.bottom + dp(12f))
            insets
        }

        updatePageNav()
        loadSource(uri)
    }

    // ------------------------------------------------------------- source

    private fun updatePageNav() {
        pageNav.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        if (pageCount > 0) pageLabel.text = str(R.string.viewer_page_of, pageIndex + 1, pageCount)
    }

    private fun turnPage(step: Int) {
        val file = pdfFile ?: return
        val target = (pageIndex + step).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (target == pageIndex) return
        pageIndex = target
        updatePageNav()
        loadSource(null)
    }

    private fun navButton(iconRes: Int, action: () -> Unit): View =
        Ui.icon(this, iconRes, 18f, getColor(R.color.text_primary)).apply {
            setPadding(dp(9f), dp(9f), dp(9f), dp(9f))
            background = Ui.rounded(this@OcrActivity, getColor(R.color.surface_high), 999f)
            setOnClickListener { if (!running) action() }
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f))
        }

    private fun loadSource(uri: Uri?) {
        if (running) return
        val context = this
        executor.execute {
            val loaded = runCatching {
                if (uri != null) decodeImage(context, uri) else renderPdfPage()
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (loaded == null) {
                    Dialogs.toast(this, str(R.string.ocr_load_failed))
                    finish()
                    return@runOnUiThread
                }
                bitmap?.takeIf { it !== loaded }?.recycle()
                bitmap = loaded
                preview.setImageBitmap(loaded)
                subtitleView.text = sourceLabel(uri)
                runRecognition()
            }
        }
    }

    private fun sourceLabel(uri: Uri?): String {
        if (uri != null) return str(R.string.ocr_source_photo)
        val name = pdfFile?.name?.removeSuffix(".pdf").orEmpty()
        return if (pageCount > 1) str(R.string.ocr_source_pdf_page, name, pageIndex + 1, pageCount)
        else str(R.string.ocr_source_pdf, name)
    }

    private fun renderPdfPage(): Bitmap? {
        val file = pdfFile ?: return null
        return PdfEngine.openRenderer(file).use { renderer ->
            if (renderer.pageCount == 0) return null
            val index = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(index).use { page ->
                val targetWidth = 1500
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
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1800) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    // --------------------------------------------------------------- OCR

    private fun buildChips() {
        chipsRow.removeAllViews()
        val items = listOf(
            OcrEngine.Script.AUTO to R.string.ocr_lang_auto,
            OcrEngine.Script.KOREAN to R.string.ocr_lang_ko,
            OcrEngine.Script.LATIN to R.string.ocr_lang_en,
            OcrEngine.Script.CHINESE to R.string.ocr_lang_zh,
            OcrEngine.Script.JAPANESE to R.string.ocr_lang_ja
        )
        items.forEach { (value, label) ->
            val selected = value == script
            val chip = Ui.text(this, str(label), 12.5f, getColor(if (selected) R.color.on_accent else R.color.text_secondary), bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
                background = Ui.rounded(
                    this@OcrActivity,
                    getColor(if (selected) R.color.accent else R.color.surface_high),
                    999f,
                    getColor(if (selected) R.color.accent_strong else R.color.outline)
                )
                isClickable = true
                setOnClickListener {
                    if (running || script == value) return@setOnClickListener
                    script = value
                    buildChips()
                    runRecognition()
                }
            }
            chipsRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8f)
            })
        }
    }

    private fun runRecognition() {
        val source = bitmap ?: return
        if (running) return
        running = true
        recognized = ""
        updateActions()
        resultView.text = ""
        resultScroll.visibility = View.GONE
        placeholder.visibility = View.VISIBLE
        spinner.visibility = View.VISIBLE
        (placeholder.getChildAt(0) as? TextView)?.text = str(R.string.ocr_reading)

        val work = source.copy(Bitmap.Config.ARGB_8888, false)
        val chosen = script
        executor.execute {
            val result = runCatching { OcrEngine.recognize(this, work, chosen) }.getOrNull()
            work.recycle()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                running = false
                spinner.visibility = View.GONE
                val text = result?.let { OcrEngine.tidy(it.text) }.orEmpty()
                recognized = text
                if (text.isEmpty()) {
                    placeholder.visibility = View.VISIBLE
                    resultScroll.visibility = View.GONE
                    (placeholder.getChildAt(0) as? TextView)?.text = str(R.string.ocr_empty)
                } else {
                    placeholder.visibility = View.GONE
                    resultScroll.visibility = View.VISIBLE
                    resultView.text = text
                    resultScroll.scrollTo(0, 0)
                }
                updateActions()
            }
        }
    }

    private fun updateActions() {
        val has = recognized.isNotBlank()
        copyButton.isEnabled = has
        copyButton.alpha = if (has) 1f else 0.5f
        shareButton.alpha = if (has) 1f else 0.5f
    }

    private fun copyText() {
        if (recognized.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(str(R.string.ocr_title), recognized))
        Dialogs.toast(this, str(R.string.ocr_copied))
    }

    private fun shareText() {
        if (recognized.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, recognized)
        }
        runCatching { startActivity(Intent.createChooser(intent, str(R.string.share))) }
            .onFailure { Dialogs.toast(this, str(R.string.share_no_app)) }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        bitmap?.recycle()
        bitmap = null
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_PAGE = "page"
        private const val EXTRA_URI = "uri"

        fun intentForPdf(context: Context, file: File, page: Int = 0): Intent =
            Intent(context, OcrActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_PAGE, page)

        fun intentForImage(context: Context, uri: Uri): Intent =
            Intent(context, OcrActivity::class.java).putExtra(EXTRA_URI, uri.toString())
    }
}
