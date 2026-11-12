# 验收

备份导入与恢复的验收方式。判据要有独立性：期望值由仓库外的脚本从原始备份算出，不能用被测的读取层自己算。

## 样本

六类，至少各一份：

1. 带附件引用的官方客户端备份。跨安装，会命中 C25 的探测而走转换器，验的是转换与附件路径重写。内容要覆盖多分支会话与重新生成、带附件与工具调用的消息、多 provider 与自定义 assistant、自定义主题与提示词。
2. 构造样本：把备份库的 identity 改掉，验证恢复分流。C80 后本仓库 Room 33、官方快照只到 v32；v33 同版本但 identity 未知且没有官方快照会被拒绝。Convert 分支要求双方版本号相同、identity 不同且该版本有快照，当前生产不可达，`OfficialRestorePlannerTest` 用显式的 v32 快照覆盖该分支。
3. 本 fork 旧构建导出的备份（改包名前，包名还是 `me.rerere.rikkahub`，带附件引用）。同样跨安装、同样命中 C25 走转换器；与第 1 类的区别是版本更低、settings 更旧。
4. 本应用自己导出、且与当前安装同一包名同一用户号的备份。不跨安装。消息引用里新写或重存过的是相对形态（C36），按当前安装解析；没重存过的仍是绝对路径，可能是当前安装的，也可能是官方或旧包名的，带这类引用的库按 [`README.md`](README.md) 的探测说明处理。设置仍是绝对路径。版本低于当前的自家备份没有样本或用例。
5. 版本高于我们的库，必须明确报错。
6. 官方兼容出口（要求 8）产出的备份，交给官方客户端恢复。步骤与判据见「设备验收」的「官方兼容出口」。

样本含私人数据，放仓库外、放设备上；测试用 `Assume` 跳过，缺哪份就跳过哪份，CI 不会因此失败。本文件记判据、样本类别与设备验收的对照值，样本文件本身不进仓库。

## 设备验收

两项都要人工看结果，不进 CI。对照值按「带引用的节点数」记：一条节点的消息里可能有多处引用，脚本会把处数与节点数分开打印。

### 官方兼容出口

1. 应用里导出，出口选官方兼容（`BackupTarget.OFFICIAL`）。
2. 装官方客户端（`me.rerere.rikkahub`），从文件恢复这份 zip。官方恢复不重建全文索引，恢复完搜索结果可能为空；搜索那条判据以两边索引都建好的状态为准。
3. 在会话里打开那张测试图片，确认能显示。
4. 停掉应用，把库联同 `-wal`、`-shm` 拉下来数节点与引用，再数 upload 文件。`PKG` 换成实际包名，序列号按设备填：

   ```bash
   PKG=me.rerere.rikkahub
   mkdir -p build
   rm -f build/rikka_hub.pulled build/rikka_hub.pulled-wal build/rikka_hub.pulled-shm
   adb -s <序列号> shell am force-stop $PKG
   # 主库一定在；-wal / -shm 在干净关闭过的情况下可能没有，按可选处理。
   # 设备侧先清同名旧文件，免得把上一次另一个包的 sidecar 也拉下来。
   adb -s <序列号> shell "su -c 'rm -f /data/local/tmp/rikka_hub* && cp /data/user/0/$PKG/databases/rikka_hub* /data/local/tmp/ && chmod 644 /data/local/tmp/rikka_hub*'"
   adb -s <序列号> pull /data/local/tmp/rikka_hub build/rikka_hub.pulled
   for suffix in -wal -shm; do
       adb -s <序列号> pull /data/local/tmp/rikka_hub$suffix build/rikka_hub.pulled$suffix 2>/dev/null || true
   done
   python tools/backup-ref-counts.py --db build/rikka_hub.pulled
   # upload 目录不存在时这条返回非零，不会给出 0
   adb -s <序列号> shell "su -c 'test -d /data/user/0/$PKG/files/upload && ls /data/user/0/$PKG/files/upload | wc -l'"
   ```

   本次结果（官方侧，改名之前测的）：会话 170、节点 676；66 个节点带附件引用，全部是官方安装的绝对路径，相对形态 0 个节点；upload 文件 98；测试图片能显示。要连搜索一起验，按「判据」的查询集在两边各跑一次。

### 换包名恢复

本应用自己导出的归档，换到另一个包名的安装上恢复。验的是库里落相对的引用按新安装解析，旧的官方绝对引用经 C25 改写成新安装。

1. 原安装里导出自家归档；本地导出默认勾选工作区，为了保持 C69 基线可比，导出前取消勾选工作区文件。
2. 临时把 `app/build.gradle.kts` 里 debug 的 `applicationIdSuffix` 改成 `.crosscheck`，构建并安装；换包名只让安装独立，不改数据格式：

   ```bash
   ./gradlew assembleGithubDebug
   adb -s <序列号> install -r app/build/outputs/apk/github/debug/app-github-arm64-v8a-debug.apk
   ```

3. 新安装里从文件恢复这份 zip，重启应用等 pending 生效，日志里有 `Rebuilt search indexes after restore`。
4. 在会话里打开测试图片，确认按新安装显示；数库只能证明引用形态，解析得靠应用这边看。
5. 按上一节的命令取数，把 `PKG` 换成 `top.lemoneko.rakkihub.crosscheck`。
6. 验完把 `applicationIdSuffix` 改回 `.debug`，卸载 `.crosscheck` 安装。

本次结果（改名之前测的）：会话 170、节点 676；2 个节点带相对引用，按新安装解析；64 个节点带官方绝对引用，经 C25 改写成新安装；upload 文件 98；测试图片正常显示。改名落地之后，被导入方的 `upload/` 是去重后的数量，要重跑取数。

## 判据

- 每张表按主键排序后的行数与内容 SHA-256，规范化规则见下。
- 附件的内容摘要与「首次启动后 live 目录里没有归档之外的多余文件」。`upload/` 下的文件导入时按内容改名（C40），期望的落盘名由期望文件里的 sha256 与归档里的扩展名算出，同内容的条目会合并成一份，所以比较的是期望值去重后的集合；其它附件目录按原名。`RestoreAppliedOutcomeTest` 拿这份集合与 live 的 `upload/` 逐项比，归档里出现 `skills/`、`fonts/` 这类目录时会直接失败（当前 v32 样本只有 `upload/`）。
- settings 的关键字段：助手与服务商的条数与 id 集合、当前助手、当前模型。期望值必须从同一份归档里取，跨归档借 `settings.json` 会把期望值算成另一份样本的数，表现为导入像丢了数据。
- 搜索：固定一组查询与期望的会话 id 集合，导入前后结果集相同。**对比时要把搜索范围调成同一个**：搜索页有「当前助手 / 全部助手」两种范围，范围不同结果集不可比。基准取恢复之前（或官方客户端手动重建索引之后）的状态：官方客户端恢复后不重建全文索引，同一份备份在官方那边恢复出来会搜不到本该有的结果，那是索引没建，不是数据丢了。我们的 FTS 用 `tokenize = 'simple'`，与官方的分词不一定一致，所以判据要求「同一组查询在官方客户端能搜到、导入后在本 fork 也能搜到相同的会话」。这条需要官方客户端，是人工闸门，不进 CI。

## 规范化

每个单元格编码成「类型字节 + 4 字节小端长度 + 载荷」：`N` NULL 空载荷、`I` 十进制 ASCII、`R` IEEE754 双精度 8 字节大端、`T` UTF-8、`B` 原始字节。行之间写一个 `0x1e`，单元格顺序取快照里的列顺序，行顺序按主键升序。

规则由 `tools/backup-expectations.py` 与 `OfficialSampleAcceptanceTest` 各实现一遍，两边逐字节一致。期望值对附件绝对路径做前缀归一（换成 `file://<files>/`），否则「路径被正确重写」会被当成「内容不一致」。

## 跑法

期望值文件一次算全：表摘要、settings 与附件清单都写进同一个 json。缺了哪一段，对应的断言会直接失败而不是跳过，所以 `--settings` 与 `--zip` 不能省。

```bash
# 1. 从原始备份里取出库与 settings，连同整份 zip 一起算期望值
python tools/backup-expectations.py \
    --schema app/schemas/me.rerere.rikkahub.data.db.AppDatabase/32.json \
    --db <备份解压出来的 rikka_hub.db> \
    --settings <同一份备份解压出来的 settings.json> \
    --zip <完整备份 zip> \
    --out <样本目录>/backup-expectations-v32.json

# 2. 推到设备，先删同名文件
adb shell rm -f /data/local/tmp/rikkahub-sample-v32.db \
    /data/local/tmp/rikkahub-sample-v32.zip \
    /data/local/tmp/backup-expectations-v32.json
adb push <备份解压出来的 rikka_hub.db> /data/local/tmp/rikkahub-sample-v32.db
adb push <完整备份 zip> /data/local/tmp/rikkahub-sample-v32.zip
adb push <样本目录>/backup-expectations-v32.json /data/local/tmp/backup-expectations-v32.json

# 3. 三个测试类各跑一次
adb shell am instrument -w \
    -e class me.rerere.rikkahub.data.sync.official.OfficialSampleAcceptanceTest \
    <包名>.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w \
    -e class me.rerere.rikkahub.data.sync.OfficialBackupRestoreAcceptanceTest \
    <包名>.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w \
    -e class me.rerere.rikkahub.data.sync.RestoreAppliedOutcomeTest \
    <包名>.test/androidx.test.runner.AndroidJUnitRunner
```

v31 的样本推成 `rikkahub-sample-v31.db` 与 `backup-expectations-v31.json`，它只参与第一个测试类。它的期望值要用 `--schema app/schemas/me.rerere.rikkahub.data.db.AppDatabase/31.json` 生成，测试会拿这个值断言期望值与样本的版本一致。

## 覆盖范围

| 类别 | 样本或测试 | 状态 |
| --- | --- | --- |
| 官方客户端导出 | v32（官方 3.1.1，带附件引用） | 表数据与转换产物全量；附件逐个比；settings 的条数、id 集合、当前助手与模型 |
| 构造样本 | `BackupManagerTest` 里改 v33 identity 的拒绝用例、`OfficialRestorePlannerTest` 的 v32 Convert 用例 | 覆盖当前拒绝和未来 Convert 分支接线 |
| 本 fork 旧构建 | v31（产出自 3.1.0 (193)，包名还是 `me.rerere.rikkahub`） | 表数据与内容摘要；settings 只手工核对过条数（经 migrate 与 decode 后不变），没有脚本或测试复现；附件未逐个比 |
| 本应用自己导出 | `BackupManagerTest` 的常规恢复用例与 `foreignAttachmentPathsAreRewrittenOnSameVersionRestore`、`OfficialForeignAttachmentPathsTest`（探测判定） | 常规用例覆盖原路径，但没有插入引用，「引用都属于当前安装、恢复时不走转换器」这条恢复路径没有用例；探测判定由 `OfficialForeignAttachmentPathsTest` 覆盖（androidTest，不进 CI）。指向别的安装的旧绝对引用命中 C25，同版本也走转换器；新写或重存过的消息引用是相对形态，按当前安装解析；迁移链升级无样本或用例 |
| 高版本库 | `BackupManagerTest` 的拒绝用例 | 验过 |
| 缺条目拒绝 | `BackupManagerTest` 的缺库、缺设置、附件名带反斜杠三条用例 | 2026-11-04 设备上验过：三条都在解压之前失败，不留暂存，现有库与附件不动 |
| 导入改名与去重 | `ImportedUploadFilesTest`（JVM）、`BackupManagerTest` 的改名与成链用例、`OfficialBackupRestoreAcceptanceTest`、`RestoreAppliedOutcomeTest` | 2026-11-04 设备上验过：v32 样本的附件按内容命名且集合一致；5GB 样本的 8080 个 upload 条目落成 682 个唯一内容 |
| 官方兼容出口 | `BackupManagerTest` 的两条路径改写用例，外加一条「官方出口归档再恢复回本应用」 | 绝对与相对路径改写验过；设备上两次交给官方客户端恢复：v32 样本（170 会话 / 676 节点 / 98 个 upload 文件）与 5GB 数据（1664 会话 / 11483 节点 / 682 个内容名附件，图片与头像能显示）；`usage_record.outcome` 的已知裁剪已实现（C80），其它 schema 分叉仍待实现 |
| 本应用自己导出、换包名恢复 | 临时 `.crosscheck` 包名的构建 | 170 会话 / 676 节点；2 个节点带相对引用，按新安装解析；64 个节点带官方绝对引用，经 C25 改写；98 个 upload 文件；测试图片显示 |

## 5GB 样本

`rikkahub_backup_20261031_064835.zip`（官方 v31 格式，5.0GB，8080 个 `upload/` 条目），2026-11-04 在设备上整份导入：

- 附件：8080 个条目按内容算出的名字集合与 live 的 `upload/` 完全一致，共 682 个文件、396MB。库里 657 个不同的引用名里有 645 个落在集合内；剩下 12 个旧 UUID 引用在归档里本来就没有对应文件，是恢复前就悬空的引用；37 个 live 文件没有被任何消息引用，来自归档里未被引用的内容。
- 库：v31 转成当前版本后 1664 会话、11483 节点；`managed_files` 682 行。导入后首次启动重建全文索引，库文件 572MB。
- 界面：消息里的图片与用户头像都能显示；在 1664 个会话里搜 `iOS` 能出结果。
- 导出同一份数据：数据库段约 24 秒，682 个文件约 14 秒，产出 612MB 归档（684 个条目）。
- 官方兼容出口：同一份数据导出 692MB，交给官方 RikkaHub 3.1.1 从文件恢复，1664 会话 / 11483 节点；645 个内容寻址引用全部能解析，12 个旧 UUID 引用与导入侧一致地缺失；消息里的图片与用户头像都能显示。官方恢复对附件是叠加：它原来的 98 个文件留在 `upload/` 里，所以恢复后是 780 个文件（682 个来自归档的内容名加 98 个旧文件）。

表数据这一层：v32 与 v31 各 14 张表全部通过。两层都验，读取层直接读原始备份，转换层把样本经转换器写成当前版本库再读。转换层只比对源版本也有的列，因为转换后是当前版本结构；另有一条断言确认转换结果里没有残留指向别的安装的附件路径。

整份归档这一层跑在 v32 上：附件逐个比对路径、大小与内容摘要且不多不少（`OfficialBackupRestoreAcceptanceTest`），恢复后 live 的 `upload/` 正好是归档那一份（`RestoreAppliedOutcomeTest`），settings 的助手与服务商条数、id 集合、当前助手与当前模型。

v31 的附件没有逐个在设备上比：整包推上设备再解压不划算，它覆盖的代码路径与 v32 相同。v16（官方 2.1.6）明确不支持，不纳入样本集。

每次改动 schema 或 `Settings` 结构之后重跑全部样本与设备验收。只编译通过不算数。

C40 与 C41 落地后的安卓端用例与 5GB 样本已在设备上跑过，结果见上表与「5GB 样本」。上面两节的设备结果仍是改名之前的数，附件的数量口径没变，只是被导入方的落盘名换成了内容指纹。

## 2026-11-12 C69 重跑

C69 给 `DisplaySetting` 增加 `confirmStopGeneration` 后，除 search 人工闸门和 crosscheck 索引日志外，按上面的标准重跑：

- `data.sync` 设备包 51 条全部通过；含 `BackupManagerTest` 的构造 Convert、同安装自家恢复、高版本拒绝。
- v32 与 5GB 的官方兼容出口各交给官方 RikkaHub 3.1.1 恢复一次，库计数与归档一致，图片正常；两份归档的 settings 都带新字段。
- 换包名 `.crosscheck` 恢复通过，绝对引用改写到新包名，图片正常。
- 附件计数：原始官方 780 = 682 个 5GB 内容名 + 98 个旧 UUID；v32 的 61 个与原集合重叠 25、新增 36，目录变 816；5GB 的 682 个全部已在原集合内、新增 0，目录仍 816。验收后官方从备份还原为 780，fork 为 168/61。
- 未记录：search 人工闸门（acceptance 没有固定查询集；官方恢复不重建 FTS，5GB 官方数据搜 iOS 为空）与 crosscheck 的 `Rebuilt search indexes after restore` 日志。

## 2026-11-12 C75 重跑

C75 给 `NetworkSetting` 增加 `maxRetries`（默认 3）。这次只新增带默认值的自定义设置字段，不改 Room/schema、数据库表、附件和官方兼容出口结构；官方兼容出口写整份 settings，因此 `settings.json` 会多一个官方不认识的 `networkSetting.maxRetries` 键，容忍方式与 C69 的 `confirmStopGeneration` 相同。按上面的标准重跑设备数据验收：

- `data.sync` 设备包 51 条全部通过（`BackupManagerTest` 26、`DatabaseBackupTest` 3、`OfficialBackupRestoreAcceptanceTest` 2、`RestoreAppliedOutcomeTest` 1、`OfficialDatabaseConverterTest` 2、`OfficialDatabaseReaderTest` 7、`OfficialDatabaseWriterTest` 4、`OfficialForeignAttachmentPathsTest` 4、`OfficialSampleAcceptanceTest` 2）。
- v32/v31 官方样本读取与转换、同安装自家恢复、官方备份恢复、applied outcome 都通过；旧 settings JSON 没有 `maxRetries` 时按 3 解码，`SettingsRetryPolicyTest` 覆盖。
- 官方 RikkaHub 3.1.1 手动恢复、5GB 和 `.crosscheck` 换包名恢复跳过，由请求方确认：官方 App 3.1.1 的设置入口在当前设备无法通过自动化进入（抽屉底部 Menu 只打开 Image Generation，右侧按钮是更新日志，Assistant 页只有 Back/Add，临时放大显示也未出现 Settings），而 C69 已用官方 3.1.1 恢复过同类自定义 Settings 字段，51 条已覆盖自家导出/恢复和官方样本；本次没有新增路径/附件/Room 变化，`.crosscheck` 的 C25 路径改写不受影响。设置页滑块也没有单独设备点验。

## 2026-11-13 C80 重跑

C80 给 `usage_record` 增加 `outcome`，Room 32 到 33，并在官方兼容导出时去掉这一列。这是 Room schema 变更，按上面的标准重跑设备数据验收：

- `data.sync` 设备包 53 条全部通过（`BackupManagerTest` 26、`DatabaseBackupTest` 3、`OfficialBackupRestoreAcceptanceTest` 2、`RestoreAppliedOutcomeTest` 1、`OfficialDatabaseConverterTest` 2、`OfficialDatabaseReaderTest` 7、`OfficialDatabaseWriterTest` 4、`OfficialForeignAttachmentPathsTest` 4、`OfficialSampleAcceptanceTest` 2、`OfficialSchemaTrimmerTest` 2）。官方兼容出口的 v33 快照现在由 `OfficialSchemaTrimmer` 去掉 `usage_record.outcome`、写回官方 v32 identity，再由读取层按 v32 快照读回；带 usage 行的 trim 也验证了行数与 token 保留。v33 同版本但 identity 未知且没有官方快照的库会被拒绝，不再走“同版本转换”。
- 数据库迁移/DAO/live 4 条通过：`Migration_32_33_Test` 验证旧行默认 UNKNOWN、新行默认 UNKNOWN；`UsageRecordDAOTest` 验证总成本含 FAILED/CANCELLED、缓存字段排除两者；`LiveDatabaseMigrationTest` 在设备上把真实 app 库迁到 33，旧 usage 行仍可读。
- 真实 app 库迁移后仍是 168 会话、61 个 upload 文件、476 条 usage，全部旧行 outcome 为 UNKNOWN；主库头在未 checkpoint 时仍是 32，带 WAL 读出的逻辑库是 33，Room 打开时按 WAL 处理。32 到 33 没有降级迁移；回退到 C80 前的构建前要保留迁移前的 v32 数据库副本，或继续使用新构建。
- 官方 RikkaHub 3.1.1 手动恢复和 5GB 没有重跑；C75 时已确认官方 App 3.1.1 的设置入口在当前设备无法通过自动化进入，且本次新增列只在自定义 `usage_record` 表，官方 v32 快照和官方兼容出口的裁剪由 53 条设备用例覆盖。若要把官方 App 手工恢复也补上，需要另找时间在能进入官方设置界面的设备上执行。
- 全量 `:app:testGithubDebugUnitTest` 791 条，7 条既有 Windows 失败未变；`:ai:testDebugUnitTest` 324 条未变；assemble、spotless、markdownlint 通过。
