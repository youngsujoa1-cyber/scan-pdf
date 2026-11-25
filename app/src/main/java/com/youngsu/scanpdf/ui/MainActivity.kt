package com.youngsu.scanpdf.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.ads.AdManager
import com.youngsu.scanpdf.data.AppPrefs
import com.youngsu.scanpdf.data.PageRenderer
import com.youngsu.scanpdf.data.PdfItem
import com.youngsu.scanpdf.data.PdfLibrary
import com.youngsu.scanpdf.data.ScanPage
import com.youngsu.scanpdf.data.ScanSession
import com.youngsu.scanpdf.edit.PageEditorActivity
import com.youngsu.scanpdf.pdf.PdfEngine
import com.youngsu.scanpdf.scan.CameraScanActivity
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str
import com.youngsu.scanpdf.util.ByteSize
import com.youngsu.scanpdf.util.FileNames
import com.youngsu.scanpdf.viewer.PdfViewerActivity
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var adContainer: FrameLayout
    private lateinit var bottomNav: BottomNavigationView

    private val screens = HashMap<Int, Screen>()
    private var currentTab = R.id.nav_scan
    private var topInset = 0
    private var bottomInset = 0

    private val library: PdfLibrary by lazy { PdfLibrary(this) }
    private val io: java.util.concurrent.ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
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
        } else {
            startActivity(Intent(this, PageEditorActivity::class.java))
        }
    }

    private val pickMergeFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        mergeFromUris(uris)
    }

    private val pickCompressFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val item = copyIntoLibrary(uri) ?: return@registerForActivityResult
        DocumentActions.compress(this, item) { refreshCurrentTab() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        AdManager.initialize(this)
        AdManager.preloadInterstitial(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
        }
        setContentView(root)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        bottomNav = BottomNavigationView(this).apply {
            inflateMenu(R.menu.nav_menu)
            background = GradientDrawable().apply {
                setColor(getColor(R.color.nav_bg))
                setStroke(dp(1f), getColor(R.color.divider))
            }
            elevation = 0f
            itemIconTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(getColor(R.color.accent), getColor(R.color.text_tertiary))
            )
            itemTextColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(getColor(R.color.accent), getColor(R.color.text_tertiary))
            )
            itemActiveIndicatorColor = ColorStateList.valueOf(getColor(R.color.accent_dim))
            itemIconSize = dp(21f)
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            setOnItemSelectedListener { item ->
                selectTab(item.itemId)
                true
            }
        }
        root.addView(bottomNav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64f)))

        // 배너 광고는 하단 탭 바보다 더 아래 — 화면 맨 끝(최하단)에 둔다
        adContainer = FrameLayout(this)
        root.addView(adContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 시스템 3버튼 내비게이션 바는 평소에 숨기고, 화면 아래를 스와이프할 때만 잠깐 나오게 한다
        // (배너가 맨 아래에 있어도 3버튼 바가 올라와 가리지 않도록 — 나타날 때는 여백으로 비켜 준다)
        hideSystemNavBar()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topInset = bars.top
            bottomInset = bars.bottom
            applyInsets()
            insets
        }

        val startTab = savedTab(savedInstanceState)
        bottomNav.selectedItemId = startTab
        selectTab(startTab)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != R.id.nav_scan) {
                    bottomNav.selectedItemId = R.id.nav_scan
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        AdManager.attachBanner(this, adContainer)
        adContainer.viewTreeObserver.addOnGlobalLayoutListener { applyInsets() }

        handleIncoming(intent)

        // 처음 실행 — 무엇보다 먼저 쓸 언어를 고르게 한다 (고르거나 닫으면 다시 묻지 않음)
        if (savedInstanceState == null && !AppPrefs.get(this).languageOnboarded) {
            content.post { if (!isFinishing && !isDestroyed) LanguageOnboarding.show(this) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 언어를 바꾸면 화면이 다시 만들어지는데, 그때 보던 탭을 그대로 연다
        outState.putInt(STATE_TAB, currentTab)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    override fun onResume() {
        super.onResume()
        // 다이얼로그·다른 화면에서 돌아오면 시스템 내비게이션 바가 다시 보이므로 또 숨긴다
        hideSystemNavBar()
        refreshCurrentTab()
    }

    /** 시스템 3버튼 내비게이션 바를 숨긴다 (아래를 스와이프하면 잠깐 나타남) */
    private fun hideSystemNavBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun applyInsets() {
        // 맨 아래에 있는 것(배너 광고, 없으면 탭 바)이 시스템 내비게이션 바 높이만큼 여백을 갖는다
        val adVisible = adContainer.childCount > 0 && adContainer.visibility == View.VISIBLE
        if (adVisible) {
            applyTabBarBottomInset(0)
            adContainer.updatePadding(bottom = bottomInset)
        } else {
            applyTabBarBottomInset(bottomInset)
            adContainer.updatePadding(bottom = 0)
        }
        screens[currentTab]?.setSystemInsets(topInset)
    }

    /** 탭 바는 내비게이션 바 높이만큼 스스로 키우고 그만큼 아래를 비워 아이콘이 가려지지 않게 한다 */
    private fun applyTabBarBottomInset(inset: Int) {
        val params = bottomNav.layoutParams
        val target = dp(64f) + inset
        if (params.height != target) {
            params.height = target
            bottomNav.layoutParams = params
        }
        bottomNav.updatePadding(bottom = inset)
    }

    private fun selectTab(itemId: Int) {
        screens[currentTab]?.onHidden()
        currentTab = itemId
        val screen = screens.getOrPut(itemId) { createScreen(itemId) }
        content.removeAllViews()
        content.addView(
            screen.view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        screen.setSystemInsets(topInset)
        screen.refresh()
    }

    private fun createScreen(itemId: Int): Screen = when (itemId) {
        R.id.nav_docs -> DocsScreen(this)
        R.id.nav_settings -> SettingsScreen(this)
        else -> HomeScreen(this)
    }

    private fun savedTab(savedInstanceState: Bundle?): Int {
        val tab = savedInstanceState?.getInt(STATE_TAB) ?: R.id.nav_scan
        return if (tab == R.id.nav_docs || tab == R.id.nav_settings) tab else R.id.nav_scan
    }

    fun openTab(itemId: Int) {
        runOnUiThread { bottomNav.selectedItemId = itemId }
    }

    fun refreshCurrentTab() {
        screens[currentTab]?.refresh()
    }

    fun showPrivacyNote() {
        Dialogs.message(
            this,
            str(R.string.settings_privacy),
            str(R.string.settings_privacy_body),
            str(R.string.close)
        )
    }

    // ------------------------------------------------------------ actions

    fun startScan() {
        startActivity(Intent(this, CameraScanActivity::class.java))
    }

    fun startPhotoImport() {
        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun startMerge() {
        val items = library.list()
        if (items.isEmpty()) {
            pickMergeFiles.launch(arrayOf("application/pdf"))
            return
        }
        val labels = items.map { it.name + "  ·  " + ByteSize.format(it.sizeBytes) }.toTypedArray()
        val checked = BooleanArray(items.size)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.merge_title)
            .setMessage(R.string.merge_hint)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNeutralButton(R.string.merge_choose, null)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.merge_title) { _, _ ->
                val selected = items.filterIndexed { index, _ -> checked[index] }
                if (selected.size < 2) {
                    Dialogs.toast(this, str(R.string.merge_need_two))
                } else {
                    mergeItems(selected)
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                pickMergeFiles.launch(arrayOf("application/pdf"))
            }
        }
        dialog.show()
    }

    fun startCompress() {
        val items = library.list()
        if (items.isEmpty()) {
            pickCompressFile.launch(arrayOf("application/pdf"))
            return
        }
        val labels = items.map { it.name + "  ·  " + ByteSize.format(it.sizeBytes) }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compress_pick_title)
            .setItems(labels) { _, which ->
                DocumentActions.compress(this, items[which]) { refreshCurrentTab() }
            }
            .setNeutralButton(R.string.merge_choose, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                pickCompressFile.launch(arrayOf("application/pdf"))
            }
        }
        dialog.show()
    }

    // -------------------------------------------------------------- merge

    private fun mergeItems(items: List<PdfItem>) {
        mergeFiles(items.map { it.file }, FileNames.defaultName(str(R.string.merge_default_prefix)))
    }

    private fun mergeFromUris(uris: List<Uri>) {
        val cacheDir = File(cacheDir, "merge_input").apply { mkdirs() }
        val files = ArrayList<File>()
        uris.forEachIndexed { index, uri ->
            val target = File(cacheDir, "src_$index.pdf")
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (copied) files.add(target)
        }
        if (files.size < 2) {
            Dialogs.toast(this, str(R.string.merge_need_two))
            return
        }
        mergeFiles(files, FileNames.defaultName(str(R.string.merge_default_prefix)), cleanUp = files)
    }

    private fun mergeFiles(sources: List<File>, baseName: String, cleanUp: List<File> = emptyList()) {
        val target = library.newFile(baseName)
        val progress = Dialogs.Progress(this, str(R.string.processing))
        progress.show()
        io.execute {
            try {
                val pages = PdfEngine.merge(sources, target, target.name.removeSuffix(".pdf")) { done, total ->
                    progress.setMessage(str(R.string.merge_progress, done, total))
                }
                progress.dismiss()
                cleanUp.forEach { runCatching { it.delete() } }
                val item = library.item(target)
                runOnUiThread {
                    AdManager.showInterstitialIfReady(this) {
                        refreshCurrentTab()
                        showResultDialog(
                            str(R.string.merge_done_title),
                            str(R.string.save_done_body, item.name, pages, ByteSize.format(item.sizeBytes)),
                            item
                        )
                    }
                }
            } catch (t: Throwable) {
                progress.dismiss()
                runCatching { target.delete() }
                cleanUp.forEach { runCatching { it.delete() } }
                runOnUiThread {
                    Dialogs.toast(this, str(R.string.merge_error, t.message ?: ""))
                }
            }
        }
    }

    private fun showResultDialog(title: String, body: String, item: PdfItem) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(R.string.open) { _, _ -> DocumentActions.open(this, item) }
            .setNeutralButton(R.string.share) { _, _ -> DocumentActions.share(this, item) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ----------------------------------------------------------- incoming

    /** 다른 앱에서 PDF 를 열거나 공유했을 때 — 보관함에 복사한 뒤 바로 연다. */
    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return
        val uri: Uri = if (action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) ?: return
        } else {
            intent.data ?: return
        }
        if (!runCatching { contentResolver.getType(uri) }.getOrNull().orEmpty().contains("pdf") &&
            !uri.toString().lowercase().endsWith(".pdf")
        ) {
            return
        }
        io.execute {
            val item = copyIntoLibrary(uri)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (item != null) {
                    openTab(R.id.nav_docs)
                    startActivity(PdfViewerActivity.intent(this, item.file))
                } else {
                    Dialogs.toast(this, str(R.string.open_error))
                }
            }
        }
    }

    private fun copyIntoLibrary(uri: Uri): PdfItem? {
        val name = displayName(uri) ?: "PDF"
        val base = FileNames.sanitize(name.removeSuffix(".pdf")) ?: "PDF"
        val target = library.newFile(base)
        val copied = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        return if (copied && target.length() > 0) library.item(target) else {
            runCatching { target.delete() }
            null
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }

    private companion object {
        const val STATE_TAB = "main_tab"
    }
}
