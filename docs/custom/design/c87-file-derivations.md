# C87 文件级 OCR 派生数据

## 状态

已落地 Phase 1，范围限定为 `file_derivations` 的持久化、读取优先级和官方导出裁剪；Phase 2 已补 producer 指纹失效、OCR 文本编辑入口和备份恢复合并（C110）。

## 问题

OCR 结果原先写在图片 part 的 `ocr_text` metadata，只对当前消息和历史分支有效。同一份上传内容在另一个会话复用时，metadata 不在那条消息上，只能依赖 3 天、64 条的缓存；缓存过期后重新识别。写回还要 flush 整份会话。

Phase 1 把 OCR 文本挂到 `managed_files` 行上，按文件只存一次。消息 metadata 保留为旧数据回退，不再作为新结果的唯一来源。

## 数据模型

Room 34 新增表：

```sql
CREATE TABLE file_derivations (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  file_id INTEGER NOT NULL,
  kind TEXT NOT NULL,
  text TEXT NOT NULL,
  producer TEXT NOT NULL,
  edited INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY(file_id) REFERENCES managed_files(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX index_file_derivations_file_id_kind_producer
  ON file_derivations(file_id, kind, producer);
```

`kind` 当前固定 `ocr`，`producer` 为 `ocr:v1:<prompt+model 指纹>` 或手改的 `ocr:manual`。`text` 存原始 OCR 文本，`<image_file_ocr>` 包装只在请求组装时做。`edited` 标记手改行，手改文本覆盖任何自动结果。

## 文件解析

`FileDerivationResolver` 把图片地址解析到 `managed_files`：

1. `fileUrlToCanonicalPath()` 处理 `file:upload/...` 相对形态和绝对 `file://`；
2. `FileUtils.getRelativePathInFilesDir()` 确认文件在本安装 `filesDir` 下；
3. `FilesRepository.getByPaths()` 批量取 `managed_files` 行。

解析不到时返回 null，OCR 回退到 metadata 或缓存，不新建文件行。

## 读取与写入

`OcrTransformer` 的顺序：

1. 有 `file_id` 且 derivation 命中时直接使用；
2. derivation 缺失但旧消息 metadata 命中时使用，并懒写一条 derivation；指纹 producer 下，有文件行的图片跳过这一步，让 OCR 重新识别，避免旧文本被重标为新 producer；
3. 缓存命中时使用，有 `file_id` 就写 derivation；缓存 key 带 producer；
4. 调 OCR 模型，成功后写 derivation；只有没有 `managed_files` 行的旧附件才继续写回消息 metadata。

手改行（`ocr:manual`, `edited=1`）在 `listForFiles` 里覆盖当前 producer 的自动结果；手改存在时 `putOcr` 不写入，置空只删除手改行。

新结果不再触发 `ChatService.attachOcrTexts` 的整会话 flush；该路径只保留给无法解析文件行的附件。

## 删除、清理与备份

- `managed_files` 删除、按路径删除、按目录删除时，由 `file_derivations.file_id` 的 `ON DELETE CASCADE` 清理（Room 打开外键）；`FilesRepository` 不再手工级联。
- 自家备份恢复会合并同内容的上传文件并删掉落选行；`UploadNames` 在删除前把落选行的 `edited=1` 手改文本挂到保留行（`INSERT OR IGNORE`，已有同名手改时保留保留行），`BackupManager.stageDatabase` 只清 `edited=0` 的自动行。同版本外来路径转换由 `OfficialDatabaseConverter` 按原 `file_id` 补回手改行，再做合并。自动行恢复后按当前 producer 重新识别，手改行继续覆盖；避免错配或孤儿。官方备份本来没有这张表，迁移后为空。

## 官方兼容

- `OfficialDatabaseSchema.ROOM_VERSION` 升到 34，identity hash 来自 `schemas/34.json`。
- `KNOWN_EXTRA_TABLES` 登记 `file_derivations`。
- `OfficialSchemaTrimmer.canReadAsV32` 拒绝未登记额外表；裁剪时先 drop 已登记额外表，再裁 `usage_record.outcome`。
- `OfficialDatabaseSchemaTest` 允许官方表集合加已登记额外表，并继续检查官方表的额外列。
- 官方 v32 快照、官方读取层、官方写入层都不需要认识这张表；转换出的当前库会得到一张空表。

## 测试

- `FileDerivationRepositoryTest`：put/get、相同文本跳过、更新、edited 不覆盖、空文本忽略、按文件列表、手改覆盖自动结果、手改后自动写入被拒、清空手改后自动结果恢复、producer 指纹稳定/隔离、`resolveAll` 的相对/绝对/目录外/未登记 URL 过滤与批量映射。
- `OcrTextChoiceTest`：derivation 优先、metadata 回填、无文件不回填、空值、指纹 producer 下忽略 metadata，以及缓存 key 带 producer。
- `OfficialDatabaseSchemaTest`：schema 34、identity、官方表集合加 `file_derivations`、已知额外列。
- `Migration_33_34_Test`（开发机）：表创建、字段读取、外键级联删除。
- `BackupManagerTest`（开发机）：恢复清空自动行、保留 `edited=1` 手改行；两份同内容上传合并后落选手改文本挂到保留行；同版本外来路径转换后手改行仍在。
- `OfficialSchemaTrimmerTest`（开发机）：裁剪后 `file_derivations` 消失、usage 行保留、官方读取层可打开；未登记额外表会抛 `OfficialExportUnsupportedException`。
- `LiveDatabaseMigrationTest`（开发机）：真实 app 库迁到 34。
- 设备链路（2026-11-16）：上传一张图片，OCR 调用后数据库生成一条 `file_derivations`；同一会话第二轮和 force-stop 重启后的第三轮都只发聊天请求，没有重复 OCR；derivation 行数始终为 1。
- 设备编辑（2026-11-17）：附件管理打开图片的 OCR 面板，保存后出现成功提示，重新打开仍显示手改文本。
- 全量 JVM 测试和 `:app:compileGithubDebugAndroidTestKotlin` 通过。

## 已知限制

- 没有自动派生结果的清空入口；手改文本置空只删除 `ocr:manual` 行，让自动结果恢复。
- producer 现在随 OCR 提示词和模型 id 变化，旧 producer 行保留但不再读取；有文件行的图片换设置后首次发送会重新识别一次。没有文件行的旧附件仍只能使用 metadata。
- 备份恢复保留手改行、清掉自动行；同内容文件合并时落选手改行会挂到保留行，保留行已有手改时以保留行为准。
- 只处理能映射到 `managed_files` 的本地图片；本安装 `filesDir` 下的绝对和相对地址都会解析，别的安装路径、目录外文件和未登记文件走旧回退。
