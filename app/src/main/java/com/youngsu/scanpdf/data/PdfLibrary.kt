package com.youngsu.scanpdf.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.content.FileProvider
import com.youngsu.scanpdf.pdf.PdfEngine
import com.youngsu.scanpdf.util.FileNames
import java.io.File
import java.io.FileOutputStream

data class PdfItem(
    val file: File,
    val name: String,
    val pages: Int,
    val sizeBytes: Long,
    val modified: Long
)

/**
 * 앱이 만든 PDF 보관함 (앱 전용 저장소).
 *
 * 파일 하나가 곧 문서 하나라서 따로 데이터베이스를 두지 않는다.
 * 페이지 수는 파일 크기·수정시각과 함께 캐시해 목록을 빠르게 그린다.
 */
class PdfLibrary(private val context: Context) {

    val directory: File
        get() {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val dir = File(base, "PDF")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val prefs by lazy { context.getSharedPreferences("scan_pdf_library", Context.MODE_PRIVATE) }

    fun list(): List<PdfItem> {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".pdf", true) } ?: emptyArray()
        return files
            .sortedByDescending { it.lastModified() }
            .map { item(it) }
    }

    fun recent(limit: Int): List<PdfItem> = list().take(limit)

    fun item(file: File): PdfItem = PdfItem(
        file = file,
        name = file.name.removeSuffix(".pdf"),
        pages = cachedPageCount(file),
        sizeBytes = file.length(),
        modified = file.lastModified()
    )

    fun find(name: String): PdfItem? = list().firstOrNull { it.name == name }

    fun exists(nameWithExtension: String): Boolean = File(directory, nameWithExtension).exists()

    /** 새 PDF 파일 경로 (아직 만들지 않음) */
    fun newFile(baseName: String): File {
        val unique = FileNames.unique(baseName) { exists(it) }
        return File(directory, "$unique.pdf")
    }

    /** 같은 이름이 있으면 비켜 간 새 이름 */
    fun availableName(baseName: String, ignore: File? = null): String {
        val safe = FileNames.sanitize(baseName) ?: "ScanPDF"
        var candidate = safe
        var index = 2
        while ((File(directory, "$candidate.pdf").exists() && File(directory, "$candidate.pdf") != ignore)) {
            candidate = "$safe ($index)"
            index++
            if (index > 999) break
        }
        return candidate
    }

    fun rename(item: PdfItem, newBase: String): File? {
        val safe = FileNames.sanitize(newBase) ?: return null
        if (safe == item.name) return item.file
        val target = File(directory, availableName(safe, item.file) + ".pdf")
        if (item.file.renameTo(target)) {
            forgetPageCount(item.file)
            return target
        }
        return null
    }

    fun delete(item: PdfItem): Boolean {
        val deleted = item.file.delete()
        if (deleted) {
            forgetPageCount(item.file)
            Thumbnails.forget(item.file)
        }
        return deleted
    }

    fun shareIntent(item: PdfItem): Intent {
        val uri = uriFor(item.file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    /**
     * 휴대폰의 다운로드 폴더로 복사한다 (Android 10 이상).
     * 파일 관리자·다른 앱에서 바로 보이게 할 때 쓴다.
     */
    fun exportToDownloads(item: PdfItem): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ScanPDF")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }
            .getOrNull() ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                item.file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("다운로드 폴더를 열 수 없습니다")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            "Download/ScanPDF/" + item.file.name
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** 처음 만든 PDF 를 앱 밖 앱으로도 열 수 있게 (다른 PDF 앱으로 열기) */
    fun viewIntent(item: PdfItem): Intent {
        val uri = uriFor(item.file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ------------------------------------------------------------ page cache

    private fun cachedPageCount(file: File): Int {
        val key = file.absolutePath
        val signature = file.length().toString() + ":" + file.lastModified()
        val stored = prefs.getString(key, null)
        if (stored != null && stored.startsWith("$signature|")) {
            return stored.substringAfter('|').toIntOrNull() ?: 0
        }
        val pages = PdfEngine.pageCount(file)
        prefs.edit().putString(key, "$signature|$pages").apply()
        return pages
    }

    private fun forgetPageCount(file: File) {
        prefs.edit().remove(file.absolutePath).apply()
    }

    /**
     * 목록·상세에 쓰는 첫 페이지 미리보기.
     * 메모리 캐시에만 두고, 화면이 사라지면 함께 사라진다.
     */
    object Thumbnails {
        private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

        fun load(file: File, maxWidthPx: Int): Bitmap? {
            val key = file.absolutePath + "|" + file.lastModified() + "|" + maxWidthPx
            cache.get(key)?.let { if (!it.isRecycled) return it }
            val bitmap = runCatching { renderFirstPage(file, maxWidthPx) }.getOrNull() ?: return null
            cache.put(key, bitmap)
            return bitmap
        }

        fun forget(file: File) {
            cache.snapshot().keys.filter { it.startsWith(file.absolutePath + "|") }.forEach { cache.remove(it) }
        }

        private fun renderFirstPage(file: File, maxWidthPx: Int): Bitmap? {
            PdfEngine.openRenderer(file).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = maxWidthPx.toFloat() / page.width
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    /** 미리보기를 JPEG 파일로 저장 (공유 미리보기 등) */
    fun writePreview(item: PdfItem, target: File): Boolean = runCatching {
        val bitmap = Thumbnails.load(item.file, 600) ?: return false
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    }.getOrDefault(false)
}
