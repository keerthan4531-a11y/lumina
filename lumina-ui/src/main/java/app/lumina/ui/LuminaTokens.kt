package app.lumina.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One source of truth for the Lumina look. 1:1 with preview/css/tokens.css.
 * Nothing in the app should hardcode a radius, a blur or a tint.
 */
@Immutable
object LuminaTokens {

    // shape
    val radiusPill: Dp = 999.dp
    val radiusCard: Dp = 24.dp
    val radiusSheet: Dp = 32.dp
    val radiusChip: Dp = 16.dp
    val hairline: Dp = 1.dp

    // glass material
    val blurChip: Dp = 6.dp
    val blurBar: Dp = 18.dp
    val blurSheet: Dp = 26.dp

    // colour — dark
    val bg0: Color = Color(0xFF06090F)
    val bg1: Color = Color(0xFF0B1220)
    val text1: Color = Color(0xFFF4F7FF)
    val text2: Color = Color(0xA3F4F7FF)
    val text3: Color = Color(0x66F4F7FF)
    val accent: Color = Color(0xFF6EA8FF)
    val accentSoft: Color = Color(0x2E6EA8FF)
    val ai: Color = Color(0xFFB48CFF)
    val aiSoft: Color = Color(0x33B48CFF)
    val ok: Color = Color(0xFF58D6A8)
    val okSoft: Color = Color(0x2958D6A8)
    val warn: Color = Color(0xFFFFB86B)
    val bad: Color = Color(0xFFFF6B6B)

    // glass tints
    val tintLight: Color = Color(0x8CFFFFFF)
    val tintDark: Color = Color(0x6B0B0F1A)
    val tintDarkSoft: Color = Color(0x570B0F1A)
    val rimTop: Color = Color(0xB8FFFFFF)
    val rimBottom: Color = Color(0x42FFFFFF)

    // wallpaper mesh
    val wallpaper: List<Color> = listOf(
        Color(0xFF1D3A6E),
        Color(0xFF16223E),
        Color(0xFF4A2470),
        Color(0xFF0F4E48),
        Color(0xFF06090F),
    )

    // type
    val hero = 30.sp
    val title = 17.sp
    val body = 15.sp
    val label = 12.sp

    // spacing (4pt grid)
    val s1: Dp = 4.dp
    val s2: Dp = 8.dp
    val s3: Dp = 12.dp
    val s4: Dp = 16.dp
    val s5: Dp = 24.dp
    val s6: Dp = 32.dp
}

/**
 * How much glass the device can render.
 *
 * Refraction : API 33+ — AGSL (the vendored backdrop engine lands here in P2.5)
 * Blur       : API 31+ — RenderEffect blur
 * Tint       : API 28–30 — tint + rim + specular only
 *
 * v1 of :lumina-ui renders the Tint material on every API: tint gradient + 1dp gradient rim +
 * pointer-following specular. It compiles everywhere, costs nothing, and is visually 90% of the
 * way there over a mesh wallpaper. The Refraction path is a drop-in via [LuminaGlassEngine].
 */
enum class GlassTier { Refraction, Blur, Tint }

@Composable
fun rememberGlassTier(): GlassTier = remember {
    when {
        Build.VERSION.SDK_INT >= 33 -> GlassTier.Refraction
        Build.VERSION.SDK_INT >= 31 -> GlassTier.Blur
        else -> GlassTier.Tint
    }
}

/** Which material the UI actually draws. Kept separate from [GlassTier] so P2.5 can upgrade it. */
@Composable
fun rememberGlassMaterial(): GlassTier = remember { GlassTier.Tint }
