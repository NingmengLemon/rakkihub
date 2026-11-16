package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrTextChoiceTest {
    @Test
    fun `derivation wins over metadata and needs no backfill`() {
        val choice = chooseOcrText(fileId = 7, derivationText = "stored", metadataText = "old")

        assertEquals("stored", choice.text)
        assertNull(choice.backfillFileId)
    }

    @Test
    fun `metadata is used and backfilled when a managed file exists`() {
        val choice = chooseOcrText(fileId = 7, derivationText = null, metadataText = "old")

        assertEquals("old", choice.text)
        assertEquals(7L, choice.backfillFileId)
    }

    @Test
    fun `metadata without a managed file does not backfill`() {
        val choice = chooseOcrText(fileId = null, derivationText = null, metadataText = "old")

        assertEquals("old", choice.text)
        assertNull(choice.backfillFileId)
    }

    @Test
    fun `blank values count as missing so OCR can run`() {
        val choice = chooseOcrText(fileId = 7, derivationText = " ", metadataText = "")

        assertNull(choice.text)
        assertNull(choice.backfillFileId)
    }
}
