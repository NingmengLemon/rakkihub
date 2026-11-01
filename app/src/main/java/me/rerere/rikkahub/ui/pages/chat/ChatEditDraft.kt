package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.files.ChatDraftStore

/**
 * 编辑历史消息期间暂存的草稿。
 *
 * 编辑要占用输入框，所以进编辑前把输入框里的内容放在这里，编辑结束后再放回去。单独成一个类是为了
 * 能脱离 ViewModel 验：两次编辑挨在一起、编辑刚结束就离开界面时，暂存的那份都不能丢。
 */
internal class ChatEditDraft {
    private var parked: ChatDraftStore.Draft? = null

    /**
     * 开始编辑，[current] 是此刻输入框里的内容。
     *
     * 输入框里有内容就由它取代之前暂存的那份：上一段编辑结束后用户已经自己写了新的东西。
     * 输入框空着时保留还没放回去的那份，这样两次编辑挨在一起也不会把它丢掉。
     */
    fun beginEditing(current: ChatDraftStore.Draft) {
        if (!current.isEmpty) parked = current
    }

    /**
     * 草稿刚从磁盘恢复出来，输入框却被编辑占着：先存着，编辑结束后再放回，别覆盖编辑内容。
     * 已经有暂存内容时不覆盖，那份才是用户眼下的输入。
     */
    fun restoreAfterEditing(draft: ChatDraftStore.Draft) {
        if (parked == null) parked = draft
    }

    /**
     * 编辑结束，[current] 是此刻输入框里的内容，返回要放回输入框的草稿。
     *
     * 输入框不是空的说明用户已经自己写了新内容，以新的为准，暂存的那份作废。
     */
    fun finishEditing(current: ChatDraftStore.Draft): ChatDraftStore.Draft? {
        val parked = parked ?: return null
        this.parked = null
        return parked.takeIf { current.isEmpty }
    }

    /** 编辑中离开界面：磁盘上该留这一份 */
    fun parkedDraft(): ChatDraftStore.Draft? = parked

    /** 编辑刚结束、还没放回去就离开界面：别拿空输入把暂存的那份删掉 */
    fun draftToPersistOnExit(current: ChatDraftStore.Draft): ChatDraftStore.Draft =
        if (current.isEmpty) parked ?: current else current
}
