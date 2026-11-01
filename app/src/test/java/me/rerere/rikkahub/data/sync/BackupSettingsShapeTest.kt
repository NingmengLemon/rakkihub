package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * settings.json 形状校验的判据。
 *
 * 这里守的是「解码器太宽容」这件事：`ignoreUnknownKeys` 加上全字段默认值，会让空对象也解码
 * 成功。所以测试要钉住两件事：真正由本应用写出的 settings 一定通过；不是设置文件的东西一定
 * 被挡下，而且报出的位置够具体。
 */
class BackupSettingsShapeTest {

    @Test
    fun `settings written by the app pass`() {
        val encoded = JsonInstant.encodeToString(Settings.serializer(), Settings())
        assertNull(BackupSettingsShape.findProblem(encoded))
    }

    @Test
    fun `official shaped settings with extra fields pass`() {
        // 官方形状：顶层与数组元素都带着不认识的字段，校验器不该因此翻脸
        val json = """
            {
              "init": false,
              "assistants": [{"id": "a1", "name": "n", "chatModelId": "m1", "tags": []}],
              "providers": [{"id": "p1", "type": "openai", "enabled": true, "models": []}],
              "displaySetting": {"dynamicColor": true, "themeId": "default"},
              "someFutureField": 42,
              "anotherOne": {"nested": [1, 2, 3]}
            }
        """.trimIndent()
        assertNull(BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `an empty object is rejected`() {
        assertEquals("missing displaySetting", BackupSettingsShape.findProblem("{}"))
    }

    @Test
    fun `missing assistants is rejected`() {
        val json = """{"providers":[],"displaySetting":{"a":1}}"""
        assertEquals("missing assistants", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `assistants without an id are rejected`() {
        val json = """{"assistants":[{"name":"a"},{"id":"b"}],"providers":[],"displaySetting":{"a":1}}"""
        assertEquals("assistants[0].id is not a string", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `providers of the wrong type are rejected`() {
        val json = """{"assistants":[],"providers":"none","displaySetting":{"a":1}}"""
        assertEquals("providers is not an array", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `provider ids must be strings`() {
        val json = """{"assistants":[],"providers":[{"id":7}],"displaySetting":{"a":1}}"""
        assertEquals("providers[0].id is not a string", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `displaySetting of the wrong type is rejected`() {
        val json = """{"assistants":[],"providers":[],"displaySetting":[]}"""
        assertEquals("displaySetting is not an object", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `an empty displaySetting is rejected`() {
        // 类型对但内容空，写回去等于把整块显示设置清成默认值
        val json = """{"assistants":[],"providers":[],"displaySetting":{}}"""
        assertEquals("displaySetting is empty", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `init of the wrong type is rejected`() {
        val json = """{"init":"yes","assistants":[],"providers":[],"displaySetting":{"a":1}}"""
        assertEquals("init is not a boolean", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `id fields must be strings when present`() {
        val theme = """{"themeId":1,"assistants":[],"providers":[],"displaySetting":{"a":1}}"""
        assertEquals("themeId is not a string", BackupSettingsShape.findProblem(theme))
        val model = """{"chatModelId":false,"assistants":[],"providers":[],"displaySetting":{"a":1}}"""
        assertEquals("chatModelId is not a string", BackupSettingsShape.findProblem(model))
        val assistant = """{"assistantId":[],"assistants":[],"providers":[],"displaySetting":{"a":1}}"""
        assertEquals("assistantId is not a string", BackupSettingsShape.findProblem(assistant))
    }

    @Test
    fun `fields the validator does not know are ignored`() {
        val json = """{"assistants":[],"providers":[],"displaySetting":{"a":1},"someNewField":42}"""
        assertNull(BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `broken json is rejected`() {
        assertEquals("not valid JSON", BackupSettingsShape.findProblem("""{"assistants":"""))
    }

    @Test
    fun `a non-object root is rejected`() {
        assertEquals("root is not a JSON object", BackupSettingsShape.findProblem("[1,2,3]"))
    }
}
