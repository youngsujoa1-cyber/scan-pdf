package com.youngsu.scanpdf.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.ads.AdManager
import com.youngsu.scanpdf.data.PdfItem
import com.youngsu.scanpdf.data.PdfLibrary
import com.youngsu.scanpdf.edit.PageOrganizerActivity
import com.youngsu.scanpdf.ocr.OcrActivity
import com.youngsu.scanpdf.pdf.CompressPreset
import com.youngsu.scanpdf.pdf.PdfEngine
import com.youngsu.scanpdf.util.ByteSize
import com.youngsu.scanpdf.util.FileNames
import com.youngsu.scanpdf.sign.SignActivity
import com.youngsu.scanpdf.viewer.PdfViewerActivity
import java.util.concurrent.Executors

/** 목록에서 파일에 할 수 있는 일들 — 열기·공유·이름 변경·저장·압축·삭제 */
object DocumentActions {

    private val executor = Executors.newSingleThreadExecutor()

    fun open(activity: Activity, item: PdfItem) {
        activity.startActivity(PdfViewerActivity.intent(activity, item.file))
    }

    fun share(activity: Activity, item: PdfItem) {
        val library = PdfLibrary(activity)
        runCatching {
            activity.startActivity(Intent.createChooser(library.shareIntent(item), activity.getString(R.string.share)))
        }.onFailure {
            Dialogs.toast(activity, activity.getString(R.string.share_no_app))
        }
    }

    fun menu(activity: Activity, item: PdfItem, onChanged: () -> Unit) {
        val entries = ArrayList<Pair<String, () -> Unit>>()
        entries.add(activity.getString(R.string.open) to { open(activity, item) })
        entries.add(activity.getString(R.string.share) to { share(activity, item) })
        entries.add(activity.getString(R.string.ocr_title) to { activity.startActivity(OcrActivity.intentForPdf(activity, item.file)) })
        entries.add(activity.getString(R.string.sign_title) to { activity.startActivity(SignActivity.intent(activity, item.file)) })
        entries.add(activity.getString(R.string.organize_title) to { activity.startActivity(PageOrganizerActivity.intent(activity, item.file)) })
        entries.add(activity.getString(R.string.rename) to { rename(activity, item, onChanged) })
        entries.add(activity.getString(R.string.compress_strength) to { compress(activity, item, onChanged) })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            entries.add(activity.getString(R.string.export_downloads) to { export(activity, item) })
        }
        entries.add(activity.getString(R.string.delete) to { delete(activity, item, onChanged) })
        MaterialAlertDialogBuilder(activity)
            .setTitle(item.name)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun rename(activity: Activity, item: PdfItem, onChanged: () -> Unit) {
        Dialogs.askName(
            activity,
            activity.getString(R.string.rename_title),
            item.name,
            activity.getString(R.string.save_name_label),
            activity.getString(R.string.save_confirm)
        ) { raw ->
            val renamed = PdfLibrary(activity).rename(item, raw)
            if (renamed == null) {
                Dialogs.toast(activity, activity.getString(R.string.rename_invalid))
            } else {
                Dialogs.toast(activity, activity.getString(R.string.rename_done))
                onChanged()
            }
        }
    }

    fun delete(activity: Activity, item: PdfItem, onChanged: () -> Unit) {
        Dialogs.confirm(
            activity,
            activity.getString(R.string.delete_title),
            activity.getString(R.string.delete_body, item.name),
            activity.getString(R.string.delete)
        ) {
            val deleted = PdfLibrary(activity).delete(item)
            if (deleted) {
                Dialogs.toast(activity, activity.getString(R.string.deleted))
                onChanged()
            }
        }
    }

    fun export(activity: Activity, item: PdfItem) {
        val path = PdfLibrary(activity).exportToDownloads(item)
        Dialogs.toast(activity, activity.getString(if (path != null) R.string.export_done else R.string.export_failed))
    }

    /** 압축 강도를 고르고, 백그라운드에서 압축한 뒤 결과를 보여 준다. */
    fun compress(activity: Activity, item: PdfItem, onChanged: () -> Unit) {
        val presets = arrayOf(CompressPreset.LIGHT, CompressPreset.NORMAL, CompressPreset.STRONG)
        val labels = arrayOf(
            activity.getString(R.string.compress_light) + " · " + activity.getString(R.string.compress_light_sub),
            activity.getString(R.string.compress_normal) + " · " + activity.getString(R.string.compress_normal_sub),
            activity.getString(R.string.compress_strong) + " · " + activity.getString(R.string.compress_strong_sub)
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.compress_strength)
            .setSingleChoiceItems(labels, 1) { dialog, which ->
                dialog.dismiss()
                runCompress(activity, item, presets[which], onChanged)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runCompress(activity: Activity, item: PdfItem, preset: CompressPreset, onChanged: () -> Unit) {
        val library = PdfLibrary(activity)
        val suffix = activity.getString(R.string.compress_suffix)
        val baseName = FileNames.sanitize(item.name + " (" + suffix + ")") ?: "compressed"
        val target = library.newFile(baseName)
        val progress = Dialogs.Progress(activity, activity.getString(R.string.compress_saving))
        progress.show()

        executor.execute {
            try {
                val result = PdfEngine.compress(item.file, target, preset, target.name.removeSuffix(".pdf")) { done, total ->
                    progress.setMessage(activity.getString(R.string.compress_progress, done, total))
                }
                progress.dismiss()
                if (!result.improved) {
                    target.delete()
                    activity.runOnUiThread {
                        Dialogs.message(
                            activity,
                            activity.getString(R.string.compress_done_title),
                            activity.getString(R.string.compress_no_gain),
                            activity.getString(R.string.done)
                        )
                    }
                    return@execute
                }
                val newItem = library.item(result.output)
                activity.runOnUiThread {
                    AdManager.showInterstitialIfReady(activity) {
                        onChanged()
                        val body = activity.getString(R.string.compress_compare, ByteSize.format(result.beforeBytes), ByteSize.format(result.afterBytes)) +
                            "\n" + activity.getString(R.string.compress_saved, result.savedPercent) +
                            "\n" + newItem.name
                        Dialogs.message(
                            activity,
                            activity.getString(R.string.compress_done_title),
                            body,
                            activity.getString(R.string.open),
                            onPositive = { open(activity, newItem) },
                            negative = activity.getString(R.string.close)
                        )
                    }
                }
            } catch (t: Throwable) {
                progress.dismiss()
                runCatching { target.delete() }
                activity.runOnUiThread {
                    Dialogs.toast(activity, activity.getString(R.string.compress_error, t.message ?: ""))
                }
            }
        }
    }
}
