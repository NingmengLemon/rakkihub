# 长期路线

本文件记方向、阶段划分与策略。具体定制的落点见 [`checklist.md`](checklist.md)，数据层的现状见 [`backup/README.md`](backup/README.md)。

## 定位

`custom` 目前是上游的个人定制分支，同时保留着最终独立成单独应用的可能：自己的 CI、按自己需求迭代、对数据层动手、独立分发。

是否真的走完这一步，取决于两件事的成本对比：继续跟随上游的合并成本，和维护独立分叉的成本。本文件的作用是让那个决定在某天变得可执行，而不是现在就预设答案。

真正不可逆的那一步是改包名，因为它让新版本与旧版本成为两个应用，迁移只能靠备份导出导入。包名已经改了（见阶段 2.1），所以现在只剩继续跟随与独立分叉之间的取舍。

当前的位置：阶段 2.1 的包名与构建侧改造已完成，阶段 1 的导入器与 settings 形状校验都已落地（C20 到 C28）、主要判据验过，数据结构的冻结随之解除。官方兼容导出已经开始裁 `settings.json`（去掉官方枚举不认识的选项），库与表那一侧的裁剪要等双方结构分叉才有内容可做。导入器加迁移链已经能给整个数据层兜底，因此数据层改造不再有前置阻塞。

## 阶段 1：易迁移

目标：官方客户端导出的备份，必须能导入本 fork。

现状：导入器已落地（C20 到 C26），官方客户端导出的备份能导入本 fork。带附件引用的官方备份现在就会过转换器，因为它的附件路径属于官方包名（C25）；planner 的 Convert 入口只在双方版本号相同而结构不同时才会用到，我们改结构、升版本号之后官方旧备份仍落在迁移链。验到的范围与缺口逐类列在 [`backup/acceptance.md`](backup/acceptance.md)。

为什么用导入器而不是「绝对不动存储结构」这种硬约束：区别在成本形态。硬约束是一次性的「不许动」，便宜，但把数据层封死；导入器是持续的「动了要维护一个转换器」，贵，但换来自由。这里选后者。

这笔自由不是无代价的。动了结构之后，升级会触发迁移，回退到旧版本会因为库的 schema 版本更高而无法打开。要用回退换数据层的自由，这一点得先认。

备份的归档格式、恢复流程与分流判据在 [`backup/README.md`](backup/README.md)，转换路径在 [`backup/importer.md`](backup/importer.md)。

### 设计要求

八条约束数据层怎么改。前两条解决「读官方库不依赖我们的 schema」，第三条解决「两种来源要分清」，后几条解决自家数据与官方兼容导出。

1. 独立只读层，不依赖 Room migration 链。

   截至上游 3.1.1，custom 与官方发布版同为 v32 且 schema 一致（31 到 32 是 `AppDatabase` 里的 `AutoMigration`，`workspaces` 表加 `type` 与 `ssh_config`），这条张力暂时不出现。开始改结构之后就变了：`AppDatabaseFactory.create` 打开官方库，要么因为版本号更高而失败，要么在双方版本号相同、语义已经不同时静默读错。后者更危险，因为没有任何报错。

   所以导入器不能用我们自己的 `@Entity` 定义去解析官方库，必须以裸 SQL 读出来，再写进我们自己的库。写入选行级照抄而不是先转领域模型：JSON 字段原样搬运能保住未知字段，也避开了再序列化带来的格式漂移。

   这一条只解决解耦方向，即我们的 schema 怎么演进都不影响读取官方库。它不等于可以随便改结构，我们自己的备份恢复仍要走自己的迁移链，见要求 6。

2. 官方 schema 在读取层里冻结。

   读取层自带一份官方 schema 的快照，不随我们的库演进。快照停在官方 v32，官方日后再升版时加一份新快照。

3. 单向转换，但自家数据必须有往返路径。

   我们新加的字段官方必然不认识。要求 8 说的「导出能被官方读回」指的是在导出侧把官方不认识的表与列裁掉，不是让官方理解我们的新增字段。

   但改包名的迁移是「旧版本导出、新版本导入」，旧版本可能是本 fork 的旧构建，它的库里已经带着我们自己的 schema 演进。所以导入侧必须同时接受两种来源：官方 schema 的库靠读取层，我们自己的历史 schema 的库靠迁移链。

   两种来源必须先能分清。版本号靠不住，双方可能同号异义。Room 在 `room_master_table` 里为每个数据库保存了 schema 的 identity hash，可作判定依据；判定必须在读取任何业务表之前完成，判不出来就明确失败。

4. 导入时归档原始 zip。

   把官方产出的原始备份另存一份，永不删除。它本来就是官方自己产出的自包含文件，随时能被官方客户端吃回去。归档负责保命，转换器负责搬家，两者解耦之后改数据的胆子可以大很多。

   归档做在本地导入与 WebDAV 导入共用的 `restoreFromBackupFile` 里，在 `stageRestore` 成功之后执行，放 `noBackupFilesDir` 而不是 `filesDir`（后者整个目录被 web 路由暴露）。

5. `settings.json` 比数据库更麻烦，要单独设计。

   数据库是 SQLite，schema 好冻结。`settings.json` 是整个 `Settings` 数据类的序列化，这一个类横跨 provider 配置、assistant 模板、UI 主题、联网配置、备份配置和各种开关，`SettingsFields.kt` 把它拆成 DataStore key 的映射就有一百多行，上游自己也在用 `SettingsJsonMigrator` 在这个类上演进。这里同样需要一个「官方 Settings JSON 到我们的模型」的独立解析器，不能复用我们自己的反序列化。

   形状校验已经落地（C28）：检查原始 JSON 的关键项与已出现字段的类型（`assistants` 与 `providers` 的元素须带字符串 `id`，`displaySetting` 须为非空对象），转换仍复用现有反序列化。完整的冻结解析器与字段映射跟第一次改 `Settings` 结构一起做。

6. 自家 schema 的演进仍要守住 migration 链。

   要求 3 的第二半依赖这一条。一旦我们的库改了结构，同一安装下本 fork 旧构建产出的备份要靠 `Migration_n_n+1` 升上来（换了包名、库里又带附件引用的，在源版本有快照时会被 C25 改走转换器），所以动结构时必须同时写迁移，并且这个迁移要能被导入路径复用。

   这也是「不能随意回退」的来源。接受这条代价，才谈得上把数据层放开。

7. 读取层要校验官方 schema 版本。

   官方若做破坏性迁移（重建表、改字段语义），读取层读到的是语义不同的数据，静默转换会写坏我们的库。读取层带已知版本的清单，遇到超出范围的版本明确失败，不做猜测。

8. 能导出官方客户端读得回去的备份，与自家导出并列。

   这是要求 1 的对称面，也是「改了结构就回不去」的解药。它在备份页上是第二个出口，不是把现有导出改掉：自家的导出始终带全量数据，包括我们新增的表与字段；官方兼容的导出只带官方认识的部分。

   当前双方 schema 都是 v32，附件目录也一致，官方兼容出口在这一步只改路径：把附件引用换成官方安装的位置（C30）。官方恢复只把附件搬进自己的 files 目录、不改库里的路径，所以路径必须在导出时换掉。schema 一旦分叉，这个出口就得真的裁表裁列，那时会直接失败。

   `settings.json` 不需要裁字段：官方恢复用的 `JsonInstant` 开着 `ignoreUnknownKeys`，多余字段会被忽略，归档里的 `launchCount` 就是先例。这个容忍度只覆盖不认识的 key，改已有字段的含义它救不了，往枚举里加值它也救不了（未知枚举值会让整份设置解码失败，所以官方出口会把官方不认识的选项去掉）。

   裁剪逻辑和导入侧一样，要能在一份固定样本集上验证，判据是官方客户端恢复成功，且恢复后的会话条数与关键字段对得上。

### 验收

见 [`backup/acceptance.md`](backup/acceptance.md)。样本分六类，判据是「没有独立期望值就不算验过」，只编译通过不算数。

### 冻结的解除

冻结已解除，条件是导入器已实现并在真实样本上验到了主要判据；逐类缺口见 [`backup/acceptance.md`](backup/acceptance.md)。解除之后，数据层的门禁从「不许动」换成「动了要维护」：改 Room 结构要同时写迁移，改完要重跑验收样本；改已有字段的含义要先建写入侧的字段映射表，那是唯一能发现语义漂移的环节。仍然生效的约束列在 [`README.md`](README.md) 的「数据结构」一节。

官方兼容导出（要求 8）与这次解除直接相连：双方结构分叉之后，那个出口会直接失败，直到裁剪逻辑落地。

### 与后续阶段的衔接

官方恢复走的是「启动前暂存，在 Koin、Room、SettingsStore 初始化之前替换文件」（`BackupManager.applyPendingRestore`）。这里可复用的是「启动前暂存、初始化之前落盘」这个时序，不是它的数据库路径：那条路径最终仍要靠 Room 迁移链把库升到当前版本（结构对不上时先在它前面过一层转换），正是要求 1 要绕开的。

改包名之后是新装应用冷启动，在这个时序里插入转换步骤比在运行时导入更干净。

## 阶段 2：独立化

### 2.1 改包名

已完成：`applicationId` 是 `top.lemoneko.rakkihub`，debug 变体带 `.debug` 后缀；`namespace` 仍是 `me.rerere.rikkahub`，类名与 R 类不受影响。

不受影响的部分：`FileProvider`、`DocumentsProvider`、`androidx-startup` 的 authorities 都是 `${applicationId}` 拼的，自动跟随。数据库文件名 `rikka_hub` 与 DataStore 文件名是不带包名的固定字符串，`rikka_hub.db` 是备份归档里的条目名，不是设备上的文件名。

真正的连锁反应有三条：

- 私有目录跟着包名走，改包名等于全新安装、零数据。把旧包数据搬过来只能走备份导出导入：库与附件路径都属于旧安装的，会在分流时被 C25 改走转换器，把路径重写成本机的
- 改包名后新旧两个应用并存，迁移路径只能是「旧应用导出、新应用导入」。旧应用可能是官方版，也可能是本 fork 的旧构建，两条来源都要能吃下，见要求 3
- `AndroidManifest.xml` 里的 shortcut scheme 是硬编码的，不跟包名走。两个应用都注册同一 scheme 时，外部隐式 intent 会落到不确定的那个，所以本分支用 `rakkihub://shortcut`，`res/xml/shortcuts.xml` 里的 `android:data` 与之对应

### 2.2 自己的 CI

这一步不依赖其它阶段，可以随时做。

`.github/workflows/daily-build.yml` 沿用上游，它的前提是：JDK 21（jetbrains distribution）、pnpm 11、Node 22、`submodules: recursive`、`web-ui` 先跑 `pnpm install --frozen-lockfile`。

两个 secrets 缺一不可：`KEY_BASE64`（keystore）与 `SIGNING_CONFIG`（写进 `local.properties`）。`GOOGLE_SERVICES_JSON` 不需要，本分支已按 C13 移除 Firebase 与 `google-services` 插件。

本仓库沿用上游的 nightly prerelease：固定 tag `nightly`，每天覆盖同一个 Release，附带分 ABI 的 APK 与 `mapping.txt`。不做正式版本发布。

`checks.yml` 在 push 到 `main`、PR 与手动触发时跑 `spotlessCheck` 与 app、ai、workspace 的 JVM 测试。push 时先把 ratchet 基点指到本次推送之前的提交，检查的是刚推上去的改动；PR 用基线的 `origin/main`；手动触发没有可靠基点，跳过格式检查，测试照跑。测试是全绿判据，Windows 本机那 7 条文件占用失败预期在 Linux 上不再出现，首次跑批确认；红了先看是不是平台差异，再改闸门，不要把失败用例静默进 ignore。

### 2.3 对数据层动手

前提是阶段 1 完成，现在已具备。原本的候选是拆掉 `Settings` 这个巨型类，评估后不做：`Settings` 的序列化形状是外部契约，旧备份的 `settings.json` 与官方兼容导出都按扁平字段读，嵌成子对象会让官方客户端静默回默认；本应用旧备份如果移动了 `displaySetting`、`assistants`、`providers` 这几个被形状校验盯着的键会直接报错，其余键静默回默认。要保住形状就得写一份根级自定义序列化器维护几十个字段的映射，收益只是代码组织，不值得。序列化形状保持扁平；以后要整理只动文件组织与 DataStore 字段表，不动 JSON 形状。

备份导出可选包含工作区文件已经落地（C29）：默认不打包，用户勾选时才带上工作区的文件区。

附件已经改成按内容寻址（C32）：文件名是内容的 SHA-256，写完临时文件、算出指纹之后先看目标在不在，在就复用。引用形式对消费方没变：内存与 UI 仍是绝对 `file://`，消息库与备份里落盘的是 `file:upload/...`（C36），序列化格式与 Room schema 也不变，按文件名去重的逻辑不受影响。指纹就在文件名里，清理时不必再算一遍内容。收益在真实备份上量过：同一份内容被反复存成多份。这次改动只让新写入去重，存量文件不会自动归并，体积回收要等旧引用消失后被清理收走。

删除语义随之改成「只摘引用」：删消息、删会话、移除输入框附件、换头像都不再直接删文件，回收统一交给标记-清除。标记-清除不用引用计数，因为引用计数要在消息删除、分支切换、助手删除、导入覆盖每一处维护，漏一处就少算，少算的结果是误删用户文件；标记-清除只要求扫描覆盖所有引用点，漏一处的最坏后果是少收一点垃圾。扫描范围是消息 JSON、设置里的助手与用户头像和背景、落盘的草稿（C31）与待发送队列（C70），以及内存里的会话、队列、提交中的消息与输入框里的附件；收藏存的是节点引用、没有消息快照，不需要扫。生成过程中不清理，避免工具结果里的新文件还没落库就被收走。手动触发，入口在设置的文件管理页。

另一件独立的事是「引用绑死了安装路径」。消息库里的附件地址已经改成相对形态落盘（C36）：内存与消费方仍是绝对，库里与备份里是 `file:upload/...`，读取时还原到当前安装；包名、用户号、工作资料变化后仍能解析。旧库里的两种绝对形态（`/data/user/...` 与旧的 `/data/data/...`）继续有效，下一次保存时转成相对。设置里的头像仍存绝对地址，靠导入侧重写；草稿不随备份走，也维持原样。官方兼容导出把相对形态与绝对路径都换成官方安装的位置（C30/C36）；官方备份进来的方向仍是绝对路径，导入器的路径重写保留。

「只导配置」现在没有入口。一份归档必须同时带设置与数据库（C40），备份页只管整份状态；要做就在备份页加一个显式入口：导出只写 `settings.json`，导入只换设置、不动库与附件。跨安装恢复时库里的会话找不到原来的助手，会退回当前助手（`Settings.getStoredAssistantOf`），入口上要把这一点写清。

导出与导入的进度、以及六个入口的互斥见 C41。

风险仍集中在这里：`Settings.kt`、`SettingsFields.kt`、`SettingsJsonMigrator` 与各设置页是数据层最敏感的部分；新增字段带默认值即可，结构性改动不做，动它们要按验收样本重跑。

## 独立更新

本仓库按独立更新维护：不再把上游合进来，只读上游的提交，按需自己实现功能与修复。`main` 是工作分支，`ref` 只作上游镜像。

### 为什么还看上游

完全不看的 fork 会慢慢烂掉：provider 协议变了、上游 API 挂了、崩溃修不上，最后连打开都用不了。独立更新只改「怎么跟」：不合并，也不再按模块重放定制，而是读他们的改动，取需要的那部分。

### 现在的做法

- `ref` 只作上游镜像：`git fetch upstream`、`git merge --ff-only`，更新后推 `origin/ref`，不合进 `main`。
- 保命层（provider 适配、上游 API 破坏性变更、崩溃与安全修复）读 diff 自己移植，不 cherry-pick。
- 能力层（上游新功能）按需自己实现；借了哪次提交的思路，在 [`checklist.md`](checklist.md) 里注明来源。
- 地基层（架构重构、UI 大改）不看，也不移植。
- 测试判据不变：没有新增失败。

### 模块参考表

模块表只用来筛「哪里值得看」，没有合并动作：

| 模块 | 性质 | 参考方式 |
| --- | --- | --- |
| `ai/` | provider SDK、协议适配、模型注册表，与 UI 重构解耦 | 看改动，协议与模型注册表的变化自己移植 |
| `search/`、`document/`、`common/` | 工具性质的独立模块 | 看改动，保命层自己移植 |
| `terminal/` | 从 termux-app 收进来的终端模拟器核心，与 `workspace/` 配合支持本地与 SSH 终端 | 看改动，保命层自己移植 |
| `workspace/` | 沙箱文件系统与 shell 执行环境 | 看改动，保命层自己移植 |
| `web/` | Ktor 服务端与 web-ui 前端产物 | 看改动，保命层自己移植 |
| `material3/`、`ui/` | 主题工具与不依赖 app 逻辑的 Compose 组件 | 看改动，保命层自己移植 |
| `oauth/` | OAuth 授权与回调处理 | 看改动，保命层自己移植 |
| `app/` | UI、业务、Service，也是大部分定制的所在地 | 我们的地盘，上游功能只借鉴思路 |
| `speech/` | C5 删掉了全部运行时实现，只留两个序列化类与它们的序列化测试 | 不看 |
| `mediagen/` | C1 保留模块以维持数据兼容，界面不使用 | 不主动看，改到 `MediaGenerationProviderSetting` 这类持久化类型时复核 |

`ai/` 下有十三处改动：C3 删掉了 `ai/util/Request.kt` 里给 aihubmix.com 加 `APP-Code` 请求头的分支；C52 把 `ClaudeProvider` 的缓存断点收敛到官方标准；C53 把 Claude 缓存策略改成按 provider 切换，默认保留上游原来的 4 个断点；C47 在 `ai/ui/Message.kt` 新增了只读的 `contextLimitStart`，不改 `limitContext`；C51 在 `ai/ui/Message.kt` 新增了序列化字段 `isCompressionPrompt`，用于标记压缩提示词；C58 在 `ai/ui/Message.kt` 新增了 `status`、`isModelVisible`、`filterModelVisible` 和 `mergeAdjacentUserMessages`；C59 让五个 `StreamChunkDecoder` 在未见终止事件时抛 `IOException`，并在五个 provider 接线点的 `onClosed` 里 `close(e)`；C61 在 `ErrorParser.kt` 增加 `LlmFailureCode`/`HttpException` 错误码和 `Retry-After`，`RetryPolicy` 按码表、jitter 和 `maxDelay` 重试，`AgentLoop` 把空回复转成 `EMPTY_RESPONSE`；C62 给 `AgentRequest` 增加 `streamIdleTimeout`，`AgentLoop` 用 `Flow.timeout` 把流内空闲转成 `TIMEOUT`；C63 给 `AgentHooks` 增加 `onContextOverflow`，`AgentLoop` 捕获 `CONTEXT_WINDOW_EXCEEDED` 后允许 hook 缩窗重试一次；C65 让工具错误结果只带摘要，堆栈只进 stderr，并给 `AgentRequest` 增加 `toolTimeout`，`AgentLoop` 用 `withTimeoutOrNull` 执行工具；C67 在 `UIMessage` 增加 `isGenerating`，加载时据此把进程死亡留下的半截回复补成 `INTERRUPTED`；C68 增加 `withoutCompressionScratch`，把压缩提示词和紧随回复从请求与导出里过滤掉。C35 的 `filterByKeywords` 与上游 `5ad19405` 逐字一致，不算分叉。移植上游对这些文件的改动时保留这十三处。

上游从 3.0.0 起把多步生成循环放进 `ai/`（`ai/src/main/java/me/rerere/ai/agent/AgentLoop.kt`），3.1.1 又把工具执行计时与 models.dev 的模型能力数据放进去。现在还值得看 `ai/` 吗，取决于它是不是一块能单独参考的区域：如果它继续吸收 `app/` 侧的耦合，读改动的成本会变高。

### 一次参考上游的动作

按下面的流程走。

1. 扫自上次查看点以来的提交：

   ```powershell
   git log --oneline <上次查看点>..upstream/master
   ```

2. 按模块参考表筛出要看的提交：保命层读 diff 自己移植，能力层评估后自己实现，地基层跳过。
3. 移植时不许覆盖登记过的定制（尤其 C3 的删除）；落进 `app/` 的改动不受合并成本限制。
4. 跑测试，判据是没有新增失败。
5. 在 `checklist.md` 里记一条，写明参考的上游提交。

### 定制原则的适用范围

[`README.md`](README.md) 里「改动尽量小，优先断入口」与「避开上游热区」这两条是合并成本的对价。`app/` 现在不需要替上游让路，两条都不适用：

- 在 `app/` 里，该删的实现直接删掉，不必为了可合并性留一整段死代码
- `ChatVM.kt`、`ChatService.kt` 这类高频文件可以随手改，不必专门绕开

`ai/` 仍要参考上游，C3 的删除在移植上游对 `ai/util/Request.kt` 的改动时要带回来，「改动尽量小」在那里仍然成立。

同类的合并成本让步还有几处：把删掉的文件路径记进定制清单以便合并时逐项核对；新定制避开上游重构期；定制清单里那些「刻意保留」的条目，比如 C1 留下未删的实现，留着都是为了不与上游产生 modify/delete 冲突。这些让步不再需要；清单本身仍然保留，用途从「合并时逐项重放」变成「移植上游改动时核对」。

## 分发与合规

独立化有一个决策变量是要不要把这个 fork 分发给别人，它直接影响合规义务。

本仓库是 AGPL-3.0，而项目带 `web` 模块，能在设备上跑 Ktor 对外提供 Web UI。自己构建自己安装不构成分发，没有额外义务。但如果 APK 要分发出去，或者把 web 版挂到公网提供服务，就需要按 AGPL 提供源码。

改包名不改变这一条，它只是让「这是谁的修改版」这个问题变得明确。

## 条目索引与依赖

[`checklist.md`](checklist.md) 的索引回答「有哪些条目」；这里给它一个依赖视图：哪些是原始定制要求，哪些是为了满足要求先建的前提条件，以及它们所处的层级。直接依赖只列「没有它这项要求做不成或做不安全」的前置，不列所有相关项；明细以 checklist 为准。

### 层级

- L0 基座：上游基线、分支模型、构建与 CI、文档。所有条目的共同前提。
- L1 平台与存储前提：包名、文案范围、版本号；官方 schema 快照、只读读取层、写入层、多版本快照、恢复流程、settings 形状、聊天草稿落盘、附件内容寻址。
- L2 数据层要求：官方导入、官方兼容导出及其附件路径扩展、备份与恢复、附件迁移。
- L3 功能与 UI 要求：移除项、聊天与 UI 功能、性能项。多数只依赖 L0/L1；上下文与消息保存的依赖见下。

### 前提条件

| 条目 | 层级 | 作用 | 直接依赖 |
| --- | --- | --- | --- |
| C12 改包名与应用名 | L1（本身也是需求） | 新旧应用并存；旧数据迁移只能走导出导入 | C19、C25（两个来源都要走，旧构建尤其需要）；C10（官方应用入口） |
| C17 官方 schema 快照 | L1 | 读取层不随本库演进 | 无 |
| C20 导入器读取层 | L1 | 裸 SQL 读官方库 | C17 |
| C21 导入器写入层 | L1 | 行级照抄写本库 | C20 |
| C22 导入器多版本快照 | L1 | 官方升级后可读 | C20 |
| C23 导入器接上恢复流程 | L1 | 启动前暂存、初始化前落盘 | C21、C22 |
| C28 settings 形状校验 | L1 | 导入时的 `settings.json` 形状检查 | 无 |
| C31 聊天草稿落盘 | L1（本身也是需求） | 附件清理要扫描草稿里的引用 | 无 |
| C32 附件内容寻址 | L1（本身也是需求） | 去重与标记-清除的基础 | C31 |
| C37 CI 跑格式检查与测试 | L0 | push 的格式与测试闸门 | 无 |
| C39 README 收成 fork 说明 | L0 | 文档基座 | 无 |
| C45 文案范围收成四份 | L1（本身也是需求） | 新文案的维护范围 | 无 |
| C48 版本号随提交滚动 | L1（本身也是需求） | 构建与崩溃日志能对到提交 | 无（需要 git） |

### 原始定制要求

| 条目 | 层级 | 直接依赖 |
| --- | --- | --- |
| C1 移除媒体创作与生图 | L3 | 无（数据模型保留，见备注） |
| C2 移除 Firebase Analytics | L3 | 无 |
| C3 移除赞助商推广，允许删所有提供商 | L3 | 无 |
| C4 清理赞助商图标、README 推广、启动弹窗 | L3 | 无 |
| C5 移除 TTS 与 STT | L3 | 无（序列化类保留，见备注） |
| C6 移除聊天回复建议 | L3 | 无 |
| C7 应用图标旋转 180 度 | L3 | 无 |
| C8 移除 Chatbox 与 Cherry Studio 导入 | L3 | 无 |
| C9 移除 S3 备份 | L3 | 无 |
| C10 新增从官方 RikkaHub 导入 | L2 | C23、C25、C28 |
| C11 导出备份显示进度 | L3 | C41 |
| C13 移除 Firebase 与 google-services | L3 | 无 |
| C14 移除设置页分享入口，统一品牌 | L3 | 无 |
| C15 移除更新检查 | L3 | 无 |
| C16 清除导出内容里的站点水印 | L3 | 无 |
| C18 新增官方兼容的导出出口 | L2 | C17 |
| C19 附件按替换语义导入，原始备份留底 | L2 | C23 |
| C24 导入器验收逐表比对 | L2 | C20、C22、C23 |
| C25 同版本备份也重写附件路径 | L2 | C21、C22、C23 |
| C26 整份备份的附件验收 | L2 | C23、C25 |
| C27 全文索引自检与重建标记 | L2 | 无 |
| C29 备份可选包含工作区文件 | L2 | C23 |
| C30 官方导出改写附件路径 | L2 | C18（C18 的附件路径扩展） |
| C33 附件拷贝挪出主线程 | L3 | 无 |
| C34 备份与工作区文件拷贝挪出主线程 | L3 | 无 |
| C35 模型选择支持多关键词搜索 | L3 | 无 |
| C36 附件引用相对路径 | L2 | C30 的扩展；C40 的前提 |
| C38 设置页提示授予本地网络权限 | L3 | 无 |
| C40 导入附件按内容寻址，归档必须有库与设置 | L2 | C19、C32、C36 |
| C41 备份与恢复按阶段报进度，六入口互斥 | L2 | C10、C18、C23、C40 |
| C42 备份页按本应用与官方分组 | L3 | C10、C18 |
| C43 错误卡片默认保留到手动关闭 | L3 | 无 |
| C44 上下文压缩的提示词可编辑 | L3 | 无（设置字段表） |
| C46 管理上下文：压缩与截断共用一个入口 | L3 | 无（C44 只影响压缩提示词入口的组成） |
| C47 上下文消息数上限的自动边界线 | L3 | C46（`contextWindowStart`） |
| C49 分支切换只写 `select_index` | L3 | 无（消息节点保存链） |
| C50 生成中定期保存回复节点 | L3 | C49 |
| C51 压缩走可见生成轮次 | L3 | C44、C46、C50 |
| C52 Claude 缓存断点按官方标准收敛 | L3 | 无（Claude provider 请求组装） |
| C53 Claude 缓存断点按提供商切换 | L3 | C52 |
| C54 移除关于页图标点击彩蛋并清理相关死代码 | L3 | 无（设置页 UI） |
| C55 关于页独立图标 | L3 | C54 |
| C56 文档 lint 配置 | L0 | 无（本地文档检查） |
| C57 请求日志开关持久化 | L3 | 无（日志页） |
| C58 失败与中断回复状态 | L3 | 无（消息状态与请求过滤） |
| C59 截断流识别与停止结算 | L3 | 无（provider 流终止与停止收尾） |
| C60 审批期间冻结 turn 上下文 | L3 | 无（审批 turn 上下文） |
| C61 结构化失败与重试策略 | L3 | 无（错误分类与重试） |
| C62 流式 idle watchdog | L3 | C61 |
| C63 上下文超限自动缩窗 | L3 | C61 |
| C64 移除设置页关于区域的社区与文档入口 | L3 | 无（设置页 UI） |
| C65 工具中断恢复结果 | L3 | 无（工具恢复） |
| C66 消息队列恢复与回插 | L3 | 无（消息队列） |
| C67 进程死亡回复恢复 | L3 | 无（进程死亡） |
| C68 压缩孤儿对加载清理与过滤 | L3 | 无（scratch 生命周期） |
| C69 停止生成前确认 | L3 | 无（停止入口与设置） |
| C70 消息队列持久化 | L3 | 无（队列文件与恢复时序） |
| C71 错误卡片持久化 | L3 | 无（错误卡片文件） |
| C72 上下文压缩按 token 预算 | L3 | 无（压缩计划与请求参数） |
| C73 发送前上下文裁剪 | L3 | 无（请求级裁剪） |

### 依赖链

- 官方导入：C17 → C20 → C21/C22 → C23；C28 只用于导入时的 settings 校验；C10 是入口；C24、C25、C26 收尾。
- 官方兼容导出：C17 → C18 → C30 → C36；C11、C42 使用导出入口。
- 附件：C31 → C32（清理要扫草稿引用）；C19、C32、C36 → C40；C25、C26 验收。
- 包名迁移：C12 的迁移路径，两个来源都要经过 C19、C25，官方应用入口是 C10。
- 上下文：C46（C44 只影响压缩提示词入口的组成）→ C47。
- 消息保存：C49 → C50。
- 重试与流：C61 → C62。
- 队列：C66 → C70。
- 压缩：C44 → C46 → C51（提示词、检查点、可见轮次），保存由 C49 → C50 支撑。
- 平台：C37、C45、C48 不依赖具体功能，但所有功能都在它们之上。

### 备注

- C1、C5 保留数据模型与序列化类，是为了让旧备份与官方兼容导出仍能解码；这是对移除项自身的约束，不构成对其它条目的依赖。
- C12、C45、C48 同时是需求与前提；C31、C32 也是「本身是需求、同时作为清理与去重的前提」，所以放在前提表并注明。
- C30、C36 是 C18 的扩展：C30 给导出加附件路径改写，C36 在此之上加相对路径；C36 又是 C40 导入的前提，所以同时出现在依赖里。
- 这张表是当前实现的直接依赖视图，不是落地顺序；待办方向的计划项（默认提示词重写、压缩面板、`tools/i18n` Python 等）不预留编号，落地时再登记。

## 待办方向

- 统一重写各功能的默认提示词。现状、调研结论与压缩提示词草稿在 [`prompts.md`](prompts.md)，先做压缩，再按同一套方法过标题、翻译、OCR。
- 压缩交互的改进：面板里临时换模型（换的时候这次请求去掉 `cache_control`）、思考强度单独可配、压缩请求的实时状态（tok/s、思考/输出阶段；流式已由 C51 完成）。
- 把 `tools/i18n` 从 Bun 重写成 Python：现在本机跑不起来（没有 bun 和 `.env`），新文案只能手翻；重写后接回同一套 config，保留缺条目与未使用条目的检查。

### Markdown 渲染能力

- 调研 HTML `<svg>` 标签支持：分别看 Android 的 `MarkdownBlock`/BetterText、Web 的 Streamdown + `rehype-raw`、HTML 预览 WebView 三处是否保留内联 SVG、是否做清洗与尺寸约束、外链和工作区文件引用怎么解析；先查清安全边界和渲染差异，再决定是否放开、放开到哪一层。
- Mermaid 支持版本升级：Web 端 `workbench-host.tsx` 目前从 `esm.sh/mermaid@11` 动态加载，`streamdown` 自带 mermaid 但 `controls.mermaid=false`，锁文件里另有一个 `mermaid@11.15.0`；Android 已有 `assets/html/mermaid.min.js` 11.16.0（含 DOMPurify 3.4.0），并通过 Mermaid 组件在正文代码块渲染，版本清单实际有三个。调研统一到哪个版本、是否在 Web 正文里直接渲染 mermaid、升级后的语法兼容、安全配置和导出回归。

### 重试、失败尝试与用量

DSH 对照版本 `dsh-v0.2.1-rc.5`（`b830ec950f`）。结论：DSH 也不做逐 token 续传，重试单位是 agent step；失败 attempt 以 `assistant/attempt` 落日志但不进入模型历史，成功才写 `assistant/message`；用户主动停止且已有内容时写 `interrupted: true` 的 `assistant/message`。重试策略由 provider 拥有，默认对 `EMPTY_RESPONSE`、`RATE_LIMIT`、`SERVER`、`TIMEOUT`、`TRANSPORT` 最多重试 5 次，也就是最多 6 次请求；退避从 500ms 到 10s，10% 对称抖动，读取 `Retry-After`；每次重试先落 `llm/retry` 再等待。参考文件：`packages/llm/llm-retry`、`packages/llm/llm/src/retry-policy.ts`、`packages/core/agent-loop/src/agent.ts`、`packages/core/session/README.md`、`packages/llm/llm-deepseek/src/transport.ts`。

RikkaHub 现状：

- 重试单位是 step，`withRetry` 失败后回到 `stepStart`，前面的 step 和工具结果保留；单步回复看起来就是整条重来。DSH 也是这个语义，不是 RikkaHub 独有。
- C61 把失败归一化为 `HttpException`/`LlmFailure`：流式和非流式的 2xx 之外的响应都带 HTTP status、错误码和 `Retry-After`；默认只重试 `RATE_LIMIT`、`SERVER`、`TIMEOUT`、`TRANSPORT`、`EMPTY_RESPONSE`，鉴权、配额、非法请求和 context overflow 不重试。空回复现在按 `EMPTY_RESPONSE` 进入同一策略，最多重试 `maxRetries` 次；固定最多 3 次，基础间隔 1s/2s/4s，10% jitter，`Retry-After` 会被 `maxDelay` 截断；开关仍是全局的。`retryOn` 保留为自定义逃生口。
- C58 已给失败和用户停止的回复加 `FAILED`/`INTERRUPTED` 状态；请求组装、标题生成、记忆整理和 `read_conversations` 都过滤 `FAILED`，失败半截不会再作为普通回复进入下一轮。多步工具调用在中途失败时仍按整条消息隔离。
- 停止或下一次发送时，未执行工具补恢复结果，按 `startedAt` 区分 `not_started`/`outcome_unknown`；审批恢复路径中的 Approved/Denied/Answered 工具仍交给 AgentLoop 继续执行。
- UI 有失败/中断标记，但没有重试 attempt 行和倒计时；错误卡片没有重试按钮；`usage_record` 每次尝试一行，但没有成功/失败状态。

待办：

- P0 失败 attempt 与成功消息分开：C58 已实现 `UIMessage.status` 标记、`FAILED` 消息从请求/标题/记忆/`read_conversations` 过滤、相邻 user 合并和 UI 失败/中断标签。剩余：多步工具调用在中途失败时目前整条隔离，per-step attempt 细化仍归 P2；工具恢复结果已由 C65 处理。
- P1 稳定错误码与可配置策略。C61 已把 provider 非 2xx 和空回复统一成带 `code`、`status`、`retryAfterMs` 的失败类型，覆盖 `AUTH`、`QUOTA`、`RATE_LIMIT`、`SERVER`、`TIMEOUT`、`EMPTY_RESPONSE`、`CONTEXT_WINDOW_EXCEEDED`、`INVALID_REQUEST`、`UNKNOWN`；裸传输错误仍按 `IOException` 重试，`TRANSPORT` 留给调用方包装。策略是 normal mode：`maxRetries`、`retryableCodes`、`initialDelay`、`maxDelay`、`jitter`；默认只重试瞬时错误，读取 `Retry-After` 并设上限。保留 step 级重试，不做 prefill 续写。验收：429、5xx、空回复可重试；400、401、403、额度、上下文超限不重试；用户停止可取消等待；重试不重复工具副作用；重试请求与前缀缓存兼容。
- P2 重试状态 UI。attempt 行或卡片显示编号、失败原因、等待时间和倒计时；失败消息有标记；错误卡片提供重试本步。不要在重试时静默清空半截内容，至少保留可见的失败 attempt。
- P3 用量区分成功与失败 attempt。`usage_record` 加 outcome、attempt、retryId 或等价字段，统计页不把失败尝试计入缓存命中率，但保留成本；消息卡片继续只显示成功 attempt 的 usage。验收：旧数据默认成功或未知，迁移可回滚。

### DSH 对照检查记录

检查基线 `dsh-v0.2.1-rc.5`（`b830ec950f`）。以下是本轮只读审计确认的问题，按修复优先级排列；路径都是 RikkaHub 侧位置。

| 优先级 | 领域 | 现状与风险 | 建议 |
| --- | --- | --- | --- |
| P1 | 截断的 SSE 被当成正常完成（C59 已修） | C59 前 Claude 的 `onClosed` 无条件补 `Finish`，没有 `message_stop` 的半截流会被提交为完整回复；OpenAI/Google 的 `onClosed` 同样无条件调用 `decoder.finish()`。C59 起五个 decoder 未见终止事件时抛 `IOException`，provider `onClosed` 转成 `close(e)`，`onFailure` 空 body 不再静默正常关闭。`ClaudeProvider.kt`、`ClaudeStreamDecoder.kt`、`ChatCompletionsAPI.kt`、`ChatCompletionsStreamDecoder.kt`、`ResponseAPI.kt`、`ResponseApiStreamDecoder.kt`、`GoogleProvider.kt`、`GoogleStreamDecoder.kt`、`InteractionsAPI.kt`、`InteractionsStreamDecoder.kt`。 | 已按此实现；严格判定对不发终止事件的中转会变成可重试错误，按 provider 加宽松条件而不是全局关掉。 |
| P1 | 停止的 pending 结算不保证执行（C59 已修） | C59 前 `stopGeneration` 先 pause/cancelJobs，再 join，join 后才 `finishInterruptedPendingTools`，不在 `NonCancellable`；页面退出导致 join 取消时 pending 工具不结算。C59 起 join 与工具收尾放进 `withContext(NonCancellable)`，发送前的 `finishInterruptedPendingTools` 仍保留作崩溃补偿。`ChatService.kt:1481-1493`、`ChatVM.kt:386-390`。 | 已按此实现；NonCancellable 只包 join 和工具收尾，不包 UI 状态。 |
| P1 | 分支切换绕过审批门禁（C60 已修） | C60 前 `selectMessageNode` 只挡压缩，不挡审批；切走分支后 `isAwaitingToolApproval` 变 false，队列可派发，pending 调用留在未选中分支。C60 起 `isAwaitingToolApproval` 或 `pendingApprovalTurn` 非空时直接抛 `ConflictException`。`ChatService.selectMessageNode`、`Conversation.isAwaitingToolApproval`。 | 已按此实现；ChatVM 目前只记日志，Web 返回 409。 |
| P1 | 审批恢复重新解析配置（C60 已修） | C60 前审批期间 `setChatModel` 没有门禁，`handleToolApproval` 恢复时重新 `runTurn`/`createContext`，工具结果和工具 schema 会落到新模型/provider。C60 起 `ConversationSession.pendingApprovalTurn` 冻结本轮 `ChatTurnContext`，审批恢复复用它；配置变化从下一轮生效。`ChatService.runTurn`、`ConversationSession.pendingApprovalTurn`；`ChatVM.setChatModel` 没有改，允许改但只影响下一轮。 | 已按此实现；冻结只在内存，进程死亡后退化为当前配置。 |
| P1 | 没有流内 idle watchdog（C62 已修） | C62 前只有 OkHttp/Ktor 的 10 分钟 per-read timeout；无 chunk 无心跳会卡 10 分钟，有 SSE 心跳时 read timeout 被不断重置，可能永久停在生成中。C62 起 `AgentRequest.streamIdleTimeout` 默认 90 秒，`AgentLoop.stream` 用 `Flow.timeout` 在相邻 chunk 间超时，抛 `LlmFailure(TIMEOUT)` 进重试；用户取消仍优先传播。`AgentLoop.kt`、`Agent.kt`。 | 已按此实现；默认值目前没有设置页入口，非流式仍依赖网络层读超时。 |
| P1 | 没有 error-driven overflow 恢复（C63 已修） | C63 前请求装配只有 `limitContext` 的条数截断，provider 返回 overflow 直接进错误卡片，压缩只能手动触发。C63 起 C61 归一化 `CONTEXT_WINDOW_EXCEEDED`，`AgentHooks.onContextOverflow` 让 app 把窗口收到最后一条 user 后，并在每个后续 step 按当前消息重算，再从本 step 重试；仍失败才出卡片。`AgentLoop.kt`、`Agent.kt`、`ChatRequestFactory.kt`。 | 已按此实现；不做自动压缩，单条超大输入仍可能失败。 |
| P2 | 工具恢复语义失真（C65 已修恢复结果） | C65 起按 `startedAt` 区分 `not_started` 与 `outcome_unknown`，`cancelInterruptedTools` 和 `withoutUnresumableToolCalls` 共用恢复结果；工具错误只给模型摘要，堆栈只进 stderr；`AgentRequest.toolTimeout` 默认 5 分钟，超时返回可读结果。尚未做 output 的 NonCancellable 写入和错误日志分级。`ConversationMessages.kt`、`Conversation.kt`、`AgentLoop.kt`。 | 已按恢复语义和工具超时实现；取消写入与日志分级留到后续。 |
| P2 | `withoutUnresumableToolCalls` 删除整条消息（C65 已修） | C65 起逐 part 处理，只把不可恢复的 Tool part 换成恢复结果，保留文本、推理和其它 part，不再删除整条 assistant 消息。`Conversation.kt`。 | 已按此实现。 |
| P2 | 进程死亡丢失重试与收尾状态（C67 已恢复半截回复，C70 已持久化队列，C71 已持久化错误卡片） | 只周期保存回复节点，5/15/30 秒分档，小节点最迟 60 秒；C67 的 `isGenerating` 随消息落库，加载时把半截回复补成 `INTERRUPTED` 并补工具结果；C70 把未出队消息落到 `noBackupFilesDir`，恢复为暂停态；C71 把错误卡片落到 `noBackupFilesDir`，启动时恢复。实时 `Retrying (n/m)` 状态仍不跨进程：进程死亡后没有在途请求，半截回复按 `INTERRUPTED` 恢复，重试倒计时恢复出来只会误导；首次保存前死亡仍会丢。C50 在节点漂移或含 base64 图片时静默跳过。`MessageSaveLoop.kt`、`ConversationSession.kt`、`ChatService.kt`、`ChatErrorStore.kt`。 | 继续持久化 turn 状态或时间戳心跳；保存失败要上报；live retrying 不跨进程，重试 attempt 的 UI 与用量归 P2 重试状态项。 |
| P2 | 消息队列暂停后静默滞留（C66 已修派发与回插，C70 已持久化） | C66 前队列 paused 且非空时，新消息只 enqueue，`takeNext` 因 paused 返回 null，消息不发也不进正文；先 `takeNext` 出队再写历史，中间失败会丢消息。C66 起显式发送总是 resume，发送失败且未落库时回插队首。C70 起未出队消息按会话落到 `noBackupFilesDir`，恢复为暂停态，用队列 id 与已落库消息去重。`ChatService.kt`、`MessageQueue.kt`、`MessageQueueStore.kt`。 | 已按此实现派发恢复、失败回插和跨进程持久化；编辑占位与暂停状态不落盘。 |
| P2 | 上下文压力只按条数，压缩能力有限（C72 已修计划对齐、摘要预算、部分摘要、范围检查，C73 已补发送前裁剪） | 发送前没有 token 估算或 usage 水位；摘要请求 `maxTokens = null`，截断即整次压缩失败；没有 tool result 裁剪和图片 offload；截断 keep=1 且末条是 assistant 时会丢掉产生它的 user。C72 起保留起点按 user 轮次对齐，摘要输出和输入都有保守 token 预算，超预算先提示截断/多保留，摘要被截断时退回部分摘要；C73 起 `buildStep` 对旧工具输出截断到前 2000 字符、旧图片换占位，最近 6 条不动，仍超预算交给 C63。精确 usage 水位和自动压缩建议仍未做。`ChatRequestFactory.kt`、`ContextPruning.kt`、`CompressionPlan.kt`、`ChatService.kt`。 | 剩余精确 usage 水位与自动压缩建议；C72/C73 已做计划对齐、摘要预算、部分摘要、范围检查、tool result 截断和图片占位。 |
| P2 | scratch 生命周期只靠调用点清理（C68 已清理崩溃对并过滤请求/导出/Web 快照/搜索） | 压缩失败或取消留下的 prompt 与半截回复只在发送、截断、再次压缩前清理；C68 起加载会话时删除回复仍为 `COMPLETE` 的崩溃对，失败/取消对标 `FAILED` 后跨 reload 保留，请求派生、导出、Web/SSE 的 `Conversation.toDto` 和 App/Web/agent 搜索结果会过滤；浮动窗口只预览当前回复，压缩轮次结束即清空，不列出孤儿对。`ChatService.kt`、`ConversationSession.kt`、`ConversationMessages.kt`、`ConversationRepository.kt`、`ai/ui/Message.kt`。 | 给 scratch 打逐 part 不可发送标记；C68 已做崩溃对载入清理、请求/导出/Web 快照/搜索过滤。 |

修复顺序：

P1 分批：P1a 截断 SSE 与停止结算，P1b 审批分支与配置快照，P1c idle watchdog、稳定错误码重试、overflow 兜底。

1. P0 失败 attempt 隔离已由 C58 完成；多步工具调用的 per-step 细化仍留 P2，工具恢复结果已由 C65 处理。
2. P1a 截断 SSE 与停止结算已由 C59 完成；P1b 审批分支与配置快照已由 C60 完成。
3. P1c 的稳定错误码重试已由 C61、idle watchdog 由 C62、overflow 兜底由 C63 完成；P1 收尾后进入 P2。
4. P2 工具恢复结果和工具超时已由 C65 完成，队列派发与失败回插由 C66 完成，进程死亡半截回复由 C67 完成，scratch 加载清理与请求/导出/Web 快照/搜索过滤由 C68 完成，队列持久化由 C70 完成，错误卡片持久化由 C71 完成，压缩计划对齐、摘要预算、部分摘要和范围检查由 C72 完成，发送前工具输出/图片裁剪由 C73 完成；剩余精确 usage 水位与自动压缩建议、取消写入、重试状态 UI 与用量。

范围说明：C59 已逐行核对五个 decoder 的终止判定和五个 provider 接线点的 `onClosed`，并用 `:ai:testDebugUnitTest` 全量验证；工具并发没有展开，当前 RikkaHub 按顺序执行，未发现正确性问题，只是并发效率较低。
