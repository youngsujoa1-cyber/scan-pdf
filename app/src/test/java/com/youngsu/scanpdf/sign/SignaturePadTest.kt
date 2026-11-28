package com.youngsu.scanpdf.sign

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.youngsu.scanpdf.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 서명 패드 — 손가락 획을 받아 되돌리기·저장용 비트맵으로 만드는지 (Robolectric) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SignaturePadTest {

    private fun pad(): SignaturePadView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = SignaturePadView(context)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 200)
        return view
    }

    private fun drawStroke(view: SignaturePadView, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, fromX, fromY, 0)
        val move = MotionEvent.obtain(0, 16, MotionEvent.ACTION_MOVE, toX, toY, 0)
        val up = MotionEvent.obtain(0, 32, MotionEvent.ACTION_UP, toX, toY, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(move)
        view.dispatchTouchEvent(up)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `empty pad has no bitmap`() {
        val view = pad()
        assertTrue(view.isEmpty)
        assertNull(view.toTrimmedBitmap())
        assertNull(view.inkBounds())
    }

    @Test
    fun `drawing makes a transparent bitmap cropped to ink`() {
        val view = pad()
        drawStroke(view, 50f, 60f, 180f, 90f)
        assertFalse(view.isEmpty)

        val bitmap = view.toTrimmedBitmap(paddingPx = 4f, maxSize = 900)
        assertNotNull(bitmap)
        bitmap!!
        // 잉크 영역(130x30) + 여백이므로 패드 폭 400 보다 훨씬 작다
        assertTrue("width ${bitmap.width}", bitmap.width < 200)
        assertTrue("height ${bitmap.height}", bitmap.height < 120)
        assertTrue(bitmap.hasAlpha())
        // 모서리는 투명, 획 근처는 불투명
        assertEquals(0, bitmap.getPixel(0, 0) ushr 24)
        val bounds = view.inkBounds()!!
        assertTrue(bounds.width() >= 129f)
    }

    @Test
    fun `undo removes the last stroke`() {
        val view = pad()
        drawStroke(view, 20f, 20f, 100f, 40f)
        drawStroke(view, 40f, 120f, 220f, 150f)
        view.undo()
        assertFalse(view.isEmpty)
        view.undo()
        assertTrue(view.isEmpty)
    }

    @Test
    fun `clear removes everything`() {
        val view = pad()
        drawStroke(view, 20f, 20f, 100f, 40f)
        view.clear()
        assertTrue(view.isEmpty)
    }

    @Test
    fun `pen color is kept per stroke`() {
        val view = pad()
        view.penColor = android.graphics.Color.BLUE
        drawStroke(view, 10f, 10f, 90f, 30f)
        assertEquals(android.graphics.Color.BLUE, view.penColor)
    }
}

/** 12개 언어 목록 — 국기 그림과 태그가 모두 채워져 있는지 (Robolectric 자원 접근) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppLanguageTest {

    @Test
    fun `twelve languages plus follow-phone`() {
        assertEquals(12, AppLanguage.LANGS.size)
        assertEquals(13, AppLanguage.TAGS.size)
        assertEquals("", AppLanguage.TAGS.first())
        assertEquals(
            listOf("ko", "en", "ja", "zh", "es", "pt", "fr", "de", "id", "vi", "hi", "ar"),
            AppLanguage.LANGS.map { it.tag }
        )
    }

    @Test
    fun `every language has a flag and a native name`() {
        AppLanguage.LANGS.forEach { lang ->
            assertNotNull(lang.nativeName)
            assertTrue("${lang.tag} flag missing", lang.flag != 0)
            assertEquals(lang, AppLanguage.langOf(lang.tag))
        }
        assertNull(AppLanguage.langOf(""))
        assertNull(AppLanguage.langOf("xx"))
    }
}
