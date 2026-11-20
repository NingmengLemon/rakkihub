# Third-party notices

`mark.bundle.js` bundles the packages below (plus their transitive
dependencies). Full license texts for every bundled package are generated into
`app/src/main/assets/html/THIRD_PARTY_LICENSES.txt`, which ships in the APK.

| Package | Version | License |
| --- | --- | --- |
| markdown-it | 14.0.0 | MIT |
| @vscode/markdown-it-katex | 1.1.2 | MIT |
| markdown-it-task-lists | 2.1.1 | ISC |
| katex | 0.16.47 | MIT |
| highlight.js | 11.11.1 | BSD-3-Clause |
| js-base64 | 3.7.5 | BSD-3-Clause |
| dompurify | 3.4.17 | MPL-2.0 OR Apache-2.0 |

`mark.html` also loads `assets/html/mermaid.min.js`, the official mermaid
11.17.2 IIFE (MIT). Its license text is appended to `THIRD_PARTY_LICENSES.txt`
by the build script; the minified file itself does not carry a mermaid banner.

License banners preserved by esbuild are not a substitute for the notices
file: several packages ship license text only in their package directory, not
in the entry files that esbuild bundles.
