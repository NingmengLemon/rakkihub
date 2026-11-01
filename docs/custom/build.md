# 构建与测试

## 前提

以下三项不在仓库内，缺任何一项都会在特定任务上失败：

- JDK 必须是 HotSpot。默认 `JAVA_HOME` 指向 OpenJ9 的 JDK 21，KSP 会抛 `NoClassDefFoundError: jdk.jfr.Event`。改用 `E:\WorkBench\Java\jdk-23.0.2`
- `local.properties` 提供 release 签名（被 gitignore）
- `material3/material-color-utilities` 是 submodule，未初始化时 `:material3:compileDebugKotlin` 报 `Unresolved reference 'dynamiccolor'`

## 构建

```powershell
$env:JAVA_HOME='E:\WorkBench\Java\jdk-23.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleGithubDebug :app:assembleGithubRelease
```

上游 3.1.1 引入 `distribution` 维度的 product flavor（`github` / `play`），任务名与产物路径随之带上渠道名。用 `github` 渠道：`play` 只给上架 Google Play 用，本仓库不需要。

产物位于 `app/build/outputs/apk/github/debug/` 与 `app/build/outputs/apk/github/release/`，各有 universal 与分 ABI 三种。debug 的 applicationId 是 `top.lemoneko.rakkihub.debug`，release 是 `top.lemoneko.rakkihub`，两者与官方版的 `me.rerere.rikkahub` 都不同，可以同时安装。只有包名相同才需要签名一致，所以 release 自签名在这里不构成与官方版共存的障碍。

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

## 格式检查

`spotlessApply` 与 `spotlessCheck` 用 ktlint，`ratchetFrom("origin/ref")` 写死，只检查相对该引用改动过的文件。因此 `origin/ref` 必须存在且跟随上游，不要清理这个引用：本地 `ref` 快进后若尚未推送，`origin/ref` 仍停在旧基点，`spotlessCheck` 会把上游那一整批文件当作改动过的来检查，报出与本地定制无关的格式违规。

`spotlessCheck` 有 4 处历史违规，与定制无关：`SettingsNormalization.kt`、`MessageQueue.kt`、`PermissionTypes.kt`、`SettingVM.kt`。跑过 `spotlessApply` 之后把这四个文件 `git checkout` 回来，不要顺手提交格式化改动；如果其中一个文件本来就要改，只回退它被格式化掉的那部分，功能改动保留。

## 设备上的 androidTest

- 推测试包之前先删掉设备上的同名文件再 `adb push`。同名文件已存在时，推上去的可能仍是旧字节，装上的旧包表现为测试进程解析不到新类
- 系统的安装限制会拦下 Gradle 的安装步骤（`INSTALL_FAILED_USER_RESTRICTED`）。改为把包装到 `/data/local/tmp`，再 `su -c "pm install -r -d --user 0 <包路径>"` 安装
- 验收样本含私人数据，放在设备上而不是仓库里，哪份不在就跳过哪份，见 [`backup/acceptance.md`](backup/acceptance.md)
