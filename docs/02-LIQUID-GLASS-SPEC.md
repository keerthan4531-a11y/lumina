# Liquid Glass Spec — `lumina-ui`

Glass engine: **[`backdrop`](https://github.com/Kyant0/AndroidLiquidGlass)** (Apache-2.0) by Kyant0.
It gives you real refraction (`effects/lens` via AGSL), blur, vibrancy, specular highlight,
inner/outer shadow — all as a `Modifier` on Compose content.

```kotlin
// the whole library in one call
Modifier.drawBackdrop(
    backdrop = backdrop,          // what's *behind* the glass
    shape    = { Capsule() },
    effects  = { vibrancy(); blur(2.dp.toPx()); lens(24.dp.toPx(), 48.dp.toPx()) },
    highlight = DefaultHighlight,  // specular edge
    shadow    = DefaultShadow
)
```

The backdrop comes from the content layer you want to refract:

```kotlin
val backdrop = rememberLayerBackdrop()                 // Compose content
Box(Modifier.layerBackdrop(backdrop)) { /* wallpaper, page, hero image */ }
```

---

## 1. Hard truth first: glass over a WebView

`rememberLayerBackdrop()` captures a Compose **`GraphicsLayer`**. A `WebView` draws through a
Surface/TextureView, so **it is not in that layer** — glass floating over the page will refract
*the wrong thing* (or nothing).

**Lumina's answer — `HybridBackdrop` (ship in P2, three strategies):**

| Situation | Backdrop strategy | Cost |
|---|---|---|
| Glass over Compose content (NTP, sheets, tabs grid, settings) | `rememberLayerBackdrop()` — true refraction | 1 extra layer draw, cheap |
| Glass over the WebView (omnibar, bottom bar, floating chips) | **bitmap backdrop**: `WebView.draw(Canvas)` → bitmap at ≤ 40% scale, re-captured on *scroll idle* / `onPageFinished` / theme change, fed through `CanvasBackdrop` | one 0.4× offscreen draw per settle (~2–4 ms) |
| WebView is scrolling / flinging, or capture is stale | **frosted tint** (no refraction): `blur(8.dp)` + 12% white/black + 1 px inner stroke | GPU blur only |

Rules:
- **Never capture during a fling.** Gate on `isScrolling`; a stale-but-static backdrop is invisible
  to users, a janky one is not.
- Cap **2 visible backdrop layers** at once (omnibar + bottom bar = 2; a sheet replaces a bar, not adds).
- Cache the captured bitmap keyed by `(scrollX, scrollY, pageId)`; invalidate on layout change.

---

## 2. Tokens

```kotlin
object LuminaTokens {
    // shape
    val radiusPill = 999.dp
    val radiusCard = 24.dp
    val radiusSheet = 32.dp
    val radiusChip = 16.dp
    val strokeHairline = 1.dp          // never 0.5dp — invisible on mdpi

    // glass material
    val blurSigma = 10.dp              // 2dp for tiny chips, 10–14 for bars, 20 max for sheets
    val refractionDepth = 24.dp        // lens displacement
    val refractionStrength = 48.dp
    val tintLight = Color.White.copy(alpha = 0.55f)
    val tintDark  = Color(0xFF0B0F1A).copy(alpha = 0.42f)
    val specularAlpha = 0.35f          // top-left highlight
    val innerStrokeAlpha = 0.18f       // catches the light on the rim
    val shadowElevation = 18.dp

    // type scale (Inter / Manrope, tabular numbers on)
    val typeHero   = 28.sp / 34.sp  // home greeting
    val typeTitle  = 20.sp / 26.sp  // result title
    val typeBody   = 15.sp / 22.sp  // snippet
    val typeLabel  = 12.sp / 16.sp  // url chip, counters

    // colour (dual-tone, never pure black/white behind glass)
    val bgGradient = listOf(Color(0xFF101A2B), Color(0xFF1B2A44), Color(0xFF2A1B36))
    val accent     = Color(0xFF6EA8FF)
    val accentAI   = Color(0xFFB48CFF)
    val danger     = Color(0xFFFF6B6B)
    val shieldOk   = Color(0xFF58D6A8)

    // spacing (4 pt grid)
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp; val s5 = 24.dp; val s6 = 32.dp
}
```

**Rules that keep glass from looking like 2012 skeuomorphism:**
1. Glass is only for **chrome that floats over content** — never for list rows, never for text blocks.
2. Text on glass: contrast ≥ 4.5:1 measured against the **lightest** possible backdrop. Use a
   12–18% scrim under text if unsure.
3. No bevels, no drop-shadow on both sides, no "plastic" gradient. One specular highlight, one rim.
4. Dark mode glass is **not** black — it's `#0B0F1A` at 42% with vibrancy.

---

## 3. API-level fallback matrix (must be coded, not hoped for)

`backdrop` exposes `isRenderEffectSupported()` (API 31+) and `isRuntimeShaderSupported()` (API 33+).
Lumina targets **minSdk 28**, so:

| API | Material | Implementation |
|---|---|---|
| 33+ | **True liquid glass** | `lens()` (AGSL displacement) + `blur` + `vibrancy` + `DefaultHighlight` |
| 31–32 | **Frosted glass** | RenderEffect blur + vibrancy + highlight (no displacement) |
| 28–30 | **Tinted glass** | static 2-stop gradient + 12% tint + hairline stroke (no blur) |
| 26–27 | — | not supported (minSdk 28) |

Put the decision in one place and let components ask:

```kotlin
enum class GlassTier { Refraction, Blur, Tint }

@Composable
fun rememberGlassTier(): GlassTier = remember {
    when {
        Build.VERSION.SDK_INT >= 33 -> GlassTier.Refraction
        Build.VERSION.SDK_INT >= 31 -> GlassTier.Blur
        else -> GlassTier.Tint
    }
}
```

Nothing else in the codebase should branch on `Build.VERSION` for glass.

---

## 4. Component inventory

| Component | Spec |
|---|---|
| `GlassSurface` | base: `drawBackdrop` + shape + tier-aware effects; everything else builds on it |
| `GlassOmnibar` | 48 dp pill, collapsed ↔ 640 dp expanded panel; shield chip left, AI toggle right, close morphs to X |
| `GlassBottomBar` | 64 dp, 4 items, highlight pill slides between items (spring 380/0.78), auto-hides on scroll |
| `GlassSheet` | 32 dp top radius, drag-to-dismiss with velocity, scrim 32%, content never blurs twice |
| `GlassChip` | 32 dp, 2 dp blur only (cheap), used for related queries / filters |
| `GlassButton` | capsule, interactive highlight follows the touch point (`InteractiveHighlight` pattern from Kyant0's `LiquidButton`) |
| `GlassResultCard` | SERP list item: favicon 20 dp, title 2 lines, url chip, snippet 2 lines, per-result tracker badge |
| `GlassAnswerCard` | AI summary: streaming text, 3 source chips, "on-device" badge, stop/regenerate |
| `GlassCounter` | tracker-blocked count, animated number, shield icon that morphs on change |

---

## 5. Motion system (this is what makes it feel expensive)

**Springs, not durations.** One global spring, three variants:

```kotlin
object LuminaSpring {
    val spatial  = spring<Dp>(dampingRatio = 0.78f, stiffness = 380f)   // size/position
    val scale    = spring<Float>(dampingRatio = 0.75f, stiffness = 900f) // press feedback
    val soft     = spring<Float>(dampingRatio = 0.90f, stiffness = 180f) // fades, colours
    const val entranceStagger = 30   // ms per item
}
```

| Interaction | Motion |
|---|---|
| Press | scale → 0.92 (`scale` spring), tint +8%, light haptic tick |
| Release | scale → 1.0 overshoot ≤ 2%, no bounce beyond |
| Omnibar expand | height + radius animate on `spatial`; suggestions fade+slide 8 dp on `soft` |
| Tab card → page | shared-element expand 420 ms, corner radius 24 → 0 as it fills |
| Sheet up | translateY + scrim alpha on `spatial`; content 40 ms delayed |
| Scroll hide bar | translateY, `soft`, driven by scroll delta not fling velocity |
| Success (tracker blocked) | shield pops scale 1 → 1.12 → 1, colour washes to `shieldOk` once |
| Error / no results | 1 shake, 60 dp/s, 220 ms total — **never** loop |

**Reduced motion:** honour `Settings.Global.ANIMATOR_DURATION_SCALE == 0` → all springs become
fades of 120 ms, no scale, no stagger. Accessibility, and it saves battery.

---

## 6. Icon motion

Icons are where an app "feels" alive or dead. Four techniques, all cheap:

1. **Press physics** — every icon goes through `pressScale()`; icon and its glass background scale
   together (never the icon alone).
2. **Morph, don't swap** — refresh ⇄ stop, shield ⇄ shield-check, play ⇄ pause, menu ⇄ close,
   mic ⇄ waveform. Use `androidx.graphics:graphics-shapes` (`materialShape`/`Morph`) on
   `AnimatedContent`-free single `Canvas`.
3. **Micro-bounce on state change** — tab switches, favourite added, tracker counter increment:
   scale 1 → 1.15 → 1 on `scale` spring, 140 ms perceived.
4. **Progress as motion** — page load = the omnibar's hairline stroke draws itself (no spinner);
   download = icon fill level; AI streaming = 3 dots on `soft` spring loop (only while streaming).

**Don't:** animate more than 2 properties per icon, don't loop animation in the tab bar, don't
animate anything that isn't the direct result of a user action (except page-load progress).

Icon source rule: 24 dp grid, 2 dp stroke, rounded caps, single path where possible (morph needs
matching node counts). Keep an `ic_lumina_*.xml` set in `lumina-ui/src/main/res/drawable`.

---

## 7. App icon (Lumina)

- Adaptive icon: `108 dp` canvas, safe zone 66 dp.
- Background: `#0B0F1A` → `#16233A` radial.
- Foreground: a **glass droplet / prism** — big radius, 1.5 dp inner specular arc top-left,
  20% white rim, soft lavender refraction at the base.
- Monochrome (Android 13 themed icons): same droplet silhouette, single colour.
- Splash: icon scales 0.9 → 1.0 on `spatial` while the WebView warms up (see P6) — the splash is
  free performance, not decoration.

---

## 8. Frame budget (why glass can be slow, and the rules that keep it fast)

At 120 Hz you have **8.3 ms/frame**. A full-screen 10 dp blur at 1080×2400 costs ~4–6 ms on a
mid-range GPU. So:

| Rule | Why |
|---|---|
| Blur/refract at ≤ 0.5× downscale, upscale for display | blur cost scales with pixels, not with visual size |
| Only blur **what's inside the glass shape**, never the screen | a 48 dp pill blurring a 600×2400 bitmap wastes 95% of the work |
| Max sigma 10 dp for bars, 20 dp for sheets | beyond that it's just expensive milk |
| Skip effects while `isScrolling` | the eye can't see refraction during a fling |
| Use `drawWithCache` and reuse `Paint`/`RenderEffect` | allocating a RenderEffect per frame = jank |
| 2 backdrop layers max | each layer = one extra full pass |
| Prefer `Capsule`/`RoundedCorner` shapes (analytic) over path-based | path tessellation per frame is real cost |
| Profile on a **real mid-range phone**, not an emulator | emulators lie about GPU cost by 3–10× |

Lint rule to add in P2: `@GlassLayerLimit` — a custom lint check that fails the build if a screen
declares more than 2 `drawBackdrop` calls in one composition tree.

---

## 9. Definition of done for `lumina-ui`

- [ ] Gallery screen with every component, on API 28 / 31 / 33 device profiles
- [ ] `GlassTier` fallbacks verified visually on all three
- [ ] Macrobenchmark: p95 frame ≤ 16.6 ms with omnibar + bottom bar + one sheet visible
- [ ] `HybridBackdrop` capture ≤ 4 ms and never during fling (trace proves it)
- [ ] Reduced-motion mode passes
- [ ] Every icon morphs (no swap-in-place), every press has haptic + scale
