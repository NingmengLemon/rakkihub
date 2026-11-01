package me.rerere.rikkahub.data.files

/**
 * 消息里附件地址的落盘形态。
 *
 * 内存与所有消费方始终用绝对 `file://`：`file:///data/user/0/<包名>/files/upload/...`。
 * 消息节点落库时，把本安装 files 目录下的 upload 地址换成 `file:upload/...`，读取时换回来，
 * 这样消息库与随它导出的备份不再绑定安装路径：包名或用户号变了，相对地址仍能解析到当前安装。
 *
 * 只转换 `upload/`：设置里的头像不在消息库，草稿不随备份走，两者各自维持原样。旧数据里的
 * 绝对地址原样保留、继续有效，下一次保存自然转成相对形态。替换带 JSON 值开头的引号，
 * 避免把消息正文里出现的同名文本也一起换掉。
 */
internal object AttachmentUrlStorage {

    private const val STORED_PREFIX = "file:upload/"
    private const val ABSOLUTE_SUFFIX = "/upload/"

    /** 落库前：本安装的绝对 upload 地址转成相对形态 */
    fun toStored(json: String, filesDirUri: String): String =
        json.replace("\"$filesDirUri$ABSOLUTE_SUFFIX", "\"$STORED_PREFIX")

    /** 读取后：相对形态还原成本安装的绝对地址 */
    fun toMemory(json: String, filesDirUri: String): String =
        json.replace("\"$STORED_PREFIX", "\"$filesDirUri$ABSOLUTE_SUFFIX")
}
