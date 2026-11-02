# 验收

备份导入与恢复的验收方式。判据要有独立性：期望值由仓库外的脚本从原始备份算出，不能用被测的读取层自己算。

## 样本

六类，至少各一份：

1. 带附件引用的官方客户端备份。跨安装，会命中 C25 的探测而走转换器，验的是转换与附件路径重写。内容要覆盖多分支会话与重新生成、带附件与工具调用的消息、多 provider 与自定义 assistant、自定义主题与提示词。
2. 构造样本：把官方备份的 identity 改掉，让它落进 `OfficialRestorePlanner` 的 Convert 分支。Convert 要求双方版本号相同、identity 不同且该版本有快照。官方目前发布的 v32 与本仓库同版本、同 identity，所以这条要等官方在同一个版本号上改结构才会被真实样本触发；在那之前只能用构造样本验证。
3. 本 fork 旧构建导出的备份（改包名前，包名还是 `me.rerere.rikkahub`，带附件引用）。同样跨安装、同样命中 C25 走转换器；与第 1 类的区别是版本更低、settings 更旧。
4. 本应用自己导出、且与当前安装同一包名同一用户号的备份。不跨安装，走原路径：Room 打开、checkpoint，不经转换器。消息引用是相对形态（C36），跨安装也能解析；设置仍是绝对路径。版本低于当前时才会走迁移链升级，这一类目前没有样本或用例。
5. 版本高于我们的库，必须明确报错。
6. 官方兼容出口（要求 8）产出的备份，交给官方客户端恢复。判据是官方那边恢复成功、会话条数与关键字段对得上、附件与头像能打开。这项在本机设备上做过一次：导出的库与官方恢复后的库都是 170 个会话、676 个节点，66 条消息附件引用全是官方绝对路径、相对形态 0 条，官方 upload 目录 98 个文件，测试图片能在会话里显示。路径改写（C30/C36）验过；schema 裁剪还没实现，之后改了 schema 或路径形态要重跑。
7. 本应用自己导出、换到另一个包名的安装上恢复（本次用临时 `.crosscheck` 包名的构建）。判据是会话与节点数与导出库一致、消息里新写或重存过的相对引用按新安装解析、旧的官方绝对引用经 C25 改写成新安装、附件能显示。本次结果：170 个会话、676 个节点保持不变；2 条相对引用按新安装显示；64 条官方绝对引用改写成新安装；upload 98 个文件；测试图片正常显示。

样本含私人数据，放仓库外、放设备上；测试用 `Assume` 跳过，缺哪份就跳过哪份，CI 不会因此失败。本文件只记判据与版本类别，不记样本的体积、条数与内容。

## 判据

- 每张表按主键排序后的行数与内容 SHA-256，规范化规则见下。
- 附件的相对路径集合与每个文件的 SHA-256，外加「首次启动后 live 目录里没有归档之外的多余文件」。后一条 `RestoreAppliedOutcomeTest` 目前只断言 `upload/`。
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
| 构造样本 | `BackupManagerTest` 里改 identity 的用例 | 覆盖接线，无独立样本 |
| 本 fork 旧构建 | v31（产出自 3.1.0 (193)，包名还是 `me.rerere.rikkahub`） | 表数据与内容摘要；settings 只手工核对过条数（经 migrate 与 decode 后不变），没有脚本或测试复现；附件未逐个比 |
| 本应用自己导出 | `BackupManagerTest` 的常规恢复用例 | 覆盖原路径（不经转换器）；新写或重存过的消息引用是相对形态，按当前安装解析；没重存过的老会话仍是绝对路径；迁移链升级无样本或用例 |
| 高版本库 | `BackupManagerTest` 的拒绝用例 | 验过 |
| 官方兼容出口 | `BackupManagerTest` 的两条路径改写用例，外加一条「官方出口归档再恢复回本应用」 | 绝对与相对路径改写验过；设备上导出并交给官方客户端恢复过一次（170 会话 / 676 节点 / 66 条官方绝对引用 / 98 个 upload 文件，测试图片能显示）；schema 裁剪未实现 |
| 本应用自己导出、换包名恢复 | 临时 `.crosscheck` 包名的构建 | 170 会话 / 676 节点；2 条相对引用按新安装解析，64 条官方绝对引用经 C25 改写；98 个 upload 文件；测试图片显示 |

表数据这一层：v32 与 v31 各 14 张表全部通过。两层都验，读取层直接读原始备份，转换层把样本经转换器写成当前版本库再读。转换层只比对源版本也有的列，因为转换后是当前版本结构；另有一条断言确认转换结果里没有残留指向别的安装的附件路径。

整份归档这一层跑在 v32 上：附件逐个比对路径、大小与内容摘要且不多不少（`OfficialBackupRestoreAcceptanceTest`），恢复后 live 的 `upload/` 正好是归档那一份（`RestoreAppliedOutcomeTest`），settings 的助手与服务商条数、id 集合、当前助手与当前模型。

v31 的附件没有逐个在设备上比：整包推上设备再解压不划算，它覆盖的代码路径与 v32 相同。v16（官方 2.1.6）明确不支持，不纳入样本集。

每次改动 schema 或 `Settings` 结构之后重跑全部样本。只编译通过不算数。
