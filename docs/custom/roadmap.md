# 长期路线

本文件记方向、阶段划分与策略。具体定制的落点见 [`checklist/`](checklist/) 的明细，数据层的现状见 [`backup/README.md`](backup/README.md)。

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

   当前 Room schema 是 v35、官方是 v32；C80 对 `usage_record.outcome` 做已知裁剪，C87 另把 `file_derivations` 整张表裁掉，官方兼容出口仍要把附件引用换成官方安装的位置（C30）。官方恢复只把附件搬进自己的 files 目录、不改库里的路径，所以路径必须在导出时换掉。其它表或列一旦分叉，这个出口仍会直接失败。

   `settings.json` 不需要裁字段：官方恢复用的 `JsonInstant` 开着 `ignoreUnknownKeys`，多余字段会被忽略，归档里的 `launchCount` 就是先例。这个容忍度只覆盖不认识的 key，改已有字段的含义它救不了，往枚举里加值它也救不了（未知枚举值会让整份设置解码失败，所以官方出口会把官方不认识的选项去掉）。

   已知裁剪和导入侧一样，要能在一份固定样本集上验证，判据是官方客户端恢复成功，且恢复后的会话条数与关键字段对得上；C80 已对 `usage_record.outcome` 落地，通用裁表裁列仍待后续。

### 验收

见 [`backup/acceptance.md`](backup/acceptance.md)。样本分六类，判据是「没有独立期望值就不算验过」，只编译通过不算数。

### 冻结的解除

冻结已解除，条件是导入器已实现并在真实样本上验到了主要判据；逐类缺口见 [`backup/acceptance.md`](backup/acceptance.md)。解除之后，数据层的门禁从「不许动」换成「动了要维护」：改 Room 结构要同时写迁移，改完要重跑验收样本；改已有字段的含义要先建写入侧的字段映射表，那是唯一能发现语义漂移的环节。仍然生效的约束列在 [`README.md`](README.md) 的「数据结构」一节。

官方兼容导出（要求 8）与这次解除直接相连：C80 已对 `usage_record.outcome` 这一处分叉做裁剪，其它结构分叉仍会让那个出口直接失败，直到通用裁剪逻辑落地。

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

本仓库沿用上游的 nightly prerelease：固定 tag `nightly`，每天覆盖同一个 Release，附带分 ABI 与 universal 的 APK 和 `mapping.txt`。不做正式版本发布。同一个 job 另把这些文件传一份到 Actions Artifacts，名字带完整 commit SHA、保留 14 天：Release 只留最新一版，按提交追溯去看 Artifacts。

`checks.yml` 在 push 到 `main`、PR 与手动触发时跑 `spotlessCheck` 与 app、ai、workspace、provider-mock 的 JVM 测试。push 时先把 ratchet 基点指到本次推送之前的提交，检查的是刚推上去的改动；PR 用基线的 `origin/main`；手动触发没有可靠基点，跳过格式检查，测试照跑。测试是全绿判据，Windows 本机那 7 条文件占用失败预期在 Linux 上不再出现，首次跑批确认；红了先看是不是平台差异，再改闸门，不要把失败用例静默进 ignore。provider 协议相关的用例可以通过 `tools/provider-mock` 在本地起一个真实 HTTP 的 mock server，覆盖四种流式协议、错误映射与截断，不访问真实接口。

### 2.3 对数据层动手

前提是阶段 1 完成，现在已具备。原本的候选是拆掉 `Settings` 这个巨型类，评估后不做：`Settings` 的序列化形状是外部契约，旧备份的 `settings.json` 与官方兼容导出都按扁平字段读，嵌成子对象会让官方客户端静默回默认；本应用旧备份如果移动了 `displaySetting`、`assistants`、`providers` 这几个被形状校验盯着的键会直接报错，其余键静默回默认。要保住形状就得写一份根级自定义序列化器维护几十个字段的映射，收益只是代码组织，不值得。序列化形状保持扁平；以后要整理只动文件组织与 DataStore 字段表，不动 JSON 形状。

备份导出可选包含工作区文件已经落地（C29）：WebDAV 默认不打包，用户勾选时才带上工作区的文件区；本地导出/导入页从 C79 起默认勾选附件与工作区文件。

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
- 能力层（上游新功能）按需自己实现；借了哪次提交的思路，在 [`checklist/`](checklist/) 明细的对应项里注明来源。
- 地基层（架构重构、UI 大改）不看，也不移植。
- 测试判据不变：没有新增失败。

### 模块参考表

模块表只用来筛「哪里值得看」，没有合并动作：

| 模块 | 性质 | 参考方式 |
| --- | --- | --- |
| `core/ai/` | provider SDK、协议适配、模型注册表，与 UI 重构解耦 | 看改动，协议与模型注册表的变化自己移植 |
| `integrations/search/`、`core/document/`、`core/common/` | 工具性质的独立模块 | 看改动，保命层自己移植 |
| `platform/terminal/` | 从 termux-app 收进来的终端模拟器核心，与 `platform/workspace/` 配合支持本地与 SSH 终端 | 看改动，保命层自己移植 |
| `platform/workspace/` | 沙箱文件系统与 shell 执行环境 | 看改动，保命层自己移植 |
| `platform/web/` | Ktor 服务端与 web-ui 前端产物 | 看改动，保命层自己移植 |
| `core/material3/`、`core/ui/` | 主题工具与不依赖 app 逻辑的 Compose 组件 | 看改动，保命层自己移植 |
| `integrations/oauth/` | OAuth 授权与回调处理 | 看改动，保命层自己移植 |
| `app/` | UI、业务、Service，也是大部分定制的所在地 | 我们的地盘，上游功能只借鉴思路 |
| `integrations/speech/` | C5 删掉了全部运行时实现，只留两个序列化类与它们的序列化测试 | 不看 |
| `integrations/mediagen/` | C1 保留模块以维持数据兼容，界面不使用 | 不主动看，改到 `MediaGenerationProviderSetting` 这类持久化类型时复核 |

`core/ai/` 下有这些改动：C3 删掉了 `core/ai/util/Request.kt` 里给 aihubmix.com 加 `APP-Code` 请求头的分支；C52 把 `ClaudeProvider` 的缓存断点收敛到官方标准；C53 把 Claude 缓存策略改成按 provider 切换，默认保留上游原来的 4 个断点；C47 在 `core/ai/ui/Message.kt` 新增了只读的 `contextLimitStart`，不改 `limitContext`；C51 在 `core/ai/ui/Message.kt` 新增了序列化字段 `isCompressionPrompt`，用于标记压缩提示词；C58 在 `core/ai/ui/Message.kt` 新增了 `status`、`isModelVisible`、`filterModelVisible`；C160 把相邻 user 合并移到各 provider 的 wire 层，并加上按 host 判断的 `ProviderSetting.mergesAdjacentUserMessages` 能力位，请求层不再合并；C59 让五个 `StreamChunkDecoder` 在未见终止事件时抛 `IOException`，并在五个 provider 接线点的 `onClosed` 里 `close(e)`；C61 在 `ErrorParser.kt` 增加 `LlmFailureCode`/`HttpException` 错误码和 `Retry-After`，`RetryPolicy` 按码表、jitter 和 `maxDelay` 重试，`AgentLoop` 把空回复转成 `EMPTY_RESPONSE`；C62 给 `AgentRequest` 增加 `streamIdleTimeout`，`AgentLoop` 用 `Flow.timeout` 把流内空闲转成 `TIMEOUT`，Claude 的 ping 产出心跳并重置看门狗；C65 让工具错误结果只带摘要，堆栈只进日志（C78 起 `Log.e`），并给 `AgentRequest` 增加 `toolTimeout`，`AgentLoop` 用 `withTimeoutOrNull` 执行工具，工具声明 `timeoutMillis` 与外层取较大值；C78 把工具失败日志从 `printStackTrace` 改成 `Log.e`；C67 在 `UIMessage` 增加 `isGenerating`，加载时据此把进程死亡留下的半截回复补成 `INTERRUPTED`；C68 增加 `withoutCompressionScratch`，把压缩提示词和紧随回复从请求与导出里过滤掉；C85 让 Claude 的 `redacted_thinking` 与思考块签名可以回放；C86 把 `NOTSET` 与 `OFF` 拆开，内部调用默认不写 `reasoning_effort`，官方出口把 `NOTSET` 归一化成 `OFF`；C96 删掉了 `configureReferHeaders`（OpenRouter 的 `X-Title` 与 `HTTP-Referer`）。C35 的 `filterByKeywords` 与上游 `5ad19405` 逐字一致，不算分叉。移植上游对这些文件的改动时保留这些改动。

上游从 3.0.0 起把多步生成循环放进 `ai/`（本地对应 `core/ai/src/main/java/me/rerere/ai/agent/AgentLoop.kt`），3.1.1 又把工具执行计时与 models.dev 的模型能力数据放进去。现在还值得看 `core/ai/` 吗，取决于它是不是一块能单独参考的区域：如果它继续吸收 `app/` 侧的耦合，读改动的成本会变高。

### 一次参考上游的动作

按下面的流程走。

1. 扫自上次查看点以来的提交：

   ```powershell
   git log --oneline <上次查看点>..upstream/master
   ```

2. 按模块参考表筛出要看的提交：保命层读 diff 自己移植，能力层评估后自己实现，地基层跳过。
3. 移植时不许覆盖登记过的定制（尤其 C3 与 C96 的删除）；落进 `app/` 的改动不受合并成本限制。
4. 跑测试，判据是没有新增失败。
5. 在 checklist 明细的对应项里记一条，写明参考的上游提交。

### 定制原则的适用范围

[`README.md`](README.md) 里「改动尽量小，优先断入口」与「避开上游热区」这两条是合并成本的对价。`app/` 现在不需要替上游让路，两条都不适用：

- 在 `app/` 里，该删的实现直接删掉，不必为了可合并性留一整段死代码
- `ChatVM.kt`、`ChatService.kt` 这类高频文件可以随手改，不必专门绕开

`core/ai/` 仍要参考上游，C3 与 C96 的删除在移植上游对 `core/ai/util/Request.kt` 的改动时要带回来，「改动尽量小」在那里仍然成立。

同类的合并成本让步还有几处：把删掉的文件路径记进定制清单以便合并时逐项核对；新定制避开上游重构期；定制清单里那些「刻意保留」的条目，比如 C1 留下未删的实现，留着都是为了不与上游产生 modify/delete 冲突。这些让步不再需要；清单本身仍然保留，用途从「合并时逐项重放」变成「移植上游改动时核对」。

## 分发与合规

独立化有一个决策变量是要不要把这个 fork 分发给别人，它直接影响合规义务。

本仓库是 AGPL-3.0，而项目带 `web` 模块，能在设备上跑 Ktor 对外提供 Web UI。自己构建自己安装不构成分发，没有额外义务。但如果 APK 要分发出去，或者把 web 版挂到公网提供服务，就需要按 AGPL 提供源码。

改包名不改变这一条，它只是让「这是谁的修改版」这个问题变得明确。

## 条目索引与依赖

[`checklist.md`](checklist.md) 的索引回答「有哪些条目」；这里给它一个依赖视图：哪些是原始定制要求，哪些是为了满足要求先建的前提条件，以及它们所处的层级。直接依赖只列「没有它这项要求做不成或做不安全」的前置，不列所有相关项；明细以 [`checklist/`](checklist/) 为准。

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
| C63 上下文超限自动缩窗（已撤销） | L3 | C61 |
| C64 移除设置页关于区域的社区与文档入口 | L3 | 无（设置页 UI） |
| C65 工具中断恢复结果 | L3 | 无（工具恢复） |
| C66 消息队列恢复与回插 | L3 | 无（消息队列） |
| C67 进程死亡回复恢复 | L3 | 无（进程死亡） |
| C68 压缩孤儿对加载清理与过滤 | L3 | 无（scratch 生命周期） |
| C69 停止生成前确认 | L3 | 无（停止入口与设置） |
| C70 消息队列持久化 | L3 | 无（队列文件与恢复时序） |
| C71 错误卡片持久化 | L3 | 无（错误卡片文件） |
| C72 上下文压缩按 token 预算 | L3 | 无（压缩计划与请求参数） |
| C73 发送前上下文裁剪（已撤销） | L3 | 无（请求级裁剪） |
| C74 上下文水位与压缩建议 | L3 | 无（chat page banner） |
| C75 自动重试次数可调（默认关闭，已恢复） | L3 | 无（网络设置） |
| C76 重试 attempt 可见化（已持久化） | L3 | 无（MessageStep.attempts 与聊天列表卡片） |
| C77 错误卡片重试本步（永久取消） | L3 | 无（ChatError 与错误卡片） |
| C78 工具失败日志分级 | L3 | 无（core/ai/agent/AgentLoop.kt） |
| C79 本地导出默认包含工作区文件 | L2 | C29 |
| C80 用量成功失败区分与官方 schema 裁剪 | L2 | C18、C30 |

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
- 这张表是当前实现的直接依赖视图，不是落地顺序；待办方向的计划项（默认提示词重写、压缩面板等）不预留编号，落地时再登记。C81 起的项尚未补入依赖表，这些项的直接依赖以 [`checklist.md`](checklist.md) 索引指向的明细为准。

## 待办方向

- 默认提示词重写（含压缩提示词和摘要文案）暂缓：现状、调研结论与草稿保留在 [`prompts.md`](prompts.md)，等压缩面板和交互稳定后再按同一套方法过标题、翻译、OCR。
- Gemini 缓存控制已调研，结论是不做 explicit `cachedContents`：默认 generateContent 路径的 implicit caching 已自动生效并解析成 cachedTokens，explicit 只支持 Gemini 3/2.5 且是 Beta，有 4096/2048 token 下限和按小时计的存储费，约 18 到 60 次复用才回本，还会关闭同一请求的 implicit caching。剩余可选小项：给 OpenAI 兼容入口的 usage 解析补一个 cached token 字段的兜底。
- U+FFFD 替换字符按 C100 重新登记为独立行为对齐项，低优先级、不排期：经中转使用 Claude 时偶发，用户侧对照显示同一中转下官方 Claude Code 正常，中转方称不是服务端问题。2026-11-21 用 provider mock 做过一轮定位：把合法多字节文本按 7 字节切片写出、让字符跨写边界时，真实 ClaudeProvider 解码后逐字正确且没有 U+FFFD；只有线上字节本身非法（删掉一个续字节）时才复现出 U+FFFD。SSE 读取器是把字节缓冲到完整事件后再解码的，这条路径上分块边界不可能是原因；但 Web UI、其它 provider、中继在行以上的改写、以及线上已经是 U+FFFD 的情况都没有覆盖，要定位仍得在真实失败会话上抓原始字节。两个场景留在 `tools/provider-mock` 里作回归用例。不要先做全局替换字符清洗。
- 简易文件管理器（未排期）：现在「设置 → 文件管理」只列 `upload/` 附件，工作区文件要进工作区详情的 Files tab，或从系统文件管理器通过 SAF 访问；两处能力也不一致（附件页有 OCR、清理，工作区页有导入/导出/分享/删除）。先定范围：只做统一入口和互相跳转，还是做一个只读的统一列表（附件 + 各本地工作区的 `files/`）；不做移动/重命名/新建文件夹这类通用管理器能力，远程工作区不暴露。落地时注意 `AttachmentCleaner` 的引用语义、大目录分页、缩略图与删除确认。
- Dependabot PR 分批处理（进行中）：安全/patch 批次已完成 okhttp、jsoup、slf4j 与 slf4j-android、sshd、jmdns、pebble、quickjs、bouncycastle、uiautomator；web-ui 批次已完成 react 19.3、streamdown 2.8、ky 2.2、i18next 27、react-i18next 17、file-type 22、react-resizable-panels 4.24 等升级；Gradle 批次已完成 wrapper 9.9.1、AGP 9.5.2、KSP 2.3.14、Ktor 3.6.1、spotless 8.11.0 等升级，并把 haze 2.1.0 的玻璃样式 API 改到 `GlassStyle.material3` 与 `optics { }`。两批都在真机上看过对应界面与 Web Server。material3 `1.5.0-rc01` 与 `androidx.ink` `1.1.0-alpha11` 同一次升级落地并验过界面，对应的 PR 等 Dependabot 自行回收；两项目前都已有更新的版本（material3 1.5.0 正式版、ink alpha12），等新 PR 出来再按同样方式验。仍待处理：等 rehype-katex 支持后再升的 katex 0.19 与单独重排的 oxfmt；PR 不自动合并，合并或关闭前确认。
- 上游更新挑选与移植（未排期）：`ref` 保持只读，按批次查看上游提交，优先 bugfix、安全修复和会影响现有行为的改动；每批一个主题，手工移植并带设备验收，不做整分支 merge。
- 上游 issue 跟进（2026-11-22 调研）：候选与排序见 [`history/upstream.md`](history/upstream.md) 的「2026-11-22 issue 调研」。`conversation_search` 未按当前助手过滤与月之暗面思考参数分派已修（C154、C156）。`#2376` 自定义 body 的合并策略暂缓、目前没有需求：以后再做时，给 `CustomBody` 增加行级操作（默认 replace，可选 merge-by-name/append/prepend），只对 `tools` 做按名合并并保持本地顺序在前，其余数组仍原样拼接；不要照搬 Clash Party 的 `+` 键名语法，借它的「操作与值解耦」思路即可。
- 跨协议思考签名重放门禁（调研完成，低优先级、未排期）：目前跨协议切模型的需求不多，方案先放末尾，见 [`design/thinking-signature-replay.md`](design/thinking-signature-replay.md)。切模型时只重放目标协议自己产生的 reasoning，其余整块跳过但保留存储与 UI；DeepSeek thinking 需要无签名回放，门禁必须按协议判断；工具轮与 Gemini 的 `skip_thought_signature_validator` 兜底放在第二阶段。Claude、OpenAI Responses、OpenRouter 的签名清除也依赖这项，单项剥离会留下非法块。

上游查看与 issue 调研记录见 [`history/upstream.md`](history/upstream.md)，已完成的落地批次见 [`history/milestones.md`](history/milestones.md)。

### 已知交互缺口

- Termux 式终端功能键栏还不能长按重复：`TerminalExtraKey` 目前只有 `clickable`，按住方向键、PgUp/PgDn、Home/End 不会连发；需要改成按下后延迟触发并循环，Ctrl/Alt/IME 这类切换键不重复。注意与终端自身的触摸滚动、软键盘焦点和可访问性手势冲突，设备上要分别验短按、长按、滑出取消和连续触发。
- 聊天文本的长按选择/复制覆盖不全：`SelectableContent` 目前只包住 Markdown 正文、推理、代码块、翻译页和记忆文件等部分 surface；工具卡片摘要/预览、部分纯 `Text`、浮动窗口等仍可能没有选区或长按菜单，消息的点击/选择模式也可能先吃掉手势。需要按 surface 审计，统一接 `BetterSelectionContainer`，避免嵌套选择容器和与消息操作冲突；设备上覆盖普通消息、流式回复、工具参数/结果、翻译、代码块、浮动窗口和选择模式。

### 提示词注入与世界书

模式注入与世界书已经移除（C157）。当前状态：请求装配不再有注入步骤，扩展页与助手/会话绑定入口、SillyTavern 世界书导入、单条注入/世界书导出和 Web 端 injections 端点都已删除；旧备份 `settings.json` 里的 `mode_injections`/`lorebooks` 由 `ignoreUnknownKeys` 忽略。`conversation` 表的 `mode_injection_ids`/`lorebook_ids` 两列保留，仅服务官方库导入与官方兼容导出，仓库层固定写 `"[]"`。

移除的原因是这套实现与需求不匹配：注入靠改写已有消息（拼 system、往历史中间插）破坏前缀缓存；没有 token 预算、触发概率、递归触发与全词/CJK 匹配；会话固定的是 id 而不是内容快照；`scanDepth` 之类的边界也没有防护。若以后重做，需要重新立项，方向仍是把系统触发的提示作为新轮次追加到消息列表末尾，而不是修改已有消息。

### 失败恢复与上下文的返工

失败恢复与消息模型的冻结已解除。B1 第一阶段已落地（C97）：step 记录与请求投影、失败消息保留已执行工具已在设备上验证；自动重试恢复为默认关闭的可选项（C75），C76 的 attempt 摘要已持久化；手动 step 重试按 C98 落地；审批/取消的 step 结算与最后节点审批收口已完成。B1 设计稿在 [`design/b1-step-commits.md`](design/b1-step-commits.md)，B2 草稿在 [`design/b2-step-messages.md`](design/b2-step-messages.md)。

当前状态与剩余：

- 重试恢复与消息模型 B1：已完成 step 记录、Claude step 级请求投影、失败工具事实、审批/取消 step 结算、最后节点审批收口（C97）与默认关闭的自动重试（C75）；C76 的 attempt 摘要已持久化，设计见 [design/b1-step-commits.md](design/b1-step-commits.md)。B2 已开始：Web DTO 的 status/steps、conversation_search 的失败消息过滤（单次会话加载）、上下文边界投影对齐已落地；C107 补了 step 稳定 id 与 failureCode；官方导出按上游 `ignoreUnknownKeys` 容忍未知字段，普通消息（无 steps 字段）与带 steps 样本均已验；手动 step 重试已落地（C98），搜索加载优化见 [design/b2-step-messages.md](design/b2-step-messages.md)。
- C58 半途失败投影已由 B1 第一阶段落地（C97）：聊天请求投影保留失败前已执行的工具事实，失败 step 的正文与推理不进请求；标题与摘要已按 step 投影（C122），记忆仍整条丢弃失败消息。B2 的消费方收尾已完成：非 FAILED 消息的投影也丢弃未完成的 ServerTool，Web 端显示失败/中断标签与历史 attempt 摘要；错误卡片的 retry 目标字段随 C77 永久取消而作废。搜索、浮动窗口、导出与 FTS 按设计保持现状。
- C86 思考强度（已落地）：`OFF` 保留为明确关闭，新增 `NOTSET` 表示不发参数，`TextGenerationParams` 默认改为 `NOTSET`，OCR 和 Qwen MT 翻译不再带 `none`，翻译的 int budget 用 `-2` 哨兵保留默认档；官方兼容导出把 `NOTSET` 归一化成 `OFF`，会话 config 同步替换。deepseek 等 provider 的具体映射仍按现有能力表维护，后续只有新 provider 接入时再补调研。
- C87 OCR 文本归属（Phase 1 已落地）：Room 34 新增 `file_derivations`，`file_id` 指向 `managed_files.id`，`kind`、`text`、`producer`、`edited`、时间戳；OCR 优先读文件级派生，旧消息 metadata 作为回退并懒回填；删除文件级联清理，自家备份恢复保留手改行、清掉自动行，官方导出登记并裁掉整张表。producer 指纹失效已补（C108），附件管理里的 OCR 文本编辑入口已补（C109），备份恢复合并手改行已补（C110），设计见 [design/c87-file-derivations.md](design/c87-file-derivations.md)。

### Markdown 渲染能力

- HTML `<svg>` 支持已定：Web 正文保留静态 SVG，但改走显式 allowlist（`svg-schema.ts`）并限制尺寸；Android 正文在气泡内用 Coil `SvgDecoder` 渲染块级 SVG（C121），默认折叠、点击展开一级，不安全或解码失败才回退到整条消息预览。SVG 文件预览的相对 `<image href>` 已由 C132 接入（WebView 以文件目录为 base，未做真机验证）；Markdown 预览里的相对 `![]()` 仍未解析，且聊天内联 SVG 仍整体拒绝 `<image>`。
- Mermaid 版本已统一到 11.17.2：Android 资产换成官方 IIFE（DOMPurify 3.4.12）、Web workbench 和 `mark.html` 的本地版本钉死，三处都改成 `securityLevel: strict` + `htmlLabels: false`；`controls.mermaid=false` 只隐藏按钮，不控制渲染。Web 正文的 Mermaid 已由 C135 接入；引擎在独立的懒加载 chunk 里（约 0.68 MB，gzip 162 KiB），首次出现图时才加载；聊天路由另有约 1.36 MB 的 route/streamdown chunk，不含引擎。`mark.html` 的离线依赖已由 C134 本地化，不再依赖 esm.sh。

### 重试、失败尝试与用量

DSH 对照版本 `dsh-v0.2.1-rc.5`（`b830ec950f`）。结论：DSH 也不做逐 token 续传，重试单位是 agent step；失败 attempt 以 `assistant/attempt` 落日志但不进入模型历史，成功才写 `assistant/message`；用户主动停止且已有内容时写 `interrupted: true` 的 `assistant/message`。重试策略由 provider 拥有，默认对 `EMPTY_RESPONSE`、`RATE_LIMIT`、`SERVER`、`TIMEOUT`、`TRANSPORT` 最多重试 5 次，也就是最多 6 次请求；退避从 500ms 到 10s，10% 对称抖动，读取 `Retry-After`；每次重试先落 `llm/retry` 再等待。参考文件：`packages/llm/llm-retry`、`packages/llm/llm/src/retry-policy.ts`、`packages/core/agent-loop/src/agent.ts`、`packages/core/session/README.md`、`packages/llm/llm-deepseek/src/transport.ts`。

RikkaHub 现状：

- 重试单位是 step，`withRetry` 失败后回到 `stepStart`，前面的 step 和工具结果保留；单步回复看起来就是整条重来。DSH 也是这个语义，不是 RikkaHub 独有。
- C61 把失败归一化为 `HttpException`/`LlmFailure`：流式和非流式的 2xx 之外的响应都带 HTTP status、错误码和 `Retry-After`；默认只重试 `RATE_LIMIT`、`SERVER`、`TIMEOUT`、`TRANSPORT`、`EMPTY_RESPONSE`，鉴权、配额、非法请求和 context overflow 不重试。空回复现在按 `EMPTY_RESPONSE` 归入同一策略。C75 把重试次数放回网络设置，默认关闭；只有用户开启后 `Settings.retryPolicy()` 才返回策略，基础退避与 `Retry-After` 的实现都在。`retryOn` 保留为自定义逃生口。
- C58 已给失败和用户停止的回复加 `FAILED`/`INTERRUPTED` 状态；请求组装过滤失败正文，标题与摘要按 step 投影，记忆整理和 `conversation_read` 仍整条过滤 `FAILED`，失败半截不会再作为普通回复进入下一轮。B1 第一阶段的请求投影已按 step 保留失败前已执行的工具事实，标题与摘要已按 step 投影（C122），记忆仍整条隔离失败消息。
- 停止或下一次发送时，未执行工具补恢复结果，按 `startedAt` 区分 `not_started`/`outcome_unknown`；审批恢复路径中的 Approved/Denied/Answered 工具仍交给 AgentLoop 继续执行。
- UI 有失败/中断标记；C76 的 attempt 行已随 C75 默认关闭的重试恢复，默认不出现，历史 step 的摘要会持久化；C77 的错误卡片重试永久取消，错误卡片只提示错误信息；C80 给 `usage_record` 加 outcome，失败成本保留、缓存命中率排除失败。

当前状态：

- 失败 attempt 与成功消息分开（剩余）：C58 已实现 `UIMessage.status` 标记、失败正文从请求过滤和 UI 失败/中断标签；相邻 user 合并已由 C160 移到 provider 转换层。剩余：失败消息的 step 级请求投影已由 C97 落地，标题与摘要已按 step 投影（C122），记忆与 `conversation_read` 仍整条隔离；完整审批/取消 step 状态机已落地，自动重试已由 C75 以默认关闭恢复。工具恢复结果已由 C65 处理。
- 稳定错误码与可配置策略（已落地）：C61 已把 provider 非 2xx 和空回复统一成带 `code`、`status`、`retryAfterMs` 的失败类型，覆盖 `AUTH`、`QUOTA`、`RATE_LIMIT`、`SERVER`、`TIMEOUT`、`EMPTY_RESPONSE`、`CONTEXT_WINDOW_EXCEEDED`、`INVALID_REQUEST`、`UNKNOWN`；裸传输错误仍按 `IOException` 重试，`TRANSPORT` 留给调用方包装。策略是 normal mode：`maxRetries`、`retryableCodes`、`initialDelay`、`maxDelay`、`jitter`；默认只重试瞬时错误，读取 `Retry-After` 并设上限；C75 把 `maxRetries` 放回网络设置并默认关闭；`Settings.retryPolicy()` 只在用户开启后返回策略。保留 step 级重试的实现，不做 prefill 续写。验收：429、5xx、空回复可重试；400、401、403、额度、上下文超限不重试；用户停止可取消等待；重试不重复工具副作用；重试请求与前缀缓存兼容。
- 重试状态 UI（已落地）：C75 已恢复为默认关闭的网络设置入口，C76 的 attempt 摘要已随 step 持久化；C77 的错误卡片 Retry 永久取消，错误卡片只做信息提示；见「失败恢复与上下文的返工」。
- 用量区分成功与失败 attempt（已落地）：C80 已给 `usage_record` 加 `outcome`（SUCCESS/FAILED/CANCELLED/UNKNOWN，旧数据 UNKNOWN），失败成本保留、统计页缓存命中率排除 FAILED/CANCELLED；消息卡片继续只显示成功 attempt 的 usage。attempt/retryId 没有单独落列，按 outcome 收口。验收：Room 32 到 33 迁移、旧数据默认 UNKNOWN、真实 app 库迁移、data.sync 53 条和官方导出裁剪都通过。32 到 33 没有降级迁移，回退旧构建前要保留 v32 库副本或继续用新构建。

### DSH 对照审计

DSH 对照检查记录、修复顺序与范围说明见 [`audit/dsh.md`](audit/dsh.md)。
