import { expect, test } from "bun:test";
import { loadConfig, type LanguageConfig } from "../src/config";
import { Translator } from "../src/translator";

test("glossary is appended to the prompt", () => {
  const config = loadConfig();
  const translator = new Translator(config);
  const entries = new Map([["system_prompt", "System Prompt"]]);
  const lang = (code: string) => config.targets.find((l) => l.code === code)!;

  const simplified = translator.buildPrompt(entries, lang("values-zh"));
  expect(simplified).toContain("Glossary for Chinese (Simplified)");
  expect(simplified).toContain("提示词");
  expect(simplified).toContain('"system_prompt": "System Prompt"');

  expect(translator.buildPrompt(entries, lang("values-ja"))).toContain("Glossary for Japanese");
});

test("languages without a glossary get no glossary section", () => {
  const translator = new Translator(loadConfig());
  const entries = new Map([["system_prompt", "System Prompt"]]);
  const noGlossary: LanguageConfig = {
    code: "values-test",
    name: "Test",
    is_source: false,
    glossary: {},
  };

  expect(translator.buildPrompt(entries, noGlossary)).not.toContain("Glossary");
});
