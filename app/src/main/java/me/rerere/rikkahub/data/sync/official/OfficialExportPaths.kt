package me.rerere.rikkahub.data.sync.official

/**
 * 官方兼容导出要换掉的附件前缀。
 *
 * 库与 settings 里的附件引用是绝对 `file://` 路径，包名段指向本安装；官方客户端恢复时只把
 * 附件搬进它自己的 files 目录，不会改路径，所以导出时要换成官方安装的位置。
 *
 * 两种形态都要覆盖：`/data/user/<用户号>/<包名>/files/` 与 `/data/data/<包名>/files/`。
 * 后者是更早的写法，导入侧的重写器也认它，库里可能留着。
 */
internal object OfficialExportPaths {

    private val USER_NUMBER = Regex("""^file:///data/user/(\d+)/""")

    /** 路径里的 Android 用户号；工作资料是 10 起的数字，取不到时返回 null。 */
    fun userNumber(filesDirUri: String): String? =
        USER_NUMBER.find(filesDirUri)?.groupValues?.get(1)

    /**
     * `LIKE` 模式里的字面量转义。
     *
     * 前缀会被拼进 `LIKE ? ESCAPE '\'` 的模式里，包名里的 `_` 与 `%` 不转义会多匹配别的行。
     */
    fun escapeLikePattern(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** 官方安装的 files 目录前缀：只有包名段不同，用户号沿用导出侧。 */
    fun officialFilesPrefix(
        filesDirUri: String,
        packageName: String,
        officialPackage: String,
    ): String = filesDirUri.replace("/$packageName/", "/$officialPackage/")

    /**
     * 库里要替换的前缀对：第一个是现状，第二个是官方安装里对应的位置。
     *
     * 除了本安装实际的 files 目录，还补两种规范形态，同一个源只出现一次。消息库里新写的
     * `"url":"file:upload/...` 相对形态也要换成官方安装的绝对位置，这一对无论包名是否相同
     * 都带上：官方客户端只认绝对路径。两个包名相同时只返回这一对。
     */
    fun attachmentRewrites(
        filesDirUri: String,
        packageName: String,
        officialPackage: String,
    ): List<Pair<String, String>> {
        val officialFilesDirUri = officialFilesPrefix(filesDirUri, packageName, officialPackage)
        val relative = "\"url\":\"file:upload/" to "\"url\":\"${officialFilesDirUri}upload/"
        if (packageName == officialPackage) return listOf(relative)
        val userNumber = userNumber(filesDirUri)
        val sources = buildList {
            add(filesDirUri)
            if (userNumber != null) {
                add("file:///data/user/$userNumber/$packageName/files/")
                add("file:///data/data/$packageName/files/")
            }
        }.distinct()
        return sources.map { source ->
            source to source.replace("/$packageName/", "/$officialPackage/")
        } + relative
    }
}
