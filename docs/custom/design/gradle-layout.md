# Gradle 模块目录布局

状态：已实施，位于分支 `refactor/gradle-layout`，已 squash 为单个提交。本文描述目录与构建接线，不改包名、namespace、project path 或运行行为。

## 目标

- 根目录不再平铺 13 个模块目录
- 让依赖方向和模块职责一眼可见
- 保留现有 project path 和 `./gradlew :app:...` 命令，避免 CI、文档和脚本连带改动
- 把迁移拆成可独立验证的阶段，尤其隔离 `material3` 的 submodule 风险

## 非目标

- 不拆 `app` 的 feature 模块。`app` 有 789 个文件、约 42 MB，大部分定制都在里面，拆分是另一个独立项目
- 不把 project path 改成 `:core:ai`、`:platform:workspace`。这会改所有 `project(":...")`、CI 任务和文档命令，收益很小
- 不移动 `web-ui`、`tools`、`docs`、`build-logic`。它们不是 Gradle 模块，或属于构建入口
- 不合并、删除或重命名任何模块
- 不改任何 Kotlin/Java 包名、Android namespace、Room schema 或资源

## 迁移前

根目录：

```text
app/  ai/  common/  document/  material3/  mediagen/  oauth/
search/  speech/  terminal/  ui/  web/  workspace/
app/baselineprofile/
```

依赖关系：

```text
app -> 所有模块
search -> ai, common
ai -> common
speech -> common
mediagen -> common
其余模块无模块间依赖
```

补充：

- `build-logic` 是 included build，入口固定在根目录
- `web-ui` 是 pnpm 前端，`web/build.gradle.kts` 通过 `rootProject/web-ui` 找它
- `material3/material-color-utilities` 是 git submodule
- `app` 是唯一 application 模块，签名、每日构建和 baseline profile 都按 `app/` 路径写死

## 最终布局

```text
app/                         # 应用入口，不动
  baselineprofile/

core/
  ai/
  common/
  document/
  ui/
  material3/                 # Phase 2，涉及 submodule

integrations/
  mediagen/
  oauth/
  search/
  speech/

platform/
  terminal/
  web/
  workspace/

build-logic/                 # included build，不动
web-ui/                      # React 前端，不动
tools/
docs/
```

`app` 留在根目录，原因：

- CI 每日构建写 `app/app.key`
- 发布产物路径是 `app/build/outputs/apk/...` 和 `app/build/outputs/mapping/...`
- `app:baselineprofile` 的 `targetProjectPath` 是 `:app`
- 签名配置从 `local.properties` 读取 `storeFile`，可能指向 `app/`
- 把 `app` 移到 `apps/app` 会同时改 CI、签名 secret 和产物路径，只换来目录对称

如果以后要做 `apps/app`，单独作为 Phase 3，不和库模块归类混在一起。

## Gradle 接线

`settings.gradle.kts` 保留 project path，只映射 `projectDir`：

```kotlin
include(":app")
include(":app:baselineprofile")

val relocatedModules = mapOf(
    ":ai" to "core/ai",
    ":common" to "core/common",
    ":document" to "core/document",
    ":material3" to "core/material3",
    ":ui" to "core/ui",
    ":mediagen" to "integrations/mediagen",
    ":oauth" to "integrations/oauth",
    ":search" to "integrations/search",
    ":speech" to "integrations/speech",
    ":terminal" to "platform/terminal",
    ":web" to "platform/web",
    ":workspace" to "platform/workspace",
)

relocatedModules.forEach { (path, dir) ->
    include(path)
    project(path).projectDir = file(dir)
}
```

要点：

- 先 `include`，再设置 `projectDir`
- `:app` 和 `:app:baselineprofile` 保持原来的物理布局
- Phase 1 不加入 `:material3`；Phase 2 再把 `":material3" to "core/material3"` 加进 `relocatedModules`
- `:ai`、`:workspace` 等 project path 不变，所有 `project(":ai")` 不需要改
- CI 里的 `:app:testGithubDebugUnitTest`、`:workspace:testDebugUnitTest` 不需要改
- `./gradlew projects` 仍显示原来的 project path

## Phase 1：移动非 submodule 模块（已执行）

创建父目录后逐个移动：

```text
ai        -> core/ai
common    -> core/common
document  -> core/document
ui        -> core/ui
mediagen  -> integrations/mediagen
oauth     -> integrations/oauth
search    -> integrations/search
speech    -> integrations/speech
terminal  -> platform/terminal
web       -> platform/web
workspace -> platform/workspace
```

推荐用 `git mv`，让 Git 记录 rename，而不是删除加新增。

同步更新：

- `settings.gradle.kts` 的映射
- `AGENTS.md` 的模块结构
- `docs/custom/roadmap.md` 的模块表
- `docs/custom/checklist/*` 中的旧源码路径
- `docs/custom/locale.md` 等引用 `integrations/oauth/src`、`integrations/speech/src` 的说明
- 各模块 README 里的相对链接

Phase 1 本身不涉及：

- `build.gradle.kts` 的 spotless exclude：Phase 1 时保持 `material3/material-color-utilities/**`，Phase 2 已改为 `core/material3/material-color-utilities/**`
- `web/build.gradle.kts` 的 `rootProject/web-ui`（迁移前路径）
- `.github/workflows/*`：project path 和 app 物理路径都没变
- `app/build.gradle.kts` 的 `:app:baselineprofile` 和 `schemas` 路径

## Phase 2：material3 与 submodule（已执行）

`material3` 带有 `material3/material-color-utilities` submodule，不能直接当普通目录移动。步骤：

1. `git submodule deinit -f material3/material-color-utilities`
2. `git mv material3 core/material3`
3. 在 `settings.gradle.kts` 的 `relocatedModules` 加入 `":material3" to "core/material3"`
4. 更新 `.gitmodules` 的 `path` 为 `core/material3/material-color-utilities`
5. `git submodule sync --recursive`
6. `git submodule update --init --recursive`
7. 更新根 `build.gradle.kts` 的 `targetExclude("core/material3/material-color-utilities/**")`
8. 重新跑构建和 `git submodule status`

如果 `git mv` 在 submodule 上失败，停止并检查 gitlink，不要用普通文件复制硬搬。Phase 2 单独验证，不和 Phase 1 混在一起。

本次已经执行 Phase 2，`material3` 现在位于 `core/material3`。保留“永久留在根目录”作为将来遇到 submodule 工具链问题时的退路，不再作为待选方案。

## Phase 3：可选，移动 app

只有确实需要 `apps/app` 对称时才做。需要同时处理：

- `settings.gradle.kts` 的 `:app`、`:app:baselineprofile` 映射
- `:app:baselineprofile` 的 `targetProjectPath`
- daily build 的 `app/app.key` 写入路径
- 发布产物的 APK 和 mapping 路径
- `SIGNING_CONFIG` secret 里的 `storeFile`
- `app/build.gradle.kts` 的 `schemas`、`compose_compiler_config.conf` 等 `$projectDir` 相对路径
- 所有文档里的 `app/src/...` 路径

这一步收益只是目录对称，不建议和 Phase 1/2 一起做。

## 迁移时路径检查

迁移后至少检查：

```powershell
rg -n "(^|[\s(`])ai/(src|build\.gradle)" docs AGENTS.md README.md
rg -n "(^|[\s(`])common/(src|build\.gradle)" docs AGENTS.md README.md
rg -n "(^|[\s(`])workspace/(src|build\.gradle)" docs AGENTS.md README.md
rg -n "(^|[\s(`])terminal/(src|build\.gradle)" docs AGENTS.md README.md
rg -n "(^|[\s(`])web/(src|build\.gradle)" docs AGENTS.md README.md
rg -n "(^|[\s(`])(search|speech|mediagen|oauth|ui|document|material3)/(src|build\.gradle)" docs AGENTS.md README.md
```

`app/` 在 Phase 1/2 不移动，相关路径不需要改。真正的干净判据是文档链接检查加构建，而不是只靠 grep。

## 验证

Phase 1 后：

```powershell
./gradlew projects
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest `
  :ai:testDebugUnitTest `
  :workspace:testDebugUnitTest
./gradlew :web:buildWebUi
./gradlew spotlessCheck
```

检查：

- `./gradlew projects` 的 project path 与迁移前一致
- `git diff --stat` 主要是 rename
- `web` 的 `buildWebUi` 仍能找到 `web-ui`
- `app` 的 baseline profile、Room schema、web 资源和 APK 产物都正常
- `git submodule status` 在 Phase 2 后干净
- `markdownlint-cli2` 和文档链接检查通过
- 设备安装一次 debug APK，确认启动、聊天、终端和 MCP 页面不受影响

CI 在推送后应保持原任务和原产物路径。Phase 1/2 不需要改 `.github/workflows/*`；Phase 3 才需要。

## 风险与回滚

| 风险 | 处理 |
| --- | --- |
| `material3` submodule 路径迁移失败 | Phase 2 单独做；失败就回滚 submodule 配置，`material3` 留在根目录 |
| `projectDir` 映射写错 | 先 `./gradlew projects`，再跑最小 assemble |
| 文档旧路径漏改 | `rg` sweep + 链接检查 + 人工看 AGENTS/roadmap |
| `app` 签名或产物路径被连带改动 | 推荐 Phase 1/2 不移动 `app` |
| `terminal` 原生构建缓存 | 迁移后 clean 一次模块 build |
| 配置缓存失效 | 预期行为，不视为失败 |

回滚方式：

- 在独立分支上做，未推送前直接 reset
- 已推送时用 `git revert`，不要强推 `main`
- Phase 2 回滚后重新 `git submodule sync`、`git submodule update --init`

## 备选方案

### 单一 `packages/` 父目录

```text
packages/app
packages/ai
packages/common
...
```

优点：一致、简单；缺点：只是把平铺换成另一个平铺，看不出职责，且移动 `app` 会牵连签名和 CI。

### 语义分组并把 app 移到 apps/

即本方案的 Phase 1/2 加 Phase 3。布局最对称，但签名 secret、产物路径和文档改动最大，收益主要是视觉一致性。

### 改 project path 为 `:core:ai`

架构上最像多层项目，但会破坏所有 `project(":...")`、CI 任务和文档命令。没有功能收益，不建议。

## 验收标准

- 根目录只剩 `app/`、`core/`、`integrations/`、`platform/` 和基础设施目录
- 所有 project path 不变
- `:app:assembleGithubDebug`、目标 JVM 测试、`web:buildWebUi`、`spotlessCheck` 通过
- `git diff` 以 rename 为主；39 个 Kotlin 文件有 ktlint 格式变化（import 顺序、空行、换行、括号展开等），语义等价
- CI 任务和产物路径不变
- 文档链接和路径引用没有旧位置
- `material3` 的 submodule 状态干净

## 未决项

- Phase 3 的 `apps/app` 暂不做；签名 secret、CI 产物路径和文档改动都留到确有需要时
- 是否把 `speech`、`document` 这类小模块进一步合并；当前不做，先保持项目边界
