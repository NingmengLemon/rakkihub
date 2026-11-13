# i18n

管理 Android `strings.xml` 翻译的 CLI，Python 实现，可调用任何兼容 OpenAI 的接口。

## 准备

需要 `uv`（本机已装）。首次运行会自动创建 `tools/i18n/.venv`：

```bash
uv run --project tools/i18n i18n --help
```

在 `tools/i18n/.env` 中配置 API（不会提交到仓库）：

```
OPENAI_API_KEY=your_api_key
OPENAI_BASE_URL=https://api.openai.com/v1
```

模块、语言、术语表、模型和 prompt 在 `config.yml` 中配置。

## 命令

在仓库根目录运行：

```bash
uv run --project tools/i18n i18n --help                          # 查看全部命令，<command> --help 查看命令选项
uv run --project tools/i18n i18n add hello_world "Hello, World!" # 添加英文条目并翻译到所有语言
uv run --project tools/i18n i18n add greeting "Welcome" -m ui --skip-translate
uv run --project tools/i18n i18n set hello_world "你好，世界！" -l values-zh
uv run --project tools/i18n i18n remove hello_world greeting     # 从所有语言中删除
uv run --project tools/i18n i18n list --missing                  # 列出缺少译文的条目
uv run --project tools/i18n i18n list --match 'memory'           # 按 key 或英文原文搜索
uv run --project tools/i18n i18n translate-missing --dry-run     # 统计缺失条目
uv run --project tools/i18n i18n translate-missing -l values-zh,values-ja
uv run --project tools/i18n i18n retranslate -l values-ja --match '\bmemory\b'
uv run --project tools/i18n i18n retranslate -l values-ja --key some_key,other_key
uv run --project tools/i18n i18n unused                          # 检测未引用的条目和多余的译文
uv run --project tools/i18n i18n unused --delete
uv run --project tools/i18n i18n test-connection
```

## 实现说明

- 读写 `strings.xml` 时只改动涉及的 `<string>` 元素，注释、`<plurals>`、`<string-array>`、空白、顺序和已有换行原样保留；新条目追加到文件末尾，追加的新行用 LF。
- 值是元素内部的原始 XML（`&amp;`、`\'` 保持原样）。`add`/`set` 的输入按纯文本处理，自动转义 `&`、`<`、`>` 和引号；加 `--raw` 可直接写 XML。
- `translatable="false"` 的条目不参与翻译和缺失统计。
- 译文会做规范化（转义裸引号和 `&`），并校验格式符、换行数量（字面量 `\n` 与真实换行合并计算）和内联标签与原文一致；不通过的条目会重试，仍失败的不写入，最后统一列出，命令以非零状态退出。
- 术语表按目标语言追加到 prompt 末尾。`config.yml` 里每种语言可以配 `protected_terms`：原文里出现这些英文术语时，译文必须原样保留（大小写不敏感、允许复数，按 ASCII 字母判断边界，所以「最大Token数」也算保留）。缺失时按校验失败重试。
- `unused` 扫描 `source_patterns` 中所有文件的 `R.string.xxx` / `@string/xxx` 引用（不分模块）；动态拼接 key 的引用无法识别，删除前需确认。

## 测试

```bash
uv run --project tools/i18n --group dev pytest tools/i18n/tests -q          # 离线单元测试
I18N_LIVE=1 uv run --project tools/i18n --group dev pytest tools/i18n/tests/test_translator_live.py  # 调用真实 API
```
