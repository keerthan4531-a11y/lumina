package app.lumina.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumina.ui.GlassAnswerCard
import app.lumina.ui.GlassBottomBar
import app.lumina.ui.GlassChip
import app.lumina.ui.GlassOmnibar
import app.lumina.ui.GlassResultCard
import app.lumina.ui.GlassSheet
import app.lumina.ui.GlassStat
import app.lumina.ui.GlassSurface
import app.lumina.ui.LuminaFavourite
import app.lumina.ui.LuminaIcons
import app.lumina.ui.LuminaNavItem
import app.lumina.ui.LuminaNtp
import app.lumina.ui.LuminaRecentPage
import app.lumina.ui.LuminaTokens
import app.lumina.ui.LuminaWallpaper
import app.lumina.ui.StaggeredEntrance

/**
 * Lumina demo — the glass UI running on a real device, without the browser fork.
 * Screens: home (native NTP) · search · results · tabs. Sheets: shields · AI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LuminaDemoApp() }
    }
}

private enum class Screen { Home, Search, Results, Tabs }

private data class DemoResult(
    val domain: String,
    val title: String,
    val snippet: String,
    val blocked: Int,
)

private val demoResults = listOf(
    DemoResult(
        "eff.org",
        "Cover Your Tracks — see how trackers view your browser",
        "Test your browser's fingerprint resistance. Lumina blocks third-party scripts, adds canvas noise and rejects cookie walls.",
        0,
    ),
    DemoResult(
        "privacyguides.org",
        "Android browsers — the privacy comparison",
        "A maintained comparison of mobile browsers by telemetry, sandboxing and tracker blocking.",
        2,
    ),
    DemoResult(
        "developer.android.com",
        "WebView security and performance guidance",
        "Renderer priority, warm-up strategy and the sandbox model. Why a pre-created WebView saves 300–400 ms.",
        1,
    ),
    DemoResult(
        "github.com",
        "Lumina — liquid glass browser engine",
        "The glass layer: tint, rim, specular and spring motion. Native home page and native result cards.",
        0,
    ),
)

private const val DEMO_ANSWER =
    "The most private option is a browser that sends no first-party telemetry, blocks third-party " +
        "scripts by default and keeps AI on-device. That means: no analytics SDK in the APK, tracker " +
        "blocking on at first launch, and summaries from Gemini Nano instead of a cloud call."

@Composable
private fun LuminaDemoApp() {
    var screen by remember { mutableStateOf(Screen.Home) }
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        LuminaWallpaper(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            when (screen) {
                Screen.Home -> {
                    Box(Modifier.weight(1f)) {
                        LuminaNtp(
                            modifier = Modifier.padding(top = LuminaTokens.s5),
                            blockedToday = 1204,
                            favourites = listOf(
                                LuminaFavourite("GitHub", "https://github.com"),
                                LuminaFavourite("Docs", "https://developer.android.com"),
                                LuminaFavourite("News", "https://news.ycombinator.com"),
                                LuminaFavourite("Music", "https://music.youtube.com"),
                            ),
                            recent = listOf(
                                LuminaRecentPage("DuckDuckGo — Privacy, simplified.", "https://duckduckgo.com", "duckduckgo.com"),
                                LuminaRecentPage("Android Developers — Compose", "https://developer.android.com", "developer.android.com"),
                                LuminaRecentPage("Kyant0 / AndroidLiquidGlass", "https://github.com", "github.com"),
                            ),
                            onSearchTap = { screen = Screen.Search },
                            onAi = { sheet = "ai" },
                            onShields = { sheet = "shield" },
                            onOpen = { screen = Screen.Results },
                        )
                    }
                }

                Screen.Search, Screen.Results -> {
                    Column(Modifier.weight(1f)) {
                        GlassOmnibar(
                            query = query,
                            onQueryChange = { query = it },
                            open = screen == Screen.Search,
                            shieldCount = 12,
                            suggestions = listOf(
                                "best private browser android",
                                "best private browser android 2026",
                                "is lumina open source",
                                "liquid glass ui compose",
                            ),
                            onSuggestion = {
                                query = it
                                screen = Screen.Results
                            },
                            onShieldClick = { sheet = "shield" },
                            onAiClick = { sheet = "ai" },
                            onSubmit = { screen = Screen.Results },
                            modifier = Modifier.padding(horizontal = LuminaTokens.s4, vertical = LuminaTokens.s2),
                        )

                        if (screen == Screen.Results) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = LuminaTokens.s4,
                                    end = LuminaTokens.s4,
                                    top = LuminaTokens.s2,
                                    bottom = 24.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
                            ) {
                                item {
                                    StaggeredEntrance(0) {
                                        GlassAnswerCard(
                                            text = DEMO_ANSWER,
                                            sources = listOf("eff.org", "privacyguides.org", "developer.android.com"),
                                        )
                                    }
                                }
                                items(demoResults) { result ->
                                    GlassResultCard(
                                        domain = result.domain,
                                        title = result.title,
                                        snippet = result.snippet,
                                        blockedTrackers = result.blocked,
                                    )
                                }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GlassChip(text = "private browser 2026")
                                        GlassChip(text = "webview sandbox")
                                        GlassChip(text = "on-device ai")
                                    }
                                }
                            }
                        }
                    }
                }

                Screen.Tabs -> {
                    Column(Modifier.weight(1f).padding(LuminaTokens.s4)) {
                        Text(
                            text = "4 TABS · PRIVATE",
                            color = LuminaTokens.text3,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(bottom = LuminaTokens.s3),
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
                            verticalArrangement = Arrangement.spacedBy(LuminaTokens.s3),
                        ) {
                            items(demoResults.take(4)) { tab ->
                                GlassSurface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clickable { screen = Screen.Results },
                                    shape = RoundedCornerShape(20.dp),
                                    tint = LuminaTokens.tintDarkSoft,
                                    elevation = 10.dp,
                                ) {
                                    Column(Modifier.padding(LuminaTokens.s3)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = LuminaIcons.globe,
                                                contentDescription = null,
                                                tint = LuminaTokens.text2,
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Text(tab.domain, color = LuminaTokens.text2, fontSize = 11.sp)
                                        }
                                        Spacer(Modifier.height(LuminaTokens.s2))
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .background(Color(0x14FFFFFF), RoundedCornerShape(10.dp)),
                                        )
                                        Spacer(Modifier.height(LuminaTokens.s2))
                                        Text(
                                            tab.title,
                                            color = LuminaTokens.text1,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            GlassBottomBar(
                items = listOf(
                    LuminaNavItem(LuminaIcons.home, "Home"),
                    LuminaNavItem(LuminaIcons.search, "Search"),
                    LuminaNavItem(LuminaIcons.tabs, "Tabs", badge = "4"),
                    LuminaNavItem(LuminaIcons.shield, "Shields"),
                ),
                selectedIndex = when (screen) {
                    Screen.Home -> 0
                    Screen.Search, Screen.Results -> 1
                    Screen.Tabs -> 2
                },
                modifier = Modifier.padding(
                    start = LuminaTokens.s4,
                    end = LuminaTokens.s4,
                    bottom = LuminaTokens.s4,
                ),
                onSelect = { index ->
                    when (index) {
                        0 -> screen = Screen.Home
                        1 -> screen = Screen.Search
                        2 -> screen = Screen.Tabs
                        else -> sheet = "shield"
                    }
                },
            )
        }

        // ------------------------------------------------------------- sheets
        Box(Modifier.align(Alignment.BottomCenter)) {
            GlassSheet(visible = sheet == "shield", onDismiss = { sheet = null }) {
                Text("Shields up", color = LuminaTokens.text1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "duckduckgo.com · 12 trackers, 8 cookies blocked",
                    color = LuminaTokens.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(LuminaTokens.s3)) {
                    GlassStat("1,204", "blocked today", Modifier.weight(1f))
                    GlassStat("18.4 MB", "data saved", Modifier.weight(1f))
                    GlassStat("0", "sent out", Modifier.weight(1f))
                }
                Spacer(Modifier.height(LuminaTokens.s4))
                listOf(
                    "Tracker blocking" to "third-party scripts",
                    "Cookie wall auto-reject" to "autoconsent",
                    "Fingerprint noise" to "canvas · audio · WebGL",
                    "AI on-device only" to "nothing leaves the phone",
                ).forEach { (title, sub) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = LuminaTokens.text1, fontSize = 14.sp)
                            Text(sub, color = LuminaTokens.text3, fontSize = 11.5.sp)
                        }
                        Box(
                            Modifier
                                .size(width = 46.dp, height = 28.dp)
                                .background(LuminaTokens.ok.copy(alpha = 0.85f), RoundedCornerShape(99.dp)),
                        )
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            GlassSheet(visible = sheet == "ai", onDismiss = { sheet = null }) {
                Text("Ask Lumina", color = LuminaTokens.text1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "on-device · sources always cited",
                    color = LuminaTokens.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    tint = Color(0x24FFFFFF),
                    elevation = 0.dp,
                    interactive = false,
                ) {
                    Text(
                        "Which browser is most private, and why?",
                        color = LuminaTokens.text1,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(LuminaTokens.s3))
                GlassAnswerCard(
                    text = DEMO_ANSWER,
                    sources = listOf("eff.org", "privacyguides.org"),
                )
                Spacer(Modifier.height(LuminaTokens.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(text = "Compare 3 tabs")
                    GlassChip(text = "Summarise page")
                    GlassChip(text = "Explain simply")
                }
            }
        }
    }
}
