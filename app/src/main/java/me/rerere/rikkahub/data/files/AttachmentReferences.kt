package me.rerere.rikkahub.data.files

import java.io.File
import java.net.URI
import java.nio.file.Paths

/**
 * 本地附件地址的摘取与归一，附件清理与草稿扫描共用。
 *
 * 地址散在消息 JSON、设置与草稿里。消息库新写的是 `file:upload/...` 相对形态，设置与草稿
 * 仍是绝对 `file:///data/...`。清理时要回答「这份文件还有没有人引用」，所以两边都换成
 * 规范路径再比，避免同一文件的不同写法被当成两份。
 */
internal val FILE_URL_PATTERN = Regex("""file:[^"\\\s]+""")

/** 消息库里保存的 upload 相对前缀；`AttachmentUrlStorage` 写库时用它，这里解析时认它 */
internal const val STORED_UPLOAD_PREFIX = "file:upload/"

/** 文本里出现过的本地文件地址（JSON 与草稿文件都可以直接喂进来） */
internal fun extractFileUrls(text: String): Set<String> =
    FILE_URL_PATTERN.findAll(text).mapTo(mutableSetOf()) { it.value }

/**
 * 地址对应的规范路径，解析不了时返回 null。
 *
 * 宁可少收垃圾也不能删错：返回 null 的地址由调用方保守处理，不要当成「没有引用」。
 * 解析不了的绝对形态（例如 `file://abc.png` 只有主机没有路径）同样返回 null。
 *
 * 消息库里新写的是 `file:upload/...` 相对形态，按 [filesDir] 还原成本安装的路径；
 * 旧数据与设置里的绝对地址走 URI 解析。
 */
internal fun fileUrlToCanonicalPath(url: String, filesDir: File?): String? {
    // 只有消息库保存的相对形态走这里；file:/... 与 file://... 仍是绝对地址
    if (filesDir != null && url.startsWith("$STORED_UPLOAD_PREFIX")) {
        return runCatching {
            File(filesDir, url.removePrefix("file:")).canonicalPath
        }.getOrNull()
    }
    // Paths.get(URI) 能正确处理 Windows 的盘符；Android 上退回按 URI 路径取
    runCatching { Paths.get(URI(url)).toFile().canonicalPath }.getOrNull()?.let { return it }
    return runCatching {
        URI(url).path?.takeIf { it.isNotBlank() }?.let { File(it).canonicalPath }
    }.getOrNull()
}
