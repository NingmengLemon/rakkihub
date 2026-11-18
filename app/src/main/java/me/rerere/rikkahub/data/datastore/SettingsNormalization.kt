package me.rerere.rikkahub.data.datastore

/** 读盘后和每次修改后都要过一遍，保证内存里的设置始终是规整的。 */
internal fun Settings.normalized(): Settings =
    withBuiltInDefaults().withoutInvalidReferences().withValuesInRange()

// 补齐缺失的内置助手/TTS，内置提供商的描述信息始终以代码里的为准。
// 提供商不再自动补回：删掉的内置提供商保持删除状态，否则「删除」对预置项无效
internal fun Settings.withBuiltInDefaults(): Settings {
    val providers = this.providers
        // 赞助商预置项已下线，顺带清掉老设置里残留的条目
        .filterNot { provider -> provider.id in REMOVED_SPONSOR_PROVIDER_IDS }
        .map { provider ->
            val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
            if (defaultProvider != null) {
                provider.copyProvider(
                    builtIn = defaultProvider.builtIn,
                    description = defaultProvider.description,
                    shortDescription = defaultProvider.shortDescription,
                )
            } else {
                provider
            }
        }
    val assistants = this.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
    DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
        if (assistants.none { it.id == defaultAssistant.id }) {
            assistants.add(defaultAssistant.copy())
        }
    }
    val ttsProviders = this.ttsProviders
        // 赞助商的 TTS 预置项同样下线
        .filterNot { provider -> provider.id in REMOVED_SPONSOR_TTS_PROVIDER_IDS }
        .ifEmpty { DEFAULT_TTS_PROVIDERS }
        .toMutableList()
    DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
        if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
            ttsProviders.add(defaultTTSProvider.copyProvider())
        }
    }
    return copy(
        providers = providers,
        assistants = assistants,
        ttsProviders = ttsProviders,
    )
}

// 去重并清理无效引用
internal fun Settings.withoutInvalidReferences(): Settings {
    val settings = this
    val stdioMcpServers = settings.stdioMcpServers
        .distinctBy { it.id }
        .distinctBy { server -> server.workspaceId.ifBlank { server.id.toString() } }
    val validMcpServerIds = settings.mcpServers.map { it.id }.toSet() + stdioMcpServers.map { it.id }
    val enabledMcpServerIds = settings.copy(stdioMcpServers = stdioMcpServers).allMcpServers()
        .filter { it.commonOptions.enable && it.commonOptions.name.isNotBlank() }
        .mapTo(HashSet()) { it.id }
    val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
    val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
    val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
    val validModelIds by lazy { settings.providers.flatMap { it.models }.mapTo(HashSet()) { it.id } }
    val asrProviders = settings.asrProviders.distinctBy { it.id }
    return settings.copy(
        providers = settings.providers.distinctBy { it.id }.map { provider ->
            provider.copyProvider(models = provider.models.distinctBy { model -> model.id })
        },
        assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
            assistant.copy(
                // 过滤掉不存在的 MCP 服务器 ID
                mcpServers = assistant.mcpServers.filter { serverId ->
                    serverId in validMcpServerIds
                }.toSet(),
                // 过滤掉不存在的模式注入 ID
                modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                    id in validModeInjectionIds
                }.toSet(),
                // 过滤掉不存在的 Lorebook ID
                lorebookIds = assistant.lorebookIds.filter { id ->
                    id in validLorebookIds
                }.toSet(),
                // 过滤掉不存在的快捷消息 ID
                quickMessageIds = assistant.quickMessageIds.filter { id ->
                    id in validQuickMessageIds
                }.toSet()
            )
        },
        ttsProviders = settings.ttsProviders.distinctBy { it.id },
        asrProviders = asrProviders,
        selectedASRProviderId = settings.selectedASRProviderId
            ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
            ?: asrProviders.firstOrNull()?.id,
        favoriteModels = settings.favoriteModels.filter { it in validModelIds },
        modeInjections = settings.modeInjections.distinctBy { it.id },
        lorebooks = settings.lorebooks.distinctBy { it.id },
        quickMessages = settings.quickMessages.distinctBy { it.id },
        stdioMcpServers = stdioMcpServers,
        stoppedMcpServerIds = settings.stoppedMcpServerIds.filterTo(mutableSetOf()) { it in enabledMcpServerIds },
    )
}

// 把取值收回合法范围，比如删掉搜索服务后选中的下标可能越界
private fun Settings.withValuesInRange(): Settings = copy(
    searchServiceSelected = searchServiceSelected.coerceIn(0, (searchServices.size - 1).coerceAtLeast(0)),
    defaultTTSPlaybackSpeed = defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f),
    agentSetting = agentSetting.normalized(),
    networkSetting = networkSetting.copy(
        maxRetries = networkSetting.maxRetries.coerceIn(0, NetworkSetting.MAX_RETRIES),
    ),
)
