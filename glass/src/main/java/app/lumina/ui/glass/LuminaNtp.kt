package app.lumina.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay

/**
 * The Lumina home page. 100% native, 100% Compose, zero network requests.
 *
 * Host it in the forked app by dropping a ComposeView into
 * `app/src/main/res/layout/include_new_browser_tab.xml` (inside `@+id/newTabContainerLayout`)
 * and calling this from `BrowserTabFragment`. See docs/01-TWO-HOUR-SPRINT.md §0:45.
 */
@Composable
fun LuminaNtp(
    modifier: Modifier = Modifier,
    greeting: String = "Good evening",
    blockedToday: Int = 0,
    favourites: List<Favourite> = emptyList(),
    recent: List<RecentPage> = emptyList(),
    onSearchFocus: () -> Unit = {},
    onAi: () -> Unit = {},
    onShields: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onOpenFavourite: (Favourite) -> Unit = {},
) {
    // one captured layer = the wallpaper. Glass above it refracts for real.
    val backdrop = rememberComposeBackdrop()

    Box(modifier.fillMaxSize()) {
        // ---- wallpaper (the thing the glass refracts) ------------------------------
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.radialGradient(
                        colors = LuminaTokens.bgGradient,
                        radius = 1600f,
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = LuminaTokens.s5, vertical = LuminaTokens.s6),
            verticalArrangement = Arrangement.spacedBy(LuminaTokens.s4),
        ) {
            // ---- header: greeting + trackers blocked today -------------------------
            StaggeredEntrance(index = 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = greeting,
                        color = LuminaTokens.textPrimary,
                        fontSize = LuminaTokens.hero.first,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    GlassPill(
                        text = "⛨ $blockedToday",
                        backdrop = backdrop,
                        onClick = onShields,
                    )
                }
            }

            Spacer(Modifier.height(LuminaTokens.s4))

            // ---- the search field: tap expands the real omnibar above -------------
            StaggeredEntrance(index = 1) {
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSearchFocus() },
                    shape = RoundedCornerShape(LuminaTokens.radiusPill),
                    tint = LuminaTokens.tintDark.copy(alpha = 0.55f),
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = LuminaTokens.s5),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
                    ) {
                        Text("⌕", color = LuminaTokens.textSecondary, fontSize = 20.sp)
                        Text(
                            text = "Search or type a URL",
                            color = LuminaTokens.textSecondary,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        GlassPill(text = "✨ AI", backdrop = backdrop, accent = true, onClick = onAi)
                    }
                }
            }

            // ---- quick cards -------------------------------------------------------
            StaggeredEntrance(index = 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3)) {
                    QuickCard(
                        backdrop = backdrop,
                        title = "Ask Lumina",
                        subtitle = "summarise · explain · compare",
                        tint = LuminaTokens.accentAi.copy(alpha = 0.20f),
                        modifier = Modifier.weight(1f),
                        onClick = onAi,
                    )
                    QuickCard(
                        backdrop = backdrop,
                        title = "Shields",
                        subtitle = "$blockedToday blocked today",
                        tint = LuminaTokens.shieldOk.copy(alpha = 0.16f),
                        modifier = Modifier.weight(1f),
                        onClick = onShields,
                    )
                }
            }

            // ---- favourites rail ---------------------------------------------------
            if (favourites.isNotEmpty()) {
                StaggeredEntrance(index = 3) {
                    Column(verticalArrangement = Arrangement.spacedBy(LuminaTokens.s2)) {
                        Text("Favourites", color = LuminaTokens.textSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3)) {
                            favourites.take(5).forEach { favourite ->
                                GlassSurface(
                                    backdrop = backdrop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clickable { onOpenFavourite(favourite) },
                                    shape = RoundedCornerShape(LuminaTokens.radiusChip),
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = favourite.title.take(1).uppercase(),
                                            color = LuminaTokens.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- recent (local only, never uploaded) ------------------------------
            if (recent.isNotEmpty()) {
                StaggeredEntrance(index = 4) {
                    Column(verticalArrangement = Arrangement.spacedBy(LuminaTokens.s2)) {
                        Text("Recent", color = LuminaTokens.textSecondary, fontSize = 12.sp)
                        recent.take(3).forEach { page ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clickable { onOpenUrl(page.url) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
                            ) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .background(
                                            LuminaTokens.textSecondary.copy(alpha = 0.15f),
                                            CircleShape,
                                        ),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(page.title, color = LuminaTokens.textPrimary, fontSize = 14.sp, maxLines = 1)
                                    Text(page.domain, color = LuminaTokens.textSecondary, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class Favourite(val title: String, val url: String)
data class RecentPage(val title: String, val url: String, val domain: String)

@Composable
private fun QuickCard(
    backdrop: com.kyant.backdrop.Backdrop,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .height(96.dp)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        shape = RoundedCornerShape(LuminaTokens.radiusCard),
        tint = tint,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(LuminaTokens.s4),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, color = LuminaTokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = LuminaTokens.textSecondary, fontSize = 12.sp, maxLines = 2)
        }
    }
}

/**
 * Fade + 24 dp slide, 30 ms apart. Cheap, and it's what makes the page feel built rather than loaded.
 * Turns into a plain fade under reduced motion.
 */
@Composable
private fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    val reduced = isReducedMotion()
    val progress = remember { Animatable(0f) }

    LaunchedEffect(index, reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            delay(index * LuminaSpring.entranceStagger.toLong())
            progress.animateTo(1f, LuminaSpring.spatial())
        }
    }

    Box(
        Modifier
            .alpha(progress.value)
            .graphicsLayer { translationY = (1f - progress.value) * 24.dp.toPx() },
    ) {
        content()
    }
}
