package app.lumina.ui.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One source of truth for the glass look. Nothing else in the app should hardcode a radius,
 * a blur sigma or a tint — that is how design systems rot.
 */
@Immutable
object LuminaTokens {

    // ---- shape ----------------------------------------------------------------
    val radiusPill: Dp = 999.dp
    val radiusCard: Dp = 24.dp
    val radiusSheet: Dp = 32.dp
    val radiusChip: Dp = 16.dp
    val strokeHairline: Dp = 1.dp

    // ---- glass material -------------------------------------------------------
    val blurChip: Dp = 2.dp
    val blurBar: Dp = 10.dp
    val blurSheet: Dp = 18.dp

    val refractionHeight: Dp = 24.dp
    val refractionAmount: Dp = 48.dp

    val tintLight: Color = Color.White.copy(alpha = 0.55f)
    val tintDark: Color = Color(0xFF0B0F1A).copy(alpha = 0.42f)
    val specularAlpha: Float = 0.35f
    val innerStrokeAlpha: Float = 0.18f
    val shadowElevation: Dp = 18.dp

    // ---- type scale (Inter / Manrope; tabular numbers on) --------------------
    val hero = 28.sp to 34.sp
    val title = 20.sp to 26.sp
    val body = 15.sp to 22.sp
    val label = 12.sp to 16.sp

    // ---- colour (never pure black or pure white behind glass) ----------------
    val bgGradient: List<Color> = listOf(
        Color(0xFF101A2B),
        Color(0xFF1B2A44),
        Color(0xFF2A1B36),
    )
    val accent: Color = Color(0xFF6EA8FF)
    val accentAi: Color = Color(0xFFB48CFF)
    val danger: Color = Color(0xFFFF6B6B)
    val shieldOk: Color = Color(0xFF58D6A8)
    val textPrimary: Color = Color(0xFFF2F5FA)
    val textSecondary: Color = Color(0xB3F2F5FA)

    // ---- spacing (4 pt grid) -------------------------------------------------
    val s1: Dp = 4.dp
    val s2: Dp = 8.dp
    val s3: Dp = 12.dp
    val s4: Dp = 16.dp
    val s5: Dp = 24.dp
    val s6: Dp = 32.dp
}

/**
 * How much glass the OS can actually render.
 *
 *  Refraction : API 33+ — AGSL lens displacement (real liquid glass)
 *  Blur       : API 31-32 — RenderEffect blur, no displacement
 *  Tint       : API 28-30 — static gradient + hairline stroke, no blur
 *
 * Branch on THIS, never on Build.VERSION directly, or the fallbacks will drift.
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
