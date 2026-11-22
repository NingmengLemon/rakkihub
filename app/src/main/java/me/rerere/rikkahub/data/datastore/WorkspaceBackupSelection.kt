package me.rerere.rikkahub.data.datastore

/**
 * 切换一个工作区的导出选择，返回 null 表示「全部」。
 *
 * 显式列表覆盖了当前全部工作区时归一成 null，避免以后新建的工作区被一份旧白名单
 * 悄悄排除在外；一个都不选时保留空列表，表示这次不导出工作区文件。
 */
internal fun toggleWorkspaceSelection(
    current: List<String>?,
    allIds: List<String>,
    id: String,
    checked: Boolean,
): List<String>? {
    val base = current ?: allIds
    val next = if (checked) (base + id).distinct() else base - id
    return next.takeUnless { it.containsAll(allIds) }
}

/** 显式选择与当前现存的工作区求交；null 表示全部。 */
internal fun effectiveWorkspaceIds(
    workspaceIds: List<String>?,
    allIds: List<String>,
): Set<String> = workspaceIds?.toSet()?.intersect(allIds.toSet()) ?: allIds.toSet()
