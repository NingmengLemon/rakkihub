package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplaySettingTest {
    @Test
    fun `old display settings default to confirming stop`() {
        val setting = JsonInstant.decodeFromString(DisplaySetting.serializer(), "{}")
        assertTrue(setting.confirmStopGeneration)
    }

    @Test
    fun `stop confirmation round trips`() {
        val encoded = JsonInstant.encodeToString(
            DisplaySetting.serializer(),
            DisplaySetting(confirmStopGeneration = false),
        )
        assertFalse(JsonInstant.decodeFromString(DisplaySetting.serializer(), encoded).confirmStopGeneration)
    }

    @Test
    fun `old display settings default to two-row terminal keys`() {
        val setting = JsonInstant.decodeFromString(DisplaySetting.serializer(), "{}")
        assertTrue(setting.terminalTwoRowKeys)
    }

    @Test
    fun `two-row terminal key setting round trips`() {
        val encoded = JsonInstant.encodeToString(
            DisplaySetting.serializer(),
            DisplaySetting(terminalTwoRowKeys = false),
        )
        assertFalse(JsonInstant.decodeFromString(DisplaySetting.serializer(), encoded).terminalTwoRowKeys)
    }
}
