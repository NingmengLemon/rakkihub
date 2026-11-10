# Repository Guidelines

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material 3 Expressive (MD3 Expressive) design language.

## Design Language

The UI follows Material 3 Expressive. New and reworked screens should match it rather than the older baseline
Material 3 look (cards with dividers, plain forms).

- The app theme is `MaterialExpressiveTheme` (app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt); prefer the
  expressive variants of components, shapes (`MaterialShapes`), emphasized typography (e.g. `titleSmallEmphasized`)
  and motion from `androidx.compose.material3` over hand-rolled equivalents.
- Settings-style and form pages use segmented groups via `CardGroup` (
  app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt) instead of a card with dividers.
- Favor larger corner radii, filled/tonal containers, and whole-row click targets.

## Build, Test, and Development Commands

```bash
./gradlew assembleGithubDebug    # 构建 GitHub 渠道的 Debug APK
./gradlew assembleGithubRelease  # 构建 GitHub Release / 每日构建用的 APK
./gradlew bundlePlayRelease      # 构建上架 Google Play 的 AAB
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
./gradlew spotlessApply          # 用 ktlint 格式化相对 origin/main 改动过的 Kotlin 文件
./gradlew spotlessCheck          # 只检查格式不改文件
```

App 有一个 `distribution` 维度的 product flavor：`github`（默认）和 `play`，共用同一个 applicationId。
`BuildConfig.IS_PLAY_STORE` 仍在，但本分支移除了更新检查（C15），目前没有代码读这个字段。

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **material3**: Material color utility extensions used by the app UI
- **ui**: Reusable Compose UI components that do not depend on app logic
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Only the serialized `TTSProviderSetting` and `ASRProviderSetting` data classes remain (kept so persisted
  settings still decode). The TTS and ASR runtime implementations were removed on this branch, see C5 in
  `docs/custom/checklist.md`.
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.
- **terminal**: Terminal emulator core taken from termux-app's `terminal-emulator` (package `com.termux.terminal`, kept
  binary compatible with the `terminal-view` aar). `TerminalSession` runs over a `TerminalTransport`: local PRoot
  terminals use `LocalPtyTransport`, SSH workspaces use the app's `SshTerminalTransport`. Origin and local changes are
  listed in terminal/README.md.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings.
  Once a conversation is persisted it also holds a `ConversationConfig` snapshot (chat model, reasoning level, search,
  MCP servers, workspace, skills) taken from the assistant; from then on chat-page changes to those settings stay on
  the conversation, and code should read them through `Settings.getAssistantOf(conversation)` /
  `Settings.getChatModelOf(conversation)` instead of the assistant directly. Once loaded, the in-memory
  `ConversationSession` is the single source of truth: change a conversation through `ChatService` (which applies
  `session.update { latest -> ... }` and then `flush()`), never by saving a whole `Conversation` snapshot or one read
  back from the database; deleting also goes through `ChatService` so a running generation cannot re-insert it. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt,
  app/src/main/java/me/rerere/rikkahub/data/model/ConversationConfig.kt,
  app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- Add, translate or clean up strings with the `tools/i18n` CLI (Bun); see the `i18n-localization` skill.

## Custom branch

This repository carries personal customizations on top of upstream `rikkahub/rikkahub`.

- Work on `main`. `ref` only follows upstream and must stay fast-forwardable; never commit to it.
- Read `docs/custom/README.md` before adding or removing a feature. It holds the branch model, the upstream sync
  procedure, and the customization principles. `docs/custom/checklist.md` is the per-customization register,
  `docs/custom/roadmap.md` the long-term plan (stage split, easy-migration target, dependency index of the
  customizations, independent-update direction, distribution/compliance), `docs/custom/backup/` the backup
  import/export chain with its acceptance procedure, and `docs/custom/build.md` / `docs/custom/locale.md` the build
  and string-resource operations.
- Tag customization commits with the `custom` scope, e.g. `feat(custom): ...`, and add the item to
  `docs/custom/checklist.md` (an index row plus a detail section) so a later upstream port can be checked against it.
- The persisted-data freeze is lifted: the official-backup importer exists, and the criteria verified on the real samples
  are v32/v31 tables and converted output plus v32 attachments and settings. The freeze was about official backups
  coming in, and that path works. The criteria that are untested or only partially tested are listed class by class in
  `docs/custom/backup/acceptance.md`. Room schema changes and `Settings` structure changes are allowed. What still
  holds is backwards compatibility with our own past builds: a Room change ships with its migration, new serialized
  fields carry a default, existing field and DataStore key meanings do not change, and the acceptance samples are
  re-run afterwards. Changing a meaning would first need a field-mapping table, which does not exist yet. The
  data-structure section of `docs/custom/README.md` lists the constraints.
- A Room schema change also makes the official-compatible export fail until its trimming logic exists (see C18). That
  failure is deliberate, not a bug.
- The branch is on independent updates: upstream is read, not merged. Read its commits, port feature or bugfix ideas
  by hand, and keep the C3 deletion in `ai/util/Request.kt` whenever upstream changes to that file are ported. The
  module reference table and the porting procedure live in the independent-update section of
  `docs/custom/roadmap.md`.
- The merge-cost concessions are gone now that the branch is on independent updates: inside `app/`, delete
  implementations outright instead of leaving entry-point shells, and edit the hot files freely. Keep changes small
  inside `ai/`, which is still read against upstream (C3's deletion, C52's standard cache breakpoints, C53's per-provider cache strategy, C59's terminal-event guard, C61's typed failures/retry policy, C62's stream idle watchdog, C63's context-overflow shrink hook, C65's tool-error summary/timeout, C67's `isGenerating` recovery flag, C68's `withoutCompressionScratch`, and the C47/C51/C58 `ai/ui/Message.kt` additions are the behavior changes there; C35's `filterByKeywords` matches upstream).
- Before pushing a batch of customizations, run an independent review: dispatch a fresh-context `subagent` (not
  `subagent_fork`) and have it load the `code-review` skill, reviewing `origin/main..main` (the batch; use `ref..main`
  to audit every customization). Fix what it finds and re-run until it passes; only then `git push origin main`.
