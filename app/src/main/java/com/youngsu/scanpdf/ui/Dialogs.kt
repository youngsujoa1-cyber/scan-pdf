package com.youngsu.scanpdf.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.scanpdf.R

object Dialogs {

    fun toast(context: Context, message: CharSequence) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 진행 상황을 보여 주는 작은 다이얼로그.
     * [message] 는 작업 스레드에서도 바꿀 수 있다.
     */
    class Progress(context: Context, title: CharSequence) {

        private val handler = Handler(Looper.getMainLooper())
        private val label: TextView
        private val dialog: androidx.appcompat.app.AlertDialog

        init {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(context.dp(24f), context.dp(26f), context.dp(24f), context.dp(10f))
            }
            val spinner = ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.accent))
                layoutParams = LinearLayout.LayoutParams(context.dp(38f), context.dp(38f))
            }
            label = Ui.text(context, title, 14f, context.getColor(R.color.text_secondary)).apply {
                gravity = Gravity.CENTER
                setPadding(0, context.dp(16f), 0, context.dp(6f))
            }
            column.addView(spinner)
            column.addView(label)
            dialog = MaterialAlertDialogBuilder(context)
                .setView(column)
                .setCancelable(false)
                .create()
        }

        fun show() {
            runCatching { dialog.show() }
        }

        fun setMessage(message: CharSequence) {
            handler.post { label.text = message }
        }

        fun dismiss() {
            handler.post { runCatching { dialog.dismiss() } }
        }
    }

    /** 이름 입력 다이얼로그. 확인을 누르면 다듬어진 이름을 돌려준다. */
    fun askName(
        activity: Activity,
        title: CharSequence,
        initial: String,
        hint: String,
        positiveLabel: CharSequence = activity.getString(R.string.save_confirm),
        onResult: (String) -> Unit
    ) {
        val input = EditText(activity).apply {
            setText(initial)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setSelectAllOnFocus(true)
            setTextColor(activity.getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(activity.dp(18f), activity.dp(14f), activity.dp(18f), activity.dp(14f))
            background = Ui.rounded(activity, activity.getColor(R.color.surface), 14f, activity.getColor(R.color.outline), ripple = false)
        }
        val container = FrameLayout(activity).apply {
            setPadding(activity.dp(20f), activity.dp(8f), activity.dp(20f), 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positiveLabel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString().orEmpty().trim()
                if (name.isEmpty()) {
                    Dialogs.toast(activity, activity.getString(R.string.rename_invalid))
                    return@setOnClickListener
                }
                dialog.dismiss()
                onResult(name)
            }
        }
        dialog.show()
    }

    /** 제목·본문·확인 버튼 하나짜리 안내 */
    fun message(
        context: Context,
        title: CharSequence,
        body: CharSequence,
        positive: CharSequence,
        onPositive: (() -> Unit)? = null,
        negative: CharSequence? = null,
        onNegative: (() -> Unit)? = null
    ) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(positive) { _, _ -> onPositive?.invoke() }
        if (negative != null) {
            builder.setNegativeButton(negative) { _, _ -> onNegative?.invoke() }
        }
        builder.show()
    }

    fun confirm(
        context: Context,
        title: CharSequence,
        body: CharSequence,
        positive: CharSequence,
        negative: CharSequence? = null,
        onConfirm: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(body)
            .setNegativeButton(negative ?: context.getString(R.string.cancel), null)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
            .show()
    }

    /**
     * 작업 스레드에서 [work] 를 돌리고, 끝나면 메인 스레드에서 [onDone] 을 부른다.
     * [onError] 에는 사람이 읽을 수 있는 메시지를 넘긴다.
     */
    fun <T> runAsync(
        activity: Activity,
        errorPrefix: (Throwable) -> String,
        work: () -> T,
        onDone: (T) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val result = work()
                handler.post { if (!activity.isFinishing && !activity.isDestroyed) onDone(result) }
            } catch (t: Throwable) {
                handler.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        toast(activity, errorPrefix(t))
                    }
                }
            }
        }.start()
    }
}
