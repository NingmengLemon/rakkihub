"""OpenAI 兼容接口的批量翻译。"""

from __future__ import annotations

import json
import re
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from typing import Self

import httpx

from .config import Config, LanguageConfig
from .validation import normalize_translation, validate_translation

JSON_FENCE = re.compile(r"^```(?:json)?\s*(.*?)\s*```$", re.DOTALL)


class TranslationError(RuntimeError):
    """翻译请求失败。"""


@dataclass
class TranslateResult:
    translations: dict[str, str] = field(default_factory=dict)
    # key -> 最后一次的错误信息
    failures: dict[str, str] = field(default_factory=dict)


def error_message(error: BaseException) -> str:
    text = str(error).splitlines()[0] if str(error) else type(error).__name__
    return text


def _message_content(data: dict) -> str:
    try:
        return str(data["choices"][0]["message"]["content"])
    except (KeyError, IndexError, TypeError) as error:
        raise TranslationError("模型响应缺少 choices[0].message.content") from error


def parse_json_object(content: str) -> dict[str, str]:
    text = content.strip()
    fence = JSON_FENCE.match(text)
    if fence:
        text = fence.group(1)
    try:
        data = json.loads(text)
    except json.JSONDecodeError as error:
        raise TranslationError(f"模型返回的不是 JSON: {error.msg}") from error
    if not isinstance(data, dict):
        raise TranslationError("模型返回的 JSON 不是对象")
    return {str(key): value for key, value in data.items() if isinstance(value, str)}


class Translator:
    def __init__(self, config: Config):
        self.config = config
        self._client = httpx.Client(timeout=httpx.Timeout(120.0, connect=30.0))

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> Self:
        return self

    def __exit__(self, *exc_info: object) -> None:
        self.close()

    def build_prompt(self, entries: dict[str, str], language: LanguageConfig) -> str:
        prompt = self.config.translation.prompt_template.replace(
            "{target_language}", language.name
        ).replace("{source_strings}", json.dumps(entries, ensure_ascii=False, indent=2))
        terms = list(language.glossary.items())
        if terms:
            prompt += (
                f"\n\nGlossary for {language.name} (mandatory, use these renderings "
                "consistently, inflecting for grammar where needed):\n"
                + "\n".join(
                    f"- {english} -> {translated}" for english, translated in terms
                )
            )
        return prompt

    def _request(self, payload: dict) -> dict:
        response = self._client.post(
            f"{self.config.base_url.rstrip('/')}/chat/completions",
            headers={
                "Authorization": f"Bearer {self.config.api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
        )
        if response.status_code >= 400:
            raise TranslationError(
                f"HTTP {response.status_code}: {response.text[:300]}"
            )
        return response.json()

    def translate_batch(
        self, entries: dict[str, str], language: LanguageConfig
    ) -> dict[str, str]:
        """单次请求翻译一批条目，返回模型给出的原始结果。"""
        payload: dict = {
            "model": self.config.translation.model,
            "messages": [
                {"role": "user", "content": self.build_prompt(entries, language)}
            ],
        }
        if self.config.translation.temperature is not None:
            payload["temperature"] = self.config.translation.temperature
        if self.config.translation.structured_outputs:
            payload["response_format"] = {
                "type": "json_schema",
                "json_schema": {
                    "name": "translations",
                    "strict": True,
                    "schema": {
                        "type": "object",
                        "additionalProperties": {"type": "string"},
                    },
                },
            }
        else:
            payload["response_format"] = {"type": "json_object"}
        data = self._request(payload)
        return parse_json_object(_message_content(data))

    def test_connection(self) -> str:
        data = self._request(
            {
                "model": self.config.translation.model,
                "messages": [
                    {
                        "role": "user",
                        "content": "Reply exactly with OK to confirm the connection.",
                    }
                ],
                "temperature": 0,
                "max_tokens": 16,
            }
        )
        return _message_content(data).strip()

    def translate_entries(
        self,
        entries: dict[str, str],
        language: LanguageConfig,
        *,
        executor: ThreadPoolExecutor,
        retries: int,
        on_batch_done=None,
    ) -> TranslateResult:
        """分批并发翻译，并校验每条译文；未通过的条目在下一轮重试。"""
        keys = list(entries)
        batch_size = self.config.translation.batch_size
        batches = [keys[i : i + batch_size] for i in range(0, len(keys), batch_size)]
        futures = [
            executor.submit(
                self._translate_batch_with_retries,
                {key: entries[key] for key in batch},
                language,
                retries,
            )
            for batch in batches
        ]

        result = TranslateResult()
        for batch, future in zip(batches, futures):
            translations, failures = future.result()
            result.translations.update(translations)
            result.failures.update(failures)
            if on_batch_done is not None:
                on_batch_done(len(batch))
        return result

    def _translate_batch_with_retries(
        self, entries: dict[str, str], language: LanguageConfig, retries: int
    ) -> tuple[dict[str, str], dict[str, str]]:
        translations: dict[str, str] = {}
        failures: dict[str, str] = {}
        pending = dict(entries)
        for _ in range(retries):
            if not pending:
                break
            try:
                response = self.translate_batch(pending, language)
            except Exception as error:  # noqa: BLE001 - 网络与解析错误都按失败处理
                for key in pending:
                    failures[key] = error_message(error)
                continue
            for key, source in list(pending.items()):
                raw = response.get(key)
                if not isinstance(raw, str):
                    failures[key] = "missing in response"
                    continue
                value = normalize_translation(raw)
                error = validate_translation(source, value, language.protected_terms)
                if error:
                    failures[key] = f"{error}: {json.dumps(value, ensure_ascii=False)}"
                    continue
                translations[key] = value
                failures.pop(key, None)
                pending.pop(key)
        return translations, failures
