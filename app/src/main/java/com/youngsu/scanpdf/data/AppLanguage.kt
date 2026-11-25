package com.youngsu.scanpdf.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

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

    /** 선택 목록 순서 — "" 는 '기기 언어 따르기' */
    val TAGS = listOf("", "ko", "en", "ja", "zh", "es")

    /** 지금 적용 중인 태그. 기기 언어를 따르면 "" */
    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        val language = locales.toLanguageTags().substringBefore(',').substringBefore('-')
        return if (language in TAGS) language else ""
    }

    /** 언어 적용 — 화면은 곧바로 새 언어로 다시 그려진다 */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }
}
