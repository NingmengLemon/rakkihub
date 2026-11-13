from __future__ import annotations

from i18n.config import load_config


def test_protected_terms_are_per_language() -> None:
    config = load_config()
    languages = {language.code: language for language in config.targets}
    assert languages["values-zh"].protected_terms == ("Token",)
    assert languages["values-zh-rTW"].protected_terms == ("Token",)
    # 日文用トークン，不强制保留 Token
    assert languages["values-ja"].protected_terms == ()
