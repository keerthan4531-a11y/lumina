# The 2-Hour Sprint — a vertical slice you can demo

**Promise:** in 120 minutes you get an **installable `Lumina` APK** that (1) is yours, (2) has no
DuckDuckGo telemetry, (3) has a **glass home page + glass omnibar** with spring motion.
**Not** promised: a finished browser. That's the 10 phases in `00-MASTER-PLAN.md`.

Rule for the whole sprint: **one long build, everything else in parallel.** The first
`assembleInternalDebug` takes 10–25 min (Gradle downloads). Start it by minute 40 and keep
writing code while it runs.

---

## Pre-flight (before the clock starts, 15 min)

```bash
# JDK 21 is mandatory (Metro compiler plugin)
java -version            # must say 21
# Android SDK 36 + platform-tools + a device/emulator on API 33+
adb devices
# node 20+ (content-scope-scripts / privacy-dashboard assets)
node -v
```

Have these ready or the sprint dies at minute 45:
- a device (API 33+ gives you real AGSL refraction; API 31–32 = blur only; 28–30 = tint)
- internet for `google()` + `mavenCentral()` + `registry.npmjs.org`
- a wallpaper image you like (the glass needs something behind it)

---

## 0:00 → 0:10 — Clone & workspace

```bash
mkdir -p ~/lumina-build && cd ~/lumina-build
git clone --depth 1 https://github.com/duckduckgo/Android.git app
cd app && git switch -c lumina
npm ci                      # content-scope-scripts + privacy dashboard assets
```

If `npm ci` fails, note it and continue — `build.gradle` tolerates missing asset dirs in debug,
but `assembleInternalDebug` may complain about `node_modules/@duckduckgo/...`. Quick fix:

```bash
mkdir -p node_modules/@duckduckgo/content-scope-scripts/build/android/pages
mkdir -p node_modules/@duckduckgo/privacy-dashboard/build/app
```

---

## 0:10 → 0:25 — Rebrand (name it Lumina)

```bash
# applicationId + app label
sed -i.bak 's/applicationId "com.duckduckgo.mobile.android"/applicationId "app.lumina.browser"/' app/build.gradle
sed -i.bak 's/rootProject.name = "androidRoot"/rootProject.name = "lumina"/' settings.gradle

# app name (all locales; keep it simple, en only is fine for the demo)
python3 - <<'PY'
import re, pathlib
p = pathlib.Path("app/src/main/res/values/strings.xml")
s = p.read_text()
s = re.sub(r'<string name="appName">[^<]*</string>',
           '<string name="appName">Lumina</string>', s)
p.write_text(s)
PY
```

**Icon (5 min):** replace `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` with a simple
adaptive icon: a `#0B0F1A` background + a white glass-drop foreground vector. Anything is better
than the duck. (Real icon work = `02-LIQUID-GLASS-SPEC.md §7`.)

**Do not** attempt a package rename now. `applicationId` change is enough to install side by side
with DuckDuckGo.

---

## 0:25 → 0:40 — Strip the weight (speed + privacy, one move)

```bash
# telemetry / funnel / survey modules — deleting the folder is enough,
# settings.gradle auto-discovers modules by directory
rm -rf pixels attributed-metrics survey anrs internalfeedback devicemigration
rm -rf vpn subscriptions subscription-* email-* sync-* widget app-store
```

Then remove the now-dangling `implementation project(':...')` lines:

```bash
grep -rn "project(':pixels\|project(':vpn\|project(':survey\|project(':anrs\|project(':attributed\|project(':internalfeedback\|project(':devicemigration\|project(':widget\|project(':app-store" --include=build.gradle .
# delete those lines with your editor, then:
./gradlew help -q          # configuration must succeed in <60s; fix what it complains about
```

`./gradlew help` is the **cheap syntax check** — it configures every module without compiling.
Iterate on it until green; only then start the expensive build.

**Flavors (optional, +5 min if it configures cleanly):** comment out `internal`/`fdroid` blocks in
`app/build.gradle` `productFlavors` and rename `play` → `lumina`. If configuration fights you,
skip it — variants don't block the demo.

---

## 0:40 → 0:45 — Kick off the long build 🔥

```bash
./gradlew :app:assembleInternalDebug \
  -x lint -x test -x spotlessCheck \
  --build-cache --parallel \
  > /tmp/lumina-build.log 2>&1 &
tail -f /tmp/lumina-build.log
```

Leave it running. **Do not touch `app/build.gradle` while it builds** — edit only new files.
Expected: 10–25 min cold. Meanwhile go to 0:45.

---

## 0:45 → 1:20 — Glass layer (while Gradle chews)

Create the module skeleton (build file only — no Gradle sync needed yet):

```
lumina-ui/
├── build.gradle.kts
└── src/main/java/app/lumina/ui/glass/
    ├── LuminaTokens.kt
    ├── LuminaGlass.kt      # GlassSurface, GlassOmnibar, GlassPill
    ├── Motion.kt           # LuminaSpring, pressScale, IconBounce
    └── LuminaNtp.kt        # the glass home page
```

The reference code for all four files is in [`../glass/`](../glass/). Copy them in and:

1. **Add the glass engine.** Fastest path: Maven Central.
   ```kotlin
   // lumina-ui/build.gradle.kts
   dependencies {
       implementation("io.github.kyant0:backdrop:<latest>")   // check Maven Central
       implementation("androidx.compose.foundation:foundation:1.9.0")
       implementation("com.kyant:shapes:<latest>")            // or inline your own Shape
   }
   ```
   If the artifact doesn't resolve (AGP/compileSdk mismatch), vendor the module instead:
   ```bash
   git clone --depth 1 https://github.com/Kyant0/AndroidLiquidGlass.git /tmp/glass
   cp -r /tmp/glass/backdrop ./backdrop
   # drop the KMP targets you don't need: keep only android+common, minSdk 28
   ```
2. **`LuminaNtp`** — glass search field, AI pill, tracker counter card, favourites rail on a
   gradient wallpaper. This is the money shot.
3. **Wire it into the home page**: in `app/src/main/res/layout/include_new_browser_tab.xml`, inside
   `@+id/newTabContainerLayout`, replace the children with a single
   `androidx.compose.ui.platform.ComposeView` and set its content in `BrowserTabFragment`:
   ```kotlin
   binding.newTabPage.findViewById<ComposeView>(R.id.luminaNtp).apply {
       setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
       setContent { LuminaTheme { LuminaNtp(onSearch = { viewModel.onOmnibarInput... }) } }
   }
   ```
   Time-box this to 20 minutes: if the existing binding fights you, **hack it** — add the
   `ComposeView` as an overlay in `activity_browser.xml` with `visibility` toggled by the existing
   `newTabPage` state. Ugly is fine; it's a demo slice.

---

## 1:20 → 1:35 — Build round 2, install, fix

```bash
# after the first build succeeded
./gradlew :app:installInternalDebug -x lint -x test --build-cache
adb shell am start -n app.lumina.browser/com.duckduckgo.app.browser.BrowserActivity
```

Typical failures and their 30-second fixes:

| Error | Fix |
|---|---|
| `DuckSans` / GitHub Packages 401 | the build auto-substitutes a stub when creds are absent; if not, delete the font deps in `app/build.gradle` |
| `node_modules/... does not exist` | run the `mkdir -p` stubs above |
| `minSdk 28` vs backdrop `21` | fine, ignore |
| `ComposeView` inside a `ConstraintLayout` with `layout_behavior` | wrap in `FrameLayout` with `match_parent` |
| `Unresolved reference: backdrop` | you're on the vendored path but haven't added `include ':backdrop'` — auto-discovery needs `backdrop/build.gradle.kts` at repo root (it is) |

---

## 1:35 → 1:50 — Motion pass (the 20% that reads as 80% premium)

Add only these, in this order, and stop:
1. `pressScale()` on every glass surface (0.92, spring `dampingRatio 0.75`, `stiffness 900`).
2. Omnibar expand/collapse spring (380 ms, damping 0.78).
3. NTP staggered entrance: cards fade+slide 24 dp, 30 ms offset each.
4. One icon morph: refresh ⇄ stop (or shield ⇄ shield-check) with `graphics-shapes`.
5. Haptic tick on press: `HapticFeedbackType.LongPress` on the glass buttons.

---

## 1:50 → 2:00 — Record the demo & commit

```bash
git add -A && git commit -m "Lumina P0 slice: rebranded, telemetry stripped, glass NTP + omnibar"
adb shell screenrecord --time-limit 20 /sdcard/lumina.mp4
```

Demo script (60 s, say it exactly like this):
1. "This is Lumina — DuckDuckGo's privacy engine underneath, nothing of their branding."
2. Cold start → glass home page appears, cards stagger in. *"The home page is native now —
   zero network round-trip, 120 Hz motion."*
3. Type → glass omnibar expands, suggestion blobs follow the finger.
4. Tap AI pill → glass sheet. *"Ask instead of search."*
5. Open a site → shield chip shows blocked trackers. *"Same protection, new skin."*

---

## What you deliberately did NOT do in 2 hours

- no package rename, no full module diet, no SERP changes, no AI implementation, no benchmarks.
- The SERP (result page) still looks old — that's **P5**, 5 dev-days, and it needs the
  skin-injection work described in `03-NTP-AND-SERP-REDESIGN.md`. Anyone who says otherwise is
  selling you a screenshot, not a browser.
