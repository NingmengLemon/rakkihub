from __future__ import annotations

from i18n.config import LanguageConfig, load_config
from i18n.translator import Translator


def test_glossary_is_appended_to_the_prompt() -> None:
    config = load_config()
    translator = Translator(config)
    entries = {"system_prompt": "System Prompt"}
    languages = {language.code: language for language in config.targets}

    simplified = translator.build_prompt(entries, languages["values-zh"])
    assert "Glossary for Chinese (Simplified)" in simplified
    assert "提示词" in simplified
    assert '"system_prompt": "System Prompt"' in simplified

    assert "Glossary for Japanese" in translator.build_prompt(
        entries, languages["values-ja"]
    )


def test_languages_without_a_glossary_get_no_glossary_section() -> None:
    translator = Translator(load_config())
    entries = {"system_prompt": "System Prompt"}
    no_glossary = LanguageConfig(
        code="values-test", name="Test", is_source=False, glossary={}
    )

    assert "Glossary" not in translator.build_prompt(entries, no_glossary)
