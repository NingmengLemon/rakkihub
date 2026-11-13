import { defaultSchema } from "rehype-sanitize";
import type { Schema } from "hast-util-sanitize";

/**
 * Sanitizer schema for message markdown.
 *
 * Streamdown's built-in pipeline (`rehype-raw` -> `rehype-sanitize` -> harden) is
 * replaced by an explicit one so that inline SVG keeps working while raw HTML is
 * still filtered. The allowlist below is deliberately static-only: no scripts,
 * no event handlers, no `<style>`, no `<foreignObject>`, no animation, no
 * filters. Anything not listed here is removed.
 */
type AttributeList = NonNullable<Schema["attributes"]>[string];

const paintAttributes: AttributeList = [
  "fill",
  "fillOpacity",
  "fillRule",
  "stroke",
  "strokeWidth",
  "strokeLinecap",
  "strokeLinejoin",
  "strokeDasharray",
  "strokeDashoffset",
  "strokeOpacity",
  "opacity",
  "transform",
  "color",
  "clipPath",
  "mask",
  "markerStart",
  "markerMid",
  "markerEnd",
];

const boxAttributes: AttributeList = ["x", "y", "width", "height", "rx", "ry"];

const svgAttributes: AttributeList = [
  "viewBox",
  "width",
  "height",
  "xmlns",
  "preserveAspectRatio",
  "role",
  "ariaLabel",
  "ariaHidden",
  ...paintAttributes,
];

const svgTags = [
  "svg",
  "g",
  "path",
  "rect",
  "circle",
  "ellipse",
  "line",
  "polyline",
  "polygon",
  "text",
  "tspan",
  "defs",
  "use",
  "symbol",
  "title",
  "desc",
  "image",
  "linearGradient",
  "radialGradient",
  "stop",
  "clipPath",
  "mask",
  "pattern",
  "marker",
];

/** Tags whose children are dropped with them instead of being unwrapped. */
const strippedTags = [
  ...(defaultSchema.strip ?? []),
  "style",
  "foreignObject",
  "iframe",
  "object",
  "embed",
  "template",
  "animate",
  "animateMotion",
  "animateTransform",
  "set",
  "filter",
  "feGaussianBlur",
  "feBlend",
  "feColorMatrix",
  "feComponentTransfer",
  "feComposite",
  "feConvolveMatrix",
  "feDiffuseLighting",
  "feDisplacementMap",
  "feDropShadow",
  "feFlood",
  "feImage",
  "feMerge",
  "feMorphology",
  "feOffset",
  "feSpecularLighting",
  "feTile",
  "feTurbulence",
];

export const svgSchema: Schema = {
  ...defaultSchema,
  tagNames: [...(defaultSchema.tagNames ?? []), ...svgTags],
  attributes: {
    ...defaultSchema.attributes,
    // remark-math emits `<span class="math math-inline">` / `<div class="math math-display">`
    // and rehype-katex runs after sanitizing, so those classes have to survive.
    span: [["className", /^math/]],
    div: [...(defaultSchema.attributes?.div ?? []), ["className", /^math/]],
    svg: svgAttributes,
    g: ["id", ...paintAttributes],
    path: ["d", "pathLength", "id", ...paintAttributes],
    rect: ["id", ...boxAttributes, ...paintAttributes],
    circle: ["cx", "cy", "r", "id", ...paintAttributes],
    ellipse: ["cx", "cy", "rx", "ry", "id", ...paintAttributes],
    line: ["x1", "y1", "x2", "y2", "id", ...paintAttributes],
    polyline: ["points", "id", ...paintAttributes],
    polygon: ["points", "id", ...paintAttributes],
    text: [
      "x",
      "y",
      "dx",
      "dy",
      "rotate",
      "textAnchor",
      "dominantBaseline",
      "fontSize",
      "fontFamily",
      "fontWeight",
      "id",
      ...paintAttributes,
    ],
    tspan: ["x", "y", "dx", "dy", "rotate", "textAnchor", "id", ...paintAttributes],
    defs: ["id"],
    symbol: ["id", "viewBox", "preserveAspectRatio"],
    use: ["href", "xLinkHref", "x", "y", "width", "height"],
    image: ["href", "xLinkHref", "x", "y", "width", "height", "preserveAspectRatio"],
    linearGradient: ["id", "x1", "y1", "x2", "y2", "gradientUnits", "gradientTransform"],
    radialGradient: ["id", "cx", "cy", "r", "fx", "fy", "gradientUnits", "gradientTransform"],
    stop: ["offset", "stopColor", "stopOpacity"],
    clipPath: ["id", "clipPathUnits", "transform"],
    mask: ["id", "maskUnits", "x", "y", "width", "height"],
    pattern: [
      "id",
      "patternUnits",
      "patternContentUnits",
      "patternTransform",
      "width",
      "height",
      "x",
      "y",
      "viewBox",
    ],
    marker: [
      "id",
      "markerWidth",
      "markerHeight",
      "refX",
      "refY",
      "orient",
      "markerUnits",
      "viewBox",
      "preserveAspectRatio",
    ],
  },
  protocols: {
    ...defaultSchema.protocols,
    // `href` 同时用于普通链接和 SVG 的 use/image：mailto/tel 是安全的，javascript: 仍被拒
    href: ["http", "https", "mailto", "tel"],
    xLinkHref: ["http", "https"],
  },
  strip: strippedTags,
};
