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
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import app.lumina.ui.LuminaNtp

/**
 * Lumina's start page, hosted inside the DuckDuckGo tab layout.
 *
 * A plain FrameLayout so the existing XML can embed it: the app's own NTP show/hide logic
 * (`newTabLayout.show()/gone()`) keeps working, and the real omnibar above is never replaced.
 * [topInset] is measured from the live omnibar so the glass page starts exactly under it instead
 * of guessing a padding that breaks on different densities / browser bar states.
 */
class LuminaHomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val topInsetDp = mutableStateOf(0)

    private val compose = ComposeView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    init {
        setBackgroundColor(0)
        addView(compose)
        compose.setContent {
            LuminaNtp(
                modifier = Modifier.fillMaxSize(),
                topInset = topInsetDp.value.dp,
                dark = isSystemInDarkTheme(),
                onSearchTap = { focusOmnibar() },
                onAi = { focusOmnibar() },
                onShields = { focusOmnibar() },
                onOpen = { url -> openUrl(url) },
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        measureOmnibar()
    }

    /** Start the page below the browser's own address bar. */
    private fun measureOmnibar() {
        val omnibar = omnibarView() ?: return
        val om = IntArray(2)
        val me = IntArray(2)
        omnibar.getLocationOnScreen(om)
        getLocationOnScreen(me)
        val density = resources.displayMetrics.density
        val bottomPx = om[1] + omnibar.height - me[1]
        val dp = if (bottomPx <= 0) 0 else ((bottomPx + 14 * density) / density).toInt()
        if (dp != topInsetDp.value) topInsetDp.value = dp
    }

    private fun omnibarView(): View? {
        val activity = context.findActivity() ?: return null
        val id = resources.getIdentifier("omnibarTextInput", "id", activity.packageName)
        if (id == 0) return null
        return activity.findViewById(id)
    }

    /** Hand the tap over to the browser's real address bar. */
    private fun focusOmnibar() {
        val input = omnibarView() ?: return
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * A shortcut was tapped. The omnibar is the only supported entry point from a hosted view, so
     * type the URL into it and submit — the existing tab machinery takes it from there.
     */
    private fun openUrl(url: String) {
        val input = omnibarView()
        if (input is EditText) {
            input.setText(url)
            input.setSelection(input.text.length)
            input.onEditorAction(EditorInfo.IME_ACTION_GO)
            return
        }
        focusOmnibar()
    }

    private fun Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
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
    s = re.sub(r"compileSdk = \d+", "compileSdk = 37", s)
    # AGP 9 ships built-in Kotlin support: applying the Kotlin plugin is a hard error there
    s = re.sub(r'^\s*id\("org\.jetbrains\.kotlin\.android"\)\s*\n', "", s, flags=re.M)
    # ...and `kotlinOptions` no longer exists on the android extension
    s = re.sub(r"\n\s*kotlinOptions\s*\{[^}]*\}\n", "\n", s)
    write(build, s, dry, "lumina-ui/build.gradle.kts adapted to the fork (AGP 9, BOM, SDK 36)")


def bump_compile_sdk(repo: Path, dry: bool) -> None:
    """
    skydoves/Cloudy pulls the current androidx stack (core 1.19, compose 1.11, activity 1.13);
    those AARs declare minCompileSdk 37, so the app is compiled against android-37 while the
    targetSdk stays 36 (a compileSdk bump does not change runtime behaviour).
    """
    build = repo / "build.gradle"
    if not build.is_file():
        print("  ! no root build.gradle, skipping")
        return
    s = read(build)
    if "compile_sdk = 37" in s:
        print("  = compile_sdk already 37")
        return
    new = re.sub(r"compile_sdk = \d+", "compile_sdk = 37", s, count=1)
    if new == s:
        print("  ! could not find compile_sdk in build.gradle")
        return
    write(build, new, dry, "root build.gradle: compile_sdk 36 -> 37 (Cloudy's androidx stack)")

    # AGP only warns for a compileSdk newer than the one it was tested with; keep the log clean.
    props = repo / "gradle.properties"
    if props.is_file():
        ps = read(props)
        if "suppressUnsupportedCompileSdk" not in ps:
            write(
                props,
                ps.rstrip("\n") + "\n# Lumina P3: android-37 is required by the glass stack\n"
                "android.suppressUnsupportedCompileSdk=37\n",
                dry,
                "gradle.properties: suppressUnsupportedCompileSdk=37",
            )


def patch_platform37_nullability(repo: Path, dry: bool) -> None:
    """
    android-37 annotates `ActivityManager.AppTask.taskInfo` as nullable, which upstream DDG
    (written against 36) dereferences directly. Two call sites in the whole tree; make them
    safe calls. Nothing else changes.
    """
    targets = list((repo / "app" / "src").rglob("*.kt"))
    hits = 0
    for path in targets:
        src = read(path)
        if ".taskInfo." not in src:
            continue
        patched = src.replace(".taskInfo.baseIntent.", ".taskInfo?.baseIntent?.")
        patched = patched.replace(".taskInfo.", ".taskInfo?.")
        if patched != src:
            hits += 1
            write(path, patched, dry, f"{path.name}: taskInfo -> taskInfo? (android-37)")
    if hits == 0:
        print("  = no taskInfo dereferences to fix")


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
    if dst.is_file() and read(dst) == HOME_VIEW:
        print("  = LuminaHomeView.kt up to date")
        return
    write(dst, HOME_VIEW, dry, "LuminaHomeView.kt (start page host)")


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

    print("1/7 glass UI module")
    copy_ui(repo, src, dry)

    print("2/7 app dependency")
    add_dependency(repo, dry)

    print("3/7 NTP host view")
    write_home_view(repo, dry)

    print("4/7 NTP layout overlay")
    patch_ntp_layout(repo, dry)

    print("5/7 launcher icon")
    install_icon(repo, dry)

    print("6/7 compileSdk 37")
    bump_compile_sdk(repo, dry)

    print("7/7 android-37 nullability")
    patch_platform37_nullability(repo, dry)

    print("\nnext: ./gradlew :app:assembleInternalDebug -Pskip-onboarding")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
