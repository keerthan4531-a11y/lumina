# `glass/` — reference implementation (read me first)

This is **reference code for the `lumina-ui` module**, written during planning (P0/P2 of
[`../docs/00-MASTER-PLAN.md`](../docs/00-MASTER-PLAN.md)). It is **not compiled** in this repo —
there is no Android SDK / Gradle build here. Treat it as a high-quality starting point, not as a
dependency.

## What's inside

| File | Purpose |
|---|---|
| `build.gradle.kts` | module skeleton — rename the folder to `lumina-ui`, it auto-registers via `settings.gradle` |
| `LuminaTokens.kt` | colours, radii, typography scale, `GlassTier` (API 33 / 31 / 28 fallback matrix) |
| `Motion.kt` | `LuminaSpring`, `pressScale()`, `iconBounce()`, reduced-motion support |
| `LuminaGlass.kt` | `GlassSurface`, `GlassOmnibar`, `GlassPill`, `rememberWebBackdrop()` + `WebSnapshot` |
| `LuminaNtp.kt` | the glass home page (NTP) composition |

## How to use it in the forked app

```bash
cp -r glass/lumina-ui   your-fork/lumina-ui          # 1. module in
# 2. glass engine (either):
#    a) implementation("io.github.kyant0:backdrop:<latest>")   — Maven Central
#    b) cp -r /tmp/glass/backdrop your-fork/backdrop           — vendored Apache-2.0
# 3. wire the home page: see docs/01-TWO-HOUR-SPRINT.md §0:45
./gradlew :app:installInternalDebug
```

## Verified against

`backdrop` API as read from `Kyant0/AndroidLiquidGlass` @ 2026-11:

```kotlin
Modifier.drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedCornerShape(24.dp) },
    effects = { vibrancy(); blur(10.dp.toPx()); lens(24.dp.toPx(), 48.dp.toPx()) },
)
// backdrops: rememberLayerBackdrop() / rememberCanvasBackdrop { ... }
// effects:   blur(radius, tileMode) · lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)
//            vibrancy() · colorFilter() · opacity() · colorControls() · effect() · runtimeShaderEffect()
```

`lens()` needs API 33+ (AGSL) and `blur()` needs API 31+ (RenderEffect); both are **no-ops** on older
devices, which is exactly why `LuminaTokens.kt` exposes `GlassTier` and every component branches on
*that*, never on `Build.VERSION` directly.
