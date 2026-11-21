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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Omnibar: 48dp glass pill that opens into a 320dp panel with suggestions.
 * Spring comes from the shared glass surface + spatial animation on the row above it.
 */
@Composable
fun GlassOmnibar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean = false,
    shieldCount: Int = 0,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    onShieldClick: () -> Unit = {},
    onAiClick: () -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberHapticTick()

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (open) LuminaTokens.radiusCard else LuminaTokens.radiusPill),
        tint = if (open) LuminaTokens.tintDark else LuminaTokens.tintDarkSoft,
        elevation = 20.dp,
    ) {
        Column(Modifier.height(if (open) 320.dp else 48.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = LuminaTokens.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
            ) {
                // shield chip → shield sheet in one tap
                Row(
                    Modifier
                        .height(28.dp)
                        .background(LuminaTokens.okSoft, RoundedCornerShape(8.dp))
                        .clickable(interactionSource = interaction, indication = null) {
                            tick()
                            onShieldClick()
                        }
                        .padding(horizontal = LuminaTokens.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = LuminaIcons.shield,
                        contentDescription = "Shields",
                        tint = LuminaTokens.ok,
                        modifier = Modifier.size(13.dp),
                    )
                    Text("$shieldCount", color = LuminaTokens.ok, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    interactionSource = interaction,
                    textStyle = TextStyle(
                        color = LuminaTokens.text1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f),
                )

                GlassChip(text = "AI", accent = true) {
                    tick()
                    onAiClick()
                }
            }

            if (open) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(suggestions) { suggestion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { onSuggestion(suggestion) }
                                .padding(horizontal = LuminaTokens.s5),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
                        ) {
                            Icon(
                                imageVector = LuminaIcons.search,
                                contentDescription = null,
                                tint = LuminaTokens.text3,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = suggestion,
                                color = LuminaTokens.text2,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One search result, as glass. Domain chip + title + snippet + per-result tracker badge. */
@Composable
fun GlassResultCard(
    domain: String,
    title: String,
    snippet: String,
    blockedTrackers: Int = 0,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberHapticTick()

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                tick()
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        tint = LuminaTokens.tintDarkSoft,
        elevation = 10.dp,
    ) {
        Column(Modifier.padding(LuminaTokens.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(Color(0x24FFFFFF), RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = domain.take(1).uppercase(),
                        color = LuminaTokens.text1,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = domain,
                    color = LuminaTokens.text2,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (blockedTrackers > 0) {
                    Row(
                        Modifier
                            .background(LuminaTokens.okSoft, RoundedCornerShape(99.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = LuminaIcons.shield,
                            contentDescription = null,
                            tint = LuminaTokens.ok,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = "$blockedTrackers blocked",
                            color = LuminaTokens.ok,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(LuminaTokens.s2))
            Text(
                text = title,
                color = LuminaTokens.text1,
                fontSize = LuminaTokens.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snippet,
                color = LuminaTokens.text2,
                fontSize = 13.5.sp,
                maxLines = 3,
            )
        }
    }
}

/** The AI answer card: streaming text, on-device badge, mandatory citations. */
@Composable
fun GlassAnswerCard(
    text: String,
    sources: List<String>,
    modifier: Modifier = Modifier,
    onDevice: Boolean = true,
    sourceClick: (Int) -> Unit = {},
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LuminaTokens.radiusCard),
        tint = Color(0x3DB48CFF),
        elevation = 14.dp,
    ) {
        Column(Modifier.padding(LuminaTokens.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = LuminaIcons.ai,
                    contentDescription = null,
                    tint = LuminaTokens.ai,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "LUMINA ANSWER",
                    color = LuminaTokens.ai,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                if (onDevice) {
                    Box(
                        Modifier
                            .background(LuminaTokens.okSoft, RoundedCornerShape(99.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "on-device",
                            color = LuminaTokens.ok,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(LuminaTokens.s3))
            Text(
                text = text,
                color = LuminaTokens.text1,
                fontSize = LuminaTokens.body,
                lineHeight = 23.sp,
            )
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(LuminaTokens.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sources.forEachIndexed { index, source ->
                        GlassChip(text = "${index + 1}  $source") { sourceClick(index) }
                    }
                }
            }
        }
    }
}

/** Big animated counter used by the shield sheet and the home page. */
@Composable
fun GlassStat(value: String, label: String, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tint = LuminaTokens.tintDarkSoft,
        elevation = 0.dp,
        interactive = false,
    ) {
        Column(Modifier.padding(LuminaTokens.s3)) {
            Text(
                text = value,
                color = LuminaTokens.text1,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = label, color = LuminaTokens.text2, fontSize = 11.sp)
        }
    }
}
