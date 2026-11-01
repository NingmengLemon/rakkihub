package me.rerere.rikkahub.data.sync.official

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方兼容导出对 `settings.json` 的处理边界。
 *
 * 官方恢复用的是 [JsonInstant]，它开着 `ignoreUnknownKeys`，所以我们往 `Settings` 里加
 * **字段**时，官方读到的多余字段会被直接忽略，不会导致恢复失败。当初 `launchCount` 就是
 * 这么混过去的：它不在 `Settings` 里，官方也照读不误。
 *
 * 这条测试把该行为钉住。一旦有人关掉 `ignoreUnknownKeys`，这里会失败，那时官方兼容导出
 * 就必须连新增字段也一起裁。
 *
 * 边界：`ignoreUnknownKeys` 只覆盖「不认识的 key」。改已有字段的含义、或者让它变成官方
 * 不认识的类型，它救不了；**往枚举里加值**同样救不了，未知枚举值会直接抛
 * `SerializationException`，所以 `WORKSPACES` 这种新枚举值必须在导出前从 settings 里
 * 去掉（`BackupManager.createBackup` 的 OFFICIAL 分支）。
 */
class OfficialSettingsCompatibilityTest {

    @Test
    fun `decoder tolerates fields it does not know`() {
        val settings = Settings()
        val baseline = JsonInstant.encodeToJsonElement(Settings.serializer(), settings)

        val withExtra = JsonObject(baseline.jsonObject + ("fieldOnlyWeKnow" to JsonPrimitive("value")))
        val decoded = JsonInstant.decodeFromString(Settings.serializer(), withExtra.toString())

        assertEquals(
            baseline,
            JsonInstant.encodeToJsonElement(Settings.serializer(), decoded),
        )
    }
}
