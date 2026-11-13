import type { Element, ElementContent, Root, RootContent } from "hast";

export type SvgGuardOptions = {
  /** Above this many elements the diagram is replaced by a placeholder. */
  maxElements: number;
  /** Text shown in place of a diagram that is too complex. */
  placeholder: string;
};

const ID_PREFIX = "user-content-";
const URL_REFERENCE = /url\(\s*['"]?#([^)'"\s]+)['"]?\s*\)/g;

/** Attributes that may carry a `url(#id)` reference. */
const URL_REFERENCE_ATTRIBUTES = [
  "fill",
  "stroke",
  "clipPath",
  "mask",
  "markerStart",
  "markerMid",
  "markerEnd",
];

function countElements(node: Element): number {
  let count = 0;
  const stack: ElementContent[] = [...node.children];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current || current.type !== "element") continue;
    count += 1;
    stack.push(...current.children);
  }
  return count;
}

/**
 * `hast-util-sanitize` prefixes every `id` to avoid DOM clobbering, which breaks
 * same-document references. Prefix `href`/`xLinkHref` and `url(#id)` paint
 * references (gradients, clip paths, masks, markers) so they keep working.
 */
function rewriteLocalReferences(node: Element): void {
  const stack: ElementContent[] = [node];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current || current.type !== "element") continue;
    for (const key of ["href", "xLinkHref"]) {
      const value = current.properties?.[key];
      if (
        typeof value === "string" &&
        value.startsWith("#") &&
        !value.startsWith(`#${ID_PREFIX}`)
      ) {
        current.properties[key] = `#${ID_PREFIX}${value.slice(1)}`;
      }
    }
    for (const key of URL_REFERENCE_ATTRIBUTES) {
      const value = current.properties?.[key];
      if (typeof value !== "string" || !value.includes("url(")) continue;
      current.properties[key] = value.replace(
        URL_REFERENCE,
        (_match, id: string) => `url(#${id.startsWith(ID_PREFIX) ? id : `${ID_PREFIX}${id}`})`,
      );
    }
    stack.push(...current.children);
  }
}

function rejectedPlaceholder(placeholder: string): Element {
  return {
    type: "element",
    tagName: "span",
    properties: { className: ["svg-rejected"] },
    children: [{ type: "text", value: placeholder }],
  };
}

/** Replace oversized diagrams and keep local references working. */
export function rehypeSvgGuard(options: SvgGuardOptions) {
  return (tree: Root): void => {
    const walk = (parent: Root | Element): void => {
      const children = parent.children as RootContent[];
      for (let index = 0; index < children.length; index += 1) {
        const child = children[index];
        if (!child || child.type !== "element") continue;
        if (child.tagName === "svg") {
          if (countElements(child) > options.maxElements) {
            children[index] = rejectedPlaceholder(options.placeholder);
            continue;
          }
          rewriteLocalReferences(child);
          continue;
        }
        walk(child);
      }
    };
    walk(tree);
  };
}
