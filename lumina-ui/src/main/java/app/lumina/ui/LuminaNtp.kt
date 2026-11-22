package app.lumina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.Sky
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky

/**
 * A shortcut tile on the start page. [title] is what the user sees (one letter is enough for the
 * icon), [url] is what gets loaded when it is tapped.
 */
data class LuminaFavourite(val title: String, val url: String)

data class LuminaRecentPage(val title: String, val url: String, val domain: String)

/**
 * What Chrome/Safari show on an empty start page: a few defaults, then whatever the user pins.
 */
object LuminaShortcuts {
    val default: List<LuminaFavourite> = listOf(
        LuminaFavourite("Google", "https://www.google.com"),
        LuminaFavourite("YouTube", "https://m.youtube.com"),
        LuminaFavourite("Wikipedia", "https://www.wikipedia.org"),
        LuminaFavourite("GitHub", "https://github.com"),
        LuminaFavourite("Reddit", "https://www.reddit.com"),
        LuminaFavourite("Amazon", "https://www.amazon.in"),
        LuminaFavourite("News", "https://news.google.com"),
        LuminaFavourite("Maps", "https://maps.google.com"),
    )
}

/**
 * Lumina's start page.
 *
 * Deliberately boring in the way a good browser start page is boring: a wordmark, one search
 * field, a grid of shortcuts and one privacy line. No dashboard, no greeting, no invented
 * "cards" — nothing that is not a place to go.
 *
 * The glass is not painted by hand: the background mesh is recorded with Cloudy's
 * [Modifier.sky] and every surface samples it through [Modifier.cloudy], so the blur is a real
 * backdrop blur of what is actually underneath (skydoves/Cloudy). On API < 31 Cloudy falls back
 * to a scrim on its own.
 */
@Composable
fun LuminaNtp(
    modifier: Modifier = Modifier,
    shortcuts: List<LuminaFavourite> = LuminaShortcuts.default,
    recent: List<LuminaRecentPage> = emptyList(),
    blockedToday: Int = 0,
    topInset: Dp = 0.dp,
    dark: Boolean = isSystemInDarkTheme(),
    onSearchTap: () -> Unit = {},
    onAi: () -> Unit = {},
    onShields: () -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    val sky = rememberSky()
    val palette = remember(dark) { NtpPalette(dark) }
    val tick = rememberHapticTick()

    Box(modifier.fillMaxSize()) {
        // 1. Everything the glass will blur. Recorded once into the sky layer.
        Box(Modifier.fillMaxSize().sky(sky)) {
            AmbientBackdrop(palette)
        }

        // 2. The page itself.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(30.dp))

            StaggeredEntrance(0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Lumina",
                        color = palette.text1,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.6).sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "search without being watched",
                        color = palette.text3,
                        fontSize = 12.sp,
                        letterSpacing = 0.2.sp,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            StaggeredEntrance(1) { SearchPill(sky, palette, onSearchTap, onAi) }

            Spacer(Modifier.height(28.dp))

            if (shortcuts.isNotEmpty()) {
                StaggeredEntrance(2) {
                    ShortcutGrid(shortcuts.take(8), sky, palette, tick, onOpen)
                }
            }

            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                StaggeredEntrance(3) { RecentList(recent.take(3), sky, palette, onOpen) }
            }

            Spacer(Modifier.weight(1f))

            StaggeredEntrance(4) {
                PrivacyLine(sky, palette, blockedToday, onShields)
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Background                                                                                     */
/* --------------------------------------------------------------------------------------------- */

/**
 * A soft four-blob mesh. It is not decoration for its own sake: glass needs something with
 * contrast behind it, and a flat colour makes any glass look like a grey rectangle.
 */
@Composable
private fun AmbientBackdrop(p: NtpPalette) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(p.base)
        val min = size.minDimension
        fun blob(color: Color, x: Float, y: Float, r: Float, alpha: Float) {
            val center = Offset(size.width * x, size.height * y)
            val radius = min * r
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        blob(p.blob1, 0.10f, 0.10f, 0.85f, p.blobAlpha)
        blob(p.blob2, 0.95f, 0.22f, 0.75f, p.blobAlpha * 0.85f)
        blob(p.blob3, 0.25f, 0.90f, 0.90f, p.blobAlpha * 0.7f)
        blob(p.blob4, 1.00f, 0.85f, 0.70f, p.blobAlpha * 0.6f)
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Pieces                                                                                         */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun SearchPill(
    sky: Sky,
    p: NtpPalette,
    onSearchTap: () -> Unit,
    onAi: () -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(12.dp, shape, clip = false, ambientColor = p.shadow, spotColor = p.shadow)
            .glass(sky, shape, p.glassTint, p.rim, 30)
            .clickable(interactionSource = interaction, indication = null) {
                onSearchTap()
            }
            .padding(start = 18.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(
            imageVector = LuminaIcons.search,
            contentDescription = null,
            tint = p.text2,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = "Search or type a URL",
            color = p.text2,
            fontSize = 15.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val aiInteraction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .height(42.dp)
                .glass(sky, RoundedCornerShape(21.dp), p.aiTint, p.rim, 22)
                .clickable(interactionSource = aiInteraction, indication = null) { onAi() }
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = LuminaIcons.ai,
                contentDescription = null,
                tint = p.ai,
                modifier = Modifier.size(14.dp),
            )
            Text("AI", color = p.ai, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ShortcutGrid(
    shortcuts: List<LuminaFavourite>,
    sky: Sky,
    p: NtpPalette,
    tick: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        shortcuts.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                rowItems.forEach { shortcut ->
                    ShortcutTile(shortcut, sky, p) {
                        tick()
                        onOpen(shortcut.url)
                    }
                }
                // keep the last row left-aligned instead of stretched
                repeat(4 - rowItems.size) { Spacer(Modifier.width(74.dp)) }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    shortcut: LuminaFavourite,
    sky: Sky,
    p: NtpPalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(17.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.width(74.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .pressScale(interaction)
                .glass(sky, shape, p.tileTint, p.rim, 20)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = shortcut.title.take(1).uppercase(),
                color = p.tileInk(shortcut.url),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = shortcut.title,
            color = p.text3,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentList(
    recent: List<LuminaRecentPage>,
    sky: Sky,
    p: NtpPalette,
    onOpen: (String) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(sky, shape, p.glassTint, p.rim, 24)
            .padding(vertical = 4.dp),
    ) {
        recent.forEach { page ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(page.url) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(p.chipFill, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LuminaIcons.globe,
                        contentDescription = null,
                        tint = p.text2,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = page.title,
                        color = p.text1,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = page.domain,
                        color = p.text3,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyLine(
    sky: Sky,
    p: NtpPalette,
    blockedToday: Int,
    onShields: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .height(38.dp)
            .glass(sky, shape, p.tileTint, p.rim, 18)
            .clip(shape)
            .clickable { onShields() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).background(p.ok, CircleShape))
        Text(
            text = if (blockedToday > 0) "$blockedToday trackers blocked today" else "Protected · no trackers found",
            color = p.text3,
            fontSize = 11.5.sp,
        )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Glass + palette                                                                                */
/* --------------------------------------------------------------------------------------------- */

/**
 * One glass surface: Cloudy's backdrop blur, clipped to [shape], plus a hairline rim so the edge
 * reads on a light background. Nothing here paints a fake highlight over the content.
 */
@Composable
private fun Modifier.glass(
    sky: Sky,
    shape: Shape,
    tint: Color,
    rim: Color,
    radius: Int,
): Modifier = this
    .cloudy(sky = sky, radius = radius, tint = tint, shape = shape)
    .clip(shape)
    .border(1.dp, rim, shape)

/** Colours for both schemes. Two objects, resolved once, no theme plumbing. */
private class NtpPalette(dark: Boolean) {
    val base: Color = if (dark) Color(0xFF0A0C12) else Color(0xFFF4F5FA)
    val blob1: Color = if (dark) Color(0xFF3F51B5) else Color(0xFFB6C7FF)
    val blob2: Color = if (dark) Color(0xFF1D6E62) else Color(0xFFA8EBD8)
    val blob3: Color = if (dark) Color(0xFF5B2C74) else Color(0xFFFFC9DE)
    val blob4: Color = if (dark) Color(0xFF7A4A1E) else Color(0xFFFFE3B8)
    val blobAlpha: Float = if (dark) 0.85f else 0.95f

    val glassTint: Color = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.62f)
    val tileTint: Color = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.70f)
    val chipFill: Color = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)
    val aiTint: Color = if (dark) Color(0xFF7C5CFF).copy(alpha = 0.28f) else Color(0xFF7C5CFF).copy(alpha = 0.16f)

    val text1: Color = if (dark) Color(0xFFF3F5F9) else Color(0xFF10131A)
    val text2: Color = if (dark) Color(0xC7F3F5F9) else Color(0xB310131A)
    val text3: Color = if (dark) Color(0x8AF3F5F9) else Color(0x8A10131A)
    val ai: Color = if (dark) Color(0xFFC0AAFF) else Color(0xFF5B3FD8)
    val ok: Color = if (dark) Color(0xFF58D6A8) else Color(0xFF15A06F)
    val shadow: Color = if (dark) Color.Black else Color(0xFF2B3550).copy(alpha = 0.35f)
    val rim: Color = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.75f)

    /** A per-site letter colour, stable across launches (hash of the domain). */
    fun tileInk(url: String): Color {
        val inks = listOf(
            Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFF34A853),
            Color(0xFF111318), Color(0xFFFF4500), Color(0xFF146EB4),
            Color(0xFF0F9D58), Color(0xFF1A73E8),
        )
        val index = (url.hashCode().mod(inks.size))
        return inks[index]
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Legacy helpers still used by the demo app                                                      */
/* --------------------------------------------------------------------------------------------- */

/** The old mesh wallpaper, kept for the standalone demo screens. */
@Composable
fun LuminaWallpaper(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = LuminaTokens.wallpaper, radius = 1400f)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xCC000000)))),
        )
    }
}

/** Small helper for the "private" dot used next to the address bar. */
@Composable
fun LuminaStatusDot(color: Color = LuminaTokens.ok) {
    Box(Modifier.size(7.dp).background(color, CircleShape))
}
