# Legal, licensing & release checklist

*Ithu boring-a theriyum, aana ithu illa-na Play Store-la reject aagum. 30 minutes-la mudinjidum.*

---

## 1. Upstream licence obligations (Apache-2.0)

`duckduckgo/Android` is **Apache-2.0** (`LICENSE`, `NOTICE`). You may fork, rename, rebrand and
sell. You must:

| Obligation | How Lumina satisfies it |
|---|---|
| Ship the licence text | `LICENSE` kept at repo root **and** in-app "Open source licences" screen |
| Preserve `NOTICE` | `NOTICE` keeps the DDG copyright lines **plus** your own modifications line ("Lumina modifications © 2026 …") |
| State changes | a short `CHANGES-LUMINA.md`: "rebranded, telemetry removed, UI replaced with Compose glass layer, modules removed" |
| Attribution for `backdrop` | Kyant0 `AndroidLiquidGlass` is also Apache-2.0 → same licence screen, retain its copyright |
| No trademark grant | Apache-2.0 §6: **no right to use DuckDuckGo's marks** → see §2 |

Do **not** remove DDG copyright headers from source files you keep. Add your own above/below.

---

## 2. Trademark & branding (the part that gets apps taken down)

Must change before any public build (`P0`, not `P9`):

| Asset | Where | Replace with |
|---|---|---|
| App name "DuckDuckGo" | `strings.xml` `appName`, store listing | **Lumina** |
| Duck logo / Dax | `res/drawable*`, onboarding, `HomeBackgroundLogo.kt`, `dax_logo*` | Lumina prism icon, no character mascot |
| "duck.ai" / "Duck.ai" strings | `duckchat` module, menus, shortcut labels | **Lumina AI** |
| About/help/privacy URLs to duckduckgo.com | `AboutDuckDuckGoActivity`, settings | your own site + upstream attribution link |
| Package/namespace `com.duckduckgo.*` | code | `app.lumina.*` (appId in P0; packages in P1) |
| Store screenshots containing DDG UI | Play listing | new screenshots of Lumina |

You **may** say in the store description: *"Built on the open-source DuckDuckGo Android browser
(Apache-2.0). Not affiliated with or endorsed by DuckDuckGo."* You may **not** put a duck in the icon,
call the app "Duck-something", or imply endorsement.

**Search results:** the results page is `duckduckgo.com` content. That's fine for a browser that
*visits* a site (that's what browsers do). What you must not do is present their results as your own
engine or hide their attribution while re-monetising (see `03-…§7`).

---

## 3. Fonts

`Duck Sans` is **proprietary** (see `NOTICE`): you may not redistribute it. The upstream build
substitutes a stub when the private font repo creds are absent — keep that path and ship an OFL
typeface instead:

- **Inter** (OFL) — closest neutral grotesque, best hinting, variable weight
- **Manrope** (OFL) — rounder, more "glass"
- **Instrument Sans** (OFL) — more character, great headline weight

Add to `NOTICE` and the licence screen. Set `android:fontFamily` in `lumina-ui` tokens only, so a
font swap is one file.

---

## 4. Telemetry strip — prove it, don't claim it

Removed in P0: `pixels`, `pixels-impl`, `attributed-metrics`, `survey`, `anrs`, `internalfeedback`,
`app-store` (metrics), plus every `pixel.fire(...)` call via a no-op `LuminaTelemetry`.

Verification (record it once, put the screenshot in the store listing):
```bash
# 1. no analytics hosts in the release APK
strings app-release.apk | grep -iE "pixel|amplitude|sentry|firebase|segment" | sort -u
# 2. live check: launch, search, browse 2 min, then
adb shell dumpsys netstats detail | grep -iE "duckduckgo|amplitude|sentry"
# 3. in-app: Settings → Privacy → "What Lumina sends" (live outbound-endpoint list)
```

Crash reporting: **off by default**, opt-in, self-hosted (Sentry) or Play Console vitals (no SDK).
If you ship Crashlytics, the data-safety form must say you collect crash data — and "Lumina sends
nothing" becomes a lie. Pick one and be consistent.

---

## 5. Permissions & Play policy

Reduce the manifest to what a browser needs, because Play scrutinises browsers:

| Permission | Keep? | Why |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | ✅ | browser |
| `CAMERA` | ⚠️ optional | only if you ship QR/photo search; else remove |
| `RECORD_AUDIO` | ⚠️ optional | voice search only |
| `ACCESS_FINE_LOCATION` | ❌ | geolocation can be granted per-site at runtime — don't predeclare |
| `POST_NOTIFICATIONS` | ⚠️ | downloads only |
| `FOREGROUND_SERVICE*` | ⚠️ | downloads/player only, with `dataSync`/`mediaPlayback` types declared |
| `QUERY_ALL_PACKAGES` | ❌ | remove — it's a Play-policy magnet |
| `SYSTEM_ALERT_WINDOW` | ❌ | remove unless a floating AI bubble is core |
| VPN / accessibility | ❌ | not in v1 (that's what the DDG VPN modules were) |

Play requirements for browsers specifically:
- declare "Browser" in the app category, provide a working address bar + multi-tab (you have it),
- **data safety**: "No data collected" only if §4 is true,
- privacy policy URL (required),
- target API 35/36 compliant (base is 36 ✅),
- content rating questionnaire, and a clear "AI-generated content" disclosure for Lumina Answer.

---

## 6. Release checklist (P9, ~3 days)

**Before upload**
- [ ] `applicationId` = `app.lumina.browser`, versionName `1.0.0-beta1`
- [ ] signed with your own release keystore (never commit it; use CI secrets)
- [ ] `LICENSE`, `NOTICE`, `CHANGES-LUMINA.md` at root
- [ ] in-app OSS licence screen lists DDG + backdrop + Kotlin/Compose/OkHttp/Jsoup…
- [ ] no DuckDuckGo marks anywhere (`grep -ri "duckduckgo\|duck\.ai\|dax" res/ strings.xml assets/`)
- [ ] DuckSans files removed from the APK
- [ ] telemetry verification (§4) captured
- [ ] APK ≤ 25 MB, cold start ≤ 700 ms on a mid-range device
- [ ] crash-free ≥ 99.5% over 3 days internal testing

**Store listing**
- [ ] title "Lumina — Private Browser" (60 ch), short desc (80 ch), full desc (4000 ch)
- [ ] screenshots ×8: home, glass omnibar expanded, SERP cards, AI answer, shield sheet, tabs,
      dark mode, settings → privacy
- [ ] 7-inch + 10-inch tablet screenshots (Play requires them for tablet visibility)
- [ ] feature graphic 1024×500, icon 512×512 with the prism
- [ ] privacy policy + AI disclosure

**Rollout**
- [ ] internal (you + 5) → closed (500) → open beta
- [ ] staged rollout 5% → 20% → 50% → 100%, watch ANR + crash rate per stage
- [ ] Play Console vitals thresholds: crash < 1%, ANR < 0.47%

---

## 7. Naming & package namespace cleanup (P1 chore, 2 hours)

```bash
# namespace + package rename (IDE "Refactor → Rename" is safer, but this is the sweep)
grep -rl "com\.duckduckgo" --include=*.kt --include=*.java --include=*.xml . \
  | xargs sed -i 's/com\.duckduckgo/app.lumina/g'
# then fix: AndroidManifest package refs, build.gradle namespaces, ProGuard rules, deep links,
# Robolectric config, and the `applicationId` you already changed in P0
./gradlew :app:assembleDebug -x lint -x test   # expect a wave of import errors, fix mechanically
```

Do this **once, early, in a dedicated branch** — it touches thousands of files and will conflict with
everything else if you leave it for later.
