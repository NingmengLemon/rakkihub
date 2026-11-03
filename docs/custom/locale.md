# 文案与 locale

共有 8 份资源目录：`values`、`values-ar`、`values-de`、`values-ja`、`values-ko-rKR`、`values-ru`、`values-zh`、`values-zh-rTW`。`app/build.gradle.kts` 的 `localeFilters` 列了 9 项（`en`、`ar`、`de`、`ja`、`ko`、`ko-rKR`、`ru`、`zh`、`zh-rTW`），`ko` 没有对应目录，但会作为独立的 locale 配置出现在 APK 里。加语言时两处一起改。

## 增删文案

删除或新增文案时八份都要同步，用 `tools/i18n` 处理。

合并上游之后，先按 `values/strings.xml` 的 key 集合过滤其余七份，因为上游会给已删除的 key 补翻译，德语尤其明显。过滤后的判据是：除 `values-de` 与 `values-zh` 外，每份只剩「上游新增但还没翻译」的缺口。

[`checklist.md`](checklist.md) 里写「7 个 locale」或具体份数的条目，指的是做那项定制时仓库里的份数，德语是上游 3.1.1 才加的；同步这类改动时按当前的 8 份处理。

## 品牌改写

品牌改写按 key 查不出来：C12 的 `app_name`、C14 的 `setting_page_web_server_desc` 与 `oauth` 的四条 `oauth_callback_*_hint`，在新增 locale 里会以官方品牌出现。合并后要逐条重放成 `RakkiHub`。

判据不靠数行数，而是排除掉有意保留的几条 key 之后必须为空：

```powershell
rg -n 'RikkaHub' app/src/main/res oauth/src/main/res -g '**/values*/strings.xml' |
  Select-String -NotMatch 'name="(backup_page_group_official|backup_page_import_from_official|backup_page_import_official_desc|backup_page_export_official|backup_page_export_official_desc|backup_page_export_schema_diverged)"'
```

放行的这六条都指官方应用：官方那组的标题、两条导入引导、两条导出说明，以及分叉时的失败提示。`web-ui` 的 `search-picker.tsx` 里 `rikkahub: "RikkaHub"` 是搜索服务的显示名，指官方接口，同样保留。

## 欠账

`values-de` 已经不缺条目，但本分支新增的很多文案直接写在 Compose 里、还没进 `strings.xml`，其中附件清理与扫描相关的一组属于欠账。

反方向还有缺口：`values-ar`、`values-ja`、`values-ko-rKR`、`values-ru`、`values-zh-rTW` 各缺 3 条上游自己没翻的压缩上下文文案（`chat_page_compress_keep_recent` 及其描述、`chat_page_compress_not_enough_messages`），那属于上游范围。新加文案时八份一起补。
