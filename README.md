# Lumina

A liquid-glass, lightweight, fast, privacy-first Android browser with native AI features,
built on top of the (Apache-2.0) DuckDuckGo Android codebase.

> **Status:** Phase 0 — planning + reference code. No app build in this repo yet.

## Why this repo exists

`duckduckgo/Android` is a solid, privacy-hardened **WebView browser**:

| Fact | Value |
|---|---|
| Gradle modules | 207 |
| Kotlin/Java files | 6,709 (1,475 in `app/` alone) |
| SDK | minSdk 28, target/compileSdk 36 |
| Kotlin | 2.4.21, JDK 21 required to build |
| Flavors | `internal`, `fdroid`, `play` |
| Home page | native XML NTP (`include_new_browser_tab.xml`) + Dax logo |
| Search results page | **remote `duckduckgo.com` HTML rendered in the WebView** |
| AI | `duckchat` (duck.ai) is already wired: swipe-up, query detection |
| License | Apache-2.0 + proprietary DuckSans font (must be replaced) |

That last-but-one row is the root cause of "the UI still looks 2014": the browser chrome is
native but the *home page* is a static logo page and the *results page* is somebody else's
2014-era HTML document. Lumina fixes that in layers — glass chrome, native NTP, native
result cards over a skinned SERP — instead of trying to rewrite a browser in a weekend.

## Documents

| Doc | What it answers |
|---|---|
| [`docs/00-MASTER-PLAN.md`](docs/00-MASTER-PLAN.md) | How many phases, what each phase delivers, effort math, perf budget, DoD |
| [`docs/01-TWO-HOUR-SPRINT.md`](docs/01-TWO-HOUR-SPRINT.md) | The honest 120-minute vertical slice (fork → installable glass APK) |
| [`docs/02-LIQUID-GLASS-SPEC.md`](docs/02-LIQUID-GLASS-SPEC.md) | Glass design system, backdrop API, API-level fallbacks, icon motion, frame budget |
| [`docs/03-NTP-AND-SERP-REDESIGN.md`](docs/03-NTP-AND-SERP-REDESIGN.md) | Why the home page and results look old, and the 3-layer fix (skin → native cards → AI) |
| [`docs/04-LEGAL-AND-RELEASE.md`](docs/04-LEGAL-AND-RELEASE.md) | Apache-2.0 obligations, DuckDuckGo trademark, DuckSans removal, telemetry strip, Play policy |
| [`glass/`](glass/) | Reference Compose glass code (tokens, GlassOmnibar, GlassNtp, motion) to drop into `lumina-ui` |

## Upstream sources

- Browser base: https://github.com/duckduckgo/Android (Apache-2.0)
- Glass engine: https://github.com/Kyant0/AndroidLiquidGlass (`backdrop`, Apache-2.0),
  published as `io.github.kyant0:backdrop` on Maven Central, or vendored as a module.

## One-line pitch

> Lumina: DuckDuckGo's privacy engine, with a glass UI that actually moves, a native home
> page and native result cards, and AI that answers instead of dumping ten blue links.
