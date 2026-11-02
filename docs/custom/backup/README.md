# 备份与恢复

官方客户端与本应用的备份是同一种自包含 zip，恢复也共用同一条 `BackupManager.stageRestore`。本目录记这条链路的现状、转换路径（把别的安装的库读出来、改写成本机的结构与路径），以及验收方式。

| 文档 | 内容 |
| --- | --- |
| `README.md` | 归档格式、恢复流程、分流判据、附件替换、原件归档、索引重建、已知限制 |
| `importer.md` | 转换：两条入口、接入方式、读取层、写入、附件路径重写、settings |
| `acceptance.md` | 验收判据、样本类别、规范化规则、跑法、当前覆盖范围 |

## 归档格式

备份是一个自包含 zip，不需要云端：

```
settings.json                              整个 Settings 的序列化，额外带 launchCount
rikka_hub.db                               VACUUM INTO 出来的标准 SQLite 快照
upload/ skills/ fonts/ media_creation/     附件，可选择不备份
workspaces/<root>/files/                   工作区文件区，默认不打包（C29）
```

前三条是官方也认识的条目；`workspaces/` 是本仓库自己加的，官方格式里没有，所以官方兼容出口既不打包这一类条目、也会从 settings.json 里去掉工作区这个选项（未知枚举值不像未知字段那样能被忽略，带着它官方会整份设置解码失败）。勾选工作区之后，每个工作区的文件区按 `workspaces/<root>/files/<relative>` 打包，与 `filesDir` 下的实际布局一致，恢复侧因此复用同一条 `files/` 映射。工作区里的 PRoot 根文件系统（`<root>/linux`）与临时目录（`<root>/tmp`）不进归档。

`rikka_hub-wal` 与 `rikka_hub-shm` 也出现在恢复逻辑里，但当前版本导出时不产出这两个条目，恢复侧保留它们只是为了兼容更早的备份。

导出走本地 SAF 与 WebDAV 两种出口，共用 `data/sync/BackupManager.kt` 的同一套归档格式。上游另有 S3 出口，本仓库已按 C9 移除。

## 恢复流程

`stageRestore` 在暂存阶段就把库打开并迁移，不是等到启动：

1. 解压归档到 `PendingRestore.createStagingDirectory()` 出来的 `preparing-*` 目录。
2. 对暂存库跑 `DatabaseBackup.normalize`，把源库的 `-wal` 合进主文件。
3. 分流：只读 `user_version` 与 `room_master_table` 的指纹，判定迁移链 / 转换 / 拒绝（见下一节）。判为迁移链时，只要源版本有快照，还会探一次附件路径，带着别的安装的 `file://` 前缀就改走转换器。
4. 需要转换时，先把备份里的库读出来写进一份新建的当前版本库，用它替换暂存库（见 [`importer.md`](importer.md)）。
5. 用 `AppDatabaseFactory.create(context, stagedDatabase.absolutePath)` 打开暂存库。Room 在这里校验 identity hash，并沿迁移链把低版本库升到当前版本。版本高于本仓库的库在第 3 步就被拒绝了，只有元数据读不出来时才会落到 Room 的 `onDowngrade` 抛错。
6. 在 `try` 里 `DatabaseBackup.checkpoint(room.openHelper.writableDatabase)` 把 WAL 合进主文件，`finally` 里 `room.close()`，最后 `removeSidecars` 清掉 `-wal`/`-shm`。
7. `PendingRestore.publish(staging)`：只把 `preparing-*` 改名为 `pending`，此时还没落盘。
8. 重启后 `PendingRestore.apply` 在 Koin、Room、SettingsStore 初始化之前建 journal、原子替换文件；失败则整体 rollback。

失败都在 publish 之前发生，staging 在 `finally` 里删掉，不会污染正在用的数据。

同一个 zip 换到另一个安装（官方版、我们的 debug 或 release、旧包名的构建）上恢复时，迁移链不改数据。官方备份、C36 之前的自家备份与设置里可能留着生产者的绝对 `file://` 路径，附件会失配；这种情况在分流时被改道。C36 之后新写或重存过的消息引用是相对形态，换安装后按当前安装解析；没再存过的老会话仍是绝对路径，跨安装恢复仍会命中探测改写。

## 分流

分流放在 `stageRestore` 处理暂存库的地方：本地导入与 WebDAV 导入最终都汇到这里，判定发生在读任何业务表之前，只碰两处元数据（`user_version` 与 `room_master_table` 的 schema 指纹）。判定本身抽在 `OfficialRestorePlanner`，是纯函数，可以直接跑 JVM 测试。

```
ourVersion：编译进应用的当前 Room 版本
ourIdentity：编译进应用的 schema 指纹

version > ourVersion   → 拒绝（迁移链降不了版本；官方升版通常是加列，硬写会在插入时崩）
version < ourVersion   → 迁移链
version == ourVersion：
    identity == ourIdentity → 迁移链
    identity 缺失           → 拒绝（来源判不出来）
    其他                    → 转换（用该版本的官方快照读，写成当前版本）
```

三点说明：

- 版本先行：`user_version` 决定能不能交给迁移链，指纹只在版本相同时用来判「结构被改过」。
- `ourVersion` 与 `ourIdentity` 都用编译进应用的常量，不从活库读。升级后首次启动时活库可能还停在旧版本，读它会把官方新版本误判成「版本更高」；全新安装时活库文件可能还不存在。
- 「版本低、identity 未知」交给迁移链试，那可能是自家旧库，也可能是结构兼容的官方旧库。迁移链打不开时会明确失败，不会写坏数据。

还有一种三种判定都拦不住的：两边 DDL 与 identity 都一样，但我们改了某个字段的含义。Room 会照常打开，读出来的是错的。identity 是 schema 的 hash，不含字段语义，所以它不会因为语义变化而变，任何基于 header 的判定都识别不出这一种。它只能靠流程约束兜住，具体规则见 [`../README.md`](../README.md) 的「数据结构」一节。

这张表只描述 `OfficialRestorePlanner`。判为迁移链之后还有一次探测：`OfficialForeignAttachmentPaths` 会扫一遍快照覆盖的文本列，只要出现 `file:///data/` 且不落在当前安装的 files 目录前缀（`file:///data/user/<用户号>/<包名>/files/`）下，就改走转换器重写路径（C25）。探测的对象是「判为迁移链的库」，也就是版本低于我们的库，以及版本相同、identity 相同的库；带附件引用的官方备份、C36 之前别的包名（debug、release、改包名前的构建）产出的备份、自家归档里还没重存过的别的安装的绝对引用，都会命中。没有 `file:///data/` 文本，或者文本列里的 `file:///data/` 都落在当前安装前缀下的备份不命中，照常走迁移链；当前安装的旧 `/data/data/` 形态不在这个前缀里，会命中并重写一遍，结果不变。相对形态的消息引用不会命中这个探测：它们按当前安装解析。

## 附件替换

归档里的四个目录是 `upload/`、`skills/`、`fonts/`、`media_creation/`，其中 `skills/` 与 `media_creation/` 会带子目录。目录与嵌套规则以 `OfficialBackupLayout` 为准。

`PendingRestore.buildRemovalEntries` 对归档里实际存在的附件目录，列出 live 目录里有、payload 里没有的文件，给每个文件合成一条 `install = false, hadOriginal = true` 的条目：apply 把目标移进 `originals/`，rollback 再移回来，journal 与 rollback 都不需要改，每条也仍然可重入。

做成文件级条目而不是目录级 rename，是因为 journal 与 rollback 都按路径记录单个文件，加目录条目得为它们另加一套状态。

两个边界：

- 只在数据库也一起替换时才做。只导附件时现有库还引用着 live 上的文件，删掉它们等于制造悬空引用，所以那种组合保持叠加语义。
- 归档里「存在但为空」的目录无法表达：写归档的侧不写目录条目，读归档的侧也会跳过目录条目，所以空目录与「没有这个目录」在 payload 里长得一样。这一条记在下面的已知限制里。

## 原件归档

导入入口（`WebDavSync.restoreFromBackupFile`）在 `stageRestore` 返回之后把原始 zip 复制一份到 `noBackupFilesDir/imported-backups/`，永不自动删除。它是官方自己产出的自包含文件，随时能被官方吃回去，也是导入写坏数据时唯一能回退的东西。

归档发生在 `publish` 之后：那时库还没真正换上，安装要等下一次启动的 `apply`。所以后面那次安装失败时，归档里仍然留着这份原始备份，这正是它该在的位置。

只在 `stageRestore` 成功之后归档：失败的恢复没有改动任何数据，不需要为它留底，留着只会堆积。

`ImportedBackupArchive.store` 先写临时文件再原子改名，进程中途被杀不会留下截断的归档；文件名带毫秒并且不覆盖已有文件，同一秒内多次导入不会互相盖掉，同毫秒撞名时追加序号。它不依赖 Android，可直接用 JVM 测试覆盖。

放 `noBackupFilesDir` 而不是 `filesDir`：后者的整个目录被 `app` 模块的 web 路由 `GET /api/files/path/{...}` 暴露出去。

归档目前在应用内没有入口：`noBackupFilesDir` 不在 FileProvider 的共享路径里，web 路由也不服务它，取回需要 root 或 adb。加一个「已归档备份」的列表页是后续的事。

## 索引重建

`message_fts*` 不在 Room 的 schema 里，`AppDatabaseFactory.onOpen` 只保证表存在，内容要重建。要不要重建由 `message_fts_state` 表里的 `rebuild_complete` 决定，表与判定都属于 C27。

恢复成功那一次要触发重建。`PendingRestore.apply` 返回 `Boolean`，`RikkaHubApp.onCreate` 把它提到 `try` 块外面接住，在 `startKoin` 之后用这个值触发，否则那时还没有 `ConversationRepository`。

只在当次启动触发不够：进程在重建完成前死掉，或重建中途失败，下次启动没有任何标记。所以除了当次触发，常规启动也要自检：有会话且（`message_fts` 为空或上次重建没跑完）时重建。自检放在 Koin 之后的后台任务里，不阻塞启动。

重建失败时不清任何标记，下次启动会重试。

## 已知限制

- 数据库被替换时，导入是替换语义，不与现有数据合并。
- 工作区文件是叠加语义：恢复只写入归档里有的文件，不删 live 里多出来的。附件那套替换语义没有套过来。归档里出现 `workspaces/<root>/linux/...`（根文件系统）一律拒绝，写坏或被改过的归档不能覆盖 PRoot 根文件系统，这一点不看有没有勾选工作区。
- 恢复工作区文件要求导入时勾上工作区：条目在归档里、当前选择不含它时会被跳过。新设备上默认选择不含工作区，所以导入一份带工作区的备份之后，库里那些工作区的文件还是空的，启动自检会把它们标成 BROKEN，之后要重新装 rootfs 才会恢复。
- 官方兼容出口（`BackupTarget.OFFICIAL`）不带工作区文件，也会从 settings.json 里去掉工作区这个选项：官方那套格式里没有这个条目，官方那个版本的枚举也不认识它。它还会把附件路径换成官方安装的位置（C30）：官方恢复只搬附件、不改路径，不换的话装过去之后附件全断。库那边替换本应用的两种绝对形态（`/data/user/<用户号>/<包名>/files/`、`/data/data/<包名>/files/`）与消息库里的相对形态（`"url":"file:upload/...`，C36），settings 那边用的是「任何安装的绝对 files 路径」那条正则。schema 分叉后的裁表裁列仍待实现。
- 官方兼容导出的路径里，`/data/user/<用户号>/` 这一形态的用户号沿用导出侧的安装：官方装在同一 Android 用户下才对得上，跨 profile 会失配。`/data/data/` 那一形态不带用户号，系统按 user 0 解析。
- 反过来，自家备份的 settings.json 里会留下 `WORKSPACES`，早于这个功能的构建读到它会整份设置解码失败。降级本来就有代价（库的 schema 版本更高也打不开），这一条只是又多了一处。
- 同一个方向：C36 之前的构建读到新库里的 `file:upload/...` 时图片大概率显示不出来，点音频、视频的附件可能抛异常，文档附件会退化成 ERROR 文本；数据不丢，升级后恢复。
- 附件替换只在 payload 同时带数据库时发生。只导附件的恢复是叠加：现有库还在引用 live 上的文件，删掉它们等于制造悬空引用。
- 聊天草稿（C31）不进备份：恢复之后的启动清理只留库里还存在的会话与上次打开的会话，所以与备份重叠的会话会带着恢复前写的草稿，里面的附件引用可能指向已被替换或删掉的文件。
- 归档里「存在但为空」的附件目录无法表达：写归档的侧不写目录条目，读归档的侧也会跳过目录条目，所以空目录与「没有这个目录」在 payload 里长得一样，替换时会退化成合并。`skills/` 被清空后留下的空目录会被 `SkillManager` 当成待清理目录。
- `upload/` 与 `fonts/` 在导出侧只收顶层文件，替换侧却是递归删除。payload 同时带数据库时，live 里这两个目录下的子目录文件会被删掉，因为它们不可能出现在任何 payload 里。这符合替换语义，但值得知道。
- `media_creation/` 下的 `.part` 临时文件导出时被跳过，替换时会当成「备份里没有」而删除。它们是半成品，删掉通常无害，但下载中的任务会受影响。
- 读取层与写入层的单元测试仍用应用自己创建的库当替身；真实官方样本走 `OfficialSampleAcceptanceTest`，只在设备上有样本时执行。
- live 文件名里带反斜杠会让整次导入与整次导出失败。导入的校验放在 `buildRemovalEntries` 里、journal 落地之前，所以失败会走 `failed-*` 分支：用户看到「已保留原数据」，下次启动不会重播。漏掉这层校验的话，`apply` 会反复抛异常，而 `RikkaHubApp` 只捕获 `RestoreFailedException`，结果是每次启动都崩。导出侧 `BackupManager.collectAttachments` 走同一条校验，会直接抛错，得先把这个文件移走。这种文件名不需要 root 就能产生：`FileUtils` 从 provider 的 displayName 取扩展名，`foo.\x` 会拼出带反斜杠的名字。
- 解压归档不设条目数或体积上限：备份由用户自己选，`stageRestore` 逐条流式写入并在失败时清掉暂存目录，但一个异常大的归档仍可能先把设备磁盘写满。
- 归档目前在应用内取不回来，需要 root 或 adb。
- 字段语义变化无法从 header 识别，只能靠流程。
- 附件已经按内容寻址（C32），但去重只作用于新写入：归档里的文件按原文件名落盘，导入不会把同内容的文件归并成一份；存量文件的随机名也不会自动改成内容指纹。两份同内容的旧文件会一直各自存在，直到各自没人引用后被清理收走。
- 官方兼容导出（C18）有骨架，分叉时的裁剪仍待实现，不在本目录范围。
