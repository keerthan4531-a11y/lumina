package app.lumina.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Where a real refraction engine (the vendored `backdrop` library, AGSL lens + vibrancy) plugs in.
 * Until then [rememberGlassMaterial] returns Tint and everything renders tint + rim + specular.
 */
interface LuminaGlassEngine {
    val tier: GlassTier
}

/**
 * The one glass surface in Lumina. Every component is built from this, so the material,
 * the rim, the specular and the elevation can never drift per-screen.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LuminaTokens.radiusCard),
    tint: Color = LuminaTokens.tintDark,
    elevation: Dp = 18.dp,
    interactive: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    var pointer by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(tint, tint.copy(alpha = tint.alpha * 0.55f)),
                ),
            )
            .then(
                if (interactive) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                pointer = event.changes.firstOrNull()?.position
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // specular highlight, follows the pointer (Kyant InteractiveHighlight, simplified)
        val center = pointer ?: Offset(46f, -24f)
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x59FFFFFF), Color.Transparent),
                        center = center,
                        radius = 260f,
                    ),
                ),
        )
        // refraction rim: 1dp gradient border, brighter top-left
        Box(
            Modifier
                .matchParentSize()
                .border(
                    LuminaTokens.hairline,
                    Brush.linearGradient(
                        listOf(
                            LuminaTokens.rimTop,
                            Color(0x0DFFFFFF),
                            Color(0x0DFFFFFF),
                            LuminaTokens.rimBottom,
                        ),
                    ),
                    shape,
                ),
        )
        content()
    }
}

/** Small glass chip: related queries, filters, citations. */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberHapticTick()
    GlassSurface(
        modifier = modifier
            .height(32.dp)
            .pressScale(interaction)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) {
                        tick()
                        onClick()
                    }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(LuminaTokens.radiusPill),
        tint = if (accent) LuminaTokens.aiSoft else LuminaTokens.tintDarkSoft,
        elevation = 0.dp,
    ) {
        Box(Modifier.padding(horizontal = LuminaTokens.s3), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (accent) LuminaTokens.ai else LuminaTokens.text2,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * Bottom sheet. Slides on the spatial spring, scrim fades on soft.
 * Content is inside the same glass, so there are never two backdrops stacked.
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (visible) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x7302050C))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onDismiss()
                },
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = LuminaSpring.spatial()) { it },
        exit = slideOutVertically(animationSpec = LuminaSpring.soft()) { it },
        modifier = modifier,
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp),
            shape = RoundedCornerShape(
                topStart = LuminaTokens.radiusSheet,
                topEnd = LuminaTokens.radiusSheet,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            ),
            tint = LuminaTokens.tintDark,
            elevation = 32.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(LuminaTokens.s5)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color(0x4DFFFFFF), RoundedCornerShape(99.dp)),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(LuminaTokens.s4))
                content()
            }
        }
    }
}

data class LuminaNavItem(
    val icon: ImageVector,
    val label: String,
    val badge: String? = null,
)

/** Glass bottom bar with a highlight pill that slides between items. */
@Composable
fun GlassBottomBar(
    items: List<LuminaNavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val tick = rememberHapticTick()
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(LuminaTokens.radiusPill),
        tint = LuminaTokens.tintDark,
        elevation = 24.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / items.size
            val offset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = LuminaSpring.spatial(),
                label = "navHighlight",
            )
            Box(
                Modifier
                    .absoluteOffset(x = offset + 6.dp, y = 9.dp)
                    .width(itemWidth - 12.dp)
                    .height(46.dp)
                    .background(Color(0x1FFFFFFF), RoundedCornerShape(LuminaTokens.radiusPill)),
            )
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    val active = index == selectedIndex
                    val bounce by animateFloatAsState(
                        targetValue = if (active) 1f else 0.94f,
                        animationSpec = LuminaSpring.scale(),
                        label = "navScale",
                    )
                    Box(
                        Modifier
                            .width(itemWidth)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                tick()
                                onSelect(index)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (active) LuminaTokens.text1 else LuminaTokens.text2,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = bounce
                                        scaleY = bounce
                                    },
                            )
                            if (item.badge != null) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .background(LuminaTokens.accent, RoundedCornerShape(99.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = item.badge,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
