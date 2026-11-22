# Home page & Results — why they look old, and how Lumina fixes them

*Ithu dhaan unga main complaint: home page-um, search result page-um 2014 style-la irukku.*

---

## 1. Diagnosis (why it still looks old after all these years)

| # | Symptom you see | Root cause in the code |
|---|---|---|
| 1 | Home page = plain background + big logo, no motion | `include_new_browser_tab.xml` is a static XML NTP with a Dax logo + a "return hatch" — native, but deliberately minimal, zero animation |
| 2 | Results page = stacked blue links, 2014 typography | **The results page is `duckduckgo.com` HTML inside `DuckDuckGoWebView`.** The app owns the chrome, the website owns the results |
| 3 | Results appear late / flash white | full page navigation: HTML + CSS + JS + favicons, then content settles (CLS) |
| 4 | Ad slot / feedback prompts sit above the first result | the website's layout, unchanged by the app |
| 5 | No grouping, no cards, no per-result trust info | website markup is one flat `<article>` list |
| 6 | Dark mode = inverted website CSS | the web page's own theme, not the app's |
| 7 | Search feels disconnected from the browser | omnibar → `Site` load; there's no native "search model" between them |
| 8 | AI is a separate trip | duck.ai is a destination page, not an inline answer |
| 9 | Nothing is tappable/morphable | there are no native result views at all |
| 10 | No personalisation affordances that respect privacy | no local shortcuts/"used often" surface on the SERP |

**Therefore:** you cannot fix #2–#6, #9 by theming Kotlin. You must either **restyle the document**
(layer A), **replace the document** (layer B), or **layer native UI over it** (layer C).

---

## 2. The three layers (ship in this order, each one is usable alone)

| Layer | What | Effort | Risk | Works when network/ToS changes |
|---|---|---|---|---|
| **A. Skin** | Inject `lumina-skin.css` + `lumina-skin.js` at `document_start` into the SERP | 1.5 d | low | degrades to plain DDG page |
| **B. Native cards** | Compose glass result list fed by a `SerpProvider` (HTML parse or JSON API) | 2.5 d | medium | provider switchable |
| **C. AI answer** | "Lumina Answer" card: summary of top results, citations, follow-ups | 1 d | low | falls back to layer B list |

Ship A alone and you already get "wow". Ship A+B and it stops being a website. Ship C and it stops
being a search engine — it becomes an answer engine.

---

## 3. New home page (NTP) — native, glass, zero network

**Principle: the home page must never load a URL.** Today's NTP already avoids the network; Lumina's
version replaces the logo with a *surface you can act on*.

```
┌───────────────────────────────────────────┐
│  Good evening                       ⛨ 12   │   ← greeting + trackers blocked today
│                                            │
│   ╭──────────────────────────────────╮     │   ← GLASS SEARCH FIELD (tap → omnibar focus)
│   │  🔍  Search or type a URL    ✨AI │     │      AI pill inside the field
│   ╰──────────────────────────────────╯     │
│                                            │
│   ╭─────────────────╮ ╭─────────────────╮  │   ← glass "quick" cards, staggered entrance
│   │ ✨ Ask Lumina   │ │ ⛨ Shields       │  │
│   │ summarise page  │ │  1,204 blocked  │  │
│   ╰─────────────────╯ ╰─────────────────╯  │
│                                            │
│   Favourites ······  [glass favicon rail]  │
│                                            │
│   Recent ··········  [3 rows, local only]  │
└───────────────────────────────────────────┘
```

**Implementation notes**
- `ComposeView` inside `newTabContainerLayout` (ids must survive — the rest of the app binds them).
- Wallpaper: 6 bundled gradient meshes + optional user image, **already downscaled and blurred**
  (the backdrop must be cheap; never blur at capture time per frame).
- Greeting + tracker counter come from local data: `trackerBlockingCount()`, `historyDao` (local only).
- No remote NTP config, no "brand design" remote assets — that's a startup tax and a privacy smell.
- Enter via 3 paths: app start, new tab, and "home" button in the omnibar → identical composition,
  zero re-inflation (keep the ComposeView alive, just re-show).

**DoD:** cold first frame ≤ 120 ms, no network requests on home, 100% of the old NTP features
(shortcuts, favourites, fire button) still reachable.

---

## 4. Layer A — the skin (`lumina-skin.css`)

### 4.1 Injection point (no white flash)

Use `androidx.webkit`'s document-start injection so the CSS exists before first paint:

```kotlin
// app/.../browser/serp/LuminaSerpSkin.kt
WebViewCompat.addDocumentStartJavaScript(
    webView,
    LuminaSkinScript.build(theme = currentTheme, tier = glassTier),
    setOf("*")                                   // or restrict to duckduckgo.com origins
)
```

Keep the CSS in **`assets/serp/lumina-skin.css`** (≈14 KB) and the JS in `assets/serp/lumina-skin.js`
(≈3 KB, only adds classes + observes DOM for lazy/infinite results). Remote-updatable via a signed
JSON in your own CDN, **never** executed from the network directly — only text tokens are remote.

### 4.2 What the skin changes

| Website element | Lumina skin |
|---|---|
| page background | transparent + inherited app gradient (`html{background:transparent}`) |
| search bar at top | sticky **glass** bar: `backdrop-filter: blur(14px) saturate(140%)`, 1 px rim, radius 999 px |
| result article | glass card: radius 20 px, 12 px padding, `backdrop-filter: blur(6px)`, 1 px `rgba(255,255,255,.14)` border, hover lift 1 px |
| result title | 20/26 px, weight 600, accent on hover — **not** the browser-blue default link |
| snippet | 15/22, 72% opacity, max 3 lines with `-webkit-line-clamp` |
| result URL | 12 px chip with favicon, `border-radius: 8px`, background 8% white |
| ads / feedback prompts | `display:none` (see §7 — do this only for first-party clutter, never to hide results) |
| instant answer module | glass card, 24 px radius, own gradient wash by category |
| related searches | glass chips, horizontal scroll, snap |
| infinite scroll sentinel | skeleton cards (glass pulse) instead of a spinner |
| dark mode | `prefers-color-scheme` + an app-injected `data-lumina-theme` attribute |

### 4.3 Selector strategy (this is where these projects die)

DDG's markup changes. So: **never hardcode selectors in Kotlin.**

1. `assets/serp/selectors.json` — a versioned map:
   ```json
   {
     "version": 3,
     "result": ["article[data-testid='result']", ".result", "div.web-result"],
     "title":  ["[data-testid='result-title-a']", ".result__a"],
     "snippet":["[data-result='snippet']", ".result__snippet"],
     "url":    ["[data-testid='result-url']", ".result__url"],
     "ia":     ["[data-testid='zci']", ".zci"]
   }
   ```
2. Each key is a **list** — the first selector that matches wins; if none match, the skin no-ops
   (fail open, never break the page).
3. A dev-only "selector doctor" screen: loads a query, reports which selectors matched, exports an
   updated JSON. Run it after every DDG redesign; takes 5 minutes instead of a release.
4. CSS uses **attribute/`:has()`-based** rules where possible (e.g. `article:has(a[data-testid])`)
   so a class rename doesn't kill the design.

### 4.4 Performance rules for injected CSS

- One `<style>` tag, `document_start`, ≤ 20 KB gzipped.
- No web fonts (the app's own typeface is not available inside the page — use
  `font-family: -apple-system, 'Inter', system-ui`).
- `backdrop-filter` on ≤ 8 elements visible; it's the single most expensive CSS property. Cards use
  a plain translucent background; only the sticky bar + IA card get real blur.
- No layout shift: reserve card min-height so images/favicons fill in without reflow (target CLS < 0.05).

---

## 5. Layer B — native result cards

### 5.1 Architecture

```kotlin
// lumina-search/
interface SerpProvider {
    suspend fun search(query: String, page: Int = 0): SerpPage
}
data class SerpPage(val results: List<SerpResult>, val instantAnswer: InstantAnswer?, val related: List<String>)
data class SerpResult(val title: String, val url: String, val snippet: String, val favicon: String?, val source: String)
```

Three providers, chosen in settings (default = whatever is most robust):

| Provider | Source | Notes |
|---|---|---|
| `DdgHtmlProvider` | `https://html.duckduckgo.com/html/?q=…` parsed with **Jsoup** | lightest DDG surface (~30 KB, no JS), no key, **but** unofficial: rate-limited, no SLA — see §7 |
| `BraveApiProvider` | Brave Search API (JSON, key) | stable, has snippets + no ads, key stored in Keystore |
| `SearxProvider` | user's own SearXNG URL `/search?q=…&format=json` | self-host = privacy purist path |

The UI never knows which provider it's talking to. This is the whole point: **Lumina is a browser
with a pluggable search layer**, not a skin of one company's website.

### 5.2 The card

```
┌────────────────────────────────────────────┐
│ 🟢 example.com                    ⛨ 3      │  favicon + domain chip + trackers it tried
│ How the thing actually works               │  20/26 semibold, 2 lines
│ A short, readable, two-line explanation of  │  15/22 at 72%
│ what the reader actually needs to know.     │
│                                     › open  │
└────────────────────────────────────────────┘
```
- Staggered entrance 24 ms/card, press scale 0.98, long-press → preview sheet (glass).
- Domain chip colour = trust heuristic from the blocklist DB (known tracker domain → amber).
- "Open" navigates the WebView; **the WebView is still the truth** — if a result fails, it opens the
  real page. Native cards are a *reader*, not a proxy.

### 5.3 Hybrid rendering (recommended after week 1)

Keep the WebView loaded underneath (for scroll-back, "open in page", JS-required modules) and put the
native card list **on top** in a Compose layer that hands off on scroll-end. This gives you:
- native paint ≤ 600 ms (cards from JSON/HTML at ~40 KB),
- the real page ready for anything the cards can't do,
- no "which engine am I in" confusion for the user.

### 5.4 Instant answer (`api.duckduckgo.com/?q=…&format=json&no_html=1`)

Public, keyless, returns Abstract/Definition/Answer/RelatedTopics. Render as a **`GlassAnswerCard`**
above the list: big statement, 2-line abstract, source chip, "expand" → full abstract. This single
card is what makes the SERP feel modern, and it's ~2 KB of JSON.

---

## 6. Layer C — the AI answer

```
┌──────────────────────────────────────────────┐
│ ✨ Lumina Answer            on-device · 1.1s │
│                                              │
│ Three sentences that actually answer the      │
│ question, written from the top three results.│
│                                              │
│ [1] example.com  [2] docs.example  [3] news  │
│ ────────────────────────────────────────────  │
│ ▸ What about pricing?   ▸ How does it compare?│
└──────────────────────────────────────────────┘
```

Flow:
1. Query → provider returns top N results (titles + snippets, ~4 KB).
2. **On-device first:** Gemini Nano via **AICore** (`com.google.mlkit:genai-summarize`) if the device
   supports it → summary never leaves the phone. Badge: `on-device`.
3. **BYOK fallback:** user's own endpoint/key (Settings → AI). Badge: `cloud · your key`.
4. Streaming tokens into the card, cancel button, citations mandatory (sentence → source index).
5. Follow-up chips come from the model's own follow-up suggestions, capped at 3.
6. **No answer is better than a wrong answer:** if the top results disagree or the confidence is
   low, render "Open results" instead of a summary.

---

## 7. Legal / ToS / ethics guardrails

- **Skinning** the page a user is already viewing in *their own browser* is standard ad-blocker /
  user-agent territory. Keep it **user-toggleable** and never remove organic results.
- **Parsing `html.duckduckgo.com`** is an unofficial interface. Don't ship it as the only path,
  don't hammer it (≥ 800 ms debounce, cache 5 min, no prefetch of next pages), and put the
  provider behind a setting with a clear label. Ship A + Brave/SearX as the recommended path.
- **Do not** strip DDG ads to re-monetise. Lumina v1 has no ads; if you add them, they're yours.
- **Trademark:** "DuckDuckGo", the duck logo and "duck.ai" are not yours — Lumina must not use any
  of them in UI, store listing or screenshots (see `04-LEGAL-AND-RELEASE.md`).

---

## 8. Rollout plan inside P5 (5 dev-days)

| Sub-sprint | Time | Output | Gate |
|---|---|---|---|
| S1 | 0.5 d | `lumina-skin.css` v1 + document-start injection + selector JSON | Loads a real SERP with glass cards, no FOUC |
| S2 | 1 d | selector doctor + dark mode + sticky glass bar + CLS fix | CLS < 0.05, 0 console errors |
| S3 | 2 d | `SerpProvider` + `DdgHtmlProvider` + native `GlassResultCard` list + hybrid handoff | results ≤ 600 ms on 4G profile |
| S4 | 1.5 d | `GlassAnswerCard` + instant answers + AI summary + follow-ups | answer ≤ 1.5 s on-device / ≤ 800 ms cloud |

Then measure (and put the numbers in `docs/baseline.md`):

| Metric | Before | Target |
|---|---|---|
| First result visible | ~1.8–2.5 s (full page) | ≤ 600 ms (native) |
| CLS | 0.15–0.3 | ≤ 0.05 |
| Result page bytes | 300–600 KB | ≤ 60 KB (native path) |
| Interactions with native motion | 0 | every card |
| AI answers without leaving the SERP | 0 | 100% |

---

## 9. Optional "wow" extras (after P9, not before)

- **Search-in-place previews:** long-press a card → glass peek sheet with the page's readable text
  (Readability + Compose), swipe up to open.
- **Local rank personalisation:** boost domains you actually visit (on-device only, never uploaded).
- **Ask-from-anywhere:** select text on any page → floating glass button → "Ask Lumina".
- **Tab summariser:** "summarise these 4 tabs" from the tab switcher.
- **Voice + camera search** into the omnibar with waveform icon motion.
