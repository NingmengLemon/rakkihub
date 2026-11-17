# 明细：平台、构建与品牌

本文件是 [checklist.md](../checklist.md) 的明细，收录 C7、C12、C37 至 C39、C45、C48、C55 至 C56、C90 至 C94、C101。

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

- 落点：根目录 `.markdownlint-cli2.jsonc`，范围 `docs/**/*.md`、`AGENTS.md`、`README.md`、`terminal/README.md`，忽略 `.agents/`、构建目录和 `node_modules`，关掉 `MD013`/`MD033`/`MD041`/`MD060`；`docs/custom/build.md` 加「文档检查」；`docs/references/chat-generation-pipeline.md` 修列表缩进和代码块语言；`docs/custom/backup/README.md` 补代码块语言。
- 理由：文档一直没有 lint 入口，列表缩进、代码块语言这类结构问题只能靠平时看；用本机已装的 markdownlint-cli2，配置适配仓库的长行、README 的居中 HTML 和紧凑表格。
- 合并注意：这是本地检查，CI 没有跑；规则列表在配置和 `build.md` 两处，改规则时同步。MD060 关掉是因为参考文档的 CJK 表格按字符宽度对齐，默认风格过不了，别按 ASCII 宽度重排；其余结构规则保持开启。markdownlint-cli2 不在仓库依赖里，换机器要自己装。
- 测试：仓库根目录 `markdownlint-cli2` 14 个文件 0 error；参考文档和备份文档的修改只动空白与代码块语言，内容未变。

<a id="c90"></a>

## C90 应用图标与可见文案改名 RakkiHub

- 落点：`app/src/main/res/mipmap-*/` 的 `ic_launcher.png`、`ic_launcher_foreground.png`、`ic_launcher_background.png` 全部换成关于页那张插画（`drawable-nodpi/about_logo.webp`）生成的图；`mipmap-anydpi-v26/ic_launcher.xml` 去掉 `<monochrome>`，删除五个 `ic_launcher_monochrome.png`；`docs/icon.png` 与 README 用的 `docs/readme-icon.png` 同步；可见文案：`SettingAboutPage.kt` 标题改用 `stringResource(R.string.app_name)`，`NsdServiceRegistrar.kt` 的 `DEFAULT_SERVICE_NAME` 改成 `rakkihub`（mDNS 地址变成 `rakkihub.local`）与 NSD 描述，`SettingPreferencesNetworkPage.kt` 的默认 User-Agent，`ContextUtil.kt` 的导出文件名，`McpOAuthCoordinator.kt` 的 OAuth clientName，`oauth` 模块 `OAuthLoopbackCallbackServer.kt` 的回调页标题、品牌与四条提示，`DataSourceModule.kt`、`ai/util/Request.kt`、`mediagen` 的 `X-Title`（`X-Title` 与 `HTTP-Referer` 已由 C96 删除），`RootfsPatcher.kt` 的生成标记
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

- 落点：`web-ui/public/favicon.ico` 重做成四帧（48/64/128/256，全部用插画，标签页那枚 16px 由浏览器从 48 帧缩下去）；新增 `web-ui/public/icon.png`（256，与 `docs/icon.png` 同一构图）；`web-ui/app/components/logo.tsx` 从内联的旧兔子线稿 SVG 改成渲染 `/icon.png`（`alt` 用品牌名，宽高属性是 256，显示尺寸由调用方的 `className` 决定），`web-ui/app/routes/conversations.tsx` 的新会话空状态去掉 `[&>svg]:size-16` 包裹与 `text-primary`；删除整个 `docs/img/`（海报 `poster.png` 与五张没有引用的界面截图，共约 4.2 MB）；生成脚本移到 `tools/gen-icons.py`，一次覆盖 launcher 图标、`docs/icon.png`、`docs/readme-icon.png`、`web-ui/public/icon.png` 与 favicon（在仓库根目录运行 `uv run --with pillow python -P tools/gen-icons.py`，Pillow 由 uv 临时安装；`build/` 被 gitignore，脚本放那里不可复现）
- 理由：C90 只换了 launcher 与 README 图标，浏览器标签页、Web UI 新会话空状态、海报三处还是旧兔子；本分支对 `docs/img/` 没有任何引用（根 README 是 fork 说明，见 C39），五张截图连上游 README 都没用，留着只是几 MB 的旧素材
- 合并注意：favicon 统一用插画，不再为 16px 单独准备兔子头剪影：16px 标签页是 48 帧缩下去的，糊一点但只有一套形象；`web-ui/public/` 由 `web` 模块 preBuild 的 `pnpm run build` 拷进 `web/src/main/resources/static`（该目录 gitignored），只改 `web-ui/public` 而不重新构建 APK 不会生效；`docs/icon.png` 保持 512（通用），`docs/readme-icon.png` 也取 512 但按 adaptive 72/108 安全区裁切，README 用它显示 launcher 的真实裁切效果；`web-ui/public/icon.png` 取 256，够 64px 显示的四倍屏；通知栏小图标 `ic_stat_rikkahub` 是有意保留的兔子头剪影（见 C90）
- 测试：四个尺寸逐帧导出核对；`pnpm run typecheck` 通过，oxfmt 对改动的两个文件通过（整棵 `web-ui/app` 在 oxfmt 默认配置下另有 64 个既有格式差异，与本次无关，CI 也不跑这项）；`:app:assembleGithubDebug` 通过，解包 APK 后 `static/favicon.ico`（72560 字节）与 `static/icon.png`（43496 字节）和 `web-ui/public` 下的文件哈希一致，打包产物里能看到 `<img src="/icon.png" alt="RakkiHub">`；用本地静态服务器把构建产物打开，新会话空状态显示插画图标，`/favicon.ico` 与 `/icon.png` 都返回 200。设备上的 Web 服务器开关要在设置里手动打开，本次没开，所以没在真机浏览器里核对标签页图标

<a id="c101"></a>

## C101 README 图标按 launcher 安全区裁切

- 落点：`tools/gen-icons.py` 增加 `readme_icon()`，按 adaptive `72/108` 安全区裁切并保留同一圆角；`docs/readme-icon.png`（512）由脚本生成，`README.md` 改引用它；`docs/icon.png`、`web-ui/public/icon.png`、favicon 和 launcher 各密度保持不变。
- 理由：README 原来直接用完整 108dp 画布的 `docs/icon.png`，插画只占 78%，在实际 launcher 的安全区裁切后视觉上偏小；单独生成安全区版本，不改变桌面和 Web 资源。
- 合并注意：改插画参数后要重跑 `uv run --with pillow python -P tools/gen-icons.py`，三个图标和 README 图标一起更新；`docs/readme-icon.png` 是生成物，不要手工改。
- 测试：`docs/readme-icon.png` 为 512x512 RGBA，和脚本重跑结果一致；README 引用路径存在；launcher、Web、favicon 无 diff。
