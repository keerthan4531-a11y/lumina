package app.lumina.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LuminaFavourite(val title: String, val url: String)
data class LuminaRecentPage(val title: String, val url: String, val domain: String)

/**
 * The Lumina home page. 100% native Compose, zero network requests:
 * greeting, glass search field, AI pill, quick cards, favourites, recent.
 */
@Composable
fun LuminaNtp(
    modifier: Modifier = Modifier,
    greeting: String = "Good evening",
    subtitle: String = "",
    blockedToday: Int = 0,
    favourites: List<LuminaFavourite> = emptyList(),
    recent: List<LuminaRecentPage> = emptyList(),
    onSearchTap: () -> Unit = {},
    onAi: () -> Unit = {},
    onShields: () -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    val tick = rememberHapticTick()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LuminaTokens.s5),
        verticalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
    ) {
        StaggeredEntrance(0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        color = LuminaTokens.text1,
                        fontSize = LuminaTokens.hero,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            color = LuminaTokens.text2,
                            fontSize = 13.sp,
                        )
                    }
                }
                GlassChip(text = "$blockedToday", accent = false) {
                    tick()
                    onShields()
                }
            }
        }

        Spacer(Modifier.height(LuminaTokens.s3))

        // search field — tap hands focus to the real omnibar above
        StaggeredEntrance(1) {
            val interaction = remember { MutableInteractionSource() }
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .pressScale(interaction)
                    .clickable(interactionSource = interaction, indication = null) {
                        tick()
                        onSearchTap()
                    },
                shape = RoundedCornerShape(LuminaTokens.radiusPill),
                tint = LuminaTokens.tintDark,
                elevation = 18.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(start = LuminaTokens.s5, end = LuminaTokens.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
                ) {
                    Icon(
                        imageVector = LuminaIcons.search,
                        contentDescription = null,
                        tint = LuminaTokens.text2,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        text = "Search or type a URL",
                        color = LuminaTokens.text2,
                        fontSize = 15.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    GlassSurface(
                        modifier = Modifier
                            .height(40.dp)
                            .clickable(interactionSource = interaction, indication = null) {
                                tick()
                                onAi()
                            },
                        shape = RoundedCornerShape(LuminaTokens.radiusPill),
                        tint = LuminaTokens.aiSoft,
                        elevation = 0.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = LuminaIcons.ai,
                                contentDescription = null,
                                tint = LuminaTokens.ai,
                                modifier = Modifier.size(14.dp),
                            )
                            Text("AI", color = LuminaTokens.ai, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(LuminaTokens.s1))

        StaggeredEntrance(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3)) {
                QuickCard(
                    title = "Ask Lumina",
                    subtitle = "summarise · explain · compare",
                    icon = LuminaIcons.ai,
                    accent = LuminaTokens.ai,
                    tint = Color(0x33B48CFF),
                    modifier = Modifier.weight(1f),
                    onClick = onAi,
                )
                QuickCard(
                    title = "Shields",
                    subtitle = "$blockedToday blocked today",
                    icon = LuminaIcons.shield,
                    accent = LuminaTokens.ok,
                    tint = Color(0x2958D6A8),
                    modifier = Modifier.weight(1f),
                    onClick = onShields,
                )
            }
        }

        if (favourites.isNotEmpty()) {
            StaggeredEntrance(3) {
                Column {
                    SectionLabel("Favourites")
                    Row(horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3)) {
                        favourites.take(5).forEach { favourite ->
                            GlassSurface(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clickable { onOpen(favourite.url) },
                                shape = RoundedCornerShape(18.dp),
                                tint = Color(0x336EA8FF),
                                elevation = 0.dp,
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = favourite.title.take(1).uppercase(),
                                        color = LuminaTokens.text1,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            StaggeredEntrance(4) {
                Column {
                    SectionLabel("Recent · local only")
                    recent.forEach { page ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable { onOpen(page.url) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
                        ) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = page.domain.take(1).uppercase(),
                                    color = LuminaTokens.text1,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = page.title,
                                    color = LuminaTokens.text1,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                )
                                Text(
                                    text = page.domain,
                                    color = LuminaTokens.text3,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = LuminaTokens.text3,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = LuminaTokens.s3, bottom = LuminaTokens.s2),
    )
}

@Composable
private fun QuickCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberHapticTick()
    GlassSurface(
        modifier = modifier
            .height(104.dp)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                tick()
                onClick()
            },
        shape = RoundedCornerShape(LuminaTokens.radiusCard),
        tint = tint,
        elevation = 12.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(LuminaTokens.s4),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(title, color = LuminaTokens.text1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = LuminaTokens.text2, fontSize = 11.5.sp, maxLines = 2)
            }
        }
    }
}

/** The mesh wallpaper the glass sits on. Drawn once, never re-allocated. */
@Composable
fun LuminaWallpaper(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = LuminaTokens.wallpaper,
                    radius = 1400f,
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x00000000), Color(0xCC000000)),
                    ),
                ),
        )
    }
}

/** Small helper for the "private" dot used next to the address bar. */
@Composable
fun LuminaStatusDot(color: Color = LuminaTokens.ok) {
    Box(
        Modifier
            .size(7.dp)
            .background(color, CircleShape),
    )
}
