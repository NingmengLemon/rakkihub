package me.rerere.rikkahub.data.files

import java.util.concurrent.ConcurrentHashMap

/**
 * 只存在于内存输入框里的附件引用。
 *
 * 草稿会覆盖停下来的输入，但编辑历史消息期间输入框被编辑内容占用，草稿不写它；编辑结束的时间点
 * 也可能还没到。附件清理必须能看见这部分，否则会把刚加进来、还没发出去的附件当成没人用删掉。
 *
 * 进程内共享：进程结束，输入框也没了，这些引用自然失效，不需要持久化。
 */
class LiveAttachmentReferences {
    private val entries = ConcurrentHashMap<Any, Set<String>>()

    /** [owner] 当前的引用集合；空集合等于没有引用 */
    fun put(owner: Any, urls: Set<String>) {
        if (urls.isEmpty()) entries.remove(owner) else entries[owner] = urls
    }

    fun remove(owner: Any) {
        entries.remove(owner)
    }

    fun snapshot(): Set<String> = buildSet {
        entries.values.forEach { addAll(it) }
    }
}
