package com.youngsu.scanpdf.data

import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.youngsu.scanpdf.R

/**
 * 앱 안에서 고르는 언어 — 화질 설정처럼 앱이 직접 기억한다.
 *
 * 언어를 바꾸면 AppCompatDelegate.setApplicationLocales 가 열려 있는 화면을 모두
 * 새 언어로 다시 만든다(카메라·편집·뷰어 포함). 액티비티가 모두 AppCompatActivity 라
 * Android 8~15 어디서나 같은 방식으로 동작하고, Android 13 부터의
 * '앱별 언어' 시스템 설정에도 같은 값이 보인다.
 *
 * 기기 언어를 따르는 선택은 빈 태그("") 로 표현한다.
 */
object AppLanguage {

    /** 언어 하나 — 태그, 그 나라 말 이름, 원형 국기 그림 */
    data class Lang(val tag: String, val nativeName: String, @param:DrawableRes val flag: Int)

    /** 지원하는 12개 언어 (1.1.0) */
    val LANGS = listOf(
        Lang("ko", "한국어", R.drawable.flag_ko),
        Lang("en", "English", R.drawable.flag_en),
        Lang("ja", "日本語", R.drawable.flag_ja),
        Lang("zh", "中文", R.drawable.flag_zh),
        Lang("es", "Español", R.drawable.flag_es),
        Lang("pt", "Português", R.drawable.flag_pt),
        Lang("fr", "Français", R.drawable.flag_fr),
        Lang("de", "Deutsch", R.drawable.flag_de),
        Lang("id", "Bahasa Indonesia", R.drawable.flag_id),
        Lang("vi", "Tiếng Việt", R.drawable.flag_vi),
        Lang("hi", "हिन्दी", R.drawable.flag_hi),
        Lang("ar", "العربية", R.drawable.flag_ar)
    )

    /** 선택 목록 순서 — "" 는 '기기 언어 따르기' */
    val TAGS: List<String> = listOf("") + LANGS.map { it.tag }

    /** 지금 적용 중인 태그. 기기 언어를 따르면 "" */
    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        val language = locales.toLanguageTags().substringBefore(',').substringBefore('-')
        return if (language in TAGS) language else ""
    }

    fun langOf(tag: String): Lang? = LANGS.firstOrNull { it.tag == tag }

    /** 언어 적용 — 화면은 곧바로 새 언어로 다시 그려진다 */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }

    /** 기기(또는 앱) 언어가 오른쪽에서 왼쪽으로 읽는 언어인지 (아랍어) */
    fun isRtl(tag: String): Boolean = tag == "ar"
}
