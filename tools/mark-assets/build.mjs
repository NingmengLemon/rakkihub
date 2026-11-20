// Builds the offline assets used by app/src/main/assets/html/mark.html.
//
// The page used to import every dependency from esm.sh and jsdelivr at runtime,
// which fails without network. This script bundles the same pinned versions into
// one local ES module and copies KaTeX's CSS and fonts next to it.
import { copyFile, mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

const here = dirname(fileURLToPath(import.meta.url));
const outputDir = join(here, "..", "..", "app", "src", "main", "assets", "html");

await mkdir(join(outputDir, "fonts"), { recursive: true });

const entry = join(here, "entry.js");
await writeFile(
  entry,
  [
    'import MarkdownIt from "markdown-it";',
    'import markdownItKatexModule from "@vscode/markdown-it-katex";',
    'const markdownItKatex = markdownItKatexModule.default ?? markdownItKatexModule;',
    'import markdownItTaskLists from "markdown-it-task-lists";',
    'import katex from "katex";',
    'import "katex/dist/contrib/mhchem.mjs";',
    'import hljsModule from "highlight.js";',
    'const hljs = hljsModule.default ?? hljsModule;',
    'import { Base64 } from "js-base64";',
    'import DOMPurify from "dompurify";',
    "export { MarkdownIt, markdownItKatex, markdownItTaskLists, katex, hljs, Base64, DOMPurify };",
    "",
  ].join("\n"),
);

await build({
  entryPoints: [entry],
  bundle: true,
  format: "iife",
  globalName: "MarkAssets",
  minify: true,
  target: ["chrome100"],
  outfile: join(outputDir, "mark.bundle.js"),
  legalComments: "eof",
});

await copyFile(
  join(here, "node_modules", "katex", "dist", "katex.min.css"),
  join(outputDir, "katex.min.css"),
);
await copyFile(
  join(here, "node_modules", "highlight.js", "styles", "atom-one-dark.min.css"),
  join(outputDir, "highlight.min.css"),
);

const fontDir = join(here, "node_modules", "katex", "dist", "fonts");
for (const font of await readdir(fontDir)) {
  await copyFile(join(fontDir, font), join(outputDir, "fonts", font));
}

const size = (await readFile(join(outputDir, "mark.bundle.js"))).length;
console.log(`offline mark assets written to ${outputDir} (${size} bytes bundle)`);
