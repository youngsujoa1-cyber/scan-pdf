package com.youngsu.scanpdf.edit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.ads.AdManager
import com.youngsu.scanpdf.data.AppPrefs
import com.youngsu.scanpdf.data.PageRenderer
import com.youngsu.scanpdf.data.PdfLibrary
import com.youngsu.scanpdf.data.ScanPage
import com.youngsu.scanpdf.data.ScanSession
import com.youngsu.scanpdf.pdf.Filters
import com.youngsu.scanpdf.pdf.PdfEngine
import com.youngsu.scanpdf.pdf.ScanGeometry
import com.youngsu.scanpdf.scan.CameraScanActivity
import com.youngsu.scanpdf.ui.Dialogs
import com.youngsu.scanpdf.ui.Ui
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.dpf
import com.youngsu.scanpdf.ui.str
import com.youngsu.scanpdf.util.ByteSize
import com.youngsu.scanpdf.util.FileNames
import com.youngsu.scanpdf.viewer.PdfViewerActivity
import java.io.File
import java.util.concurrent.Executors

/**
 * 찍은 페이지를 다듬는 화면 — 회전·자르기·필터·순서·삭제 후 PDF 로 저장한다.
 */
class PageEditorActivity : AppCompatActivity() {

    private lateinit var topBar: LinearLayout
    private lateinit var counterView: TextView
    private lateinit var previewImage: ImageView
    private lateinit var previewStatus: TextView
    private lateinit var chipsRow: LinearLayout
    private lateinit var thumbRow: LinearLayout
    private lateinit var toolRow: LinearLayout
    private lateinit var bottomBar: LinearLayout

    private val thumbViews = HashMap<Int, ImageView>()
    private val thumbContainers = ArrayList<FrameLayout>()
    private var index = 0
    private var renderJob = 0
    private var thumbJob = 0
    private var saving = false

    private val executor = Executors.newSingleThreadExecutor()
    private val thumbExecutor = Executors.newSingleThreadExecutor()

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        addFromGallery(uris)
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val quad = result.data?.getFloatArrayExtra(CropActivity.EXTRA_QUAD) ?: return@registerForActivityResult
        val page = ScanSession.pages.getOrNull(index) ?: return@registerForActivityResult
        val originalSpace = ScanGeometry.unrotate(quad, page.rotation)
        page.quad = if (ScanGeometry.isFull(originalSpace)) null else originalSpace
        refreshPreview()
        refreshThumbAt(index)
    }

    private val addPagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) refreshAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        AdManager.preloadInterstitial(this)

        if (ScanSession.isEmpty()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
        }
        setContentView(root)

        topBar = Ui.row(this).apply {
            setPadding(dp(10f), dp(6f), dp(12f), dp(6f))
        }
        val back = Ui.icon(this, R.drawable.ic_back, 20f, getColor(R.color.text_primary)).apply {
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
            setOnClickListener { confirmLeave() }
        }
        topBar.addView(back, LinearLayout.LayoutParams(dp(44f), dp(44f)))

        val titles = Ui.column(this).apply {
            setPadding(dp(8f), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        }
        titles.addView(Ui.text(this, str(R.string.editor_title), 16f, getColor(R.color.text_primary), bold = true))
        counterView = Ui.text(this, "", 11.5f, getColor(R.color.text_secondary)).apply {
            setPadding(0, dp(2f), 0, 0)
        }
        titles.addView(counterView)
        topBar.addView(titles)

        val makePdf = Ui.text(this, str(R.string.editor_make_pdf), 13f, getColor(R.color.on_accent), bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(this@PageEditorActivity, getColor(R.color.accent), 999f)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            setOnClickListener { askNameAndSave() }
        }
        topBar.addView(makePdf)
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 미리보기
        val stage = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.bg_deep))
        }
        previewImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        }
        stage.addView(previewImage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        previewStatus = Ui.text(this, "", 12f, getColor(R.color.text_tertiary)).apply {
            gravity = Gravity.CENTER
        }
        stage.addView(previewStatus, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        // 필터
        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(14f), dp(6f), dp(14f), dp(2f))
            clipToPadding = false
        }
        chipsRow = Ui.row(this)
        filterScroll.addView(chipsRow)
        root.addView(filterScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 도구
        toolRow = Ui.row(this).apply {
            setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
            gravity = Gravity.CENTER
        }
        root.addView(toolRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62f)))

        // 페이지 썸네일
        bottomBar = Ui.column(this).apply {
            setPadding(0, dp(2f), 0, dp(6f))
        }
        val thumbScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(14f), 0, dp(14f), 0)
            clipToPadding = false
        }
        thumbRow = Ui.row(this)
        thumbScroll.addView(thumbRow)
        bottomBar.addView(thumbScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74f)))
        root.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(4f))
            bottomBar.updatePadding(bottom = bars.bottom + dp(4f))
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmLeave()
        })

        buildChips()
        buildTools()
        refreshAll()
    }

    // ------------------------------------------------------------- preview

    private fun currentPage(): ScanPage? = ScanSession.pages.getOrNull(index)

    private fun refreshAll() {
        if (ScanSession.isEmpty()) {
            ScanSession.clear()
            finish()
            return
        }
        if (index >= ScanSession.pages.size) index = ScanSession.pages.size - 1
        counterView.text = str(R.string.editor_counter, index + 1, ScanSession.pages.size)
        refreshChips()
        refreshPreview()
        refreshThumbs()
        updateToolStates()
    }

    private fun refreshPreview() {
        val page = currentPage() ?: return
        val job = ++renderJob
        previewStatus.text = str(R.string.processing)
        previewStatus.visibility = View.VISIBLE
        executor.execute {
            val bitmap: Bitmap? = runCatching {
                PageRenderer.render(page.file, page.rotation, page.quad, page.filter, PREVIEW_DIM)
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed || job != renderJob) return@runOnUiThread
                if (bitmap == null) {
                    previewStatus.text = str(R.string.editor_preview_failed)
                    previewStatus.visibility = View.VISIBLE
                    previewImage.setImageDrawable(null)
                } else {
                    previewImage.setImageBitmap(bitmap)
                    previewStatus.visibility = View.GONE
                }
            }
        }
    }

    private fun refreshThumbs() {
        val job = ++thumbJob
        val pages = ScanSession.pages.toList()
        thumbRow.removeAllViews()
        thumbViews.clear()
        thumbContainers.clear()

        pages.forEachIndexed { position, page ->
            val container = FrameLayout(this).apply {
                setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
                background = thumbFrame(position == index)
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
            }
            container.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val badge = Ui.text(this, (position + 1).toString(), 9.5f, getColor(R.color.text_primary), bold = true).apply {
                gravity = Gravity.CENTER
                setBackgroundColor(0xCC0B1220.toInt())
                setPadding(dp(4f), 0, dp(4f), 0)
            }
            container.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(14f), Gravity.BOTTOM or Gravity.END))
            thumbViews[position] = image
            thumbContainers.add(container)
            thumbRow.addView(container, LinearLayout.LayoutParams(dp(54f), dp(68f)).apply {
                rightMargin = dp(8f)
            })
            Ui.clickable(container) { selectPage(position) }
            renderThumb(job, page, image)
        }

        // 페이지 추가 버튼
        val add = FrameLayout(this).apply {
            background = Ui.rounded(this@PageEditorActivity, getColor(R.color.surface_alt), 10f, getColor(R.color.outline))
            addView(Ui.icon(this@PageEditorActivity, R.drawable.ic_plus, 18f, getColor(R.color.text_secondary)).apply {
                layoutParams = FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER)
            })
            Ui.clickable(this) { showAddSheet() }
        }
        thumbRow.addView(add, LinearLayout.LayoutParams(dp(54f), dp(68f)))
    }

    /** 페이지를 고를 때는 썸네일을 다시 그리지 않고 테두리만 바꾼다 (빠르게) */
    private fun selectPage(position: Int) {
        if (position == index) return
        if (position !in ScanSession.pages.indices) return
        index = position
        counterView.text = str(R.string.editor_counter, index + 1, ScanSession.pages.size)
        refreshChips()
        refreshPreview()
        paintThumbFrames()
        updateToolStates()
    }

    private fun paintThumbFrames() {
        thumbContainers.forEachIndexed { position, container ->
            container.background = thumbFrame(position == index)
        }
    }

    private fun thumbFrame(selected: Boolean): android.graphics.drawable.Drawable =
        if (selected) Ui.rounded(this, getColor(R.color.accent_dim), 10f, getColor(R.color.accent), 2f, ripple = false)
        else Ui.rounded(this, getColor(R.color.surface_alt), 10f, getColor(R.color.outline), 1f, ripple = false)

    /** 회전·자르기·필터를 바꾼 뒤 그 페이지 썸네일만 다시 그린다 */
    private fun refreshThumbAt(position: Int) {
        val page = ScanSession.pages.getOrNull(position) ?: return
        val image = thumbViews[position] ?: return
        renderThumb(thumbJob, page, image)
    }

    private fun renderThumb(job: Int, page: ScanPage, target: ImageView) {
        thumbExecutor.execute {
            val bitmap = runCatching {
                PageRenderer.render(page.file, page.rotation, page.quad, page.filter, 220)
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed || job != thumbJob) return@runOnUiThread
                if (bitmap != null) target.setImageBitmap(bitmap)
            }
        }
    }

    // --------------------------------------------------------------- chips

    private fun buildChips() {
        chipsRow.removeAllViews()
        val kinds = listOf(
            Filters.Kind.SHARP to R.string.filter_sharp,
            Filters.Kind.DOCUMENT to R.string.filter_document,
            Filters.Kind.BW to R.string.filter_bw,
            Filters.Kind.GRAY to R.string.filter_gray,
            Filters.Kind.ORIGINAL to R.string.filter_original
        )
        kinds.forEach { (kind, label) ->
            val chip = Ui.text(this, str(label), 13f, getColor(R.color.text_secondary), bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
                setOnClickListener {
                    val page = currentPage() ?: return@setOnClickListener
                    if (page.filter == kind) return@setOnClickListener
                    page.filter = kind
                    refreshChips()
                    refreshPreview()
                    refreshThumbAt(index)
                }
                tag = kind
            }
            chipsRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8f)
            })
        }
    }

    private fun refreshChips() {
        val selected = currentPage()?.filter ?: Filters.Kind.DOCUMENT
        for (i in 0 until chipsRow.childCount) {
            val chip = chipsRow.getChildAt(i) as TextView
            val active = chip.tag == selected
            chip.setTextColor(if (active) getColor(R.color.text_primary) else getColor(R.color.text_secondary))
            chip.background = GradientDrawable().apply {
                cornerRadius = dpf(14f)
                setColor(if (active) getColor(R.color.accent_dim) else getColor(R.color.surface_high))
                setStroke(dp(1f), if (active) getColor(R.color.accent) else getColor(R.color.outline))
            }
        }
    }

    // --------------------------------------------------------------- tools

    private fun buildTools() {
        toolRow.removeAllViews()
        fun addTool(icon: Int, label: Int, action: () -> Unit) {
            val view = Ui.toolButton(this, icon, str(label), action)
            toolRow.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { weight = 1f })
        }
        addTool(R.drawable.ic_rotate, R.string.tool_rotate) { rotateCurrent() }
        addTool(R.drawable.ic_crop, R.string.tool_crop) { cropCurrent() }
        addTool(R.drawable.ic_chevron_left, R.string.tool_move_left) { moveCurrent(-1) }
        addTool(R.drawable.ic_chevron_right, R.string.tool_move_right) { moveCurrent(1) }
        addTool(R.drawable.ic_delete, R.string.tool_delete) { deleteCurrent() }
    }

    private fun updateToolStates() {
        // 첫 페이지는 앞으로, 마지막 페이지는 뒤로 이동할 수 없다
        val left = toolRow.getChildAt(2)
        val right = toolRow.getChildAt(3)
        left.alpha = if (index > 0) 1f else 0.35f
        right.alpha = if (index < ScanSession.pages.size - 1) 1f else 0.35f
        left.isEnabled = index > 0
        right.isEnabled = index < ScanSession.pages.size - 1
    }

    private fun rotateCurrent() {
        val page = currentPage() ?: return
        page.rotation = (page.rotation + 90) % 360
        refreshPreview()
        refreshThumbAt(index)
    }

    private fun cropCurrent() {
        val page = currentPage() ?: return
        val quadInView = ScanGeometry.rotate(page.quad ?: ScanGeometry.fullQuad(), page.rotation)
        cropLauncher.launch(CropActivity.intent(this, page.file, page.rotation, quadInView))
    }

    private fun moveCurrent(direction: Int) {
        val target = index + direction
        if (target < 0 || target >= ScanSession.pages.size) return
        val page = ScanSession.pages.removeAt(index)
        ScanSession.pages.add(target, page)
        index = target
        refreshAll()
    }

    private fun deleteCurrent() {
        val page = currentPage() ?: return
        val confirm = Runnable {
            ScanSession.pages.remove(page)
            runCatching { page.file.delete() }
            if (ScanSession.isEmpty()) {
                ScanSession.clear()
                finish()
            } else {
                if (index >= ScanSession.pages.size) index = ScanSession.pages.size - 1
                refreshAll()
            }
        }
        if (ScanSession.pages.size == 1) {
            Dialogs.confirm(
                this,
                str(R.string.tool_delete),
                str(R.string.editor_delete_page),
                str(R.string.delete),
                onConfirm = confirm::run
            )
        } else {
            confirm.run()
        }
    }

    private fun showAddSheet() {
        MaterialAlertDialogBuilder(this)
            .setTitle(str(R.string.tool_add))
            .setItems(arrayOf(str(R.string.camera_title), str(R.string.action_photos))) { _, which ->
                if (which == 0) {
                    addPagesLauncher.launch(
                        Intent(this, CameraScanActivity::class.java)
                            .putExtra(CameraScanActivity.EXTRA_ADD_MODE, true)
                    )
                } else {
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addFromGallery(uris: List<Uri>) {
        val dir = ScanSession.directory(this)
        var added = 0
        uris.forEach { uri ->
            val target = File(dir, "photo_" + System.currentTimeMillis() + "_" + added + ".jpg")
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (copied) {
                ScanSession.add(ScanPage(target))
                added++
            }
        }
        if (added == 0) {
            Dialogs.toast(this, str(R.string.camera_saved_failed))
            return
        }
        refreshAll()
    }

    // ---------------------------------------------------------------- save

    private fun askNameAndSave() {
        if (ScanSession.isEmpty()) {
            Dialogs.toast(this, str(R.string.editor_need_one_page))
            return
        }
        val library = PdfLibrary(this)
        val defaultName = FileNames.defaultName(str(R.string.file_prefix))
        Dialogs.askName(
            this,
            str(R.string.save_title),
            defaultName,
            str(R.string.save_name_label)
        ) { raw ->
            val safe = FileNames.sanitize(raw)
            if (safe == null) {
                Dialogs.toast(this, str(R.string.rename_invalid))
                return@askName
            }
            savePdf(library, safe)
        }
    }

    private fun savePdf(library: PdfLibrary, baseName: String) {
        if (saving) return
        saving = true
        val quality = AppPrefs.get(this).quality
        val target = library.newFile(baseName)
        val progress = Dialogs.Progress(this, str(R.string.save_saving))
        progress.show()

        executor.execute {
            try {
                val pages = PdfEngine.create(ScanSession.pages.toList(), quality, target, target.name.removeSuffix(".pdf")) { done, total ->
                    progress.setMessage(str(R.string.save_progress, done, total))
                }
                progress.dismiss()
                val item = library.item(target)
                runOnUiThread {
                    AdManager.showInterstitialIfReady(this) {
                        showSavedDialog(item, pages)
                    }
                }
            } catch (t: Throwable) {
                progress.dismiss()
                runCatching { target.delete() }
                runOnUiThread {
                    saving = false
                    Dialogs.toast(this, str(R.string.save_error, t.message ?: ""))
                }
            }
        }
    }

    private fun showSavedDialog(item: com.youngsu.scanpdf.data.PdfItem, pages: Int) {
        saving = false
        if (isFinishing || isDestroyed) return
        val body = str(R.string.save_done_body, item.name, pages, ByteSize.format(item.sizeBytes))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.save_done_title)
            .setMessage(body)
            .setPositiveButton(R.string.open) { _, _ ->
                startActivity(PdfViewerActivity.intent(this, item.file))
                finishFlow()
            }
            .setNeutralButton(R.string.share) { _, _ ->
                runCatching { startActivity(Intent.createChooser(PdfLibrary(this).shareIntent(item), str(R.string.share))) }
                    .onFailure { Dialogs.toast(this, str(R.string.share_no_app)) }
                finishFlow()
            }
            .setNegativeButton(R.string.close) { _, _ -> finishFlow() }
            .setCancelable(false)
            .show()
    }

    private fun finishFlow() {
        ScanSession.clear()
        setResult(RESULT_OK)
        finish()
    }

    private fun confirmLeave() {
        if (ScanSession.isEmpty()) {
            finish()
            return
        }
        Dialogs.confirm(
            this,
            str(R.string.editor_leave_title),
            str(R.string.editor_leave_body),
            str(R.string.editor_leave_confirm),
            onConfirm = {
                ScanSession.clear()
                setResult(RESULT_CANCELED)
                finish()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        thumbExecutor.shutdown()
    }

    private companion object {
        const val PREVIEW_DIM = 1200
    }
}
