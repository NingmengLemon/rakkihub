# 上游查看记录

本文件记录上游镜像的查看点与每轮调研。同步流程见 [README.md](../README.md)，模块筛选见 [roadmap.md](../roadmap.md) 的「独立更新」。

## 上次查看点

上游 `upstream/master` 到 `2435dbdf`（3.3.1）。2026-11-22 查看 `358e2931..2435dbdf`，共 7 个提交。

本轮筛选结果：移植 `fbfb9926`（Google 模型列表 `displayName` 回退）、`48a68f87`（模型名两行）、`e4646a77`（Markdown 导出时间）为 C145，移植 `2d93676e`（安全模式导出与导入备份）为 C146。`2435dbdf` 只是版本号；`b7cf74dd` 的 Phone Use 无障碍修复不适用，本分支不做 Phone Use；`a2479770` 的 tag 发布工作流不适用，本分支没有 tag 驱动的 Play 上传与对应 CI 密钥，发布走 Daily Build。

### 2026-11-20 3.3.0 查看

2026-11-20 查看 `b10c1685..358e2931`，共 26 个提交。筛选结果：`2898c3de`（Gemini 独立 `thoughtSignature` part）的崩溃已由 C84 的 `GoogleThoughtSignatureQueue` 覆盖，不重复搬队列；同一提交里 unknown part 跳过与空文本保留的改动没有一并移植。`a8508e23`（DeepSeek 备份文件名含 `#`）不适用：本分支已移除第三方导入器，没有 `DeepSeekImporter`；提交还改了共享的 `RemoteFileStore` MIME 推断，本分支仍用 `URLConnection.guessContentTypeFromName`，唯一上传调用方属于 C1 已移除的媒体生成。`a75f3f9c`（测试连接弹窗键盘关闭）已移植为 C137；`8e2afbd3`（后台任务详情长命令滚动）对应组件在本分支不存在，跳过。其余多为新功能或本分支已移除的能力（语音、更新检查、快速入门、消息气泡拆分等），本批不移植。

### 2026-11-13 3.2.0 查看

2026-11-13 查看 `5ad19405..b10c1685`。值得关注的上游变化：Phone Use 无障碍操作、按上下文比例/轮次中途自动压缩、Claude thinking 块签名修复、Mistral 多轮 422 修复、模型列表手动刷新、快捷消息短按追加、导出保存为文件、SSH 跳板机与后台 shell、信任用户 CA、推荐提供商/余额查询。自动压缩与本分支不做自动压缩的决定冲突，未移植；Phone Use 完全不做（不移植）。其余更新往后放；上游 issue 区该轮只做调研。

## 2026-11-13 issue 调研

上游 issue 区做了一轮只读调研（293 个 open）。优先候选：GoogleProvider 遇到独立 `thoughtSignature` 直接抛错（#2316）、Claude thinking 签名与 `redacted_thinking` 处理缺失（#2206/#1629，上游 3.2.0 的 `e1667163` 已移植，见 C85；上游标记 Fixes #2308）、OCR 默认 `reasoning_effort=none`（#1644，已修，见 C86）、Exa 正文没有长度上限（#1569，已修，见 C88）、历史图片每次请求重复 OCR（#1736/#1678，已修，见 C87）、workspace shell 超时后残留进程（#2225，已修前台命令，见 C89；后台命令未移植）。Phone Use 和自动压缩相关的一律不做；这批修复往后排。
