import * as React from "react";
import type { DiagramPlugin } from "@streamdown/mermaid";

type MermaidTheme = "dark" | "default";

// The mermaid engine is large and only needed when a message actually contains
// a diagram; this module is loaded through React.lazy from markdown.tsx.
let pluginCache: { theme: MermaidTheme; promise: Promise<DiagramPlugin> } | null = null;

function loadPlugin(theme: MermaidTheme): Promise<DiagramPlugin> {
  if (pluginCache?.theme !== theme) {
    const promise = import("@streamdown/mermaid").then(({ createMermaidPlugin }) =>
      createMermaidPlugin({
        config: {
          startOnLoad: false,
          securityLevel: "strict",
          htmlLabels: false,
          theme,
          fontFamily: "inherit",
        },
      }),
    );
    pluginCache = { theme, promise };
  }
  return pluginCache.promise;
}

// Mermaid honours %%{init: ...}%% and YAML frontmatter for non-secure config,
// including themeCSS, which can reference remote URLs and end up in the SVG
// <style> without being sanitized. Our plugin config is the only accepted
// source, so drop both directives before rendering. Mermaid also accepts an
// unclosed directive at EOF, single quotes, CR-only line endings and indented
// frontmatter, so normalize line endings and strip through EOF when needed.
export function sanitizeMermaidSource(source: string): string {
  let next = source.replace(/\r\n?/g, "\n");
  // Stripping a directive can expose a frontmatter that was behind it, and
  // removing one frontmatter can expose another. Loop both rules until stable,
  // then refuse anything that still looks like a config block.
  let previous: string;
  do {
    previous = next;
    next = next
      .replace(/%%\{[\s\S]*?(?:\}%%|$)/g, "")
      .replace(/^\s*---[ \t]*\n[\s\S]*?\n[ \t]*---[ \t]*(?:\n|$)/, "");
  } while (next !== previous);
  if (next.includes("%%{")) return next.slice(0, next.indexOf("%%{")).trim();
  if (/^---[ \t]*$/.test(next.trimStart().split("\n", 1)[0] ?? "")) return "";
  return next.trim();
}

export default function MermaidDiagram({ code, isDark }: { code: string; isDark: boolean }) {
  const reactId = React.useId();
  const renderCount = React.useRef(0);
  const [svg, setSvg] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    const source = sanitizeMermaidSource(code);
    const id = `mermaid-${reactId.replace(/[^a-zA-Z0-9_-]/g, "")}-${renderCount.current++}`;
    setSvg("");
    setError(null);
    if (!source) {
      setError("Empty mermaid diagram");
      return () => {
        cancelled = true;
      };
    }
    loadPlugin(isDark ? "dark" : "default")
      .then((plugin) => plugin.getMermaid().render(id, source))
      .then(({ svg: next }) => {
        if (!cancelled) setSvg(next);
      })
      .catch((reason: unknown) => {
        if (!cancelled) setError(reason instanceof Error ? reason.message : String(reason));
      });
    return () => {
      cancelled = true;
    };
  }, [code, isDark, reactId]);

  if (error) {
    return (
      <div className="my-4 rounded-md border border-border bg-muted/40 p-3">
        <p className="font-medium text-sm">Mermaid Error</p>
        <pre className="mt-2 overflow-x-auto text-xs">{code}</pre>
      </div>
    );
  }

  if (!svg) {
    return (
      <div className="my-4 flex min-h-[160px] items-center justify-center text-muted-foreground text-sm">
        Loading diagram...
      </div>
    );
  }

  return (
    <div
      className="my-4 flex justify-center overflow-x-auto"
      role="img"
      aria-label="Mermaid chart"
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
