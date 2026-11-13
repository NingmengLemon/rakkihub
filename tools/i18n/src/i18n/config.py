"""配置加载：config.yml、.env 与环境变量。"""

from __future__ import annotations

import os
import re
from dataclasses import dataclass, field
from pathlib import Path

import yaml

TOOL_DIR = Path(__file__).resolve().parents[2]
DEFAULT_BASE_URL = "https://api.openai.com/v1"

_ENV_LINE = re.compile(r"^\s*(?:export\s+)?([\w.]+)\s*=\s*(.*?)\s*$")


class ConfigError(RuntimeError):
    """配置文件不合法。"""


@dataclass(frozen=True)
class ModuleConfig:
    name: str
    res_path: str


@dataclass(frozen=True)
class LanguageConfig:
    code: str
    name: str
    is_source: bool = False
    glossary: dict[str, str] = field(default_factory=dict)
    # 原文里出现这些英文术语时，译文必须原样保留
    protected_terms: tuple[str, ...] = ()


@dataclass(frozen=True)
class TranslationConfig:
    model: str
    batch_size: int = 10
    temperature: float | None = None
    structured_outputs: bool = False
    prompt_template: str = ""


@dataclass(frozen=True)
class Config:
    project_root: Path
    modules: list[ModuleConfig]
    source_patterns: list[str]
    source: LanguageConfig
    targets: list[LanguageConfig]
    translation: TranslationConfig
    api_key: str
    base_url: str


def _load_dotenv(path: Path) -> None:
    """只填充尚未设置的变量，不覆盖已有的环境变量。"""
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        match = _ENV_LINE.match(line)
        if not match:
            continue
        key, raw = match.group(1), match.group(2)
        if key in os.environ:
            continue
        if len(raw) >= 2 and raw[0] == raw[-1] and raw[0] in "'\"":
            raw = raw[1:-1]
        os.environ[key] = raw


def _require(mapping: object, key: str, where: str) -> object:
    if not isinstance(mapping, dict) or key not in mapping:
        raise ConfigError(f"{where} 缺少 {key}")
    return mapping[key]


def load_config(path: Path | None = None) -> Config:
    _load_dotenv(TOOL_DIR / ".env")
    raw = yaml.safe_load((path or TOOL_DIR / "config.yml").read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ConfigError("config.yml 顶层必须是映射")

    modules = [
        ModuleConfig(name=str(m["name"]), res_path=str(m["res_path"]))
        for m in _require(raw, "modules", "config.yml") or []
    ]
    if not modules:
        raise ConfigError("modules 不能为空")

    languages: list[LanguageConfig] = []
    for item in _require(raw, "languages", "config.yml") or []:
        glossary = item.get("glossary") or {}
        languages.append(
            LanguageConfig(
                code=str(item["code"]),
                name=str(item["name"]),
                is_source=bool(item.get("is_source", False)),
                glossary={str(k): str(v) for k, v in glossary.items()},
                protected_terms=tuple(
                    str(term) for term in item.get("protected_terms") or ()
                ),
            )
        )
    if not languages:
        raise ConfigError("languages 不能为空")

    sources = [language for language in languages if language.is_source]
    if len(sources) != 1:
        raise ConfigError(
            f"languages 中必须有且只有一个 is_source 语言，当前 {len(sources)} 个"
        )

    translation_raw = _require(raw, "translation", "config.yml")
    batch_size = int(translation_raw.get("batch_size", 10))
    if batch_size < 1:
        raise ConfigError("translation.batch_size 必须是正整数")
    temperature = translation_raw.get("temperature")
    translation = TranslationConfig(
        model=str(_require(translation_raw, "model", "translation")),
        batch_size=batch_size,
        temperature=float(temperature) if temperature is not None else None,
        structured_outputs=bool(translation_raw.get("structured_outputs", False)),
        prompt_template=str(
            _require(translation_raw, "prompt_template", "translation")
        ),
    )

    return Config(
        project_root=(TOOL_DIR / str(raw.get("project_root", "../.."))).resolve(),
        modules=modules,
        source_patterns=[str(p) for p in raw.get("source_patterns") or []],
        source=sources[0],
        targets=[language for language in languages if not language.is_source],
        translation=translation,
        api_key=os.environ.get("OPENAI_API_KEY", ""),
        base_url=os.environ.get("OPENAI_BASE_URL") or DEFAULT_BASE_URL,
    )


def strings_path(config: Config, module: ModuleConfig, lang_code: str) -> Path:
    return config.project_root / module.res_path / lang_code / "strings.xml"
