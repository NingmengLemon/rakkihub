package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatRequestFactoryContextTest {
    @Test
    fun `context overflow halves the visible window`() {
        assertEquals(5, shrinkContextLimit(configured = 0, visibleCount = 10))
        assertEquals(2, shrinkContextLimit(configured = 5, visibleCount = 5))
    }

    @Test
    fun `cannot shrink below two messages or configured limit`() {
        assertNull(shrinkContextLimit(configured = 0, visibleCount = 2))
        assertNull(shrinkContextLimit(configured = 1, visibleCount = 100))
        assertNull(shrinkContextLimit(configured = 5, visibleCount = 100))
    }
}
