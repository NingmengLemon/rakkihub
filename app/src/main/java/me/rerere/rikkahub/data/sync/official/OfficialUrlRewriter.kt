package me.rerere.rikkahub.data.sync.official

/**
 * 把绝对文件 URI 里的安装路径换成另一个安装的。
 *
 * 附件引用存成 `file:///data/user/0/<包名>/files/upload/...`，包名是路径的一部分，换一个
 * 包名安装之后这些路径既不存在也无权访问，消息里的图片、文档、头像会全部失配。两个方向都用
 * 这一套：导入时换成当前安装（官方备份、以及我们别的包名的备份），导出官方兼容备份时换成
 * 官方安装。
 *
 * 只认「绝对 files 目录」这一种形状：官方包名、本 fork 的旧包名都可能出现，所以不硬编码
 * 任何包名。相对路径、`content://`、网络 URL 都原样返回。
 *
 * 不依赖 Android，可以直接跑 JVM 测试。
 */
internal object OfficialUrlRewriter {

    /** `file:///data/user/0/<包名>/files/`，包名段不限定 */
    private val absoluteFilesUri = Regex("""file:///data/(?:user/\d+|data)/[^/]+/files/""")

    /**
     * @param filesUriPrefix 目标安装的 files 目录 URI 前缀，形如 `file:///data/user/0/<包>/files/`
     */
    fun rewrite(url: String, filesUriPrefix: String): String =
        if (url.startsWith("file://") && absoluteFilesUri.containsMatchIn(url)) {
            absoluteFilesUri.replace(url, filesUriPrefix)
        } else {
            url
        }

    /**
     * 一段 JSON 里可能嵌着多条引用，逐条替换。
     *
     * 对整段文本做替换而不是先解析 JSON：官方把这些字段当不透明的 JSON 存，解析再序列化会
     * 丢掉我们不认识的字段，也会改掉键的先后顺序。
     */
    fun rewriteEmbedded(json: String, filesUriPrefix: String): String =
        if (!json.contains("file://")) json else absoluteFilesUri.replace(json, filesUriPrefix)
}
