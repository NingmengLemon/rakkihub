package me.rerere.rikkahub.data.files

import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.repository.FilesRepository
import java.io.File

/**
 * 把消息里的附件地址解析到 `managed_files` 行。
 *
 * 只认本安装 filesDir 下的文件；别的安装路径、目录外文件和没有登记的上传返回 null，由 OCR
 * 回退到旧 metadata/缓存路径。地址可能是 `file:upload/...` 相对形态，也可能是绝对 `file://`。
 */
internal object FileDerivationResolver {
    suspend fun resolveAll(
        urls: Collection<String>,
        filesDir: File,
        filesRepository: FilesRepository,
    ): Map<String, ManagedFileEntity> {
        val pathsByUrl = LinkedHashMap<String, String>()
        for (url in urls.distinct()) {
            val canonicalPath = fileUrlToCanonicalPath(url, filesDir) ?: continue
            val relativePath = FileUtils.getRelativePathInFilesDir(filesDir, File(canonicalPath)) ?: continue
            pathsByUrl[url] = relativePath
        }
        if (pathsByUrl.isEmpty()) return emptyMap()
        val byPath = filesRepository.getByPaths(pathsByUrl.values)
            .associateBy { it.relativePath }
        return pathsByUrl.mapNotNull { (url, path) ->
            byPath[path]?.let { url to it }
        }.toMap()
    }
}
