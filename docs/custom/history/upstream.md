# 上游查看记录

本文件记录上游镜像的查看点与每轮调研。同步流程见 [README.md](../README.md)，模块筛选见 [roadmap.md](../roadmap.md) 的「独立更新」。

## 上次查看点

上游镜像停在 `5ad19405`；`ref` 尚未更新。2026-11-13 查看 `upstream/master` 到 `b10c1685`（3.2.0），该轮 50 个提交。下一次查看从 `b10c1685` 之后开始。

该轮值得关注的上游变化：Phone Use 无障碍操作、按上下文比例/轮次中途自动压缩、Claude thinking 块签名修复、Mistral 多轮 422 修复、模型列表手动刷新、快捷消息短按追加、导出保存为文件、SSH 跳板机与后台 shell、信任用户 CA、推荐提供商/余额查询。自动压缩与本分支不做自动压缩的决定冲突，未移植；Phone Use 完全不做（不移植）。其余更新往后放；上游 issue 区该轮只做调研。

## 2026-11-13 issue 调研

上游 issue 区做了一轮只读调研（293 个 open）。优先候选：GoogleProvider 遇到独立 `thoughtSignature` 直接抛错（#2316）、Claude thinking 签名与 `redacted_thinking` 处理缺失（#2206/#1629，上游 3.2.0 的 `e1667163` 已移植，见 C85；上游标记 Fixes #2308）、OCR 默认 `reasoning_effort=none`（#1644，已修，见 C86）、Exa 正文没有长度上限（#1569，已修，见 C88）、历史图片每次请求重复 OCR（#1736/#1678，已修，见 C87）、workspace shell 超时后残留进程（#2225，已修前台命令，见 C89；后台命令未移植）。Phone Use 和自动压缩相关的一律不做；这批修复往后排。
