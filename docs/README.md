# 文档

| 路径 | 内容 |
| --- | --- |
| [`custom/`](custom/README.md) | 本仓库在上游之上的定制：分支模型、流程、定制清单、构建、locale、备份与导入、路线 |
| [`references/`](references/chat-generation-pipeline.md) | 上游自带的参考文档，本分支未改动 |
| `icon.png`、`img/` | 应用图标与界面截图，上游的素材 |

仓库根只有一份 `README.md`，内容是本分支维护的 fork 说明与官方仓库入口（C39）。模块自己的 README（`ai/`、`terminal/`、`mediagen/`、`web-ui/`、`tools/i18n`、`tools/trace`）仍属上游；`tools/` 下另有本分支新增的 `backup-expectations.py`、`backup-ref-counts.py` 与 `gen-official-snapshot.ps1`，它们没有 README，用法见 [`custom/backup/acceptance.md`](custom/backup/acceptance.md) 与 [`custom/backup/importer.md`](custom/backup/importer.md)。
