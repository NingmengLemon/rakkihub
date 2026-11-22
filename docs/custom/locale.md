# 文案与 locale

共 4 份资源目录：`values`（英）、`values-zh`（简中）、`values-zh-rTW`（繁中）、`values-ja`（日）。`app/build.gradle.kts` 的 `localeFilters` 与之一致（`en`、`ja`、`zh`、`zh-rTW`），加语言时两处一起改；`tools/i18n/config.yml` 的 `languages` 也要同步。这个范围是 C45 砍定的，之前条目里写的份数按当时的仓库为准。

没有目录的语言回退到英文（德、俄、韩、阿等）；繁中设备命中 `values-zh-rTW`。

## 增删文案

删除或新增文案时四份同步。`tools/i18n` 是 Python 实现，用 `uv run --project tools/i18n i18n <command>` 运行，命令见 `tools/i18n/README.md`；API key 写在 `tools/i18n/.env`（不提交），没有 key 时只能手翻。四份都不缺条目。

`app` 之外的 `search`、`oauth`、`ui` 三个模块也各有自己的 `strings.xml` 与这四份 locale，新增或删除文案时一起改。

合并上游之后，先按 `values/strings.xml` 的 key 集合过滤其余三份，因为上游会给已删除的 key 补翻译。过滤后的判据是：其余三份只剩「上游新增但还没翻译」的缺口。

## 品牌改写

品牌改写按 key 查不出来：C12 的 `app_name`、C14 的 `setting_page_web_server_desc` 与 `oauth` 的四条 `oauth_callback_*_hint`，在新补的文案里会以官方品牌出现，合并后要逐条重放成 `RakkiHub`。

判据不靠数行数，而是排除掉有意保留的几条 key 之后必须为空。C149 之后 `backup_page_export_schema_diverged` 不再带官方品牌，放行列表只剩五条：

```powershell
rg -n 'RikkaHub' app/src/main/res integrations/oauth/src/main/res -g '**/values*/strings.xml' |
  Select-String -NotMatch 'name="(backup_page_group_official|backup_page_import_from_official|backup_page_import_official_desc|backup_page_export_official|backup_page_export_official_desc)"'
```

放行的这五条都指官方应用：官方那组的标题、两条导入引导、两条导出说明。`web-ui` 的 `search-picker.tsx` 里 `rikkahub: "RikkaHub"` 是搜索服务的显示名，指官方接口，同样保留。

## 标点与句式

- 中文（简中与繁中）句末不加「。」；问句、感叹句保留「？」「！」，句内的句号照常用。`app`、`integrations/search`、`integrations/oauth` 与 `core/ui` 的中文资源都按这条；唯一例外是 `setting_display_page_font_size_preview`，它是字体样例，保留原文标点。
- 省略号统一用「…」，不用 `...`。
- 中文之间用全角逗号、分号、冒号；URL、代码标识符与英文括注里的标点保持原样。
- 日文句末保留「。」，不要跟着中文一起裁；并列用「、」。日文的变量说明保留「変数: 」这种半角冒号加空格。

## 欠账

本文件只列未结清项；已结清的批次见 [`history/milestones.md`](history/milestones.md)。

- 仍硬编码的界面文案集中在上游文件：工作区文件管理页与它的 ViewModel 错误诊断、WebView 与日志页的 `contentDescription`、模型列表的 Clear、聊天气泡的 Prev/Next 等。按 C151 的口径属于上游文案，不在本分支文案打磨范围；改到这些页面时再逐条登记。调试页同样不在范围。
- 工具卡片这类中文标签冒号仍是半角 `:` 加空格（「读取: %s」），没有随变量说明一起统一；改到这些页面时再逐条处理。
- `McpOAuthCoordinator` 的异常信息是中文技术诊断（例如「授权服务器缺少 token_endpoint」），会显示在 MCP 错误卡片里；本地化要把 `Context` 传进协调器，尚未处理。
