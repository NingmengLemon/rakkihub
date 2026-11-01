package me.rerere.rikkahub.data.files

/**
 * 消息里附件地址的落盘形态。
 *
 * 内存与 UI 消费方始终用绝对 `file://`：`file:///data/user/0/<包名>/files/upload/...`。
 * 消息节点落库时，只把 JSON 里 `url` 字段的值换成 `file:upload/...`，读取时换回来。
 * 这样消息库与随它导出的备份不再绑定安装路径：包名或用户号变了，相对地址仍能解析。
 *
 * 只改 `url` 字段与 `upload/`：设置里的头像不在消息库，草稿不随备份走，两者都维持原样。
 * 消息正文里出现同名前缀的文本不会被改动。旧数据里本安装的两种绝对形态
 * （`/data/user/<用户号>/` 与旧的 `/data/data/`）原样保留、继续有效，下一次保存时转成相对。
 *
 * [filesDirUri] 不带结尾斜杠，例如 `file:///data/user/0/<包名>/files`。
 */
internal object AttachmentUrlStorage {

    private const val URL_KEY_PREFIX = "\"url\":\""
    private const val ABSOLUTE_SUFFIX = "/upload/"

    /** `/data/user/<用户号>/<包名>/files` 里取包名，用旧形态 `/data/data/<包名>/files` 拼回 */
    private val USER_DIR = Regex("""^file:///data/user/\d+/([^/]+)/files$""")

    /** 落库前：`url` 字段里的本安装绝对 upload 地址转成相对形态 */
    fun toStoredJson(json: String, filesDirUri: String): String {
        val prefixes = listOfNotNull(filesDirUri, legacyFilesDirUri(filesDirUri))
        return prefixes.fold(json) { acc, prefix ->
            acc.replace(
                "$URL_KEY_PREFIX$prefix$ABSOLUTE_SUFFIX",
                "$URL_KEY_PREFIX$STORED_UPLOAD_PREFIX",
            )
        }
    }

    /** 读取后：`url` 字段里的相对形态还原成本安装的绝对地址 */
    fun toMemoryJson(json: String, filesDirUri: String): String =
        json.replace(
            "$URL_KEY_PREFIX$STORED_UPLOAD_PREFIX",
            "$URL_KEY_PREFIX$filesDirUri$ABSOLUTE_SUFFIX",
        )

    private fun legacyFilesDirUri(filesDirUri: String): String? =
        USER_DIR.find(filesDirUri)?.let { "file:///data/data/${it.groupValues[1]}/files" }
}
