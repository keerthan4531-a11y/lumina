# Lumina — Master Rebuild Plan

**Base:** `duckduckgo/Android` @ `develop` (Apache-2.0) · **Glass engine:** `Kyant0/AndroidLiquidGlass` (`backdrop`, Apache-2.0)
**Goal:** Liquid-glass UI, lightweight, fast, high privacy, native AI — under the name **Lumina**.
**Answer to "evlo phase":** **10 phases (P0–P9) in 4 waves.** ~30 dev-days of work ≈ 6 weeks solo, ~3 weeks with 2 devs, ~2 weeks with 3.

---

## 0. Reality check (mudhalla ithu theriyanum)

| Question | Honest answer |
|---|---|
| Full rebuild in 2 hours? | **No.** A browser with 207 modules / 6,709 source files cannot be rebuilt in 2 hours. |
| 2 hours-la enna mudiyum? | Fork + rebrand + green build + **glass omnibar + glass home page** running on a device → a demo APK you can show. See `01-TWO-HOUR-SPRINT.md`. |
| Where does "old style" actually come from? | The home page and the results page are **`duckduckgo.com` HTML inside a WebView**. Nothing to restyle in Kotlin — that's why it still looks 2014. Fix = native NTP + skinned/native SERP. |
| Fastest path to "Lumina look"? | Don't rewrite the browser. Fork it, delete what you don't need, and put a glass layer + native pages on top. 80% of the visual win is P0→P4 (≈10 days). |

### Measured facts about the base repo

```
modules with build files ........ 207
Kotlin/Java files ............... 6,709   (app/: 1,475)
minSdk / target / compile ....... 28 / 36 / 36
Kotlin .......................... 2.4.21 (build needs JDK 21)
flavors ......................... internal, fdroid, play  → 5 build variants
NTP layout ...................... app/src/main/res/layout/include_new_browser_tab.xml (XML + Dax logo)
SERP ............................ remote duckduckgo.com in DuckDuckGoWebView
AI today ........................ app/.../browser/duckchat/ (swipe-up chat, AI query detection)
telemetry modules to strip ...... pixels, attributed-metrics, survey, anrs, internalfeedback
ship-blocking licence issue ..... DuckSans font is proprietary — must be replaced
```

---

## 1. Product definition (iru line-la)

> **Lumina** = DuckDuckGo's tracker blocking + a glass UI that moves at 120fps + a native home
> page and native answer cards + AI that summarises and cites instead of dumping links.

**Pillars (priority order — conflict vandha mela irukkaradhu win):**

1. **Speed** — cold start ≤ 700 ms, results visible ≤ 1.0 s, zero jank on the chrome.
2. **Privacy** — no first-party telemetry by default, tracker blocking on from launch, no account.
3. **Glass UI** — backdrop refraction, spring motion, icon micro-interactions.
4. **AI** — on-device first, BYOK cloud second, always with sources.
5. **Light** — one APK ≤ 25 MB, ≤ 2 variants.

**Non-goals for v1:** extensions, desktop sync, VPN, email protection, bookmarks import from
other browsers beyond bookmarks HTML, tablets/foldables polish, iOS.

---

## 2. Phase map

| Phase | Name | Dev-days | Wave | Ships |
|---|---|---|---|---|
| **P0** | Fork, rebrand, telemetry strip, green build | 1 | **W1 – Skeleton** | Installable `app.lumina.browser` APK |
| **P1** | Slim the module graph, CI, baseline metrics | 2 | W1 | 2 variants, CI green, benchmark harness |
| **P2** | `lumina-ui` glass design system | 4 | **W2 – Glass** | GlassSurface/Omnibar/Sheet/Chip + motion tokens |
| **P3** | Native glass NTP (home page) | 3 | W2 | New home page, no web round-trip |
| **P4** | Glass chrome: omnibar, tabs, bottom bar | 3 | W2 | Full glass chrome + icon motion |
| **P5** | SERP v2: glass skin + native result cards | 5 | **W3 – Search & AI** | Results that look native, answer card, AI summary |
| **P6** | Performance pass | 3 | W3 | Startup 2× faster than baseline, WebView warm pool |
| **P7** | Privacy hardening + shields UI | 2 | W3 | Per-site shield sheet, DNS blocklists, GPC |
| **P8** | AI layer (on-device + BYOK) | 4 | **W4 – Ship** | Summarise, Ask-this-page, follow-ups |
| **P9** | Beta release | 3 | W4 | Play internal→closed beta, store assets |
| | **Total** | **30** | | |

Solo = 30 working days (6 weeks at 5 d/week). 2 devs (one UI, one platform) = ~17 days.
3 devs = ~12 days. **P0–P4 = the "Lumina look" MVP in ~10 dev-days.**

---

## 3. Phases in detail

### P0 — Fork, rebrand, strip telemetry, green build (1 day)

*Enna panrom: DDG-a fork panni, peru maathi, tracking code-a neekki, build-a pass pannuvom.*

**Do**
1. `git clone --depth 1 https://github.com/duckduckgo/Android.git lumina-app` → copy into this repo
   (keep upstream history in a `upstream` remote; do **not** commit upstream `.git`).
2. Rename identity:
   - `applicationId "com.duckduckgo.mobile.android"` → `app.lumina.browser` (`app/build.gradle`)
   - `namespace` → `app.lumina.browser`; root `settings.gradle` `rootProject.name = "lumina"`
   - package rename is **optional in P0** (namespace + appId is enough to install side-by-side);
     full package rename is a P1 chore via `sed` + IDE refactor.
3. Strings/branding: `app/src/main/res/values/strings.xml` — app name, about strings, help URLs.
   Delete DuckDuckGo logos: `res/drawable*/ic_launcher*`, `dax_logo*`, `duck` illustrations.
4. Icons: new Lumina adaptive icon (see `02-LIQUID-GLASS-SPEC.md §7`).
5. Telemetry strip (this alone cuts startup + ~2 MB):
   - remove `implementation project(':pixels-impl')` style lines and the `Pixel` interfaces,
   - delete `attributed-metrics`, `survey`, `anrs`, `internalfeedback`, `:app-store` from the
     root module discovery (they auto-register by directory — deleting the folder is enough),
   - `BrowserTabViewModel` / `BrowserViewModel` pixel calls: replace with a no-op `LuminaTelemetry`.
6. Font: add `Inter`/`Manrope` (OFL) as `DuckSans` substitute — the build already substitutes a
   stub when the private font repo creds are missing, keep that path.
7. Build: `./gradlew :app:assembleInternalDebug` with JDK 21. Install and confirm it opens a page.

**Definition of done**
- APK installs next to DuckDuckGo, opens, loads a site, blocks trackers, `adb logcat` has zero
  pixel/telemetry network calls (verify with mitm or `adb shell dumpsys netstats` diff).
- `./gradlew jvm_tests` green.

**Risks:** private font repo / `node_modules` assets (`@duckduckgo/content-scope-scripts`,
`privacy-dashboard`) — run `npm ci` first; if it fails, stub the asset dirs in `build.gradle`.

---

### P1 — Slim the graph, CI, baseline metrics (2 days)

*Enna panrom: vendadha modules-a neekki, build-a fast aakki, speed-a measure pannuvom.*

**Do**
1. Module diet — target **≤ 60 modules**, one variant per store:
   - **keep:** `common-utils`, `browser-api`, `browser-mode`, `app-database`, `app-tracking-protection`
     (tracker blocking), `ad-blocking`, `autoconsent`, `broken-site`, `duckplayer`, `newtabpage`
     pieces you keep, `android-design-system` (until P2 replaces it), `statistics`/`subscriptions` **only** if
     you keep the Pro funnel (recommended: drop for v1), `duckchat`.
   - **drop:** `vpn*`, `email*`, `subscriptions*`, `subscription-*`, `remote-messaging` (unless
     needed for blocklists), `sync*`, `macos`/`windows` helpers, `survey`, `anrs`, `pixels`,
     `attributed-metrics`, `internalfeedback`, `devicemigration`, `autofill` (optional), `widget`.
   - after deleting, `settings.gradle` auto-discovery picks up the rest — nothing to edit.
2. Flavors → **one**: `lumina` (release/debug). Delete `play`/`fdroid` splits, keep
   `installInternalRelease` renamed to `installLuminaRelease`.
3. CI: GitHub Actions — `jvm_tests`, `assembleRelease`, cache Gradle + `~/.gradle`, upload APK artifact.
4. **Baseline metrics** (must exist before you optimise):
   - `androidx.benchmark.macro` StartupTimingMetric + FrameTimingMetric on a real mid-range device,
   - APK size via `assembleRelease` + `apkanalyzer apk summary`,
   - record numbers in `docs/baseline.md`. Everything after this is judged against it.

**DoD:** ≤ 60 modules, CI < 15 min cold, `docs/baseline.md` committed, one APK per build type.

---

### P2 — `lumina-ui` glass design system (4 days)

*Enna panrom: Kyant0 glass engine-a vendor panni, Lumina-ku oru design system kattuvom.*

**Do**
1. Vendor `backdrop` (Apache-2.0) as `:backdrop` module **or** depend on
   `io.github.kyant0:backdrop` from Maven Central. Vendoring is safer: you get `effects/lens`,
   `highlight`, `shadow` and can patch them.
2. New module `lumina-ui` (Compose) containing:
   - `LuminaTheme` (dark/light + dynamic color opt-out), tokens (see `02-…§2`),
   - `GlassSurface`, `GlassOmnibar`, `GlassBottomBar`, `GlassSheet`, `GlassChip`, `GlassButton`,
     `GlassFab` — all built on `Modifier.drawBackdrop(...)`,
   - `HybridBackdrop` (see next point),
   - motion: `LuminaSpring`, `pressScale()`, `IconMorph`, `IconBounce`, `FireMorph`.
3. **Solve the WebView-glass problem now, not in P4.** `rememberLayerBackdrop()` captures a
   Compose `GraphicsLayer` — a `WebView` renders through a Surface/TextureView and cannot be
   captured that way. So `lumina-ui` ships `HybridBackdrop`:
   - Compose content above the page → real `LayerBackdrop` refraction,
   - over the WebView → a **downscaled bitmap backdrop** (`WebView.draw(Canvas)` into a ≤40%
     scale bitmap, re-captured on scroll-idle / page-finished, never during fling) fed to
     `CanvasBackdrop`, with an automatic fallback to frosted tint when the capture is stale.

**DoD:** a demo screen (`:lumina-ui:gallery` or `debug` `LuminaGalleryActivity`) shows every
component with 60/120 fps, on API 28 (fallback), 31 (RenderEffect blur), 33+ (AGSL refraction).
Frame-time trace committed to `docs/`.

---

### P3 — Native glass NTP / home page (3 days)

*Enna panrom: ippo irukka static logo page-a neekki, glass home page kattuvom.*

**Do**
1. Replace `include_new_browser_tab.xml` content: keep `newTabLayout` / `newTabPage` ids (the
   rest of the app binds them), swap the inner views for a `ComposeView` hosting `LuminaNtp`.
2. `LuminaNtp` (design in `03-…§3`):
   - floating glass search field (tap → focuses omnibar, no page navigation),
   - **AI pill** ("Ask Lumina") → opens glass sheet with chat,
   - tracker-blocked counter card (live from `AppTrackingProtection` / `TrackerDetector`),
   - favourites rail (glass cards, drag-to-reorder later), recent history (private: local only),
   - wallpapers: 6 gradient meshes + user image (blurred as ambient backdrop).
3. Delete/disable the remote-NTP path so the home page never round-trips to the network.
4. Motion: staggered entrance (30 ms offset), logo parallax on pull, spring on card press.

**DoD:** home page renders in < 120 ms cold (no network), 100% Compose, glass over wallpaper,
0 jank frames in a 5 s scroll trace.

---

### P4 — Glass chrome: omnibar, tabs, bottom bar (3 days)

*Enna panrom: mukkiyamana UI — address bar, tabs, bottom bar — glass + motion.*

**Do**
1. `view_omnibar.xml` → `ComposeView` with `GlassOmnibar`:
   - collapsed pill ↔ expanded panel (spring `low-stiffness`, 380 ms, damping 0.78),
   - suggestions list with per-item favicon micro-bounce, highlight blob that follows the finger,
   - shield chip (tap → site shield sheet), refresh→stop morph icon, "AI" segmented toggle.
2. Tab switcher: glass card deck, `Modifier.drawBackdrop` per card, pinch-to-grid, swipe-to-close
   with velocity-based spring, tab count badge morph.
3. Bottom bar: `GlassBottomBar` with 4 items + moving highlight; hide-on-scroll with
   `NestedScrollConnection`.
4. **Icon motion spec** (`02-…§5`): press scale 0.92 + haptic tick, icon morphs (refresh→stop,
   shield→shield-check, play→pause), path-morph via `androidx.graphics:graphics-shapes`.

**DoD:** every interactive element has press state + spring; chrome never drops below 60 fps
during scroll (FrameTimingMetric, p95 ≤ 16.6 ms); no layout thrash on omnibar expand.

---

### P5 — SERP v2: glass skin + native result cards (5 days) ← *the "old results" fix*

Full detail in `03-NTP-AND-SERP-REDESIGN.md`. Summary:

1. **A. Skin (1.5 d):** inject `lumina-skin.css` + `lumina-skin.js` at `document_start`
   (`WebViewRequestInterceptor` / content-scope injection path already exists). Glass cards,
   20 dp radii, real type scale, sticky glass search bar, grouped sections, hide the ad clutter.
2. **B. Native cards (2.5 d):** `html.duckduckgo.com/html/?q=` parsed with Jsoup into Compose
   `GlassResultCard`s (favicon, title, url chip, snippet, tracker count per result), plus
   **Instant Answer** cards from `api.duckduckgo.com/?q=…&format=json` (public, keyless).
   WebView stays as fallback + for the full page.
3. **C. AI layer (1 d):** "Lumina Answer" — top-3 result snippets → on-device summary with
   source chips, streaming text, follow-up chips.

**DoD:** results paint in ≤ 600 ms on a 4G profile; native SERP path used for ≥ 80% of queries;
skin never causes layout shift > 0.05 CLS; fallback works with JS disabled.

---

### P6 — Performance pass (3 days)

*Enna panrom: speed-a measure panni, tune pannuvom.*

**Do**
1. **Startup**: lazy-init Dagger/Metro graphs, move `WorkManager` + blocklist DB init off the
   critical path, `ProfileInstaller` baseline profile generated from a startup macrobenchmark.
2. **WebView**: warm pool of 1 pre-created WebView (created during splash), `rendererPriority`
   policy, `setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false)` while foreground,
   `WebView.setWebContentsDebuggingEnabled(false)` in release, hardware-accelerated canvas,
   `--disable-javascript` for NTP/error pages.
3. **Glass budget**: ≤ 2 backdrop layers visible, backdrop bitmap ≤ 40% scale, blur sigma
   ≤ 12 px, skip blur during scroll fling (`Modifier.drawWithCache` + `isScrolling` gate).
4. **APK diet**: R8 full mode, `resourceConfigurations` = en+ta, remove unused drawables,
   `<shrinkResources true>`, `split by abi` off (one universal), 25 MB target.

**DoD:** cold start ≤ 700 ms (mid-range, cold), TTI ≤ 1.0 s, p95 frame ≤ 16.6 ms,
APK ≤ 25 MB, memory (PSS) ≤ 250 MB with 5 tabs. Numbers in `docs/baseline.md` as before/after.

---

### P7 — Privacy hardening + shields UI (2 days)

*Enna panrom: privacy-a innum strong aakki, adha user-ku kaattu.*

**Do**
1. Zero first-party telemetry (already gone in P0) + in-app "Lumina sends nothing" proof screen
   (list of outbound endpoints, live packet counter of blocked requests).
2. Keep & surface: tracker blocking (`app-tracking-protection`), cookie-consent autoconsent,
   HTTPS-only, GPC signal, fingerprint noise (canvas/audio/WebGL), link tracking param removal.
3. **Glass shield sheet**: per-site toggles, blocked-request list with reasons, "why" explainer.
4. Blocklists: on-device DNS-level lists from a signed bundle, weekly delta updates, no identifier.

**DoD:** EFF Cover Your Tracks = strong protection; zero analytics domains in release network log;
shield sheet reachable in 1 tap from omnibar.

---

### P8 — AI layer (4 days)

*Enna panrom: AI-a natively merge pannuvom — links illa, answer.*

**Do**
1. **On-device first**: Gemini Nano via **AICore / ML Kit GenAI** (`com.google.mlkit:genai-summarize`,
   `genai-proofreading`…) when `AICore` is available; fall back to **BYOK** (user's own OpenAI /
   Anthropic / Gemini / Ollama endpoint, key in Android Keystore, never logged).
2. Features: summarise page, "Ask this page", SERP answer with citations, follow-up conversation,
   translate selection, rewrite selection, tab-group summariser.
3. Reuse existing `duckchat` plumbing (`app/.../browser/duckchat/`): keep swipe-up gesture,
   replace the WebView chat with a native glass chat sheet.
4. Guardrails: every AI output carries source chips; "on-device" badge when no network egress;
   streaming with cancellation; token/time caps.

**DoD:** summarise works offline (device with AICore); with BYOK, zero requests without user
action; latency to first token ≤ 800 ms cloud / ≤ 1.5 s on-device.

---

### P9 — Beta release (3 days)

*Enna panrom: legal + store + stability.*

1. `docs/04-LEGAL-AND-RELEASE.md` checklist: NOTICE regenerated, `backdrop` + upstream attribution
   in an in-app "Open source licences" screen, **DuckDuckGo name/logo/marks removed**, DuckSans gone.
2. Play Console: new app id, data-safety = "no data collected", privacy policy URL, screenshots
   (glass hero, NTP, SERP, AI sheet), feature graphic.
3. Stability: Firebase Crashlytics **or** self-hosted Sentry with explicit opt-in; ANR watch;
   Maestro smoke flows from `.maestro/` reused for the new flows.
4. Closed beta → 500 testers → crash-free ≥ 99.5% for a week → open beta.

---

## 4. Wave view (for planning the calendar)

```
W1  Skeleton   P0 ▓ P1 ▓▓            → "it builds, it's ours, we have numbers"
W2  Glass      P2 ▓▓▓▓ P3 ▓▓▓ P4 ▓▓▓ → "it looks like Lumina"  ← demo this to people
W3  Search+AI  P5 ▓▓▓▓▓ P6 ▓▓▓ P7 ▓▓ → "it feels fast and private"
W4  Ship       P8 ▓▓▓▓ P9 ▓▓▓        → "beta"
```

The demo people fall in love with is **end of W2 (≈10 dev-days)**. Everything before that is
plumbing; everything after is what makes it a real product.

---

## 5. Performance budget (non-negotiable, measured in P1 and enforced in P6)

| Metric | Budget | How |
|---|---|---|
| Cold start (mid-range, 4 GB) | ≤ 700 ms | macrobenchmark `StartupTimingMetric` |
| Home page first frame | ≤ 120 ms | no network, Compose only |
| TTI on result page | ≤ 1.0 s | native cards path |
| Frame p95 (chrome) | ≤ 16.6 ms | `FrameTimingMetric` |
| Janky frames | < 1% | Perfetto / JankStats |
| APK | ≤ 25 MB | apkanalyzer |
| PSS, 5 tabs | ≤ 250 MB | `dumpsys meminfo` |
| Backdrop layers | ≤ 2 visible | lint rule + code review |

---

## 6. Target repo layout after P2

```
lumina/
├── app/                     # from upstream, rebranded, slimmed
├── lumina-ui/               # NEW: glass design system (Compose)
├── lumina-search/           # NEW: SERP skin + native cards + instant answers (P5)
├── lumina-ai/               # NEW: on-device + BYOK (P8)
├── backdrop/                # vendored glass engine (Apache-2.0)
├── app-tracking-protection/ ad-blocking/ autoconsent/ ...  # kept upstream modules
├── docs/
└── glass/                   # reference snippets from this planning repo
```

---

## 7. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Glass over WebView can't be captured | Visual break / jank | `HybridBackdrop` bitmap snapshot (P2), never capture during fling |
| AGSL refraction needs API 33+ | Looks flat on API 28–32 | 3-tier fallback (tint → RenderEffect blur → lens) shipped in P2 |
| Upstream is 207 modules / heavy DI (Metro) | Slow builds, hard to slim | Slim first (P1), measure every phase |
| DDG trademark / brand | Store rejection, legal | Rebrand fully in P0, no DDG marks, no duck.ai branding → own "Lumina AI" |
| SERP scraping (`html.duckduckgo.com`) rate limits / ToS | Native cards break | Skin path (A) is the always-works fallback; keep WebView authoritative |
| DuckSans licence | Cannot redistribute | Replace with OFL font (Inter/Manrope), delete font assets |
| Private GitHub Packages deps (font, some libs) | Build fails for others | Stub/fork those modules in P0 |
| 2-hour expectation vs 30 dev-days | Frustration | Publish the 2-hour sprint as a *slice* with a visible demo, not a product |

---

## 8. What "v1.0 Lumina" means (definition of done for the whole rebuild)

- [ ] Glass home page, glass chrome, glass SERP cards — one coherent motion language
- [ ] Cold start ≤ 700 ms, results ≤ 1 s, APK ≤ 25 MB
- [ ] Zero first-party telemetry, tracker blocking on by default, per-site shield sheet
- [ ] AI: summarise / ask / cite, on-device when available
- [ ] No DuckDuckGo marks, DuckSans removed, NOTICE + OSS licence screen complete
- [ ] Play closed beta live, crash-free ≥ 99.5%
