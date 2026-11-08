# 定制分支

本仓库基于上游 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 维护一组个人定制：去掉不需要的功能，以及按需补充的小功能。

定制与上游持续同步，因此这里记录的不是一次性改动，而是长期维护所需的约定。

| 文档 | 内容 |
| --- | --- |
| `README.md` | 分支模型、上游同步流程、定制原则 |
| [`checklist.md`](checklist.md) | 定制清单：索引与逐项的落点、理由、合并注意 |
| [`build.md`](build.md) | 构建、测试、格式检查、文档检查、设备上的 androidTest |
| [`locale.md`](locale.md) | 四份 locale 的增删文案与品牌改写 |
| [`prompts.md`](prompts.md) | 各功能默认提示词的现状、重写计划与压缩提示词草稿 |
| [`backup/`](backup/README.md) | 官方备份的导入与导出：恢复链路、导入器、验收 |
| [`roadmap.md`](roadmap.md) | 定位、阶段划分、条目索引与依赖、独立更新、分发与合规 |

大的上游同步可以另建一份作业单放在 `upstream/`，合并落地后删除；当前没有作业单，所以那个目录不存在。

## 分支模型

| 分支 | 用途 |
| --- | --- |
| `ref` | 上游镜像，只跟随上游，不承载任何本地提交，任何时刻都应可 fast-forward |
| `main` | 唯一工作分支，全部定制提交集中在这里 |

远端配置：

| 远端 | 仓库 | 用途 |
| --- | --- | --- |
| `origin` | NingmengLemon/rikkahub | 本仓库，可推送；已从 GitHub 的 fork network 脱离 |
| `upstream` | rikkahub/rikkahub | 官方仓库，只读；push 地址是 `DISABLED_no_push_to_upstream`，误推会直接失败 |

远端默认分支是 `main`，`origin/HEAD` 指向它；缓存过期时用 `git remote set-head origin -a` 刷新。

`origin/ref` 与 `origin/main` 都要保留：`ref` 是上游镜像，`main` 是工作分支。`spotlessCheck` 以 `origin/main` 为比较基点，与 `origin/ref` 无关，原因见 [`build.md`](build.md) 的格式检查一节。

## 上游参考流程

本仓库按独立更新维护：上游只作参考，不合并。`ref` 是上游镜像，按下面的方式更新：

```powershell
git fetch upstream --no-tags
git checkout ref
git merge --ff-only upstream/master
git push origin ref
```

`ref` 上用 `--ff-only`，一旦出现非快进说明有东西误提交到了 `ref`，先查清楚再继续，不要用普通 merge 掩盖。

看上游改了什么的入口：

```powershell
git log --oneline <上次查看点>..upstream/master
```

按 [`roadmap.md`](roadmap.md) 的模块参考表筛：保命层读 diff 自己移植，能力层评估后自己实现，地基层跳过。移植之后跑测试，判据是「没有新增失败」，Windows 上固定失败的那批用例见 [`build.md`](build.md) 的测试一节。移植时按 [`checklist.md`](checklist.md) 复核已登记的定制没有被覆盖，新增或删除文案按 [`locale.md`](locale.md) 处理。

完整的移植流程与模块划分见 [`roadmap.md`](roadmap.md) 的「独立更新」一节。

### 上次查看点

上游镜像停在 `5ad19405`，下一次看上游从该点之后开始。

## 定制原则

「改动尽量小，优先断入口」与「避开上游热区」两条是合并成本的对价；在独立更新下它们在 `app/` 里不再适用，只有 `ai/` 仍按「改动尽量小」处理。其余几条不依赖合并成本。具体差异见 [`roadmap.md`](roadmap.md) 的「定制原则的适用范围」一节。

### 改动尽量小，优先断入口

这条现在只约束 `ai/`：它仍要参考上游，改动越小，移植上游对同一文件的改动时越容易比对。删除 `ai/` 里的功能时优先改条件、去掉入口，而不是删掉整段实现。确实要删整个文件，把路径记进 [`checklist.md`](checklist.md)，移植上游对该文件的改动时专门核对。

`app/` 不受这条约束：该删的实现在那里可以直接删掉，不必为了可合并性留入口或死代码。

### 数据结构

数据结构的冻结已解除：官方备份的导入器已实现，验到的是 v32 与 v31 的表数据与转换产物、v32 的附件与 settings。这条冻结针对的是「官方备份能导进来」，这条路已经通了。没验到的判据逐类列在 [`backup/acceptance.md`](backup/acceptance.md) 的覆盖范围里。改 Room schema 与 `Settings` 结构不再需要额外的门禁。

解除之后仍然成立的是对自家历史数据的兼容。这几条与导入器无关，不会随冻结一起消失：

- Room 改结构时同时写迁移。同一安装下，本 fork 旧构建产出的备份要靠迁移链升上来（换了包名、库里又带附件引用的，在源版本有快照时会被 C25 改走转换器）
- 新增序列化字段必须带默认值，旧数据仍要能读出。典型载体是 `UIMessage`、`Conversation`、`Assistant` 这类模型，以及 DataStore 里的 `Settings`
- 不改已有字段与 DataStore key 的含义。改含义不会让 schema 指纹变化，Room 照常打开并读出错的数据，任何基于 header 的判定都发现不了。判断口径：「含义」指这个值表示什么；某个列表项变成必然包含、读取时补回缺省项不算改含义，前提是旧值仍能正确解码、不丢数据。`webDavConfig.items` 的 `DATABASE` 属于这一类（C40）
- 确实要改含义时，先建立写入侧的字段映射表，并配一条覆盖全部列的完整性测试。这张表现在没有，在它建起来之前这条就是禁止
- 每次改完重跑验收样本

需要「记录额外状态」时，优先考虑运行时内存或带默认值的新字段，而不是改已有结构。

改结构还有一个直接后果：官方兼容导出（C18）在双方结构分叉后会直接失败，直到裁剪逻辑落地。这是刻意行为，不是故障。

### 一个定制一个提交

提交信息用 `custom` 作为 scope，例如 `feat(custom): ...`、`refactor(custom): ...`，便于检索。一项定制一个提交，同时在 [`checklist.md`](checklist.md) 里登记。

例外：几项定制共用同一批实现（同一个函数签名、同一个 ViewModel 的状态、同一批文件的布局），拆开需要为同一批文件维护多个只存在于历史里的中间版本时，可以合成一个提交。正文逐项写清编号与合并原因，checklist 仍一项一行；能单独编译、单独回滚的项不适用这一条。

审计全部定制：

```powershell
git log --oneline ref..main
```

### Review 门禁

每批较大改动完成后，推送前必须过一次独立 review。目的是用干净上下文复核实现者的判断，避免实现者给自己打分。

- 用 `subagent` 派发，不要用 `subagent_fork`。fork 会继承实现这批改动的对话上下文，失去独立视角的意义。
- 在派发的 prompt 里要求子代理先加载 `code-review` 技能，再按其流程执行。
- review 基线取 `origin/main..main`，即本批还没推出去的改动；要审计全部定制用 `git log --oneline ref..main`。
- 关注三件事：是否引入回归、是否触碰已存储数据的结构、是否符合本文件的定制原则。`app/` 里已经不再要求「优先断入口」，评审按当前原则判断。
- 未通过就修复后重跑，不得带病推送。通过后才允许 `git push origin main`。

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
