package com.youngsu.scanpdf.edit

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.data.PageRenderer
import com.youngsu.scanpdf.pdf.ScanGeometry
import com.youngsu.scanpdf.ui.Dialogs
import com.youngsu.scanpdf.ui.Ui
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str
import java.io.File
import java.util.concurrent.Executors

/**
 * 페이지 자르기.
 * 들어온 사진을 화면에 맞춰 보여 주고, 4점 사각형을 돌려준다.
 * 돌려주는 좌표는 "화면에 보이는(회전된) 이미지" 기준이고,
 * 편집 화면이 원본 좌표로 되돌려 저장한다.
 */
class CropActivity : AppCompatActivity() {

    private lateinit var overlay: CropOverlayView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var loading: FrameLayout
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val file = File(intent.getStringExtra(EXTRA_PATH) ?: return finish())
        val rotation = intent.getIntExtra(EXTRA_ROTATION, 0)
        val initialQuad = intent.getFloatArrayExtra(EXTRA_QUAD)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg_deep))
        }
        setContentView(root)

        topBar = Ui.row(this).apply {
            setPadding(dp(8f), dp(8f), dp(10f), dp(4f))
        }
        val cancel = Ui.text(this, str(R.string.cancel), 14f, getColor(R.color.text_secondary), bold = true).apply {
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            setOnClickListener { finish() }
        }
        topBar.addView(cancel)
        topBar.addView(Ui.text(this, str(R.string.crop_title), 16f, getColor(R.color.text_primary), bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        })
        val apply = Ui.text(this, str(R.string.crop_apply), 14f, getColor(R.color.on_accent), bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(this@CropActivity, getColor(R.color.accent), 999f)
            setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
            setOnClickListener { finishWithQuad() }
        }
        topBar.addView(apply)
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val stage = FrameLayout(this)
        overlay = CropOverlayView(this).apply {
            quad = initialQuad ?: ScanGeometry.fullQuad()
        }
        stage.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        loading = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.bg_deep))
        }
        stage.addView(loading, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

        bottomBar = Ui.column(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20f), dp(10f), dp(20f), dp(14f))
        }
        bottomBar.addView(Ui.text(this, str(R.string.crop_hint), 12f, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
        })
        bottomBar.addView(Ui.button(this, str(R.string.crop_reset), primary = false, iconRes = R.drawable.ic_crop) {
            overlay.resetToFull()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12f)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        root.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(4f))
            bottomBar.updatePadding(bottom = bars.bottom + dp(10f))
            insets
        }

        // 사진은 조금 크게 읽어 자른 뒤에도 선명하게
        executor.execute {
            val bitmap = runCatching { PageRenderer.render(file, rotation, null, com.youngsu.scanpdf.pdf.Filters.Kind.ORIGINAL, 1600) }
                .getOrElse {
                    runOnUiThread {
                        Dialogs.toast(this, str(R.string.editor_preview_failed))
                        finish()
                    }
                    null
                }
            if (bitmap != null) {
                runOnUiThread {
                    overlay.bitmap = bitmap
                    loading.visibility = ImageView.GONE
                }
            }
        }
    }

    private fun finishWithQuad() {
        val result = Intent().apply {
            putExtra(EXTRA_QUAD, overlay.quad)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_ROTATION = "rotation"
        const val EXTRA_QUAD = "quad"

        fun intent(context: Context, file: File, rotation: Int, quad: FloatArray?): Intent =
            Intent(context, CropActivity::class.java).apply {
                putExtra(EXTRA_PATH, file.absolutePath)
                putExtra(EXTRA_ROTATION, rotation)
                putExtra(EXTRA_QUAD, quad)
            }
    }
}
