package me.rerere.rikkahub.ui.components.ai

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopConfirmStateTest {
    @Test
    fun `confirmation starts hidden and closes back to hidden`() {
        val state = StopConfirmState()
        assertFalse(state.visible)
        assertFalse(state.open(Job()).close().visible)
    }

    @Test
    fun `confirmation only matches the job it was opened for`() {
        val first = Job()
        val second = Job()
        val opened = StopConfirmState().open(first)

        assertTrue(opened.visible)
        assertTrue(opened.matches(first))
        assertFalse(opened.matches(second))
        assertFalse(opened.matches(null))
        assertTrue(StopConfirmState().open(null).matches(null))
    }

    @Test
    fun `disabled confirmation stops immediately`() {
        val job = Job()
        assertEquals(
            StopAction.StopNow,
            resolveStopAction(confirmEnabled = false, requestedJob = null, currentJob = job)
        )
    }

    @Test
    fun `disabled confirmation also ignores a stop already in flight`() {
        val job = Job()
        assertEquals(
            StopAction.Ignored,
            resolveStopAction(confirmEnabled = false, requestedJob = job, currentJob = job)
        )
    }

    @Test
    fun `enabled confirmation opens for the current job`() {
        val job = Job()
        val action = resolveStopAction(confirmEnabled = true, requestedJob = null, currentJob = job)

        assertTrue(action is StopAction.Confirm)
        assertEquals(job, (action as StopAction.Confirm).state.targetJob)
    }

    @Test
    fun `a stop already requested for this job is ignored`() {
        val job = Job()
        assertEquals(StopAction.Ignored, resolveStopAction(confirmEnabled = true, requestedJob = job, currentJob = job))
    }

    @Test
    fun `a requested stop does not block the next job`() {
        val first = Job()
        val second = Job()
        assertTrue(
            resolveStopAction(confirmEnabled = true, requestedJob = first, currentJob = second) is StopAction.Confirm
        )
    }
}
