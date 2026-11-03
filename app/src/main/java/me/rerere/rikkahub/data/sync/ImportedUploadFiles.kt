package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.data.files.ContentAddressedFiles
import me.rerere.rikkahub.data.files.FileUtils
import java.io.File
import java.io.InputStream

/**
 * 导入时把归档里的附件写到内容寻址的文件名上，顺带把同内容的重复收成一份。
 *
 * 官方客户端给每次上传起一个随机 UUID 名，同一张图发几次就是几个文件；按内容命名之后同样的
 * 内容落到同一个文件，名字也与本应用新写的一致（C32）。扩展名沿用归档里的文件名：导入侧拿不到
 * 可靠的 MIME，用文件名换出来是唯一稳定的选择，同一份内容因此要求扩展名也一致才会合并。
 *
 * 写入本身走 C32 的 [ContentAddressedFiles] 与 [FileUtils.buildContentFileName]，不另写一套
 * 指纹与临时文件：两边的命名一旦漂移，归档里的名字与内存里的引用就对不上。
 *
 * 只认 `upload/`。`skills/`、`fonts/`、`media_creation/` 是本仓库自己的目录，归档里的名字来自
 * 本应用的随机命名，没有按内容去重的历史包袱，改名反而要连带改一批工具结果里的引用。
 */
internal object ImportedUploadFiles {

    /**
     * 引用有两种落盘形态：消息库里的相对形态 `file:upload/<名字>`（C36）与绝对形态
     * `file:///data/.../files/upload/<名字>`，设置里的头像、背景也是后者。两种都含
     * `upload/<名字>` 这一段，所以两个锚点各取一段前缀，再按名字查表替换；正文里出现的裸
     * `upload/<名字>` 不会被改，避免误伤消息内容。
     */
    private val REFERENCE = Regex("""(files/upload/|file:upload/)([^"'\s?#\\<>]+)""")

    /** 文本里带前缀的附件引用换成新名字；不在 [renames] 里的名字原样保留。 */
    fun rewrite(text: String, renames: Map<String, String>): String {
        if (renames.isEmpty() || "upload/" !in text) return text
        return REFERENCE.replace(text) { match ->
            val replacement = renames[match.groupValues[2]]
            if (replacement == null) match.value else match.groupValues[1] + replacement
        }
    }

    /** 把 [input] 写到 [directory] 下以内容命名的文件上，返回实际文件名。 */
    fun write(directory: File, archiveName: String, input: InputStream): String =
        ContentAddressedFiles.write(directory, archiveName, mimeType = null) { output ->
            input.copyTo(output)
        }.name

    /** `<sha256>.<扩展名>`，与 C32 的新写入同名；没有扩展名时是 `.bin`。 */
    fun contentName(hash: String, archiveName: String): String =
        FileUtils.buildContentFileName(hash, archiveName, mimeType = null)
}
