# 定制分支

本仓库基于上游 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 维护一组个人定制：去掉不需要的功能，以及按需补充的小功能。

定制与上游持续同步，因此这里记录的不是一次性改动，而是长期维护所需的约定。

| 文档 | 内容 |
| --- | --- |
| `README.md` | 分支模型、上游同步流程、定制原则 |
| [`checklist.md`](checklist.md) | 定制清单：索引与逐项的落点、理由、合并注意 |
| [`build.md`](build.md) | 构建、测试、格式检查、设备上的 androidTest |
| [`locale.md`](locale.md) | 8 份 locale 的增删文案与品牌改写 |
| [`backup/`](backup/README.md) | 官方备份的导入与导出：恢复链路、导入器、验收 |
| [`roadmap.md`](roadmap.md) | 定位、阶段划分、追更策略、分发与合规 |

大的上游同步可以另建一份作业单放在 `upstream/`，合并落地后删除；当前没有作业单，所以那个目录不存在。

## 分支模型

| 分支 | 用途 |
| --- | --- |
| `master` | 只跟随上游，不承载任何本地提交，任何时刻都应可 fast-forward |
| `custom` | 唯一工作分支，全部定制提交集中在这里 |

远端配置：

| 远端 | 仓库 | 用途 |
| --- | --- | --- |
| `origin` | NingmengLemon/rikkahub | 本仓库 fork，可推送 |
| `upstream` | rikkahub/rikkahub | 官方仓库，只读；push 地址是 `DISABLED_no_push_to_upstream`，误推会直接失败 |

远端默认分支是 `custom`。本地 `origin/HEAD` 的缓存仍指向 `origin/master`，可用 `git remote set-head origin -a` 刷新。

`origin/master` 要保留并跟随上游：`spotlessCheck` 拿它当比较基点，原因见 [`build.md`](build.md) 的格式检查一节。

## 上游同步流程

```powershell
git fetch upstream --no-tags
git checkout master
git merge --ff-only upstream/master
git push origin master
git checkout custom
git merge master
```

`master` 上用 `--ff-only`，一旦出现非快进说明有东西误提交到了 `master`，先查清楚再继续，不要用普通 merge 掩盖。

合并到 `custom` 后跑一遍测试：

```powershell
$env:JAVA_HOME='E:\WorkBench\Java\jdk-23.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test --continue --console=plain
```

判据是「没有新增失败」，不是「全部通过」；Windows 上固定失败的那批用例见 [`build.md`](build.md) 的测试一节。

测试之外还有两步：按 [`checklist.md`](checklist.md) 逐项复核定制没有被静默丢掉，按 [`locale.md`](locale.md) 处理新增或删除的文案。

以上是当前的合并流程。按模块边界分层的追更方式尚未落地，方向见 [`roadmap.md`](roadmap.md)。

### 当前待处理的同步

无。上游已同步到 `a5db9c9b`，下一次同步从该点开始。

## 定制原则

本节中「改动尽量小，优先断入口」与「避开上游热区」两条是为降低上游合并成本而设的，会随整分支合并结束而失效；其余几条不依赖合并成本。失效的时机、放开之后的具体差异，以及另外几处同类的合并成本让步，见 [`roadmap.md`](roadmap.md) 的「定制原则的适用范围」一节。

### 改动尽量小，优先断入口

删除功能时优先改条件、去掉入口，而不是删掉整段实现。上游改同一功能的实现时，条件行通常不会冲突，删光的实现则会在每次上游改动时反复冲突。

确实要删整个文件时，把路径记进 [`checklist.md`](checklist.md)，合并时专门检查上游对该文件的改动。

C8 与 C9 删的是完整实现而不是入口。这两项功能各自独立成文件，改动集中在自己那一小块里，不进入上游的核心改动路径；保留断入口会让每次上游改动都要重新确认入口是否还在，维护成本高于删除。与上游热区重叠的功能仍按上面这条规则处理。

### 数据结构

数据结构的冻结已解除：官方备份的导入器已实现，验到的是 v32 与 v31 的表数据与转换产物、v32 的附件与 settings。这条冻结针对的是「官方备份能导进来」，这条路已经通了。没验到的判据逐类列在 [`backup/acceptance.md`](backup/acceptance.md) 的覆盖范围里。改 Room schema 与 `Settings` 结构不再需要额外的门禁。

解除之后仍然成立的是对自家历史数据的兼容。这几条与导入器无关，不会随冻结一起消失：

- Room 改结构时同时写迁移。同一安装下，本 fork 旧构建产出的备份要靠迁移链升上来（换了包名、库里又带附件引用的，在源版本有快照时会被 C25 改走转换器）
- 新增序列化字段必须带默认值，旧数据仍要能读出。典型载体是 `UIMessage`、`Conversation`、`Assistant` 这类模型，以及 DataStore 里的 `Settings`
- 不改已有字段与 DataStore key 的含义。改含义不会让 schema 指纹变化，Room 照常打开并读出错的数据，任何基于 header 的判定都发现不了
- 确实要改含义时，先建立写入侧的字段映射表，并配一条覆盖全部列的完整性测试。这张表现在没有，在它建起来之前这条就是禁止
- 每次改完重跑验收样本

需要「记录额外状态」时，优先考虑运行时内存或带默认值的新字段，而不是改已有结构。

改结构还有一个直接后果：官方兼容导出（C18）在双方结构分叉后会直接失败，直到裁剪逻辑落地。这是刻意行为，不是故障。

### 一个定制一个提交

提交信息用 `custom` 作为 scope，例如 `feat(custom): ...`、`refactor(custom): ...`，便于检索。一项定制一个提交，同时在 [`checklist.md`](checklist.md) 里登记。

审计全部定制：

```powershell
git log --oneline master..custom
```

### Review 门禁

每批较大改动完成后，推送前必须过一次独立 review。目的是用干净上下文复核实现者的判断，避免实现者给自己打分。

- 用 `subagent` 派发，不要用 `subagent_fork`。fork 会继承实现这批改动的对话上下文，失去独立视角的意义。
- 在派发的 prompt 里要求子代理先加载 `code-review` 技能，再按其流程执行。
- review 基线取 `master..custom`，即本批定制相对上游的全部改动。
- 关注三件事：是否引入回归、是否触碰已存储数据的结构、是否符合本文件的定制原则。C8 与 C9 按「改动尽量小，优先断入口」一节的补充说明直接删了实现，不按「优先断入口」判；这条评审标准本身随该原则一并失效。
- 未通过就修复后重跑，不得带病推送。通过后才允许 `git push origin custom`。

### 文档与实现

文档里关于运行时行为的每一句，写之前先读实现，写完再核一遍。不要从旧文档搬运：旧文本可能描述的是另一个分支、另一版实现，或者只是当时的猜测。

三个具体的坑，都是真踩过的：

- 去时间化最容易写反。把「当时是 X」改成现状表述时，先确认代码现在真的是 X，而不是曾经在某个分支上是 X。
- 不要为了让结论好看而给缺口补解释。缺口是什么就写什么，不要顺手推断它「不影响」哪条路径。
- 代码改动让行为变了，同一个提交里把受影响的文档改掉。文档落后一轮之后，下一次改动就会把它当事实搬运。

### 避开上游热区

两个口径都看，用途不同。以下为 2026-10-20 在 `e5bb7073`（上游 3.1.0 的基点）上实测，路径取精确文件路径（用通配符会多计）。

全历史改动次数，反映该文件长期被上游改动的密度，也意味着其中积累的结构性变化多：

| 文件 | 全历史 |
| --- | --- |
| `ChatVM.kt` | 175 |
| `RouteActivity.kt` | 116 |
| `ChatService.kt` | 114 |
| `ChatDrawer.kt` | 38 |
| `FilesPicker.kt` | 16 |

最近 200 个提交内的改动次数，反映当前活跃度：

| 文件 | 近 200 提交 |
| --- | --- |
| `ChatService.kt` | 19 |
| `ChatDrawer.kt` | 10 |
| `ChatVM.kt` | 8 |
| `RouteActivity.kt` | 8 |
| `FilesPicker.kt` | 6 |

两个口径会给出不同结论。C1 与 C2 按全历史属于动了最密集的两个文件，按近窗口则是中等热度。C5 的语音清理落在 `ChatService.kt` 上，它是近期最热的文件之一，合并时按 [`checklist.md`](checklist.md) 的合并注意逐项复核。

`GenerationLoop.kt` 已随上游 3.0.0 的重构删除，多步生成循环迁到 `ai/src/main/java/me/rerere/ai/agent/AgentLoop.kt`。后者随 3.1.0 的合并进来，此后上游每次改它都在累计热度，目前仍然偏低。

上游另有两个长期未合并的重构分支 `refactor/chat-service`（停在 08-26）与 `refactor/ai-stream`（停在 08-12）。它们目前不构成冲突来源，但若上游重启这类重构，上述文件会整体重写，届时应暂停对热区的新定制。
