# 文案与 locale

共 4 份资源目录：`values`（英）、`values-zh`（简中）、`values-zh-rTW`（繁中）、`values-ja`（日）。`app/build.gradle.kts` 的 `localeFilters` 与之一致（`en`、`ja`、`zh`、`zh-rTW`），加语言时两处一起改；`tools/i18n/config.yml` 的 `languages` 也要同步。这个范围是 C45 砍定的，之前条目里写的份数按当时的仓库为准。

没有目录的语言回退到英文（德、俄、韩、阿等）；繁中设备命中 `values-zh-rTW`。

## 增删文案

删除或新增文案时四份同步。`tools/i18n` 是 Python 实现，用 `uv run --project tools/i18n i18n <command>` 运行，命令见 `tools/i18n/README.md`；API key 写在 `tools/i18n/.env`（不提交），没有 key 时只能手翻。四份都不缺条目。

`app` 之外的 `search`、`oauth`、`ui` 三个模块也各有自己的 `strings.xml` 与这四份 locale，新增或删除文案时一起改。

合并上游之后，先按 `values/strings.xml` 的 key 集合过滤其余三份，因为上游会给已删除的 key 补翻译。过滤后的判据是：其余三份只剩「上游新增但还没翻译」的缺口。

## 品牌改写

品牌改写按 key 查不出来：C12 的 `app_name`、C14 的 `setting_page_web_server_desc` 与 `oauth` 的四条 `oauth_callback_*_hint`，在新补的文案里会以官方品牌出现，合并后要逐条重放成 `RakkiHub`。

判据不靠数行数，而是排除掉有意保留的几条 key 之后必须为空：

```powershell
rg -n 'RikkaHub' app/src/main/res oauth/src/main/res -g '**/values*/strings.xml' |
  Select-String -NotMatch 'name="(backup_page_group_official|backup_page_import_from_official|backup_page_import_official_desc|backup_page_export_official|backup_page_export_official_desc|backup_page_export_schema_diverged)"'
```

放行的这六条都指官方应用：官方那组的标题、两条导入引导、两条导出说明，以及分叉时的失败提示。`web-ui` 的 `search-picker.tsx` 里 `rikkahub: "RikkaHub"` 是搜索服务的显示名，指官方接口，同样保留。

## 欠账

- 本分支新增的很多文案直接写在 Compose 里、还没进 `strings.xml`，其中附件清理与扫描相关的一组属于欠账；这部分与语言无关。
- 上游自己没翻的三条压缩上下文文案（`chat_page_compress_keep_recent`、它的描述、`chat_page_compress_not_enough_messages`）已补进 `values-ja` 与 `values-zh-rTW`，四份都不缺条目。
