package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `fingerprinted producer ignores metadata for a managed file`() {
        val choice = chooseOcrText(
            fileId = 7,
            derivationText = null,
            metadataText = "old",
            useMetadata = false,
        )

        assertNull(choice.text)
        assertNull(choice.backfillFileId)
    }

    @Test
    fun `cache key includes the producer`() {
        val first = ocrCacheKey("ocr:v1:aaaa", "file:upload/H1.png")
        val second = ocrCacheKey("ocr:v1:bbbb", "file:upload/H1.png")

        assertTrue(first != second)
        assertTrue(first.contains("ocr:v1:aaaa"))
    }

    @Test
    fun `blank values count as missing so OCR can run`() {
        val choice = chooseOcrText(fileId = 7, derivationText = " ", metadataText = "")

        assertNull(choice.text)
        assertNull(choice.backfillFileId)
    }
}
