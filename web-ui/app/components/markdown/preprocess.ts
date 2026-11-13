const INLINE_LATEX_REGEX = /\\\((.+?)\\\)/g;
const BLOCK_LATEX_REGEX = /\\\[(.+?)\\\]/gs;
const CODE_BLOCK_REGEX = /```[\s\S]*?```|`[^`\n]*`/g;
const SVG_BLOCK_REGEX = /<svg\b[\s\S]*?<\/svg\s*>/gi;

/** Inline SVG longer than this (in UTF-16 code units) is replaced by a placeholder. */
export const MAX_INLINE_SVG_CHARS = 128 * 1024;

export type PreProcessOptions = {
  /** Shown in place of an SVG block that is too large to render. */
  svgOversizePlaceholder: string;
  maxSvgChars?: number;
};

type Range = { start: number; end: number };

function codeBlockRanges(content: string): Range[] {
  const ranges: Range[] = [];
  const regex = new RegExp(CODE_BLOCK_REGEX.source, "g");
  let match: RegExpExecArray | null;
  while ((match = regex.exec(content)) !== null) {
    ranges.push({ start: match.index, end: match.index + match[0].length });
  }
  return ranges;
}

function isInRanges(position: number, ranges: Range[]): boolean {
  return ranges.some((range) => position >= range.start && position < range.end);
}

/** Drop SVG blocks that are too large to parse and render safely. */
function guardSvgBlocks(content: string, options: PreProcessOptions): string {
  const budget = options.maxSvgChars ?? MAX_INLINE_SVG_CHARS;
  const ranges = codeBlockRanges(content);
  const replacements: Range[] = [];

  const regex = new RegExp(SVG_BLOCK_REGEX.source, "gi");
  let match: RegExpExecArray | null;
  while ((match = regex.exec(content)) !== null) {
    if (isInRanges(match.index, ranges)) continue;
    if (match[0].length <= budget) continue;
    replacements.push({ start: match.index, end: match.index + match[0].length });
  }

  if (replacements.length === 0) return content;

  let result = "";
  let cursor = 0;
  for (const range of replacements) {
    result += content.slice(cursor, range.start);
    result += `\n\n${options.svgOversizePlaceholder}\n\n`;
    cursor = range.end;
  }
  return result + content.slice(cursor);
}

/**
 * Normalize LaTeX delimiters and cap oversized inline SVG before Streamdown
 * parses the message. Code blocks are skipped for both steps.
 */
export function preProcess(content: string, options: PreProcessOptions): string {
  const guarded = guardSvgBlocks(content, options);

  // 每次替换后 offset 都会变，范围必须按当前字符串重算
  const inlineRanges = codeBlockRanges(guarded);
  const withInlineMath = guarded.replace(
    new RegExp(INLINE_LATEX_REGEX.source, "g"),
    (match, group1: string, offset: number) => {
      if (isInRanges(offset, inlineRanges)) return match;
      return `$${group1}$`;
    },
  );

  const blockRanges = codeBlockRanges(withInlineMath);
  return withInlineMath.replace(
    new RegExp(BLOCK_LATEX_REGEX.source, "gs"),
    (match, group1: string, offset: number) => {
      if (isInRanges(offset, blockRanges)) return match;
      return `$$${group1}$$`;
    },
  );
}
