# 定制清单

本分支相对上游的全部定制，一项一个编号。编号连续，不复用已作废的编号。

新增定制时先往索引表加一行，再在「明细」里写对应小节。三项内容分别回答「改在哪」「为什么改」「上游合并时要注意什么」。合并冲突时按明细逐项复核，确保定制意图没有在合并中被静默丢掉。

## 索引

| 编号 | 内容 | 性质 |
| --- | --- | --- |
| C1 | 移除媒体创作与生图 | 移除 |
| C2 | 移除 Firebase Analytics | 移除 |
| C3 | 移除赞助商推广，并允许删除所有提供商 | 移除 |
| C4 | 清理赞助商图标、README 推广与启动赞助弹窗 | 移除 |
| C5 | 移除 TTS 与 STT（语音合成与语音识别） | 移除 |
| C6 | 移除聊天回复建议 | 移除 |
| C7 | 应用图标旋转 180 度 | 改造 |
| C8 | 移除 Chatbox 与 Cherry Studio 导入 | 移除 |
| C9 | 移除 S3 备份 | 移除 |
| C10 | 新增从官方 RikkaHub 导入 | 新增 |
| C11 | 导出备份显示进度 | 新增 |
| C12 | 改包名与应用名 | 改造 |
| C13 | 移除 Firebase 与 google-services | 移除 |
| C14 | 移除设置页的分享本应用入口，统一品牌文案 | 移除 |
| C15 | 移除更新检查 | 移除 |
| C16 | 清除导出内容里的站点水印 | 移除 |
| C17 | 冻结官方备份的结构快照 | 新增 |
| C18 | 新增官方兼容的导出出口 | 新增 |
| C19 | 附件按替换语义导入，导入的原始备份留底 | 改造 |
| C20 | 导入器读取层 | 新增 |
| C21 | 导入器写入层 | 新增 |
| C22 | 导入器支持多版本官方快照 | 新增 |
| C23 | 导入器接上恢复流程 | 新增 |
| C24 | 导入器验收：与独立算出的期望值逐表比对 | 新增 |
| C25 | 同版本备份也重写附件路径 | 改造 |
| C26 | 整份备份的附件验收 | 新增 |
| C27 | 全文索引自检与重建完整性标记 | 改造 |
| C28 | settings 形状校验 | 新增 |
| C29 | 备份可选包含工作区文件 | 新增 |
| C30 | 官方兼容导出改写附件路径 | 改造 |
| C31 | 聊天草稿落盘 | 新增 |
| C32 | 附件按内容去重，删除改为摘引用 | 新增 |
| C33 | 附件拷贝挪出主线程 | 改造 |
| C34 | 备份与工作区文件的拷贝挪出主线程 | 改造 |
| C35 | 模型选择支持多关键词搜索 | 改造 |
| C36 | 消息附件引用按相对路径存储 | 改造 |
| C37 | CI 跑格式检查与 JVM 测试 | 改造 |
| C38 | 设置页提示授予本地网络权限 | 新增 |
| C39 | 把 README 收成一份 fork 说明 | 改造 |
| C40 | 导入时附件按内容寻址，归档必须有数据库与设置 | 改造 |
| C41 | 备份与恢复按阶段报进度，六个入口互斥 | 改造 |
| C42 | 备份页按本应用与官方 RikkaHub 分组 | 改造 |
| C43 | 错误卡片默认保留到手动关闭 | 改造 |
| C44 | 上下文压缩的提示词可编辑 | 新增 |
| C45 | 文案范围收成四份 | 改造 |
| C46 | 管理上下文：压缩与截断共用一个入口 | 改造 |
| C47 | 上下文消息数上限的自动边界线 | 改造 |
| C48 | 版本号随提交自动滚动 | 改造 |
| C49 | 分支切换只写 select_index | 改造 |
| C50 | 生成中定期保存回复节点 | 改造 |
| C51 | 压缩走可见生成轮次 | 改造 |
| C52 | Claude 缓存断点按官方标准收敛 | 改造 |
| C53 | Claude 缓存断点按提供商切换 | 改造 |
| C54 | 移除关于页图标点击彩蛋并清理相关死代码 | 移除 |
| C55 | 关于页独立图标 | 改造 |
| C56 | 文档 lint 配置 | 改造 |
| C57 | 请求日志开关持久化 | 改造 |
| C58 | 失败与中断回复状态 | 改造 |
| C59 | 截断流识别与停止结算 | 改造 |
| C60 | 审批期间冻结 turn 上下文 | 改造 |
| C61 | 结构化失败与重试策略 | 改造 |
| C62 | 流式 idle watchdog | 改造 |
| C63 | 上下文超限自动缩窗 | 改造 |

## 明细

### C1 移除媒体创作与生图

- 落点：`RouteActivity`（删 `Screen.ImageGen`、`Screen.MediaCreation`、`Screen.MediaCreationSessions` 及其 entry 与 intent 处理，`Screen.SettingMedia` 的 entry 保留）、`ChatDrawer`（侧边栏两个入口）、`ViewModelModule`、`RikkaHubApp`（启动恢复）、`SettingPage`（媒体设置入口）、`shortcuts.xml`、`AndroidManifest`；删除 `ui/pages/mediacreation` 与 `ui/pages/imggen`，以及它们遗留的 157 条无引用字符串（以 `media_creation_*`、`imggen_page_*` 为主，另有 `chat_page_menu_image_generation`、`shortcut_media_creation`、`setting_page_media_desc`、`menu`）；`media_creation_*` 中仍有 19 条在用（`MediaCreationService`、`MediaCreationForegroundService`、`RikkaHubApp`），不要按前缀整批删；`sketch_draw_on_image` 保留，它同时是聊天输入框画板的按钮文案；`res/drawable/ic_media_creation.xml` 随入口一起删掉
- 理由：不使用
- 合并注意：刻意保留未动：`data/model/MediaCreation.kt`、`data/files/MediaCreationFiles.kt`、`data/db` 下的实体与 DAO、`data/repository/MediaCreationRepository.kt`、`service/MediaCreationService.kt`、`service/MediaCreationForegroundService.kt`、整个 `mediagen/` 模块、manifest 的 `<service>` 声明、`Settings.mediaGenerationProviders`、`SettingMediaPage.kt` 及其路由。合并时不要因为界面上看不到就顺手删掉这些。两处冲突风险：上游若重排 `RouteActivity` 的 `Screen` 与 entry 列表；上游 `feat/mediagen` 分支（16 提交，2026-10-05 仍有活动）正在改 `MediaCreationService.kt` 与被删除的 mediacreation UI，合并时按该分支进度判断是否恢复部分实现

### C2 移除 Firebase Analytics

- 落点：`app/build.gradle.kts`、`AppModule`、`ChatVM`、`ViewModelModule`
- 理由：自用不需要埋点上报
- 合并注意：上游若增删 `ChatVM` 构造参数，`ViewModelModule` 里的显式传参会冲突

### C3 移除赞助商推广，并允许删除所有提供商

- 落点：`DefaultProviders.kt`（删掉 AiHubMix、APIMart、随想AI网关、MaruCode 四项，新增 `REMOVED_SPONSOR_PROVIDER_IDS`）、`RecommendedProviders.kt`（整个文件删除，它只包含这四家）、`SettingProviderPage.kt`（断开右上角推荐 Sheet、去掉 AiHubMix 的「10% 优惠」标签、去掉列表删除菜单的 `builtIn` 门禁）、`SettingProviderDetailPage.kt`（去掉详情页删除按钮的 `builtIn` 门禁）、`ai/util/Request.kt`（去掉 `configureReferHeaders` 里给 aihubmix.com 加 `APP-Code` 的分支）、`DefaultSettings.kt`（去掉 AiHubMix 的 TTS 预置项，新增 `REMOVED_SPONSOR_TTS_PROVIDER_IDS`）、`SettingsNormalization.kt`（提供商不再补回；助手与 TTS 仍补回）、相关测试
- 理由：不用这些推广渠道；预置项原本在界面上无法删除，规范化又会把删掉的补回来
- 合并注意：行为改动，`Settings.providers` 字段与 DataStore key 未变，不触发迁移。三点副作用：其一，存量里的赞助商条目会在下一次设置变更写盘时从设置中清掉，用户在这四家填过的 API key 与自建模型随之消失，回退旧版本后不会再出现；其二，因为不再补回，上游以后新增的预置提供商不会出现在已有安装里，需要手动添加；其三，删掉提供商后 `chatModelId` 等可能悬空，查找返回 null 并按未选择处理。上游若重写 `withBuiltInDefaults`，必须确认仍不补回提供商，否则删除功能失效；上游若恢复 `RecommendedProviders.kt` 或推荐入口，需要重新删除。`setting_provider_page_recommend` 字符串已删除

### C4 清理赞助商图标、README 推广与启动赞助弹窗

- 落点：`utils/AIIconMatcher.kt`（删掉 aihubmix 的图标映射与匹配正则）、`app/src/main/assets/icons/aihubmix-color.svg`（删除）、`README.md`（删掉 Sponsors 章节；README 的现状见 C39）、`SettingPage.kt`（删掉启动 100 次后的赞助弹窗及其失效的三个 import 与 `launchCount` 订阅）、`SettingVM.kt`（删掉失去唯一读者的 `launchCount`）、弹窗的 4 条 `setting_page_sponsor_alert_*` 字符串 × 7 个 locale
- 理由：不需要推广入口
- 合并注意：捐赠页 `SettingDonatePage`、`SponsorAPI`、`Screen.SettingDonate` 与捐赠相关字符串保留不动。`Settings.sponsorAlertDismissedAt` 是已存储字段，未删除以免改动结构，只是不再有自动写入方，`DebugPage` 仍可手动改。刻意删除：`docs/sponsors/*.png` 与 `docs/donate.png` 已无引用，一并删掉；合并时上游若更新这些图片会成 modify/delete 冲突，按删除为准。README 已收成一份 fork 说明，不再跟随上游，见 C39；`SettingPage` 的弹窗区域与 `SettingProviderPage` 类似，上游若加回推广块，需要重新删除。`AIIconMatcher` 的图标映射本身不是推广入口，只删掉带推广性质的 aihubmix 那一条；上游后续新增的赞助商图标映射（3.1.1 的 `naapi`）保留

### C5 移除 TTS 与 STT（语音合成与语音识别）

- 落点：
  - **speech 模块删除 32 个文件**（`speech/src/main/java/me/rerere/` 下除 `tts/provider/TTSProviderSetting.kt`、`asr/ASRProviderSetting.kt` 外的全部）：ASR 侧 `ASRController.kt`、`ASRState.kt`、`ASRVoiceTurn.kt`、`AudioAmplitude.kt`、`providers/DashScopeASRController.kt`、`providers/MiMoASRController.kt`、`providers/OpenAIRealtimeASRController.kt`、`providers/StepASRController.kt`、`providers/VolcengineASRController.kt`、`providers/VolcengineASRProtocol.kt`；TTS 侧 `controller/AudioPlayer.kt`、`controller/TextChunker.kt`、`controller/TtsController.kt`、`controller/TtsSynthesizer.kt`、`model/PlaybackState.kt`、`model/TTSRequest.kt`、`model/TTSResponse.kt`、`provider/TTSManager.kt`、`provider/TTSProvider.kt`、`provider/TTSProviderException.kt`、`provider/providers/` 下 `ElevenLabsTTSProvider.kt`、`FishAudioTTSProvider.kt`、`GeminiTTSProvider.kt`、`GroqTTSProvider.kt`、`MiMoTTSProvider.kt`、`MiniMaxTTSProvider.kt`、`OpenAITTSProvider.kt`、`QwenTTSProvider.kt`、`StepTTSProvider.kt`、`SystemTTSProvider.kt`、`VolcengineTTSProvider.kt`、`XAITTSProvider.kt`
  - **测试删除 5 个**：`app/src/test/java/me/rerere/rikkahub/ui/pages/chat/VoiceSessionControllerTest.kt`、`speech/src/test/java/me/rerere/asr/ASRVoiceTurnTest.kt`、`speech/src/test/java/me/rerere/asr/providers/VolcengineASRProtocolTest.kt`、`speech/src/test/java/me/rerere/tts/provider/providers/MiMoTTSProviderTest.kt`、`speech/src/test/java/me/rerere/tts/provider/providers/VolcengineTTSProviderTest.kt`
  - **app 侧删除**：`ui/hooks/TTS.kt`、`ui/hooks/ASR.kt`、`ui/context/LocalTTSState.kt`、`ui/context/LocalASRState.kt`、`ui/components/ui/TTSController.kt`、`ui/components/ai/AsrButton.kt`、`ui/components/ai/VoiceModeRow.kt`、`ui/pages/chat/VoiceSessionController.kt`、`ui/pages/chat/VoiceMode.kt`、`ui/pages/chat/TTSAutoPlay.kt`、`ui/pages/setting/SettingSpeechPage.kt`、`ui/pages/setting/components/TTSProviderConfigure.kt`、`ui/pages/setting/components/ASRProviderConfigure.kt`、`data/ai/tools/local/TextToSpeechTool.kt`
  - **断入口**：`SettingPage` 语音项、`SettingPreferencesGeneralPage` 的「TTS 设置」区块、`ChatInput` 麦克风与录音权限音效、`FilesPicker` 语音模式项、`ChatMessageActions` 朗读、`AssistantLocalToolPage` 的 TTS 开关、`RouteActivity` 的 TTS/ASR 状态与 `Screen.SettingSpeech`、`BuiltinToolUIs` 的 TTS 渲染与 `ToolUIRegistry` 注册、`LocalTools` 不再产出 TTS 工具、构造参数去掉 `ttsManager` 与 `settingsStore`、`AppModule` 解绑 `TTSManager` 并调整 `LocalTools` 注入、`ChatPage` 与 `ChatVM` 的语音会话接线、manifest 的 `RECORD_AUDIO` 与 `TTS_SERVICE` 查询
  - **一并清掉的死代码与孤儿资源**：`AppEvent.Speak`、`ChatService.enqueueVoiceMessage`、`SettingsExt` 的 `getSelectedTTSProvider` 与 `getSelectedASRProvider`、`PermissionTypes.PermissionRecordAudio`、`MessageQueue` 的 reply 等待通道与 `MessageQueuePausedException`、`ChatService` 里对应的 reply 完成路径与 `failReplyWaiters` 调用、`ChatService.generationDoneFlow` 与 `ChatVM` 的转发（唯一消费者是 TTSAutoPlay）、3 个 voice 队列测试、`res/raw/asr_start.mp3` 与 `asr_stop.mp3`，以及 7 个 locale 各 316 条无引用字符串（按归属拆开：媒体创作与生图 157 条属 C1、提供商推荐 1 条属 C3、赞助弹窗 4 条属 C4、语音相关 154 条属 C5）
- 理由：不用语音功能
- 合并注意：保留存储与反序列化：`Settings.ttsProviders`、`asrProviders`、`selectedTTSProviderId`、`selectedASRProviderId`、`defaultTTSPlaybackSpeed`、`ttsOnlyReadQuoted`、`ttsOnlyReadOutsideBrackets`、`autoPlayTTSAfterGeneration` 等字段与 DataStore key 未动；`TTSProviderSetting`、`ASRProviderSetting` 逐字节未改；`DEFAULT_TTS_PROVIDERS`（只剩 SystemTTS）与 TTS 补回逻辑保留；`LocalToolOption.Tts` 枚举值保留，旧助手配置仍可反序列化；`speech` 的 `build.gradle.kts` 未精简，仍带 media3 等用不到的依赖。与 C4 不同，这里选择的是彻底清理而非保留孤儿，理由是语音功能的入口、实现、文案与音效已全部消失，留着孤儿只会增加维护时的误判。app 侧语音代码是上游活跃区域（3.1.0 加过语音模式开关，3.1.1 又动了 `speech` 的 `TTSProvider.kt` 与 `ChatService` 的 reply 等待路径），恢复实现时需补齐上述入口；`speech` 只剩两个序列化类，上游改那一带时按本节保持删除。`SettingPage`、`SettingPreferencesGeneralPage`、`ChatInput`、`ChatMessageActions`、`ChatPage`、`ChatVM`、`ChatService`、`RouteActivity` 均为热区，冲突时以保留删除为准

### C6 移除聊天回复建议

- 落点：生成侧：`ChatService` 的 `generateSuggestion` 方法与每轮结束时的调用、`ConversationMetaGenerator.generateSuggestions`；`data/ai/prompts/Suggestion.kt` 整个删除，`DEFAULT_SUGGESTION_PROMPT` 内联到 `Settings.kt` 作为字段默认值。UI 侧：`ChatList` 的 `onClickSuggestion` 参数与 `ChatSuggestionsRow`、`ChatPage` 的回调、`SettingModelPage` 的开关、`SettingModelPromptPage` 的建议提示词编辑项；7 个 locale 删掉四条建议文案，`setting_model_page_fast_model_desc` 去掉「chat suggestions」的说法
- 理由：不用这个功能，每条回复都会多一次生成请求
- 合并注意：保留存储与协议：`Settings.enableSuggestion`、`Settings.suggestionPrompt` 的字段与 DataStore key 不动，`Conversation.chatSuggestions` 是 Room 字段不能删，`WebDto.chatSuggestions` 与 `ConversationRepository.updateChatSuggestions` 一并保留，历史数据仍能读出与同步。`UsageSource.SUGGESTION` 枚举值保留，用量统计里不会再有新记录。`setting_model_page_suggestion_prompt_vars` 仍被标题提示词项使用，没有删。`web-ui` 仍会渲染历史会话里已存的 `chatSuggestions`，只影响旧数据，React 侧没有跟删

### C7 应用图标旋转 180 度

- 落点：`app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` 下的 `ic_launcher.png`、`ic_launcher_background.png`、`ic_launcher_foreground.png`、`ic_launcher_monochrome.png`，5 个密度共 20 个 png 各旋转 180 度；`mipmap-anydpi-v26/ic_launcher.xml` 的结构不变
- 理由：让桌面上装的这个版本与官方版一眼区分开，造型变成倒着的兔子
- 合并注意：用 hflip 加 vflip 逐像素翻转，没有重采样，尺寸与 alpha 通道保持不变。上游的背景是浅上深下的纵向渐变，转后变成深上浅下，必须跟着转：legacy 的 `ic_launcher.png` 把背景与兔子烤在同一张图里，整图旋转后渐变必然反转，自适应路径下只有一起转两条路径的观感才一致。`monochrome` 必须一起做，否则 Android 13 的主题图标会退回官方样式。设置-关于页直接复用 `R.mipmap.ic_launcher`，无需单独改。上游 3.1.0 的 `bf04cde9` 调整过图标配色，此后上游再改这些 png 时会与本项冲突，处理方式一律保留我们旋转后的版本。它同时改了 `web-ui/public/favicon.ico`、`docs/icon.png`、`docs/img/poster.png`，这几处不在本项范围内

### C8 移除 Chatbox 与 Cherry Studio 导入

- 落点：`data/sync/importer/` 整个包删除（主源集 `ChatboxImporter.kt`、`CherryStudioProviderImporter.kt`，测试源集 `app/src/test/java/me/rerere/rikkahub/data/sync/importer/ChatboxImporterTest.kt`）；`BackupVM`（`restoreFromChatBox`、`restoreFromCherryStudio`、`ChatboxRestoreResult` 及随之失去读者的 `conversationRepository`、`filesManager` 两个构造参数）；`ImportExportTab`（备份页「从其他 APP 导入」分组的两个入口）；7 个 locale 的 `backup_page_import_chatbox_desc`、`backup_page_import_cherry_studio_desc`、`backup_page_import_from_chatbox`、`backup_page_import_from_cherry_studio`、`backup_page_import_from_other_app` 已删除
- 理由：不用这两个第三方客户端，也不需要它们的迁移路径
- 合并注意：这里删的是实现而不是断入口，理由见 [`README.md`](README.md) 的「改动尽量小，优先断入口」一节的补充说明。上游若重新加回这两个入口，需要重新删除

### C9 移除 S3 备份

- 落点：`data/sync/S3Sync.kt` 与 `ui/pages/backup/tabs/S3Tab.kt` 整个删除；`DataSourceModule` 的 `S3Sync` 绑定；`BackupVM`（`s3Sync` 参数、`s3BackupItems`、`loadS3BackupFileItems`、`testS3`、`backupToS3`、`restoreFromS3`、`deleteS3BackupFile`，以及 `S3BackupItem` 的 import）；`BackupPage`（S3 分页与 `HugeIcons.Bucket` import）；`backup_page_s3_backup` 与 `backup_page_s3_backup_files` 两条文案已删除；`setting_media_page_s3_desc` 里「与备份用的 S3 相互独立」半句在 7 个 locale 一并去掉
- 理由：不用 S3 做备份，WebDAV 与本地文件已够用
- 合并注意：**S3 底层必须保留**：`data/sync/s3/S3Client.kt`、`AwsSignatureV4.kt`、`S3Config.kt`、`ui/components/ui/S3ConfigItems.kt` 仍被媒体创作的素材上传使用（`RemoteFileStore` + `Settings.uploadS3Config` + `SettingMediaPage`），`backup_page_s3_endpoint` 等 7 条连接配置文案也仍在使用。`Settings.s3Config` 是已存储字段，未删除以免改动结构，只是不再有读写方。上游若改 `S3Config` 或 `S3ConfigItems`，要确认媒体页那条路径仍然可用

### C10 新增从官方 RikkaHub 导入

- 落点：`ImportExportTab`（官方 RikkaHub 分组里的「从官方 RikkaHub 导入」入口，操作类型用 `BackupOperation.IMPORT_OFFICIAL`，与本地导入共用同一个确认对话框与 `openDocumentLauncher`；分组本身见 C42）；7 个 locale 新增 `backup_page_import_from_official` 与 `backup_page_import_official_desc`
- 理由：改包名后本应用与官方应用并存，需要一个明确的迁移入口
- 合并注意：官方备份与本应用的归档格式相同（`settings.json` + `rikka_hub.db` + 附件目录），所以复用同一条 `stageRestore` 流程，入口只负责引导用户先在官方应用里导出备份。`JsonInstant` 开了 `ignoreUnknownKeys`，官方备份里的额外字段不会导致解码失败。本节只管导入方向；反过来的「导出官方兼容备份」是并列的第二个出口，由 C18 落地，现在遇到 schema 分叉会直接失败

### C11 导出备份显示进度

- 落点：`BackupManager.createBackup` 的可选 `onProgress` 与 `BackupProgress`（阶段与阶段内条目数，见 C41）；`WebDavSync.prepareBackupFile` 透传；`BackupVM` 的 `exportProgress` 状态在 `finally` 里清空；`ImportExportTab` 的进度行：打包与写目标文件两段都显示，写目标文件那一段是不确定进度。`backup_page_saving` 保留；打包各段用 C41 的分阶段文案（`backup_page_export_preparing` 等）。
- 理由：附件多时导出要等很久，没有进度不知道卡在哪一步。
- 合并注意：打包阶段先收集附件列表再写入，为的是给出每段确定的总数；代价是「列出」与「写入」之间文件消失的窗口从一个文件扩大到整个打包阶段，`PARTIAL_SUFFIX` 过滤与 `resolveInside` 校验都保留。进度模型改成阶段制之后，条目总数的算法在 C41 里。

### C12 改包名与应用名

- 落点：`app/build.gradle.kts`（`applicationId` 改为 `top.lemoneko.rakkihub`）、`AndroidManifest.xml`（shortcut deep link 的 scheme 从 `rikkahub` 改为 `rakkihub`）、`res/xml/shortcuts.xml`（`android:data` 同步）、7 个 locale 的 `app_name` 改为 `RakkiHub`（德语是上游 3.1.1 才加的）、`ExampleInstrumentedTest`（不再写死旧包名，改与 `BuildConfig.APPLICATION_ID` 比对）
- 理由：自主 fork 阶段要与官方应用并存，安装标识与私有数据目录必须独立
- 合并注意：`namespace` 保持 `me.rerere.rikkahub` 不动，类名、R 类与 `shortcuts.xml` 的 `targetClass` 都不受影响；`applicationIdSuffix = ".debug"` 保留，debug 包名是 `top.lemoneko.rakkihub.debug`；`${applicationId}` 拼的 authorities 与 `BuildConfig.APPLICATION_ID` 自动跟随

### C13 移除 Firebase 与 google-services

- 落点：`app/build.gradle.kts`（两个 plugin alias、`firebase-bom` 与 `firebase-crashlytics` 依赖、冗余的 `barcode.scanning`）、根 `build.gradle.kts`（两个 plugin alias）、`libs.versions.toml`（firebase 三个版本、四个库定义、`google-services` 插件、`barcodeScanning`）、`AppModule.kt`（两处 import 与 `single { Firebase.crashlytics }` 绑定，这个绑定本来就没有消费者）、本地 `app/google-services.json`、`README.md`（删掉「需要 google-services.json 才能构建」的提示；README 的现状见 C39）、CI 的 `GOOGLE_SERVICES_JSON` secret 与 workflow 里的写入步骤；顺带为 `ImageUtils.kt` 补上显式的 `androidx.exifinterface` 依赖
- 理由：不需要崩溃上报，也不想让应用依赖 Google 的运行时服务
- 合并注意：代码与构建脚本里已无 `com.google.firebase` 的引用；checked-in 的 baseline profile（`app/src/main/generated/baselineProfiles/`）里还留着 firebase 与 crashlytics 的历史条目，也有 C1/C5 删掉的 MediaCreation、ImgGen 与 TTS 类。这些条目指向已经不存在的类，ART 会跳过，不影响运行；重新生成能自愈，但在测试设备上跑不通（Gradle 装 nonMinifiedRelease 被系统拦下，用 su 装包后 instrumentation 又卡住并以 `DeadObjectException` 退出），真要重做需要换模拟器或干净设备。扫码在 3.1.1 由上游换成 CameraX 加 zxing，`quickie-bundled`、`barcode.scanning` 与 ML Kit 的传递依赖都不再存在；`androidx.exifinterface` 由上游在同一处显式声明，合并时删掉重复的那一行。上游若重新引入 Firebase，需要重新删除

### C14 移除设置页的分享本应用入口，统一品牌文案

- 落点：`SettingPage.kt`（about 分组里的分享 item、`shareText`/`share`/`noShareApp` 三个局部变量与三个失效 import）、7 个 locale 的 `setting_page_share`、`setting_page_share_desc`、`setting_page_share_text`、`setting_page_no_share_app`；品牌文案：`setting_page_web_server_desc`（7 个 locale）、`oauth` 模块的 4 条回调提示（7 个 locale）、`web-ui` 的 `page.json` 标题与描述、`conversation-sidebar.tsx` 的侧边栏标题
- 理由：分享出去还自称官方品牌并指向上游官网，没有意义；其余文案跟着应用名一起改成 RakkiHub
- 合并注意：`search-picker.tsx` 里的 `rikkahub: "RikkaHub"` 是搜索服务的显示名，指官方接口，保留；`backup_page_import_from_official` 的「Import from RikkaHub」指官方应用，同样保留。`SettingAboutPage` 的 `RikkaHub` 标题与 `rikka-ai.com`、GitHub、LICENSE 链接保留，设置根页的 `docs.rikka-ai.com` 链接也保留，`web-ui` 页脚的官方站链接同样保留，作为上游出处标注，没有改成自己的品牌

### C15 移除更新检查

- 落点：`utils/UpdateChecker.kt` 与 `ui/components/ui/UpdateCard.kt` 整个删除，随前者一起消失的还有只服务于版本比较的 `Version` 类与 `app/src/test/java/me/rerere/rikkahub/utils/VersionTest.kt`（89 行、11 个用例）；`AppModule` 的 `UpdateChecker` 绑定与 import、`ViewModelModule` 的 `updateChecker` 实参、`ChatVM` 的构造参数与 `updateState` 流、`ChatDrawer` 的更新卡片与暂停计时逻辑、`SettingPreferencesNotificationPage` 的更新提醒设置项与暂停对话框；8 个 locale 各删 13 条文案（5 个 `update_card_*`、6 个 `setting_update_reminder_*`、2 个 `setting_display_page_show_updates_*`），`setting_page_preferences_notification_desc` 去掉「更新提醒」那一段
- 理由：更新源指向官方，下载下来是官方包，装上去只是多出第二个应用；要新版本自己去看
- 合并注意：保留 `Settings.displaySetting.updateCheckDisabledUntilEpochMillis` 这类已存储字段，DataStore key 不动，只是不再有读写方。`distribution` flavor 与 `BuildConfig.IS_PLAY_STORE` 保留：那是上游的构建结构，删掉会抬高每次合并的成本，现在没有代码读这个字段。这里和 C8 一样删的是实现而不是断入口，理由见 [`README.md`](README.md) 的「改动尽量小，优先断入口」一节的补充说明。上游若重构 `UpdateChecker` 或恢复正常更新流程，需要重新删除

### C16 清除导出内容里的站点水印

- 落点：`ui/pages/chat/ConversationExport.kt`（导出会话图片时左下角不再拼 `rikka-ai.com`，只留时间）、`ui/components/richtext/Mermaid.kt`（导出 Mermaid 图时不再在画布底部绘制站点地址）
- 理由：导出的内容是自己生成的，不该带上游站点痕迹
- 合并注意：只删绘制水印的那几行，图表配色与布局不动，`onBackground` 仍被 mermaid 自身的配置使用。上游若再加回水印或其他署名，重新删掉

### C17 冻结官方备份的结构快照

- 落点：新增 `data/sync/official/`：`OfficialDatabaseSchema`（14 张表、106 个列、14 个索引、5 个外键、3 处自增）、`OfficialBackupLayout`（归档条目名、为兼容保留的 WAL 侧条目、`launchCount`、四个附件目录与其中两个嵌套目录），以及三个校验测试。配套改动：`BackupManager` 新增 `SETTINGS_ENTRY` 常量并让写入、恢复与 `PendingRestore` 三条路径共用它，另把 `ATTACHMENT_FOLDERS`、`NESTED_ATTACHMENT_FOLDERS`、`LAUNCH_COUNT_KEY` 从 private 放宽到 internal 以便对账
- 理由：导入器和官方兼容导出都需要一份「官方那侧认识什么」的描述，且不能随我们自己的 Room 定义漂，否则一旦我们改结构，导入器就读不懂官方库
- 合并注意：快照只描述官方那侧，数据从 `app/schemas/.../32.json` 生成。`message_fts*` 这类由手写 SQL 建的全文索引表不在其中：`AppDatabaseFactory` 打开数据库时只保证表存在，索引内容要靠 `ConversationRepository.rebuildAllIndexes` 重建，所以导入完成后要跑一次重建，否则搜索是空的。快照测试与 `32.json` 逐表、逐列、逐索引、逐外键对账，并断言仓库里最高的 Room schema 版本等于快照版本；布局测试与 `BackupManager`、`DatabaseBackup` 的常量对账；settings 测试钉住官方解码器的 `ignoreUnknownKeys` 行为。官方升级会触发版本断言，我们自己改了归档布局会触发布局断言；自家 schema 与官方分叉后版本那条要重做

### C18 新增官方兼容的导出出口

- 落点：新增 `BackupTarget`（`SELF` / `OFFICIAL`）、`OfficialDatabaseCompatibility`（判定我们的库还在不在官方形状上，比较版本与 identity hash，配 `OfficialDatabaseCompatibilityTest`）、`OfficialDatabaseAdapter`（一致时把附件路径改写成官方安装的位置再交出，分叉时抛 `OfficialExportUnsupportedException`）；`BackupManager.createBackup` 与 `WebDavSync.prepareBackupFile` 加目标参数，`BackupVM.exportToFile` 透传，只有 `SELF` 会更新备份提醒的时间戳；`ImportExportTab` 加第二个导出入口，落在官方 RikkaHub 分组里（C42），与自家导出的区别在数据库与其中的路径，目标用 `rememberSaveable` 存，避免选择文件期间被回收后退回自家格式；8 个 locale 新增 `backup_page_export_official`、`backup_page_export_official_desc`、`backup_page_export_failed`、`backup_page_export_schema_diverged`；`BackupManagerTest` 补一条官方目标的 androidTest
- 理由：改结构之后自家备份官方读不了，需要一个能把数据交回官方的出口；它和自家导出并列，不是把现有导出改掉
- 合并注意：当前双方 schema 一致，两个出口的差别只在本应用独占的选项与路径改写：适配层做「一致就把路径换成官方安装的位置、分叉就失败」，裁表裁列等真改结构时再补（路径改写见 C30）。失败是刻意的，静默产出官方打不开的备份要等到恢复时才发现；分叉走单独的提示文案，技术原因只进日志。导出失败时会尝试删掉选择位置时建出的空文档，尽量不留 0 字节文件（删除本身可能被 provider 拒绝，是 best-effort）。`settings.json` 不裁字段，官方解码器的 `ignoreUnknownKeys` 会忽略多余字段，这一点由 `OfficialSettingsCompatibilityTest` 钉住；但官方不认识的枚举值它吞不掉，所以官方出口会把工作区选项去掉（C29）。WebDAV 出口暂时只产出 `SELF` 格式。上游若改 `createBackup` 的签名或备份页的导出分组，第二个入口要跟着调整

### C19 附件按替换语义导入，导入的原始备份留底

- 落点：`PendingRestore.buildEntries` 与新增的 `buildRemovalEntries`（对归档里实际存在的附件目录，为 live 上有、payload 里没有的文件合成 `install = false` 的删除条目，复用现有 journal 与 rollback；只在数据库也一起替换时才做）；新增 `ImportedBackupArchive`（原子写入 `noBackupFilesDir/imported-backups/`，`stageRestore` 成功后才归档）；`WebDavSync.restoreFromBackupFile` 接上归档；`PendingRestoreTest` 补五条、新增 `ImportedBackupArchiveTest` 五条
- 理由：导入官方备份时附件是合并不是替换，目标目录里多出来的文件永远留着，启动时 `syncManagedFiles` 还会把它们重新登记进 `managed_files`，技能目录里的旧技能也会被扫到；另外导入完原始 zip 就没了，写坏数据没有回退来源
- 合并注意：删除条目只用文件级条目，不做目录级 rename：journal 与 rollback 都按路径记录单个文件，目录条目得为它们另加一套状态。`install = false` 的条目让 apply 把目标移进 `originals/`、rollback 再移回来，现有 journal 一行都不用改。只导附件时保持叠加语义，因为现有库还引用着 live 上的文件；导入现在必须带数据库（C40），这条分支只剩更早版本留下的 pending 目录会走到。空目录无法表达、`upload`/`fonts` 的平铺与递归不对称，都记在 [`backup/README.md`](backup/README.md) 的已知限制里。归档放 `noBackupFilesDir` 而不是 `filesDir`，后者整个目录被 web 路由暴露

### C20 导入器读取层

- 落点：新增 `data/sync/official/OfficialDatabaseReader.kt`（按冻结快照只读打开官方库，打开时核对 `user_version`、每张表、每列，行值按 affinity 读出并校验实际存储类型）、`OfficialSchemaVerifier.kt`（结构与类型判断抽成纯函数，不依赖 Android）；`OfficialSchemaVerifierTest` 七条 JVM 测试、`OfficialDatabaseReaderTest` 七条 androidTest
- 理由：导入器要用裸 SQL 读官方库，不能与我们的 Room schema 耦合
- 合并注意：读取层本身不接入口；写入与分流由 C23 接上。四个 Kotlin 文件都是新增，上游不会碰；上游升 schema 后要重新生成 `OfficialDatabaseSchema` 快照，读取层会跟着新快照读。设备限制见 [`backup/README.md`](backup/README.md) 的已知限制，样本缺口见 `OfficialDatabaseReaderTest` 的 KDoc

### C21 导入器写入层

- 落点：新增 `data/sync/official/OfficialTableOrder.kt`（从快照的外键推出写入顺序，循环外键报错）、`OfficialDatabaseWriter.kt`（一个事务把读出的行写进新建的空库，行级照抄不动 JSON 字段）、`OfficialUrlRewriter.kt`（把任意安装的绝对 `file://` files 前缀换成给定前缀，导入与官方兼容导出两个方向共用）；`OfficialUrlRewriterTest` 七条 JVM 测试、`OfficialTableOrderTest` 四条 JVM 测试、`OfficialDatabaseWriterTest` 四条 androidTest
- 理由：读取层只负责读，导入器还要把数据写进我们自己结构的库
- 合并注意：只写新建的空库，不接入口、不碰正在用的数据。这几个文件都是新增，上游不会碰。设备上跑 androidTest 前要先 `rm -f` 目标文件再 `adb push`，否则推的是旧字节

### C22 导入器支持多版本官方快照

- 落点：新增 `OfficialSnapshots`（版本到快照的注册表）、`OfficialSchemaTypes`（表/列/索引/外键的类型，抽出来避免快照与注册表循环初始化）、`OfficialSnapshotV31` 与 `OfficialSnapshotV32`（从各自的 schema json 生成）；`OfficialDatabaseSchema` 收缩成「当前版本 + v32 的别名」；读取层按库里的 `user_version` 选快照，不在清单里的版本明确失败；`OfficialSchemaVerifier` 改成接受选中的表定义
- 理由：读取层要能读 3.1.0 那版（v31）的备份，单版本快照做不到；验收也要用同一套读取逻辑比对两份样本。恢复路径上 v31 的版本比我们低，但它的附件路径属于旧包名，会被 C25 改走转换器而不是走 Room 迁移链；快照服务的是读取层、转换器与之后的字段映射层
- 合并注意：v31 的 identity 是 `a428043e...`，与官方 3.1.0 的库逐表对比 14 张表的列零差异。这份样本产自 3.1.0 (193) 的构建，当时包名还是 `me.rerere.rikkahub`、schema 与官方一致，所以它同时代表官方 3.1.0 与本 fork 改包名前的旧构建。v16（官方 2.1.6）明确不支持：它和 v31 之间隔了十几个版本。两个快照文件由 `tools/gen-official-snapshot.ps1` 生成，官方升版时重新生成并在注册表加一行

### C23 导入器接上恢复流程

- 落点：新增 `OfficialRestorePlanner`（按 `user_version` 与 schema 指纹决定迁移链 / 转换 / 拒绝，纯函数）、`OfficialDatabaseHeaders`（只读读两处元数据）、`OfficialDatabaseConverter`（官方库转成当前版本库，动手前对账目标表与列，缺表或缺列直接拒绝）；`BackupManager.stageRestore` 先 `normalize` 再分流，转换后替换暂存库并继续原流程；`OfficialRestorePlannerTest` 六条 JVM 测试、`OfficialSnapshotFilesTest` 两条（v31/v32 快照与各自 schema 文件逐列对账）、`OfficialDatabaseConverterTest` 两条 androidTest、`BackupManagerTest` 补两条（同版本不同结构先转换再发布、高版本明确拒绝）；`RikkaHubApp` 在恢复成功后后台重建全文索引；快照生成脚本移到 `tools/gen-official-snapshot.ps1`（`build/` 被 gitignore，脚本放那里不可复现）
- 理由：迁移链只能往上升，官方库版本高于我们或同版本不同结构时打不开；这是「导入器可用」的最后一段接线
- 合并注意：判定在读任何业务表之前完成，`ourVersion` 与 `ourIdentity` 都用编译进应用的常量，不从活库读（升级后首次启动时活库可能还停在旧版本）。高版本一律拒绝而不是回落到迁移链：迁移链降不了版本，而官方升版通常是加列，硬写会在插入时崩。同版本但缺 schema 指纹也拒绝，来源判不出来时不猜。`stageRestore` 是热区，上游改恢复流程时按本节复核顺序（normalize → 分流 → 转换 → 打开校验 → checkpoint）

### C24 导入器验收：与独立算出的期望值逐表比对

- 落点：新增 `tools/backup-expectations.py`（用 Python 的 sqlite3 直接读原始备份，按 schema 的列顺序与主键排序，算每张表的行数与规范化 SHA-256）、`OfficialSampleAcceptanceTest`（androidTest，两层：直接读原始备份、以及经转换器写成当前版本库后再读，各自与期望值逐表比对；没有样本就跳过）；判据与跑法记在 [`backup/acceptance.md`](backup/acceptance.md)
- 理由：没有独立期望值时，「读取层读对了」只是自证；roadmap 也要求「只编译通过不算数」
- 合并注意：规范化是「类型字节 + 4 字节小端长度 + 载荷」，行间 `0x1e`，两侧各实现一遍并逐字节对齐。样本含私人数据，放仓库外，测试用 `Assume` 跳过，CI 不会因此失败。v31 与 v32 两份样本都跑过（各 14 张表全过）；v16 明确不支持，不纳入样本集。这两处都靠 `app/schemas` 的列顺序，上游升 schema 后要重新生成期望值

### C25 同版本备份也重写附件路径

- 落点：新增 `OfficialForeignAttachmentPaths`（只探测：库里的文本列是否还带着别的安装的 `file:///data/.../files/` 前缀）；`BackupManager.stageRestore` 在判定为迁移链之后再探一次，命中就改走转换器，由写入层把路径重写成本机；`BackupManagerTest` 加一条回归用例；新增 `OfficialForeignAttachmentPathsTest` 四条 instrumentation 用例（外来、本安装、混合、无引用）
- 理由：判为迁移链的备份本来走迁移链，而迁移链不改数据。带附件的官方备份、或 C36 之前别的包名（debug / release、改过包名的构建）下产生的备份，消息、设置与头像里都可能带着生产者的绝对路径，装进当前安装后附件全部失配；C36 之后新写或重存过的消息引用是相对形态，不需要这一步；没再存过的老会话与设置仍是绝对路径。
- 合并注意：探测只看快照覆盖的文本列，读不到快照就不猜、直接走迁移链。判断按出现位置做：先删掉当前安装的前缀，剩下的文本里还有 `file:///data/` 就算命中，同一格里既有本安装前缀又有外来前缀时也不会漏。代价是命中时要把整库复制一遍（转换器不改原库）。探测比写入宽：正文里出现别的 `file:///data/` 文本也会命中，这种情况下没有路径可改写

### C26 整份备份的附件验收

- 落点：`tools/backup-expectations.py` 增加 `--settings` 与 `--zip`：只统计 settings 的助手与服务商条数，并从归档里算每个附件的路径、大小、SHA-256；新增 `OfficialBackupRestoreAcceptanceTest`（androidTest）：把真实归档走一遍 `stageRestore`，逐个断言附件的存在性与内容摘要，并确认解压出来的附件不多不少；另加 `RestoreAppliedOutcomeTest`：恢复应用之后 live 的 `upload/` 必须正好是归档那一份，上一次安装残留的文件要被清掉；settings 断言补上助手与服务商的 id 集合、当前助手、当前模型
- 理由：表数据在库里能验，附件与 settings 不行：附件是文件、settings 不在任何表里。没有这一层，导入把附件丢了或换了内容都没人发现
- 合并注意：样本是完整备份（含私人数据），放仓库外、设备上，缺了就跳过。settings 的期望值必须从**同一份归档**里取。跨归档借 `settings.json` 会把期望值算成另一份样本的数，表现为导入像丢了数据，实际是期望值来源错了，与导入链路无关

### C27 全文索引自检与重建完整性标记

- 落点：`MessageFtsManager`（`isEmpty` 判断索引里有没有内容；`message_fts_state` 表记 `rebuild_complete`，重建开始置 0、成功收尾置 1）、`SearchIndexHealth.needsRebuild`（纯函数，新增 `rebuildComplete` 参数：有会话且（索引空或上次重建没跑完），六条 JVM 测试）、`ConversationRepository.needsSearchIndexRebuild`；`RikkaHubApp` 在恢复成功那次与常规启动都跑自检
- 理由：重建是「先清空再逐会话写」，只靠「索引非空」发现不了残缺：重建中途被杀时索引非空但缺内容，自检会跳过，搜索永久缺一部分，用户也不知道该重建。恢复被中断同样会留下残缺索引
- 合并注意：标记表与 `message_fts*` 同属 `onOpen` 里手写 SQL 建的表，不进 Room schema、不动 DataStore 键、不影响 identity 校验；恢复换库后若缺该行会触发一次重建，符合预期。判据仍有一处理论缺口：如果重建没跑过、但索引里恰好有内容，会多重建一次

### C28 settings 形状校验

- 落点：新增 `data/sync/BackupSettingsShape.kt`（纯 JVM，不依赖 Android）与 `BackupSettingsShapeTest` 十四条；`BackupManager.stageRestore` 在 `SettingsJsonMigrator.migrate` 之后、`decodeFromString` 之前调用一次，不合格直接失败
- 理由：解码器开着 `ignoreUnknownKeys`，每个字段又都有默认值，空对象或别家的 JSON 也能解码成功，得到一份全默认值的 Settings 然后覆盖用户配置
- 合并注意：只校验关键项（`assistants`、`providers` 必在各为数组、元素带字符串 `id`，`displaySetting` 必在且是非空对象，`init` 与几个 id 字段若存在则类型必须对），不校验完整 schema，因为旧备份缺新字段是正常的。报错信息用英文，与 `BackupManager` 里其它异常一致。上游给 `Settings` 加字段不需要动这里；改这几个关键字段的名字或形状时要同步

### C29 备份可选包含工作区文件

- 落点：`WebDavConfig.BackupItem` 增加 `WORKSPACES`（默认不在 `items` 里，`DEFAULT_SELECTION` 由设置默认与本地导出页共用，所以默认关）、两个备份页的多选标签与 8 个 locale 的 `backup_page_workspaces`；`BackupManager.createBackup` 增加 `includeWorkspaceFiles`，新增 `collectWorkspaceFiles`（按 `workspaceDao()` 的工作区逐个走 `<root>/files`，跳过 `linux/` 与 `tmp/`，root 不合法时记 warning 并跳过）；`stageRestore` 增加同名参数与 `isWorkspaceFile` 校验（形状无条件校验，不看勾选）；官方兼容出口编码 settings 前把 `WORKSPACES` 从 `webDavConfig.items` 里滤掉；目录名改用 `WorkspaceManager.BASE_DIR_NAME` 与 `FILES_DIR`；归档条目名与 `filesDir` 下的布局一致（`workspaces/<root>/files/<relative>`），恢复侧因此复用已有的 `files/` 映射
- 理由：工作区文件是用户产物，但工作区里还可能有整个 PRoot 根文件系统，体积与必要性都不适合默认进备份
- 合并注意：只打包文件区，归档里出现 `workspaces/<root>/linux/...` 一律拒绝，避免写坏或被改过的归档覆盖根文件系统。恢复是叠加语义，不删 live 里多出来的工作区文件；附件那套替换语义没有套过来，这条有专门用例钉住。恢复要求导入时勾上工作区，条目在归档里但当前选择不含工作区时会被跳过，启动自检随后可能把这些工作区标成 BROKEN。`WORKSPACES` 是本仓库新增的枚举值，官方不认识，未知枚举值不像未知字段那样能被 `ignoreUnknownKeys` 忽略，所以官方出口必须把它从 settings 里去掉

### C30 官方兼容导出改写附件路径

- 落点：新增 `data/sync/official/OfficialExportPaths.kt`（纯函数，推导要替换的前缀对：本安装实际的 files 目录，加上 `/data/user/<用户号>/<包名>/files/` 与 `/data/data/<包名>/files/` 两种规范形态，用户号从路径里取；另带 `LIKE` 模式的字面量转义）与 `OfficialExportPathsTest` 十条 JVM 用例；`OfficialDatabaseAdapter.adaptForOfficial` 增加 `attachmentPrefixRewrites`，一致性校验之后逐表逐列对 TEXT 列做前缀 `REPLACE`（一个事务、`LIKE` 模式转义、写完确认没有残留副文件），产出改写过的库副本（没有要改的就返回原快照）；`BackupManager.createBackup` 用这份前缀对改写库，settings 走 `OfficialUrlRewriter.rewriteEmbedded`；`OFFICIAL_APPLICATION_ID` 常量放在 official 包里；`OfficialUrlRewriter` 的 KDoc 改成方向中立（导入与导出共用）；`BackupManagerTest` 补三条用例（消息里的附件、settings 里的头像、官方归档回灌本应用），每条都同时断言自家出口不改或回灌后换回本机路径
- 理由：官方客户端恢复只把附件搬进自己的 files 目录，不会改库与设置里的路径。我们导出的库里消息附件可能是相对形态（C36），设置与旧行仍是本应用的绝对 `file://`，不换的话装过去之后图片、文档、头像会断
- 合并注意：改写只在 OFFICIAL 目标发生，自家出口一行不动。官方包名写死在 `OFFICIAL_APPLICATION_ID`，官方换 applicationId 时要跟着改。消息库里新写的相对形态（C36）也会在导出时换成官方安装的绝对位置。这条只解决路径，schema 分叉后的裁表裁列仍待实现；官方客户端实际恢复一遍仍是人工闸门

### C31 聊天草稿落盘

- 落点：新增 `data/files/ChatDraftStore.kt`（一个会话一个 JSON，先写临时文件再原子替换、临时名带随机后缀，坏文件当作没有草稿，空草稿删文件，读写都在 IO 上，另有 `deleteOrphans` 与给抽屉用的 `conversationIds`）与 `ChatDraftStoreTest` 十五条 JVM 用例；`RepositoryModule` 注册实例并把目录指到 `noBackupFilesDir/chat-drafts`；`ChatVM` 加构造参数（`ViewModelModule` 接线），`init` 里先恢复再观察输入变化（`snapshotFlow` 加 500ms 防抖，编辑历史消息期间观察值是 null），另有一段观察编辑状态的转变、编辑结束立刻把编辑前的内容放回输入框；`onCleared` 补一次同步写，编辑中被销毁时留的是进编辑前那份；新增 `ChatEditDraft` 收起暂存的放回规则，`ChatEditDraftTest` 九条 JVM 用例钉住，`ChatVM.beginEditingMessage` 进编辑前把草稿交给它，并另起一次写盘（不等防抖），`ChatPage` 的编辑入口改为调它；`ChatService.deleteConversation` 顺手删草稿（没落过库的新会话也要覆盖），`ConversationRepository.getConversationIds` 交给 `RikkaHubApp` 启动时清理；抽屉的会话项上标出还有草稿的会话（`ChatDrawerVM` 持有一份 id 集合，打开抽屉与切换会话时刷新），8 个 locale 新增 `chat_page_has_draft`；`ui/hooks/SharedPreferences` 新增 `readLastConversationId` 与 `writeLastConversationId`，启动页、`ChatVM`、`RikkaHubApp` 清理与 `SafeModeActivity` 共用
- 理由：输入框内容只在 ViewModel 里，进程一死就没了，附件引用也跟着消失；将来做附件清理时，草稿里的引用必须能被扫描到
- 合并注意：草稿是文件不是表，不动 Room schema，官方兼容导出不受影响；放 `noBackupFilesDir` 是因为 `filesDir` 整个目录被 web 路由暴露，草稿也不进备份。只记文字与附件，不记「正在编辑哪条消息」。清理的保留集是「库里的会话 + 上次打开的会话」：新建会话的 id 只在内存里，没发消息就退进程时库里查不到它，但关掉「启动新建会话」时下次启动会直接打开它，所以不能按「不在库里」删。编辑结束后草稿立刻放回输入框（不等防抖），放回时输入框不是空的话不覆盖，以用户新写的内容为准；删除会话会一起删草稿，撤销删除不会恢复草稿，从别的页面删掉还停在聊天页上的会话后，页面继续输入会重新写出一份草稿，这个 id 仍是「上次打开的会话」时启动清理会留着它。防抖窗口内进程被杀丢的是最后一次停顿之后的内容；附件清理（C32）靠草稿与 `LiveAttachmentReferences` 保住输入框里的附件，两个都不在了这层引用才消失。抽屉里的草稿标记只在打开抽屉与切换会话时刷新，不是实时订阅，清掉草稿后标记要等下次刷新才消失；`conversationIds` 只看文件名，坏掉的草稿也会列出，打开会话时发现没有内容，下一次刷新标记就没了。`ChatVM`、`ChatService`、`ChatDrawer` 与 `RouteActivity` 都是上游热区，合并时按本行复核

### C32 附件按内容去重，删除改为摘引用

- 落点：新增 `data/files/ContentAddressedFiles.kt`（边写边算 SHA-256，写进同目录的 `.pending-<uuid>` 临时文件，落盘后按指纹改名，目标已存在就复用）与 `FileUtils.buildContentFileName`（扩展名优先按 MIME，其次来源文件名）；`FilesManager` 的 upload 写入全部走它（`saveManagedFrom*`、`createChatFilesByContents/ByByteArrays`、`createChatTextFile`），`createManagedFileEntity` 遇到同一路径复用已有登记，`syncFolder` 不登记 `.pending-*`；新增 `data/files/AttachmentReferences.kt`（从 JSON 与草稿文本里摘 `file:` 地址、归一成规范路径）、`LiveAttachmentReferences.kt`（进程内登记输入框附件，编辑历史消息期间草稿不写输入框，靠它兜住）与 `AttachmentCleaner.kt`（扫引用点、删无引用文件与 `managed_files` 行，`collectReferencedPaths` 与 `shouldDeleteUploadFile` 单独成纯函数）；删掉所有立即删文件的入口：`ChatService` 的落库差异删除与队列附件清理、删会话时的文件删除、`ConversationRepository.deleteConversation`、输入框移除附件（`AttachmentChips`、`ChatInputState.shouldDeleteFileOnRemove`）、助手头像与背景、用户头像，`FilesManager.deleteChatFiles`、`MessageQueue.unreferencedQueuedAttachmentUrls` 与上游的 `QueuedAttachmentCleanupTest.kt` 一并删除；`MessageNodeDAO.getContentsWithFileReferences` 只取含 `file:` 的消息行，`ChatDraftStore.referencedFileUrls`、`ChatService.unsavedAttachmentUrls` 与 `ChatService.isGenerating` 补上库外与内存引用；设置的文件管理页加「清理无引用的文件」入口，按天清理也走同一套引用判断（`AttachmentCleaner.cleanOlderThan`，`FilesManager.deleteOlderThan` 删除），新增 7 条文案 × 8 个 locale；新增 `ContentAddressedFilesTest` 四条、`AttachmentReferencesTest` 六条、`AttachmentCleanerTest` 十三条、`LiveAttachmentReferencesTest` 五条 JVM 用例，`ChatDraftStoreTest` 补一条草稿引用扫描
- 理由：文件名原来是随机 UUID，写之前无从判断内容是否已经存在，同一张图在多个会话、多条消息里各存一份，备份体积里有很大一块是重复内容。改成内容寻址之后同内容只留一份，删除也就不能再按「删消息就删文件」做
- 合并注意：内存与 UI 的引用形式仍是绝对 `file://`；消息库里落盘的是相对形态（C36），清理按两种形态解析，官方导出的改写也覆盖相对形态；序列化格式与 Room schema 不变。`managed_files` 只是旁路索引，消息读取不经过它，去重之后同一路径只有一条登记。清理是「宁可少收垃圾也不能删错」：解析不了的地址按文件名保守保留，生成开始前与删除前各查一次 `isGenerating`，工具结果里的新文件可能还没落库；输入框里的附件由草稿（C31）与 `LiveAttachmentReferences`（C32）保活，编辑历史消息期间不写草稿的那部分也在后者里；Web 客户端上传后还停在浏览器输入框里的附件不在扫描范围，清理前别让浏览器里留着没发出去的附件；工作区把 `filesDir/upload` bind mount 到 `/upload`，从终端写进去的文件没有任何引用，也会被清掉。消息扫描一次把所有含本地地址的行读进内存，历史很大时是手动清理的时间与内存上限；删除前还会再取一次只存在于内存的输入框登记与内存会话、队列引用，缩小扫描窗口内新加附件被误删的可能。文件页的按天清理也走同一套引用判断，只删没有引用、且登记时间（没有登记时按文件修改时间）早于截止时间的 upload 文件；登记时间在去重复用时不刷新，所以年龄是「首次写进来的时间」，不是「最后一次被用」；「全部文件」、单个删除与 Web 的 `DELETE /api/files/{id}` 仍然直接删文件，这是显式的破坏性操作。存量文件的随机名不会被就地归并：应用不会主动扫盘改名，只有带数据库的导入会把归档里的文件按内容改名并去掉重复（C40）。`FilesManager`、`ChatService`、`ConversationRepository`、`AttachmentChips`、`AssistantVM`、`AssistantDetailVM`、`ChatVM`、`SettingFilesPage`、`ViewModelModule`、`RepositoryModule` 都动了，合并时按本行复核。

### C33 附件拷贝挪出主线程

- 落点：附件进入 upload 的 UI 调用点把拷贝与 MIME 查询包进 `withContext(Dispatchers.IO)`：`ChatPage` 的分享入口、`ChatAttachmentPicker` 的选图、拍照（裁剪与不裁剪）、视频、音频、文档，`UIAvatar.saveAvatarImage`，`BackgroundPicker`。`CropLauncher.useCropLauncher` 的 `onCroppedImageReady` 改成 `suspend`，调用方在回调里等拷贝完成再返回，`CropLauncher` 等回调返回后才删裁剪用的临时文件；取消时重抛 `CancellationException`，不弹「裁剪失败」。相机不裁剪时输出文件在拷贝完成后的 `finally` 里删。`ChatService.forkConversationAtMessage` 把 `source.fork(...)` 包在 `withContext(IO)` 里，`copyFile` 仍是同步 lambda。`ChatInput` 的粘贴图片保持同步，`consume` 出来的内容离开回调可能失效。
- 理由：主线程上拷贝大文件会卡界面甚至 ANR，C32 给内容寻址写入加了 `fsync` 之后更明显。
- 合并注意：`CropLauncher` 的回调现在是 suspend 契约，调用方必须在回调里完成拷贝再返回，否则临时文件会在拷贝前被删；`ChatPage`、`ChatAttachmentPicker`、`ChatInput`、`CropLauncher`、`UIAvatar`、`BackgroundPicker`、`ChatService` 都动过，合并时按本行复核。异步化带来一个已知交错：拷贝期间用户可以发送或关闭选择器，附件会在拷贝完成后追加到输入框，分享路径还会整体覆盖输入框内容；没有加「导入中」状态。粘贴路径必须保持同步；fork 的 `copyFile` 参数仍是同步类型，只是在调用点包了 IO。

### C34 备份与工作区文件的拷贝挪出主线程

- 落点：`ImportExportTab` 的导出复制（临时 zip 到用户选择的位置）与导入暂存（用户选择的位置到 cache 临时包，连同 `vm.restoreFromLocalFile`）包进 `withContext(Dispatchers.IO)`；`vm.exportToFile` 在主线程调用，打包主体在 `BackupManager` 里自带 IO 派发，只有复制在 IO。`ImportExportTab` 的开流失败（`openOutputStream`/`openInputStream` 返回 null）按失败处理，不会静默报成功或拿空包去恢复；失败分支会删掉目标文档，若用户选择覆盖已有文档，null 开流时那份文档也会被删，这是失败清理策略的残余风险。复制被取消时重抛 `CancellationException`、不弹失败；导出取消会静默删掉没写完的目标文档；导出与导入的占用由 `BackupVM` 的统一锁管理，`release` 在 `finally` 里调用（C41）；导出临时文件在 `finally` 里删。`WorkspaceDetailPage` 的文件选择器把名字查询、`openInputStream` 与交给 VM 都放进同一个 IO 块，避免交接前被取消留下没关闭的流。
- 理由：备份 zip 可能有几百 MB，整包复制放在主线程会卡界面甚至 ANR；SAF 的查询与打开是 binder 调用，也放到 IO 更稳。
- 合并注意：改了上游的 `ImportExportTab` 与 `WorkspaceDetailPage`，合并时按本行复核。`restoreFromLocalFile` 从 IO 块里调用，其链路自带 IO 派发；`exportToFile` 仍在主线程调用，内部只碰 StateFlow、设置读写与 IO，不能假设自己必须在 Main。六个备份入口共用一把锁（C41），复制期间不能再启动另一条；锁的抢占与释放在 `BackupVM` 里，与这里的 IO 派发无关。取消时清理在 `finally` / `catch` 里，不要让 `CancellationException` 走进失败提示。恢复在 `stageRestore` 里发布 pending 之后才归档原始包；如果取消发生在发布之后，pending 仍会在下次启动生效，界面可能没弹成功提示。

### C35 模型选择支持多关键词搜索

- 来源：上游 `5ad19405`（`feat(model): 模型选择面板支持多关键词搜索`），代码与该提交一致；`ModelFilterTest` 六条用例与 `ref` 里的那份相同，本分支相对它的差异只有 KDoc 与一个空行。
- 落点：`ai/src/main/java/me/rerere/ai/provider/Model.kt` 加 `List<Model>.filterByKeywords`（按空格拆关键词、忽略大小写、每个关键词命中 `modelId` 或 `displayName` 的一部分、不要求顺序，空查询返回原列表）；`ModelList.kt` 的聊天模型面板与 `SettingProviderDetailPage.kt` 的可用模型面板共用这一条规则；`ModelFilterTest` 六条 JVM 用例（与上游该提交一致）。
- 理由：聊天面板原来整串匹配且只看 `displayName`，多词或按模型 ID 搜不到；提供商详情的面板已经按空格拆词并同时匹配 `modelId` 与 `displayName`。这次抽成共用函数，两边用同一条规则，聊天面板也因此能按模型 ID 搜。
- 移植注意：`filterByKeywords` 在 `ai/`，该模块仍尽量跟随上游；以后跟随上游对 `Model.kt` 的改动时保留这个函数，两个 UI 调用点在 `app/`，合并成本让步已不适用。上游后续如果再改这块，按本行核对。

### C36 消息附件引用按相对路径存储

- 落点：新增 `data/files/AttachmentUrlStorage.kt`（两个纯函数：落盘前只把 JSON 里 `url` 字段的、本安装 files 目录下的 upload 绝对地址换成 `file:upload/...`，读取后还原；本安装的 `/data/user/<用户号>/` 与旧的 `/data/data/` 两种绝对形态都认）与十条 JVM 用例；`ConversationRepository` 构造多一个 `attachmentFilesDirUri`（Koin 里从 `context.filesDir` 生成），`saveMessageNodes` 落库前转换，读取消息时还原；`AttachmentReferences.fileUrlToCanonicalPath` 与 `collectReferencedPaths` 的 `filesDir` 改成必填，把相对形态还原成规范路径，`AttachmentCleaner` 扫描时传入当前 filesDir；`OfficialExportPaths.attachmentRewrites` 增加 `"url":"file:upload/` 到官方安装前缀的替换（包名相同时也要换，官方客户端只认绝对路径），配 `OfficialExportPathsTest` 断言，官方兼容导出的库仍是官方能读的绝对地址。`AttachmentCleanerTest` 补一条真实 `upload/` 文件的相对形态解析用例，删掉 `MessageNodeDAO` 里没有调用点、会绕过转换的 `getNodesOfConversation`、`insert`、`update`。
- 理由：消息库与随它导出的备份原来把安装路径写死在每条附件地址里，包名或用户号一变就整体失效；导入侧的路径重写是事后补救，落盘形态本身应该可移植。改完之后相对地址在任意安装下都能解析，旧库里的绝对地址继续有效并在下一次保存时转换。
- 合并注意：消息 JSON 的读写都经过 `ConversationRepository` 的转换，别的入口直接读写 `MessageNodeEntity.messages` 会绕过它；设置里的头像仍存绝对地址（靠导入侧重写），草稿不随备份走，两者都没改。清理与官方导出都按绝对、相对两种形态处理；官方导出的相对前缀带 `"url":"` 字段边界，避免碰到正文。旧数据不做一次性迁移，读到什么算什么，下一次保存时本安装的两种绝对形态都会转成相对。C36 之前的构建读到新库里的相对地址时图片大概率显示不出来，点音频、视频的附件可能抛异常，文档附件会退化成 ERROR 文本；数据不丢，升级后恢复。

### C37 CI 跑格式检查与 JVM 测试

- 落点：新增 `.github/workflows/checks.yml`（push 到 `main`、PR、手动触发）：环境与每日构建一致（JDK 21、pnpm 11、Node 22、web-ui 依赖、submodules），跑 `spotlessCheck` 与 `:app:testGithubDebugUnitTest`、`:ai:testDebugUnitTest`、`:workspace:testDebugUnitTest`，失败时上传测试报告。push 时把 `origin/main` 指到 `github.event.before` 再跑格式检查（首次推送、强推后基点不可达时跳过并打 notice），PR 用基线的 `origin/main`，手动触发没有可靠基点也跳过；测试是全绿判据。`docs/custom/roadmap.md` 的 2.2 与 `docs/custom/build.md` 同步。
- 理由：仓库原来只有每日构建，格式与测试靠人工；这两类检查反复出现（Windows 固定失败、spotless 的 ratchet 基点），把它们做成自动闸门。app、ai 与 workspace 是定制最集中的三个模块，其余模块的测试本轮不跑，边际收益低。
- 合并注意：环境版本（JDK/pnpm/Node）与 `daily-build.yml` 两处维护，上游改其中之一时两边一起更新；`spotlessCheck` 的基点在 push 时由 workflow 改写，本地仍按 `origin/main`，build.md 有说明。CI 在 Linux 上跑，Windows 本机那批文件占用失败预期不再出现，首次跑批确认；若红了先看平台差异。`checks.yml` 是相对上游新增的文件，上游若加同名 workflow 时按本行核对（不会自动合并）。

### C38 设置页提示授予本地网络权限

- 来源：上游 `903e058b`（`feat(setting): 设置页顶部提示授予本地网络权限`），代码与该提交一致。
- 落点：新增 `ui/components/ui/permission/LocalNetworkPermissionCard.kt`（`rememberLocalNetworkPermissionState` 在 SDK 37 及以上返回权限状态，`LocalNetworkPermissionCard` 是点一下发起申请的提示卡）；`SettingPage` 在通用设置之前按未授权状态插入这张卡；8 个 locale 的 `permission_local_network_desc` 改成覆盖「连接局域网内的自建服务」与「其他设备访问 Web 服务器」两类场景。
- 理由：targetSdk 37 起应用默认不能访问局域网。Web 服务器页会申请这个权限，但连局域网里的自建 provider、SearXNG、MCP 服务时没有对应入口，只会超时；设置页的提示卡补上这个入口。卡片只在未授权时出现，已经授权的安装不受影响。
- 移植注意：卡片用 `PermissionLocalNetwork`（`PermissionTypes.kt` 里标了 `@RequiresApi(37)`），SDK 判断在 `rememberLocalNetworkPermissionState` 里；`RikkaHubApp` 启动 Web 服务器前也会检查这个权限，那处逻辑没动。上游以后改这张卡或权限申请流程时按本行核对。

### C39 把 README 收成一份 fork 说明

- 落点：根 `README.md` 重写为简体中文的 fork 说明（fork 定位、谨慎使用、官方仓库与官网、nightly 构建说明、定制文档入口、许可证），删除 `README_ZH_CN.md` 与 `README_ZH_TW.md`；`docs/README.md` 的说明同步，C4、C13 里涉及 README 的落点指向本项。
- 理由：本仓库是个人分支，保留上游三份翻译会让人误以为这是官方项目，官方仓库链接也埋在功能列表后面。README 需要一眼说清这是个人 fork、有风险，并指向官方仓库。
- 移植注意：上游 README 不再跟随；上游若改安装、构建或主要功能说明，按需抄进 `README.md` 与 `docs/custom/build.md`，不要恢复多语言版本。

### C40 导入时附件按内容寻址，归档必须有数据库与设置

- 落点：`WebDavConfig.BackupItem` 加 `SELECTABLE`（只有 `FILES` 与 `WORKSPACES`）与 `normalize`（聊天记录始终在内，`DEFAULT_SELECTION` 由它生成）；`BackupManager.createBackup` 去掉 `includeDatabase`，始终写数据库快照，写进 settings.json 的 `items` 也先归一；`stageRestore` 同样去掉该参数，并改成先列条目再解压：`planRestoreEntries` 在第一个字节写入之前判定，缺 `rikka_hub.db` 抛 `BackupRejectedException(MISSING_DATABASE)`、缺 `settings.json` 抛 `MISSING_SETTINGS`；`upload/` 下的附件解压时按 SHA-256 改名并去重，新增 `ImportedUploadFiles`（`upload/` 条目直接走 C32 的 `ContentAddressedFiles.write`，名字用 `FileUtils.buildContentFileName`，指纹与命名不另写一套）与 `UploadNames.rewriteDatabase`（从 `sqlite_master` 与 `PRAGMA table_info` 现查表与文本列，逐行把 `files/upload/<名字>` 与 `file:upload/<名字>` 换成新名字，`message_fts` 与影子表跳过；`managed_files.relative_path` 单独处理，先按新路径分组、每组只留 id 最小的一行，再把要改的行分两步落盘（先挪到临时路径）避免改名成链时撞唯一索引），settings.json 的替换复用 `ImportedUploadFiles.rewrite`；新增 `BackupRejectedException` 与两条文案，`ImportExportTab`、`WebDavTab` 通过 `restoreErrorMessage` 选文案；两页的「备份内容」只列 `SELECTABLE`，WebDAV 的持久化取值在读写两侧都过 `normalize`。测试：JVM 的 `ImportedUploadFilesTest`（五条）与 `BackupItemTest`（三条）；androidTest 的改名去重用例与三条拒绝用例（缺库、缺设置、附件名带反斜杠）；`BackupManagerTest`、`OfficialBackupRestoreAcceptanceTest`、`RestoreAppliedOutcomeTest` 按新规则调整，后两者按「sha256 + 归档里的扩展名」算出 `upload/` 的期望落盘名。
- 理由：官方给每次上传起随机 UUID 名，同一张图发几次就是几个文件；导入侧原来原样落盘，去重只作用于本应用的新写入。文件名由库里的文本列引用，改名必须连带改库、设置与文件登记，所以只有归档同时带数据库时才安全；只带附件的归档恢复出来对不上任何引用，应当在解压之前拒绝。设置与库是一对：库里的会话引用设置里的助手，缺一半会错配。
- 合并注意：改名只做 `upload/`；`skills/`、`fonts/`、`media_creation/` 是自有目录：`skills/` 的子目录名来自 SKILL.md 的 `name`，`fonts/` 与 `media_creation/` 是随机命名；三种都没有按内容去重的历史包袱，改名要连带改工具结果里的引用，收益不值。扩展名取自归档里的文件名并转小写，导入侧拿不到可靠 MIME，同内容不同扩展名会落成两份。归档里附件条目的文件名带反斜杠时在计划阶段就拒绝，别留到 apply 才失败。`managed_files` 是唯一需要先去重的表（`relative_path` 有唯一索引）；`favorites.ref_key` 与 `workspaces.root` 也是唯一索引，但都不存附件路径。扫描跳过 `message_fts*`：索引里只有正文，恢复之后本来就要重建。顺序是转换、迁移、再改名、最后 settings，因为转换器先把安装前缀换成本机，改名作用在换过前缀的文本上。`PendingRestore.buildRemovalEntries` 的「只导附件」分支现在只剩更早版本留下的 pending 目录会走到。持久化的 `webDavConfig.items` 里每一项的含义没变，但聊天记录不能再关掉，旧值由 `normalize` 补上；`normalize` 幂等，解码与 DataStore key 都不动。相关记录同步在 [`backup/README.md`](backup/README.md)、[`backup/importer.md`](backup/importer.md) 与 [`backup/acceptance.md`](backup/acceptance.md)。

### C41 备份与恢复按阶段报进度，六个入口互斥

- 落点：新增 `data/sync/BackupProgress.kt`（`BackupStage` 与 `BackupProgress`，`FILES`、`WORKSPACES`、`EXTRACTING` 按条目计，`DATABASE` 段 `total` 恒为 0）；`BackupManager.createBackup` 在清点、写数据库、附件、工作区四处上报，`stageRestore` 在解压与校验数据库两处上报；`WebDavSync.prepareBackupFile` / `restoreFromLocalFile` 透传 `onProgress`；`BackupVM` 增加 `exportProgress`、`restoreProgress` 与 `operation`（`BackupOperation` 六种入口，`claim`/`release` 用 `compareAndSet` 抢占，选文件的系统面板打开期间也算占用，进度在 `finally` 里清空）；`ImportExportTab` 把进度行放进触发操作所在的分组，数据库段给不确定动画，附件与解压段给 `done / total`，写目标文件与读源文件两段沿用不确定动画；`WebDavTab` 的备份按钮、恢复列表与 `BackupFilesSheet` 用同一个锁；`BackupComponents` 新增 `backupProgressItem`，确定进度的 `LinearProgressIndicator` 传 `drawStopIndicator = {}`，去掉 material3 默认在轨道末端画的停靠点。文案：新增 12 条（分组标题 2 条、依赖说明 1 条、拒绝 2 条、导出阶段 4 条、导入阶段 3 条），删掉 `backup_page_export_packing`，八份 locale 同步。
- 理由：原来只数 zip 条目，数据库快照与官方出口的整库改写挤在同一次 tick 之间，100 个附件时进度条停在 1% 很久，看起来像卡住；导入只有图标转圈，完全没有进度条。六个入口各管一段状态：导出期间能点导入，本地与 WebDAV 互不相干，并发导入要等对方跑完才发现 pending 已存在。
- 合并注意：`BackupProgress` 是导出与导入共用的类型，WebDAV 页目前不显示进度，只共用锁。`operation` 在 picker 打开之前就占据，取消 picker 时释放；picker 打开期间进程被回收时，回调里按 `operation == null` 补一次 `claim`；协程被取消时 `finally` 会释放并清空进度。`drawStopIndicator` 是 material3 1.5 的参数，升级 material3 时如果签名变化按本行改。进度文案里的 `%1$d` / `%2$d` 是条目数，改文案时八份 locale 的占位符要一致。

### C42 备份页按本应用与官方 RikkaHub 分组

- 落点：`ImportExportTab` 拆成三个 `CardGroup`：备份内容（多选与一行说明）、本应用（导出到文件、从文件导入）、官方 RikkaHub（导出给 RikkaHub、从官方导入），进度行跟着运行的操作走；`backupContentItem` 去掉标题行，按钮组下方固定一行说明（聊天记录与设置始终包含，附件与工作区文件可选）；新增 `backup_page_group_app` 与 `backup_page_group_official` 两条文案，八份 locale 同步。
- 理由：官方兼容导出原来放在本应用那一组、从官方导入单独一组，同一个互操作方向被拆到两处；分组标题又和导出入口的标题重复。
- 合并注意：官方那组的两条既有文案（`backup_page_export_official`、`backup_page_import_from_official`）保留，它们是 `docs/custom/locale.md` 品牌检查里放行的条目。`CardGroup` 的 `title` 是普通参数，分组标题不在 `backupContentItem` 里，两个 tab 共用同一份组件。

### C43 错误卡片默认保留到手动关闭

- 落点：`DisplaySetting` 新增 `autoDismissErrorCards`（默认 `false`，即保留）；`ErrorCardsDisplay` 与 `ErrorCard` 增加 `autoDismiss` 参数，`LaunchedEffect` 只在开启时 `delay(5000)` 后关闭，`error.id` 与 `autoDismiss` 都进 key；`ChatList` 把 `settings.displaySetting.autoDismissErrorCards` 传进去；`SettingPreferencesUIPage` 的「显示 Token 和上下文统计」下方加一个开关；8 份 locale 新增 `setting_display_page_auto_dismiss_errors_title` 与 `_desc`。既然不再自动消失，同时把错误列表的会话口径补齐：`ChatError.conversationId` 与 `addError` 的入参都改成非空，`ChatVM.errors` 只放出当前会话的错误，压缩失败那处补上会话 id，删除会话时连带清掉它的错误；`ChatService.addError` 交给 `withNewError`（标题、会话、异常类型与消息都相同的算同一条，只留最新一份，每个会话各留 `MAX_RETAINED_ERRORS` 条），`clearErrors` 也只看当前会话，别的会话的卡片不会被清掉或挤掉；卡片栈限高 320dp 且可滚动，来了新错误滚到最新一条，「全部清除」移到最后。
- 理由：错误卡片原来固定 5 秒后收起。网络中断常常发生在用户离开手机的时候，回来看不到错误卡片，只看到半截回复，无法判断是模型断了还是自己停的。默认保留，靠卡片右侧的关闭按钮或多条时的「全部清除」收起；想恢复旧行为可以在 UI 设置里打开。卡片常驻之后，原来被 5 秒掩盖的两个问题会暴露出来：错误是应用级状态、聊天页没有按会话过滤，以及每轮自动起标题、工具能力提醒、用户重复同一个操作都会把同一类失败再报一遍，所以一起处理。新增的设置字段带默认值，改完按约定重跑了验收样本：`data.sync` 包的 51 条设备用例（含官方样本表数据、整包恢复、applied outcome）全部通过。
- 合并注意：默认值 `false` 与上游行为相反，上游改 `ErrorCard` 时要把 `autoDismiss` 参数一起合过来。会话过滤放在 `ChatVM` 而不是 `ChatList`：Web 端 SSE 与悬浮窗本来就是按 `conversationId` 过滤的，`ChatVM` 这一层补齐后聊天页与它们一致；`ChatError.conversationId` 非空，新加错误入口时不会再漏。滚动在最新一条变化时做一次（按 `errors.lastOrNull()?.id` 起效，等一帧布局完再滚），也就是来了新的一条或最新那条被关掉；关掉中间的卡片、旋转这类高度变化不再动滚动位置。这个开关只管聊天页的错误卡片，悬浮窗（`ChatFloatingContent`）用 `ChatError` 自己画状态徽标，不经过这里；`WorkspaceDetailPage` 里的 `ErrorCard(String)` 是另一个同名私有函数，不受影响。

### C44 上下文压缩的提示词可编辑

- 落点：压缩指令从 `CompressPrompt.kt` 里的硬编码文本改成 `Settings.compressPrompt`（默认 `DEFAULT_COMPRESS_PROMPT`），`buildCompressPrompt(prompt, additionalPrompt, locale)` 先用 `applyPlaceholders` 把 `{locale}` 换成系统语言，再追加本次的额外指令；`ChatService.startCompression` 的压缩轮次从设置里取模板；DataStore 侧新增 `SettingsStore.COMPRESS_PROMPT`（key `compress_prompt`）并登记到 `SETTINGS_FIELDS`，`SettingsFieldsTest` 的样本、盘上 key 集合与断言同步；`SettingModelPromptPage` 的 `PromptSlot` 增加 COMPRESS 一项（图标、变量说明、重置默认），模型设置页的「提示词」里自动多一条；压缩对话框在「额外指令」下面加一行提示基础指令的位置；8 份 locale 新增 `setting_model_page_prompt_compress`、`setting_model_page_compress_prompt_vars`、`chat_page_compress_prompt_hint` 三条文案。测试：`CompressPromptTest`（默认模板替换语言、追加额外指令、空白额外指令不追加）与 `SettingsFieldsTest`（字段契约、读写往返、盘上 key 集合）。
- 理由：总结结构不合适时只能改源码，入口只有每次压缩时的「额外指令」，一次性而且不好找。压缩用的模型、请求组装与正常对话一致，模板本身是可以按使用习惯调的，和标题、翻译、OCR 的提示词放在同一处管理更合理。新增的设置字段带默认值，改完按约定重跑了验收样本：`data.sync` 包的 51 条设备用例（含官方样本表数据、整包恢复、applied outcome）全部通过。
- 合并注意：`{locale}` 是唯一变量，默认模板与原来的硬编码内容等价（只把 Kotlin 插值换成占位符），旧设置解码时用默认值。模板不经过输入转换器（模板转换器会把花括号当模板语法），只在 `buildCompressPrompt` 里拼；压缩请求的前缀要与正常对话一致才能命中提示词缓存，所以模板只能作为末尾追加的那条 user 消息，不要塞进 `buildStep` 的转换链。`Settings` 新增字段必须同时登记到 `SETTINGS_FIELDS`，否则不会落盘：读写只走登记过的字段，`SettingsFieldsTest` 的第一条契约用例就是拦这个的。

### C45 文案范围收成四份

- 落点：删除 `app`、`search`、`oauth`、`ui` 四个模块的 `values-ar`、`values-de`、`values-ko-rKR`、`values-ru`（16 个目录）；`app/build.gradle.kts` 的 `localeFilters` 改成 `en`、`ja`、`zh`、`zh-rTW`；`tools/i18n/config.yml` 的 `languages` 只留 `values`、`values-zh`、`values-zh-rTW`、`values-ja`，`tools/i18n/README.md` 的示例同步；补上 `values-ja` 与 `values-zh-rTW` 缺的三条压缩上下文文案（`chat_page_compress_keep_recent`、它的描述、`chat_page_compress_not_enough_messages`）；把中文里译成「令牌 / 權杖 / 詞元」的地方统一回 `Token`（`chat_page_compress_context_desc`、`search_detail_max_tokens`、`search_detail_max_tokens_per_page`、`stats_page_cached_tokens`、`stats_page_input_tokens`、`stats_page_output_tokens`）；`docs/custom/locale.md` 按四份重写。
- 理由：`tools/i18n` 用 Bun，本机跑不起来（没有 bun 与 `.env`），新文案只能手翻，8 份时每条要翻 7 次，四份只要 3 次；德、阿、韩、俄不在目标范围，繁中保留阅读习惯所以没删 `values-zh-rTW`。顺带清掉了上游自己没翻的三条压缩文案的缺条目债。
- 合并注意：没有目录的语言回退到英文，繁中命中 `values-zh-rTW`。`localeFilters` 与资源目录要一起动：它是允许列表，不在列表里的语言资源不会打进 APK（包括 app 自己的），但仓库里留着的目录不会被它删掉。上游给被删语言补的翻译，移植时不用再抄；以后要恢复某个语言，从上游对应版本拷 `strings.xml` 再补本分支的 key。清单里历史条目写的「8 份 / 7 个 locale」指当时的份数，不改写。把 `tools/i18n` 重写成 Python 记在 [`roadmap.md`](roadmap.md) 的待办方向。

### C46 管理上下文：压缩与截断共用一个入口

- 落点：`FilesPicker` 的「压缩历史」入口改成「管理上下文」并永远显示（去掉 `contextMessageLimit <= 0` 的限制）；`CompressContextDialog` 换成 `ContextManagementDialog`，两个 tab「压缩」「截断」，两处「保留最近 N 条」都用数字输入框 + ±（压缩原来只有 ±）；新增 `ChatService.truncateHistory` 与 `ChatVM.handleTruncateHistory`，边界由 `Conversation.insertHistoryTruncation` 插入一条空的检查点消息（`isContextCheckpoint = true`，空文本会被 `isValidToUpload` 过滤，不发给模型）；`TemplateTransformer` 跳过空检查点，否则模板里的角色标签之类的字面文本会把它变成非空消息发出去；窗口起点抽成 `Conversation.contextWindowStart()`，压缩、截断与对话框都按它算（`limitContext` 在 ai 模块里按同样的规则取最后一个检查点）；`ChatMessageContextCheckpoint` 按空文本区分截断标记，只留删除（撤销）；截断面板实时显示窗口大小、保留条数与将丢弃的条数，第一条保留消息是 assistant 时给 `TriangleAlert` 警告（只提示，不阻止确认）；截断前先清掉失败压缩留下的 scratch 对，窗口按清理后的节点算，`FilesPicker` 的消息计数也不含这对；四份 locale 新增 18 条文案，删掉不再使用的 `chat_page_compress_context` 与 `chat_page_compress_context_title`。测试：`ConversationHistoryTruncationTest`（空标记的插入位置、不发给模型、`limitContext` 起点、窗口起点；锚点不存在时不插入；空检查点与摘要可区分；`Conversation.planHistoryTruncation` 的截断先清掉压缩对、keep 计数不含对、只剩对时 NothingToCut、keep 不小于窗口时 KeepTooMany）与 `TemplateTransformerTest`（空检查点不参与模板改写，摘要照常参与）。
- 理由：用户已经用正常对话让模型写好交接摘要时，再调一次模型压缩是多余的；截断零成本、零延迟，也允许用户自己挑边界。两个动作共用「保留最近 N 条」的概念，放在同一个入口更顺。空标记复用了检查点机制，没有新增序列化字段，官方兼容导出的消息 JSON 不变。
- 合并注意：截断计划抽成 `Conversation.planHistoryTruncation`，服务层只映射错误文案，测试直接打这个纯函数；截断必须先清掉压缩 scratch 对再算窗口和锚点，否则检查点会插进对中间，下一次清对只删得掉提示词；空文本是截断标记的唯一判据，这个不变式靠 `streamCompressionSummary` 里的 `ifBlank { error }` 兜底（`compressionSummaryOrNull` 本身只做 trim，单看它拦不住空串）；以后如果允许把检查点编辑成空白，语义会退化成截断，与它的效果一致。`limitContext` 只认最后一个检查点，连续截断两次时旧标记留在历史里但不再生效；在前一个压缩摘要之后做截断，那个摘要也不再是请求起点，所以删除标记的提示写的是「回到之前的边界」而不是「恢复全部历史」，旧检查点的卡片也会显示「已被更新的检查点取代」。`contextWindowStart`、`contextWindowSize` 同时给服务端与对话框用，别在两处各算一遍。Web 端已经有 `isContextCheckpoint`（WebDto），会把空标记渲染成一张空的摘要卡片并显示编辑；要处理时改 web-ui 的分支，Android 这边按空文本区分。

### C47 上下文消息数上限的自动边界线

- 落点：`ai/Message.kt` 新增 `contextLimitStart(limit)`，按与 `limitContext` 相同的取法（最后一个检查点之后、同一套滞回与 `alignContextStart` 对齐）算出第一条被保留的尾部消息下标（提供方之后还会按 `isValidToUpload` 过滤空消息），`limitContext` 本身不动；`Conversation.contextLimitBoundaryIndex(limit)` 在起点严格晚于检查点之后的窗口起点时返回下标，也就是真的发生了条数截断才返回非空，最后一条是助手回复时先去掉它再算（否则线会落到回复上方把最后一条 user 划出窗口），压缩 scratch 对存在时返回 null；`ChatList` 用 `settings.getAssistantOf(conversation)`（与请求同一来源，助手被删也能退回当前助手）的 `contextMessageLimit` 取上限，在第一条被保留的消息上方画一条只读虚线，文案 `chat_page_context_limit_boundary`（四份 locale）说的是「请求窗口从这里开始」，因为线上方的检查点卡片（压缩摘要）仍会发送；不落盘，不加设置。测试：`MessageContextLimitTest` 五条（起点与 `limitContext` 的尾部一致、检查点之后有截断时的起点、未截断时停在检查点之后、检查点在末尾、按检查点之后算而不是按整个列表）与 `ConversationContextLimitTest` 八条（无上限或未截断时不返回下标、只有检查点边界不画线、无检查点截断时的起点、检查点之后的起点、最后一条助手回复不参与窗口、带回复时仍守检查点、带回复且不截断时不画、压缩 scratch 对不画）。
- 理由：消息数上限只在组装请求时算，界面完全看不见；用户一直手动管理上下文，这条线能把自动被丢掉的那段标出来。它按滞回规则跳动、连续多轮不动，不破坏提示词缓存；最后一条是助手回复时按去掉它的列表算，标出产生这条回复的请求窗口。多步工具/续写轮次的最后一步请求可能包含这条助手消息，边界仍可能与那一步差一个滞回步幅；压缩 scratch 对是界面临时对，正常请求不用这对节点，压缩提示词存在时不画线。
- 合并注意：`contextLimitStart` 必须复用 `limitContext` 的取法，别另写一套条数逻辑；边界按消息下标定位，检查点卡片本身不算条数，也不能画在检查点上方（摘要会发送）。上限为 0 或没有发生条数截断时不画。重新生成中间节点时请求只取到该节点之前，边界可能比该次请求多显示一段；重新生成最后一条时按去掉新回复算。Web 端不显示这条线。

### C48 版本号随提交自动滚动

- 落点：`app/build.gradle.kts` 用 `providers.exec` 读 `git rev-list --count HEAD` 与 `git rev-parse --short=7 HEAD`，`versionCode = 194 + 提交数`，`versionName = "3.1.1-custom.<提交数>+<短 SHA>"`，不加 dirty 后缀。git 命令执行失败（例如不在仓库里）时退回 `194` / `3.1.1-custom`；git 可执行文件本身不存在时会在配置阶段失败，配置缓存无法把进程启动失败当作可回退的输入值，构建环境需要 git。已用 `aapt2 dump badging` 核对构建里的 `versionCode`/`versionName` 与当次提交一致，并验证 Gradle 配置缓存会跟踪该外部命令输出（输出变化时缓存失效）。
- 理由：崩溃日志与安装包只看得到 versionCode/versionName；之前所有构建都停在 3.1.1/194，分不清是哪次提交。每日构建与本地构建共用同一套规则，短 SHA 直接对上。
- 合并注意：上游改 `versionCode`/`versionName` 时同步脚本里的 `upstreamVersionCode`/`upstreamVersionName`；提交数在 reset/rebase 改写 main 历史后会倒退，versionCode 随之下跌；merge 只会让计数变大、不会回退，只在 main 上构建（ref 的提交数少，构建出来会是更低的 versionCode）；本地浅克隆（depth-1）会把提交数算成 1、versionCode 退到 195，需要完整历史；checks 与 daily-build 的构建 job 是 `fetch-depth: 0`，daily-build 的 check job 是 depth 1（只查提交，不打包）。版本名不只用于显示与崩溃日志，`BuildConfig.VERSION_NAME` 还是默认 HTTP User-Agent（`DataSourceModule.kt` 与 `SettingPreferencesNetworkPage.kt`），会随提交变化。

### C49 分支切换只写 select_index

- 落点：`MessageNodeDAO.updateSelectIndex(conversationId, nodeId, selectIndex)` 带 `conversation_id` 条件并返回受影响行数；`ConversationRepository.updateMessageSelectIndex`；`ConversationStore.updateNodeSelectIndex`；`ConversationSession.selectMessageNode(nodeId, selectIndex, expectedIncarnation)` 在写锁内只改内存，且只有最近一次成功写入后的持久化计数（`persistedMessageCounts`）显示该节点已有这个下标时才写列，否则留给下一次全量 `flush` 把消息和下标一起写；`ChatService.selectMessageNode` 改走它，保留 `dispatchNextQueuedMessage`；载入时把 `selectIndex` 夹紧到消息下标范围（`loadMessageNodes` 与 `conversationEntityToConversation` 两处）。测试：`ConversationSessionTest` 新增两条（持久化计数包含下标时只写列、随后 `flush` 不重写；计数不足时只改内存、下一次 `flush` 把消息和下标一起写）。
- 理由：分支切换原来走 `mutate` → 全量 `flush` → 删除并重插所有 `message_node`，会把巨长回复重新编码，OOM 就发生在这次编码上；切换分支实际只改一个整数列。守卫防止「节点行还是旧的分支数量」时写进越界下标（重新生成较早回复、Web 路由都能触发），否则下次加载 `currentMessage` 会抛异常。
- 合并注意：写列的前提是 `persistedMessageCounts[nodeId]` 大于目标下标（C50 之后由单节点写和全量写共同维护）；内存里有新分支但还没落库时只改内存，下一次全量写补上。写列成功后只在 `lastWritten === before` 时推进 `lastWritten`，避免把并发的未落库修改误标成已写。载入 clamp 是坏数据兜底。Web 路由共用同一个 `ChatService.selectMessageNode`。生成中定期落库见 C50。

### C50 生成中定期保存回复节点

- 落点：`MessageSaveLoop` + `MessageSavePolicy`（5/15/30 秒档、64KB 增长、60 秒兜底、堆守卫 `max(16MB, 4×估算)`、估算超过 16MB 的节点跳过并每分钟记一次日志）；估算逐层累加 `JsonElement` 的 primitive 长度，不调 `toString()`；`估算字节 = 字符数 × 2 + 1MB`，是 UTF-8 的近似值（ASCII 偏保守，CJK 偏小），所以 16MB 上限是估算值，不是硬行大小；`ConversationSession.persistMessageNode` 在写锁内按追加规则写单个 `message_node`（新节点必须在末尾且前缀 id 一致，已有节点下标必须一致），副本先 `finishReasoning`，含递归 base64 图片则跳过，不推进 `lastWritten`，成功后更新 `persistedMessageCounts`；`persistedMessageCounts` 在载入、全量 flush、replace 时重建，C49 的 select_index 守卫改用它；`touchUpdateAt` 每轮第一次成功保存时同步内存与 `update_at` 列；`ConversationRepository.upsertMessageNode` 与 `ConversationDAO.updateUpdateAt`；`ChatService.runTurn` 用 `supervisorScope` 起 saver，生成结束的 `finally` 里 `NonCancellable { saver.cancelAndJoin(); finishGeneration() }`；`withoutUnresumableToolCalls` 造成节点结构变化时先 `flush()`；翻译流式只改内存，结束后单节点写一次，清除翻译也走单节点写；编辑、分叉、删除在 `job != null || isAwaitingToolApproval` 时抛 `ConflictException`（Web 409），Android 页面预挡、VM 捕获。
- 理由：原来只在发送、审批、turn 结束和手动修改时全量落库；长回复生成期间崩溃会整条消失。定期保存只编码当前节点，配合 C49 把长回复的保存成本从「每次整条会话」降到「当前节点」。
- 合并注意：追加规则避免写到错误的节点下标；漂移、base64、堆不足或估算超过 16MB 时跳过，交给最终全量 flush。落库返回空（漂移/base64）和节点过大时会推进计时，堆守卫不足时只跳过本次判断、不拷贝节点。单节点写不推进 `lastWritten`，全量 flush 仍是最终校正。tick 整体兜异常，取消异常必须重抛；写失败后退避 10 秒。保存的内容可能缺少 `onGenerationFinish` 的最终转换，`modelSnapshot` 可能为空，均接受。工具输出里的 base64 图片比库的顶层检查更保守，会让该节点整个 turn 跳过定期保存，这是有意的取舍。`replyNodeId` 在连续 assistant 节点时可能指向前一个 assistant 节点，这是既有边界。发送、审批、停止、turn 结束、fork 时的全量写仍会编码巨长节点，可能 OOM；翻译结束、清除翻译的单节点写同样会编码所在节点；超过 16MB 估算的节点不做定期保存，崩溃会丢整条回复；待审批期间的压缩/截断、生成中删除会话也一样。saver 与生成协程的 `session.update` 之间没有锁，保存的是写锁内的快照，下一次全量 flush 校正。忙碌检查是 check-then-act，Android 的页面预挡是主防线，竞态下编辑输入可能丢失。Web UI 在生成中禁用按钮并提示 409 是后续项。
- 测试：`ConversationSessionTest` 40 条，新增追加、漂移、前缀不一致、计数守卫、正向分支写、base64（顶层与工具输出）、`finishReasoning`、`touchUpdateAt`、草稿跳过；`MessageSaveLoopTest` 15 条，覆盖档位（按字节而非字符）、增长、最长 60 秒、堆守卫、16MB 边界、空跳过不重试、超限日志节流、失败退避、估算异常后恢复、取消异常重抛、估算函数逐层求和。两个未自动化的缺口：saver 与 `finishGeneration` 的 join 顺序没有集成测试（靠代码审查确认），409 门禁没有 `ChatService` 层测试（Android 页面预挡与 VM 捕获已编译验证）。设备验收待做：巨长生成中杀进程后重启能看到已保存的部分。

### C51 压缩走可见生成轮次

- 落点：`ChatService.startCompression` 在 `synchronized(session)` 里检查 job/待审批并设置 `compressionActive`，失败返回 `Result.failure`，job 不启前台服务；`runCompressionTurn` 先用 `Conversation.planCompression` 在插入提示词之前钉死 `messagesToSend`/锚点，再插提示词和空回复并 flush，起 per-turn saver，成功用一次 `ConversationSession.flushWith(incarnation)` 完成「检查点 + 删提示词/回复 + updateAt」，失败/取消保留这对节点并 `finishGeneration`；`UIMessage.isCompressionPrompt`（带默认值的序列化字段），`editMessage` 保留标记，`removeCompressionPair(s)` 一次删一对，`sendQueuedMessage` 前和压缩计划前排除旧 scratch，prepare 再清最新状态；`gates`：压缩期间拒绝重新生成、工具审批、分支切换和起标题；`ChatRequestFactory.streamCompressionSummary` 与正常对话同前缀（保留工具定义、`maxTokens = null`、提示词最后追加），`withRetry` 包住收集、每次重试重置同一 message id、捕获 `StreamChunk.Finish.finishReason`，截断按归一化字符串集合判失败；`ChatVM.handleCompressContext` 改为 `startCompression` 的 Result 处理，`deleteCompressionPair`；对话框 `onCompress` 改成 `() -> Unit` 并点开始即关；`ChatList` 跳过紧随提示词的回复节点，由 `CompressionCard` 一张卡承载折叠头/展开区/停止/重试/删除，失败默认展开；`ChatPage` 串起重试（删对后重开管理上下文对话框）；四份 locale 加卡片与取消/截断文案。旧的 `compressConversation` 与 `generateCompressionSummary` 删除。
- 理由：压缩原来是一次隐藏的非流式请求，用户看不到提示词和摘要怎么生成，也不能取消；改成可见轮次后流式、可取消、错误走现有卡片，失败不截断历史。请求前缀保持不变，提示词缓存仍然命中；工具定义保留，模型返回工具调用按失败处理，不执行工具。
- 合并注意：`planCompression` 必须在插入可见提示词之前算，请求只用计划里的消息，否则提示词会被发两遍或吞掉保留消息。检查点按锚点插在保留区之前，可见提示词的位置只是 UI；成功是 prepare + finalize 两次全量写，失败是 prepare + finishGeneration 两次，旧节点仍在树里，每次都会编码保留区。压缩期间的分支切换、发送、队列派发都被挡；发送、重新生成、截断、分支切换、标题生成目前各自判断能否开始（谓词不统一，统一留到下次改 ChatService，`CompressionPlan` 的待审批/可续跑用例已补）；失败后下次真实发送或截断会先清 scratch 对，不会把提示词当成普通历史。崩溃在截断前会留下提示词和部分回复，重启后没有检查点，用户可重试/删除；截断后的崩溃检查点已落库。临时换模型会改变请求前缀、失去缓存命中，入口留在对话框并说明。Web/SSE 没有压缩流，浮动窗口和导出把临时对当普通消息。
- 测试：`CompressionPlanTest` 9 条（计划范围/空范围、检查点插入与删对、缺回复的清理、普通消息不误删、截断 finish reason 归一化、待审批工具、可续跑的已批准工具、待审批工具只在保留尾部时仍 Ready）；`ConversationSessionTest` 增加 `flushWith` 两条（变换落库并推进快照、incarnation 不匹配不写）；`ChatRequestFactory` 的流式请求依赖已有 `compressionSummaryOrNull` 的守卫。设备验收待做：真实压缩的流式显示、停止、失败重试/删除、成功后的检查点；`data.sync` 51 条不受影响的确认。

### C52 Claude 缓存断点按官方标准收敛

- 上游引入：`6098e1d3`（2026-02-24）先在请求顶层加 `cache_control`；`aede7f45`（2026-02-26，#877/#880）改成 system、最后一个 tool、倒数第二条真实 user 三处手动断点；`d2e52106`（2026-05-27）又在手动断点上加顶层自动缓存，一共 4 个，顶满 Anthropic 的上限，没有余量。长思考或中转再补一个就报 `A maximum of 4 blocks with cache_control may be provided. Found 5.`，这是断点顶满上限的来源。
- 落点：`ClaudeProvider` 只保留顶层自动缓存和 system 最后一个文本块的断点；没有 system 时保留最后一个工具定义的断点；删掉 `insertMessagesCacheControl` 和倒数第二条 user 的断点（这两条描述的是 STANDARD；C53 起默认 AGGRESSIVE，激进模式会恢复它们，见 C53）；回放 server tool 的 raw call/result 时剥掉 `cache_control`；构造完请求统计断点，超过 4 去掉 messages 里的断点并记日志；更新 `ClaudePromptCacheRequestTest` 断言（正常 2 个断点），修 `ClaudeRequestMessageTest`/`ClaudeServerToolTest` 里 `buildMessages` 的反射签名。
- 理由：官方 prompt caching 文档的 agent loop 推荐组合是「system 显式断点 + 顶层自动缓存」；工具在 system 之前渲染，system 断点已经覆盖 tools + system，工具断点是冗余的；倒数第二条 user 的断点也被顶层自动断点覆盖。线性对话的增量缓存仍然成立，STANDARD 下缓存写入点从 4 个减到 2 个；分支/编辑历史时中段复用会略少，静态前缀不受影响。顶层自动缓存依赖平台支持，官方当前模型支持；代理或旧平台不支持时会直接报错，这是上游原来就有的前提。
- 合并注意：这是 `ai` 模块的自定义行为改动，上游改 `ClaudeProvider` 时按这个标准合；provider 的单元测试要跑 `:ai:testDebugUnitTest`，app 的测试任务不会覆盖它们。自定义 body 如果自带 `cache_control`，护栏只保证 messages 里的断点被去掉，其它位置需要用户自己控制。设备上用长思考场景复现，400 消失；该次测试中转后台显示 cache_read=0，缓存命中是否稳定待观察。C53 把请求构造改成按 provider 切换，默认 AGGRESSIVE；C52 的两断点是 STANDARD 选项。
- 测试：`ClaudePromptCacheRequestTest` 的标准策略 7 条（自动 + system、无 system 时用 tools、无 system 且无 tools 时只有自动断点、只有一条 user 时没有 messages 断点、1h TTL 在两处、messages 永远没有断点、promptCaching=false 没有断点；C53 补激进策略 6 条后共 13 条）；`ClaudeRequestMessageTest` 7 条、`ClaudeServerToolTest` 10 条全过；`ai` 模块的测试任务通过。

### C53 Claude 缓存断点按提供商切换

- 落点：`ProviderSetting.Claude.promptCacheStrategy`（`ClaudePromptCacheStrategy.STANDARD`/`AGGRESSIVE`，默认 `AGGRESSIVE`）；`ClaudeProvider.buildMessageRequest` 在 STANDARD 下是顶层自动缓存加 system（无 system 时 tools），在 AGGRESSIVE 下是顶层加 system 加 tools 加倒数第二条真实 user，回到上游 `d2e52106` 的 4 个断点；`ProviderConfigure` 在提示词缓存开关下加策略选择，选项只显示标准/激进，策略标题行右侧的 (i) 打开说明弹窗，关闭复用 `common_confirm`；四份 locale 的选项改成标准/激进，并补说明和 info 文案；C52 的 `limitCacheControl` 与 server tool raw 剥离继续生效。
- 理由：上游原来的激进策略用了很久，只在个别中转补断点时遇到一次 Found 5；默认保留激进能继续拿到更多缓存断点，遇到 400 的 provider 手动切标准即可。标准是官方推荐的两断点组合，给中转补断点留余量。策略是 provider 级，不是助手级，切换后同一 provider 的所有 Claude 请求生效。
- 合并注意：新字段带默认值，旧设置反序列化为 AGGRESSIVE，升级后与 C52 之前的行为一致；STANDARD 是 C52 的结果，AGGRESSIVE 是上游 `d2e52106` 的原始组合；两个模式都保留顶层自动缓存，STANDARD 只有在没有 system 时才给 tools 断点；改上游 ClaudeProvider 时按这个分叉合。
- 测试：`ClaudePromptCacheRequestTest` 标准策略 7 条加激进策略 6 条（默认激进 4 断点、三条 user 时只标中间一条、无 system 时 3 断点、无 system 且无 tools 时 2 断点、只有一条 user 时 3 断点、1h TTL 在四个断点），共 13 条；`ClaudeRequestMessageTest` 7 条、`ClaudeServerToolTest` 10 条仍过。

### C54 移除关于页图标点击彩蛋并清理相关死代码

- 落点：`SettingAboutPage` 删掉 logo 点击的 emoji 喷射、音效、形状变形、按下缩放和坐标跟踪（去掉 `EmojiBurstHost`、`SoundEffectPlayer`、`logoMorphShape`、`graphicsLayer`/`onGloballyPositioned`），保留静态的 `rememberAppLogoLayers` 绘制并加 `clipToBounds`（图层按 108dp 画进 136dp 盒子，不裁会溢出；C55 起改成独立的 about_logo），版本号长按进 Debug 保留；同时删掉 `utils/SoundEffectPlayer.kt`、Koin 绑定、`res/raw/bingbingbing.mp3`/`gangguan.mp3`，以及没有调用方的 `ui/easteregg/EmojiBurst.kt`；`AppLogo.kt` 的 `logoMorphShape` 保留给浮动窗口。
- 理由：关于页只需要静态图标和版本信息；删掉彩蛋后音效播放器、raw 音效和 EmojiBurst 都没有调用方，按 C5 的约定一并清掉。
- 合并注意：`EmojiBurst.kt` 是上游 `ui/` 的文件，删它是为了清死代码；上游改 `SettingAboutPage` 时按这个删。关于页图标由 C55 单独提供，launcher 和浮动窗口仍用 `R.mipmap.ic_launcher`；静态图标用方形裁剪，没有无障碍描述，属于装饰图。
- 测试：编译通过；点击 logo 不再有喷射、音效和变形；版本号长按仍进 Debug；浅色模式下没有深色背景溢出。

### C55 关于页独立图标

- 落点：新增 `app/src/main/res/drawable-nodpi/about_logo.webp`（1254×1240，来自 `rikka_icon.webp`），`SettingAboutPage` 用 `Image` 加 `ContentScale.Crop` 和 `RoundedCornerShape(28.dp)` 绘制，替代原来的 `rememberAppLogoLayers`/`drawAppLogo`；launcher 图标和浮动窗口不变，浮动窗口仍用 `rememberAppLogoLayers`。
- 理由：关于页想用自己的图标，同时不动应用 launcher。
- 合并注意：上游改 `SettingAboutPage` 时按这个；换图直接替换 `about_logo.webp`；C54 的彩蛋移除保持不变。
- 测试：编译通过；关于页显示新图标，launcher 与浮动窗口仍用原图标。

### C56 文档 lint 配置

- 落点：根目录 `.markdownlint-cli2.jsonc`，范围 `docs/**/*.md`、`AGENTS.md`、`README.md`、`terminal/README.md`，忽略 `.agents/`、构建目录和 `node_modules`，关掉 `MD013`/`MD033`/`MD041`/`MD060`；`docs/custom/build.md` 加「文档检查」；`docs/references/chat-generation-pipeline.md` 修列表缩进和代码块语言；`docs/custom/backup/README.md` 补代码块语言。
- 理由：文档一直没有 lint 入口，列表缩进、代码块语言这类结构问题只能靠平时看；用本机已装的 markdownlint-cli2，配置适配仓库的长行、README 的居中 HTML 和紧凑表格。
- 合并注意：这是本地检查，CI 没有跑；规则列表在配置和 `build.md` 两处，改规则时同步。MD060 关掉是因为参考文档的 CJK 表格按字符宽度对齐，默认风格过不了，别按 ASCII 宽度重排；其余结构规则保持开启。markdownlint-cli2 不在仓库依赖里，换机器要自己装。
- 测试：仓库根目录 `markdownlint-cli2` 14 个文件 0 error；参考文档和备份文档的修改只动空白与代码块语言，内容未变。

### C57 请求日志开关持久化

- 落点：新增 `RequestLoggingPreference`（SharedPreferences），`RikkaHubApp.onCreate` 读取并设置 `Logging`，`LogPage` 切换时写回。
- 理由：请求日志开关原来只在 `Logging` 的内存里，重启回到关闭，容易忘记打开；日志本身仍是内存环形缓冲，重启清空。
- 合并注意：用 SharedPreferences 而不是 Settings/DataStore，这是调试开关，不进应用内备份（Android 12+ 的 Auto Backup 可能带上这个布尔值）；不要把日志内容一起持久化，体积和隐私都不合适。日志开关在 `common` 的 `Logging` 对象里，持久化留在 app 侧。
- 测试：编译通过；手动验收：开关打开后重启仍为开启，日志列表清空。

### C58 失败与中断回复状态

- 落点：`UIMessage` 增加 `status`，取值为 `COMPLETE`、`INTERRUPTED`、`FAILED`，默认 `COMPLETE`；`ChatService.runTurn` 通过 `resolveSettlementStatus` 在失败时标 `FAILED`、用户停止时标 `INTERRUPTED`，由 `finishGeneration` 一起落库；`ChatRequestFactory.buildStep` 组装请求前用 `filterModelVisible()` 过滤 `FAILED`，再用 `mergeAdjacentUserMessages()` 合并所有相邻的 user，不只失败过滤造成的那对，保留前一条的 id、createdAt、isSynthetic、isContextCheckpoint，只拼 parts 和 annotations，transforms 在合并之前；`ConversationMetaGenerator` 和 `dialogueMessages()`（记忆整理、`read_conversations`）同样过滤 `FAILED`；`ChatMessageAssistantAvatar` 在模型名旁显示失败/中断标签；新增四份 locale 的 `chat_message_status_failed`、`chat_message_status_interrupted`。
- 理由：失败或停止的半截回复原来作为普通 assistant 消息保存，下一次请求会把它当成完整回复带上。失败 attempt 只应给用户看，不应进入模型历史；用户主动停止的内容用户已经看过，可以继续参与后续对话，但需要和正常完成区分。过滤失败回复后前后两条 user 会相邻，部分 provider 要求 role 交替，所以在请求列表里合并，不改会话本身。
- 合并注意：`status` 是带默认值的序列化字段，旧消息、旧备份和官方兼容导出里的旧 JSON 解码为 `COMPLETE`；这是 `ai/ui/Message.kt` 的分叉，移植上游 `UIMessage` 改动时保留字段以及 `isModelVisible`、`filterModelVisible`、`mergeAdjacentUserMessages`。`runTurn` 在进入 `runAgent` 收集之前失败（包括 `createContext`/`createAgent` 内部失败）时没有本轮回复可标，审批续写会保留原消息状态并只出错误卡片；此时 `replyMessageId` 可能已指向旧回复，但没有结算路径读它。已收到 `AgentFinishReason` 后发生的取消保持 `COMPLETE`。删除、替换、cleanup 的内部取消也可能短暂标 `INTERRUPTED`，随后被替换或删除，用户主动停止是主要路径。`markReplyStatus` 是单向的，不会把 `FAILED` 复位成 `COMPLETE`；当前正常路径不可达，若将来出现“Pending 工具 + FAILED”的续写，需要同时处理过滤与复位。目前按整条消息标记，多步工具调用在中途失败时会整条隔离；per-step attempt 和工具语义（`TOOL_NOT_STARTED`、`TOOL_OUTCOME_UNKNOWN`、`withoutUnresumableToolCalls`、`cancelInterruptedTools`）仍归 P2。压缩请求在 `buildStep` 之后单独追加 compress prompt，仍是 user+user，不走这次合并，这是压缩指令独立成轮的既有设计。Web、导出图片、Markdown 导出和浮动窗口没有 status，只有 Android 主聊天 UI 显示标签；标签在头像行内，模型已删除且没有快照、又未开助手头像时看不到。`contextWindowSize`、`contextLimitBoundaryIndex`、`planCompression` 仍按未过滤消息计算，失败消息夹在中间时边界和压缩计数尚未同步，归 P2。FTS 索引和 `conversation_search` 仍会把失败回复文本带进模型上下文，这次没有处理，是“失败只给人看”的已知例外。
- 测试：`UIMessageSerializationTest` 覆盖状态序列化、旧 JSON 默认 `COMPLETE`、过滤只去掉 `FAILED`；`MessageMergeTest` 覆盖相邻 user 合并和过滤失败回复后的合并；`ConversationMessagesTest` 覆盖状态只改目标消息、无变化返回 null；`SettlementStatusTest` 覆盖完成、失败、取消和完成后再取消；`ConversationSessionTest` 覆盖 `markReplyStatus`；`DialogueMessagesTest` 覆盖失败回复不进入记忆和 `read_conversations`；`ConversationRecentModelMessagesTest` 覆盖标题上下文过滤。命令：`:ai:testDebugUnitTest --tests "me.rerere.ai.ui.UIMessageSerializationTest" --tests "me.rerere.ai.ui.MessageMergeTest"`、`:app:testGithubDebugUnitTest --tests "me.rerere.rikkahub.data.model.ConversationMessagesTest" --tests "me.rerere.rikkahub.service.ConversationSessionTest" --tests "me.rerere.rikkahub.service.SettlementStatusTest" --tests "me.rerere.rikkahub.data.ai.tools.DialogueMessagesTest" --tests "me.rerere.rikkahub.data.ai.ConversationRecentModelMessagesTest"`；`assembleGithubDebug` 通过。本轮没有重跑 README 要求的设备验收样本；`UIMessage` 只增加带默认值的字段，旧 JSON 解码测试通过，设备和样本重跑留到下一批。

### C59 截断流识别与停止结算

- 落点：五个 `StreamChunkDecoder` 的 `onClosed()` 必须见过协议终止事件，否则抛 `IOException`：Claude 要 `message_stop` 或 `[DONE]`（`[DONE]` 是既有行为）；OpenAI ChatCompletions 要 `finish_reason` 或 `[DONE]`；OpenAI Responses 要 `response.completed`、`response.incomplete` 或 `[DONE]`；Google 要 `finishReason` 或 `[DONE]`；Google Interactions 要 `interaction.completed` 或 `[DONE]`。五个 provider 接线点（`ClaudeProvider`、`GoogleProvider`、`ChatCompletionsAPI`、`ResponseAPI`、`InteractionsAPI`）的 `onClosed` 捕获异常后 `close(e)`，`onFailure` 在 `t` 和响应体都为空时用 `IOException` 兜底，不再静默正常关闭。`ChatService.stopGeneration` 把 `jobs.join()` 和 `finishInterruptedPendingTools` 放进 `withContext(NonCancellable)`。
- 理由：传输层正常 EOF 不等于协议完成；原来 `onClosed` 无条件补 `Finish`，代理截断或半截 EOF 会被当成完整回复，不进重试也不报错。停止结算在 `viewModelScope` 取消后可能不执行，pending 工具收尾丢失，`isAwaitingToolApproval` 还可能挡住发送、编辑和删除。
- 合并注意：这是严格判定。中转或旧平台如果不发送终止事件，行为会从“静默成功”变成可重试的 `IOException`；确需兼容时按 provider 加宽松条件，不要全局关掉。重复收尾由各 decoder 的 `finished` 状态保证返回空；`finish_reason`/`finishReason` 是终止信号，不参与幂等。`onClosed` 捕获异常后必须 `close(e)`，不能只 `close()`。`stopGeneration` 的 NonCancellable 只包 join 和工具收尾，不包 UI 状态；发送前的 `finishInterruptedPendingTools` 仍保留作崩溃补偿。工具不响应取消时 join 会继续等待，UI 不阻塞但结算延后；`cancelJobs` 是快照，join 之前新起的 job 不在等待范围内。`spotlessApply` 对 `ai/` 既有格式做过一次重排，无语义变化，移植上游时按语义保留。C58 的失败/中断消息状态与本节独立。
- 测试：`TruncatedStreamTest` 覆盖五个 decoder 的早关抛错、真实终止标记、`[DONE]` 和 delta 后截断；`GoogleToolCombinationTest` 的流 fixture 补上 `finishReason: STOP`，避免半截 fixture 被新规则判成截断；`:ai:testDebugUnitTest` 全量通过。`:app:testGithubDebugUnitTest` 687 条里只有已知的 Windows 7 失败（`SettingsStoreTest` 6 条、`ProviderAccountManagerTest` 1 条），没有新增；`assembleGithubDebug` 通过。NonCancellable 接线没有 JVM 单测，`cancelInterruptedTools` 本身由 `ConversationMessagesTest` 覆盖；手动验收：生成中停止后立即离开页面，pending 工具仍被收尾，发送/编辑/删除不再被 `isAwaitingToolApproval` 锁住。

### C60 审批期间冻结 turn 上下文

- 落点：`ConversationSession.pendingApprovalTurn` 只存在内存；`ChatService.runTurn` 增加 `resumeApproval` 参数，只有 `handleToolApproval` 在所有 pending 工具都处理完后传 true 并复用这份 `ChatTurnContext`，其他入口传 false 并丢弃 stale frozen；本轮以 `AgentFinishReason.AwaitingApproval` 结束时存回，其余结束清空。`selectMessageNode` 在 `isAwaitingToolApproval` 或 `pendingApprovalTurn` 非空时抛 `ConflictException`；`regenerateAtMessage` 在截断、清 scratch、清 frozen 之前用 `ConversationSession.canRegenerate()` 拒绝生成中、审批中和冻结恢复中的重新生成，并报错误卡片；冷 session 在 `ensureInitialized` 之后、清 scratch/截断之前再校验一次待审批和 frozen。`stopGeneration` 没有 job 但有待审批时也会清 frozen 并收尾，`ConversationSession.replace` 清空冻结上下文。
- 理由：审批恢复原来重新 `createContext`，审批期间改模型/provider 会把工具结果和工具 schema 发给新配置；切走后 `isAwaitingToolApproval` 变 false，pending 调用成孤儿，下一次 `withoutUnresumableToolCalls` 会整条删除消息。冻结后配置改动从下一轮生效。regenerate 如果先截断再发现被挡住，会留下半成功历史；用显式 `resumeApproval` 取代“末条消息有没有可恢复工具”的猜测，也避免审批恢复在非末条消息上误判。
- 合并注意：冻结只存内存，进程死亡或会话重建后丢失，审批恢复退化为当时的配置。`runTurn` 在 `createAgent` 之前失败时 frozen 已经被消费，批准状态已经写进消息，审批 UI 不再出现；下一次发送的 `finishInterruptedPendingTools` 会把这条已批准工具写成 `cancelled by user`，不是“用当前配置重跑”，这个边界不要当成可重试审批。分支切换被拒绝时 `ChatVM` 只记日志，Web 路由返回 409；如果要做可见提示，需要在 ChatVM 加文案。审批期间改配置不报错，只影响下一轮；这是与“直接拒绝”不同的取舍。`canRegenerate()` 同时拒绝“有 job 在跑时重新生成更早消息”，这是有意从原来的接管改成拒绝，避免审批恢复被新 job 顶掉。本批同时补了 C59 的 `[DONE]` 用例，登记在 C59，不算 C60 改 `ai/`。
- 测试：`ConversationSessionTest` 覆盖 `replace` 清空 frozen approval turn；`ConversationSession.canRegenerate()` 由同一测试类覆盖正常、待审批和冻结三种结果；`:app:testGithubDebugUnitTest` 687 条里只有已知 Windows 7 失败，`:ai:testDebugUnitTest` 全量通过；`assembleGithubDebug` 通过。手动验收：多工具部分审批、批准、拒绝、回答各走一遍；审批中改模型/provider 再批，工具结果仍发给原配置；审批中切分支被拒；审批中 regenerate 被拒且历史不变；停止（审批态 App 和浮窗都没有停止按钮，只有 Web `/stop` 可验证）后 frozen 清空且 pending 工具收尾。

### C61 结构化失败与重试策略

- 落点：`ErrorParser.kt` 新增 `LlmFailureCode`、`LlmFailure`，`HttpException` 带上 code、HTTP status 和 `Retry-After`；`toHttpException`、`withResponse` 负责从响应归一化。五个聊天 provider 接线点（Claude、ChatCompletions、Responses、Google generateContent、Interactions）的非流式非 2xx 改抛 `HttpException`，流式 `onFailure` 的解析错误附加响应状态，空响应体也保留状态。`RetryPolicy` 改为按错误码允许列表重试，支持 `maxDelay`、10% jitter 和 `Retry-After`；`AgentLoop` 在流式和非流式路径把空回复包成 `EMPTY_RESPONSE`，进入同一重试循环。
- 理由：原来只按 `IOException` 判断，HTTP 429/5xx 的 JSON 错误大多不重试；空回复直接以完成状态落库；重试固定 1s/2s/4s，没有 jitter，也不看服务端 `Retry-After`。
- 合并注意：默认只重试 `RATE_LIMIT`、`SERVER`、`TIMEOUT`、`TRANSPORT`、`EMPTY_RESPONSE`；`AUTH`、`QUOTA`、`INVALID_REQUEST`、`CONTEXT_WINDOW_EXCEEDED` 不重试。`RetryPolicy` 的 `retryOn` 仍是可选的逃生口，自定义策略优先于码表。`Retry-After` 会被 `maxDelay` 截断，不做“服务端要求超过上限就放弃”的区分；jitter 固定 10%，测试用 `jitterRatio = 0`。空回复判定只把空白 Text/Reasoning 当空，Tool、图片、文档等 part 都算有内容；这会让某些本来静默成功的空回复变成重试后错误，是预期行为。结构化错误优先于 HTTP status，`insufficient_quota` 和嵌套 `QuotaFailure` 即使是 429 也归 `QUOTA` 且不重试；没有 `QuotaFailure` 的 Google `RESOURCE_EXHAUSTED` 429 仍是 `RATE_LIMIT`，可重试。非 JSON body 只按 HTTP status 分类，不拿 HTML 里的关键词做信号。流式 onFailure 解析失败时用响应状态兜底，不再把 HTML 502 当序列化异常。`ai/build.gradle.kts` 打开 `unitTests.isReturnDefaultValues` 只是为了 provider 接线测试能调用 `Log`，范围仅限 `ai` 单测。C3 的 `ai/` 小改动原则下，这是第八处分叉。
- 手动验收：用假 provider 或代理返回 429 带 `Retry-After`、5xx 带 JSON/HTML、401、402、400 context、空回复，确认重试次数和间隔符合码表；退避等待中停止生成应立即结束；重试后工具副作用不重复。
- 测试：`RetryTest` 覆盖码表、`Retry-After` 原值和超过 `maxDelay` 的截断、jitter 归零后的固定间隔、`QUOTA` 不重试和取消；`ErrorParserTest` 覆盖 401/402/429/5xx/400 context 分类、结构化 `insufficient_quota`、嵌套 `QuotaFailure`、Gemini `RESOURCE_EXHAUSTED` 429 仍可重试、`withResponse` 保留结构化 code、HTTP date、非 JSON body 只按 status 分类和 `Retry-After` 解析；`AgentLoopTest` 覆盖流式和非流式空回复重试后成功；`ChatCompletionsErrorTest` 用假 interceptor 验证 429 `insufficient_quota` 到 `HttpException(status/code/Retry-After)` 的接线；`:ai:testDebugUnitTest` 全量通过；`:app:assembleGithubDebug` 通过。`:app:testGithubDebugUnitTest` 仍为已知 Windows 7 失败，本轮没有改 app。

### C62 流式 idle watchdog

- 落点：`AgentRequest.streamIdleTimeout` 默认 90 秒，可置空关闭；`AgentLoop.stream` 用 `Flow.timeout` 限制相邻 chunk 的等待，超时抛 `LlmFailure(TIMEOUT)`，启用重试策略时进入 C61 的重试循环。正常用户停止是 `JobCancellationException`，进不了这个窄 catch；catch 里的 `ensureActive()` 只兜“超时与取消同时发生”的竞态。
- 理由：原来只有 OkHttp/Ktor 的读超时，SSE 心跳会不断重置它；代理保持连接但不发内容时可以卡很久，界面一直显示生成中。watchdog 只看流内 chunk 间隔，不覆盖非流式请求、工具执行、上下文压缩和翻译的独立 streamText 路径。
- 合并注意：默认值写在 `AgentRequest`，当前没有设置页入口；要可配置时从 `ChatRequestFactory.createAgent` 注入。`Flow.timeout` 只在上游没有新 chunk 时触发，收到任意 chunk 会重置；超时后上游被取消，重试从本步起点重放，因此不会有半截内容进入下一轮。非流式仍依赖网络层读超时。`streamIdleTimeout <= 0` 目前会立即超时并耗尽重试，调用方应只传正数。`ai/` 分叉计数因此变为九处。
- 测试：`AgentLoopTest` 覆盖“流挂起超过 1 秒后超时并重试成功”和“相邻 chunk 都在超时内但总时长超过超时仍完成”；`:ai:testDebugUnitTest` 全量通过。手动验收：代理保持 SSE 连接但不发事件，约 90 秒后错误卡片/重试；等待中停止生成应立即结束，不额外重试。

### C63 上下文超限自动缩窗

- 落点：`AgentHooks.onContextOverflow()` 返回 true 表示调用方已缩小下一 step 的上下文；`AgentLoop` 捕获 `LlmFailure(CONTEXT_WINDOW_EXCEEDED)` 后只调用一次 hook，成功则用 `retryingStep` 跳过完成判定，从本 step 起点重试，不再让 C61 的重试码表处理。`ChatRequestFactory` 的 hook 只记 `shrinkActive`；每次 `prepareStep` 先用当前可见消息过 `limitContext`，再用 `overflowWindow` 取最后一条 user 起、至少两条且比当前窗口小的窗口。`buildStep` 每步重新计算，不会把旧的 assistant 快照带进重试后的下一步。`AgentLoop` 复用失败尝试的 base，避免 preview 已经写出的消息 id 被换掉。
- 理由：C61 已能把 provider 的 context overflow 归一化，但之前直接进错误卡片；用户只能手动压缩。自动缩窗不需要额外摘要请求，失败时只牺牲旧历史，保留最后几轮。
- 合并注意：只缩一次，不做自动压缩；窗口先受 `contextMessageLimit` 约束，再取最后一条 user 起、至少两条的子窗口，仍可能因单条超大输入失败。按消息条数裁剪，不算 token；非 user 的前导消息会被丢弃，压缩检查点也可能被这个 fallback 跳过。窗口之后还会 `mergeAdjacentUserMessages`，相邻 user 合并后实际条数可能少于两条，但首条仍然是 user。hook 状态属于本次 `createAgent` 的 hooks 实例，后续工具 step 每步按当前消息重算。缩窗只改变发给模型的历史，不改会话里已有的消息和分支。C61 的 `CONTEXT_WINDOW_EXCEEDED` 分类是前置条件；非流式和流式都经过 AgentLoop，所以都覆盖。`ai/` 分叉计数因此变为十处。
- 测试：`AgentLoopTest` 用抛 `CONTEXT_WINDOW_EXCEEDED` 的假流验证首轮、工具 step 后、连续两次、以及 preview 后重试的消息 id 复用；`ChatRequestFactoryContextTest` 覆盖 configured 窗口子集、最后 user 起始、至少两条、fallback 到上一条 user、以及当前消息刷新的边界；`:ai:testDebugUnitTest`、`:app:testGithubDebugUnitTest` 对应测试通过。手动验收：有足够旧历史时，用超长历史触发 provider 400 context，确认窗口缩到最后 user 后重试成功；重试后如果模型再发起工具调用，下一步请求必须包含新的 tool call 和工具结果，且工具只执行一次；只有一两条可见消息或单条超大输入仍失败并出卡片，不无限缩。
