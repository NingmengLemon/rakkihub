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
    fun `an empty object is rejected`() {
        assertEquals("缺少 displaySetting", BackupSettingsShape.findProblem("{}"))
    }

    @Test
    fun `missing assistants is rejected`() {
        val json = """{"providers":[],"displaySetting":{}}"""
        assertEquals("缺少 assistants", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `assistants without an id are rejected`() {
        val json = """{"assistants":[{"name":"a"},{"id":"b"}],"providers":[],"displaySetting":{}}"""
        assertEquals("assistants[0].id 不是字符串", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `providers of the wrong type are rejected`() {
        val json = """{"assistants":[],"providers":"none","displaySetting":{}}"""
        assertEquals("providers 不是数组", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `displaySetting of the wrong type is rejected`() {
        val json = """{"assistants":[],"providers":[],"displaySetting":[]}"""
        assertEquals("displaySetting 不是对象", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `init of the wrong type is rejected`() {
        val json = """{"init":"yes","assistants":[],"providers":[],"displaySetting":{}}"""
        assertEquals("init 不是布尔值", BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `fields the validator does not know are ignored`() {
        val json = """{"assistants":[],"providers":[],"displaySetting":{},"someNewField":42}"""
        assertNull(BackupSettingsShape.findProblem(json))
    }

    @Test
    fun `broken json is rejected`() {
        assertEquals("不是合法的 JSON", BackupSettingsShape.findProblem("""{"assistants":"""))
    }

    @Test
    fun `a non-object root is rejected`() {
        assertEquals("根节点不是 JSON 对象", BackupSettingsShape.findProblem("[1,2,3]"))
    }
}
