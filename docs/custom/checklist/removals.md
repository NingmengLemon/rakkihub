# 明细：移除与清理

本文件是 [checklist.md](../checklist.md) 的明细，收录 C1 至 C6、C8 至 C9、C13 至 C16、C54、C64、C95 至 C96。

<a id="c1"></a>

## C1 移除媒体创作与生图

- 落点：`RouteActivity`（删 `Screen.ImageGen`、`Screen.MediaCreation`、`Screen.MediaCreationSessions` 及其 entry 与 intent 处理，`Screen.SettingMedia` 的 entry 保留）、`ChatDrawer`（侧边栏两个入口）、`ViewModelModule`、`RikkaHubApp`（启动恢复）、`SettingPage`（媒体设置入口）、`shortcuts.xml`、`AndroidManifest`；删除 `ui/pages/mediacreation` 与 `ui/pages/imggen`，以及它们遗留的 157 条无引用字符串（以 `media_creation_*`、`imggen_page_*` 为主，另有 `chat_page_menu_image_generation`、`shortcut_media_creation`、`setting_page_media_desc`、`menu`）；`media_creation_*` 中仍有 19 条在用（`MediaCreationService`、`MediaCreationForegroundService`、`RikkaHubApp`），不要按前缀整批删；`sketch_draw_on_image` 保留，它同时是聊天输入框画板的按钮文案；`res/drawable/ic_media_creation.xml` 随入口一起删掉
- 理由：不使用
- 合并注意：刻意保留未动：`data/model/MediaCreation.kt`、`data/files/MediaCreationFiles.kt`、`data/db` 下的实体与 DAO、`data/repository/MediaCreationRepository.kt`、`service/MediaCreationService.kt`、`service/MediaCreationForegroundService.kt`、整个 `integrations/mediagen/` 模块、manifest 的 `<service>` 声明、`Settings.mediaGenerationProviders`、`SettingMediaPage.kt` 及其路由。合并时不要因为界面上看不到就顺手删掉这些。两处冲突风险：上游若重排 `RouteActivity` 的 `Screen` 与 entry 列表；上游 `feat/mediagen` 分支（16 提交，2026-10-05 仍有活动）正在改 `MediaCreationService.kt` 与被删除的 mediacreation UI，合并时按该分支进度判断是否恢复部分实现

<a id="c2"></a>

## C2 移除 Firebase Analytics

- 落点：`app/build.gradle.kts`、`AppModule`、`ChatVM`、`ViewModelModule`
- 理由：自用不需要埋点上报
- 合并注意：上游若增删 `ChatVM` 构造参数，`ViewModelModule` 里的显式传参会冲突

<a id="c3"></a>

## C3 移除赞助商推广，并允许删除所有提供商

- 落点：`DefaultProviders.kt`（删掉 AiHubMix、APIMart、随想AI网关、MaruCode 四项，新增 `REMOVED_SPONSOR_PROVIDER_IDS`）、`RecommendedProviders.kt`（整个文件删除，它只包含这四家）、`SettingProviderPage.kt`（断开右上角推荐 Sheet、去掉 AiHubMix 的「10% 优惠」标签、去掉列表删除菜单的 `builtIn` 门禁）、`SettingProviderDetailPage.kt`（去掉详情页删除按钮的 `builtIn` 门禁）、`core/ai/util/Request.kt`（去掉 `configureReferHeaders` 里给 aihubmix.com 加 `APP-Code` 的分支；这个函数整个已由 C96 删除）、`DefaultSettings.kt`（去掉 AiHubMix 的 TTS 预置项，新增 `REMOVED_SPONSOR_TTS_PROVIDER_IDS`）、`SettingsNormalization.kt`（提供商不再补回；助手与 TTS 仍补回）、相关测试
- 理由：不用这些推广渠道；预置项原本在界面上无法删除，规范化又会把删掉的补回来
- 合并注意：行为改动，`Settings.providers` 字段与 DataStore key 未变，不触发迁移。三点副作用：其一，存量里的赞助商条目会在下一次设置变更写盘时从设置中清掉，用户在这四家填过的 API key 与自建模型随之消失，回退旧版本后不会再出现；其二，因为不再补回，上游以后新增的预置提供商不会出现在已有安装里，需要手动添加；其三，删掉提供商后 `chatModelId` 等可能悬空，查找返回 null 并按未选择处理。上游若重写 `withBuiltInDefaults`，必须确认仍不补回提供商，否则删除功能失效；上游若恢复 `RecommendedProviders.kt` 或推荐入口，需要重新删除。`setting_provider_page_recommend` 字符串已删除

<a id="c4"></a>

## C4 清理赞助商图标、README 推广与启动赞助弹窗

- 落点：`utils/AIIconMatcher.kt`（删掉 aihubmix 的图标映射与匹配正则）、`app/src/main/assets/icons/aihubmix-color.svg`（删除）、`README.md`（删掉 Sponsors 章节；README 的现状见 C39）、`SettingPage.kt`（删掉启动 100 次后的赞助弹窗及其失效的三个 import 与 `launchCount` 订阅）、`SettingVM.kt`（删掉失去唯一读者的 `launchCount`）、弹窗的 4 条 `setting_page_sponsor_alert_*` 字符串 × 7 个 locale
- 理由：不需要推广入口
- 合并注意：捐赠页 `SettingDonatePage`、`SponsorAPI`、`Screen.SettingDonate` 与捐赠相关字符串保留不动。`Settings.sponsorAlertDismissedAt` 是已存储字段，未删除以免改动结构，只是不再有自动写入方，`DebugPage` 仍可手动改。刻意删除：`docs/sponsors/*.png` 与 `docs/donate.png` 已无引用，一并删掉；合并时上游若更新这些图片会成 modify/delete 冲突，按删除为准。README 已收成一份 fork 说明，不再跟随上游，见 C39；`SettingPage` 的弹窗区域与 `SettingProviderPage` 类似，上游若加回推广块，需要重新删除。`AIIconMatcher` 的图标映射本身不是推广入口，只删掉带推广性质的 aihubmix 那一条；上游后续新增的赞助商图标映射（3.1.1 的 `naapi`）保留

<a id="c5"></a>

## C5 移除 TTS 与 STT（语音合成与语音识别）

- 落点：
  - **speech 模块删除 32 个文件**（`integrations/speech/src/main/java/me/rerere/` 下除 `tts/provider/TTSProviderSetting.kt`、`asr/ASRProviderSetting.kt` 外的全部）：ASR 侧 `ASRController.kt`、`ASRState.kt`、`ASRVoiceTurn.kt`、`AudioAmplitude.kt`、`providers/DashScopeASRController.kt`、`providers/MiMoASRController.kt`、`providers/OpenAIRealtimeASRController.kt`、`providers/StepASRController.kt`、`providers/VolcengineASRController.kt`、`providers/VolcengineASRProtocol.kt`；TTS 侧 `controller/AudioPlayer.kt`、`controller/TextChunker.kt`、`controller/TtsController.kt`、`controller/TtsSynthesizer.kt`、`model/PlaybackState.kt`、`model/TTSRequest.kt`、`model/TTSResponse.kt`、`provider/TTSManager.kt`、`provider/TTSProvider.kt`、`provider/TTSProviderException.kt`、`provider/providers/` 下 `ElevenLabsTTSProvider.kt`、`FishAudioTTSProvider.kt`、`GeminiTTSProvider.kt`、`GroqTTSProvider.kt`、`MiMoTTSProvider.kt`、`MiniMaxTTSProvider.kt`、`OpenAITTSProvider.kt`、`QwenTTSProvider.kt`、`StepTTSProvider.kt`、`SystemTTSProvider.kt`、`VolcengineTTSProvider.kt`、`XAITTSProvider.kt`
  - **测试删除 5 个**：`app/src/test/java/me/rerere/rikkahub/ui/pages/chat/VoiceSessionControllerTest.kt`、`integrations/speech/src/test/java/me/rerere/asr/ASRVoiceTurnTest.kt`、`integrations/speech/src/test/java/me/rerere/asr/providers/VolcengineASRProtocolTest.kt`、`integrations/speech/src/test/java/me/rerere/tts/provider/providers/MiMoTTSProviderTest.kt`、`integrations/speech/src/test/java/me/rerere/tts/provider/providers/VolcengineTTSProviderTest.kt`
  - **app 侧删除**：`ui/hooks/TTS.kt`、`ui/hooks/ASR.kt`、`ui/context/LocalTTSState.kt`、`ui/context/LocalASRState.kt`、`ui/components/ui/TTSController.kt`、`ui/components/ai/AsrButton.kt`、`ui/components/ai/VoiceModeRow.kt`、`ui/pages/chat/VoiceSessionController.kt`、`ui/pages/chat/VoiceMode.kt`、`ui/pages/chat/TTSAutoPlay.kt`、`ui/pages/setting/SettingSpeechPage.kt`、`ui/pages/setting/components/TTSProviderConfigure.kt`、`ui/pages/setting/components/ASRProviderConfigure.kt`、`data/ai/tools/local/TextToSpeechTool.kt`
  - **断入口**：`SettingPage` 语音项、`SettingPreferencesGeneralPage` 的「TTS 设置」区块、`ChatInput` 麦克风与录音权限音效、`FilesPicker` 语音模式项、`ChatMessageActions` 朗读、`AssistantLocalToolPage` 的 TTS 开关、`RouteActivity` 的 TTS/ASR 状态与 `Screen.SettingSpeech`、`BuiltinToolUIs` 的 TTS 渲染与 `ToolUIRegistry` 注册、`LocalTools` 不再产出 TTS 工具、构造参数去掉 `ttsManager` 与 `settingsStore`、`AppModule` 解绑 `TTSManager` 并调整 `LocalTools` 注入、`ChatPage` 与 `ChatVM` 的语音会话接线、manifest 的 `RECORD_AUDIO` 与 `TTS_SERVICE` 查询
  - **一并清掉的死代码与孤儿资源**：`AppEvent.Speak`、`ChatService.enqueueVoiceMessage`、`SettingsExt` 的 `getSelectedTTSProvider` 与 `getSelectedASRProvider`、`PermissionTypes.PermissionRecordAudio`、`MessageQueue` 的 reply 等待通道与 `MessageQueuePausedException`、`ChatService` 里对应的 reply 完成路径与 `failReplyWaiters` 调用、`ChatService.generationDoneFlow` 与 `ChatVM` 的转发（唯一消费者是 TTSAutoPlay）、3 个 voice 队列测试、`res/raw/asr_start.mp3` 与 `asr_stop.mp3`，以及 7 个 locale 各 316 条无引用字符串（按归属拆开：媒体创作与生图 157 条属 C1、提供商推荐 1 条属 C3、赞助弹窗 4 条属 C4、语音相关 154 条属 C5）
- 理由：不用语音功能
- 合并注意：保留存储与反序列化：`Settings.ttsProviders`、`asrProviders`、`selectedTTSProviderId`、`selectedASRProviderId`、`defaultTTSPlaybackSpeed`、`ttsOnlyReadQuoted`、`ttsOnlyReadOutsideBrackets`、`autoPlayTTSAfterGeneration` 等字段与 DataStore key 未动；`TTSProviderSetting`、`ASRProviderSetting` 逐字节未改；`DEFAULT_TTS_PROVIDERS`（只剩 SystemTTS）与 TTS 补回逻辑保留；`LocalToolOption.Tts` 枚举值保留，旧助手配置仍可反序列化；`speech` 的 `build.gradle.kts` 未精简，仍带 media3 等用不到的依赖。与 C4 不同，这里选择的是彻底清理而非保留孤儿，理由是语音功能的入口、实现、文案与音效已全部消失，留着孤儿只会增加维护时的误判。app 侧语音代码是上游活跃区域（3.1.0 加过语音模式开关，3.1.1 又动了 `speech` 的 `TTSProvider.kt` 与 `ChatService` 的 reply 等待路径），恢复实现时需补齐上述入口；`speech` 只剩两个序列化类，上游改那一带时按本节保持删除。`SettingPage`、`SettingPreferencesGeneralPage`、`ChatInput`、`ChatMessageActions`、`ChatPage`、`ChatVM`、`ChatService`、`RouteActivity` 均为热区，冲突时以保留删除为准

<a id="c6"></a>

## C6 移除聊天回复建议

- 落点：生成侧：`ChatService` 的 `generateSuggestion` 方法与每轮结束时的调用、`ConversationMetaGenerator.generateSuggestions`；`data/ai/prompts/Suggestion.kt` 整个删除，`DEFAULT_SUGGESTION_PROMPT` 内联到 `Settings.kt` 作为字段默认值。UI 侧：`ChatList` 的 `onClickSuggestion` 参数与 `ChatSuggestionsRow`、`ChatPage` 的回调、`SettingModelPage` 的开关、`SettingModelPromptPage` 的建议提示词编辑项；7 个 locale 删掉四条建议文案，`setting_model_page_fast_model_desc` 去掉「chat suggestions」的说法
- 理由：不用这个功能，每条回复都会多一次生成请求
- 合并注意：保留存储与协议：`Settings.enableSuggestion`、`Settings.suggestionPrompt` 的字段与 DataStore key 不动，`Conversation.chatSuggestions` 是 Room 字段不能删，`WebDto.chatSuggestions` 与 `ConversationRepository.updateChatSuggestions` 一并保留，历史数据仍能读出与同步。`UsageSource.SUGGESTION` 枚举值保留，用量统计里不会再有新记录。`setting_model_page_suggestion_prompt_vars` 仍被标题提示词项使用，没有删。`web-ui` 仍会渲染历史会话里已存的 `chatSuggestions`，只影响旧数据，React 侧没有跟删

<a id="c8"></a>

## C8 移除 Chatbox 与 Cherry Studio 导入

- 落点：`data/sync/importer/` 整个包删除（主源集 `ChatboxImporter.kt`、`CherryStudioProviderImporter.kt`，测试源集 `app/src/test/java/me/rerere/rikkahub/data/sync/importer/ChatboxImporterTest.kt`）；`BackupVM`（`restoreFromChatBox`、`restoreFromCherryStudio`、`ChatboxRestoreResult` 及随之失去读者的 `conversationRepository`、`filesManager` 两个构造参数）；`ImportExportTab`（备份页「从其他 APP 导入」分组的两个入口）；7 个 locale 的 `backup_page_import_chatbox_desc`、`backup_page_import_cherry_studio_desc`、`backup_page_import_from_chatbox`、`backup_page_import_from_cherry_studio`、`backup_page_import_from_other_app` 已删除
- 理由：不用这两个第三方客户端，也不需要它们的迁移路径
- 合并注意：这里删的是实现而不是断入口，理由见 [`README.md`](../README.md) 的「改动尽量小，优先断入口」一节的补充说明。上游若重新加回这两个入口，需要重新删除

<a id="c9"></a>

## C9 移除 S3 备份

- 落点：`data/sync/S3Sync.kt` 与 `ui/pages/backup/tabs/S3Tab.kt` 整个删除；`DataSourceModule` 的 `S3Sync` 绑定；`BackupVM`（`s3Sync` 参数、`s3BackupItems`、`loadS3BackupFileItems`、`testS3`、`backupToS3`、`restoreFromS3`、`deleteS3BackupFile`，以及 `S3BackupItem` 的 import）；`BackupPage`（S3 分页与 `HugeIcons.Bucket` import）；`backup_page_s3_backup` 与 `backup_page_s3_backup_files` 两条文案已删除；`setting_media_page_s3_desc` 里「与备份用的 S3 相互独立」半句在 7 个 locale 一并去掉
- 理由：不用 S3 做备份，WebDAV 与本地文件已够用
- 合并注意：**S3 底层必须保留**：`data/sync/s3/S3Client.kt`、`AwsSignatureV4.kt`、`S3Config.kt`、`ui/components/ui/S3ConfigItems.kt` 仍被媒体创作的素材上传使用（`RemoteFileStore` + `Settings.uploadS3Config` + `SettingMediaPage`），`backup_page_s3_endpoint` 等 7 条连接配置文案也仍在使用。`Settings.s3Config` 是已存储字段，未删除以免改动结构，只是不再有读写方。上游若改 `S3Config` 或 `S3ConfigItems`，要确认媒体页那条路径仍然可用

<a id="c13"></a>

## C13 移除 Firebase 与 google-services

- 落点：`app/build.gradle.kts`（两个 plugin alias、`firebase-bom` 与 `firebase-crashlytics` 依赖、冗余的 `barcode.scanning`）、根 `build.gradle.kts`（两个 plugin alias）、`libs.versions.toml`（firebase 三个版本、四个库定义、`google-services` 插件、`barcodeScanning`）、`AppModule.kt`（两处 import 与 `single { Firebase.crashlytics }` 绑定，这个绑定本来就没有消费者）、本地 `app/google-services.json`、`README.md`（删掉「需要 google-services.json 才能构建」的提示；README 的现状见 C39）、CI 的 `GOOGLE_SERVICES_JSON` secret 与 workflow 里的写入步骤；顺带为 `ImageUtils.kt` 补上显式的 `androidx.exifinterface` 依赖
- 理由：不需要崩溃上报，也不想让应用依赖 Google 的运行时服务
- 合并注意：代码与构建脚本里已无 `com.google.firebase` 的引用；checked-in 的 baseline profile（`app/src/main/generated/baselineProfiles/`）里还留着 firebase 与 crashlytics 的历史条目，也有 C1/C5 删掉的 MediaCreation、ImgGen 与 TTS 类。这些条目指向已经不存在的类，ART 会跳过，不影响运行；重新生成能自愈，但在测试设备上跑不通（Gradle 装 nonMinifiedRelease 被系统拦下，用 su 装包后 instrumentation 又卡住并以 `DeadObjectException` 退出），真要重做需要换模拟器或干净设备。扫码在 3.1.1 由上游换成 CameraX 加 zxing，`quickie-bundled`、`barcode.scanning` 与 ML Kit 的传递依赖都不再存在；`androidx.exifinterface` 由上游在同一处显式声明，合并时删掉重复的那一行。上游若重新引入 Firebase，需要重新删除

<a id="c14"></a>

## C14 移除设置页的分享本应用入口，统一品牌文案

- 落点：`SettingPage.kt`（about 分组里的分享 item、`shareText`/`share`/`noShareApp` 三个局部变量与三个失效 import）、7 个 locale 的 `setting_page_share`、`setting_page_share_desc`、`setting_page_share_text`、`setting_page_no_share_app`；品牌文案：`setting_page_web_server_desc`（7 个 locale）、`oauth` 模块的 4 条回调提示（7 个 locale）、`web-ui` 的 `page.json` 标题与描述、`conversation-sidebar.tsx` 的侧边栏标题
- 理由：分享出去还自称官方品牌并指向上游官网，没有意义；其余文案跟着应用名一起改成 RakkiHub
- 合并注意：`search-picker.tsx` 里的 `rikkahub: "RikkaHub"` 是搜索服务的显示名，指官方接口，保留；`backup_page_import_from_official` 的「Import from RikkaHub」指官方应用，同样保留。`SettingAboutPage` 的标题在 C90 起改用 `app_name`（RakkiHub），`rikka-ai.com`、GitHub、LICENSE 链接保留，设置根页的 `docs.rikka-ai.com` 链接已由 C64 移除，`web-ui` 页脚的官方站链接同样保留，作为上游出处标注，没有改成自己的品牌

<a id="c15"></a>

## C15 移除更新检查

- 落点：`utils/UpdateChecker.kt` 与 `ui/components/ui/UpdateCard.kt` 整个删除，随前者一起消失的还有只服务于版本比较的 `Version` 类与 `app/src/test/java/me/rerere/rikkahub/utils/VersionTest.kt`（89 行、11 个用例）；`AppModule` 的 `UpdateChecker` 绑定与 import、`ViewModelModule` 的 `updateChecker` 实参、`ChatVM` 的构造参数与 `updateState` 流、`ChatDrawer` 的更新卡片与暂停计时逻辑、`SettingPreferencesNotificationPage` 的更新提醒设置项与暂停对话框；8 个 locale 各删 13 条文案（5 个 `update_card_*`、6 个 `setting_update_reminder_*`、2 个 `setting_display_page_show_updates_*`），`setting_page_preferences_notification_desc` 去掉「更新提醒」那一段
- 理由：更新源指向官方，下载下来是官方包，装上去只是多出第二个应用；要新版本自己去看
- 合并注意：保留 `Settings.displaySetting.updateCheckDisabledUntilEpochMillis` 这类已存储字段，DataStore key 不动，只是不再有读写方。`distribution` flavor 与 `BuildConfig.IS_PLAY_STORE` 保留：那是上游的构建结构，删掉会抬高每次合并的成本，现在没有代码读这个字段。这里和 C8 一样删的是实现而不是断入口，理由见 [`README.md`](../README.md) 的「改动尽量小，优先断入口」一节的补充说明。上游若重构 `UpdateChecker` 或恢复正常更新流程，需要重新删除

<a id="c16"></a>

## C16 清除导出内容里的站点水印

- 落点：`ui/pages/chat/ConversationExport.kt`（导出会话图片时左下角不再拼 `rikka-ai.com`，只留时间）、`ui/components/richtext/Mermaid.kt`（导出 Mermaid 图时不再在画布底部绘制站点地址）
- 理由：导出的内容是自己生成的，不该带上游站点痕迹
- 合并注意：只删绘制水印的那几行，图表配色与布局不动，`onBackground` 仍被 mermaid 自身的配置使用。上游若再加回水印或其他署名，重新删掉

<a id="c54"></a>

## C54 移除关于页图标点击彩蛋并清理相关死代码

- 落点：`SettingAboutPage` 删掉 logo 点击的 emoji 喷射、音效、形状变形、按下缩放和坐标跟踪（去掉 `EmojiBurstHost`、`SoundEffectPlayer`、`logoMorphShape`、`graphicsLayer`/`onGloballyPositioned`），保留静态的 `rememberAppLogoLayers` 绘制并加 `clipToBounds`（图层按 108dp 画进 136dp 盒子，不裁会溢出；C55 起改成独立的 about_logo），版本号长按进 Debug 保留；同时删掉 `utils/SoundEffectPlayer.kt`、Koin 绑定、`res/raw/bingbingbing.mp3`/`gangguan.mp3`，以及没有调用方的 `ui/easteregg/EmojiBurst.kt`；`AppLogo.kt` 的 `logoMorphShape` 保留给浮动窗口。
- 理由：关于页只需要静态图标和版本信息；删掉彩蛋后音效播放器、raw 音效和 EmojiBurst 都没有调用方，按 C5 的约定一并清掉。
- 合并注意：`EmojiBurst.kt` 是上游 `core/ui/` 的文件，删它是为了清死代码；上游改 `SettingAboutPage` 时按这个删。关于页图标由 C55 单独提供，launcher 和浮动窗口仍用 `R.mipmap.ic_launcher`；静态图标用方形裁剪，没有无障碍描述，属于装饰图。
- 测试：编译通过；点击 logo 不再有喷射、音效和变形；版本号长按仍进 Debug；浅色模式下没有深色背景溢出。

<a id="c64"></a>

## C64 移除设置页关于区域的社区与文档入口

- 落点：`SettingPage` 关于卡片移除 QQ 群按钮、Discord 按钮和使用文档行；删除 `QQGroupBottomSheet`、`QQGroup`、`QQ_GROUPS`、`Context.joinQQGroup`、`core/ui/src/main/java/me/rerere/ui/icons/TencentQQIcon.kt`、`core/ui/src/main/java/me/rerere/ui/icons/DiscordIcon.kt`，以及四份 locale 的 `setting_page_documentation`/`setting_page_documentation_desc`。
- 理由：这些入口指向外部社区和官方文档，不再随本分支维护；保留只会带来失效链接和额外图标/字符串维护成本。
- 合并注意：关于卡片仍保留设置页跳转、请求日志和赞助入口；没有新增替代链接。删除的是入口和死代码，不影响设置页其它分组。两个图标是上游 `core/ui/` 文件，移植上游对 `core/ui/src/main/java/me/rerere/ui/icons/` 的改动时按本项删除；C14 里“设置根页也保留 docs 链接”的旧说法已由本项取代。`core/ai/` 无改动。
- 测试：`assembleGithubDebug`、`spotlessCheck`、markdownlint 通过；手动检查设置页关于区域不再出现 QQ/Discord/文档项，关于页跳转、日志和赞助仍可用。

<a id="c95"></a>

## C95 清理 RikkaHub 提供商残留

- 落点：删除 `app/src/main/assets/icons/rikkahub.svg`；`app/src/main/java/me/rerere/rikkahub/utils/AIIconMatcher.kt` 去掉 `PATTERN_RIKKAHUB`（`rikka|auto`）分支；四个 locale 删除 `rikkahub_provider_description`；删除 `app/src/main/java/me/rerere/rikkahub/data/remote/RikkaHubAPI.kt` 与 `DataSourceModule.kt` 里只给它用的 Retrofit 单例（baseUrl 是 `https://api.rikka-ai.com`，接口本身是空壳，没有注入点）；`DEFAULT_AUTO_MODEL_ID` 改名 `DEFAULT_MODEL_ID` 并补注释，涉及 `DefaultProviders.kt`、`SettingsFields.kt`、`SettingsFieldsTest.kt` 与 `app/baselineprofile/mock-provider.patch`；新增 `app/src/test/java/me/rerere/rikkahub/utils/AIIconMatcherTest.kt`
- 理由：上游 `0533acde` 已把默认 RikkaHub 提供商（`api.rikka-ai.com`，含 `auto` 模型）从 `DEFAULT_PROVIDERS` 删掉，本分支跟着不再预置；但官方图标、`rikka|auto` 匹配规则、描述文案，以及只给这个提供商用的空 `RikkaHubAPI` 与指向同一域名的 Retrofit 单例还留着，任何名字里带 rikka 或 auto、且没被更靠前规则先命中的提供商都会拿到官方图标
- 合并注意：老设置里若还有这个提供商，`withBuiltInDefaults` 按 id 在 `DEFAULT_PROVIDERS` 里找不到对应项，就原样保留：`builtIn` 是 `@Transient` 不落盘，解码回落成 false，于是它成了可编辑的普通项（API 路径字段可改），不再有预置的图标与描述；但持久化的 `models` 列表还在，`Model(id = DEFAULT_MODEL_ID, modelId = "auto")` 仍能被解析到，所以 `DEFAULT_MODEL_ID` 只在没有这份老设置时才是不对应任何模型的占位 id，首启的聊天、快速、翻译模型都指向它，`baselineprofile/mock-provider.patch` 靠这个固定值把默认模型挂到假接口上，因此只改名不删；搜索服务 `RikkaHubSearchService` 的选项里 `displayName` 是 `RikkaHub`，与提供商共用同一个 matcher，去掉 `rikka|auto` 分支后搜索选择器里的官方图标回退成首字母 R，要保留图标得同时给搜索服务单独一条匹配规则和一个图标文件；Web UI 的 `ai-icon.tsx` 把空名字归一成 `auto`，未选模型时的占位图标同样从官方图标变成首字母 A；`core/ai/util/Request.kt` 给 OpenRouter 的 `HTTP-Referer` 由 C96 删除；`app/src/main/generated/baselineProfiles/` 里还留着 `getDEFAULT_AUTO_MODEL_ID` 与 `RikkaHubAPI` 的旧条目，那是设备生成的快照，重新生成时才会消失，而按 C13 记录的现状这条路在本机跑不通（Gradle 装 nonMinifiedRelease 被拦、instrumentation DeadObjectException），ART 对不存在的方法与类直接忽略
- 测试：`AIIconMatcherTest` 2 条通过（名字含 rikka/auto 不再命中，OpenAI、DeepSeek 仍命中）；`:app:testGithubDebugUnitTest` 805 条全过（JBR 21），spotless 通过；`git apply --check app/baselineprofile/mock-provider.patch` 仍可应用；`i18n list -m app --missing` 显示 0/1537，`unused -m app,search,oauth,ui` 从 46 条降到 45 条（少掉的就是这条没有引用的文案）；搜索选择器与 Web `ai-icon` 的首字母回退是登记的行为变化，人工核对过，没有单测

<a id="c96"></a>

## C96 移除发给 OpenRouter 的来源标识头

- 落点：删除 `core/ai/src/main/java/me/rerere/ai/util/Request.kt` 的 `configureReferHeaders`（原本只在 host 是 `openrouter.ai` 时加 `X-Title` 与 `HTTP-Referer`），以及六个 provider 文件里的十一处调用与各文件的 import：`OpenAIProvider`、`openai/ChatCompletionsAPI`、`openai/ResponseAPI`、`GoogleProvider`、`google/InteractionsAPI`、`ClaudeProvider`；`mediagen` 的 `OpenRouterMediaGenerationProvider.request` 同样去掉这两个头与那行注释，`OpenRouterMediaGenerationProviderTest` 改成断言 `X-Title` 与 `HTTP-Referer` 都不存在
- 理由：这两个头是 OpenRouter 的调用来源标识（排行榜归属与应用统计），不参与鉴权，上游 `acdb75e6` 加进来时 `HTTP-Referer` 指 `https://rikka-ai.com`。本分支改名后仍发官方站点，等于把我们的调用在 OpenRouter 那边算成官方 app；本分支没有自己的站点，也没有理由替官方做归属，干脆不发
- 合并注意：上游再往 `configureReferHeaders` 里加分支时按本项整体保留删除；用户自己在提供商或助手的自定义请求头里写 `X-Title`、`HTTP-Referer` 不受影响，`mergeCustomHeaders` 与 `PropertyEditor` 的常用头列表都没动；`app/src/main/generated/baselineProfiles/` 里还留着 `configureReferHeaders` 的旧条目，同 C13、C95 的说明，ART 会跳过
- 测试：`ai` 与 `mediagen` 的 JVM 测试全过（mediagen 那条改成断言这两个头都不存在）；`rg` 确认源码里除用户自定义头列表及其测试外没有 `X-Title` 与 `HTTP-Referer`；app JVM 805 条仍全过
