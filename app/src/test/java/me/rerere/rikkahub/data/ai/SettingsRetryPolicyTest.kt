package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.datastore.NetworkSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRetryPolicyTest {
    private fun settings(retries: Int, enabled: Boolean = true) = Settings.dummy().copy(
        networkSetting = NetworkSetting(
            enableAutoRetry = enabled,
            maxRetries = retries,
        )
    )

    @Test
    fun `enabled uses the configured retry count`() {
        assertEquals(5, settings(5).retryPolicy().maxRetries)
    }

    @Test
    fun `disabled disables retries`() {
        assertEquals(0, settings(5, enabled = false).retryPolicy().maxRetries)
    }

    @Test
    fun `zero disables retries`() {
        assertEquals(0, settings(0).retryPolicy().maxRetries)
    }

    @Test
    fun `out of range is clamped`() {
        assertEquals(NetworkSetting.MAX_RETRIES, settings(99).retryPolicy().maxRetries)
        assertEquals(0, settings(-1).retryPolicy().maxRetries)
    }

    @Test
    fun `default is three retries`() {
        assertEquals(3, Settings.dummy().retryPolicy().maxRetries)
    }

    @Test
    fun `old settings JSON without the field uses the default`() {
        val decoded = JsonInstant.decodeFromString<NetworkSetting>(
            """{"enableAutoRetry":true}"""
        )

        assertEquals(3, decoded.maxRetries)
    }
}
