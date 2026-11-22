# 明细：数据、备份与附件

本文件是 [checklist.md](../checklist.md) 的明细，收录 C10 至 C11、C17 至 C34、C36、C40 至 C42、C79 至 C80、C87 至 C88、C108 至 C110、C142、C158。

<a id="c10"></a>

## C10 新增从官方 RikkaHub 导入

- 落点：`ImportExportTab`（官方 RikkaHub 分组里的「从官方 RikkaHub 导入」入口，操作类型用 `BackupOperation.IMPORT_OFFICIAL`，与本地导入共用同一个确认对话框与 `openDocumentLauncher`；分组本身见 C42）；7 个 locale 新增 `backup_page_import_from_official` 与 `backup_page_import_official_desc`
- 理由：改包名后本应用与官方应用并存，需要一个明确的迁移入口
- 合并注意：官方备份与本应用的归档格式相同（`settings.json` + `rikka_hub.db` + 附件目录），所以复用同一条 `stageRestore` 流程，入口只负责引导用户先在官方应用里导出备份。`JsonInstant` 开了 `ignoreUnknownKeys`，官方备份里的额外字段不会导致解码失败。本节只管导入方向；反过来的「导出官方兼容备份」是并列的第二个出口，由 C18 落地；C80 之后 v33 的 `usage_record.outcome`、C87 之后 v34 的 `file_derivations` 通过已知裁剪去掉，其它 schema 分叉仍直接失败

<a id="c11"></a>

## C11 导出备份显示进度

- 落点：`BackupManager.createBackup` 的可选 `onProgress` 与 `BackupProgress`（阶段与阶段内条目数，见 C41）；`WebDavSync.prepareBackupFile` 透传；`BackupVM` 的 `exportProgress` 状态在 `finally` 里清空；`ImportExportTab` 的进度行：打包与写目标文件两段都显示，写目标文件那一段是不确定进度。`backup_page_saving` 保留；打包各段用 C41 的分阶段文案（`backup_page_export_preparing` 等）。
- 理由：附件多时导出要等很久，没有进度不知道卡在哪一步。
- 合并注意：打包阶段先收集附件列表再写入，为的是给出每段确定的总数；代价是「列出」与「写入」之间文件消失的窗口从一个文件扩大到整个打包阶段，`PARTIAL_SUFFIX` 过滤与 `resolveInside` 校验都保留。进度模型改成阶段制之后，条目总数的算法在 C41 里。

<a id="c17"></a>

## C17 冻结官方备份的结构快照

- 落点：新增 `data/sync/official/`：`OfficialDatabaseSchema`（14 张表、106 个列、14 个索引、5 个外键、3 处自增）、`OfficialBackupLayout`（归档条目名、为兼容保留的 WAL 侧条目、`launchCount`、四个附件目录与其中两个嵌套目录），以及三个校验测试。配套改动：`BackupManager` 新增 `SETTINGS_ENTRY` 常量并让写入、恢复与 `PendingRestore` 三条路径共用它，另把 `ATTACHMENT_FOLDERS`、`NESTED_ATTACHMENT_FOLDERS`、`LAUNCH_COUNT_KEY` 从 private 放宽到 internal 以便对账
- 理由：导入器和官方兼容导出都需要一份「官方那侧认识什么」的描述，且不能随我们自己的 Room 定义漂，否则一旦我们改结构，导入器就读不懂官方库
- 合并注意：快照只描述官方那侧，数据从 `app/schemas/.../32.json` 生成。`message_fts*` 这类由手写 SQL 建的全文索引表不在其中：`AppDatabaseFactory` 打开数据库时只保证表存在，索引内容要靠 `ConversationRepository.rebuildAllIndexes` 重建，所以导入完成后要跑一次重建，否则搜索是空的。快照测试与 `32.json` 逐表、逐列、逐索引、逐外键对账；C80 的 Room 33 只允许已登记的 `usage_record.outcome`，C87 的 Room 34 另允许整张 `file_derivations`，由 `OfficialDatabaseSchemaTest` 校验；布局测试与 `BackupManager`、`DatabaseBackup` 的常量对账；settings 测试钉住官方解码器的 `ignoreUnknownKeys` 行为。官方升级会触发版本断言，我们自己改了归档布局会触发布局断言；自家 schema 与官方分叉后版本那条要重做

<a id="c18"></a>

## C18 新增官方兼容的导出出口

- 落点：新增 `BackupTarget`（`SELF` / `OFFICIAL`）、`OfficialDatabaseCompatibility`（判定我们的库还在不在官方形状上，比较版本与 identity hash，配 `OfficialDatabaseCompatibilityTest`）、`OfficialDatabaseAdapter`（一致时把附件路径改写成官方安装的位置再交出，已知分叉裁剪，其它分叉抛 `OfficialExportUnsupportedException`）；`BackupManager.createBackup` 与 `WebDavSync.prepareBackupFile` 加目标参数，`BackupVM.exportToFile` 透传，只有 `SELF` 会更新备份提醒的时间戳；`ImportExportTab` 加第二个导出入口，落在官方 RikkaHub 分组里（C42），与自家导出的区别在数据库与其中的路径，目标用 `rememberSaveable` 存，避免选择文件期间被回收后退回自家格式；8 个 locale 新增 `backup_page_export_official`、`backup_page_export_official_desc`、`backup_page_export_failed`、`backup_page_export_schema_diverged`；`BackupManagerTest` 补一条官方目标的 androidTest
- 理由：改结构之后自家备份官方读不了，需要一个能把数据交回官方的出口；它和自家导出并列，不是把现有导出改掉
- 合并注意：C80 后本仓库 Room 33，C87 后为 Room 34，C142 后为 Room 35；官方 v32 不变。适配层对一致形状改写路径，对已登记的 `usage_record.outcome` 做列裁剪、对 `file_derivations` 做整表 drop，并在 C142 后把 `message_node` 的复合索引换回官方单列索引，其它分叉仍失败。失败是刻意的，静默产出官方打不开的备份要等到恢复时才发现；分叉走单独的提示文案，技术原因只进日志。导出失败时会尝试删掉选择位置时建出的空文档，尽量不留 0 字节文件（删除本身可能被 provider 拒绝，是 best-effort）。`settings.json` 不裁字段，官方解码器的 `ignoreUnknownKeys` 会忽略多余字段，这一点由 `OfficialSettingsCompatibilityTest` 钉住；但官方不认识的枚举值它吞不掉，所以官方出口会把工作区选项去掉（C29）。WebDAV 出口暂时只产出 `SELF` 格式。上游若改 `createBackup` 的签名或备份页的导出分组，第二个入口要跟着调整。消息 JSON 的 `steps`/`callComplete` 依赖官方解码器的 `ignoreUnknownKeys` 容忍，上游 `ref` 的 `JsonInstant` 已开启；官方 3.1.1 已用普通消息与带 steps 两份样本确认容忍，不做 JSON 投影兜底，见 [`backup/acceptance.md`](../backup/acceptance.md) 的「B1 官方兼容样本」。

<a id="c19"></a>

## C19 附件按替换语义导入，导入的原始备份留底

- 落点：`PendingRestore.buildEntries` 与新增的 `buildRemovalEntries`（对归档里实际存在的附件目录，为 live 上有、payload 里没有的文件合成 `install = false` 的删除条目，复用现有 journal 与 rollback；只在数据库也一起替换时才做）；新增 `ImportedBackupArchive`（原子写入 `noBackupFilesDir/imported-backups/`，`stageRestore` 成功后才归档）；`WebDavSync.restoreFromBackupFile` 接上归档；`PendingRestoreTest` 补五条、新增 `ImportedBackupArchiveTest` 五条
- 理由：导入官方备份时附件是合并不是替换，目标目录里多出来的文件永远留着，启动时 `syncManagedFiles` 还会把它们重新登记进 `managed_files`，技能目录里的旧技能也会被扫到；另外导入完原始 zip 就没了，写坏数据没有回退来源
- 合并注意：删除条目只用文件级条目，不做目录级 rename：journal 与 rollback 都按路径记录单个文件，目录条目得为它们另加一套状态。`install = false` 的条目让 apply 把目标移进 `originals/`、rollback 再移回来，现有 journal 一行都不用改。只导附件时保持叠加语义，因为现有库还引用着 live 上的文件；导入现在必须带数据库（C40），这条分支只剩更早版本留下的 pending 目录会走到。空目录无法表达、`upload`/`fonts` 的平铺与递归不对称，都记在 [`backup/README.md`](../backup/README.md) 的已知限制里。归档放 `noBackupFilesDir` 而不是 `filesDir`，后者整个目录被 web 路由暴露

<a id="c20"></a>

## C20 导入器读取层

- 落点：新增 `data/sync/official/OfficialDatabaseReader.kt`（按冻结快照只读打开官方库，打开时核对 `user_version`、每张表、每列，行值按 affinity 读出并校验实际存储类型）、`OfficialSchemaVerifier.kt`（结构与类型判断抽成纯函数，不依赖 Android）；`OfficialSchemaVerifierTest` 七条 JVM 测试、`OfficialDatabaseReaderTest` 七条 androidTest
- 理由：导入器要用裸 SQL 读官方库，不能与我们的 Room schema 耦合
- 合并注意：读取层本身不接入口；写入与分流由 C23 接上。四个 Kotlin 文件都是新增，上游不会碰；上游升 schema 后要重新生成 `OfficialDatabaseSchema` 快照，读取层会跟着新快照读。设备限制见 [`backup/README.md`](../backup/README.md) 的已知限制，样本缺口见 `OfficialDatabaseReaderTest` 的 KDoc

<a id="c21"></a>

## C21 导入器写入层

- 落点：新增 `data/sync/official/OfficialTableOrder.kt`（从快照的外键推出写入顺序，循环外键报错）、`OfficialDatabaseWriter.kt`（一个事务把读出的行写进新建的空库，行级照抄不动 JSON 字段）、`OfficialUrlRewriter.kt`（把任意安装的绝对 `file://` files 前缀换成给定前缀，导入与官方兼容导出两个方向共用）；`OfficialUrlRewriterTest` 七条 JVM 测试、`OfficialTableOrderTest` 四条 JVM 测试、`OfficialDatabaseWriterTest` 四条 androidTest
- 理由：读取层只负责读，导入器还要把数据写进我们自己结构的库
- 合并注意：只写新建的空库，不接入口、不碰正在用的数据。这几个文件都是新增，上游不会碰。设备上跑 androidTest 前要先 `rm -f` 目标文件再 `adb push`，否则推的是旧字节

<a id="c22"></a>

## C22 导入器支持多版本官方快照

- 落点：新增 `OfficialSnapshots`（版本到快照的注册表）、`OfficialSchemaTypes`（表/列/索引/外键的类型，抽出来避免快照与注册表循环初始化）、`OfficialSnapshotV31` 与 `OfficialSnapshotV32`（从各自的 schema json 生成）；`OfficialDatabaseSchema` 收缩成「当前版本 + v32 的别名」；读取层按库里的 `user_version` 选快照，不在清单里的版本明确失败；`OfficialSchemaVerifier` 改成接受选中的表定义
- 理由：读取层要能读 3.1.0 那版（v31）的备份，单版本快照做不到；验收也要用同一套读取逻辑比对两份样本。恢复路径上 v31 的版本比我们低，但它的附件路径属于旧包名，会被 C25 改走转换器而不是走 Room 迁移链；快照服务的是读取层、转换器与之后的字段映射层
- 合并注意：v31 的 identity 是 `a428043e...`，与官方 3.1.0 的库逐表对比 14 张表的列零差异。这份样本产自 3.1.0 (193) 的构建，当时包名还是 `me.rerere.rikkahub`、schema 与官方一致，所以它同时代表官方 3.1.0 与本 fork 改包名前的旧构建。v16（官方 2.1.6）明确不支持：它和 v31 之间隔了十几个版本。两个快照文件由 `tools/gen-official-snapshot.ps1` 生成，官方升版时重新生成并在注册表加一行

<a id="c23"></a>

## C23 导入器接上恢复流程

- 落点：新增 `OfficialRestorePlanner`（按 `user_version` 与 schema 指纹决定迁移链 / 转换 / 拒绝，纯函数）、`OfficialDatabaseHeaders`（只读读两处元数据）、`OfficialDatabaseConverter`（官方库转成当前版本库，动手前对账目标表与列，缺表或缺列直接拒绝）；`BackupManager.stageRestore` 先 `normalize` 再分流，转换后替换暂存库并继续原流程；`OfficialRestorePlannerTest` 七条 JVM 测试、`OfficialSnapshotFilesTest` 两条（v31/v32 快照与各自 schema 文件逐列对账）、`OfficialDatabaseConverterTest` 两条 androidTest、`BackupManagerTest` 补两条（当前版本未知 identity 拒绝、高版本明确拒绝），Convert 分支由 planner 显式 v32 快照覆盖；`RikkaHubApp` 在恢复成功后后台重建全文索引；快照生成脚本移到 `tools/gen-official-snapshot.ps1`（`build/` 被 gitignore，脚本放那里不可复现）
- 理由：迁移链只能往上升，官方库版本高于我们或同版本不同结构时打不开；这是「导入器可用」的最后一段接线
- 合并注意：判定在读任何业务表之前完成，`ourVersion` 与 `ourIdentity` 都用编译进应用的常量，不从活库读（升级后首次启动时活库可能还停在旧版本）。高版本一律拒绝而不是回落到迁移链：迁移链降不了版本，而官方升版通常是加列，硬写会在插入时崩。同版本但缺 schema 指纹也拒绝，来源判不出来时不猜。`stageRestore` 是热区，上游改恢复流程时按本节复核顺序（normalize → 分流 → 转换 → 打开校验 → checkpoint）

<a id="c24"></a>

## C24 导入器验收：与独立算出的期望值逐表比对

- 落点：新增 `tools/backup-expectations.py`（用 Python 的 sqlite3 直接读原始备份，按 schema 的列顺序与主键排序，算每张表的行数与规范化 SHA-256）、`OfficialSampleAcceptanceTest`（androidTest，两层：直接读原始备份、以及经转换器写成当前版本库后再读，各自与期望值逐表比对；没有样本就跳过）；判据与跑法记在 [`backup/acceptance.md`](../backup/acceptance.md)
- 理由：没有独立期望值时，「读取层读对了」只是自证；roadmap 也要求「只编译通过不算数」
- 合并注意：规范化是「类型字节 + 4 字节小端长度 + 载荷」，行间 `0x1e`，两侧各实现一遍并逐字节对齐。样本含私人数据，放仓库外，测试用 `Assume` 跳过，CI 不会因此失败。v31 与 v32 两份样本都跑过（各 14 张表全过）；v16 明确不支持，不纳入样本集。这两处都靠 `app/schemas` 的列顺序，上游升 schema 后要重新生成期望值

<a id="c25"></a>

## C25 同版本备份也重写附件路径

- 落点：新增 `OfficialForeignAttachmentPaths`（只探测：库里的文本列是否还带着别的安装的 `file:///data/.../files/` 前缀）；`BackupManager.stageRestore` 在判定为迁移链之后再探一次，命中就改走转换器，由写入层把路径重写成本机；`BackupManagerTest` 加一条回归用例；新增 `OfficialForeignAttachmentPathsTest` 四条 instrumentation 用例（外来、本安装、混合、无引用）
- 理由：判为迁移链的备份本来走迁移链，而迁移链不改数据。带附件的官方备份、或 C36 之前别的包名（debug / release、改过包名的构建）下产生的备份，消息、设置与头像里都可能带着生产者的绝对路径，装进当前安装后附件全部失配；C36 之后新写或重存过的消息引用是相对形态，不需要这一步；没再存过的老会话与设置仍是绝对路径。
- 合并注意：探测只看快照覆盖的文本列，读不到快照就不猜、直接走迁移链。判断按出现位置做：先删掉当前安装的前缀，剩下的文本里还有 `file:///data/` 就算命中，同一格里既有本安装前缀又有外来前缀时也不会漏。代价是命中时要把整库复制一遍（转换器不改原库）。探测比写入宽：正文里出现别的 `file:///data/` 文本也会命中，这种情况下没有路径可改写

<a id="c26"></a>

## C26 整份备份的附件验收

- 落点：`tools/backup-expectations.py` 增加 `--settings` 与 `--zip`：只统计 settings 的助手与服务商条数，并从归档里算每个附件的路径、大小、SHA-256；新增 `OfficialBackupRestoreAcceptanceTest`（androidTest）：把真实归档走一遍 `stageRestore`，逐个断言附件的存在性与内容摘要，并确认解压出来的附件不多不少；另加 `RestoreAppliedOutcomeTest`：恢复应用之后 live 的 `upload/` 必须正好是归档那一份，上一次安装残留的文件要被清掉；settings 断言补上助手与服务商的 id 集合、当前助手、当前模型
- 理由：表数据在库里能验，附件与 settings 不行：附件是文件、settings 不在任何表里。没有这一层，导入把附件丢了或换了内容都没人发现
- 合并注意：样本是完整备份（含私人数据），放仓库外、设备上，缺了就跳过。settings 的期望值必须从**同一份归档**里取。跨归档借 `settings.json` 会把期望值算成另一份样本的数，表现为导入像丢了数据，实际是期望值来源错了，与导入链路无关

<a id="c27"></a>

## C27 全文索引自检与重建完整性标记

- 落点：`MessageFtsManager`（`isEmpty` 判断索引里有没有内容；`message_fts_state` 表记 `rebuild_complete`，重建开始置 0、成功收尾置 1）、`SearchIndexHealth.needsRebuild`（纯函数，新增 `rebuildComplete` 参数：有会话且（索引空或上次重建没跑完），六条 JVM 测试）、`ConversationRepository.needsSearchIndexRebuild`；`RikkaHubApp` 在恢复成功那次与常规启动都跑自检
- 理由：重建是「先清空再逐会话写」，只靠「索引非空」发现不了残缺：重建中途被杀时索引非空但缺内容，自检会跳过，搜索永久缺一部分，用户也不知道该重建。恢复被中断同样会留下残缺索引
- 合并注意：标记表与 `message_fts*` 同属 `onOpen` 里手写 SQL 建的表，不进 Room schema、不动 DataStore 键、不影响 identity 校验；恢复换库后若缺该行会触发一次重建，符合预期。判据仍有一处理论缺口：如果重建没跑过、但索引里恰好有内容，会多重建一次

<a id="c28"></a>

## C28 settings 形状校验

- 落点：新增 `data/sync/BackupSettingsShape.kt`（纯 JVM，不依赖 Android）与 `BackupSettingsShapeTest` 十四条；`BackupManager.stageRestore` 在 `SettingsJsonMigrator.migrate` 之后、`decodeFromString` 之前调用一次，不合格直接失败
- 理由：解码器开着 `ignoreUnknownKeys`，每个字段又都有默认值，空对象或别家的 JSON 也能解码成功，得到一份全默认值的 Settings 然后覆盖用户配置
- 合并注意：只校验关键项（`assistants`、`providers` 必在各为数组、元素带字符串 `id`，`displaySetting` 必在且是非空对象，`init` 与几个 id 字段若存在则类型必须对），不校验完整 schema，因为旧备份缺新字段是正常的。报错信息用英文，与 `BackupManager` 里其它异常一致。上游给 `Settings` 加字段不需要动这里；改这几个关键字段的名字或形状时要同步

<a id="c29"></a>

## C29 备份可选包含工作区文件

- 落点：`WebDavConfig.BackupItem` 增加 `WORKSPACES`（本地导出页由 C79 改为默认勾选；WebDAV 的 `DEFAULT_SELECTION` 仍默认关）、两个备份页的多选标签与 8 个 locale 的 `backup_page_workspaces`；`BackupManager.createBackup` 增加 `includeWorkspaceFiles`，新增 `collectWorkspaceFiles`（按 `workspaceDao()` 的工作区逐个走 `<root>/files`，跳过 `linux/` 与 `tmp/`，root 不合法时记 warning 并跳过）；`stageRestore` 增加同名参数与 `isWorkspaceFile` 校验（形状无条件校验，不看勾选）；官方兼容出口编码 settings 前把 `WORKSPACES` 从 `webDavConfig.items` 里滤掉；目录名改用 `WorkspaceManager.BASE_DIR_NAME` 与 `FILES_DIR`；归档条目名与 `filesDir` 下的布局一致（`workspaces/<root>/files/<relative>`），恢复侧因此复用已有的 `files/` 映射
- 理由：工作区文件是用户产物，但工作区里还可能有整个 PRoot 根文件系统，体积与必要性都不适合默认进 WebDAV 备份；本地导出页由 C79 改为默认勾选，仍然排除 `linux/` 与 `tmp/`
- 合并注意：只打包文件区，归档里出现 `workspaces/<root>/linux/...` 一律拒绝，避免写坏或被改过的归档覆盖根文件系统。恢复是叠加语义，不删 live 里多出来的工作区文件；附件那套替换语义没有套过来，这条有专门用例钉住。恢复要求导入时勾上工作区，条目在归档里但当前选择不含工作区时会被跳过，启动自检随后可能把这些工作区标成 BROKEN。`WORKSPACES` 是本仓库新增的枚举值，官方不认识，未知枚举值不像未知字段那样能被 `ignoreUnknownKeys` 忽略，所以官方出口必须把它从 settings 里去掉

<a id="c30"></a>

## C30 官方兼容导出改写附件路径

- 落点：新增 `data/sync/official/OfficialExportPaths.kt`（纯函数，推导要替换的前缀对：本安装实际的 files 目录，加上 `/data/user/<用户号>/<包名>/files/` 与 `/data/data/<包名>/files/` 两种规范形态，用户号从路径里取；另带 `LIKE` 模式的字面量转义）与 `OfficialExportPathsTest` 十条 JVM 用例；`OfficialDatabaseAdapter.adaptForOfficial` 增加 `attachmentPrefixRewrites`，一致性校验之后逐表逐列对 TEXT 列做前缀 `REPLACE`（一个事务、`LIKE` 模式转义、写完确认没有残留副文件），产出改写过的库副本（没有要改的就返回原快照）；`BackupManager.createBackup` 用这份前缀对改写库，settings 走 `OfficialUrlRewriter.rewriteEmbedded`；`OFFICIAL_APPLICATION_ID` 常量放在 official 包里；`OfficialUrlRewriter` 的 KDoc 改成方向中立（导入与导出共用）；`BackupManagerTest` 补三条用例（消息里的附件、settings 里的头像、官方归档回灌本应用），每条都同时断言自家出口不改或回灌后换回本机路径
- 理由：官方客户端恢复只把附件搬进自己的 files 目录，不会改库与设置里的路径。我们导出的库里消息附件可能是相对形态（C36），设置与旧行仍是本应用的绝对 `file://`，不换的话装过去之后图片、文档、头像会断
- 合并注意：改写只在 OFFICIAL 目标发生，自家出口一行不动。官方包名写死在 `OFFICIAL_APPLICATION_ID`，官方换 applicationId 时要跟着改。消息库里新写的相对形态（C36）也会在导出时换成官方安装的绝对位置。这条只解决路径；schema 分叉后的裁表裁列在 C80 已对 `usage_record.outcome` 做了已知裁剪，其它分叉仍待实现；官方客户端实际恢复一遍仍是人工闸门

<a id="c31"></a>

## C31 聊天草稿落盘

- 落点：新增 `data/files/ChatDraftStore.kt`（一个会话一个 JSON，先写临时文件再原子替换、临时名带随机后缀，坏文件当作没有草稿，空草稿删文件，读写都在 IO 上，另有 `deleteOrphans` 与给抽屉用的 `conversationIds`）与 `ChatDraftStoreTest` 十五条 JVM 用例；`RepositoryModule` 注册实例并把目录指到 `noBackupFilesDir/chat-drafts`；`ChatVM` 加构造参数（`ViewModelModule` 接线），`init` 里先恢复再观察输入变化（`snapshotFlow` 加 500ms 防抖，编辑历史消息期间观察值是 null），另有一段观察编辑状态的转变、编辑结束立刻把编辑前的内容放回输入框；`onCleared` 补一次同步写，编辑中被销毁时留的是进编辑前那份；新增 `ChatEditDraft` 收起暂存的放回规则，`ChatEditDraftTest` 九条 JVM 用例钉住，`ChatVM.beginEditingMessage` 进编辑前把草稿交给它，并另起一次写盘（不等防抖），`ChatPage` 的编辑入口改为调它；`ChatService.deleteConversation` 顺手删草稿（没落过库的新会话也要覆盖），`ConversationRepository.getConversationIds` 交给 `RikkaHubApp` 启动时清理；抽屉的会话项上标出还有草稿的会话（`ChatDrawerVM` 持有一份 id 集合，打开抽屉与切换会话时刷新），8 个 locale 新增 `chat_page_has_draft`；`ui/hooks/SharedPreferences` 新增 `readLastConversationId` 与 `writeLastConversationId`，启动页、`ChatVM`、`RikkaHubApp` 清理与 `SafeModeActivity` 共用
- 理由：输入框内容只在 ViewModel 里，进程一死就没了，附件引用也跟着消失；将来做附件清理时，草稿里的引用必须能被扫描到
- 合并注意：草稿是文件不是表，不动 Room schema，官方兼容导出不受影响；放 `noBackupFilesDir` 是因为 `filesDir` 整个目录被 web 路由暴露，草稿也不进备份。只记文字与附件，不记「正在编辑哪条消息」。清理的保留集是「库里的会话 + 上次打开的会话」：新建会话的 id 只在内存里，没发消息就退进程时库里查不到它，但关掉「启动新建会话」时下次启动会直接打开它，所以不能按「不在库里」删。编辑结束后草稿立刻放回输入框（不等防抖），放回时输入框不是空的话不覆盖，以用户新写的内容为准；删除会话会一起删草稿，撤销删除不会恢复草稿，从别的页面删掉还停在聊天页上的会话后，页面继续输入会重新写出一份草稿，这个 id 仍是「上次打开的会话」时启动清理会留着它。防抖窗口内进程被杀丢的是最后一次停顿之后的内容；附件清理（C32）靠草稿与 `LiveAttachmentReferences` 保住输入框里的附件，两个都不在了这层引用才消失。抽屉里的草稿标记只在打开抽屉与切换会话时刷新，不是实时订阅，清掉草稿后标记要等下次刷新才消失；`conversationIds` 只看文件名，坏掉的草稿也会列出，打开会话时发现没有内容，下一次刷新标记就没了。`ChatVM`、`ChatService`、`ChatDrawer` 与 `RouteActivity` 都是上游热区，合并时按本行复核

<a id="c32"></a>

## C32 附件按内容去重，删除改为摘引用

- 落点：新增 `data/files/ContentAddressedFiles.kt`（边写边算 SHA-256，写进同目录的 `.pending-<uuid>` 临时文件，落盘后按指纹改名，目标已存在就复用）与 `FileUtils.buildContentFileName`（扩展名优先按 MIME，其次来源文件名）；`FilesManager` 的 upload 写入全部走它（`saveManagedFrom*`、`createChatFilesByContents/ByByteArrays`、`createChatTextFile`），`createManagedFileEntity` 遇到同一路径复用已有登记，`syncFolder` 不登记 `.pending-*`；新增 `data/files/AttachmentReferences.kt`（从 JSON 与草稿文本里摘 `file:` 地址、归一成规范路径）、`LiveAttachmentReferences.kt`（进程内登记输入框附件，编辑历史消息期间草稿不写输入框，靠它兜住）与 `AttachmentCleaner.kt`（扫引用点、删无引用文件与 `managed_files` 行，`collectReferencedPaths` 与 `shouldDeleteUploadFile` 单独成纯函数）；删掉所有立即删文件的入口：`ChatService` 的落库差异删除与队列附件清理、删会话时的文件删除、`ConversationRepository.deleteConversation`、输入框移除附件（`AttachmentChips`、`ChatInputState.shouldDeleteFileOnRemove`）、助手头像与背景、用户头像，`FilesManager.deleteChatFiles`、`MessageQueue.unreferencedQueuedAttachmentUrls` 与上游的 `QueuedAttachmentCleanupTest.kt` 一并删除；`MessageNodeDAO.getContentsWithFileReferences` 只取含 `file:` 的消息行，`ChatDraftStore.referencedFileUrls`、`ChatService.unsavedAttachmentUrls` 与 `ChatService.isGenerating` 补上库外与内存引用；设置的文件管理页加「清理无引用的文件」入口，按天清理也走同一套引用判断（`AttachmentCleaner.cleanOlderThan`，`FilesManager.deleteOlderThan` 删除），新增 7 条文案 × 8 个 locale；新增 `ContentAddressedFilesTest` 四条、`AttachmentReferencesTest` 六条、`AttachmentCleanerTest` 十三条、`LiveAttachmentReferencesTest` 五条 JVM 用例，`ChatDraftStoreTest` 补一条草稿引用扫描
- 理由：文件名原来是随机 UUID，写之前无从判断内容是否已经存在，同一张图在多个会话、多条消息里各存一份，备份体积里有很大一块是重复内容。改成内容寻址之后同内容只留一份，删除也就不能再按「删消息就删文件」做
- 合并注意：内存与 UI 的引用形式仍是绝对 `file://`；消息库里落盘的是相对形态（C36），清理按两种形态解析，官方导出的改写也覆盖相对形态；序列化格式与 Room schema 不变。`managed_files` 只是旁路索引，消息读取不经过它，去重之后同一路径只有一条登记。清理是「宁可少收垃圾也不能删错」：解析不了的地址按文件名保守保留，生成开始前与删除前各查一次 `isGenerating`，工具结果里的新文件可能还没落库；输入框里的附件由草稿（C31）与 `LiveAttachmentReferences`（C32）保活，编辑历史消息期间不写草稿的那部分也在后者里；Web 客户端上传后还停在浏览器输入框里的附件不在扫描范围，清理前别让浏览器里留着没发出去的附件；工作区把 `filesDir/upload` bind mount 到 `/upload`，从终端写进去的文件没有任何引用，也会被清掉。消息扫描一次把所有含本地地址的行读进内存，历史很大时是手动清理的时间与内存上限；删除前还会再取一次只存在于内存的输入框登记与内存会话、队列引用，缩小扫描窗口内新加附件被误删的可能。文件页的按天清理也走同一套引用判断，只删没有引用、且登记时间（没有登记时按文件修改时间）早于截止时间的 upload 文件；登记时间在去重复用时不刷新，所以年龄是「首次写进来的时间」，不是「最后一次被用」；「全部文件」、单个删除与 Web 的 `DELETE /api/files/{id}` 仍然直接删文件，这是显式的破坏性操作。存量文件的随机名不会被就地归并：应用不会主动扫盘改名，只有带数据库的导入会把归档里的文件按内容改名并去掉重复（C40）。`FilesManager`、`ChatService`、`ConversationRepository`、`AttachmentChips`、`AssistantVM`、`AssistantDetailVM`、`ChatVM`、`SettingFilesPage`、`ViewModelModule`、`RepositoryModule` 都动了，合并时按本行复核。

<a id="c33"></a>

## C33 附件拷贝挪出主线程

- 落点：附件进入 upload 的 UI 调用点把拷贝与 MIME 查询包进 `withContext(Dispatchers.IO)`：`ChatPage` 的分享入口、`ChatAttachmentPicker` 的选图、拍照（裁剪与不裁剪）、视频、音频、文档，`UIAvatar.saveAvatarImage`，`BackgroundPicker`。`CropLauncher.useCropLauncher` 的 `onCroppedImageReady` 改成 `suspend`，调用方在回调里等拷贝完成再返回，`CropLauncher` 等回调返回后才删裁剪用的临时文件；取消时重抛 `CancellationException`，不弹「裁剪失败」。相机不裁剪时输出文件在拷贝完成后的 `finally` 里删。`ChatService.forkConversationAtMessage` 把 `source.fork(...)` 包在 `withContext(IO)` 里，`copyFile` 仍是同步 lambda。`ChatInput` 的粘贴图片保持同步，`consume` 出来的内容离开回调可能失效。
- 理由：主线程上拷贝大文件会卡界面甚至 ANR，C32 给内容寻址写入加了 `fsync` 之后更明显。
- 合并注意：`CropLauncher` 的回调现在是 suspend 契约，调用方必须在回调里完成拷贝再返回，否则临时文件会在拷贝前被删；`ChatPage`、`ChatAttachmentPicker`、`ChatInput`、`CropLauncher`、`UIAvatar`、`BackgroundPicker`、`ChatService` 都动过，合并时按本行复核。异步化带来一个已知交错：拷贝期间用户可以发送或关闭选择器，附件会在拷贝完成后追加到输入框，分享路径还会整体覆盖输入框内容；没有加「导入中」状态。粘贴路径必须保持同步；fork 的 `copyFile` 参数仍是同步类型，只是在调用点包了 IO。

<a id="c34"></a>

## C34 备份与工作区文件的拷贝挪出主线程

- 落点：`ImportExportTab` 的导出复制（临时 zip 到用户选择的位置）与导入暂存（用户选择的位置到 cache 临时包，连同 `vm.restoreFromLocalFile`）包进 `withContext(Dispatchers.IO)`；`vm.exportToFile` 在主线程调用，打包主体在 `BackupManager` 里自带 IO 派发，只有复制在 IO。`ImportExportTab` 的开流失败（`openOutputStream`/`openInputStream` 返回 null）按失败处理，不会静默报成功或拿空包去恢复；失败分支会删掉目标文档，若用户选择覆盖已有文档，null 开流时那份文档也会被删，这是失败清理策略的残余风险。复制被取消时重抛 `CancellationException`、不弹失败；导出取消会静默删掉没写完的目标文档；导出与导入的占用由 `BackupVM` 的统一锁管理，`release` 在 `finally` 里调用（C41）；导出临时文件在 `finally` 里删。`WorkspaceDetailPage` 的文件选择器把名字查询、`openInputStream` 与交给 VM 都放进同一个 IO 块，避免交接前被取消留下没关闭的流。
- 理由：备份 zip 可能有几百 MB，整包复制放在主线程会卡界面甚至 ANR；SAF 的查询与打开是 binder 调用，也放到 IO 更稳。
- 合并注意：改了上游的 `ImportExportTab` 与 `WorkspaceDetailPage`，合并时按本行复核。`restoreFromLocalFile` 从 IO 块里调用，其链路自带 IO 派发；`exportToFile` 仍在主线程调用，内部只碰 StateFlow、设置读写与 IO，不能假设自己必须在 Main。六个备份入口共用一把锁（C41），复制期间不能再启动另一条；锁的抢占与释放在 `BackupVM` 里，与这里的 IO 派发无关。取消时清理在 `finally` / `catch` 里，不要让 `CancellationException` 走进失败提示。恢复在 `stageRestore` 里发布 pending 之后才归档原始包；如果取消发生在发布之后，pending 仍会在下次启动生效，界面可能没弹成功提示。

<a id="c36"></a>

## C36 消息附件引用按相对路径存储

- 落点：新增 `data/files/AttachmentUrlStorage.kt`（两个纯函数：落盘前只把 JSON 里 `url` 字段的、本安装 files 目录下的 upload 绝对地址换成 `file:upload/...`，读取后还原；本安装的 `/data/user/<用户号>/` 与旧的 `/data/data/` 两种绝对形态都认）与十条 JVM 用例；`ConversationRepository` 构造多一个 `attachmentFilesDirUri`（Koin 里从 `context.filesDir` 生成），`saveMessageNodes` 落库前转换，读取消息时还原；`AttachmentReferences.fileUrlToCanonicalPath` 与 `collectReferencedPaths` 的 `filesDir` 改成必填，把相对形态还原成规范路径，`AttachmentCleaner` 扫描时传入当前 filesDir；`OfficialExportPaths.attachmentRewrites` 增加 `"url":"file:upload/` 到官方安装前缀的替换（包名相同时也要换，官方客户端只认绝对路径），配 `OfficialExportPathsTest` 断言，官方兼容导出的库仍是官方能读的绝对地址。`AttachmentCleanerTest` 补一条真实 `upload/` 文件的相对形态解析用例，删掉 `MessageNodeDAO` 里没有调用点、会绕过转换的 `getNodesOfConversation`、`insert`、`update`。
- 理由：消息库与随它导出的备份原来把安装路径写死在每条附件地址里，包名或用户号一变就整体失效；导入侧的路径重写是事后补救，落盘形态本身应该可移植。改完之后相对地址在任意安装下都能解析，旧库里的绝对地址继续有效并在下一次保存时转换。
- 合并注意：消息 JSON 的读写都经过 `ConversationRepository` 的转换，别的入口直接读写 `MessageNodeEntity.messages` 会绕过它；设置里的头像仍存绝对地址（靠导入侧重写），草稿不随备份走，两者都没改。清理与官方导出都按绝对、相对两种形态处理；官方导出的相对前缀带 `"url":"` 字段边界，避免碰到正文。旧数据不做一次性迁移，读到什么算什么，下一次保存时本安装的两种绝对形态都会转成相对。C36 之前的构建读到新库里的相对地址时图片大概率显示不出来，点音频、视频的附件可能抛异常，文档附件会退化成 ERROR 文本；数据不丢，升级后恢复。

<a id="c40"></a>

## C40 导入时附件按内容寻址，归档必须有数据库与设置

- 落点：`WebDavConfig.BackupItem` 加 `SELECTABLE`（只有 `FILES` 与 `WORKSPACES`）与 `normalize`（聊天记录始终在内，`DEFAULT_SELECTION` 由它生成）；`BackupManager.createBackup` 去掉 `includeDatabase`，始终写数据库快照，写进 settings.json 的 `items` 也先归一；`stageRestore` 同样去掉该参数，并改成先列条目再解压：`planRestoreEntries` 在第一个字节写入之前判定，缺 `rikka_hub.db` 抛 `BackupRejectedException(MISSING_DATABASE)`、缺 `settings.json` 抛 `MISSING_SETTINGS`；`upload/` 下的附件解压时按 SHA-256 改名并去重，新增 `ImportedUploadFiles`（`upload/` 条目直接走 C32 的 `ContentAddressedFiles.write`，名字用 `FileUtils.buildContentFileName`，指纹与命名不另写一套）与 `UploadNames.rewriteDatabase`（从 `sqlite_master` 与 `PRAGMA table_info` 现查表与文本列，逐行把 `files/upload/<名字>` 与 `file:upload/<名字>` 换成新名字，`message_fts` 与影子表跳过；`managed_files.relative_path` 单独处理，先按新路径分组、每组只留 id 最小的一行，再把要改的行分两步落盘（先挪到临时路径）避免改名成链时撞唯一索引），settings.json 的替换复用 `ImportedUploadFiles.rewrite`；新增 `BackupRejectedException` 与两条文案，`ImportExportTab`、`WebDavTab` 通过 `restoreErrorMessage` 选文案；两页的「备份内容」只列 `SELECTABLE`，WebDAV 的持久化取值在读写两侧都过 `normalize`。测试：JVM 的 `ImportedUploadFilesTest`（五条）与 `BackupItemTest`（三条）；androidTest 的改名去重用例与三条拒绝用例（缺库、缺设置、附件名带反斜杠）；`BackupManagerTest`、`OfficialBackupRestoreAcceptanceTest`、`RestoreAppliedOutcomeTest` 按新规则调整，后两者按「sha256 + 归档里的扩展名」算出 `upload/` 的期望落盘名。
- 理由：官方给每次上传起随机 UUID 名，同一张图发几次就是几个文件；导入侧原来原样落盘，去重只作用于本应用的新写入。文件名由库里的文本列引用，改名必须连带改库、设置与文件登记，所以只有归档同时带数据库时才安全；只带附件的归档恢复出来对不上任何引用，应当在解压之前拒绝。设置与库是一对：库里的会话引用设置里的助手，缺一半会错配。
- 合并注意：改名只做 `upload/`；`skills/`、`fonts/`、`media_creation/` 是自有目录：`skills/` 的子目录名来自 SKILL.md 的 `name`，`fonts/` 与 `media_creation/` 是随机命名；三种都没有按内容去重的历史包袱，改名要连带改工具结果里的引用，收益不值。扩展名取自归档里的文件名并转小写，导入侧拿不到可靠 MIME，同内容不同扩展名会落成两份。归档里附件条目的文件名带反斜杠时在计划阶段就拒绝，别留到 apply 才失败。`managed_files` 是唯一需要先去重的表（`relative_path` 有唯一索引）；`favorites.ref_key` 与 `workspaces.root` 也是唯一索引，但都不存附件路径。扫描跳过 `message_fts*`：索引里只有正文，恢复之后本来就要重建。顺序是转换、迁移、再改名、最后 settings，因为转换器先把安装前缀换成本机，改名作用在换过前缀的文本上。`PendingRestore.buildRemovalEntries` 的「只导附件」分支现在只剩更早版本留下的 pending 目录会走到。持久化的 `webDavConfig.items` 里每一项的含义没变，但聊天记录不能再关掉，旧值由 `normalize` 补上；`normalize` 幂等，解码与 DataStore key 都不动。相关记录同步在 [`backup/README.md`](../backup/README.md)、[`backup/importer.md`](../backup/importer.md) 与 [`backup/acceptance.md`](../backup/acceptance.md)。

<a id="c41"></a>

## C41 备份与恢复按阶段报进度，六个入口互斥

- 落点：新增 `data/sync/BackupProgress.kt`（`BackupStage` 与 `BackupProgress`，`FILES`、`WORKSPACES`、`EXTRACTING` 按条目计，`DATABASE` 段 `total` 恒为 0）；`BackupManager.createBackup` 在清点、写数据库、附件、工作区四处上报，`stageRestore` 在解压与校验数据库两处上报；`WebDavSync.prepareBackupFile` / `restoreFromLocalFile` 透传 `onProgress`；`BackupVM` 增加 `exportProgress`、`restoreProgress` 与 `operation`（`BackupOperation` 六种入口，`claim`/`release` 用 `compareAndSet` 抢占，选文件的系统面板打开期间也算占用，进度在 `finally` 里清空）；`ImportExportTab` 把进度行放进触发操作所在的分组，数据库段给不确定动画，附件与解压段给 `done / total`，写目标文件与读源文件两段沿用不确定动画；`WebDavTab` 的备份按钮、恢复列表与 `BackupFilesSheet` 用同一个锁；`BackupComponents` 新增 `backupProgressItem`，确定进度的 `LinearProgressIndicator` 传 `drawStopIndicator = {}`，去掉 material3 默认在轨道末端画的停靠点。文案：新增 12 条（分组标题 2 条、依赖说明 1 条、拒绝 2 条、导出阶段 4 条、导入阶段 3 条），删掉 `backup_page_export_packing`，八份 locale 同步。
- 理由：原来只数 zip 条目，数据库快照与官方出口的整库改写挤在同一次 tick 之间，100 个附件时进度条停在 1% 很久，看起来像卡住；导入只有图标转圈，完全没有进度条。六个入口各管一段状态：导出期间能点导入，本地与 WebDAV 互不相干，并发导入要等对方跑完才发现 pending 已存在。
- 合并注意：`BackupProgress` 是导出与导入共用的类型，WebDAV 页目前不显示进度，只共用锁。`operation` 在 picker 打开之前就占据，取消 picker 时释放；picker 打开期间进程被回收时，回调里按 `operation == null` 补一次 `claim`；协程被取消时 `finally` 会释放并清空进度。`drawStopIndicator` 是 material3 1.5 的参数，升级 material3 时如果签名变化按本行改。进度文案里的 `%1$d` / `%2$d` 是条目数，改文案时八份 locale 的占位符要一致。

<a id="c42"></a>

## C42 备份页按本应用与官方 RikkaHub 分组

- 落点：`ImportExportTab` 拆成三个 `CardGroup`：备份内容（多选与一行说明）、本应用（导出到文件、从文件导入）、官方 RikkaHub（导出给 RikkaHub、从官方导入），进度行跟着运行的操作走；`backupContentItem` 去掉标题行，按钮组下方固定一行说明（聊天记录与设置始终包含，附件与工作区文件可选）；新增 `backup_page_group_app` 与 `backup_page_group_official` 两条文案，八份 locale 同步。
- 理由：官方兼容导出原来放在本应用那一组、从官方导入单独一组，同一个互操作方向被拆到两处；分组标题又和导出入口的标题重复。
- 合并注意：官方那组的两条既有文案（`backup_page_export_official`、`backup_page_import_from_official`）保留，它们是 `docs/custom/locale.md` 品牌检查里放行的条目。`CardGroup` 的 `title` 是普通参数，分组标题不在 `backupContentItem` 里，两个 tab 共用同一份组件。

<a id="c79"></a>

## C79 本地导出默认包含工作区文件

- 落点：`WebDavConfig.BackupItem` 新增 `LOCAL_DEFAULT_SELECTION = normalize(listOf(FILES, WORKSPACES))`；`BackupVM.localBackupItems` 从 `DEFAULT_SELECTION` 改为 `LOCAL_DEFAULT_SELECTION`。本地导出/导入页默认勾选附件与工作区文件；WebDAV 的 `webDavConfig.items` 默认仍是 `DEFAULT_SELECTION`（只带附件）；官方兼容出口继续在 `BackupManager` 里强制排除 `WORKSPACES`。
- 理由：用户要求本地导出默认带上工作区文件，避免每次手动勾选；工作区里的 PRoot 根文件系统与临时目录仍不打包，WebDAV 备份仍默认不带工作区，避免默认备份体积过大。
- 合并注意：本地导出和导入共用 `localBackupItems`，所以导入一份带工作区的备份时默认也会尝试恢复工作区文件；恢复仍是叠加语义，不删 live 里多出来的。官方出口的选择会被过滤，界面上勾着工作区也不会进官方归档。`Settings.webDavConfig.items` 的默认值没有变，旧设置解码不变；本地页的选择不写回设置，重新打开页面恢复默认勾选。不改 Room、Settings 序列化形状或备份格式。
- 测试：`BackupItemTest` 钉住本地默认含 `DATABASE/FILES/WORKSPACES`、WebDAV 默认只含 `DATABASE/FILES`；全量 `:app:testGithubDebugUnitTest` 787 条，7 条既有 Windows 失败未变；assemble、spotless、markdownlint 通过。设备上未单独打开备份页点验（当前抽屉设置入口自动化不可达）；常量由 `BackupItemTest` 钉住，`BackupVM.localBackupItems` 使用该常量，未实例化 VM 测试。

<a id="c80"></a>

## C80 用量成功失败区分与官方 schema 裁剪

- 落点：`UsageRecordEntity` 增加 `outcome`（`UsageOutcome.SUCCESS/FAILED/CANCELLED/UNKNOWN`），默认 `UNKNOWN`；Room 32 到 33 的 `Migration_32_33` 加列，`AppDatabase` 升到 33，`AppDatabaseFactory` 注册迁移。`UsageRecorder` 在 `generateText` 成功、流式正常结束、流式异常和取消时分别写 SUCCESS/FAILED/CANCELLED；流中途已收到的 usage 仍按 `finally` 记下。`UsageRecordDAO.getTotals` 增加只统计非 FAILED/CANCELLED 的 `cachePromptTokens`/`cacheCachedTokens`，总成本仍含所有行；统计页缓存命中率改用这两个字段，`-` 仍然处理 0 分母。官方兼容导出的 C18 裁剪落地：`OfficialSchemaTrimmer` 识别 `usage_record.outcome` 这一处已知额外列，重建表去掉该列、写回官方 v32 identity，`OfficialDatabaseAdapter` 在导出时裁剪；`OfficialDatabaseReader` 允许当前 Room 33 按 v32 快照读取并忽略已知额外列；`OfficialForeignAttachmentPaths` 对 33 也按 v32 快照探测；`OfficialDatabaseSchema` 增加 `ROOM_VERSION=33`、`ROOM_IDENTITY_HASH` 和 `KNOWN_EXTRA_COLUMNS`。
- 理由：`usage_record` 原来每次尝试一行但没有成功/失败状态，失败和取消的部分 usage 会混进缓存命中率；C80 保留失败成本，只让成功/未知进入缓存率，失败和取消都排除。Room 升版后官方兼容导出本来会因 schema 分叉失败，C80 只对已经明确登记的 `usage_record.outcome` 做裁剪，其它分叉仍然直接失败。
- 合并注意：`outcome` 是带默认值的 Room 字段，旧数据迁移为 UNKNOWN，迁移前的历史行仍按未知计入缓存率；`generateText` 抛错时没有 usage 可记，流式异常、取消、下游处理失败和流空闲超时才会带部分 usage；下游处理失败按 SUCCESS 记，空闲超时按 FAILED 记；空回复在 provider 层是成功请求，仍记 SUCCESS，是否重试由 AgentLoop 的 `EMPTY_RESPONSE` 决定；失败 attempt 的 attempt/retryId 没有单独落列，P3 的“或等价字段”按 outcome 收口；消息卡片仍只显示成功 attempt 的 usage。官方裁剪只处理 `usage_record.outcome`；C87 后另登记并 drop `file_derivations` 整张表，只按表名和列名判断、不重建外键、硬编码官方 v32；其它表/列分叉仍抛 `OfficialExportUnsupportedException`，官方升版时要同步 `OfficialDatabaseSchema.VERSION`/identity 和 trimmer；`OfficialDatabaseSchema.ROOM_VERSION`/`ROOM_IDENTITY_HASH` 随下次 Room 升级同步更新。32 到 33 是单向迁移，没有降级迁移；回退到 C80 前的构建时 Room 会拒绝打开 checkpoint 后的 v33 库，需要先保留迁移前的 v32 数据库副本或继续使用新构建。恢复判定改用 Room 版本 34：v32 官方/旧自家库走迁移链，同版本但 identity 未知且没有官方快照直接拒绝；Convert 分支只在 Room 版本等于官方快照时可达，planner 单测用 v32 覆盖。不改 Settings、备份格式或 `core/ai/`。
- 测试：`UsageRecorderTest` 覆盖生成成功、流式成功、取消、异常、下游处理失败、流空闲超时六种 outcome 和部分 usage；`UsageRecordDAOTest` 覆盖总成本含 FAILED/CANCELLED、缓存字段排除两者；`Migration_32_33_Test` 覆盖旧行默认 UNKNOWN 和新行默认；`LiveDatabaseMigrationTest` 在设备上把真实 app 库迁到当前 Room 版本；`OfficialDatabaseSchemaTest` 校验当前版本的额外列/额外表与 identity 已登记；`OfficialDatabaseReaderTest`/`WriterTest`/`SampleAcceptanceTest` 用 v33 读取并按 v32 快照写出；`OfficialSchemaTrimmerTest` 带 usage 行验证裁剪保留行数与 token、identity 写回和未登记额外列拒绝；全量 `:app:testGithubDebugUnitTest` 791 条，7 条既有 Windows 失败未变；`:ai:testDebugUnitTest` 324 条未变；assemble、spotless、markdownlint 通过。设备数据：`data.sync` 53 条全部通过（含官方导出裁剪、带行 trim 和未登记额外列拒绝）；数据库迁移/DAO/live 4 条通过；真实 app 库迁到 v33，仍 168 会话、61 个 upload 文件、476 条 usage 全部 UNKNOWN；官方兼容出口的 v33 快照被裁成 v32 后能被本仓库 v32 快照读取，官方 3.1.1 手动恢复未重跑。

<a id="c87"></a>

## C87 OCR 结果按 managed file 持久化，历史图片不再重复识别

- 落点：Room 34 新增 `file_derivations`，`file_id` 指向 `managed_files.id`，`kind=ocr`、`producer=ocr:v1`（C108 起为 `ocr:v1:<prompt+model 指纹>`），每个文件每个 producer 一条。`OcrTransformer` 优先读 derivation，其次读旧消息 `OcrMetadata.ocr_text` 并懒回填，再读缓存；只有没有 `managed_files` 行的旧附件才继续调用 `Conversation.withOcrTexts` 和 `ChatService.attachOcrTexts` 写回消息 metadata。删除 managed file 依赖 `ON DELETE CASCADE`；自家备份恢复清掉自动行、保留手改行（C110），官方导出仍由 `OfficialSchemaTrimmer` drop 这张表。设计见 [design/c87-file-derivations.md](../design/c87-file-derivations.md)。
- 理由：metadata 只对当前消息和历史分支有效；同一上传内容在别的会话复用时只能靠 3 天、64 条的缓存，过期后重复识别。文件级派生数据把 OCR 从消息附属物提升为文件属性，跨会话、跨进程和缓存过期都能命中。
- 合并注意：旧消息的 `OcrMetadata` 保留为兼容回退，不批量迁移；解析不到 `managed_files` 的图片仍走旧 metadata/缓存。C108 起 producer 为 `ocr:v1:<prompt+model 指纹>`，有文件行的图片在指纹 producer 下不再回退/回填旧 metadata，换设置后首次发送会重新识别；C109 起 `edited` 行的写入方在附件管理，C110 起备份恢复保留手改行、合并同内容文件时把落选手改行挂到保留行。备份恢复不再整表清空，自动行重新识别一次。官方 v32 读/写层不认识这张表，官方导出前会 drop；`OfficialDatabaseSchemaTest` 允许已登记额外表，未登记表由 `OfficialSchemaTrimmerTest` 在开发机验证会让导出失败。
- 追加（C108）：producer 指纹失效、OCR 缓存 key 带 producer，以及有文件行时禁止旧 metadata 重标为新 producer；producer 参数和测试见 C108。
- 测试：`FileDerivationRepositoryTest` 覆盖 put/get、相同文本、更新、edited 保护、空文本、批量读取、URL 解析和目录外拒绝；`OcrTextChoiceTest` 覆盖 derivation 优先、metadata 回填、无文件不回填和空值；`OfficialDatabaseSchemaTest` 覆盖 schema 34、identity、官方表加 `file_derivations`；`Migration_33_34_Test` 在开发机覆盖建表和外键级联；`BackupManagerTest` 在开发机覆盖自动行清空、手改行保留；`OfficialSchemaTrimmerTest` 在开发机覆盖 drop 表、未登记额外表和官方读取；全量 JVM、spotless 和 assemble 通过。设备链路：上传图片后 OCR 生成一条 derivation，同一会话第二轮和 force-stop 重启后的第三轮都只发聊天请求，derivation 始终只有一条。

<a id="c88"></a>

## C88 Exa 搜索正文长度上限

- 落点：`integrations/search/src/main/java/me/rerere/search/ExaSearchService.kt` 普通搜索的 `contents.text` 从 `true` 改成 `{maxCharacters: 2000}`，`mapSearchResult` 增加 `maxTextCharacters` 参数并在映射时截断，`mapScrapedResult` 截到 8000，抽出 `hasEvidenceOptions` 供请求与映射共用；`integrations/search/src/test/java/me/rerere/search/ExaSearchServiceTest.kt` 补三条并更新两条旧断言
- 理由：上游 #1569。普通搜索请求 `contents.text = true` 时 Exa 返回整页正文，10 条结果可达数十万字符；`SearchTools` 原样序列化给模型，超出上下文后被截断，模型解析失败会反复搜索，浪费 token 和搜索配额。请求侧限制是主修复，映射侧截断是兜底（API 不认 `maxCharacters` 时 `items[].text` 也不会失控；`answer` 和 `highlights` 不在这层限制内，highlights 由服务端按 URL 限 1200，`answer` 只来自 `type=deep`）
- 合并注意：普通搜索只要摘要，全文交给 `scrape`；带日期、域名或 `maxAgeHours` 的 evidence 搜索仍请求 8000 字符，映射时也按 8000 截断。截断标记是 `…[truncated]`，模型能看出内容被截。上游若改 Exa 请求体或结果映射，要保留这两处上限
- 测试：`:search:testDebugUnitTest` 18 条全过（新增两条并重写一条：普通搜索请求带上 2000 上限（由旧用例改写）、搜索映射截断超长正文、scrape 映射截断超长内容；三条旧断言从 `text == true` 改成 `text.maxCharacters == 2000`）；assemble、spotless、markdownlint 通过。没有真实 Exa 响应样本，判据是请求体断言和映射单元测试

<a id="c108"></a>

## C108 C87 OCR producer 指纹失效

- 落点：`FileDerivationRepository` 的 `get` / `listForFiles` / `putOcr` 接受 producer，新增 `ocrProducer(prompt, modelId)`，对提示词和模型 id 做 SHA-256 取前 8 字节生成 `ocr:v1:<hash>`；`OcrTransformer` 按当前设置计算 producer，读取、回填和写入都用它，文件缓存 key 也带上 producer。
- 理由：旧实现 producer 固定 `ocr:v1`，换 OCR 提示词或模型后仍会读到旧文本；缓存 key 只按 URL，也会绕过派生失效。
- 合并注意：升级后首次发送时，有文件行的图片会因 producer 变化重新识别一次；没有文件行的旧附件仍只能使用 metadata。旧 `ocr:v1` 行保留但不再被读取；`edited` 行仍不会被覆盖。producer 变化不删除旧行，避免误删历史；如果以后要清理，按 producer 前缀和编辑标记单独做。备份恢复的自动/手改处理见 C110。
- 测试：`FileDerivationRepositoryTest` 覆盖同配置指纹稳定、16 位小写十六进制、提示词或模型变化产生不同 producer、不同 producer 行互不覆盖；`OcrTextChoiceTest` 覆盖指纹 producer 下忽略 metadata 和缓存 key 带 producer；app JVM 测试通过。设备上的 OCR 重识别未在本次单独重跑。

<a id="c109"></a>

## C109 C87 OCR 文本编辑入口

- 落点：`FileDerivationDAO` 增加 `getAny` / `listAny` / `delete`；`FileDerivationRepository` 增加 `getAnyOcr`、`editOcr` 和固定 `OCR_MANUAL_PRODUCER=ocr:manual`。手改行 `edited=true`，在 `listForFiles` 中覆盖任何 producer 的自动结果；空文本删除手改行。`SettingFilesPage` 的图片卡片增加编辑按钮和底部输入面板，新增四份 locale 文案。
- 理由：自动 OCR 对表格、手写或特殊排版可能出错，需要用户直接修正；手改文本必须跨 OCR 提示词/模型变化继续生效，不能下次自动识别又被覆盖。
- 合并注意：手改行与自动行分开存，不删除自动行；`putOcr` 看到任意手改行就不覆盖。空文本只删手改行，让自动结果重新可见；不提供清空自动结果的入口。备份恢复会保留手改行，同内容文件合并时由 UploadNames 把落选手改行挂到保留行；官方导出仍会 drop 整张表，手改文本不随官方导出保留。UI 只对 `image/*` 显示编辑按钮，保存相同文本视为成功。编辑入口只改文本，不改文件本身或消息 parts。
- 测试：`FileDerivationRepositoryTest` 覆盖手改覆盖自动结果、手改后 `putOcr` 不写入、空文本删除手改后自动结果恢复；app JVM 测试通过。设备上打开附件管理，编辑并保存一张图片的 OCR 文本，出现保存成功提示，重新打开显示手改文本。

<a id="c110"></a>

## C110 C87 备份恢复保留手改 OCR

- 落点：`FileDerivationDAO` 增加 `clearGenerated()`，`BackupManager.stageDatabase` 恢复时只清 `edited=0`；`UploadNames.rewriteManagedFiles` 在合并同内容文件、删除落选 `managed_files` 行之前，把落选行的 `edited=1` 派生文本 `INSERT OR IGNORE` 挂到保留行；`OfficialDatabaseConverter` 对同版本外来路径转换也按原 `file_id` 补回手改行。
- 理由：手改 OCR 是用户内容，不是可重新生成的缓存；整表清空会让恢复后的附件丢掉手工修正。自动行仍可重新识别，不需要保留。
- 合并注意：保留行已有同 producer 的手改时保留保留行，不覆盖；落选手改行随后显式删除，避免外键未开启时留下孤儿。同版本外来路径转换会由 `OfficialDatabaseConverter` 按原 `file_id` 补回手改行，再做 UploadNames 合并。官方导出仍 drop 整张表，手改文本不随官方备份保留。恢复后首次发送会重新识别自动行，但手改行继续覆盖。
- 测试：`BackupManagerTest`（开发机）覆盖自动行清空、手改行保留，两份同内容上传合并后只保留落选行的手改文本，以及同版本外来路径转换后手改行仍在；`connectedGithubDebugAndroidTest` 通过。

<a id="c142"></a>

## C142 message_node 复合索引

- 落点：`MessageNodeEntity` 的索引从 `conversation_id` 改为 `(conversation_id, node_index)`；`AppDatabase` 版本升到 35，新增并注册 `Migration_34_35`（只建复合索引、删旧单列索引，不重建表），Room 生成 `app/schemas/.../35.json`；新增 `Migration_34_35_Test` 验证旧索引被替换为复合索引。来源是上游 `c1c3c4d1`。
- 理由：按会话加载消息时按 `node_index` 排序分页，只有 `conversation_id` 索引时每页都要把整个会话的行取出来排序，几千条消息的会话打开会明显变慢；复合索引仍以 `conversation_id` 开头，外键和按会话查询照常可用。
- 合并注意：本分支的 schema 33 已被 `usage_record.outcome` 占用，不能直接套上游的 32→33 迁移，所以另起 34→35；这是纯索引迁移，不改表数据，也不能用 AutoMigration（Room 会重建整张 `message_node`）。官方兼容导出只按表/列判断分叉，索引差异不在 `canReadAsV32` 检查里，所以 `OfficialSchemaTrimmer.trim` 会额外把复合索引换回官方单列索引，避免导出库带着自定义索引冒充官方库；官方客户端上的导出验收已在 2026-11-21 复验，见 [`backup/acceptance.md`](../backup/acceptance.md)。
- 测试：`:app:kspGithubDebugKotlin` 生成 35.json，`:app:compileGithubDebugKotlin`、`:app:compileGithubDebugAndroidTestKotlin` 通过；设备上执行 `:app:connectedGithubDebugAndroidTest` 的 `Migration_34_35_Test` 通过，确认旧索引被替换为复合索引；`OfficialSchemaTrimmerTest` 增加断言，导出后 `message_node` 只有官方单列索引；2026-11-21 在 K60 上走真实导出出口复验：导出库 `user_version` 32、identity 是官方 v32 值、`message_node` 只有 `index_message_node_conversation_id`，官方 RikkaHub 3.1.1 恢复后计数与导出一致，会话里的测试图片正常显示。

<a id="c158"></a>

## C158 导出备份选择具体工作区

- 落点：`WebDavConfig.workspaceIds: List<String>?`，null 表示全部，旧设置解码为 null；`WorkspaceBackupSelection.kt` 的 `toggleWorkspaceSelection`/`effectiveWorkspaceIds` 负责把显式全选归一成 null、把选择与现存 id 求交。本地导出用 `BackupVM.localWorkspaceIds`（null 全部，接 `WorkspaceRepository` 后只列 `localFiles` 的工作区），WebDAV 页把选择写进配置；`BackupManager.createBackup` 新增 `workspaceIds` 参数，`collectWorkspaceFiles` 按 id 过滤。恢复侧 `BackupManager.stageRestore` 返回 `RestoreStagingInfo`：`stageDatabase` 读出本机工作区，和本次计划恢复的工作区条目 root 对比；本次勾选恢复工作区文件且归档至少带了一个条目时，把没有文件的工作区列进 `BackupDialog` 的恢复提示，安全模式导入也接住了这个返回值。官方出口把 `workspaceIds` 置 null。新增 7 条文案（多选标题、全选提示、默认全选、空列表、未选择警告、导出专用说明、恢复缺文件提示）。
- 理由：工作区可能很大，之前只有全局开关，导出前应该能只挑需要的；备份里没有文件的工作区恢复后仍然存在，本机已有文件不会被清掉。
- 合并注意：数据库与设置始终全量导出，选择只影响导出；本地页的「Workspaces」总开关仍控制导入是否恢复工作区文件，具体工作区多选只作用于导出。显式选择覆盖全部现存工作区时归一成 null，之后新建的工作区默认包含；只选一部分时，新建工作区要重新勾选。归档里完全没有工作区条目、或本次导入没勾恢复工作区文件时都不提示缺文件。恢复是叠加语义，不会清掉本机已有文件。官方兼容出口仍不打包工作区。S3 备份已在 C9 移除，本项只覆盖本地与 WebDAV。`workspaceIds` 是带默认 null 的新 Settings 字段，旧设置与旧备份解码行为不变，验收记录见 [`backup/acceptance.md`](../backup/acceptance.md) 的「2026-11-22 C158 重跑」。
- 测试：`BackupWorkspaceSelectionTest` 11 条（全选、显式选择、空选择、全选归一、过期 id、缺文件提示、WebDAV 配置默认与往返）；`BackupManagerTest` 新增 4 条（按选择打包、恢复提示、没勾恢复工作区不提示、归档没有条目不提示）；`:app:testGithubDebugUnitTest`（JBR 21）、`:ai:testDebugUnitTest`、`spotlessCheck`、markdownlint 通过；K60 上跑 `data.sync` 设备包，并点验本地与 WebDAV 页的默认全选、一个都不选的警告和全选按钮。
