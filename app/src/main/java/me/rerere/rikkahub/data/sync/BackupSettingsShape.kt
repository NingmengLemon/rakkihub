package me.rerere.rikkahub.data.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant

/**
 * 备份里 settings.json 的形状校验。
 *
 * 解码器开着 `ignoreUnknownKeys`，每个字段又都有默认值，所以一份根本不是设置的文件（空对象、
 * 别家的 JSON、写坏了一半的文件）也能解码成功，得到一份全是默认值的 Settings，然后静默覆盖
 * 用户的配置。这里在解码之前先看关键项在不在、类型对不对，把这种情况挡在 publish 之前。
 *
 * 只校验关键项与已出现字段的类型，不校验完整 schema：旧版本的备份缺少新字段是正常的，缺省由
 * 解码器的默认值补齐。
 */
internal object BackupSettingsShape {

    /** 形状有问题时返回第一处问题，正常返回 null。 */
    fun findProblem(raw: String): String? {
        val root = runCatching { JsonInstant.parseToJsonElement(raw) }.getOrNull()
            ?: return "不是合法的 JSON"
        val obj = root as? JsonObject ?: return "根节点不是 JSON 对象"

        obj["init"]?.let { value ->
            val isBoolean = value is JsonPrimitive && !value.isString &&
                value.content in setOf("true", "false")
            if (!isBoolean) return "init 不是布尔值"
        }
        checkString(obj, "themeId")?.let { return it }
        checkString(obj, "chatModelId")?.let { return it }
        checkString(obj, "assistantId")?.let { return it }
        checkObject(obj, "displaySetting", required = true)?.let { return it }
        checkIdList(obj, "assistants", required = true)?.let { return it }
        checkIdList(obj, "providers", required = true)?.let { return it }
        return null
    }

    private fun checkString(obj: JsonObject, key: String): String? {
        val value = obj[key] ?: return null
        return if (value is JsonPrimitive && value.isString) null else "$key 不是字符串"
    }

    private fun checkObject(obj: JsonObject, key: String, required: Boolean): String? {
        val value = obj[key]
        if (value == null) return if (required) "缺少 $key" else null
        return if (value is JsonObject) null else "$key 不是对象"
    }

    private fun checkIdList(obj: JsonObject, key: String, required: Boolean): String? {
        val value = obj[key]
        if (value == null) return if (required) "缺少 $key" else null
        if (value !is JsonArray) return "$key 不是数组"
        value.forEachIndexed { index, element ->
            if (element !is JsonObject) return "$key[$index] 不是对象"
            val id = element["id"]
            if (id !is JsonPrimitive || !id.isString) return "$key[$index].id 不是字符串"
        }
        return null
    }
}
