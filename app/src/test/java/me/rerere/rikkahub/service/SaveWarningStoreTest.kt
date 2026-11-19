package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class SaveWarningStoreTest {
    private val store = SaveWarningStore()

    @Test
    fun `report records warning per conversation`() {
        val first = Uuid.random()
        val second = Uuid.random()

        store.report(first, MessageSaveWarning.WRITE_FAILED)
        store.report(second, MessageSaveWarning.TOO_LARGE)

        assertEquals(MessageSaveWarning.WRITE_FAILED, store.warnings.value[first])
        assertEquals(MessageSaveWarning.TOO_LARGE, store.warnings.value[second])
    }

    @Test
    fun `later failure replaces the earlier reason`() {
        val id = Uuid.random()

        store.report(id, MessageSaveWarning.WRITE_FAILED)
        store.report(id, MessageSaveWarning.TOO_LARGE)

        assertEquals(MessageSaveWarning.TOO_LARGE, store.warnings.value[id])
    }

    @Test
    fun `clear removes only the given conversation`() {
        val first = Uuid.random()
        val second = Uuid.random()
        store.report(first, MessageSaveWarning.WRITE_FAILED)
        store.report(second, MessageSaveWarning.TOO_LARGE)

        store.clear(first)

        assertNull(store.warnings.value[first])
        assertEquals(MessageSaveWarning.TOO_LARGE, store.warnings.value[second])
    }

    @Test
    fun `clear on unknown conversation is a no-op`() {
        val id = Uuid.random()
        store.report(id, MessageSaveWarning.WRITE_FAILED)

        store.clear(Uuid.random())

        assertEquals(MessageSaveWarning.WRITE_FAILED, store.warnings.value[id])
    }
}
