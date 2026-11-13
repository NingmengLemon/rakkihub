"""调用真实 API 检查翻译质量，默认跳过。

运行: I18N_LIVE=1 uv run --project tools/i18n --group dev pytest tools/i18n/tests/test_translator_live.py
"""

from __future__ import annotations

import os

import pytest
from i18n.config import load_config
from i18n.translator import Translator

pytestmark = pytest.mark.skipif(
    not os.environ.get("I18N_LIVE"), reason="需要 I18N_LIVE=1 和真实 API"
)


def translate(entries: dict[str, str]) -> dict[str, str]:
    config = load_config()
    chinese = next(
        language for language in config.targets if language.code == "values-zh"
    )
    return Translator(config).translate_batch(entries, chinese)


def test_prompt_is_translated_as_提示词() -> None:
    result = translate(
        {"system_prompt": "System Prompt", "prompt_template": "Prompt Template"}
    )
    assert "提示词" in result["system_prompt"]
    assert "提示词" in result["prompt_template"]


def test_placeholders_and_brand_names_are_preserved() -> None:
    result = translate(
        {
            "welcome_user": "Welcome, %1$s!",
            "items_count": "You have %d items",
            "provider_openai": "OpenAI Provider",
        }
    )
    assert "%1$s" in result["welcome_user"]
    assert "%d" in result["items_count"]
    assert "OpenAI" in result["provider_openai"]


def test_response_keys_match_the_input() -> None:
    entries = {
        "setting_title": "Settings",
        "cancel_button": "Cancel",
        "confirm_button": "Confirm",
    }
    result = translate(entries)
    assert sorted(result) == sorted(entries)
