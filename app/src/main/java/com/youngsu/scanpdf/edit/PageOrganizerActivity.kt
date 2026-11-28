package com.youngsu.scanpdf.edit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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
 * PDF 페이지 편집 — 썸네일을 길게 눌러 순서를 바꾸고, 필요 없는 쪽을 지우고,
 * 고른 쪽만 따로 떼어 낸다.
 *
 * 원본 파일은 건드리지 않고 항상 새 PDF 로 저장한다.
 */
class PageOrganizerActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: PageAdapter
    private lateinit var plan: PagePlan
    private lateinit var library: PdfLibrary
    private lateinit var item: PdfItem
    private lateinit var countLabel: TextView
    private lateinit var splitButton: View
    private lateinit var saveButton: View
    private val selected = LinkedHashSet<Int>()
    private val executor = Executors.newSingleThreadExecutor()
    private var busy = false

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
        plan = PagePlan(item.pages)
        if (plan.size == 0) {
            Dialogs.toast(this, str(R.string.open_error))
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
        }
        setContentView(root)

        // ------------------------------------------------------------- 상단 바
        val topBar = Ui.row(this).apply { setPadding(dp(8f), dp(6f), dp(14f), dp(6f)) }
        val back = Ui.icon(this, R.drawable.ic_back, 20f, getColor(R.color.text_primary)).apply {
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
            setOnClickListener { finish() }
        }
        topBar.addView(back, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        val titles = Ui.column(this).apply {
            setPadding(dp(6f), 0, dp(6f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        }
        titles.addView(Ui.text(this, str(R.string.organize_title), 16f, getColor(R.color.text_primary), bold = true, maxLines = 1))
        countLabel = Ui.text(this, "", 11.5f, getColor(R.color.text_secondary), maxLines = 1)
        titles.addView(countLabel)
        topBar.addView(titles)
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(
            Ui.text(this, str(R.string.organize_hint), 11.5f, getColor(R.color.text_tertiary)).apply {
                setPadding(dp(18f), 0, dp(18f), dp(8f))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // ------------------------------------------------------------- 썸네일
        recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@PageOrganizerActivity, 3)
            setPadding(dp(12f), dp(2f), dp(12f), dp(18f))
            clipToPadding = false
            setBackgroundColor(getColor(R.color.bg))
        }
        adapter = PageAdapter()
        recycler.adapter = adapter
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                if (plan.move(from, to)) {
                    adapter.notifyItemMoved(from, to)
                    updateLabels()
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(recycler)

        // ------------------------------------------------------------- 아래 버튼
        val buttons = Ui.row(this).apply { setPadding(dp(14f), dp(8f), dp(14f), dp(14f)) }
        splitButton = Ui.button(this, str(R.string.organize_split), primary = false, iconRes = R.drawable.ic_merge) { splitSelected() }
        saveButton = Ui.button(this, str(R.string.organize_save), primary = true, iconRes = R.drawable.ic_check) { saveAll() }
        buttons.addView(splitButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; rightMargin = dp(8f) })
        buttons.addView(saveButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f; leftMargin = dp(8f) })
        root.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(4f))
            buttons.updatePadding(bottom = bars.bottom + dp(10f))
            insets
        }

        updateLabels()
    }

    private fun updateLabels() {
        countLabel.text = str(R.string.organize_count, plan.size, plan.originalCount)
        val split = splitButton as com.google.android.material.button.MaterialButton
        split.text = if (selected.isEmpty()) str(R.string.organize_split)
        else str(R.string.organize_split_n, selected.size)
        split.isEnabled = selected.isNotEmpty()
        split.alpha = if (selected.isEmpty()) 0.5f else 1f
    }

    // ------------------------------------------------------------- actions

    private fun deleteAt(position: Int) {
        if (plan.size <= 1) {
            Dialogs.toast(this, str(R.string.organize_need_one))
            return
        }
        val removed = plan.removeAt(position) ?: return
        selected.remove(removed)
        adapter.notifyItemRemoved(position)
        // 삭제하면 뒤 항목들의 자리가 하나씩 당겨진다 — 전체를 다시 그려 번호를 맞춘다
        adapter.notifyItemRangeChanged(position, plan.size - position)
        updateLabels()
    }

    private fun toggleSelection(pageId: Int) {
        if (!selected.add(pageId)) selected.remove(pageId)
        updateLabels()
        adapter.notifyDataSetChanged()
    }

    private fun saveAll() {
        val order = plan.order()
        val base = FileNames.sanitize(item.name + " (" + str(R.string.organize_suffix) + ")") ?: "organized"
        run(PdfLibrary(this).newFile(base), order, R.string.organize_done_title)
    }

    private fun splitSelected() {
        if (selected.isEmpty()) return
        val order = plan.order().filter { it in selected }
        if (order.isEmpty()) return
        selected.clear()
        val base = FileNames.sanitize(item.name + " (" + str(R.string.organize_split_suffix) + ")") ?: "split"
        run(PdfLibrary(this).newFile(base), order, R.string.organize_split_done_title)
    }

    private fun run(target: File, order: List<Int>, doneTitle: Int) {
        if (busy) return
        busy = true
        val quality = AppPrefs.get(this).quality
        val progress = Dialogs.Progress(this, str(R.string.processing))
        progress.show()
        executor.execute {
            try {
                val pages = PdfEngine.rewrite(item.file, order, target, target.name.removeSuffix(".pdf"), quality) { done, total ->
                    progress.setMessage(str(R.string.organize_progress, done, total))
                }
                progress.dismiss()
                val newItem = library.item(target)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    AdManager.showInterstitialIfReady(this) {
                        busy = false
                        MaterialAlertDialogBuilder(this)
                            .setTitle(doneTitle)
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
                }
            } catch (t: Throwable) {
                progress.dismiss()
                runCatching { target.delete() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    busy = false
                    Dialogs.toast(this, str(R.string.save_error, t.message ?: ""))
                }
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

        private val cache = object : LruCache<Int, Bitmap>(32 * 1024 * 1024) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
        }

        override fun getItemCount(): Int = plan.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val card = FrameLayout(parent.context).apply {
                background = Ui.rounded(this@PageOrganizerActivity, getColor(R.color.surface_alt), 14f, getColor(R.color.outline), ripple = false)
                setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            }
            val image = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
            }
            card.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val badge = Ui.text(parent.context, "", 10.5f, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(7f), dp(2f), dp(7f), dp(2f))
                background = GradientDrawable().apply {
                    cornerRadius = dpf(999f)
                    setColor(0xB3000000.toInt())
                }
            }
            card.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(8f)
                bottomMargin = dp(8f)
            })

            val remove = Ui.icon(parent.context, R.drawable.ic_close, 12f, Color.WHITE).apply {
                setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xCC1A2230.toInt())
                }
            }
            card.addView(remove, FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(6f)
                rightMargin = dp(6f)
            })

            val check = Ui.text(parent.context, "✓", 12f, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER
                visibility = View.GONE
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(getColor(R.color.accent))
                }
            }
            card.addView(check, FrameLayout.LayoutParams(dp(24f), dp(24f), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(6f)
                leftMargin = dp(6f)
            })

            val holder = PageHolder(card, image, badge, check, remove)
            card.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210f)).apply {
                setMargins(dp(5f), dp(5f), dp(5f), dp(5f))
            }
            return holder
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val pageId = plan.pageAt(position)
            holder.badge.text = str(R.string.editor_page_index, pageId + 1)
            holder.boundPosition = position
            holder.check.visibility = if (pageId in selected) View.VISIBLE else View.GONE
            holder.card.background = Ui.rounded(
                this@PageOrganizerActivity,
                getColor(if (pageId in selected) R.color.accent_dim else R.color.surface_alt),
                14f,
                getColor(if (pageId in selected) R.color.accent else R.color.outline),
                ripple = false
            )
            holder.remove.setOnClickListener { deleteAt(holder.bindingAdapterPosition) }
            holder.card.setOnClickListener { toggleSelection(pageId) }

            val cached = cache.get(pageId)
            if (cached != null && !cached.isRecycled) {
                holder.image.setImageBitmap(cached)
                return
            }
            holder.image.setImageDrawable(null)
            loadThumb(pageId, holder)
        }

        private fun loadThumb(pageId: Int, holder: PageHolder) {
            executor.execute {
                val bitmap = runCatching {
                    PdfEngine.openRenderer(item.file).use { renderer ->
                        if (pageId >= renderer.pageCount) return@use null
                        renderer.openPage(pageId).use { page ->
                            val targetWidth = 360
                            val scale = targetWidth.toFloat() / page.width
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(result)
                            canvas.drawColor(Color.WHITE)
                            page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result
                        }
                    }
                }.getOrNull() ?: return@execute
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    cache.put(pageId, bitmap)
                    if (holder.boundPosition != RecyclerView.NO_POSITION &&
                        holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                        plan.pageAt(holder.bindingAdapterPosition.coerceIn(0, plan.size - 1)) == pageId
                    ) {
                        holder.image.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private class PageHolder(
        val card: FrameLayout,
        val image: ImageView,
        val badge: TextView,
        val check: TextView,
        val remove: View
    ) : RecyclerView.ViewHolder(card) {
        var boundPosition: Int = RecyclerView.NO_POSITION
    }

    private fun dpf(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val EXTRA_PATH = "path"

        fun intent(context: Context, file: File): Intent =
            Intent(context, PageOrganizerActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath)
    }
}
