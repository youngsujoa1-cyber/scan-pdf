package com.youngsu.scanpdf.ui

import android.view.View
import com.youngsu.scanpdf.R
import com.youngsu.scanpdf.data.PdfItem

/** 하단 탭 하나가 보여 주는 화면 */
abstract class Screen(protected val activity: MainActivity) {

    abstract val view: View

    open fun refresh() {}

    open fun onHidden() {}

    /** 상태 표시줄 높이를 화면이 알아서 비켜 간다 */
    open fun setSystemInsets(topInset: Int) {}

    protected fun openViewer(item: PdfItem) = DocumentActions.open(activity, item)

    protected fun editOrCreate() = activity.startScan()

    protected fun pickPhotos() = activity.startPhotoImport()

    protected fun mergePdfs() = activity.startMerge()

    protected fun compressPdf() = activity.startCompress()

    protected fun goToDocs() = activity.openTab(R.id.nav_docs)

    protected fun planSafe() = activity.showPrivacyNote()

    /** 화면 안에서 문구를 짧게 가져온다 */
    protected fun str(id: Int, vararg args: Any): String = activity.getString(id, *args)
}
