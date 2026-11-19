package me.rerere.ai.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningLevelTest {
    @Test
    fun `budget sentinels keep notset and auto distinct`() {
        assertEquals(ReasoningLevel.NOTSET, ReasoningLevel.fromBudgetTokens(-2))
        assertEquals(ReasoningLevel.AUTO, ReasoningLevel.fromBudgetTokens(-1))
        assertEquals(ReasoningLevel.AUTO, ReasoningLevel.fromBudgetTokens(null))
        assertEquals(ReasoningLevel.OFF, ReasoningLevel.fromBudgetTokens(0))
    }
}
