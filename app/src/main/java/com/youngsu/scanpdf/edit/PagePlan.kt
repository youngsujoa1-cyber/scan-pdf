package com.youngsu.scanpdf.edit

/**
 * PDF 페이지 순서 계획 — 화면에서 바꾼 순서·삭제를 그대로 들고 있는 작은 모델.
 *
 * 각 항목은 원본 페이지 번호(0부터)라서, 마지막에 [order] 를 PDF 에 넘기면 된다.
 * 화면(RecyclerView)과 저장 로직이 같은 모델을 보므로 보이는 순서대로 저장된다.
 */
class PagePlan(pageCount: Int) {

    private val pages = ArrayList<Int>(pageCount).apply {
        for (index in 0 until pageCount) add(index)
    }

    val size: Int get() = pages.size

    /** 원본 페이지 수 (삭제 전) */
    val originalCount: Int = pageCount

    fun pageAt(position: Int): Int = pages[position]

    fun order(): List<Int> = ArrayList(pages)

    /** [from] 자리 페이지를 [to] 자리로 옮긴다. */
    fun move(from: Int, to: Int): Boolean {
        if (from == to) return false
        if (from !in pages.indices || to !in pages.indices) return false
        val page = pages.removeAt(from)
        pages.add(to, page)
        return true
    }

    /** [position] 자리 페이지를 뺀다. 뺀 원본 페이지 번호를 돌려준다. */
    fun removeAt(position: Int): Int? {
        if (position !in pages.indices) return null
        return pages.removeAt(position)
    }

    /** 뺐던 페이지를 [position] 자리에 되돌린다. */
    fun insertAt(position: Int, page: Int) {
        val safe = position.coerceIn(0, pages.size)
        pages.add(safe, page)
    }

    /** 처음 순서 그대로인지 */
    fun isUnchanged(): Boolean = pages.withIndex().all { (position, page) -> position == page }

    companion object {
        /** 화면 미리보기·테스트에서 쓰는 이름 */
        fun label(position: Int): String = "p${position + 1}"
    }
}
