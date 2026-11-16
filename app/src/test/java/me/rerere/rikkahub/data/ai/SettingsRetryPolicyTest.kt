package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.datastore.NetworkSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 自动重试是默认关闭的可选项；开启后才读取次数并限制在允许范围内。 */
class SettingsRetryPolicyTest {
    private fun settings(retries: Int, enabled: Boolean = false) = Settings.dummy().copy(
        networkSetting = NetworkSetting(
            enableAutoRetry = enabled,
            maxRetries = retries,
        )
    )

    @Test
    fun `automatic retry is off by default`() {
        assertEquals(0, settings(5).retryPolicy().maxRetries)
        assertEquals(0, settings(5, enabled = false).retryPolicy().maxRetries)
        assertEquals(0, settings(0, enabled = true).retryPolicy().maxRetries)
    }

    @Test
    fun `enabled retry uses the configured count`() {
        assertEquals(5, settings(5, enabled = true).retryPolicy().maxRetries)
        assertEquals(NetworkSetting.MAX_RETRIES, settings(99, enabled = true).retryPolicy().maxRetries)
    }

    @Test
    fun `old settings JSON without the field defaults to off`() {
        val decoded = JsonInstant.decodeFromString<NetworkSetting>("{}")

        assertFalse(decoded.enableAutoRetry)
        assertEquals(3, decoded.maxRetries)
    }

    @Test
    fun `explicitly enabled old settings JSON stays enabled`() {
        val decoded = JsonInstant.decodeFromString<NetworkSetting>(
            """{"enableAutoRetry":true}"""
        )

        assertEquals(true, decoded.enableAutoRetry)
        assertEquals(3, decoded.maxRetries)
    }
}
