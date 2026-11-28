package com.youngsu.scanpdf.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * 온디바이스 글자 인식(OCR).
 *
 * ML Kit 의 글자 인식 모델(라틴·한글·중국어·일본어)을 앱에 포함해 두어 인터넷 없이 동작한다.
 * 한 번에 한 문자 체계만 읽을 수 있어서, '자동'은 앱 언어에 맞는 모델과 라틴 모델을 모두 돌려
 * 실제로 글자를 더 많이 읽어 낸 쪽을 고른다. 사진은 기기 밖으로 나가지 않는다.
 */
object OcrEngine {

    /** 읽을 문자 체계 */
    enum class Script { AUTO, LATIN, KOREAN, CHINESE, JAPANESE }

    data class Result(
        val text: String,
        val script: Script,
        /** 인식에 쓴 모델(자동일 때 실제로 고른 쪽) */
        val used: Script
    ) {
        val isEmpty: Boolean get() = text.isBlank()
    }

    /**
     * [bitmap] 에서 글자를 읽는다. 네트워크를 쓰지 않지만 모델 실행이라 시간이 걸리므로
     * 반드시 작업 스레드에서 부른다(메인 스레드에서 부르면 예외).
     */
    fun recognize(context: Context, bitmap: Bitmap, script: Script): Result {
        val candidates = candidatesFor(context, script)
        var best = Result("", script, candidates.first())
        var bestScore = -1
        candidates.forEach { candidate ->
            val text = runCatching {
                val image = InputImage.fromBitmap(bitmap, 0)
                Tasks.await(recognizer(candidate).process(image), TIMEOUT_SECONDS, TimeUnit.SECONDS).text
            }.getOrDefault("").trim()
            val score = if (candidate == Script.KOREAN || candidate == Script.CHINESE || candidate == Script.JAPANESE) {
                // CJK 모델은 라틴 글자도 읽으므로, 해당 문자 체계 글자에 가중치를 준다
                usefulCount(text) + scriptCount(text, candidate)
            } else {
                usefulCount(text)
            }
            if (score > bestScore) {
                bestScore = score
                best = Result(text, script, candidate)
            }
        }
        return best
    }

    /** 자동일 때 돌려 볼 모델 목록 — 앱 언어가 한자·가나·한글이면 그 모델을 먼저 */
    private fun candidatesFor(context: Context, script: Script): List<Script> = when (script) {
        Script.AUTO -> {
            val tag = com.youngsu.scanpdf.data.AppLanguage.currentTag()
            val preferred = when {
                tag == "ko" -> Script.KOREAN
                tag == "ja" -> Script.JAPANESE
                tag == "zh" -> Script.CHINESE
                else -> null
            }
            if (preferred == null) listOf(Script.LATIN) else listOf(preferred, Script.LATIN)
        }
        else -> listOf(script)
    }

    private val recognizers = HashMap<Script, TextRecognizer>()

    private fun recognizer(script: Script): TextRecognizer = synchronized(recognizers) {
        recognizers.getOrPut(script) {
            when (script) {
                Script.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.Builder().build())
            }
        }
    }

    /** 글자·숫자 개수 (공백·기호 제외) */
    fun usefulCount(text: String): Int = text.count { it.isLetterOrDigit() }

    /** 해당 문자 체계 글자 개수 */
    fun scriptCount(text: String, script: Script): Int = text.count { ch ->
        when (script) {
            Script.KOREAN -> ch in '\uAC00'..'\uD7A3' || ch in '\u1100'..'\u11FF'
            Script.JAPANESE -> ch in '\u3040'..'\u30FF'
            Script.CHINESE -> ch in '\u4E00'..'\u9FFF'
            else -> ch.code < 0x250 && ch.isLetter()
        }
    }

    /**
     * 화면에 보여 줄 글자 다듬기 — 줄 끝 공백과 빈 줄이 겹친 부분만 정리한다.
     * (원문을 바꾸지 않아 복사한 내용이 인식 결과와 같다)
     */
    fun tidy(raw: String): String = raw
        .replace("\r\n", "\n")
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private const val TIMEOUT_SECONDS = 45L
}
