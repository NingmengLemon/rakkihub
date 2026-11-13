<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>RakkiHub</h1>

  [RikkaHub](https://github.com/rikkahub/rikkahub) 的个人 fork，自用。
</div>

> [!WARNING]
> 这是个人 fork，不是官方版本，与官方没有任何关系。代码按个人需要改过、删过功能，也不保证跟上上游。
>
> 请不要把它当官方客户端使用。需要稳定版本请去[官方仓库](https://github.com/rikkahub/rikkahub)或[官网](https://rikka-ai.com/)下载。从非官方渠道安装的 APK 可能有隐私泄露与过度申请权限的风险，使用前请自行判断。

## 这个仓库是什么

- 官方项目是 [RikkaHub](https://github.com/rikkahub/rikkahub)，一个 Android LLM 聊天客户端。
- 本仓库是它的个人定制分支，只为自己使用：不接 PR，也不提供支持。
- 分支沿用上游的每日构建 workflow，GitHub 上有一个固定 tag 的 `nightly` prerelease，内容是按当前代码自动构建的 APK。它没有经过发布检查，不保证可用，也不是正式版本。
- 应用名是 RakkiHub，包名 `top.lemoneko.rakkihub`（debug 变体为 `top.lemoneko.rakkihub.debug`），可以和官方版本并存安装。两边数据互不相通，迁移靠备份的导出与导入。

## 定制记录

逐项改动、理由与移植注意事项在 [`docs/custom/checklist.md`](docs/custom/checklist.md)。分支怎么跟上游走、数据层的约束见 [`docs/custom/README.md`](docs/custom/README.md)，构建方式见 [`docs/custom/build.md`](docs/custom/build.md)，备份的导入导出与验收见 [`docs/custom/backup/`](docs/custom/backup/)。

## 官方仓库

<https://github.com/rikkahub/rikkahub>

本仓库不镜像官方的最新状态，也不代表官方行为。遇到问题时先对照官方版本，判断是不是 fork 引入的。

## 许可证

本仓库是官方项目的衍生作品，按 [GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）授权，修改过的源码都在本仓库里。`LICENSE` 保持上游原文不动，GitHub 才能识别出许可证类型；本仓库修改部分的版权归 NingmengLemon。
