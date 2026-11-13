"""Android strings.xml 翻译管理 CLI。"""

from __future__ import annotations

import argparse
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from .config import (
    Config,
    ConfigError,
    LanguageConfig,
    ModuleConfig,
    load_config,
    strings_path,
)
from .references import RESERVED_KEYS, find_referenced_keys
from .strings_xml import StringsFile
from .translator import TranslationError, Translator, error_message
from .validation import text_to_xml, validate_xml

_progress_shown = False


def split_list(value: str | None) -> list[str]:
    return [part.strip() for part in (value or "").split(",") if part.strip()]


def fail(message: str) -> None:
    clear_progress()
    print(message, file=sys.stderr)
    raise SystemExit(1)


def progress(message: str) -> None:
    global _progress_shown
    if not sys.stdout.isatty():
        return
    sys.stdout.write(f"\r\x1b[K{message}")
    sys.stdout.flush()
    _progress_shown = True


def clear_progress() -> None:
    global _progress_shown
    if not _progress_shown:
        return
    sys.stdout.write("\r\x1b[K")
    sys.stdout.flush()
    _progress_shown = False


def log(message: str) -> None:
    clear_progress()
    print(message)


def pick_module(config: Config, name: str | None) -> ModuleConfig:
    if not name:
        return config.modules[0]
    return pick_modules(config, [name])[0]


def pick_modules(config: Config, names: list[str]) -> list[ModuleConfig]:
    if not names:
        return config.modules
    known = {module.name for module in config.modules}
    unknown = [name for name in names if name not in known]
    if unknown:
        fail(f"未找到模块 {', '.join(unknown)}，可用模块：{', '.join(known)}")
    return [module for module in config.modules if module.name in names]


def pick_language(config: Config, code: str) -> LanguageConfig:
    language = next(
        (item for item in [config.source, *config.targets] if item.code == code), None
    )
    if language is None:
        fail(
            f"未找到语言 {code}，可用语言：{', '.join(item.code for item in config.targets)}"
        )
    return language


def pick_targets(config: Config, codes: list[str]) -> list[LanguageConfig]:
    if not codes:
        return config.targets
    languages = []
    for code in codes:
        language = pick_language(config, code)
        if language is config.source:
            fail(f"{code} 是源语言，不能作为翻译目标")
        languages.append(language)
    return languages


def compile_regex(pattern: str | None) -> re.Pattern | None:
    if not pattern:
        return None
    try:
        return re.compile(pattern, re.IGNORECASE)
    except re.error as error:
        fail(f"无效的正则表达式: {error}")


def positive_int(value: str, name: str) -> int:
    try:
        number = int(value)
    except ValueError:
        fail(f"--{name} 必须是正整数")
    if number < 1:
        fail(f"--{name} 必须是正整数")
    return number


def to_xml(value: str, raw: bool) -> str:
    """纯文本转义后写入；--raw 时原样写入，但必须是合法的 XML 片段。"""
    if not raw:
        return text_to_xml(value)
    error = validate_xml(value)
    if error:
        fail(error)
    return value


def relative_path(config: Config, path: Path) -> str:
    return path.relative_to(config.project_root).as_posix()


def create_translator(config: Config) -> Translator:
    if not config.api_key:
        fail("未设置 OPENAI_API_KEY（可写在 tools/i18n/.env）")
    return Translator(config)


def cmd_add(args: argparse.Namespace) -> None:
    config = load_config()
    module = pick_module(config, args.module)
    xml = to_xml(args.value, args.raw)
    source = StringsFile.load(strings_path(config, module, config.source.code))
    if not source.exists:
        fail(f"源语言文件不存在: {source.path}")
    existed = source.has(args.key)
    source.set(args.key, xml)
    source.save()
    log(
        f"{'更新' if existed else '添加'} {relative_path(config, source.path)}: {args.key} = {xml}"
    )

    if args.skip_translate:
        if existed:
            log("注意：已跳过翻译，其他语言仍是旧译文")
        return

    concurrency = positive_int(args.concurrency, "concurrency")
    retries = positive_int(args.retries, "retries")
    failed = 0
    with (
        create_translator(config) as translator,
        ThreadPoolExecutor(max_workers=concurrency) as executor,
    ):
        for language in config.targets:
            result = translator.translate_entries(
                {args.key: xml}, language, executor=executor, retries=retries
            )
            translated = result.translations.get(args.key)
            if translated is None:
                print(
                    f"  ✗ {language.code}: {result.failures.get(args.key)}",
                    file=sys.stderr,
                )
                failed += 1
                continue
            target = StringsFile.load(strings_path(config, module, language.code))
            target.set(args.key, translated)
            target.save()
            log(f"  ✓ {language.code}: {translated}")
    if failed:
        fail(f"{failed} 种语言翻译失败，可运行 translate-missing 补全")


def cmd_set(args: argparse.Namespace) -> None:
    config = load_config()
    module = pick_module(config, args.module)
    language = pick_language(config, args.lang or config.source.code)
    xml = to_xml(args.value, args.raw)

    if language is not config.source and not StringsFile.load(
        strings_path(config, module, config.source.code)
    ).has(args.key):
        log(f"注意：源语言中没有 {args.key}")
    target = StringsFile.load(strings_path(config, module, language.code))
    target.set(args.key, xml)
    target.save()
    log(f"✓ {relative_path(config, target.path)}: {args.key} = {xml}")


def cmd_remove(args: argparse.Namespace) -> None:
    config = load_config()
    module = pick_module(config, args.module)
    keys = list(args.keys)
    removed = {key: [] for key in keys}
    for language in [config.source, *config.targets]:
        target = StringsFile.load(strings_path(config, module, language.code))
        for key in keys:
            if target.delete(key):
                removed[key].append(language.code)
        target.save()
    missing = 0
    for key, languages in removed.items():
        if not languages:
            print(f"✗ {module.name} 中没有 {key}", file=sys.stderr)
            missing += 1
        else:
            log(f"✓ 已删除 {key}（{len(languages)} 个语言文件）")
    if missing:
        raise SystemExit(1)


def cmd_list(args: argparse.Namespace) -> None:
    config = load_config()
    module = pick_module(config, args.module)
    regex = compile_regex(args.match)
    source = StringsFile.load(strings_path(config, module, config.source.code))
    targets = [
        (language, StringsFile.load(strings_path(config, module, language.code)))
        for language in config.targets
    ]
    translatable = source.translatable_entries()

    shown = 0
    entries = source.entries()
    for key, value in entries.items():
        if regex and not regex.search(key) and not regex.search(value):
            continue
        missing = (
            [language.code for language, target in targets if not target.has(key)]
            if key in translatable
            else []
        )
        if args.missing and not missing:
            continue
        shown += 1
        preview = value if len(value) <= 60 else f"{value[:57]}..."
        log(f"{key:<40} {preview}")
        if missing:
            log(f"{'':<40} 缺少: {', '.join(missing)}")
    log(f"\n{module.name}: 显示 {shown} / {len(entries)} 条")


def run_batch(
    config: Config,
    args: argparse.Namespace,
    action: str,
    select,
) -> None:
    modules = pick_modules(config, split_list(args.module))
    languages = pick_targets(config, split_list(args.lang))
    concurrency = positive_int(args.concurrency, "concurrency")
    retries = positive_int(args.retries, "retries")

    jobs: list[tuple[str, LanguageConfig, StringsFile, dict[str, str]]] = []
    for module in modules:
        source = StringsFile.load(strings_path(config, module, config.source.code))
        if not source.exists:
            continue
        entries = source.translatable_entries()
        for language in languages:
            target = StringsFile.load(strings_path(config, module, language.code))
            selected = select(entries, target)
            if not selected:
                continue
            label = f"[{module.name}/{language.code}]"
            log(f"{label} {action} {len(selected)} 条")
            jobs.append((label, language, target, selected))
    if not jobs:
        log("没有需要翻译的条目")
        return
    if args.dry_run:
        return

    total = sum(len(job[3]) for job in jobs)
    done = 0
    failures: list[str] = []

    def on_batch_done(count: int) -> None:
        nonlocal done
        done += count
        progress(f"翻译中 {done}/{total}")

    with (
        create_translator(config) as translator,
        ThreadPoolExecutor(max_workers=concurrency) as executor,
    ):
        for label, language, target, selected in jobs:
            result = translator.translate_entries(
                selected,
                language,
                executor=executor,
                retries=retries,
                on_batch_done=on_batch_done,
            )
            for key, value in result.translations.items():
                target.set(key, value)
            target.save()
            log(f"{label} ✓ 写入 {len(result.translations)}/{len(selected)} 条")
            failures.extend(
                f"  {label} {key}: {error}" for key, error in result.failures.items()
            )

    if failures:
        print(f"\n{len(failures)} 条翻译失败（未写入，可重新运行）：", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        raise SystemExit(1)
    log("完成！")


def cmd_translate_missing(args: argparse.Namespace) -> None:
    config = load_config()
    run_batch(
        config,
        args,
        "缺失",
        lambda entries, target: {
            key: value for key, value in entries.items() if not target.has(key)
        },
    )


def cmd_retranslate(args: argparse.Namespace) -> None:
    config = load_config()
    regex = compile_regex(args.match)
    keys = set(split_list(args.key))
    if not regex and not keys:
        fail("请至少指定 --match 或 --key")

    def select(entries: dict[str, str], target: StringsFile) -> dict[str, str]:
        return {
            key: value
            for key, value in entries.items()
            if target.has(key)
            and (key in keys or (regex is not None and regex.search(value)))
        }

    run_batch(config, args, "重新翻译", select)


def cmd_unused(args: argparse.Namespace) -> None:
    config = load_config()
    if not config.source_patterns:
        fail("config.yml 中未配置 source_patterns")
    referenced = find_referenced_keys(config.project_root, config.source_patterns)
    total = 0

    for module in pick_modules(config, split_list(args.module)):
        source = StringsFile.load(strings_path(config, module, config.source.code))
        if not source.exists:
            continue
        targets = [
            StringsFile.load(strings_path(config, module, language.code))
            for language in config.targets
        ]
        unused_keys = [
            key
            for key in source.entries()
            if key not in referenced and key not in RESERVED_KEYS
        ]
        if unused_keys:
            log(f"[{module.name}] 未引用 {len(unused_keys)} 条:")
            for key in unused_keys:
                log(f"  {key}")
        # 只存在于译文中、源语言已没有的条目
        for target in targets:
            orphans = [key for key in target.entries() if not source.has(key)]
            if not orphans:
                continue
            log(
                f"[{module.name}] {relative_path(config, target.path)} 多余 {len(orphans)} 条:"
            )
            for key in orphans:
                log(f"  {key}")
            if args.delete:
                for key in orphans:
                    target.delete(key)
            total += len(orphans)
        total += len(unused_keys)
        if args.delete:
            for file in [source, *targets]:
                for key in unused_keys:
                    file.delete(key)
                file.save()

    if total == 0:
        log("没有发现未使用的条目")
    elif args.delete:
        log(f"✓ 已删除 {total} 条")
    else:
        log(
            f"\n共 {total} 条，加 --delete 删除（动态拼接 key 引用的条目也会被列出，删除前请确认）"
        )


def cmd_test_connection(args: argparse.Namespace) -> None:
    config = load_config()
    log(f"Base URL: {config.base_url}")
    log(f"Model: {config.translation.model}")
    try:
        with create_translator(config) as translator:
            reply = translator.test_connection()
    except Exception as error:  # noqa: BLE001 - 网络错误统一报告
        fail(f"✗ 连接失败: {error_message(error)}")
    log(f"✓ 连接成功{('，响应: ' + reply) if reply else ''}")


class _ArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        self.print_usage(sys.stderr)
        fail(message)


def build_parser() -> argparse.ArgumentParser:
    parser = _ArgumentParser(prog="i18n", description="Android strings.xml 翻译管理")
    sub = parser.add_subparsers(dest="command", required=True)

    add = sub.add_parser("add", help="添加/更新英文条目并翻译到所有语言")
    add.add_argument("key", help="条目 key")
    add.add_argument("value", help="英文原文")
    add.add_argument("-m", "--module", help="模块名（默认第一个模块）")
    add.add_argument("--skip-translate", action="store_true", help="只写入英文，不翻译")
    add.add_argument(
        "--raw", action="store_true", help="按原始 XML 写入，不转义 & < > 和引号"
    )
    add.add_argument("-c", "--concurrency", default="8", help="并发请求数")
    add.add_argument("--retries", default="3", help="每批最大尝试次数")
    add.set_defaults(func=cmd_add)

    set_cmd = sub.add_parser("set", help="手动设置某个语言的条目值")
    set_cmd.add_argument("key")
    set_cmd.add_argument("value")
    set_cmd.add_argument("-l", "--lang", help="语言代码（默认源语言）")
    set_cmd.add_argument("-m", "--module", help="模块名")
    set_cmd.add_argument("--raw", action="store_true", help="按原始 XML 写入")
    set_cmd.set_defaults(func=cmd_set)

    remove = sub.add_parser("remove", help="从所有语言中删除条目")
    remove.add_argument("keys", nargs="+", help="要删除的 key，可以有多个")
    remove.add_argument("-m", "--module", help="模块名")
    remove.set_defaults(func=cmd_remove)

    list_cmd = sub.add_parser("list", help="列出条目")
    list_cmd.add_argument("-m", "--module", help="模块名")
    list_cmd.add_argument("--missing", action="store_true", help="只显示缺少译文的条目")
    list_cmd.add_argument("--match", help="按 key 或原文过滤（忽略大小写）")
    list_cmd.set_defaults(func=cmd_list)

    def add_batch_args(command: argparse.ArgumentParser) -> None:
        command.add_argument("-m", "--module", help="模块名，逗号分隔（默认全部）")
        command.add_argument(
            "-l", "--lang", help="目标语言代码，逗号分隔（默认所有目标语言）"
        )
        command.add_argument("-c", "--concurrency", default="8", help="并发请求数")
        command.add_argument("--retries", default="3", help="每批最大尝试次数")
        command.add_argument(
            "--dry-run", action="store_true", help="只统计待翻译条目，不调用翻译"
        )

    translate_missing = sub.add_parser("translate-missing", help="批量翻译缺失的条目")
    add_batch_args(translate_missing)
    translate_missing.set_defaults(func=cmd_translate_missing)

    retranslate = sub.add_parser(
        "retranslate", help="重新翻译已有条目（例如更新术语表后）"
    )
    add_batch_args(retranslate)
    retranslate.add_argument("--match", help="按英文原文选择条目（忽略大小写）")
    retranslate.add_argument(
        "--key", help="按 key 选择条目，逗号分隔，与 --match 取并集"
    )
    retranslate.set_defaults(func=cmd_retranslate)

    unused = sub.add_parser(
        "unused", help="检测代码中未引用的条目和只存在于译文中的条目"
    )
    unused.add_argument("-m", "--module", help="模块名，逗号分隔（默认全部）")
    unused.add_argument("--delete", action="store_true", help="删除检测到的条目")
    unused.set_defaults(func=cmd_unused)

    test_connection = sub.add_parser("test-connection", help="测试 AI 服务连接")
    test_connection.set_defaults(func=cmd_test_connection)

    return parser


def main(argv: list[str] | None = None) -> None:
    args = build_parser().parse_args(argv)
    try:
        args.func(args)
    except (ConfigError, TranslationError) as error:
        fail(str(error))
    except SystemExit:
        raise
    except Exception as error:  # noqa: BLE001 - CLI 统一报告
        fail(error_message(error))
