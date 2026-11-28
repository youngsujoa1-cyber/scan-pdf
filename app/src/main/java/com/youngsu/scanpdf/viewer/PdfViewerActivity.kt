package com.youngsu.scanpdf.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.data.PdfItem
import com.youngsu.scanpdf.data.PdfLibrary
import com.youngsu.scanpdf.edit.PageOrganizerActivity
import com.youngsu.scanpdf.ocr.OcrActivity
import com.youngsu.scanpdf.pdf.PdfEngine
import com.youngsu.scanpdf.sign.SignActivity
import com.youngsu.scanpdf.ui.Dialogs
import com.youngsu.scanpdf.ui.Ui
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str
import java.io.File
import java.util.concurrent.Executors

/** 저장한 PDF 를 넘겨 보는 화면 */
class PdfViewerActivity : AppCompatActivity() {

    private lateinit var topBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var pagePill: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: PageAdapter

    private lateinit var library: PdfLibrary
    private lateinit var item: PdfItem
    private val executor = Executors.newSingleThreadExecutor()
    private var pageSizes: List<Pair<Int, Int>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val file = File(intent.getStringExtra(EXTRA_PATH) ?: return finish())
        if (!file.exists()) {
            Dialogs.toast(this, str(R.string.open_error))
            finish()
            return
        }
        library = PdfLibrary(this)
        item = library.item(file)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg_deep))
        }
        setContentView(root)

        topBar = Ui.row(this).apply {
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setBackgroundColor(getColor(R.color.bg))
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
        titleView = Ui.text(this, item.name, 15f, getColor(R.color.text_primary), bold = true, maxLines = 1)
        titles.addView(titleView)
        titles.addView(Ui.text(this, itemMeta(), 11.5f, getColor(R.color.text_secondary), maxLines = 1))
        topBar.addView(titles)

        val share = Ui.icon(this, R.drawable.ic_share, 19f, getColor(R.color.text_primary)).apply {
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
            setOnClickListener { shareItem() }
        }
        topBar.addView(share, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        val more = Ui.icon(this, R.drawable.ic_more, 19f, getColor(R.color.text_primary)).apply {
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
            setOnClickListener { showMenu() }
        }
        topBar.addView(more, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val stage = FrameLayout(this)
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PdfViewerActivity)
            setBackgroundColor(getColor(R.color.bg_deep))
            clipToPadding = false
            setPadding(dp(14f), dp(8f), dp(14f), dp(56f))
        }
        stage.addView(recycler, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        adapter = PageAdapter()
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updatePagePill()
            }
        })

        pagePill = Ui.text(this, "", 11.5f, getColor(R.color.text_primary), bold = true).apply {
            gravity = Gravity.CENTER
            background = com.youngsu.scanpdf.ui.Ui.rounded(this@PdfViewerActivity, 0xE60B1220.toInt(), 999f, 0x44FFFFFF)
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        }
        stage.addView(pagePill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(18f)
        })
        root.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(4f))
            recycler.setPadding(dp(14f), dp(8f), dp(14f), dp(56f) + bars.bottom)
            insets
        }

        loadPages()
    }

    private fun itemMeta(): String {
        val pages = item.pages
        return if (pages > 0) str(R.string.item_meta, pages, com.youngsu.scanpdf.util.ByteSize.format(item.sizeBytes), com.youngsu.scanpdf.ui.PdfRow.relativeTime(this, item.modified))
        else com.youngsu.scanpdf.util.ByteSize.format(item.sizeBytes)
    }

    private fun loadPages() {
        executor.execute {
            val sizes = runCatching {
                PdfEngine.openRenderer(item.file).use { renderer ->
                    (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page -> page.width to page.height }
                    }
                }
            }.getOrElse { emptyList() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                pageSizes = sizes
                if (sizes.isEmpty()) {
                    Dialogs.toast(this, str(R.string.open_error))
                    finish()
                    return@runOnUiThread
                }
                adapter.notifyDataSetChanged()
                updatePagePill()
            }
        }
    }

    private fun updatePagePill() {
        if (pageSizes.isEmpty()) return
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstCompletelyVisibleItemPosition().let { if (it < 0) manager.findFirstVisibleItemPosition() else it }
        if (first < 0) return
        pagePill.text = str(R.string.viewer_page_of, first + 1, pageSizes.size)
        pagePill.visibility = View.VISIBLE
    }

    /** 지금 보고 있는 쪽 번호 (0부터) */
    private fun currentPageIndex(): Int {
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return 0
        val first = manager.findFirstCompletelyVisibleItemPosition().let { if (it < 0) manager.findFirstVisibleItemPosition() else it }
        return first.coerceAtLeast(0)
    }

    private fun shareItem() {
        runCatching { startActivity(Intent.createChooser(library.shareIntent(item), str(R.string.share))) }
            .onFailure { Dialogs.toast(this, str(R.string.share_no_app)) }
    }

    private fun showMenu() {
        val entries = ArrayList<Pair<String, () -> Unit>>()
        val currentPage = currentPageIndex()
        entries.add(str(R.string.ocr_title) to { startActivity(OcrActivity.intentForPdf(this, item.file, currentPage)) })
        entries.add(str(R.string.sign_title) to { startActivity(SignActivity.intent(this, item.file, currentPage)) })
        entries.add(str(R.string.organize_title) to { startActivity(PageOrganizerActivity.intent(this, item.file)) })
        entries.add(str(R.string.share) to { shareItem() })
        entries.add(str(R.string.rename) to { renameItem() })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            entries.add(str(R.string.export_downloads) to { exportItem() })
        }
        entries.add(str(R.string.open_with) to { openWith() })
        entries.add(str(R.string.delete) to { deleteItem() })
        MaterialAlertDialogBuilder(this)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renameItem() {
        Dialogs.askName(this, str(R.string.rename_title), item.name, str(R.string.save_name_label), str(R.string.save_confirm)) { raw ->
            val renamed = library.rename(item, raw)
            if (renamed == null) {
                Dialogs.toast(this, str(R.string.rename_invalid))
            } else {
                item = library.item(renamed)
                titleView.text = item.name
                Dialogs.toast(this, str(R.string.rename_done))
            }
        }
    }

    private fun exportItem() {
        val path = library.exportToDownloads(item)
        Dialogs.toast(this, if (path != null) str(R.string.export_done) else str(R.string.export_failed))
    }

    private fun openWith() {
        runCatching { startActivity(library.viewIntent(item)) }
            .onFailure { Dialogs.toast(this, str(R.string.open_error)) }
    }

    private fun deleteItem() {
        Dialogs.confirm(
            this,
            str(R.string.delete_title),
            str(R.string.delete_body, item.name),
            str(R.string.delete)
        ) {
            if (library.delete(item)) {
                Dialogs.toast(this, str(R.string.deleted))
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        recycler.adapter = null
    }

    // -------------------------------------------------------------- adapter

    private inner class PageAdapter : RecyclerView.Adapter<PageHolder>() {

        private val cache = object : LruCache<Int, Bitmap>(48 * 1024 * 1024) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
        }

        override fun getItemCount(): Int = pageSizes.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val frame = FrameLayout(parent.context).apply {
                background = Ui.rounded(this@PdfViewerActivity, getColor(R.color.paper), 10f, getColor(R.color.outline), 1f, ripple = false)
            }
            val image = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setBackgroundColor(Color.WHITE)
            }
            frame.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val holder = PageHolder(frame, image)
            frame.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14f)
            }
            return holder
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val size = pageSizes[position]
            val width = (recycler.width - dp(28f)).coerceAtLeast(dp(200f))
            val height = (width.toFloat() * size.second / size.first).toInt().coerceAtLeast(1)
            holder.frame.layoutParams = (holder.frame.layoutParams as RecyclerView.LayoutParams).apply {
                this.width = ViewGroup.LayoutParams.MATCH_PARENT
                this.height = height
            }
            holder.boundPage = position
            val cached = cache.get(position)
            if (cached != null && !cached.isRecycled) {
                holder.image.setImageBitmap(cached)
                return
            }
            holder.image.setImageDrawable(null)
            render(position, width, height) { bitmap ->
                if (holder.boundPage != position) return@render
                holder.image.setImageBitmap(bitmap)
            }
        }

        private fun render(index: Int, width: Int, height: Int, onReady: (Bitmap) -> Unit) {
            val targetWidth = width.coerceAtMost(1600)
            executor.execute {
                val bitmap = runCatching {
                    PdfEngine.openRenderer(item.file).use { renderer ->
                        renderer.openPage(index).use { page ->
                            val scale = targetWidth.toFloat() / page.width
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(result)
                            canvas.drawColor(Color.WHITE)
                            page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result
                        }
                    }
                }.getOrNull()
                runOnUiThread {
                    if (isFinishing || isDestroyed || bitmap == null) return@runOnUiThread
                    cache.put(index, bitmap)
                    onReady(bitmap)
                }
            }
        }
    }

    private class PageHolder(val frame: FrameLayout, val image: ImageView) : RecyclerView.ViewHolder(frame) {
        var boundPage: Int = -1
    }

    companion object {
        private const val EXTRA_PATH = "path"

        fun intent(context: Context, file: File): Intent =
            Intent(context, PdfViewerActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath)
    }
}
