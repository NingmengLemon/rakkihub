# 文档

| 路径 | 内容 |
| --- | --- |
| [`custom/`](custom/README.md) | 本仓库在上游之上的定制：分支模型、流程、定制清单、构建、locale、备份与导入、路线 |
| [`references/`](references/chat-generation-pipeline.md) | 上游自带的参考文档，本分支只改格式（代码块语言、列表缩进），内容未改 |
| `icon.png` | 通用应用图标，由 `tools/gen-icons.py` 生成 |
| `readme-icon.png` | README 用的安全区裁切图标，由 `tools/gen-icons.py` 生成 |

仓库根只有一份 `README.md`，内容是本分支维护的 fork 说明与官方仓库入口（C39）。模块自己的 README（`core/ai/`、`platform/terminal/`、`integrations/mediagen/`、`web-ui/`、`tools/trace`）仍属上游，`tools/i18n` 的 README 由本分支维护（C93）；`tools/` 下另有本分支新增的 `backup-expectations.py`、`backup-ref-counts.py` 与 `gen-official-snapshot.ps1`，它们没有 README，用法见 [`custom/backup/acceptance.md`](custom/backup/acceptance.md) 与 [`custom/backup/importer.md`](custom/backup/importer.md)。`tools/gen-icons.py` 按关于页插画重新生成 launcher 图标、`docs/icon.png`、README 用的 `docs/readme-icon.png`、Web UI 的 `icon.png`、`favicon.ico` 与 `apple-touch-icon.png`，用法见 [`custom/checklist/platform.md`](custom/checklist/platform.md) 的 C94。
