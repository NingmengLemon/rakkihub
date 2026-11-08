# 构建与测试

## 前提

以下四项不在仓库内，缺任何一项都会在特定任务上失败：

- JDK 必须是 HotSpot。默认 `JAVA_HOME` 指向 OpenJ9 的 JDK 21，KSP 会抛 `NoClassDefFoundError: jdk.jfr.Event`。改用 `E:\WorkBench\Java\jdk-23.0.2`
- `local.properties` 提供 release 签名（被 gitignore）
- `material3/material-color-utilities` 是 submodule，未初始化时 `:material3:compileDebugKotlin` 报 `Unresolved reference 'dynamiccolor'`
- 文档检查用 `markdownlint-cli2`（v0.23.0 / markdownlint v0.41.0），本机全局安装，不在仓库依赖里

## 构建

```powershell
$env:JAVA_HOME='E:\WorkBench\Java\jdk-23.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleGithubDebug :app:assembleGithubRelease
```

上游 3.1.1 引入 `distribution` 维度的 product flavor（`github` / `play`），任务名与产物路径随之带上渠道名。用 `github` 渠道：`play` 只给上架 Google Play 用，本仓库不需要。

产物位于 `app/build/outputs/apk/github/debug/` 与 `app/build/outputs/apk/github/release/`，各有 universal 与分 ABI 三种。debug 的 applicationId 是 `top.lemoneko.rakkihub.debug`，release 是 `top.lemoneko.rakkihub`，两者与官方版的 `me.rerere.rikkahub` 都不同，可以同时安装。只有包名相同才需要签名一致，所以 release 自签名在这里不构成与官方版共存的障碍。

## 版本号

`versionCode` 与 `versionName` 由 `app/build.gradle.kts` 从 git 推导：`versionCode = 194 + 提交数`，`versionName = 3.1.1-custom.<提交数>+<短 SHA>`。提交数取 `git rev-list --count HEAD`，不加 dirty 后缀。git 命令执行失败（例如不在仓库里）时退回 `194` / `3.1.1-custom`；git 可执行文件本身不存在时会在配置阶段失败，配置缓存无法把进程启动失败当作可回退的输入值，这是构建环境的要求。Gradle 的配置缓存会跟踪这个外部命令的输出，换提交后会重新配置，不会拿旧值。本地浅克隆（depth-1）会把提交数算成 1，versionCode 退到 195，需要完整历史；上游改版本时同步脚本里的 `upstreamVersionCode` / `upstreamVersionName` 两个常量，以及这里的前缀说明。

## 测试

```powershell
$env:JAVA_HOME='E:\WorkBench\Java\jdk-23.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test --continue --console=plain
```

`test` 覆盖所有模块，app 有 `github` 与 `play` 两个渠道，会按 debug 与 release 组合出 4 套变体各跑一遍，速度慢一些。

判据是「没有新增失败」，不是「全部通过」。Windows 上有一批测试必然失败，属于测试本身假设类 Unix 环境，与代码改动无关。app 模块里固定失败 7 个：

| 测试类 | 失败数 | 原因 |
| --- | --- | --- |
| `data.datastore.SettingsStoreTest` | 6/12 | DataStore 在 Windows 上不能 rename 覆盖被占用的目标文件 |
| `data.ai.account.ProviderAccountManagerTest` | 1 | 同源问题，用例末尾写设置时撞上 DataStore 的 rename |

workspace 模块另有 `workspace.ExampleUnitTest`（4/10，硬编码执行 `/bin/sh`，Windows 上 `CreateProcess error=2`）与 `workspace.RootfsInstallerTest`（1/3，软链断言期望 `/bin/busybox`）失败。

CI 的 checks workflow 在 Linux 上跑 app、ai、workspace 的 JVM 测试，判据是全绿；上面这张表是本机 Windows 的判据。Linux 上那批文件占用失败预期不再出现，首次跑批确认。

## 格式检查

`spotlessApply` 与 `spotlessCheck` 用 ktlint，`ratchetFrom("origin/main")` 写死，只检查相对该引用改动过的文件，也就是本批还没推出去的改动。`origin/main` 必须存在：它指向最近一次推上去的状态，也就是本批的基点。

CI 的 checks workflow 在 push 时先把 `origin/main` 指到本次推送之前的提交（`github.event.before`）再跑，所以检查的是刚推上去的改动；PR 用基线的 `origin/main`；手动触发没有可靠的基点，跳过格式检查，只跑测试。首次推送、强推之后基点不可达时同样跳过并打 notice。

基点只管本批，历史文件里与定制无关的格式违规不会因为跑一次 `spotlessCheck` 全部冒出来，改到哪个文件才检查哪个。已知的四处（`SettingsNormalization.kt`、`MessageQueue.kt`、`PermissionTypes.kt`、`SettingVM.kt`）仍在，某次改动碰到其中一个时，整个文件会被检查，顺手把它修干净就好，不必为它们单独跑一轮。

## 文档检查

在仓库根目录运行 `markdownlint-cli2`（v0.23.0 / markdownlint v0.41.0），它会自动发现根目录的 `.markdownlint-cli2.jsonc`；从 `docs/` 子目录跑不会向上找配置：

```powershell
markdownlint-cli2
```

`globs` 覆盖 `docs/**/*.md`、`AGENTS.md`、`README.md`、`terminal/README.md`，忽略 `.agents/`、构建目录和 `node_modules`。关掉的规则是 `MD013`（长行）、`MD033`（README 的内联 HTML）、`MD041`（README 首行是 HTML）、`MD060`（参考文档的 CJK 表格按字符宽度对齐，默认风格过不了，暂不强制表格竖线风格）；其余结构规则保持开启。新增或修改文档后跑一次，应该 0 error。

## 设备上的 androidTest

- 推测试包之前先删掉设备上的同名文件再 `adb push`。同名文件已存在时，推上去的可能仍是旧字节，装上的旧包表现为测试进程解析不到新类
- 系统的安装限制会拦下 Gradle 的安装步骤（`INSTALL_FAILED_USER_RESTRICTED`）。改为把包装到 `/data/local/tmp`，再 `su -c "pm install -r -d --user 0 <包路径>"` 安装
- 验收样本含私人数据，放在设备上而不是仓库里，哪份不在就跳过哪份，见 [`backup/acceptance.md`](backup/acceptance.md)
