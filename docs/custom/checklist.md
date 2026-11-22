# 定制清单

本分支相对上游的全部定制，一项一个编号。编号按顺序分配；已作废的编号不复用，已作为阶段号登记过的编号段也不复用。

新增定制时先往索引表加一行，再在对应领域文件里写小节。小节回答「改在哪」「为什么改」「移植上游改动时要注意什么」，有测试的补一条测试。移植上游改动时按明细逐项复核，确保定制意图没有被静默丢掉。

一项只占一个顶层编号。项内分阶段推进时用 `C111.1` 这样的子号，不再另起顶层编号。历史登记用过的编号段整体保留，不再用作顶层编号，新项从保留段之后继续。

## 索引

| 编号 | 内容 | 性质 |
| --- | --- | --- |
| C1 | [移除媒体创作与生图](checklist/removals.md#c1) | 移除 |
| C2 | [移除 Firebase Analytics](checklist/removals.md#c2) | 移除 |
| C3 | [移除赞助商推广，并允许删除所有提供商](checklist/removals.md#c3) | 移除 |
| C4 | [清理赞助商图标、README 推广与启动赞助弹窗](checklist/removals.md#c4) | 移除 |
| C5 | [移除 TTS 与 STT（语音合成与语音识别）](checklist/removals.md#c5) | 移除 |
| C6 | [移除聊天回复建议](checklist/removals.md#c6) | 移除 |
| C7 | [应用图标旋转 180 度](checklist/platform.md#c7) | 改造 |
| C8 | [移除 Chatbox 与 Cherry Studio 导入](checklist/removals.md#c8) | 移除 |
| C9 | [移除 S3 备份](checklist/removals.md#c9) | 移除 |
| C10 | [新增从官方 RikkaHub 导入](checklist/data.md#c10) | 新增 |
| C11 | [导出备份显示进度](checklist/data.md#c11) | 新增 |
| C12 | [改包名与应用名](checklist/platform.md#c12) | 改造 |
| C13 | [移除 Firebase 与 google-services](checklist/removals.md#c13) | 移除 |
| C14 | [移除设置页的分享本应用入口，统一品牌文案](checklist/removals.md#c14) | 移除 |
| C15 | [移除更新检查](checklist/removals.md#c15) | 移除 |
| C16 | [清除导出内容里的站点水印](checklist/removals.md#c16) | 移除 |
| C17 | [冻结官方备份的结构快照](checklist/data.md#c17) | 新增 |
| C18 | [新增官方兼容的导出出口](checklist/data.md#c18) | 新增 |
| C19 | [附件按替换语义导入，导入的原始备份留底](checklist/data.md#c19) | 改造 |
| C20 | [导入器读取层](checklist/data.md#c20) | 新增 |
| C21 | [导入器写入层](checklist/data.md#c21) | 新增 |
| C22 | [导入器支持多版本官方快照](checklist/data.md#c22) | 新增 |
| C23 | [导入器接上恢复流程](checklist/data.md#c23) | 新增 |
| C24 | [导入器验收：与独立算出的期望值逐表比对](checklist/data.md#c24) | 新增 |
| C25 | [同版本备份也重写附件路径](checklist/data.md#c25) | 改造 |
| C26 | [整份备份的附件验收](checklist/data.md#c26) | 新增 |
| C27 | [全文索引自检与重建完整性标记](checklist/data.md#c27) | 改造 |
| C28 | [settings 形状校验](checklist/data.md#c28) | 新增 |
| C29 | [备份可选包含工作区文件](checklist/data.md#c29) | 新增 |
| C30 | [官方兼容导出改写附件路径](checklist/data.md#c30) | 改造 |
| C31 | [聊天草稿落盘](checklist/data.md#c31) | 新增 |
| C32 | [附件按内容去重，删除改为摘引用](checklist/data.md#c32) | 新增 |
| C33 | [附件拷贝挪出主线程](checklist/data.md#c33) | 改造 |
| C34 | [备份与工作区文件的拷贝挪出主线程](checklist/data.md#c34) | 改造 |
| C35 | [模型选择支持多关键词搜索](checklist/chat.md#c35) | 改造 |
| C36 | [消息附件引用按相对路径存储](checklist/data.md#c36) | 改造 |
| C37 | [CI 跑格式检查与 JVM 测试](checklist/platform.md#c37) | 改造 |
| C38 | [设置页提示授予本地网络权限](checklist/platform.md#c38) | 新增 |
| C39 | [把 README 收成一份 fork 说明](checklist/platform.md#c39) | 改造 |
| C40 | [导入时附件按内容寻址，归档必须有数据库与设置](checklist/data.md#c40) | 改造 |
| C41 | [备份与恢复按阶段报进度，六个入口互斥](checklist/data.md#c41) | 改造 |
| C42 | [备份页按本应用与官方 RikkaHub 分组](checklist/data.md#c42) | 改造 |
| C43 | [错误卡片默认保留到手动关闭](checklist/chat.md#c43) | 改造 |
| C44 | [上下文压缩的提示词可编辑](checklist/chat.md#c44) | 新增 |
| C45 | [文案范围收成四份](checklist/platform.md#c45) | 改造 |
| C46 | [管理上下文：压缩与截断共用一个入口](checklist/chat.md#c46) | 改造 |
| C47 | [上下文消息数上限的自动边界线](checklist/chat.md#c47) | 改造 |
| C48 | [版本号随提交自动滚动](checklist/platform.md#c48) | 改造 |
| C49 | [分支切换只写 select_index](checklist/chat.md#c49) | 改造 |
| C50 | [生成中定期保存回复节点](checklist/chat.md#c50) | 改造 |
| C51 | [压缩走可见生成轮次](checklist/chat.md#c51) | 改造 |
| C52 | [Claude 缓存断点按官方标准收敛](checklist/chat.md#c52) | 改造 |
| C53 | [Claude 缓存断点按提供商切换](checklist/chat.md#c53) | 改造 |
| C54 | [移除关于页图标点击彩蛋并清理相关死代码](checklist/removals.md#c54) | 移除 |
| C55 | [关于页独立图标](checklist/platform.md#c55) | 改造 |
| C56 | [文档 lint 配置](checklist/platform.md#c56) | 改造 |
| C57 | [请求日志开关持久化](checklist/chat.md#c57) | 改造 |
| C58 | [失败与中断回复状态](checklist/chat.md#c58) | 改造 |
| C59 | [截断流识别与停止结算](checklist/chat.md#c59) | 改造 |
| C60 | [审批期间冻结 turn 上下文](checklist/chat.md#c60) | 改造 |
| C61 | [结构化失败与重试策略](checklist/chat.md#c61) | 改造 |
| C62 | [流式 idle watchdog（心跳与开关）](checklist/chat.md#c62) | 改造 |
| C63 | [上下文超限自动缩窗](checklist/chat.md#c63) | 撤销 |
| C64 | [移除设置页关于区域的社区与文档入口](checklist/removals.md#c64) | 移除 |
| C65 | [工具中断恢复结果与超时设置](checklist/chat.md#c65) | 改造 |
| C66 | [消息队列恢复与回插](checklist/chat.md#c66) | 改造 |
| C67 | [进程死亡回复恢复](checklist/chat.md#c67) | 改造 |
| C68 | [压缩孤儿对加载清理与过滤](checklist/chat.md#c68) | 改造 |
| C69 | [停止生成前确认](checklist/chat.md#c69) | 新增 |
| C70 | [消息队列持久化](checklist/chat.md#c70) | 新增 |
| C71 | [错误卡片持久化](checklist/chat.md#c71) | 新增 |
| C72 | [上下文压缩按 token 预算](checklist/chat.md#c72) | 新增 |
| C73 | [发送前上下文裁剪](checklist/chat.md#c73) | 撤销 |
| C74 | [上下文水位与压缩建议](checklist/chat.md#c74) | 新增 |
| C75 | [自动重试次数可调](checklist/chat.md#c75) | 改造（默认关闭） |
| C76 | [重试 attempt 可见化](checklist/chat.md#c76) | 改造 |
| C77 | [错误卡片重试本步](checklist/chat.md#c77) | 永久取消 |
| C78 | [工具失败日志分级](checklist/chat.md#c78) | 改造 |
| C79 | [本地导出默认包含工作区文件](checklist/data.md#c79) | 改造 |
| C80 | [用量成功失败区分与官方 schema 裁剪](checklist/data.md#c80) | 新增 |
| C81 | [压缩面板：高级设置、临时模型与思考继承](checklist/chat.md#c81) | 改造 |
| C82 | [消息 HTML 清洗与 Mermaid 版本统一](checklist/chat.md#c82) | 改造 |
| C83 | [超长 diff 行崩溃修复](checklist/chat.md#c83) | 改造 |
| C84 | [Gemini 独立 thoughtSignature 兼容](checklist/chat.md#c84) | 改造 |
| C85 | [Claude 思考块签名与 redacted_thinking 回放](checklist/chat.md#c85) | 改造 |
| C86 | [思考强度 NOTSET 与 OFF 分离](checklist/chat.md#c86) | 改造 |
| C87 | [OCR 结果按 managed file 持久化，历史图片不再重复识别](checklist/data.md#c87) | 改造 |
| C88 | [Exa 搜索正文长度上限](checklist/data.md#c88) | 改造 |
| C89 | [workspace 前台命令超时后的孤儿进程清理](checklist/workspace.md#c89) | 改造 |
| C90 | [应用图标与可见文案改名 RakkiHub](checklist/platform.md#c90) | 改造 |
| C91 | [关于页链接改指本仓库](checklist/platform.md#c91) | 改造 |
| C92 | [launcher 插画重新对位](checklist/platform.md#c92) | 改造 |
| C93 | [tools/i18n 重写成 Python](checklist/platform.md#c93) | 改造 |
| C94 | [图标收尾：favicon、Web 空状态 logo、清理旧素材](checklist/platform.md#c94) | 改造 |
| C95 | [清理 RikkaHub 提供商残留](checklist/removals.md#c95) | 移除 |
| C96 | [移除发给 OpenRouter 的来源标识头](checklist/removals.md#c96) | 移除 |
| C97 | [Claude step 级请求投影与失败工具事实](checklist/chat.md#c97) | 改造 |
| C98 | [消息操作手动重试失败 step](checklist/chat.md#c98) | 改造 |
| C99 | [失败请求记录完整 provider 响应体](checklist/chat.md#c99) | 改造 |
| C100 | [U+FFFD 替换字符与官方 Claude 行为对齐](checklist/chat.md#c100) | 待调研（低优先） |
| C101 | [README 图标按 launcher 安全区裁切](checklist/platform.md#c101) | 改造 |
| C102 | [工作区编辑器返回时恢复滚动并收起 IME](checklist/workspace.md#c102) | 改造 |
| C103 | [工作区写/编辑文件显示运行状态](checklist/workspace.md#c103) | 改造 |
| C104 | [工作区终端支持 IME 文本输入](checklist/workspace.md#c104) | 新增 |
| C105 | [工作区 Markdown 预览与源码切换](checklist/workspace.md#c105) | 新增 |
| C106 | [工作区编辑器未保存退出保护](checklist/workspace.md#c106) | 新增 |
| C107 | [B2 step 稳定标识与失败码](checklist/chat.md#c107) | 改造 |
| C108 | [C87 OCR producer 指纹失效](checklist/data.md#c108) | 改造 |
| C109 | [C87 OCR 文本编辑入口](checklist/data.md#c109) | 新增 |
| C110 | [C87 备份恢复保留手改 OCR](checklist/data.md#c110) | 改造 |
| C111 | [stdio MCP 服务器（专用工作区 rootfs 子进程）](checklist/workspace.md#c111) | 新增 |
| C120 | [MCP 工具失败重试元数据](checklist/workspace.md#c120) | 新增 |
| C121 | [聊天正文的 SVG 内联预览](checklist/chat.md#c121) | 新增 |
| C122 | [B2 标题与摘要的 step 级投影](checklist/chat.md#c122) | 改造 |
| C123 | [MCP 手动停止态跨进程](checklist/workspace.md#c123) | 改造 |
| C124 | [工作区 Markdown 编辑器的未保存标记与输入法恢复](checklist/workspace.md#c124) | 改造 |
| C125 | [终端传统输入与 IME 切换](checklist/workspace.md#c125) | 改造 |
| C126 | [stdio MCP stderr 日志](checklist/workspace.md#c126) | 新增 |
| C127 | [Gradle 模块目录分组](checklist/platform.md#c127) | 改造 |
| C128 | [信任用户安装的 CA 证书](checklist/platform.md#c128) | 新增 |
| C129 | [每日构建上传 Actions Artifacts](checklist/platform.md#c129) | 新增 |
| C130 | [相邻推理块重复显示为生成中](checklist/chat.md#c130) | 改造 |
| C131 | [定期保存失败的用户提示](checklist/chat.md#c131) | 新增 |
| C132 | [workspace SVG 预览的相对资源引用](checklist/workspace.md#c132) | 改造 |
| C133 | [按协议清除历史思考签名](checklist/chat.md#c133) | 新增 |
| C134 | [mark.html 离线渲染](checklist/chat.md#c134) | 改造 |
| C135 | [Web 正文 Mermaid](checklist/chat.md#c135) | 新增 |
| C136 | [Python 脚本 PEP 723 元数据](checklist/platform.md#c136) | 改造 |
| C137 | [测试连接弹窗不再被键盘关闭](checklist/chat.md#c137) | 上游移植 |
| C138 | [提供商图标补充](checklist/platform.md#c138) | 上游移植 |
| C139 | [请求体日志只在 debug 构建打印](checklist/platform.md#c139) | 上游移植 |
| C140 | [内置助手可删除](checklist/chat.md#c140) | 上游移植 |
| C141 | [消息概览标出压缩检查点](checklist/chat.md#c141) | 上游移植 |
| C142 | [message_node 复合索引](checklist/data.md#c142) | 上游移植 |
| C143 | [provider mock server](checklist/platform.md#c143) | 新增 |
| C144 | [安全模式清除超大设置](checklist/platform.md#c144) | 上游移植 |
| C145 | [上游 3.3.1 的模型名与导出修正](checklist/chat.md#c145) | 上游移植 |
| C146 | [安全模式导出与导入备份](checklist/platform.md#c146) | 上游移植 |
| C147 | [Markdown 导出可省略工具调用详情](checklist/chat.md#c147) | 新增 |
| C148 | [终端功能键双排布局](checklist/workspace.md#c148) | 改造 |
| C149 | [设置与备份文案打磨](checklist/platform.md#c149) | 改造 |
| C150 | [MCP 与 provider 文案打磨](checklist/platform.md#c150) | 改造 |
| C151 | [Web 文案打磨与主题/搜索审计](checklist/platform.md#c151) | 改造 |
| C152 | [聊天页文案打磨](checklist/chat.md#c152) | 改造 |
| C153 | [硬编码文案清理：Web Mermaid、聊天多选与 MCP OAuth](checklist/platform.md#c153) | 改造 |
| C154 | [conversation_search 按当前助手过滤](checklist/chat.md#c154) | 改造 |

C112 至 C119 是 C111 历史阶段的保留段，不再用作顶层编号；下一个新项从 C155 起。

## 明细

明细按领域分文件，文件内按编号顺序排列；每个顶层编号小节前有 `<a id="cN"></a>` 锚点，索引直接跳到对应小节。

| 文件 | 领域 | 覆盖 |
| --- | --- | --- |
| [removals.md](checklist/removals.md) | 移除与清理 | C1 至 C6、C8 至 C9、C13 至 C16、C54、C64、C95 至 C96 |
| [data.md](checklist/data.md) | 数据、备份与附件 | C10 至 C11、C17 至 C34、C36、C40 至 C42、C79 至 C80、C87 至 C88、C108 至 C110、C142 |
| [chat.md](checklist/chat.md) | 聊天、上下文与模型 | C35、C43 至 C44、C46 至 C47、C49 至 C53、C57 至 C63、C65 至 C78、C81 至 C86、C97 至 C100、C107、C121、C122、C130、C131、C133、C134、C135、C137、C140、C141、C145、C147、C152、C154 |
| [platform.md](checklist/platform.md) | 平台、构建与品牌 | C7、C12、C37 至 C39、C45、C48、C55 至 C56、C90 至 C94、C101、C127 至 C129、C136、C138、C139、C143、C144、C146、C149、C150、C151、C153 |
| [workspace.md](checklist/workspace.md) | 工作区与 MCP | C89、C102 至 C106、C111、C120、C123 至 C126、C132、C148 |
