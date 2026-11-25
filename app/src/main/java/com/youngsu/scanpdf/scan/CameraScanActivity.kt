package com.youngsu.scanpdf.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.data.PageRenderer
import com.youngsu.scanpdf.data.ScanPage
import com.youngsu.scanpdf.data.ScanSession
import com.youngsu.scanpdf.edit.PageEditorActivity
import com.youngsu.scanpdf.ui.Dialogs
import com.youngsu.scanpdf.ui.Ui
import com.youngsu.scanpdf.ui.dp
import com.youngsu.scanpdf.ui.str
import java.io.File
import java.util.concurrent.Executors

/**
 * 문서를 연속으로 찍는 카메라 화면.
 * 찍은 사진은 세션 캐시에 쌓이고, [완료] 를 누르면 편집 화면으로 넘어간다.
 */
class CameraScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var root: FrameLayout
    private lateinit var counterText: TextView
    private lateinit var doneButton: TextView
    private lateinit var thumbStrip: LinearLayout
    private lateinit var flashOverlay: View
    private lateinit var permissionCard: View
    private lateinit var hintText: TextView
    private lateinit var torchButton: ImageView

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var torchOn = false
    private var captureInFlight = false

    private val thumbExecutor = Executors.newSingleThreadExecutor()

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionCard.visibility = View.GONE
            startCamera()
        } else {
            showPermissionCard(denied = true)
        }
    }

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        addFromGallery(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(DocumentGuideView(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        flashOverlay = View(this).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
        }
        root.addView(flashOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        buildTopBar()
        buildBottomBar()

        permissionCard = buildPermissionCard()
        root.addView(permissionCard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(6f))
            bottomBar.updatePadding(bottom = bars.bottom + dp(10f))
            insets
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            permissionCard.visibility = View.GONE
            startCamera()
        } else {
            showPermissionCard(denied = false)
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        updateCounter()
        updateThumbs()
    }

    // ------------------------------------------------------------------ UI

    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout

    private fun buildTopBar() {
        topBar = Ui.row(this).apply {
            // 밝은 배경에서도 닫기·안내 글자가 읽히도록 위쪽에 어두운 그라데이션
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xD903060C.toInt(), 0x0003060C)
            )
            setPadding(dp(12f), dp(6f), dp(12f), dp(10f))
        }
        val close = Ui.icon(this, R.drawable.ic_close, 20f, Color.WHITE).apply {
            background = Ui.rounded(this@CameraScanActivity, 0x66000000.toInt(), 999f)
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
        }
        val closeWrap = FrameLayout(this).apply {
            addView(close, FrameLayout.LayoutParams(dp(44f), dp(44f)))
            Ui.clickable(this) { finishIfEmpty() }
        }
        topBar.addView(closeWrap)

        val titles = Ui.column(this).apply {
            setPadding(dp(12f), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        }
        titles.addView(Ui.text(this, str(R.string.camera_title), 16f, Color.WHITE, bold = true))
        hintText = Ui.text(this, str(R.string.camera_hint), 11.5f, 0xB3FFFFFF.toInt()).apply {
            setPadding(0, dp(2f), 0, 0)
        }
        titles.addView(hintText)
        topBar.addView(titles)

        torchButton = Ui.icon(this, R.drawable.ic_flash_off, 20f, Color.WHITE).apply {
            background = Ui.rounded(this@CameraScanActivity, 0x66000000.toInt(), 999f)
            setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
            visibility = View.GONE
            setOnClickListener { toggleTorch() }
        }
        topBar.addView(torchButton, LinearLayout.LayoutParams(dp(44f), dp(44f)))

        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP
        })
    }

    private fun buildBottomBar() {
        bottomBar = Ui.column(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xF203060C.toInt(), 0x0003060C)).apply {
                cornerRadius = 0f
            }
            setPadding(dp(16f), dp(18f), dp(16f), dp(10f))
        }

        thumbStrip = Ui.row(this).apply {
            gravity = Gravity.CENTER
        }
        bottomBar.addView(thumbStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56f)).apply {
            bottomMargin = dp(12f)
        })

        val controls = Ui.row(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val galleryButton = Ui.icon(this, R.drawable.ic_gallery, 22f, Color.WHITE).apply {
            background = Ui.rounded(this@CameraScanActivity, 0x33FFFFFF, 999f)
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            setOnClickListener {
                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        controls.addView(galleryButton, LinearLayout.LayoutParams(dp(52f), dp(52f)))

        val shutterWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(84f)).apply { weight = 1f }
        }
        val shutter = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            Ui.clickable(this) { capture() }
        }
        val inner = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(getColor(R.color.accent))
            }
        }
        shutter.addView(inner, FrameLayout.LayoutParams(dp(58f), dp(58f), Gravity.CENTER))
        shutterWrap.addView(shutter, FrameLayout.LayoutParams(dp(76f), dp(76f), Gravity.CENTER))
        controls.addView(shutterWrap)

        val doneColumn = Ui.column(this).apply {
            gravity = Gravity.CENTER
        }
        counterText = Ui.text(this, "", 11.5f, 0xFFEAF0FA.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(999f).toFloat()
                setColor(0x66101828)
                setStroke(dp(1f), 0x55FFFFFF)
            }
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        }
        doneButton = Ui.text(this, str(R.string.camera_done), 12.5f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(999f).toFloat()
                setColor(getColor(R.color.accent))
            }
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnClickListener { openEditor() }
        }
        doneColumn.addView(counterText)
        doneColumn.addView(doneButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8f)
        })
        val doneWrap = FrameLayout(this).apply {
            addView(doneColumn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            minimumWidth = dp(64f)
        }
        controls.addView(doneWrap, LinearLayout.LayoutParams(dp(72f), dp(72f)))

        bottomBar.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(bottomBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
        })
    }

    private fun buildPermissionCard(): View {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xE6060A12.toInt())
            visibility = View.GONE
        }
        val card = Ui.column(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f), dp(28f), dp(24f), dp(24f))
            background = Ui.rounded(this@CameraScanActivity, 0xFF111A28.toInt(), 24f, 0xFF22314A.toInt(), ripple = false)
        }
        card.addView(Ui.icon(this, R.drawable.ic_camera, 30f, getColor(R.color.accent)), LinearLayout.LayoutParams(dp(30f), dp(30f)))
        card.addView(Ui.text(this, str(R.string.camera_permission_title), 17f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14f), 0, 0)
        })
        card.addView(Ui.text(this, str(R.string.camera_permission_body), 13f, 0xFF93A4BE.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
        })
        val settingsButton = Ui.button(this, str(R.string.camera_settings), primary = true) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        card.addView(settingsButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(20f)
        })
        overlay.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = dp(28f)
            rightMargin = dp(28f)
        })
        return overlay
    }

    private fun showPermissionCard(denied: Boolean) {
        permissionCard.visibility = View.VISIBLE
        hintText.text = str(if (denied) R.string.camera_permission_denied else R.string.camera_hint)
    }

    // -------------------------------------------------------------- camera

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                    .build()
                provider?.unbindAll()
                camera = provider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
                torchButton.visibility = if (camera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.GONE
            }.onFailure { error ->
                Dialogs.toast(this, str(R.string.camera_start_failed, error.message ?: ""))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        val control = camera?.cameraControl ?: return
        torchOn = !torchOn
        control.enableTorch(torchOn)
        torchButton.setImageResource(if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    private fun capture() {
        val capture = imageCapture ?: return
        if (captureInFlight) return
        captureInFlight = true
        val file = ScanSession.newPhotoFile(this)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                captureInFlight = false
                ScanSession.add(ScanPage(file))
                flash()
                updateCounter()
                updateThumbs()
            }

            override fun onError(exception: ImageCaptureException) {
                captureInFlight = false
                Dialogs.toast(this@CameraScanActivity, str(R.string.camera_saved_failed))
            }
        })
    }

    private fun flash() {
        flashOverlay.animate().cancel()
        flashOverlay.alpha = 0.85f
        flashOverlay.animate().alpha(0f).setDuration(260L).start()
    }

    private fun updateCounter() {
        val count = ScanSession.size()
        counterText.text = str(R.string.camera_shots_taken, count)
        doneButton.alpha = if (count > 0) 1f else 0.4f
    }

    private fun updateThumbs() {
        val pages = ScanSession.pages.toList()
        thumbStrip.removeAllViews()
        val show = pages.takeLast(5)
        show.forEach { page ->
            val container = FrameLayout(this).apply {
                background = Ui.rounded(this@CameraScanActivity, 0xFFEAF0FA.toInt(), 7f, 0x99FFFFFF.toInt(), ripple = false)
                setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
            }
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
            container.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            thumbStrip.addView(container, LinearLayout.LayoutParams(dp(38f), dp(48f)).apply {
                leftMargin = dp(4f)
                rightMargin = dp(4f)
            })
            Ui.clickable(container) {
                openEditor()
            }
            loadThumb(page.file, image)
        }
    }

    private fun loadThumb(file: File, target: ImageView) {
        thumbExecutor.execute {
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bitmap: Bitmap? = runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
            if (bitmap != null) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) target.setImageBitmap(bitmap)
                }
            }
        }
    }

    // -------------------------------------------------------------- gallery

    private fun addFromGallery(uris: List<Uri>) {
        val dir = ScanSession.directory(this)
        var added = 0
        for (uri in uris) {
            val target = File(dir, "photo_" + System.currentTimeMillis() + "_" + added + ".jpg")
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (copied) {
                ScanSession.add(ScanPage(target))
                added++
            }
        }
        if (added == 0) {
            Dialogs.toast(this, str(R.string.camera_saved_failed))
            return
        }
        updateCounter()
        updateThumbs()
        openEditor()
    }

    // --------------------------------------------------------------- flow

    private fun openEditor() {
        if (ScanSession.isEmpty()) {
            Dialogs.toast(this, str(R.string.editor_need_one_page))
            return
        }
        if (intent.getBooleanExtra(EXTRA_ADD_MODE, false)) {
            setResult(RESULT_OK)
            finish()
            return
        }
        startActivity(Intent(this, PageEditorActivity::class.java))
        finish()
    }

    private fun finishIfEmpty() {
        // 편집 화면에서 "페이지 추가"로 들어온 경우에는 찍은 것만 버리고 돌아간다
        if (intent.getBooleanExtra(EXTRA_ADD_MODE, false)) {
            finish()
            return
        }
        if (ScanSession.isEmpty()) {
            finish()
        } else {
            Dialogs.confirm(
                this,
                str(R.string.editor_leave_title),
                str(R.string.editor_leave_body),
                str(R.string.editor_leave_confirm),
                onConfirm = {
                    ScanSession.clear()
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        provider?.unbindAll()
        thumbExecutor.shutdown()
    }

    companion object {
        /** 편집 화면에서 "페이지 추가"로 열렸을 때 true — 완료를 누르면 결과만 돌려주고 닫힌다. */
        const val EXTRA_ADD_MODE = "add_mode"
    }
}
