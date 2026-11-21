package app.lumina.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

// ---------------------------------------------------------------------------------------------
// Base surface
// ---------------------------------------------------------------------------------------------

/**
 * The only place in Lumina that calls `drawBackdrop`. Everything glass goes through here, so the
 * tier fallbacks, the tint, the rim stroke and the blur budget can never drift per-screen.
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LuminaTokens.radiusCard),
    tier: GlassTier = rememberGlassTier(),
    tint: Color = LuminaTokens.tintDark,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val blurPx = with(density) { LuminaTokens.blurBar.toPx() }
    val lensHeightPx = with(density) { LuminaTokens.refractionHeight.toPx() }
    val lensAmountPx = with(density) { LuminaTokens.refractionAmount.toPx() }

    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    // vibrancy makes colours punchier through the glass; cheap, always on
                    vibrancy()
                    when (tier) {
                        GlassTier.Refraction -> {
                            blur(blurPx)
                            lens(lensHeightPx, lensAmountPx, chromaticAberration = true)
                        }
                        GlassTier.Blur -> blur(blurPx)
                        GlassTier.Tint -> Unit // gradient + tint only (API 28-30)
                    }
                },
            )
            .background(tint, shape)
            .border(
                LuminaTokens.strokeHairline,
                Color.White.copy(alpha = LuminaTokens.innerStrokeAlpha),
                shape,
            ),
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Backdrops
// ---------------------------------------------------------------------------------------------

/**
 * For glass over Compose content (home page, sheets, tab grid): a true captured layer,
 * i.e. real refraction of what is actually behind the glass.
 *
 *     val backdrop = rememberComposeBackdrop()
 *     Box(Modifier.layerBackdrop(backdrop)) { Wallpaper() }
 *     GlassSurface(backdrop) { ... }
 */
@Composable
fun rememberComposeBackdrop() = rememberLayerBackdrop()

/**
 * For glass over a **WebView**: the page is not in a Compose GraphicsLayer, so a LayerBackdrop
 * would refract the wrong thing. We feed a downscaled bitmap snapshot instead.
 *
 * Rules (docs/02-LIQUID-GLASS-SPEC.md §1):
 *  - capture at <= 40% scale, only on scroll-idle / page-finished / theme change
 *  - never capture during a fling
 *  - if the snapshot is null, fall back to a tinted colour (still looks fine)
 */
@Composable
fun rememberWebBackdrop(
    snapshot: ImageBitmap?,
    fallback: Color = LuminaTokens.bgGradient.first(),
): Backdrop = rememberCanvasBackdrop {
    val image = snapshot
    if (image != null) {
        drawImage(
            image = image,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    } else {
        drawRect(fallback)
    }
}

/** Offscreen page capture. Verify on a real device: some OEM WebViews return blank. */
object WebSnapshot {
    fun capture(view: WebView, scale: Float = 0.4f): ImageBitmap? {
        val w = (view.width * scale).toInt()
        val h = (view.height * scale).toInt()
        if (w <= 0 || h <= 0) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            view.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull() // fail open: glass falls back to tint
    }
}

// ---------------------------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------------------------

/** Small glass chip: related queries, filters, source citations. Cheap (2 dp blur territory). */
@Composable
fun GlassPill(
    text: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .height(32.dp)
            .pressScale(interaction)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier),
        shape = RoundedCornerShape(LuminaTokens.radiusPill),
        tint = if (accent) LuminaTokens.accentAi.copy(alpha = 0.22f) else LuminaTokens.tintDark,
    ) {
        Box(Modifier.padding(horizontal = LuminaTokens.s3), contentAlignment = Alignment.Center) {
            Text(text = text, color = LuminaTokens.textPrimary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/**
 * The omnibar. Collapsed = 48 dp pill, focused = expanded panel with suggestions.
 * Height/radius animate on the `spatial` spring; suggestions fade in on `soft`.
 */
@Composable
fun GlassOmnibar(
    backdrop: Backdrop,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    forcedOpen: Boolean = false,
    shieldCount: Int = 0,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    onShieldClick: () -> Unit = {},
    onAiClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val open = forcedOpen || focused

    val height by animateDpAsState(
        targetValue = if (open) 320.dp else 48.dp,
        animationSpec = LuminaSpring.spatial(),
        label = "omnibarHeight",
    )
    val radius by animateDpAsState(
        targetValue = if (open) LuminaTokens.radiusCard else LuminaTokens.radiusPill,
        animationSpec = LuminaSpring.spatial(),
        label = "omnibarRadius",
    )

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        tint = if (open) LuminaTokens.tintDark else LuminaTokens.tintDark.copy(alpha = 0.34f),
    ) {
        Column(Modifier.height(height)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = LuminaTokens.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
            ) {
                // shield chip -> shield sheet in one tap
                Box(
                    Modifier
                        .height(28.dp)
                        .background(
                            LuminaTokens.shieldOk.copy(alpha = 0.16f),
                            RoundedCornerShape(8.dp),
                        )
                        .clickable(interactionSource = interaction, indication = null) { onShieldClick() }
                        .padding(horizontal = LuminaTokens.s2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⛨ $shieldCount", color = LuminaTokens.shieldOk, fontSize = 12.sp)
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    interactionSource = interaction,
                    textStyle = TextStyle(
                        color = LuminaTokens.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f),
                )

                GlassPill(text = "✨", backdrop = backdrop, accent = true, onClick = onAiClick)
            }

            // suggestions: only composed while open, so the collapsed bar costs nothing
            if (open) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(suggestions) { suggestion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { onSuggestion(suggestion) }
                                .padding(horizontal = LuminaTokens.s4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = suggestion,
                                color = LuminaTokens.textSecondary,
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
