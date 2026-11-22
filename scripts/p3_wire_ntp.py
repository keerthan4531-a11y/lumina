#!/usr/bin/env python3
"""
Lumina P3 — wire the glass home page into the rebranded DuckDuckGo fork.

What it does (idempotent, dry-run friendly):

  1. copies `lumina-ui/` next to `app/` so Gradle auto-discovers it as `:lumina-ui`
     and pins its Compose BOM to the fork's own `version.androidx.compose`
  2. adds `implementation project(':lumina-ui')` to `app/build.gradle`
  3. writes `app/src/main/java/app/lumina/ntp/LuminaHomeView.kt` — a FrameLayout that
     hosts the Compose glass NTP and hands focus to the *real* omnibar on tap
  4. inserts that view as the last child of `include_new_browser_tab.xml`, so it draws
     over the old logo page and inherits the app's show/hide logic
  5. installs the Lumina prism app icon (adaptive + monochrome)

    python3 scripts/p3_wire_ntp.py --repo app-fork [--dry-run]
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

MARKER = "<!-- lumina:glass-ntp -->"

HOME_VIEW = '''package app.lumina.ntp

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import app.lumina.ui.LuminaFavourite
import app.lumina.ui.LuminaNtp
import app.lumina.ui.LuminaRecentPage
import app.lumina.ui.LuminaTokens
import app.lumina.ui.LuminaWallpaper

/**
 * Lumina's glass home page, hosted inside the DuckDuckGo tab layout.
 *
 * It is a plain FrameLayout so the existing XML can embed it; the old NTP stays underneath
 * (harmless, it is never seen) and all of the app's NTP show/hide logic keeps working.
 */
class LuminaHomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val compose = ComposeView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    init {
        setBackgroundColor(0xFF06090F.toInt())
        addView(compose)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                LuminaWallpaper(Modifier.fillMaxSize())
                LuminaNtp(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 96.dp),
                    greeting = "Lumina",
                    subtitle = "Private by default · on-device AI",
                    blockedToday = 0,
                    favourites = demoFavourites,
                    recent = emptyList(),
                    onSearchTap = { focusOmnibar() },
                    onAi = { focusOmnibar() },
                    onShields = { focusOmnibar() },
                    onOpen = { /* P3.1: load in the current tab */ },
                )
            }
        }
    }

    /** Hand the tap over to the browser's real address bar. */
    private fun focusOmnibar() {
        val activity = context.findActivity() ?: return
        val id = resources.getIdentifier("omnibarTextInput", "id", activity.packageName)
        if (id == 0) return
        val input: View = activity.findViewById(id) ?: return
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private companion object {
        val demoFavourites = listOf(
            LuminaFavourite("Search", "https://duckduckgo.com"),
            LuminaFavourite("Docs", "https://developer.android.com"),
            LuminaFavourite("GitHub", "https://github.com"),
            LuminaFavourite("News", "https://news.ycombinator.com"),
        )
    }
}
'''

ICON_FOREGROUND = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M54,26 C66,40 76,50 76,62 a22,22 0 0 1 -44,0 c0,-12 10,-22 22,-36 z"
        android:fillColor="#8FB4FF" />
    <path
        android:pathData="M54,34 C62,46 68,54 68,62 a14,14 0 0 1 -28,0 c0,-8 6,-16 14,-28 z"
        android:fillColor="#B48CFF" />
    <path
        android:pathData="M46,54 a10,10 0 0 0 6,16"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="2.4"
        android:strokeLineCap="round" />
</vector>
'''

ICON_BACKGROUND = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z" android:fillColor="#0B1220" />
</vector>
'''

ADAPTIVE_ICON = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/lumina_icon_background" />
    <foreground android:drawable="@drawable/lumina_icon_foreground" />
    <monochrome android:drawable="@drawable/lumina_icon_foreground" />
</adaptive-icon>
'''

NTP_OVERLAY = f'''    {MARKER}
    <app.lumina.ntp.LuminaHomeView
        android:id="@+id/luminaGlassHome"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

'''


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def write(p: Path, s: str, dry: bool, note: str) -> None:
    if dry:
        print(f"  · would write {p.name}: {note}")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(s, encoding="utf-8")
    print(f"  ✓ {note}")


def compose_version(repo: Path) -> str:
    props = repo / "versions.properties"
    if props.is_file():
        m = re.search(r"^version\.androidx\.compose=(.+)$", read(props), re.M)
        if m:
            return m.group(1).strip()
    return "2024.10.01"


def copy_ui(repo: Path, src: Path, dry: bool) -> None:
    dst = repo / "lumina-ui"
    if dry:
        print(f"  · would copy {src.name}/ → {dst.name}/")
    else:
        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(
            src,
            dst,
            ignore=shutil.ignore_patterns("build", ".gradle", "*.iml"),
        )
        print(f"  ✓ copied lumina-ui/ ({compose_version(repo)} compose)")

    build = dst / "build.gradle.kts"
    if not build.is_file():
        return
    s = read(build)
    # fork is compileSdk 36 / minSdk 28 and owns the Compose BOM version
    s = re.sub(r'compose-bom:[0-9.]+', f"compose-bom:{compose_version(repo)}", s)
    s = re.sub(r"compileSdk = \d+", "compileSdk = 36", s)
    # AGP 9 ships built-in Kotlin support: applying the Kotlin plugin is a hard error there
    s = re.sub(r'^\s*id\("org\.jetbrains\.kotlin\.android"\)\s*\n', "", s, flags=re.M)
    # ...and `kotlinOptions` no longer exists on the android extension
    s = re.sub(r"\n\s*kotlinOptions\s*\{[^}]*\}\n", "\n", s)
    write(build, s, dry, "lumina-ui/build.gradle.kts adapted to the fork (AGP 9, BOM, SDK 36)")


def add_dependency(repo: Path, dry: bool) -> None:
    app = repo / "app" / "build.gradle"
    s = read(app)
    if "':lumina-ui'" in s:
        print("  = :lumina-ui dependency already present")
        return
    m = re.search(r"\ndependencies\s*\{", s)
    if not m:
        print("  ! could not find dependencies block in app/build.gradle")
        return
    insert = m.end()
    s2 = s[:insert] + "\n    implementation project(':lumina-ui')  // Lumina glass UI" + s[insert:]
    write(app, s2, dry, "app/build.gradle: + :lumina-ui")


def write_home_view(repo: Path, dry: bool) -> None:
    dst = repo / "app" / "src" / "main" / "java" / "app" / "lumina" / "ntp" / "LuminaHomeView.kt"
    if dst.is_file() and "class LuminaHomeView" in read(dst):
        print("  = LuminaHomeView already written")
        return
    write(dst, HOME_VIEW, dry, "LuminaHomeView.kt (glass NTP host)")


def patch_ntp_layout(repo: Path, dry: bool) -> None:
    layout = repo / "app" / "src" / "main" / "res" / "layout" / "include_new_browser_tab.xml"
    if not layout.is_file():
        print("  ! include_new_browser_tab.xml not found")
        return
    s = read(layout)
    if MARKER in s:
        print("  = NTP overlay already inserted")
        return
    idx = s.rfind("</FrameLayout>")
    if idx == -1:
        print("  ! root is not a FrameLayout — cannot insert safely")
        return
    s2 = s[:idx] + NTP_OVERLAY + s[idx:]
    write(layout, s2, dry, "include_new_browser_tab.xml: + glass NTP overlay")


def install_icon(repo: Path, dry: bool) -> None:
    res = repo / "app" / "src" / "main" / "res"
    write(res / "drawable" / "lumina_icon_foreground.xml", ICON_FOREGROUND, dry, "prism icon foreground")
    write(res / "drawable" / "lumina_icon_background.xml", ICON_BACKGROUND, dry, "prism icon background")
    write(res / "mipmap-anydpi-v26" / "ic_launcher.xml", ADAPTIVE_ICON, dry, "adaptive launcher icon")
    write(res / "mipmap-anydpi-v26" / "ic_launcher_round.xml", ADAPTIVE_ICON, dry, "adaptive round icon")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    repo = Path(args.repo).resolve()
    src = Path(__file__).resolve().parent.parent / "lumina-ui"
    if not (repo / "app").is_dir():
        print(f"error: {repo} is not the browser fork", file=sys.stderr)
        return 2
    if not src.is_dir():
        print(f"error: {src} missing (run from the Lumina repo)", file=sys.stderr)
        return 2

    dry = args.dry_run
    print(f"Lumina P3 wiring — {repo}{' (dry run)' if dry else ''}\n")

    print("1/5 glass UI module")
    copy_ui(repo, src, dry)

    print("2/5 app dependency")
    add_dependency(repo, dry)

    print("3/5 NTP host view")
    write_home_view(repo, dry)

    print("4/5 NTP layout overlay")
    patch_ntp_layout(repo, dry)

    print("5/5 launcher icon")
    install_icon(repo, dry)

    print("\nnext: ./gradlew :app:assembleInternalDebug -Pskip-onboarding")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
