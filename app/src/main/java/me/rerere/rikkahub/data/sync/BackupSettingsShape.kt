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
 *
 * 报错信息用英文，与 `BackupManager` 里其它异常一致，它们会经同一处 toast 露给用户。
 */
internal object BackupSettingsShape {

    /** 形状有问题时返回第一处问题，正常返回 null。 */
    fun findProblem(raw: String): String? {
        val root = runCatching { JsonInstant.parseToJsonElement(raw) }.getOrNull()
            ?: return "not valid JSON"
        val obj = root as? JsonObject ?: return "root is not a JSON object"

        obj["init"]?.let { value ->
            val isBoolean = value is JsonPrimitive && !value.isString &&
                value.content in setOf("true", "false")
            if (!isBoolean) return "init is not a boolean"
        }
        checkString(obj, "themeId")?.let { return it }
        checkString(obj, "chatModelId")?.let { return it }
        checkString(obj, "assistantId")?.let { return it }
        checkObject(obj, "displaySetting")?.let { return it }
        checkIdList(obj, "assistants")?.let { return it }
        checkIdList(obj, "providers")?.let { return it }
        return null
    }

    private fun checkString(obj: JsonObject, key: String): String? {
        val value = obj[key] ?: return null
        return if (value is JsonPrimitive && value.isString) null else "$key is not a string"
    }

    private fun checkObject(obj: JsonObject, key: String): String? {
        val value = obj[key] ?: return "missing $key"
        if (value !is JsonObject) return "$key is not an object"
        // 空对象能通过类型检查，但把它写回去等于把整块设置清成默认值。真实备份里这个对象
        // 一定有字段：官方与本应用都开着 encodeDefaults，编码时会把每个字段写出来。
        if (value.isEmpty()) return "$key is empty"
        return null
    }

    private fun checkIdList(obj: JsonObject, key: String): String? {
        val value = obj[key] ?: return "missing $key"
        if (value !is JsonArray) return "$key is not an array"
        value.forEachIndexed { index, element ->
            if (element !is JsonObject) return "$key[$index] is not an object"
            val id = element["id"]
            if (id !is JsonPrimitive || !id.isString) return "$key[$index].id is not a string"
        }
        return null
    }
}
