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

## 明细

### C1 移除媒体创作与生图

- 落点：`RouteActivity`（删 `Screen.ImageGen`、`Screen.MediaCreation`、`Screen.MediaCreationSessions` 及其 entry 与 intent 处理，`Screen.SettingMedia` 的 entry 保留）、`ChatDrawer`（侧边栏两个入口）、`ViewModelModule`、`RikkaHubApp`（启动恢复）、`SettingPage`（媒体设置入口）、`shortcuts.xml`、`AndroidManifest`；删除 `ui/pages/mediacreation` 与 `ui/pages/imggen`，以及它们遗留的 157 条无引用字符串（以 `media_creation_*`、`imggen_page_*` 为主，另有 `chat_page_menu_image_generation`、`shortcut_media_creation`、`setting_page_media_desc`、`menu`）；`media_creation_*` 中仍有 19 条在用（`MediaCreationService`、`MediaCreationForegroundService`、`RikkaHubApp`），不要按前缀整批删；`sketch_draw_on_image` 保留，它同时是聊天输入框画板的按钮文案
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

- 落点：`utils/AIIconMatcher.kt`（删掉 aihubmix 的图标映射与匹配正则）、`app/src/main/assets/icons/aihubmix-color.svg`（删除）、`README.md`、`README_ZH_CN.md`、`README_ZH_TW.md`（删掉 Sponsors 章节）、`SettingPage.kt`（删掉启动 100 次后的赞助弹窗及其失效的三个 import 与 `launchCount` 订阅）、`SettingVM.kt`（删掉失去唯一读者的 `launchCount`）、弹窗的 4 条 `setting_page_sponsor_alert_*` 字符串 × 7 个 locale
- 理由：不需要推广入口
- 合并注意：捐赠页 `SettingDonatePage`、`SponsorAPI`、`Screen.SettingDonate` 与捐赠相关字符串保留不动。`Settings.sponsorAlertDismissedAt` 是已存储字段，未删除以免改动结构，只是不再有自动写入方，`DebugPage` 仍可手动改。刻意删除：`docs/sponsors/*.png` 与 `docs/donate.png` 已无引用，一并删掉；合并时上游若更新这些图片会成 modify/delete 冲突，按删除为准。三份 README 属上游高频改动文档，以上游最新版为准并重新删掉 Sponsors 章节；`SettingPage` 的弹窗区域与 `SettingProviderPage` 类似，上游若加回推广块，需要重新删除。`AIIconMatcher` 的图标映射本身不是推广入口，只删掉带推广性质的 aihubmix 那一条；上游后续新增的赞助商图标映射（3.1.1 的 `naapi`）保留

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
- 合并注意：保留存储与协议：`Settings.enableSuggestion`、`Settings.suggestionPrompt` 的字段与 DataStore key 不动，`Conversation.chatSuggestions` 是 Room 字段不能删，`WebDto.chatSuggestions` 与 `ConversationRepository.updateChatSuggestions` 一并保留，历史数据仍能读出与同步。`UsageSource.SUGGESTION` 枚举值保留，用量统计里不会再有新记录。`setting_model_page_suggestion_prompt_vars` 仍被标题提示词项使用，没有删

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

- 落点：`ImportExportTab`（新增「从官方 RikkaHub 导入」分组与 `ImportSource.OFFICIAL`，与本地导入共用同一个确认对话框与 `openDocumentLauncher`）；7 个 locale 新增 `backup_page_import_from_official` 与 `backup_page_import_official_desc`
- 理由：改包名后本应用与官方应用并存，需要一个明确的迁移入口
- 合并注意：官方备份与本应用的归档格式相同（`settings.json` + `rikka_hub.db` + 附件目录），所以复用同一条 `stageRestore` 流程，入口只负责引导用户先在官方应用里导出备份。`JsonInstant` 开了 `ignoreUnknownKeys`，官方备份里的额外字段不会导致解码失败。本节只管导入方向；反过来的「导出官方兼容备份」是并列的第二个出口，由 C18 落地，现在遇到 schema 分叉会直接失败

### C11 导出备份显示进度

- 落点：`BackupManager.createBackup`（新增可选 `onProgress` 回调与 `BackupProgress`，附件先收集成列表再写入）；`WebDavSync.prepareBackupFile`（透传回调）；`BackupVM`（`exportProgress` 状态与 `finally` 清理）；`ImportExportTab`（打包阶段显示确定进度，写入目标文件阶段显示不确定进度）；7 个 locale 新增 `backup_page_export_packing` 与 `backup_page_saving`
- 理由：附件多时导出要等很久，没有进度不知道卡在哪一步
- 合并注意：打包阶段按条目推进：`settings.json`、数据库快照、每个附件各算一条，`total` 与 `advance()` 的调用点一一对应。改为先收集附件再写入是为了给出确定的总数，代价是「列出」与「写入」之间文件消失的窗口从一个文件扩大到整个打包阶段；`PARTIAL_SUFFIX` 过滤与 `resolveInside` 校验都保留

### C12 改包名与应用名

- 落点：`app/build.gradle.kts`（`applicationId` 改为 `top.lemoneko.rakkihub`）、`AndroidManifest.xml`（shortcut deep link 的 scheme 从 `rikkahub` 改为 `rakkihub`）、`res/xml/shortcuts.xml`（`android:data` 同步）、7 个 locale 的 `app_name` 改为 `RakkiHub`（德语是上游 3.1.1 才加的，见 [`locale.md`](locale.md)）、`ExampleInstrumentedTest`（不再写死旧包名，改与 `BuildConfig.APPLICATION_ID` 比对）
- 理由：自主 fork 阶段要与官方应用并存，安装标识与私有数据目录必须独立
- 合并注意：`namespace` 保持 `me.rerere.rikkahub` 不动，类名、R 类与 `shortcuts.xml` 的 `targetClass` 都不受影响；`applicationIdSuffix = ".debug"` 保留，debug 包名是 `top.lemoneko.rakkihub.debug`；`${applicationId}` 拼的 authorities 与 `BuildConfig.APPLICATION_ID` 自动跟随

### C13 移除 Firebase 与 google-services

- 落点：`app/build.gradle.kts`（两个 plugin alias、`firebase-bom` 与 `firebase-crashlytics` 依赖、冗余的 `barcode.scanning`）、根 `build.gradle.kts`（两个 plugin alias）、`libs.versions.toml`（firebase 三个版本、四个库定义、`google-services` 插件、`barcodeScanning`）、`AppModule.kt`（两处 import 与 `single { Firebase.crashlytics }` 绑定，这个绑定本来就没有消费者）、本地 `app/google-services.json`、`README.md`/`README_ZH_CN.md`/`README_ZH_TW.md`（删掉「需要 google-services.json 才能构建」的提示）、CI 的 `GOOGLE_SERVICES_JSON` secret 与 workflow 里的写入步骤；顺带为 `ImageUtils.kt` 补上显式的 `androidx.exifinterface` 依赖
- 理由：不需要崩溃上报，也不想让应用依赖 Google 的运行时服务
- 合并注意：代码与构建脚本里已无 `com.google.firebase` 的引用；checked-in 的 baseline profile（`app/src/main/generated/baselineProfiles/`）里还留着 firebase 与 crashlytics 的历史条目，也有 C1/C5 删掉的 MediaCreation、ImgGen 与 TTS 类。这些条目指向已经不存在的类，ART 会跳过，不影响运行；重新生成能自愈，但在测试设备上跑不通（Gradle 装 nonMinifiedRelease 被系统拦下，用 su 装包后 instrumentation 又卡住并以 `DeadObjectException` 退出），真要重做需要换模拟器或干净设备。扫码在 3.1.1 由上游换成 CameraX 加 zxing，`quickie-bundled`、`barcode.scanning` 与 ML Kit 的传递依赖都不再存在；`androidx.exifinterface` 由上游在同一处显式声明，合并时删掉重复的那一行。上游若重新引入 Firebase，需要重新删除

### C14 移除设置页的分享本应用入口，统一品牌文案

- 落点：`SettingPage.kt`（about 分组里的分享 item、`shareText`/`share`/`noShareApp` 三个局部变量与三个失效 import）、7 个 locale 的 `setting_page_share`、`setting_page_share_desc`、`setting_page_share_text`、`setting_page_no_share_app`；品牌文案：`setting_page_web_server_desc`（7 个 locale）、`oauth` 模块的 4 条回调提示（7 个 locale）、`web-ui` 的 `page.json` 标题与描述、`conversation-sidebar.tsx` 的侧边栏标题
- 理由：分享出去还自称官方品牌并指向上游官网，没有意义；其余文案跟着应用名一起改成 RakkiHub
- 合并注意：`search-picker.tsx` 里的 `rikkahub: "RikkaHub"` 是搜索服务的显示名，指官方接口，保留；`backup_page_import_from_official` 的「Import from RikkaHub」指官方应用，同样保留

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

- 落点：新增 `BackupTarget`（`SELF` / `OFFICIAL`）、`OfficialDatabaseCompatibility`（判定我们的库还在不在官方形状上，比较版本与 identity hash，配 `OfficialDatabaseCompatibilityTest`）、`OfficialDatabaseAdapter`（一致时把附件路径改写成官方安装的位置再交出，分叉时抛 `OfficialExportUnsupportedException`）；`BackupManager.createBackup` 与 `WebDavSync.prepareBackupFile` 加目标参数，`BackupVM.exportToFile` 透传，只有 `SELF` 会更新备份提醒的时间戳；`ImportExportTab` 的导出分组加第二个入口，与自家导出的区别在数据库与其中的路径，目标用 `rememberSaveable` 存，避免选择文件期间被回收后退回自家格式；8 个 locale 新增 `backup_page_export_official`、`backup_page_export_official_desc`、`backup_page_export_failed`、`backup_page_export_schema_diverged`；`BackupManagerTest` 补一条官方目标的 androidTest
- 理由：改结构之后自家备份官方读不了，需要一个能把数据交回官方的出口；它和自家导出并列，不是把现有导出改掉
- 合并注意：当前双方 schema 一致，两个出口的差别只在本应用独占的选项与路径改写：适配层做「一致就把路径换成官方安装的位置、分叉就失败」，裁表裁列等真改结构时再补（路径改写见 C30）。失败是刻意的，静默产出官方打不开的备份要等到恢复时才发现；分叉走单独的提示文案，技术原因只进日志。导出失败时会尝试删掉选择位置时建出的空文档，尽量不留 0 字节文件（删除本身可能被 provider 拒绝，是 best-effort）。`settings.json` 不裁字段，官方解码器的 `ignoreUnknownKeys` 会忽略多余字段，这一点由 `OfficialSettingsCompatibilityTest` 钉住；但官方不认识的枚举值它吞不掉，所以官方出口会把工作区选项去掉（C29）。WebDAV 出口暂时只产出 `SELF` 格式。上游若改 `createBackup` 的签名或备份页的导出分组，第二个入口要跟着调整

### C19 附件按替换语义导入，导入的原始备份留底

- 落点：`PendingRestore.buildEntries` 与新增的 `buildRemovalEntries`（对归档里实际存在的附件目录，为 live 上有、payload 里没有的文件合成 `install = false` 的删除条目，复用现有 journal 与 rollback；只在数据库也一起替换时才做）；新增 `ImportedBackupArchive`（原子写入 `noBackupFilesDir/imported-backups/`，`stageRestore` 成功后才归档）；`WebDavSync.restoreFromBackupFile` 接上归档；`PendingRestoreTest` 补五条、新增 `ImportedBackupArchiveTest` 四条
- 理由：导入官方备份时附件是合并不是替换，目标目录里多出来的文件永远留着，启动时 `syncManagedFiles` 还会把它们重新登记进 `managed_files`，技能目录里的旧技能也会被扫到；另外导入完原始 zip 就没了，写坏数据没有回退来源
- 合并注意：删除条目只用文件级条目，不做目录级 rename：journal 与 rollback 都按路径记录单个文件，目录条目得为它们另加一套状态。`install = false` 的条目让 apply 把目标移进 `originals/`、rollback 再移回来，现有 journal 一行都不用改。只导附件时保持叠加语义，因为现有库还引用着 live 上的文件。空目录无法表达、`upload`/`fonts` 的平铺与递归不对称，都记在 [`backup/README.md`](backup/README.md) 的已知限制里。归档放 `noBackupFilesDir` 而不是 `filesDir`，后者整个目录被 web 路由暴露

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

- 落点：新增 `OfficialForeignAttachmentPaths`（只探测：库里的文本列是否还带着别的安装的 `file:///data/.../files/` 前缀）；`BackupManager.stageRestore` 在判定为迁移链之后再探一次，命中就改走转换器，由写入层把路径重写成本机；`BackupManagerTest` 加一条回归用例
- 理由：判为迁移链的备份本来走迁移链，而迁移链不改数据。带附件的官方备份、或我们自己在别的包名（debug / release、改过包名的构建）下产生的备份都带着生产者的绝对路径，装进当前安装后附件全部失配
- 合并注意：探测只看快照覆盖的文本列，读不到快照就不猜、直接走迁移链。代价是命中时要把整库复制一遍（转换器不改原库），换来的是路径一定被重写

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

- 落点：`OfficialDatabaseAdapter.adaptForOfficial` 增加 `attachmentPrefixRewrites`，一致性校验之后逐表逐列对 TEXT 列做前缀 `REPLACE`，产出改写过的库副本（无改写时直接返回原快照）；`BackupManager.createBackup` 计算本机 files 前缀与官方前缀（只有包名段不同，用户号沿用本机的），OFFICIAL 目标把库与 `settings.json` 两处都换过去（settings 走 `OfficialUrlRewriter.rewriteEmbedded`）；`OFFICIAL_APPLICATION_ID` 常量放在 official 包里；`BackupManagerTest` 补两条用例（消息里的附件、settings 里的头像），同时断言自家出口不改
- 理由：官方客户端恢复只把附件搬进自己的 files 目录，不会改库与设置里的路径。我们导出的库里存的是本应用的绝对 `file://`，装过去之后图片、文档、头像全断
- 合并注意：改写只在 OFFICIAL 目标发生，自家出口一行不动。官方包名写死在 `OFFICIAL_APPLICATION_ID`，官方换 applicationId 时要跟着改。这条只解决路径，schema 分叉后的裁表裁列仍待实现；官方客户端实际恢复一遍仍是人工闸门
