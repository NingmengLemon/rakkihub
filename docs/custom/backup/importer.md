# 导入器

转换：把备份里的库读出来，写进一份新建的当前版本库，同时把附件路径重写成本机。有两条入口，见下节。

## 现状

导入器已落地（C20 到 C23）：读取层、多版本快照、写入、路径重写与接入方式都在。验收覆盖读取层与转换产物两层（C24）；整份归档的附件与 settings 由 C26 覆盖，实际跑在 v32 上。

## 两条入口

1. `OfficialRestorePlanner` 判为 Convert。条件是双方版本号相同、identity 不同，也就是官方在同一个版本号上改了结构；identity 读不出来、或该版本没有快照时不是转换而是直接拒绝。官方 v32 的 identity 与本仓库相同（`c64deeb13766eaf6528a2f4f6965c836`），这一条现在不会触发；我们改结构时会把版本号升上去，届时官方 v32 备份仍是「版本低」，落在迁移链，走的不是这一条。
2. 判为迁移链之后，`OfficialForeignAttachmentPaths` 探测到别的安装的附件路径（C25）。这一条现在就在跑：官方包名是 `me.rerere.rikkahub`，本应用是 `top.lemoneko.rakkihub`，带附件引用的官方备份与本 fork 改包名前的备份都会命中。没有附件引用的备份不命中，照常走迁移链。

实际走哪条取决于库本身的版本与附件引用：官方备份带附件引用时命中第 2 条，没有附件引用的照常走迁移链；第 1 条要等双方版本号撞上才用得到。

## 要解决的问题

迁移链只能把结构上可迁移的库升上来。两种情况它处理不了：

- 官方库版本高于我们：没有 downgrade 路径。
- 官方库的 identity 与我们不同（只可能出现在版本号相同时）：Room 拒绝打开。

还有一类是迁移链能处理、但不该交给它的：结构对得上，甚至版本更低，可库里存的是别的安装的绝对 `file://` 路径。迁移链不改数据，装进来之后附件全部失配，所以这类也走转换器（C25）。

另外，两边一致但我们改了某个字段的含义这种情况，任何基于 header 的判定都识别不出，见 [`README.md`](README.md) 的分流一节。

## 接入方式

分叉之后，导入器不改正在用的库，而是造一份结构等同于当前版本的库，再走现有安装流程：

1. 解压到 `PendingRestore.createStagingDirectory()` 的 `preparing-*` 目录，保留 `rikka_hub-wal` 这类旧格式条目。
2. `OfficialDatabaseReader` 把官方库读成行数据。
3. 在 staging 的 `payload/database/` 下新建空的 Room 库，把行数据写进去。
   - 文件名必须是 `rikka_hub`：`PendingRestore.buildEntries` 按 `database/<name>` 识别，`targetFile` 也只接受这个名字，其他名字会在 apply 时直接报错。
   - 写完后按现有顺序收尾：`try { checkpoint } finally { close }`，再 `removeSidecars`。留着非空的 `-wal` 会让 `buildEntries` 对同一路径生成两条 entry。
4. settings 按原流程迁移、解码、重新编码，`launchCount` 一并带进 pending 的 `settings.json`。
5. `PendingRestore.publish(staging)`，重启时原子替换。

payload 的布局沿用现有约定：`payload/database/rikka_hub`、`payload/files/<附件路径>`，`settings.json` 放在 staging 根。payload 为空时 `apply` 会写一份空 journal 然后返回 true，也就是「静默成功、什么都没装」，所以导入器必须确认至少写入了数据库或附件。

## 读取层

`data/sync/official/OfficialDatabaseReader` 提供只读访问，打开时用 `PRAGMA table_info` 与 `user_version` 核对结构，结构与类型的判断抽在 `OfficialSchemaVerifier` 里，不依赖 Android，可以直接跑 JVM 测试。

- `SQLiteDatabase.openDatabase(..., OPEN_READONLY)` 打开。不跑 integrity_check，那是 `DatabaseBackup.normalize` 的职责；导入器在解压后先调一次 `normalize` 把 `-wal` 合进去，再读。
- 不加载 Room、不引用本仓库的 `@Entity`，表名列名按库里的 `user_version` 从 `OfficialSnapshots` 选版本快照。
- 只读业务表，`message_fts*` 跳过。
- 逐行迭代，不 materialize：每张表产出 `Sequence<OfficialRow>`。`OfficialRow` 携带表名与列值，列值按 affinity 在读取时转成确定的类型，行对象不依赖 cursor。读取时校验：快照里有、库里没有的列失败；类型与快照冲突失败；表缺失失败；库里多出来的列忽略并记日志。
- 附件目录与嵌套规则引用 `OfficialBackupLayout`，不要在这里重列一遍。
- 读取层里碰 SQLite 的部分只能在 androidTest 里验证，工程没有 JVM SQLite 或 Robolectric；结构与类型的判断已经抽成纯函数，那部分跑 JVM 测试。

## 写入

写入目标是新建的空库，不是正在用的库：没有清空这一步，也没有与运行中会话抢写的问题。

- 一个事务包住全部写入，失败即整个临时库作废。
- 表顺序按外键：先父表后子表，顺序由 `OfficialTableOrder` 从快照的外键推出，循环外键直接报错。
- `message_node` 的 `node_index`、`select_index` 与 id 原样保留。`ConversationEntity.nodes` 是遗留空列（v12 起恒为 `"[]"`，树在 `message_node` 里），照抄即可。
- 自增表（`GenMediaEntity`、`managed_files`、`usage_record`）显式写 id；SQLite 会自己把 `sqlite_sequence` 提到显式 id 之上。
- JSON 字段（`UIMessage`、`config`、`toolApprovals` 等）原样搬运，不解析再序列化。解析会丢未知字段，也会把官方的格式变化变成我们这边的转换错误。
- 快照里有、我们当前的 `@Entity` 没有的列：拒绝导入这一版，不按默认值硬填。转换器在动手写之前就做这项对账，缺列时给出明确信息而不是让插入失败。
- 除文本列的附件路径重写（见下节）之外，其余列逐列照抄，目前没有字段映射表：两边没有分叉，所有列的处理方式都是「直接照抄」。真正需要区分「照抄还是转换」是在我们改字段结构之后，届时要为每个官方版本维护一张逐列的映射表，并配一条完整性测试，保证表里覆盖了该版本快照的每一列。

## 附件路径重写

附件引用是绝对 `file://` URI，包名在路径里。写入文本列前要把 `file:///data/user/<用户号>/<包名>/files/` 与 `file:///data/data/<包名>/files/` 这两种前缀换成当前应用的 files 目录（正则就是 `file:///data/(?:user/\d+|data)/[^/]+/files/`），否则导入到改过包名的应用后附件全部失配。官方备份里的消息引用普遍是这种绝对形式；助手头像与用户头像在 `settings.json` 里，方向相同但重写发生在 settings 分支。只认这一种形状，不绑定包名；JSON 整段做字符串替换，不解析。

等附件引用改成相对形式之后，这一步对自家归档不再需要，但官方备份进来的方向仍然是绝对路径，重写要一直保留。

## settings

官方 `settings.json` 的处理与 `stageRestore` 一致：`SettingsJsonMigrator.migrate` → `JsonInstant.decodeFromString<Settings>`，然后按现有方式重新编码写回 pending。

解码之前有一层直接作用在原始 JSON 上的检查（`BackupSettingsShape`，C28）：把原文解析成 `JsonObject`，看关键项在不在、类型对不对，不合格就在 publish 之前失败。它要求 `assistants` 与 `providers` 必在、必为数组、元素必须带字符串 `id`，`displaySetting` 必在必为对象，`init` 若存在必须是布尔值，几个 id 字段若存在必须是字符串。

这层只挡「根本不是设置文件」的情况，不校验完整 schema：旧备份缺新字段是正常的，缺省由解码器的默认值补齐。完整的独立解析器（冻结的 `OfficialSettingsV32` 加字段映射）与写入侧的字段映射是同一件事，在 `Settings` 开始与官方分叉时一起做。

钩子放在共用的恢复入口（`stageRestore`）上，所以官方 v32 走那条路时也会跑。

`launchCount` 不属于 `Settings`，由 `PendingRestore.apply` 在启动前与 settings 一起写入，不在运行时单独设。

## 维护时机

- 官方升 schema：用 `tools/gen-official-snapshot.ps1` 生成新快照，在 `OfficialSnapshots` 注册一行；读取层只认清单里的版本，不在清单里的版本明确失败。
- 我们自己改 schema：补齐迁移链，确认转换器对账的表与列仍然覆盖，再重跑 [`acceptance.md`](acceptance.md) 的样本。
- 我们改字段含义：先按 [`../README.md`](../README.md) 的「数据结构」一节建立写入侧的字段映射表，再动字段。语义漂移只有这一处能发现。
