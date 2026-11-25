package com.youngsu.scanpdf.data

import android.content.Context
import com.youngsu.scanpdf.pdf.PdfQuality

/** 앱 설정 — PDF 화질과 첫 실행 언어 선택 완료 여부 */
class AppPrefs private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("scan_pdf_prefs", Context.MODE_PRIVATE)

    var quality: PdfQuality
        get() = PdfQuality.fromId(prefs.getString(KEY_QUALITY, null))
        set(value) = prefs.edit().putString(KEY_QUALITY, value.name).apply()

    /** 첫 실행 언어 선택을 마쳤는지 — false 면 앱을 열 때 언어 카드가 먼저 뜬다 */
    var languageOnboarded: Boolean
        get() = prefs.getBoolean(KEY_LANGUAGE_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_LANGUAGE_ONBOARDED, value).apply()

    companion object {
        private const val KEY_QUALITY = "pdf_quality"
        private const val KEY_LANGUAGE_ONBOARDED = "language_onboarded"
        @Volatile private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context).also { instance = it }
            }
    }
}
