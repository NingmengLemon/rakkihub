package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatRequestFactoryContextTest {
    @Test
    fun `context overflow halves the actual window`() {
        assertEquals(5, shrinkContextLimit(configured = 0, visibleCount = 10))
        assertEquals(2, shrinkContextLimit(configured = 5, visibleCount = 5))
        assertEquals(2, shrinkContextLimit(configured = 5, visibleCount = 100))
        assertEquals(2, shrinkContextLimit(configured = 0, visibleCount = 3))
    }

    @Test
    fun `cannot shrink below two messages or a one message window`() {
        assertNull(shrinkContextLimit(configured = 0, visibleCount = 2))
        assertNull(shrinkContextLimit(configured = 1, visibleCount = 100))
        assertNull(shrinkContextLimit(configured = 2, visibleCount = 2))
    }
}
