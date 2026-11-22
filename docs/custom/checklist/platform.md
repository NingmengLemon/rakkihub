# 明细：平台、构建与品牌

本文件是 [checklist.md](../checklist.md) 的明细，收录 C7、C12、C37 至 C39、C45、C48、C55 至 C56、C90 至 C94、C101、C127 至 C129、C136、C138、C139、C143、C144、C146、C149、C150、C151。

<a id="c7"></a>

## C7 应用图标旋转 180 度

- 落点：`app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` 下的 `ic_launcher.png`、`ic_launcher_background.png`、`ic_launcher_foreground.png`、`ic_launcher_monochrome.png`，5 个密度共 20 个 png 各旋转 180 度；`mipmap-anydpi-v26/ic_launcher.xml` 的结构不变
- 理由：让桌面上装的这个版本与官方版一眼区分开，造型变成倒着的兔子
- 已被 C90 覆盖：图标整组换成插画，monochrome 层删除，180 度旋转不再适用。原记录如下。合并注意：用 hflip 加 vflip 逐像素翻转，没有重采样，尺寸与 alpha 通道保持不变。上游的背景是浅上深下的纵向渐变，转后变成深上浅下，必须跟着转：legacy 的 `ic_launcher.png` 把背景与兔子烤在同一张图里，整图旋转后渐变必然反转，自适应路径下只有一起转两条路径的观感才一致。`monochrome` 必须一起做，否则 Android 13 的主题图标会退回官方样式。设置-关于页直接复用 `R.mipmap.ic_launcher`，无需单独改。上游 3.1.0 的 `bf04cde9` 调整过图标配色，此后上游再改这些 png 时会与本项冲突，处理方式一律保留我们旋转后的版本。它同时改了 `web-ui/public/favicon.ico`、`docs/icon.png`、`docs/img/poster.png`，这几处不在本项范围内

<a id="c12"></a>

## C12 改包名与应用名

- 落点：`app/build.gradle.kts`（`applicationId` 改为 `top.lemoneko.rakkihub`）、`AndroidManifest.xml`（shortcut deep link 的 scheme 从 `rikkahub` 改为 `rakkihub`）、`res/xml/shortcuts.xml`（`android:data` 同步）、7 个 locale 的 `app_name` 改为 `RakkiHub`（德语是上游 3.1.1 才加的）、`ExampleInstrumentedTest`（不再写死旧包名，改与 `BuildConfig.APPLICATION_ID` 比对）
- 理由：自主 fork 阶段要与官方应用并存，安装标识与私有数据目录必须独立
- 合并注意：`namespace` 保持 `me.rerere.rikkahub` 不动，类名、R 类与 `shortcuts.xml` 的 `targetClass` 都不受影响；`applicationIdSuffix = ".debug"` 保留，debug 包名是 `top.lemoneko.rakkihub.debug`；`${applicationId}` 拼的 authorities 与 `BuildConfig.APPLICATION_ID` 自动跟随

<a id="c37"></a>

## C37 CI 跑格式检查与 JVM 测试

- 落点：新增 `.github/workflows/checks.yml`（push 到 `main`、PR、手动触发）：环境与每日构建一致（JDK 21、pnpm 11、Node 22、web-ui 依赖、submodules），跑 `spotlessCheck` 与 `:app:testGithubDebugUnitTest`、`:ai:testDebugUnitTest`、`:workspace:testDebugUnitTest`，失败时上传测试报告。push 时把 `origin/main` 指到 `github.event.before` 再跑格式检查（首次推送、强推后基点不可达时跳过并打 notice），PR 用基线的 `origin/main`，手动触发没有可靠基点也跳过；测试是全绿判据。`docs/custom/roadmap.md` 的 2.2 与 `docs/custom/build.md` 同步。
- 理由：仓库原来只有每日构建，格式与测试靠人工；这两类检查反复出现（Windows 固定失败、spotless 的 ratchet 基点），把它们做成自动闸门。app、ai 与 workspace 是定制最集中的三个模块，其余模块的测试本轮不跑，边际收益低。
- 合并注意：环境版本（JDK/pnpm/Node）与 `daily-build.yml` 两处维护，上游改其中之一时两边一起更新；`spotlessCheck` 的基点在 push 时由 workflow 改写，本地仍按 `origin/main`，build.md 有说明。CI 在 Linux 上跑，Windows 本机那批文件占用失败预期不再出现，首次跑批确认；若红了先看平台差异。`checks.yml` 是相对上游新增的文件，上游若加同名 workflow 时按本行核对（不会自动合并）。

<a id="c38"></a>

## C38 设置页提示授予本地网络权限

- 来源：上游 `903e058b`（`feat(setting): 设置页顶部提示授予本地网络权限`），代码与该提交一致。
- 落点：新增 `ui/components/ui/permission/LocalNetworkPermissionCard.kt`（`rememberLocalNetworkPermissionState` 在 SDK 37 及以上返回权限状态，`LocalNetworkPermissionCard` 是点一下发起申请的提示卡）；`SettingPage` 在通用设置之前按未授权状态插入这张卡；8 个 locale 的 `permission_local_network_desc` 改成覆盖「连接局域网内的自建服务」与「其他设备访问 Web 服务器」两类场景。
- 理由：targetSdk 37 起应用默认不能访问局域网。Web 服务器页会申请这个权限，但连局域网里的自建 provider、SearXNG、MCP 服务时没有对应入口，只会超时；设置页的提示卡补上这个入口。卡片只在未授权时出现，已经授权的安装不受影响。
- 移植注意：卡片用 `PermissionLocalNetwork`（`PermissionTypes.kt` 里标了 `@RequiresApi(37)`），SDK 判断在 `rememberLocalNetworkPermissionState` 里；`RikkaHubApp` 启动 Web 服务器前也会检查这个权限，那处逻辑没动。上游以后改这张卡或权限申请流程时按本行核对。

<a id="c39"></a>

## C39 把 README 收成一份 fork 说明

- 落点：根 `README.md` 重写为简体中文的 fork 说明（fork 定位、谨慎使用、官方仓库与官网、nightly 构建说明、定制文档入口、许可证），删除 `README_ZH_CN.md` 与 `README_ZH_TW.md`；`docs/README.md` 的说明同步，C4、C13 里涉及 README 的落点指向本项。
- 理由：本仓库是个人分支，保留上游三份翻译会让人误以为这是官方项目，官方仓库链接也埋在功能列表后面。README 需要一眼说清这是个人 fork、有风险，并指向官方仓库。
- 移植注意：上游 README 不再跟随；上游若改安装、构建或主要功能说明，按需抄进 `README.md` 与 `docs/custom/build.md`，不要恢复多语言版本。

<a id="c45"></a>

## C45 文案范围收成四份

- 落点：删除 `app`、`search`、`oauth`、`ui` 四个模块的 `values-ar`、`values-de`、`values-ko-rKR`、`values-ru`（16 个目录）；`app/build.gradle.kts` 的 `localeFilters` 改成 `en`、`ja`、`zh`、`zh-rTW`；`tools/i18n/config.yml` 的 `languages` 只留 `values`、`values-zh`、`values-zh-rTW`、`values-ja`，`tools/i18n/README.md` 的示例同步；补上 `values-ja` 与 `values-zh-rTW` 缺的三条压缩上下文文案（`chat_page_compress_keep_recent`、它的描述、`chat_page_compress_not_enough_messages`）；把中文里译成「令牌 / 權杖 / 詞元」的地方统一回 `Token`（`chat_page_compress_context_desc`、`search_detail_max_tokens`、`search_detail_max_tokens_per_page`、`stats_page_cached_tokens`、`stats_page_input_tokens`、`stats_page_output_tokens`）；`docs/custom/locale.md` 按四份重写。
- 理由：`tools/i18n` 用 Bun，本机跑不起来（没有 bun 与 `.env`），新文案只能手翻，8 份时每条要翻 7 次，四份只要 3 次；德、阿、韩、俄不在目标范围，繁中保留阅读习惯所以没删 `values-zh-rTW`。顺带清掉了上游自己没翻的三条压缩文案的缺条目债。
- 合并注意：没有目录的语言回退到英文，繁中命中 `values-zh-rTW`。`localeFilters` 与资源目录要一起动：它是允许列表，不在列表里的语言资源不会打进 APK（包括 app 自己的），但仓库里留着的目录不会被它删掉。上游给被删语言补的翻译，移植时不用再抄；以后要恢复某个语言，从上游对应版本拷 `strings.xml` 再补本分支的 key。清单里历史条目写的「8 份 / 7 个 locale」指当时的份数，不改写。把 `tools/i18n` 重写成 Python 记在 [`roadmap.md`](../roadmap.md) 的待办方向。

<a id="c48"></a>

## C48 版本号随提交自动滚动

- 落点：`app/build.gradle.kts` 用 `providers.exec` 读 `git rev-list --count HEAD` 与 `git rev-parse --short=7 HEAD`，`versionCode = 194 + 提交数`，`versionName = "3.1.1-custom.<提交数>+<短 SHA>"`，不加 dirty 后缀。git 命令执行失败（例如不在仓库里）时退回 `194` / `3.1.1-custom`；git 可执行文件本身不存在时会在配置阶段失败，配置缓存无法把进程启动失败当作可回退的输入值，构建环境需要 git。已用 `aapt2 dump badging` 核对构建里的 `versionCode`/`versionName` 与当次提交一致，并验证 Gradle 配置缓存会跟踪该外部命令输出（输出变化时缓存失效）。
- 理由：崩溃日志与安装包只看得到 versionCode/versionName；之前所有构建都停在 3.1.1/194，分不清是哪次提交。每日构建与本地构建共用同一套规则，短 SHA 直接对上。
- 合并注意：上游改 `versionCode`/`versionName` 时同步脚本里的 `upstreamVersionCode`/`upstreamVersionName`；提交数在 reset/rebase 改写 main 历史后会倒退，versionCode 随之下跌；merge 只会让计数变大、不会回退，只在 main 上构建（ref 的提交数少，构建出来会是更低的 versionCode）；本地浅克隆（depth-1）会把提交数算成 1、versionCode 退到 195，需要完整历史；checks 与 daily-build 的构建 job 是 `fetch-depth: 0`，daily-build 的 check job 是 depth 1（只查提交，不打包）。版本名不只用于显示与崩溃日志，`BuildConfig.VERSION_NAME` 还是默认 HTTP User-Agent（`DataSourceModule.kt` 与 `SettingPreferencesNetworkPage.kt`），会随提交变化。

<a id="c55"></a>

## C55 关于页独立图标

- 落点：新增 `app/src/main/res/drawable-nodpi/about_logo.webp`（1254×1240，来自 `rikka_icon.webp`），`SettingAboutPage` 用 `Image` 加 `ContentScale.Crop` 和 `RoundedCornerShape(28.dp)` 绘制，替代原来的 `rememberAppLogoLayers`/`drawAppLogo`；launcher 图标和浮动窗口不变，浮动窗口仍用 `rememberAppLogoLayers`。
- 已被 C90 覆盖：launcher 也换成这张插画，关于页与 launcher 一致。原记录如下。理由：关于页想用自己的图标，同时不动应用 launcher。
- 合并注意：上游改 `SettingAboutPage` 时按这个；换图直接替换 `about_logo.webp`；C54 的彩蛋移除保持不变。
- 测试：编译通过；关于页显示新图标，launcher 与浮动窗口仍用原图标。

<a id="c56"></a>

## C56 文档 lint 配置

- 落点：根目录 `.markdownlint-cli2.jsonc`，范围 `docs/**/*.md`、`AGENTS.md`、`README.md`、`platform/terminal/README.md`，忽略 `.agents/`、构建目录和 `node_modules`，关掉 `MD013`/`MD033`/`MD041`/`MD060`；`docs/custom/build.md` 加「文档检查」；`docs/references/chat-generation-pipeline.md` 修列表缩进和代码块语言；`docs/custom/backup/README.md` 补代码块语言。
- 理由：文档一直没有 lint 入口，列表缩进、代码块语言这类结构问题只能靠平时看；用本机已装的 markdownlint-cli2，配置适配仓库的长行、README 的居中 HTML 和紧凑表格。
- 合并注意：这是本地检查，CI 没有跑；规则列表在配置和 `build.md` 两处，改规则时同步。MD060 关掉是因为参考文档的 CJK 表格按字符宽度对齐，默认风格过不了，别按 ASCII 宽度重排；其余结构规则保持开启。markdownlint-cli2 不在仓库依赖里，换机器要自己装。
- 测试：仓库根目录 `markdownlint-cli2` 14 个文件 0 error；参考文档和备份文档的修改只动空白与代码块语言，内容未变。

<a id="c90"></a>

## C90 应用图标与可见文案改名 RakkiHub

- 落点：`app/src/main/res/mipmap-*/` 的 `ic_launcher.png`、`ic_launcher_foreground.png`、`ic_launcher_background.png` 全部换成关于页那张插画（`drawable-nodpi/about_logo.webp`）生成的图；`mipmap-anydpi-v26/ic_launcher.xml` 去掉 `<monochrome>`，删除五个 `ic_launcher_monochrome.png`；`docs/icon.png` 与 README 用的 `docs/readme-icon.png` 同步；可见文案：`SettingAboutPage.kt` 标题改用 `stringResource(R.string.app_name)`，`NsdServiceRegistrar.kt` 的 `DEFAULT_SERVICE_NAME` 改成 `rakkihub`（mDNS 地址变成 `rakkihub.local`）与 NSD 描述，`SettingPreferencesNetworkPage.kt` 的默认 User-Agent，`ContextUtil.kt` 的导出文件名，`McpOAuthCoordinator.kt` 的 OAuth clientName，`oauth` 模块 `OAuthLoopbackCallbackServer.kt` 的回调页标题、品牌与四条提示，`DataSourceModule.kt`、`core/ai/util/Request.kt`、`mediagen` 的 `X-Title`（`X-Title` 与 `HTTP-Referer` 已由 C96 删除），`RootfsPatcher.kt` 的生成标记
- 理由：应用改名为 RakkiHub，图标换成和关于页一致的插画。插画是满幅人物，monochrome 层用它的 alpha 会变成一块黑色剪影，主题图标反而更差，所以去掉 monochrome 层，本应用不参与系统主题着色
- 合并注意：图标由 `about_logo.webp` 生成，前景缩放到 108dp 画布的 70% 居中，主体落在圆形遮罩内，但四角和边缘的头发、耳朵尖会被圆形遮罩裁掉一点，满幅人像没有更小的构图可退；legacy 圆角 20.8%，背景取插画主色 `#F4EEEF`；插画底部是裁切的人像，方形图标底部会留一条浅色带。浮动窗口气泡、导出分享图、workspace 文档图标复用 `R.mipmap.ic_launcher` 或 `ic_launcher_foreground`，会跟着变，不需要单独改。通知栏小图标 `ic_stat_rikkahub` 仍是旧的兔子。mDNS 主机名改成 `rakkihub.local`，旧书签需要重填。`search-picker.tsx` 的 `rikkahub: "RikkaHub"`、备份导入导出的官方 RikkaHub 文案都指官方服务，保留；官方 provider 描述已由 C95 删除。`web-ui/public/favicon.ico` 与整个 `docs/img/` 已由 C94 处理，通知栏小图标 `ic_stat_rikkahub` 有意保留
- 测试：`:app:assembleGithubDebug` 通过，设备上安装启动正常；图标用圆形、圆角方形和 96px 预览核对；app JVM 803 条（7 条既有 Windows 失败）、ai 345 条、search 18 条、mediagen 43 条、oauth 9 条全过，改动过的 workspace `ExampleUnitTest` 1 条通过；spotless、markdownlint 通过。没有在真实桌面上截图验证图标，也没有验证主题图标；`web-ui/public/favicon.ico` 与整个 `docs/img/` 已由 C94 处理

<a id="c91"></a>

## C91 关于页链接改指本仓库

- 落点：`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingAboutPage.kt` 删除 Website 入口，GitHub 与 License 链接从上游 `rikkahub/rikkahub` 换成 `NingmengLemon/rakkihub`，License 指向 `blob/main/LICENSE`；四个 locale 删除不再使用的 `about_page_website`；`README.md` 的许可证段落补一句说明
- 理由：仓库改名后关于页仍指向官方官网和上游仓库；本仓库没有自己的网站，直接删掉入口，GitHub 和 License 指向本仓库
- 合并注意：上游改关于页时保留这两处链接；`LICENSE` 文件保持纯 AGPL-3.0 文本，不加作者信息，否则 GitHub 识别不出许可证类型，fork 的修改版权在 README 的许可证段落里说明。C14 里「`rikka-ai.com`、GitHub、LICENSE 链接保留」的旧说法已由本项取代
- 测试：`:app:compileGithubDebugKotlin` 通过；关于页 Website 入口消失，四个 locale 删除后没有残留引用；没有在设备上逐个点开链接验证

<a id="c92"></a>

## C92 launcher 插画重新对位

- 落点：重新生成 `app/src/main/res/mipmap-*/` 的 `ic_launcher.png`、`ic_launcher_foreground.png`、`docs/icon.png` 与 `docs/readme-icon.png`（`ic_launcher_background.png` 不变），插画占 108dp 画布 78%，并相对画布中心左移 1.5%、上移 6%；README 图标再按 adaptive 72/108 安全区裁切
- 理由：70% 居中时眼睛落在圆形遮罩的右下，看起来没对正；按眼睛位置（画布 47.4%、69.6%）换算，78% 再左移 1.5%、上移 6% 后眼睛大致落在圆形遮罩中心
- 合并注意：C90 里“前景缩放到 70%”的说法已由本项取代。圆形遮罩下耳朵尖和两侧头发仍会被裁掉；插画底部是裁切的人像，上移后底部裁切线接近遮罩下缘，再往上移就会露出来。legacy 圆角 20.8%、背景 `#F4EEEF` 不变
- 测试：`:app:assembleGithubDebug` 通过并安装到设备；生成后的圆角方形、圆形和 96px 预览核对；没有在真实桌面上截图验证

<a id="c93"></a>

## C93 tools/i18n 重写成 Python

- 落点：`tools/i18n/` 换成 Python 实现，新增 `pyproject.toml`、`uv.lock`、`src/i18n/{cli,config,strings_xml,validation,references,translator}.py`、`tests/`，`config.yml` 的简中/繁中加 `protected_terms`（`Token`）与 `token` 术语（日文不加，仍用トークン），`.gitignore` 改成 Python 的忽略项；删除 Bun/TypeScript 的 `package.json`、`bun.lock`、`tsconfig.json`、`.oxfmtrc.json`、`src/*.ts`、`test/*.ts`；`README.md`、`docs/README.md`、`docs/custom/locale.md`、`docs/custom/roadmap.md`、`AGENTS.md` 与 `.agents/skills/i18n-localization/SKILL.md` 的命令同步成 `uv run --project tools/i18n i18n`
- 理由：本机没有 bun，工具跑不起来，新文案只能手翻；Python 用 uv 直接可跑，接回同一份 `config.yml`，保留缺条目与未使用条目的检查。官方版出现过把压缩语境里的 Token 译成「令牌」「權杖」的问题，本分支修过（C45），这里给简中/繁中加按语言配置的 `protected_terms` 校验：原文出现该术语而译文缺失时按校验失败重试，术语表里点明 Token 保持英文；边界按 ASCII 字母判断，所以「最大Token数」这类紧贴 CJK 的写法也算保留，日文不配这个列表，仍用トークン
- 合并注意：上游改 `tools/i18n` 时按本项整体替换；`config.yml` 原有字段与语义不变，新增 `protected_terms`；`.env` 不提交，没有 key 时只能 `--skip-translate` 或手翻；`strings.xml` 读写仍然只改动涉及的 `<string>`，已有换行原样保留，追加的新行用 LF；校验时字面量 `\n` 与真实换行按同一数量比较，CDATA 里的 `&` 和未知实体不再误报
- 测试：`uv run --project tools/i18n --group dev pytest tools/i18n/tests -q` 25 条通过；`list -m app --missing` 显示 0/1538，`list -m search` 显示 5/5，`unused -m app,search,oauth,ui` 输出 46 条与旧工具一致；用 `.env` 实测 `test-connection` 与一次简中/繁中的批量翻译，`Max Tokens`/`No token limit` 在简中/繁中都保留为 Token，日文输出トークン，三种语言都通过校验

<a id="c94"></a>

## C94 图标收尾：favicon、Web 空状态 logo、清理旧素材

- 落点：`web-ui/public/favicon.ico` 用 16/32/48/64/128/256 六帧，全部取 launcher 的 72/108 安全区裁切；`web-ui/public/icon.png`（256）用同一裁切套 20.8% 圆角，另有不透明方图 `web-ui/public/apple-touch-icon.png`（180）；`web-ui/app/root.tsx` 同时声明 favicon.ico、256 PNG 与 apple-touch-icon；`web-ui/app/components/logo.tsx` 从内联的旧兔子线稿 SVG 改成渲染 `/icon.png`（`alt` 用品牌名，宽高属性是 256，显示尺寸由调用方的 `className` 决定），`web-ui/app/routes/conversations.tsx` 的新会话空状态去掉 `[&>svg]:size-16` 包裹与 `text-primary`；删除整个 `docs/img/`（海报 `poster.png` 与五张没有引用的界面截图，共约 4.2 MB）；生成脚本移到 `tools/gen-icons.py`，一次覆盖 launcher 图标、`docs/icon.png`、`docs/readme-icon.png`、`web-ui/public/icon.png`、`web-ui/public/favicon.ico` 与 `web-ui/public/apple-touch-icon.png`（在仓库根目录运行 `uv run --with pillow python -P tools/gen-icons.py`，Pillow 由 uv 临时安装；`build/` 被 gitignore，脚本放那里不可复现）
- 理由：C90 只换了 launcher 与 README 图标，浏览器标签页、Web UI 新会话空状态、海报三处还是旧兔子；本分支对 `docs/img/` 没有任何引用（根 README 是 fork 说明，见 C39），五张截图连上游 README 都没用，留着只是几 MB 的旧素材
- 合并注意：favicon 统一用插画，16/32 帧也从同一构图生成，不再为 16px 单独准备兔子头剪影；`web-ui/public/icon.png` 与 favicon 先按 adaptive 72/108 裁切，避免比 launcher 多一圈留白；圆角沿用 legacy PNG 的 20.8%，adaptive 图标的遮罩由 OEM 决定，两者轮廓不完全相同；透明背景没有采用：插画本身带不透明品牌底色，去掉圆角裁切得不到透明底，要抠图，且 16px 下浅色头发与浅色主题对比不足，所以保留 `#F4EEEF` 底色，`apple-touch-icon.png` 不透明、圆角交给 iOS 自己裁；`web-ui/public/` 由 `web` 模块 preBuild 的 `pnpm run build` 拷进 `platform/web/src/main/resources/static`（该目录 gitignored），只改 `web-ui/public` 而不重新构建 APK 不会生效；`docs/icon.png` 保持 512（通用），`docs/readme-icon.png` 也取 512 但按 adaptive 72/108 安全区裁切，README 用它显示 launcher 的真实裁切效果；`web-ui/public/icon.png` 取 256，够 64px 显示的四倍屏；通知栏小图标 `ic_stat_rikkahub` 是有意保留的兔子头剪影（见 C90）
- 测试：六个尺寸逐帧导出核对，`web-ui/public/icon.png` 为 256 RGBA 透明圆角，`apple-touch-icon.png` 为 180 全不透明；`pnpm run typecheck`、`pnpm run build` 通过，oxfmt 对 `root.tsx` 的改动行无新增差异（整棵 `web-ui/app` 另有既有格式差异，CI 不跑这项）；Pillow 13.0.0（uv 临时安装，脚本未钉版本）；`:app:assembleGithubRelease` 通过，解包 APK 后 `static/favicon.ico`（95232 字节）、`static/icon.png`（54238 字节）、`static/apple-touch-icon.png`（33120 字节）与 `web-ui/public` 下的文件哈希一致；K60 上启动 Web Server 后三个文件都返回 200 且内容一致，页面运行时生成 favicon、256 PNG 与 apple-touch-icon 三条链接，新会话空状态 logo 在深色主题下显示正常。没有在桌面浏览器标签栏里做像素级核对

<a id="c101"></a>

## C101 README 图标按 launcher 安全区裁切

- 落点：`tools/gen-icons.py` 增加 `readme_icon()`，按 adaptive `72/108` 安全区裁切并保留同一圆角；`docs/readme-icon.png`（512）由脚本生成，`README.md` 改引用它；`docs/icon.png`、`web-ui/public/icon.png`、favicon 和 launcher 各密度保持不变。
- 理由：README 原来直接用完整 108dp 画布的 `docs/icon.png`，插画只占 78%，在实际 launcher 的安全区裁切后视觉上偏小；单独生成安全区版本，不改变桌面和 Web 资源。
- 合并注意：改插画参数后要重跑 `uv run --with pillow python -P tools/gen-icons.py`，三个图标和 README 图标一起更新；`docs/readme-icon.png` 是生成物，不要手工改。
- 测试：`docs/readme-icon.png` 为 512x512 RGBA，和脚本重跑结果一致；README 引用路径存在；本项落地时 launcher、Web、favicon 无 diff。
- 2026-11-22 更新：Web 侧 `icon.png`、`favicon.ico` 与新增的 `apple-touch-icon.png` 已由 C94 改为与 `docs/readme-icon.png` 相同的 72/108 裁切；本项当时写的 Web 资源保持不变只适用于当时，`docs/readme-icon.png` 的生成逻辑未变。

<a id="c127"></a>

## C127 Gradle 模块目录分组

- 落点：根目录的 `ai/common/document/material3/ui` 移到 `core/`，`mediagen/oauth/search/speech` 移到 `integrations/`，`terminal/web/workspace` 移到 `platform/`；`app`、`app/baselineprofile`、`build-logic`、`web-ui`、`tools`、`docs` 不动；`settings.gradle.kts` 用显式 `projectDir` 映射保留 `:app`、`:ai`、`:workspace` 等 project path；`material3` 的 `material-color-utilities` submodule 一并移动并更新 `.gitmodules`；`web-ui/copy.ts` 的目标改为 `platform/web/src/main/resources/static`；`spotless` 的 submodule 排除、文档、i18n 配置、markdownlint glob 和 `tools/trace` 输出根同步新路径。
- 理由：根目录平铺 13 个模块，职责边界和依赖方向不直观；保留 project path 和包名能让 `./gradlew :app:...`、CI、签名、baseline profile 和下游脚本都不必改，只调整目录和 `projectDir`。
- 合并注意：这不是功能改动，但 rename 会让这些文件进入 spotless ratchet，39 个 Kotlin 文件因此有 ktlint 的 import 顺序、空行、换行、括号展开和冗余分号变化，语义不变；上游同步时路径引用要按新目录核对，尤其 `tools/trace`、`tools/i18n`、`web-ui/copy.ts` 和 `.gitmodules`；`app` 移到 `apps/app` 的 Phase 3 暂不做，签名 secret 和每日构建产物路径仍按根目录 `app/`；不要把 `web-ui`、`tools`、`docs`、`build-logic` 一起收进模块目录。
- 测试：`./gradlew projects` 的 14 个 project path 与迁移前一致且位置正确；`:app:assembleGithubDebug`、`:app:testGithubDebugUnitTest`（898 条）、`:ai:testDebugUnitTest`（385 条）通过；`:workspace:testDebugUnitTest` 的定向 2 条通过，完整 56 条仍有 25 条既有 Windows 基线失败；`:web:buildWebUi` 把 33 个文件写到 `platform/web/src/main/resources/static`，不再生成旧的 `web/`；`spotlessCheck`、markdownlint（28 文件）和文档链接检查通过；`git submodule status` 干净。

<a id="c128"></a>

## C128 信任用户安装的 CA 证书

- 落点：新增 `app/src/main/res/xml/network_security_config.xml`，`base-config` 同时信任 `system` 与 `user` 证书并保留 `cleartextTrafficPermitted="true"`；`app/src/main/AndroidManifest.xml` 的 `<application>` 增加 `android:networkSecurityConfig="@xml/network_security_config"`。与上游 `90fe936e` 一致。
- 理由：Android 7.0（API 24）起，`targetSdk` 24 及以上的应用默认只信任系统证书，自建 CA 签发的 Provider、MCP、搜索等 HTTPS 端点连不上；本分支大量使用自建中转与自托管服务，需要这条通道。同时这也是在设备上用 mitmproxy 抓包的前提。
- 合并注意：信任范围覆盖全部应用流量，任何装进用户证书库的 CA 都能解密，这是有意取舍；上游改这个文件时按本项对齐，不要退回只信系统证书；`targetSdk` 是 37，配置存在后 `android:usesCleartextTraffic` 不再被读取，所以 `base-config` 要显式写出 `cleartextTrafficPermitted`：API 28 及以上省略它会断开原有明文端点（Web 服务、自建 S3、rootfs 下载、OAuth 与 MCP 回环），API 26 和 27 的平台默认仍允许明文，写出只是把意图写明；用户证书锚与设备版本无关，从 `minSdk` 26 起覆盖整个支持范围。
- 测试：`:app:assembleGithubRelease` 与 `spotlessCheck` 通过；APK 内 `AndroidManifest.xml` 的 `android:networkSecurityConfig` 指向 `xml/network_security_config`，`aapt2 dump xmltree` 展开后是 `cleartextTrafficPermitted=true` 加 `system`/`user` 两个 `<certificates>`；安装到设备成功。经 mitmproxy 抓包与 U+FFFD 复现由使用侧验证，不在本项范围。

<a id="c129"></a>

## C129 每日构建上传 Actions Artifacts

- 落点：`.github/workflows/daily-build.yml` 在 `Gradle Build` 之后、发布 nightly prerelease 之前新增 `actions/upload-artifact@v7`，上传 `app/build/outputs/apk/github/release/*.apk` 与 `app/build/outputs/mapping/githubRelease/mapping.txt`，artifact 名为 `nightly-<完整 commit SHA>`，`overwrite: true`、`if-no-files-found: error`、`continue-on-error: true`，保留 14 天；`docs/custom/roadmap.md` 的 nightly 说明同步。
- 理由：nightly 用的是固定 tag，每晚覆盖同一个 Release，只能看到最新一版；按提交追溯某次每日构建的产物和 mapping 就没了。上传到 Actions Artifacts 后每次运行各留一份，名字带 SHA，可直接对上 commit。
- 合并注意：`continue-on-error: true` 让上传失败不连带跳过 Release，附加产物不能卡住主产物；`overwrite: true` 用于重跑同一次运行，v4 起 artifact 不可变、同名会直接失败，而重跑沿用同一个 SHA 与同一个 run、名字必然重复；步骤放在发布之前，所以 Release 失败也不影响这一轮的产物；artifact 保留 14 天，超过就随运行记录清理，长期留档仍以 Release 为准；`mapping.txt` 未压缩时有 167 MB 左右，加 3 个 APK 每次约 210 MB，公开仓库不占配额但上传耗时和保存期都要留意；`actions/upload-artifact` 的大版本要和 `checks.yml` 里的一起升，两处不同步时先看各自文档的 breaking change；`path` 给了两个模式时，artifact 根取公共父目录，zip 里会带 `apk/github/release/` 与 `mapping/githubRelease/` 前缀，不是平铺。
- 测试：本次只改 workflow 与文档，本地无法执行 Actions；推送后触发一次 `workflow_dispatch` 确认 artifact 出现在运行页面、内容为 3 个 APK（arm64-v8a、x86_64、universal）加 `mapping.txt`，且 nightly Release 仍照常更新。

<a id="c136"></a>

## C136 Python 脚本 PEP 723 元数据

- 落点：`tools/gen-icons.py` 在模块 docstring 后加 `# /// script`，声明 `requires-python = ">=3.10"` 与 `pillow`；`tools/backup-expectations.py`、`tools/backup-ref-counts.py` 在 shebang 后加同样的块，依赖为空。`.agents/skills/` 下的 vendored 脚本不加：它们随 skills 整批同步，单独加会被覆盖。
- 理由：这些脚本此前靠 `uv run --with ...` 或全局环境运行，依赖散落在文档命令里；PEP 723 把解释器与依赖声明放进文件，`uv run <script>` 直接可跑。
- 合并注意：PEP 723 的 `# /// script` 块只要在顶层（列 0）就能被 uv 解析，放在 docstring 之后甚至 import 之后也有效，`gen-icons.py` 就是把块放在 docstring 后；两个 backup 脚本把块放在 shebang 之后、docstring 之前，`gen-icons.py` 没有 shebang，块放在 docstring 之后；位置只影响阅读，不是 uv 的硬性要求。`requires-python` 取 3.10，其中 `backup-ref-counts.py` 的 `str | None` 注解在 3.9 会真实报错。Pillow 没有 pin，因为仓库里没有版本基线，`ImageDraw.rounded_rectangle` 只推出下限；要加 pin 时先确认多尺寸 ICO 的保存路径。`gen-icons.py` 文档里的 `uv run --with pillow` 命令仍然有效，不删。
- 测试：保留的 3 个脚本都通过 `python -m py_compile`；用 `tomllib` 复核块可解析、`requires-python`/`dependencies` 存在、docstring 仍在、`ast.parse(feature_version=...)` 通过；`uv run --offline --no-project --script` 对两种放置位置各跑过一个合成脚本。

<a id="c138"></a>

## C138 提供商图标补充

- 落点：新增 `app/src/main/assets/icons/sensenova-color.svg`；`AIIconMatcher.kt` 增加 `sensenova|sensetime|sensechat|商汤|日日新` 匹配，并把 NVIDIA 规则扩到 `nemotron|英伟达`。来源是上游 `1ed0a77c`。
- 理由：上游新增的提供商图标，属于低风险映射表补齐，便于按名称自动识别。
- 合并注意：匹配顺序在 NVIDIA 之后、PPIO 之前；新增图标保持同名 SVG 放在 `assets/icons/`。
- 测试：`:app:compileGithubDebugKotlin` 通过；未单独截图验证图标匹配。

<a id="c139"></a>

## C139 请求体日志只在 debug 构建打印

- 落点：`core/ai` 新增 `util/RequestLog.kt` 的 `logRequestBody`，`build.gradle.kts` 打开 `buildConfig`；Claude、Google、Interactions、Chat Completions、OpenAI 图片生成、Responses 的请求体日志改为 `logRequestBody`，发布构建不再执行序列化 lambda。来源是上游 `3adeba47`。
- 理由：请求体包含历史图片 base64，可能有几十 MB；发布版只为一行日志重新序列化一次，白占内存和 CPU。
- 合并注意：日志名保持原行为名（generateText/streamText/generateImage）；响应体和错误日志不在此范围。`RequestLog` 是 internal，只在 `core/ai` 内使用。
- 测试：`:ai:compileDebugKotlin`、`:ai:testDebugUnitTest` 通过；没有在 release APK 里实测日志缺失。

<a id="c143"></a>

## C143 provider mock server

- 落点：新增 JVM 模块 `tools/provider-mock`（project path `:tools:provider-mock`，目录与 project path 一致，`settings.gradle.kts` 直接 include），Kotlin JVM 17 加 `application` 插件；`gradle/libs.versions.toml` 增加 `kotlin-jvm` 插件别名，根 `build.gradle.kts` 以 `apply false` 声明，避免子模块解析插件时版本未知；依赖只有 Ktor 3.6.1 的 `ktor-server-core` 与 `ktor-server-cio`、`kotlinx-serialization-json`、`kotlinx-coroutines-core` 与 JUnit。`MockPayloads` 固定四种协议的正常文本、推理、工具调用、429、500、截断、空与慢场景，另有 `mock-utf8-split`（合法多字节文本按 7 字节切片）与 `mock-utf8-broken`（删一个续字节）两个 UTF-8 边界场景；`ProviderMockServer` 绑定 `127.0.0.1` 的临时端口（`0`），构造时启动、`close()` 停止，SSE 用 `respondBytesWriter` 手工分帧，因此能精确控制终止事件与中途断开；`MockScenario` 用模型名（`mock-tool` 等）选场景，`X-Mock-Scenario` 请求头可覆盖；`RequestRecorder` 记录方法、路径、query、请求头、原始请求体与时间戳，凭据类请求头只留存在性与 `Authorization` 的 scheme 前缀。`ProviderMockRule` 是 JUnit 4 的 `ExternalResource`；`Main` 接受 `--port` 固定端口，配合 `adb reverse` 可以让设备上的应用直接连过来。`core/ai/build.gradle.kts` 只加一行 `testImplementation(project(":tools:provider-mock"))`，新增 `ProviderMockIntegrationTest` 用真实 provider 实现走完整 HTTP 路径。
- 理由：`core/ai/src/test/resources/stream-traces` 只做解码器与合并器的离线回放，覆盖不到 SSE 分帧、请求头、非 2xx 与中途断流；用真实 provider 联调又需要 key 且结果不稳定。mock server 让这层路径可以在 JVM 单测里重复执行，本地联调也可以把应用直接指过来，不需要设备与真实网络。四种协议的终止事件形状、错误体形状与 `Retry-After` 都按各自 decoder 的判据写，改动偏移会同时打到模块测试与 `core/ai` 集成测试。
- 合并注意：本模块不参与 `app` 构建，也不作为任何已发布产物的依赖，只能在 `testImplementation` 里出现。`kotlin-jvm` 别名与根 `apply false` 不能省：build-logic 已经把 Kotlin 插件带上 classpath，子模块再带版本解析会报 `already on classpath with an unknown version`。不要用 `jvmToolchain(17)`，那样会触发 toolchain 解析与可能的 JDK 下载；改用 `compilerOptions.jvmTarget` 加 `java` 的 source/target 17，与 app、baselineprofile 一致。Ktor 只引 `cio` 与 `core`，SSE 分帧、状态码与 HTML 错误体都自己写，避免 `ktor-server-sse` 的格式或缓冲影响终止事件的精确性。模块里不要引入 OkHttp 或 Android 类，否则 `:ai` 单测 classpath 会多一份实现；`ProviderMockRule` 属于 JUnit 4，main 源集里只有这一个 JUnit 依赖。上游若改 provider 的请求路径、SSE 事件名或错误映射，先改 `MockPayloads`，再同步 `ProviderMockServerTest` 与 `ProviderMockIntegrationTest`。
- 测试：`.\gradlew.bat :tools:provider-mock:test :ai:testDebugUnitTest spotlessCheck` 通过，provider-mock 27 条、`ai` 模块 410 条（其中 `ProviderMockIntegrationTest` 20 条，覆盖四种协议的文本、工具、usage 与推理，以及四种协议的 429 带 `Retry-After`、Responses 的 500、Claude 与 Gemini 的截断 `IOException`、OpenAI Chat 与 Gemini 的空响应 `EMPTY_RESPONSE`），全部为 0 失败。`.\gradlew.bat :tools:provider-mock:run` 能打印四个 base URL，手动停止前可访问。反向对照：同一严格断言先通过 `mock-normal` 的流，再对 `mock-truncate` 的流抛 `AssertionError`，说明它既能通过正常流也能识别缺少终止事件的流。UTF-8 边界用 `mock-utf8-split`（合法文本按 7 字节切片）与 `mock-utf8-broken`（删一个续字节）两套：前者经真实 ClaudeProvider 解码后与原文逐字相等且没有 U+FFFD，后者的线上字节不是合法 UTF-8，解码后出现 U+FFFD。CI 的 checks 也跑 `:tools:provider-mock:test`。

<a id="c144"></a>

## C144 安全模式清除超大设置

- 落点：`SettingsField` 增加 `reset()`，把字段恢复成 key 不存在时的默认值；`SettingsStore.largeStoredFieldsFlow(minChars)` 只读盘上原始字符串长度、不解码，列出达到阈值的字段并按字符数倒序；`resetStoredField(keyName)` 通过 `update` 把字段恢复默认值并落盘，认不出的 key 直接返回；C157 移除注入/世界书后，`mode_injections`/`lorebooks` 两个 legacy key 由单独的列表列出并用 `remove` 清除。`SafeModeActivity` 在存在超大字段时显示「数据过大」分组，每行是字段标签与本地化数字格式的字符数，点「清除」弹确认框；「进入应用」改为第一位主按钮，「切换助手」改为描边按钮，说明文案去掉「可以切换助手解决问题」。新增 5 条字符串并在 values、values-zh、values-zh-rTW、values-ja 同步。来源是上游 `27976e4d`。
- 理由：导入超大世界书后，设置落盘时整份 JSON 编码会 OOM，而且每次启动后的第一次写入都会重现。安全模式原来只能切换助手，遇到这类数据没有出口；现在可以清掉撑爆内存的字段，先让应用能继续写盘。清理仍走 `update` 的 `normalized()`，引用该字段的关联值会一起收敛，例如清掉 `lorebooks` 会移除助手上指向它的 `lorebookIds`，清掉 `assistants` 可能改变当前助手。`largeStoredFieldsFlow` 只看盘上字符串长度，不触发 JSON 解码，避免在列表里再次分配大对象。
- 合并注意：5,000,000 字符的阈值只针对 DataStore 里的 String 字段，布尔和数字不参与；字段名用 `Preferences.Key.name`，界面把 `assistants` 与 legacy 的 `mode_injections`/`lorebooks` 映射成可读标签，其余显示原始 key。`resetStoredField` 走 `update`，内存和盘上一起改；清完后 `largeStoredFieldsFlow` 会重新发射，卡片自动消失。上游的英文文案没有照搬成硬编码，已本地化；`safe_mode_description` 同步删除「切换助手可解决」的承诺。进入安全模式后不要先点「进入应用」，否则下一次写盘仍可能 OOM。上游只做单字段清理：启动后第一次写盘的 `persisted` 还是 null，会重新编码整份设置，如果同时存在两个超大字段，清掉一个时另一个仍可能把这次写盘撑爆，这种文件需要先手工清多个字段或恢复备份。
- 测试：`SettingsStoreTest` 15 条（大字段时间距样本从 Lorebook 换成 QuickMessage：写入 20 万字符的 `QuickMessage`，`largeStoredFieldsFlow` 能列出 `quick_messages`，重启后 `resetStoredField` 把盘上字段缩回默认值且保留 `titlePrompt`；另有 1 条覆盖 legacy `lorebooks` key 的列出与清除）；`:app:testGithubDebugUnitTest` 906 条、`spotlessCheck` 通过。K60 上安装 release 后直接启动 `SafeModeActivity`：说明文案与按钮顺序正确；手工把 5.1M 字符的 `lorebooks` 注入 `settings.preferences_pb` 后，安全模式显示「Oversized data / Lorebook / 5.1 million characters / Clear」，确认清除后卡片消失，设置文件从 5,121,043 字节缩回 20,931 字节，其余字段与恢复后的设置只有 `launch_count` 差异；本次注入的样本没有被任何助手引用，归一化联动与多个超大字段、横屏布局都没有在设备上覆盖。

<a id="c146"></a>

## C146 安全模式导出与导入备份

- 落点：`SafeModeActivity` 增加「Export app data to a file」与「Import local backup file」两个描边按钮，导出走 `CreateDocument("application/zip")` 和 `BackupManager.createBackup(includeFiles = true)`（不带工作区文件），导入走 `OpenDocument`、确认框、`BackupManager.stageRestore(archive, includeFiles = true, includeWorkspaceFiles = true)`，完成后 `BackupDialog()` 提示重启；`DatabaseBackup.createFileSnapshot` 只读打开库文件后 `VACUUM main INTO` 拍快照，保留盘上 schema；`BackupManager.createDatabaseSnapshot` 先走 Room 快照，失败时回退到文件快照并记录警告。新增 `safe_mode_export_failed` / `safe_mode_import_failed` 两条字符串。来源 `2d93676e`。
- 理由：崩溃后进不去主界面时，备份页也打不开；数据库迁移失败或降级安装时 Room 打不开库，但盘上数据完整，需要绕过 Room 直接导出。导入沿用备份页的 staged restore，下次启动在 Room 打开库之前替换，当前库坏了也能恢复。
- 合并注意：安全模式导出固定 SELF 归档，包含设置、数据库与附件，不带工作区文件：工作区列表要从数据库读，放在快照前会让数据库打不开时的文件快照回退还没开始就失败，所以安全模式统一不导出工作区。导入仍接受归档里的工作区条目，归档带 workspace 时会一起恢复；正式备份页的官方出口与按工作区勾选不在这里；文件快照只读打开，不 checkpoint、不改 journal 模式；导出失败和导入失败用本地化 Toast 提示，异常细节只写日志；导入确认后只暂存，`BackupDialog` 的 Restart 会 `exitProcess(0)`，下次启动才真正替换；安全模式注入 `BackupManager` 不会提前打开 Room，数据库对象是懒打开的。上游没有给这两个入口写测试。
- 测试：`:app:testGithubDebugUnitTest` 906 条、`spotlessCheck` 通过。K60 上安装 release 后直接启动 `SafeModeActivity`，两个按钮显示。完整导入：用一个 282,506 字节、含 `workspaces/...` 条目的归档走完确认、选择、暂存与重启（该归档由不含工作区裁剪的旧构建导出，用来验证导入侧仍接受工作区条目），导入前放进 `upload/` 的标记文件在重启后消失，库回到 22 个会话、59 个节点，主界面正常；没有逐项核对工作区文件内容。文件快照回退：把库的 `PRAGMA user_version` 改成 999 让 Room 打不开，安全模式导出仍成功，生成 280,100 字节归档，`unzip -l` 里是数据库、`settings.json` 与 `upload/`，数据库 `user_version` 仍是 999、22 个会话，日志有 `Room snapshot failed, falling back to the database file`，没有工作区条目；之后用开工前的安全快照恢复库、设置、附件与工作区文件，主界面正常。

<a id="c149"></a>

## C149 设置与备份文案打磨

- 落点：第一批共改 32 条英文文案，覆盖 `backup_page_*`、`setting_files_page_*`、`setting_display_page_*`、`setting_page_*`（通用、显示、通知、网络、工具时限）、`setting_model_page_fast_model_desc` 与 `safe_mode_*`。主要改动：`chat records` 统一成 `chats/conversations`；进度 `%1$d / %2$d` 改成 `of`；导出失败、官方兼容失败、缺失库/设置等错误补上下一步动作；OCR、文件清理、网络超时、工具超时等说明去掉直译腔；`Auto Dismiss Errors` 改成句首大写的 `Auto-dismiss error cards`；Web Server 描述改成 `Allow access to RakkiHub from a web browser.`。安全模式的导出/导入 Toast 不再拼接原始异常文本，只显示本地化的可行动提示，异常细节留在日志。超大设置的大小改成两个格式参数，英文用百万、简中/繁中用「万」、日文用「万文字」。
- 理由：这批界面是用户排障与恢复数据时最容易停下来的地方，原来的“chat records”“Packing files x / y”和直接显示异常 message 都不符合动作明确、错误可行动的要求；`Auto Dismiss Errors` 也不符合句首大写的界面语气。统一术语后，后续批次可以复用同一套词。Web Server 的开关说明在本批顺带修了，Web 页面本身还没过。
- 合并注意：官方 RikkaHub 的导入导出 5 条文案保持官方品牌；`Conversation` 统一译成「对话」而不是「聊天记录」，附件仍用「附件」。`safe_mode_oversized_size` 的英文只使用 `%1$`、简中/繁中/日文使用 `%2$`，调用方必须同时传百万与万的数值，不能只传一个。安全模式 Toast 不再带 `%1$s`，改文案时不要把占位符加回去。MCP、provider、主题、搜索详情、Web 与聊天页还没过，留给后续批次。
- 测试：`uv run --project tools/i18n i18n list --missing` 为 0；`:app:compileGithubDebugKotlin`、`:app:testGithubDebugUnitTest` 910 条、`spotlessCheck`、markdownlint 通过。设备上未逐页截图核对长文案换行，排序与格式参数已用资源文件核对。

<a id="c150"></a>

## C150 MCP 与 provider 文案打磨

- 落点：stdio MCP 的 14 条说明与状态文案、Claude prompt cache 策略说明共 15 条英文重写，四份 locale 同步。主要改动：`Stopping keeps the server stopped...` 改成先讲结果再讲保留项；workspace 说明去掉大写 `READY` 与 `Only local...` 的倒装；args/env 说明改成「每行一项」并明确不做 shell 解析，command 说明改成可执行名或 rootfs 绝对路径；startup timeout 明确只限制 initialize；日志说明补成完整句；workspace conflict 文案（`setting_mcp_page_stdio_workspace_conflict`）改成可直接执行的「Another stdio server already uses this workspace」；prompt cache 保留 `usually gives more cache hits` 的说法，只把前后句连成更顺的排障说明，`cache_control` 与 `Found 5` 两个关键词保留。
- 理由：MCP 的 stdio 配置是本分支新增的能力，读者有 agent 工具经验但不一定知道 MCP 生命周期；原文案大量用结果从句与内部状态词，容易让人不知道停掉后会发生什么、参数是否经过 shell。provider 的缓存说明面向排障，保留错误关键词但把语气改成“通常命中更多”。
- 合并注意：`cache_control`、`Found 5`、`rootfs`、`stdio` 是排障时可直接搜索的词，不要翻成中文或改写；`setting_mcp_page_stdio_startup_timeout_desc` 里的 `SDK's` 在 XML 里保留 `\'` 转义，中文写成「SDK 的」而不是保留英文所有格。MCP 的 OAuth 授权提示仍是上游硬编码中文，不在本批范围；Web 与聊天页见 C151/C152。
- 测试：`:app:processGithubDebugResources`、`:app:testGithubDebugUnitTest` 910 条、`spotlessCheck`、markdownlint 通过；`i18n list --missing` 为 0。设备上未逐项打开 MCP 配置页截图核对。

<a id="c151"></a>

## C151 Web 文案打磨与主题/搜索审计

- 落点：Web 的自定义文案集中在 `message`、`input`、`markdown`、`page` 四个命名空间。本批只改 3 条英文与 2 条简中：重试次数从 `Attempt 1/3` 改成 `Attempt 1 of 3`；等待文案改成句首大写的 `Waited 2s`；未知原因从 `Unknown reason` 改成 `Reason not recorded`，简中对应「未记录原因」。简中的重试次数也补成「第 N 次尝试，共 M 次」。其余新增文案（生成失败/已中断、上下文检查点删除确认、`notset` 推理档、代码预览标题、SVG 过大、Web 标题与描述）逐条看过，保留；Mermaid 组件里的 `Empty mermaid diagram`、`Mermaid Error`、`Mermaid chart` 是硬编码英文，已登进 `locale.md` 的欠账，不在这批 locale 文案里。
- 理由：Web 的文案范围比 Android 小，很多是状态标签；`Attempt 1/3` 与 `waited` 小写在卡片里不够像完整句子，`Unknown reason` 也会让人以为系统知道原因但没显示。主题与搜索详情页的页面源码对上游没有 diff，本分支没有新增用户可见文本；之前 zh/zh-TW 的搜索详情翻译定制不在本批重写。
- 合并注意：Web 只有 zh-CN 与 en-US 两份 locale，和 Android 的四份分开维护；不要因为 Android 的 C149/C150 改了术语就把英文键直接搬过来，Web 的 status/attempt 文案在 `message.json`，不在 Android 资源里。主题/搜索页的硬编码字符串属于上游，不在本分支文案打磨范围；如果以后改了这两页，再把新增文本登记进来。
- 测试：`pnpm typecheck`、`pnpm check:markdown` 通过；没有在浏览器里逐页截图核对，也没有跑 Android 测试（本批不碰 Android 资源）。

<a id="c153"></a>

## C153 硬编码文案清理：Web Mermaid、聊天多选与 MCP OAuth

- 落点：Web 正文的 Mermaid 渲染（`mermaid-diagram.tsx`、`markdown.tsx` 的 Suspense 兜底，加载态抽成共用的 `mermaid-loading.tsx`）共五条硬编码英文移入 `web-ui/app/locales/{en-US,zh-CN}/markdown.json` 的 `mermaid` 分组（`empty`、`error`、`loading`、`chart`），中英两份同步；空图与渲染失败共用一个判断，渲染失败仍显示原始代码块。Android 侧补 8 条字符串资源并四份 locale 同步：聊天多选的 Clear selection / Select all / Confirm 与搜索框的 Clear（`chat_page_clear_selection`、`chat_page_select_all`、`chat_page_confirm_selection`、`chat_page_clear_search`），MCP OAuth 的状态与按钮（`setting_mcp_page_oauth_*` 四条）。
- 理由：`locale.md` 从 2026-11-01 起记着「附件清理与扫描的一组仍硬编码」，核对后这组已经不存在：`SettingFilesPage` 的界面文案全部走 `stringResource`（C149 覆盖了 `setting_files_page_*`），`AttachmentCleaner` 只返回结果对象。实际剩下的本分支新增硬编码是聊天多选三条、搜索框 Clear 与 Web Mermaid 五条（含 Suspense 兜底）。MCP OAuth 的提示原本是上游硬编码中文，在英文与日文界面里显示中文，按 C150 的遗留说明一并收掉。
- 合并注意：Web 只有 zh-CN 与 en-US，Mermaid 的键在 `markdown` 命名空间，不要只加 Android 一份；`aria-label` 与 Suspense 兜底也走翻译，不要留在组件里。聊天多选的三条 tooltip 用 `chat_page_` 前缀，不要复用带计数的 `setting_provider_page_select_all`。MCP OAuth 的四条 key 用 `setting_mcp_page_oauth_` 前缀，和 stdio 的 `setting_mcp_page_stdio_*` 分开；日文四条统一用「認可」，不要与「認証」混用。
- 测试：`:app:testGithubDebugUnitTest` 910 条、`spotlessCheck`、`i18n list --missing` 为 0、`pnpm typecheck`、markdownlint 通过。设备（en-US）截图核对了本批直接改到的可见界面：聊天多选的三条 tooltip、MCP 列表与 stdio 详情；搜索框 Clear 只有无障碍文案、没有可见文本可截图；设置、文件管理、备份与上下文管理面板只是顺手复核资源没有受影响，不作为本批验收。MCP OAuth 四条没有可触发的 OAuth 服务器，未做界面截图；Web Mermaid 只过了 typecheck，没有在浏览器里逐项核对。

<a id="c155"></a>

## C155 中文标点与省略号统一

- 落点：`app` 的 `values-zh`、`values-zh-rTW` 界面文案去掉句末「。」，`setting_display_page_font_size_preview` 作为字体样例保留原标点；同一条规则推到 `integrations/search`、`integrations/oauth` 与 `core/ui` 的繁简中文资源；三份非英文 locale 的进度与占位文案把 `...` 改成「…」；简中/繁中里夹在中文之间的半角 `,` `;` `:` 改成全角，`_prompt_vars` 的变量标签冒号也一并改成全角；繁中备份页的两处「導入」统一成「匯入」；日文两处变量说明的逗号改成「、」。同批修了三处：`setting_page_web_server_address_note_desc` 在简中与日文里用真实换行（aapt2 会压成空格），改成 `\n`；简中/繁中 `backup_page_importing` 从「恢复/恢復」改成「导入/匯入」；日文 `backup_page_export_official_desc` 原本误植繁中，改成日文。规则写进 `locale.md` 的「标点与句式」。
- 理由：中文界面句末不加句号、省略号用「…」；原有翻译混用 `...` 与「…」、半角与全角标点，繁中同一页还混用「導入」「匯入」，日文有一句误植繁中，地址说明的换行在编译后会被压成空格。
- 合并注意：日文句末保留「。」，日文「変数: 」的半角冒号加空格也保留；全角化只处理中文之间的标点，URL、代码标识符与英文括注不要动。`setting_provider_page_test_tool_called` 两字段之间是双空格分隔，没改。变量说明的标签冒号已全角，工具卡片这类其余标签冒号仍是半角，已记进 `locale.md` 欠账。
- 测试：`i18n list --missing` 为 0、`:app:processGithubDebugResources`、markdownlint 通过；只改字符串资源，未跑 JVM 测试。逐 key 比对过四份文件的 key 集合与格式参数（`%1$s` 等）没有变化，英文 `values` 零改动。
