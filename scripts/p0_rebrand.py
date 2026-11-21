#!/usr/bin/env python3
"""
Lumina P0 — rebrand + telemetry strip.

Operates on a checkout of duckduckgo/Android and makes it "ours":
  * applicationId  -> app.lumina.browser
  * root project   -> lumina
  * appName        -> Lumina
  * telemetry / funnel modules deleted (pixels, survey, anrs, ...) + dangling deps removed
  * one store flavour (lumina) instead of internal/fdroid/play
  * node asset stubs so the build survives a failed `npm ci`

Idempotent: safe to run twice. Use --dry-run to see what would change.

    python3 scripts/p0_rebrand.py --repo app-fork
    python3 scripts/p0_rebrand.py --repo app-fork --dry-run
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

# Modules that exist purely to measure users. Removing the directory is enough:
# settings.gradle auto-discovers modules by scanning for build files.
TELEMETRY_DIRS = [
    "pixels",
    "pixels-impl",
    "attributed-metrics",
    "attributed-metrics-impl",
    "survey",
    "survey-impl",
    "anrs",
    "anrs-internal",
    "anrs-impl",
    "internalfeedback",
    "internalfeedback-impl",
    "app-store",
    "app-store-impl",
]

# Not telemetry, but dead weight for v1 Lumina (money / sync / other platforms).
DROP_DIRS = [
    "vpn",
    "vpn-impl",
    "vpn-onboarding",
    "subscriptions",
    "subscription-impl",
    "email",
    "email-impl",
    "sync",
    "sync-impl",
    "devicemigration",
    "devicemigration-impl",
    "widget",
    "widget-impl",
    "macos",
    "windows",
]

NODE_STUBS = [
    "node_modules/@duckduckgo/content-scope-scripts/build/android/pages",
    "node_modules/@duckduckgo/privacy-dashboard/build/app",
]

REMOVED_PROJECTS = TELEMETRY_DIRS + DROP_DIRS


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def write(p: Path, s: str, dry: bool, note: str) -> None:
    if dry:
        print(f"  · would write {p.name}: {note}")
        return
    backup = p.with_suffix(p.suffix + ".lumina.bak")
    if not backup.exists():
        backup.write_text(read(p), encoding="utf-8")
    p.write_text(s, encoding="utf-8")
    print(f"  ✓ {p.relative_to(p.parents[len(p.parents) - 1])}: {note}")


def short(p: Path, repo: Path) -> str:
    try:
        return str(p.relative_to(repo))
    except ValueError:
        return str(p)


def rebrand_gradle(repo: Path, dry: bool) -> None:
    app_gradle = repo / "app" / "build.gradle"
    if app_gradle.exists():
        s = read(app_gradle)
        s2 = s.replace(
            'applicationId "com.duckduckgo.mobile.android"',
            'applicationId "app.lumina.browser"',
        )
        if s2 != s:
            write(app_gradle, s2, dry, "applicationId -> app.lumina.browser")
        elif 'applicationId "app.lumina.browser"' in s:
            print("  = applicationId already Lumina")

    settings = repo / "settings.gradle"
    if settings.exists():
        s = read(settings)
        s2 = re.sub(r'rootProject\.name\s*=\s*"[^"]+"', 'rootProject.name = "lumina"', s)
        if s2 != s:
            write(settings, s2, dry, "rootProject.name -> lumina")
        else:
            print("  = rootProject.name already lumina")


def rebrand_strings(repo: Path, dry: bool) -> None:
    strings = repo / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    if not strings.exists():
        print(f"  ! {short(strings, repo)} missing — skipping app name")
        return
    s = read(strings)
    s2, n = re.subn(r'<string name="appName"[^>]*>[^<]*</string>',
                    '<string name="appName" translatable="false">Lumina</string>', s)
    if n and s2 != s:
        write(strings, s2, dry, "appName -> Lumina")
    else:
        print("  = appName already Lumina")


def strip_modules(repo: Path, dry: bool) -> None:
    removed = []
    for name in REMOVED_PROJECTS:
        d = repo / name
        if d.is_dir():
            removed.append(name)
            if dry:
                print(f"  · would delete {name}/")
            else:
                shutil.rmtree(d)
                print(f"  ✓ deleted {name}/")
    if not removed:
        print("  = telemetry/funnel modules already gone")


# --- module graph manipulation -------------------------------------------------
# settings.gradle auto-discovers modules: any top-level dir with a build file becomes
# ":dir", otherwise its child dirs become ":child" (flat names, projectDir remapped).
IGNORED_DIRS = {
    "buildSrc", "fastlane", "submodules", "node_modules", "gradle",
    "build", "build-cache", ".maestro",
}

PROJECT_REF = re.compile(r"project\(\s*['\"](:[-\w.:]+)['\"]\s*\)")
DEP_LINE = re.compile(
    r"^\s*(implementation|api|compileOnly|runtimeOnly|kapt|ksp|debugImplementation|"
    r"releaseImplementation|testImplementation|androidTestImplementation|internalImplementation)\b"
)


def has_build_file(d: Path) -> bool:
    return (d / "build.gradle").is_file() or (d / "build.gradle.kts").is_file()


def valid_module_paths(repo: Path) -> set[str]:
    """Mirror settings.gradle's discovery so we know which :modules actually exist."""
    valid: set[str] = set()
    for parent in repo.iterdir():
        if not parent.is_dir() or parent.name.startswith(".") or parent.name in IGNORED_DIRS:
            continue
        if has_build_file(parent):
            valid.add(":" + parent.name)
        else:
            for child in parent.iterdir():
                if child.is_dir() and has_build_file(child):
                    valid.add(":" + child.name)
    return valid


def strip_dangling_deps(repo: Path, dry: bool) -> None:
    """
    Delete dependency lines that point at modules which no longer exist.

    Set-difference, not a name list: deleting `anrs/` also deletes `anrs-api` and
    `anrs-impl` (auto-discovered as :anrs-api / :anrs-impl), and every module that
    depended on them must lose that line or Gradle configuration fails.
    """
    valid = valid_module_paths(repo)
    print(f"  · {len(valid)} modules discovered after the diet")

    removed: list[str] = []
    touched = 0
    for build in sorted(repo.rglob("build.gradle")) + sorted(repo.rglob("build.gradle.kts")):
        parts = set(build.parts)
        if parts & {"node_modules", "build", "buildSrc"}:
            continue
        try:
            lines = read(build).splitlines(keepends=True)
        except (UnicodeDecodeError, OSError):
            continue

        out, changed = [], False
        for line in lines:
            refs = PROJECT_REF.findall(line)
            missing = [r for r in refs if r not in valid]
            if missing and (DEP_LINE.match(line) or "project(" in line):
                changed = True
                removed.append(f"{short(build, repo)}: {line.strip()[:110]}")
                continue
            out.append(line)

        if changed:
            touched += 1
            write(build, "".join(out), dry, f"{short(build, repo)} cleaned")

    if removed:
        print(f"  · dropped {len(removed)} dangling dependency line(s), first 40:")
        for line in removed[:40]:
            print(f"      - {line}")
    else:
        print("  = no dangling module dependencies")


def single_flavour(repo: Path, dry: bool) -> None:
    """
    Keep ONE flavour: `internal`.

    Why not rename it to `lumina`? The upstream root build.gradle enforces
    "'internal' modules must use internalImplementation" by checking that the
    configuration name contains "internal". Renaming the flavour breaks that check
    in ~23 places, so we keep the name and delete `play` + `fdroid` instead.
    """
    app_gradle = repo / "app" / "build.gradle"
    if not app_gradle.exists():
        return
    s = read(app_gradle)
    before = s

    for name in ("play", "fdroid"):
        if re.search(r"\n[ \t]*" + name + r"[ \t]*\{", s):
            s = remove_gradle_block(s, name)

    # flavour-specific dependency configurations disappear with the flavours
    s = re.sub(r"^[ \t]*(play|fdroid)(Implementation|Compile|RuntimeOnly|Api)\b.*\n", "", s, flags=re.M)

    # the variantFilter only existed to skip fdroidDebug
    s = remove_gradle_block(s, "variantFilter")

    # tidy: collapse the blank lines the deletions left behind
    s = re.sub(r"\n{3,}", "\n\n", s)

    if s != before:
        write(app_gradle, s, dry, "flavours -> single 'internal' (play + fdroid removed)")
    else:
        print("  = already a single flavour")


def node_stubs(repo: Path, dry: bool) -> None:
    for rel in NODE_STUBS:
        d = repo / rel
        if d.is_dir():
            continue
        if dry:
            print(f"  · would create {rel}/")
        else:
            d.mkdir(parents=True, exist_ok=True)
            print(f"  ✓ stub {rel}/")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True, help="path to the duckduckgo/Android checkout")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    repo = Path(args.repo).resolve()
    if not (repo / "settings.gradle").exists():
        print(f"error: {repo} does not look like the duckduckgo/Android repo", file=sys.stderr)
        return 2

    dry = args.dry_run
    print(f"Lumina P0 rebrand — {repo}{' (dry run)' if dry else ''}\n")

    print("1/6 identity")
    rebrand_gradle(repo, dry)

    print("2/6 app name")
    rebrand_strings(repo, dry)

    print("3/6 telemetry + dead-weight modules")
    strip_modules(repo, dry)

    print("4/6 dangling dependencies")
    strip_dangling_deps(repo, dry)

    print("5/6 flavours")
    single_flavour(repo, dry)

    print("6/6 node asset stubs")
    node_stubs(repo, dry)

    print("\nnext: ./gradlew help -q   (cheap config check), then:")
    print("      ./gradlew :app:assembleInternalDebug -x lint -x test")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
