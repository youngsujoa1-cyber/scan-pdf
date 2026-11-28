package com.youngsu.scanpdf.sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * 저장해 둔 서명 — 한 번 만들어 두면 다음부터는 골라 쓰기만 하면 된다.
 * 파일은 앱 전용 폴더(files/signatures)에 PNG 로 두고, 최근 [MAX_KEEP]개만 남긴다.
 */
class SignatureStore(private val context: Context) {

    val directory: File
        get() {
            val dir = File(context.filesDir, "signatures")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun list(): List<File> =
        (directory.listFiles { file -> file.isFile && file.name.endsWith(".png") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }

    fun save(bitmap: Bitmap): File? = runCatching {
        val file = File(directory, "sign_" + System.currentTimeMillis() + ".png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        trim()
        file
    }.getOrNull()

    fun load(file: File): Bitmap? = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()

    fun delete(file: File) {
        runCatching { file.delete() }
    }

    private fun trim() {
        val files = list()
        files.drop(MAX_KEEP).forEach { runCatching { it.delete() } }
    }

    companion object {
        private const val MAX_KEEP = 12
    }
}
