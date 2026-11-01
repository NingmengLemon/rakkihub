package me.rerere.rikkahub.data.files

import java.io.File
import java.net.URI
import java.nio.file.Paths

/**
 * 本地附件地址的摘取与归一，附件清理与草稿扫描共用。
 *
 * 存下来的地址是 `file:///data/...` 这种形态，散在消息 JSON、设置与草稿里。清理时要回答的问题是
 * 「这份文件还有没有人引用」，所以两边都换成规范路径再比，避免同一文件的不同写法被当成两份。
 */
internal val FILE_URL_PATTERN = Regex("""file:[^"\\\s]+""")

/** 文本里出现过的本地文件地址（JSON 与草稿文件都可以直接喂进来） */
internal fun extractFileUrls(text: String): Set<String> =
    FILE_URL_PATTERN.findAll(text).mapTo(mutableSetOf()) { it.value }

/**
 * 地址对应的规范路径，解析不了时返回 null。
 *
 * 宁可少收垃圾也不能删错：返回 null 的地址由调用方保守处理，不要当成「没有引用」。
 * 只认带绝对路径的地址：`file://abc.png` 这种只有主机、没有路径的写法会得到空路径，不能拿来比较。
 */
internal fun fileUrlToCanonicalPath(url: String): String? {
    // Paths.get(URI) 能正确处理 Windows 的盘符；Android 上退回按 URI 路径取
    runCatching { Paths.get(URI(url)).toFile().canonicalPath }.getOrNull()?.let { return it }
    return runCatching {
        URI(url).path?.takeIf { it.isNotBlank() }?.let { File(it).canonicalPath }
    }.getOrNull()
}
