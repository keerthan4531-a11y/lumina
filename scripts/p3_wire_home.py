"""Lumina P3.3 — the native home page inside the DuckDuckGo fork.

Turns a fresh `app-fork/` (produced by `scripts/p0-fork.sh`) into a buildable Lumina binary:

  1. copies `lumina-android/java/**`  → `app/src/main/java/**`
  2. copies `lumina-android/res/**`   → `app/src/main/res/**`
  3. retires the old Compose overlay (module, host view, Gradle wiring) if a previous run left it
  4. inserts the native host into `include_new_browser_tab.xml`
  5. installs the prism launcher icon

Everything is plain Android views on purpose: the ComposeView overlay drew over the whole
new-tab panel and consumed the touch stream, which is why the omnibar's search / mic / lens
stopped responding. A ViewGroup only eats what its children eat.

    python3 scripts/p3_wire_home.py --repo app-fork [--dry-run]
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

MARKER = "<!-- lumina:home -->"

HOST_VIEW = f'''    {MARKER}
    <app.lumina.home.LuminaHomeHost
        android:id="@+id/luminaHome"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

'''

VALUES = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Lumina home page. Kept in XML too so layouts and code cannot drift apart. -->
    <color name="lumina_accent">#FF4A3D</color>
    <color name="lumina_text_1">#F6F7F9</color>
    <color name="lumina_text_2">#B4BAC4</color>
    <color name="lumina_text_3">#7D838E</color>
    <color name="lumina_wall_0">#0A0A0D</color>
    <dimen name="lumina_gutter">18dp</dimen>
    <dimen name="lumina_radius_card">26dp</dimen>
    <dimen name="lumina_bottom_bar_height">64dp</dimen>
</resources>
'''

ICON_FOREGROUND = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M54,26 C66,40 76,50 76,62 a22,22 0 0 1 -44,0 c0,-12 10,-22 22,-36 z"
        android:fillColor="#FF6B5A" />
    <path
        android:pathData="M54,34 C62,46 68,54 68,62 a14,14 0 0 1 -28,0 c0,-8 6,-16 14,-28 z"
        android:fillColor="#FF9A6B" />
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
    <path android:pathData="M0,0h108v108h-108z" android:fillColor="#0A0A0D" />
</vector>
'''

ADAPTIVE_ICON = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/lumina_icon_background" />
    <foreground android:drawable="@drawable/lumina_icon_foreground" />
    <monochrome android:drawable="@drawable/lumina_icon_foreground" />
</adaptive-icon>
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


def copy_sources(repo: Path, src: Path, dry: bool) -> None:
    """Merge our java/ and res/ trees into the app module. Never overwrite silently."""
    for leaf in ("java", "res"):
        source = src / leaf
        if not source.is_dir():
            print(f"  ! missing {source}")
            continue
        target = repo / "app" / "src" / "main" / leaf
        count = 0
        for path in source.rglob("*"):
            if not path.is_file():
                continue
            relative = path.relative_to(source)
            destination = target / relative
            if dry:
                count += 1
                continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, destination)
            count += 1
        print(f"  ✓ {leaf}/ → app ({(count)} files)")


def retire_compose_overlay(repo: Path, dry: bool) -> None:
    """Remove whatever an older Lumina run added. Idempotent, and a no-op on a clean clone."""
    legacy_host = repo / "app" / "src" / "main" / "java" / "app" / "lumina" / "ntp"
    if legacy_host.is_dir():
        if dry:
            print("  · would delete app/lumina/ntp (Compose overlay)")
        else:
            shutil.rmtree(legacy_host)
            print("  ✓ removed the Compose overlay host")

    module = repo / "lumina-ui"
    if module.is_dir():
        if dry:
            print("  · would delete lumina-ui/ (Compose module)")
        else:
            shutil.rmtree(module)
            print("  ✓ removed the Compose module")

    settings = repo / "settings.gradle"
    if settings.is_file():
        s = read(settings)
        if "lumina-ui" in s:
            s2 = re.sub(r"^.*include\s*\(?['\"]:lumina-ui['\"].*\n", "", s, flags=re.M)
            write(settings, s2, dry, "settings.gradle: - :lumina-ui")

    app_gradle = repo / "app" / "build.gradle"
    if app_gradle.is_file():
        s = read(app_gradle)
        if "lumina-ui" in s:
            s2 = re.sub(r"^.*project\(':lumina-ui'\).*\n", "", s, flags=re.M)
            write(app_gradle, s2, dry, "app/build.gradle: - :lumina-ui")


def insert_host(repo: Path, dry: bool) -> None:
    layout = repo / "app" / "src" / "main" / "res" / "layout" / "include_new_browser_tab.xml"
    if not layout.is_file():
        print("  ! include_new_browser_tab.xml not found")
        return
    s = read(layout)
    if MARKER in s:
        print("  = home host already inserted")
        return
    index = s.rfind("</FrameLayout>")
    if index == -1:
        print("  ! no closing FrameLayout in the new-tab layout")
        return
    write(layout, s[:index] + HOST_VIEW + s[index:], dry, "new-tab layout: + LuminaHomeHost")


def install_icon(repo: Path, dry: bool) -> None:
    res = repo / "app" / "src" / "main" / "res"
    write(res / "drawable" / "lumina_icon_foreground.xml", ICON_FOREGROUND, dry, "prism icon foreground")
    write(res / "drawable" / "lumina_icon_background.xml", ICON_BACKGROUND, dry, "prism icon background")
    adaptive = ADAPTIVE_ICON
    write(res / "mipmap-anydpi-v26" / "ic_launcher.xml", adaptive, dry, "adaptive launcher icon")
    write(res / "mipmap-anydpi-v26" / "ic_launcher_round.xml", adaptive, dry, "adaptive round icon")


def install_values(repo: Path, dry: bool) -> None:
    write(repo / "app" / "src" / "main" / "res" / "values" / "lumina.xml", VALUES, dry, "values/lumina.xml")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    repo = Path(args.repo).resolve()
    src = Path(__file__).resolve().parent.parent / "lumina-android"
    if not (repo / "app").is_dir():
        print(f"error: {repo} is not the browser fork", file=sys.stderr)
        return 2
    if not src.is_dir():
        print(f"error: {src} missing (run from the Lumina repo)", file=sys.stderr)
        return 2

    dry = args.dry_run
    print(f"Lumina P3.3 wiring — {repo}{' (dry run)' if dry else ''}\n")

    print("1/5 retire the Compose overlay")
    retire_compose_overlay(repo, dry)

    print("2/5 native home page sources")
    copy_sources(repo, src, dry)

    print("3/5 home host in the new-tab layout")
    insert_host(repo, dry)

    print("4/5 values")
    install_values(repo, dry)

    print("5/5 launcher icon")
    install_icon(repo, dry)

    print("\nnext: ./gradlew :app:assembleInternalDebug -Pskip-onboarding")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
