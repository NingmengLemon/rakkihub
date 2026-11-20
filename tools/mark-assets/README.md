# mark-assets

Builds the offline assets used by `app/src/main/assets/html/mark.html`.

Before this tool existed the page imported markdown-it, KaTeX, highlight.js,
DOMPurify and js-base64 from esm.sh and jsdelivr at runtime, so the whole-message
WebView preview was blank without network. The bundle also avoids ES module
imports under `file://`, which Android WebView blocks.

## Usage

```powershell
cd tools/mark-assets
pnpm install
pnpm run build
```

The build writes into `app/src/main/assets/html/`:

- `mark.bundle.js`: ES2020 IIFE (`window.MarkAssets`) with the pinned libraries
- `katex.min.css`, `highlight.min.css`
- `fonts/`: KaTeX font files referenced by the CSS

The generated files are committed. Rebuild and commit them after changing a
dependency version or the entry list in `build.mjs`; CI does not run this tool.

## Notes

- Mermaid is not bundled here. `mark.html` uses the existing
  `assets/html/mermaid.min.js` IIFE so both paths keep one version.
- `mark.html` references the files through `{{ASSET_BASE}}`; the app replaces it
  with `WEB_VIEW_ASSET_URL/html/`, which maps to APK assets through
  `WebViewLocalAssets`.
- Versions are pinned in `package.json`; keep them aligned with
  `THIRD_PARTY_NOTICES.md` and the generated license file when upgrading.
- `entry.js` and `node_modules/` are generated and gitignored.
