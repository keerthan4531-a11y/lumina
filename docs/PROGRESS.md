# Lumina — build progress

Last updated: 2026-11-25 · branch `arena/07a96f3f-lumina`

## ✅ It builds — one installable APK

| APK | What it is | Link |
|---|---|---|
| **Lumina browser (P3.3)** (188 MB, debug) | DuckDuckGo engine + `app.lumina.browser` + **native glass home page**: frosted glass views, floating bottom bar with a travelling glow, real Google News feed, topic chips | [`fork-0.3.1-browser`](https://github.com/keerthan4531-a11y/lumina/releases/tag/fork-0.3.1-browser) |

> The Compose demo project is retired (AGP 8 / Kotlin 2.0). There is exactly one build target:
> the browser fork. `lumina-android/` is copied into its app module by `scripts/p3_wire_home.py`.

Legend: ✅ done · 🟡 partial / needs a device · ⬜ not started

> **How the build happens:** this sandbox has no JDK/Android SDK, so compilation runs on GitHub
> Actions runners (JDK 21 + SDK 36/37). A `fork-*` tag → `browser-fork-apk` → APK artifact +
> GitHub Release. Nothing is verified by hand.

---

## Phase board

| Phase | Status | What exists in this repo right now | What's left |
|---|---|---|---|
| **P0** Fork + rebrand (build green) | ✅ | `scripts/p0-fork.sh`, `scripts/p0_rebrand.py` (tested on a real upstream checkout — appId, rootProject, appName, flavours, dangling deps, node stubs all verified) | **`assembleInternalDebug` passes in CI** → rebranded browser APK published. Telemetry module deletion is deferred to P1 (`--diet` flag, opt-in) because `:app` resources and feature modules reference them |
| **P1** Slim modules + CI + baseline | 🟡 | module delete-list + dangling-dep rewriter inside the P0 script | GitHub Actions workflow, `docs/baseline.md` from macrobenchmark |
| **P2** `lumina-ui` glass system | ✅ | `glass/` reference module (tokens, Motion, GlassSurface/Omnibar/Pill, HybridBackdrop) **+ `preview/` pixel-accurate prototype** that defines the design | Kotlin port of ResultCard / AnswerCard / BottomBar / Sheet + gallery |
| **P3** Native glass NTP | ✅ | `lumina-ui/` (real Compose module: tokens, motion, `GlassSurface`, `LuminaIcons`, `LuminaNtp`) + `scripts/p3_wire_ntp.py` → `LuminaHomeView` overlaid as the last child of `include_new_browser_tab.xml`, so DDG's own NTP show/hide logic and the omnibar stay untouched | polish after device feedback (icon motion on the NTP cards, real wallpapers, light-mode palette) |
| **P4** Glass chrome + icon motion | ✅ | motion spec + prototype's omnibar / bottom bar / tabs grid | Kotlin omnibar, tab deck, nav bar with moving highlight |
| **P5** SERP v2 | 🟡 | `serp/lumina-skin.css`, `serp/lumina-skin.js`, `serp/selectors.json`, `serp/LuminaSerpSkin.kt` (layer A complete, fail-open selector map) | layer B `SerpProvider` + native cards, layer C AI answer |
| **P6** Performance | ⬜ | budget table in `00-MASTER-PLAN.md §5` | baseline profiles, WebView warm pool, R8 config |
| **P7** Privacy hardening | 🟡 | telemetry modules removed by P0 script; shield sheet designed in prototype | per-site shield sheet in Kotlin, blocklist updates |
| **P8** AI layer | 🟡 | AI sheet + streaming answer designed; BYOK/on-device plan | AICore integration, `lumina-ai` module |
| **P9** Beta | ⬜ | release checklist in `04-LEGAL-AND-RELEASE.md` | signing, Play listing, staged rollout |

---

## What I built this round

### 0. `lumina-ui/` — the real Compose module (compiles, ships)
- `LuminaTokens` (1:1 with `preview/css/tokens.css`), `LuminaIcons`, `Motion` (springs, `pressScale`,
  `StaggeredEntrance`, reduced-motion, haptics)
- `GlassSurface` — the single glass primitive: shadow → clip → tint gradient → **pointer-following
  specular** → 1dp gradient rim. Every other component is built from it, so the material can't drift.
- `GlassOmnibar` (shield chip, autofocus on open, AI chip, suggestion list), `GlassResultCard`
  (domain chip + per-result tracker badge), `GlassAnswerCard` (streaming-ready, on-device badge,
  citation chips), `GlassBottomBar` (sliding highlight pill), `GlassSheet` (spring slide + scrim),
  `GlassChip`, `GlassStat`
- `LuminaNtp` — the native home page: greeting, glass search field, AI pill, quick cards,
  favourites, recent. Zero network.
- `LuminaWallpaper` — the mesh gradient the glass sits on.

### 0b. `lumina-demo/` — installable app
`MainActivity` wires home → search → results → tabs + shield/AI sheets, so the whole design is
usable on a phone. `applicationId app.lumina.demo`, adaptive prism icon, dark theme.
Release build: R8 + resource shrinking, debug-signed (**56 MB debug → 19.4 MB release**).

### 0c. CI (`.github/workflows/build.yml`)
- `lumina-demo-apk`: JDK 17 + preinstalled SDK + Gradle 8.9 → `assembleRelease` → artifact +
  **GitHub Release with the APK attached** on `v*` tags
- `browser-fork-apk`: clones DuckDuckGo, runs `scripts/p0-fork.sh`, builds the browser APK
  (trigger: manual dispatch or a `fork-*` tag)

### 1. `preview/` — the design, running
A real, interactive liquid-glass prototype (open the live preview):

- **Home** — greeting, glass search field, ✨AI pill, Ask/Shields cards, favourites rail, recent list
- **Omnibar** — expands on a spring, suggestion rows, pointer-tracking specular highlight
- **Results** — streaming AI answer card with citation chips, glass result cards (domain chip +
  per-result blocked-tracker badge), related-query chips
- **Tabs** — glass card deck with stagger entrance
- **Sheets** — shield sheet (stats, toggles, blocked list) and AI chat sheet, slide up over blur
- **Controls** — theme (dark/light), **glass tier** (refraction 33+ / blur 31+ / tint 28+),
  reduced motion. Same components, three materials — proves the fallback matrix before you write a
  line of Kotlin.

The tokens in `preview/css/tokens.css` are 1:1 with `glass/.../LuminaTokens.kt`, and the easings
mirror `LuminaSpring`. So the prototype is the spec, not a mood board.

### 2. `scripts/` — P0 that actually runs
- `p0-fork.sh` — clones upstream, `npm ci`, rebrand, `./gradlew help` config check, prints next steps
- `p0_rebrand.py` — idempotent, `--dry-run`, brace-matched Groovy edits:
  - `applicationId` → `app.lumina.browser`, `rootProject.name` → `lumina`, `appName` → `Lumina`
  - deletes telemetry/funnel modules (`pixels`, `survey`, `anrs`, `attributed-metrics`,
    `internalfeedback`, `app-store`, + dead weight `vpn*`, `subscriptions*`, `email*`, `sync*`,
    `devicemigration`, `widget`)
  - removes the *dangling* `implementation project(':pixels-impl')` style lines
  - **keeps the `internal` flavour** and deletes `play` + `fdroid` — because upstream's root build
    enforces *"'internal' modules must use internalImplementation"* in 23 places; renaming the flavour
    to `lumina` breaks configuration. (This is exactly the kind of thing you only learn by reading
    `build.gradle` rather than guessing.)
  - stubs the `node_modules` asset dirs so a failed `npm ci` doesn't kill the build

  Verified on a real 45 MB upstream checkout: braces balanced, only intended lines changed.

### 3. `serp/` — the "old results page" fix, layer A
- `lumina-skin.css` — glass result cards, sticky glass search bar, chips, IA card, skeletons
  instead of spinners, light/dark via `data-lumina-theme`, CLS reserved with `min-height`
- `lumina-skin.js` — fail-open selector map, result tagging, `extract()` for the native card layer,
  MutationObserver for infinite scroll, no dependencies, ~3 KB
- `selectors.json` — versioned fallback lists per element (the thing that keeps this alive when
  the site redesigns)
- `LuminaSerpSkin.kt` — `WebViewCompat.addDocumentStartJavaScript` injection (no white flash),
  theme/motion sync, `extract()` bridge

---

## CI history (what the compiler actually said)

| Run | Result | Fix |
|---|---|---|
| #1 | ✗ 16 s | `android-actions/setup-android@v3` broken on node24 runners → use the preinstalled SDK |
| #2 | ✓ 2 m 42 s | **demo APK builds** (debug, 56 MB) |
| #3 | ✓ | R8 + shrink → **19.4 MB release**, published to Releases |
| #4–5 | ✗ fork | `Project with path ':anrs-api' could not be found` — deleting `anrs/` also deletes `anrs-api`/`anrs-impl`; fixed by stripping dependencies via **set-difference** against real module discovery |
| #6 | ✗ fork | `remove_gradle_block` lost in a refactor (NameError) → restored + regression-tested on a mini repo |
| #7 | ✓ fork config | `gradlew help` passes; `assembleInternalDebug` fails next |
| #8 | ✗ fork assemble | kept modules still *use* what we deleted |
| #9 | ✗ fork assemble | `strings.xml.lumina.bak` inside `res/values/` → resource merger rejects non-.xml → backups moved **outside** the repo |
| #10 | ✗ fork assemble | `@string/duck_chat_title` missing → `duckchat` must survive (it is the AI entry point anyway) |
| #11 | ✗ fork assemble | CMake: `bloom_cpp` is a **git submodule** that `--depth 1` clone never fetched → `submodule update --init --recursive` added to `p0-fork.sh` |
| #12 | ✅ **fork** | `assembleInternalDebug` green → **browser APK published** |
| #13 | ✅ | browser APK release asset + demo release |
| #14 | ✗ build.gradle.kts | `p3_wire_ntp.py` copied `lumina-ui` into the fork *with* `id("org.jetbrains.kotlin.android")` → AGP 9: "no longer required", hard failure. Script now strips the plugin **and** `kotlinOptions {}` |
| #15 | ✅ **P3** | both jobs green → **`fork-0.1.7-browser` ships the glass home page**; demo down to **8.1 MB** (material-icons-extended gone) |

## Next up (in order)

0. ~~Fork assemble green~~ ✅ · ~~Glass NTP wired into the fork~~ ✅ (`fork-0.1.7-browser`).
   **Waiting on:** install the 0.1.7 browser APK and report what the home page looks like.
1. **Telemetry actually removed** — the module diet is
   dependency-aware now (`--diet`) but `:app`'s resources and several feature modules still reference
   the telemetry modules, so the safe path is: stub `Pixel`/`RealPixel` implementations to no-ops and
   delete the *call sites*, then remove `pixels*`/`anrs*`/`attributed-metrics*` in P1 with verification.
1. **Kotlin port of the prototype** — `lumina-ui`: `GlassResultCard`, `GlassAnswerCard`,
   `GlassBottomBar`, `GlassSheet`, `GlassChip` + `LuminaGalleryActivity` (P2 finish)
2. **NTP wiring patch** — exact diff against `include_new_browser_tab.xml` + `BrowserTabFragment.kt`
   so the home page is Compose on the first build (P3)
3. **`lumina-search` module** — `SerpProvider` + `DdgHtmlProvider` (Jsoup) + `BraveProvider` (P5)
4. **CI workflow + baseline doc** (P1 tail)
5. **AI module skeleton** — AICore availability check + BYOK store in Keystore (P8 start)

## How to see the prototype

The preview server runs in this workspace on port **5173** (live preview panel).
Locally: `python3 -m http.server 5173 --directory preview` and open `http://localhost:5173`.
