package com.youngsu.scanpdf.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 페이지 순서 계획 — 화면에서 바꾼 순서가 그대로 저장 순서가 되는지 */
class PagePlanTest {

    @Test
    fun `starts in original order`() {
        val plan = PagePlan(4)
        assertEquals(listOf(0, 1, 2, 3), plan.order())
        assertTrue(plan.isUnchanged())
    }

    @Test
    fun `move forward shifts the other pages`() {
        val plan = PagePlan(4)
        assertTrue(plan.move(0, 3))
        assertEquals(listOf(1, 2, 3, 0), plan.order())
        assertFalse(plan.isUnchanged())
    }

    @Test
    fun `move backward shifts the other pages`() {
        val plan = PagePlan(4)
        assertTrue(plan.move(3, 1))
        assertEquals(listOf(0, 3, 1, 2), plan.order())
    }

    @Test
    fun `move to the same place does nothing`() {
        val plan = PagePlan(3)
        assertFalse(plan.move(1, 1))
        assertEquals(listOf(0, 1, 2), plan.order())
    }

    @Test
    fun `remove takes the page out and reports which one`() {
        val plan = PagePlan(4)
        assertEquals(2, plan.removeAt(2))
        assertEquals(listOf(0, 1, 3), plan.order())
        assertEquals(3, plan.size)
        assertEquals(4, plan.originalCount)
    }

    @Test
    fun `removed page can be put back`() {
        val plan = PagePlan(3)
        val removed = plan.removeAt(1)
        plan.insertAt(1, removed!!)
        assertEquals(listOf(0, 1, 2), plan.order())
        assertTrue(plan.isUnchanged())
    }

    @Test
    fun `split order follows the visible order`() {
        val plan = PagePlan(5)
        plan.move(4, 0) // 4 0 1 2 3
        plan.removeAt(3) // 4 0 1 3
        val chosen = setOf(0, 3)
        assertEquals(listOf(0, 3), plan.order().filter { it in chosen })
    }

    @Test
    fun `out of range operations are ignored`() {
        val plan = PagePlan(2)
        assertFalse(plan.move(0, 5))
        assertEquals(null, plan.removeAt(9))
        assertEquals(listOf(0, 1), plan.order())
    }
}
