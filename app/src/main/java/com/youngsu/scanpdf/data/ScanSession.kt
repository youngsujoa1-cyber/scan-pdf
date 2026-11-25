package com.youngsu.scanpdf.data

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * 촬영/선택한 페이지를 편집 화면까지 들고 가는 임시 보관소.
 *
 * 사진은 캐시 폴더에 두고, 페이지 목록만 메모리에 둔다. 앱을 벗어나면 비운다.
 */
object ScanSession {

    val pages = mutableListOf<ScanPage>()
    private var sessionDir: File? = null

    fun directory(context: Context): File {
        val existing = sessionDir
        if (existing != null && existing.exists()) return existing
        val dir = File(context.cacheDir, "scan_" + UUID.randomUUID().toString().take(8))
        dir.mkdirs()
        sessionDir = dir
        return dir
    }

    /** 카메라가 사진을 저장할 새 파일 */
    fun newPhotoFile(context: Context): File =
        File(directory(context), "page_" + System.currentTimeMillis() + ".jpg")

    fun add(page: ScanPage) {
        pages.add(page)
    }

    fun isEmpty(): Boolean = pages.isEmpty()

    fun size(): Int = pages.size

    fun clear() {
        pages.clear()
        sessionDir?.let { dir ->
            runCatching { dir.deleteRecursively() }
        }
        sessionDir = null
    }
}
