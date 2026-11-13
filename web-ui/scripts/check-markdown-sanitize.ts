/**
 * Sanity check for the message markdown sanitizer.
 *
 * The web client has no test runner, so this is a standalone script:
 *   pnpm run check:markdown
 *
 * It builds hostile hast trees and asserts that the allowlist in
 * `app/components/markdown/svg-schema.ts` keeps static SVG while dropping
 * scripts, styles, event handlers, foreign content and remote references.
 */
import { sanitize } from "hast-util-sanitize";
import type { Element, ElementContent, Root, RootContent } from "hast";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import rehypeSanitize from "rehype-sanitize";
import rehypeStringify from "rehype-stringify";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import remarkParse from "remark-parse";
import remarkRehype from "remark-rehype";
import { unified } from "unified";
import { preProcess } from "../app/components/markdown/preprocess";
import { rehypeSvgGuard } from "../app/components/markdown/rehype-svg-guard";
import { svgSchema } from "../app/components/markdown/svg-schema";

let failures = 0;

function check(name: string, condition: boolean, detail?: unknown): void {
  if (condition) {
    console.log(`ok   ${name}`);
    return;
  }
  failures += 1;
  console.error(`FAIL ${name}`, detail === undefined ? "" : detail);
}

function element(
  tagName: string,
  properties: Element["properties"] = {},
  children: ElementContent[] = [],
): Element {
  return { type: "element", tagName, properties, children };
}

function text(value: string): ElementContent {
  return { type: "text", value };
}

function root(children: ElementContent[]): Root {
  return { type: "root", children };
}

function html(tree: Root): string {
  const render = (node: Root | RootContent): string => {
    switch (node.type) {
      case "text":
        return node.value;
      case "comment":
      case "doctype":
        return "";
      case "raw":
        return node.value;
      case "root":
        return node.children.map(render).join("");
      default: {
        const attributes = Object.entries(node.properties ?? {})
          .map(([key, value]) => ` ${key}=${JSON.stringify(value)}`)
          .join("");
        const children = node.children.map(render).join("");
        return `<${node.tagName}${attributes}>${children}</${node.tagName}>`;
      }
    }
  };
  return tree.children.map(render).join("");
}

function sanitized(children: ElementContent[]): Root {
  return sanitize(root(children), svgSchema) as Root;
}

// --- kept ---------------------------------------------------------------

{
  const tree = sanitized([
    element("svg", { viewBox: "0 0 10 10" }, [
      element("circle", { cx: "5", cy: "5", r: "4", fill: "#fff" }),
      element("path", { d: "M0 0 L10 10" }),
      element("text", { x: "1", y: "2" }, [text("hi")]),
    ]),
  ]);
  const output = html(tree);
  check("static svg survives", output.includes("<svg") && output.includes("<circle"));
  check("svg text survives", output.includes("hi"));
  check("viewBox survives", output.includes("viewBox"));
}

{
  const tree = sanitized([
    element("span", { className: ["math", "math-inline"] }, [text("x")]),
    element("div", { className: ["math", "math-display"] }, [text("y")]),
  ]);
  const output = html(tree);
  check("math inline class survives", output.includes("math-inline"));
  check("math display class survives", output.includes("math-display"));
}

{
  const tree = sanitized([element("use", { href: "#arrow" })]);
  check("local use reference survives sanitize", html(tree).includes("#arrow"));
}

// --- dropped ------------------------------------------------------------

{
  const tree = sanitized([element("script", {}, [text("alert(1)")])]);
  check("script content dropped", !html(tree).includes("alert"));
}

{
  const tree = sanitized([element("style", {}, [text("body{display:none}")])]);
  check("style content dropped", !html(tree).includes("display:none"));
}

{
  const tree = sanitized([
    element("svg", {}, [element("foreignObject", {}, [element("div", {}, [text("x")])])]),
  ]);
  check("foreignObject content dropped", !html(tree).includes("<div"));
}

{
  const tree = sanitized([element("svg", { onLoad: "alert(1)" }, [element("circle")])]);
  check("event handler dropped", !html(tree).toLowerCase().includes("onload"));
}

{
  const tree = sanitized([element("a", { href: "javascript:alert(1)" }, [text("x")])]);
  check("javascript href dropped", !html(tree).includes("javascript"));
}

{
  const tree = sanitized([element("img", { src: "data:image/svg+xml;base64,AAAA" })]);
  check("data image dropped", !html(tree).includes("data:"));
}

{
  const tree = sanitized([element("div", { style: "position:fixed" }, [text("x")])]);
  check("style attribute dropped", !html(tree).includes("position:fixed"));
}

{
  const tree = sanitized([element("svg", {}, [element("animate", { attributeName: "x" })])]);
  check("animation dropped", !html(tree).includes("animate"));
}

{
  const tree = sanitized([element("iframe", { src: "https://example.com" })]);
  check("iframe dropped", !html(tree).includes("iframe"));
}

// --- size and reference guards -----------------------------------------

{
  const oversized = `<svg>${"x".repeat(256)}</svg>`;
  const result = preProcess(oversized, {
    svgOversizePlaceholder: "SVG_TOO_LARGE",
    maxSvgChars: 128,
  });
  check("oversized svg replaced", result.includes("SVG_TOO_LARGE") && !result.includes("<svg>"));
}

{
  const small = '<svg><circle r="1"/></svg>';
  const result = preProcess(small, { svgOversizePlaceholder: "SVG_TOO_LARGE" });
  check("small svg untouched", result.includes("<svg>"));
}

{
  const fenced = "```html\n<svg>" + "x".repeat(256) + "</svg>\n```";
  const result = preProcess(fenced, {
    svgOversizePlaceholder: "SVG_TOO_LARGE",
    maxSvgChars: 128,
  });
  check("svg inside a code fence untouched", !result.includes("SVG_TOO_LARGE"));
}

{
  const latex = preProcess("inline \\(a+b\\) and block \\[c\\]", {
    svgOversizePlaceholder: "SVG_TOO_LARGE",
  });
  check("latex delimiters normalized", latex.includes("$a+b$") && latex.includes("$$c$$"));
}

{
  const tree = sanitized([
    element("svg", {}, [
      element("defs", {}, [element("marker", { id: "arrow" })]),
      element("use", { href: "#arrow" }),
    ]),
  ]);
  rehypeSvgGuard({ maxElements: 1000, placeholder: "TOO_COMPLEX" })(tree);
  const output = html(tree);
  check("use reference follows clobbered id", output.includes("#user-content-arrow"));
  check("clobbered id kept", output.includes("user-content-arrow"));
}

{
  const children: ElementContent[] = Array.from({ length: 20 }, () => element("circle"));
  const tree = sanitized([element("svg", {}, children)]);
  rehypeSvgGuard({ maxElements: 5, placeholder: "TOO_COMPLEX" })(tree);
  const output = html(tree);
  check("complex svg replaced by placeholder", output.includes("TOO_COMPLEX"));
  check("placeholder is inline-safe", output.includes("<span") && !output.includes("<p><span"));
}

{
  const tree = sanitized([
    element("svg", {}, [
      element("defs", {}, [
        element("linearGradient", { id: "g" }),
        element("clipPath", { id: "c" }),
        element("marker", { id: "m" }),
      ]),
      element("rect", {
        fill: "url(#g)",
        stroke: "url('#g')",
        clipPath: "url( #c )",
        markerStart: "url(#m)",
      }),
    ]),
  ]);
  rehypeSvgGuard({ maxElements: 1000, placeholder: "TOO_COMPLEX" })(tree);
  const output = html(tree);
  check("fill url reference follows clobbered id", output.includes("url(#user-content-g)"));
  check("clip-path url reference follows clobbered id", output.includes("url(#user-content-c)"));
  check("marker url reference follows clobbered id", output.includes("url(#user-content-m)"));
  check("marker attribute survives sanitize", output.includes("markerStart"));
}

{
  const tree = sanitized([element("a", { href: "mailto:a@b.c" }, [text("mail")])]);
  check("mailto link survives", html(tree).includes("mailto:a@b.c"));
}

{
  const tree = sanitized([element("a", { href: "tel:+123" }, [text("call")])]);
  check("tel link survives", html(tree).includes("tel:+123"));
}

{
  const fenced = "\\(a\\)\\(b\\)\\(c\\)\\(d\\)\\(e\\)\n\n```\n\\[c\\]\n```";
  const result = preProcess(fenced, { svgOversizePlaceholder: "SVG_TOO_LARGE" });
  check("latex inside a fence survives later replacements", result.includes("\\[c\\]"));
}

if (failures > 0) {
  console.error(`\n${failures} check(s) failed`);
  process.exit(1);
}

// --- full pipeline ------------------------------------------------------

async function render(markdown: string): Promise<string> {
  const file = await unified()
    .use(remarkParse)
    .use(remarkGfm)
    .use(remarkMath)
    .use(remarkRehype, { allowDangerousHtml: true })
    .use(rehypeRaw)
    .use(rehypeSanitize, svgSchema)
    .use(rehypeSvgGuard, { maxElements: 1000, placeholder: "TOO_COMPLEX" })
    .use(rehypeKatex, { trust: false, strict: false, maxSize: 50, maxExpand: 1000 })
    .use(rehypeStringify, { allowDangerousHtml: true })
    .process(markdown);
  return String(file);
}

{
  const output = await render(
    [
      "# title",
      "",
      '<svg viewBox="0 0 10 10"><circle cx="5" cy="5" r="4"/></svg>',
      "",
      "<script>alert(1)</script>",
      "",
      '<img src=x onerror="alert(1)">',
      "",
      '<svg onload="alert(1)"><foreignObject><div>x</div></foreignObject></svg>',
      "",
      "Inline math $a^2$ and `code`.",
      "",
      "| a | b |",
      "| - | - |",
      "| 1 | 2 |",
    ].join("\n"),
  );
  check("pipeline keeps static svg", output.includes("<circle"));
  check("pipeline keeps headings", output.includes("<h1"));
  check("pipeline keeps gfm tables", output.includes("<table"));
  check("pipeline keeps inline code", output.includes("<code"));
  check("pipeline renders katex", output.includes("katex"));
  check("pipeline drops script", !output.includes("alert(1)"));
  check("pipeline drops event handler", !output.toLowerCase().includes("onload"));
  check("pipeline drops foreignObject", !output.includes("foreignObject"));
}

console.log("\nall checks passed");
