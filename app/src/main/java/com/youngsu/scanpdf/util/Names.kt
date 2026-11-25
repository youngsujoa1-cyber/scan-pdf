package com.youngsu.scanpdf.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 용량 표기 — 1.2 MB 처럼 짧게 */
object ByteSize {

    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 100) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.0f MB", mb)
    }

    /** 압축률(%) — 원본 대비 줄어든 비율 */
    fun savedPercent(before: Long, after: Long): Int {
        if (before <= 0) return 0
        val saved = ((before - after).toDouble() / before * 100.0).toInt()
        return saved.coerceIn(0, 100)
    }
}

/** 파일 이름 규칙 */
object FileNames {

    private val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\n', '\r', '\t')
    private const val MAX_LENGTH = 80

    /** 사용자가 입력한 이름을 안전하게 다듬는다. 쓸 수 없으면 null. */
    fun sanitize(raw: String): String? {
        var name = raw.trim()
        if (name.lowercase(Locale.ROOT).endsWith(".pdf")) {
            name = name.dropLast(4)
        }
        name = name.map { if (it in ILLEGAL) ' ' else it }.joinToString("")
        name = name.replace(Regex("\\s+"), " ").trim().trim('.')
        if (name.isEmpty()) return null
        if (name.length > MAX_LENGTH) name = name.take(MAX_LENGTH).trim()
        if (name.isEmpty()) return null
        return name
    }

    /** 기본 이름: "스캔 2026-11-25 04.12" */
    fun defaultName(prefix: String, timeMillis: Long = System.currentTimeMillis()): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault())
        return "$prefix ${format.format(Date(timeMillis))}"
    }

    /** 같은 이름이 있으면 "이름 (2).pdf" 처럼 비켜 간다. */
    fun unique(base: String, exists: (String) -> Boolean): String {
        val safe = sanitize(base) ?: "ScanPDF"
        if (!exists("$safe.pdf")) return safe
        var index = 2
        while (index < 1000) {
            val candidate = "$safe ($index)"
            if (!exists("$candidate.pdf")) return candidate
            index++
        }
        return "$safe ${System.currentTimeMillis()}"
    }
}
