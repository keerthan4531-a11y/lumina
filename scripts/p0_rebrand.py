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
import tempfile
from pathlib import Path

# Backups must live OUTSIDE the repo: Android's resource merger rejects any non-.xml
# file inside res/ (that is exactly what "strings.xml.lumina.bak" triggered).
BACKUP_ROOT = Path(tempfile.gettempdir()) / "lumina-p0-backup"

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
    try:
        rel = p.resolve().relative_to(REPO_ROOT)
        backup = BACKUP_ROOT / rel
        if not backup.exists():
            backup.parent.mkdir(parents=True, exist_ok=True)
            backup.write_text(read(p), encoding="utf-8")
    except (ValueError, AttributeError):
        pass  # no repo root known — git is the backup anyway
    p.write_text(s, encoding="utf-8")
    print(f"  ✓ {note}")


REPO_ROOT: Path | None = None


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


"""Modules we would like to delete (leaf-name patterns). Nothing is deleted on a pattern
alone: the diet is resolved against the real dependency graph first."""
CANDIDATE_PATTERNS = [
    r"^pixels(-|$)",
    r"^survey(-|$)",
    r"^anrs(-|$)",
    r"^attributed-metrics(-|$)",
    r"^internalfeedback(-|$)",
    r"^app-store(-|$)",
    r"^subscriptions?(-|$)",
    r"^subscription-",
    r"^sync(-|$)",
    r"^vpn(-|$)",
    r"^network-protection(-|$)",
    r"^email(-|$)",
    r"^macos(-|$)",
    r"^windows(-|$)",
    r"^devicemigration(-|$)",
    r"^widget(-|$)",
]

IGNORED_DIRS = {
    "buildSrc", "fastlane", "submodules", "node_modules", "gradle",
    "build", "build-cache", ".maestro",
}

PROJECT_REF = re.compile(r"project\(\s*(?:path\s*:\s*)?['\"](:[-\w.:]+)['\"]")
DEP_LINE = re.compile(
    r"^\s*(implementation|api|compileOnly|runtimeOnly|kapt|ksp|debugImplementation|"
    r"releaseImplementation|testImplementation|androidTestImplementation|internalImplementation)\b"
)


def has_build_file(d: Path) -> bool:
    return (d / "build.gradle").is_file() or (d / "build.gradle.kts").is_file()


def discover_modules(repo: Path) -> dict[str, Path]:
    """Mirror settings.gradle: top-level dir with a build file → ":dir"; else ":child" (flat)."""
    modules: dict[str, Path] = {}
    for parent in sorted(repo.iterdir()):
        if not parent.is_dir() or parent.name.startswith(".") or parent.name in IGNORED_DIRS:
            continue
        if has_build_file(parent):
            modules[":" + parent.name] = parent
        else:
            for child in sorted(parent.iterdir()):
                if child.is_dir() and has_build_file(child):
                    modules[":" + child.name] = child
    return modules


def module_graph(repo: Path, modules: dict[str, Path]) -> dict[str, set[str]]:
    """module → set of modules it depends on (project(':x') / project(path: ':x'))."""
    graph: dict[str, set[str]] = {m: set() for m in modules}
    for name, d in modules.items():
        build = d / "build.gradle" if (d / "build.gradle").is_file() else d / "build.gradle.kts"
        try:
            text = read(build)
        except (UnicodeDecodeError, OSError):
            continue
        graph[name] = set(PROJECT_REF.findall(text))
    return graph


def resolve_diet(graph: dict[str, set[str]]) -> tuple[set[str], dict[str, list[str]]]:
    """
    Fixpoint: a candidate module is deleted only when nothing that survives depends on it.
    `:app` does not block deletion — those dependency lines are stripped afterwards.

    Returns (modules to delete, modules kept *because* someone still needs them).
    """
    candidates = {
        name for name in graph
        if any(re.search(pat, name.lstrip(":")) for pat in CANDIDATE_PATTERNS)
    }
    removed: set[str] = set()
    changed = True
    while changed:
        changed = False
        for name in sorted(candidates - removed):
            blocking = {
                depender for depender, deps in graph.items()
                if name in deps and depender not in removed and depender != ":app"
            }
            if not blocking:
                removed.add(name)
                changed = True

    kept: dict[str, list[str]] = {}
    for name in sorted(candidates - removed):
        blockers = sorted({
            depender for depender, deps in graph.items()
            if name in deps and depender not in removed and depender != ":app"
        })
        kept[name] = blockers
    return removed, kept


def apply_diet(repo: Path, dry: bool) -> None:
    modules = discover_modules(repo)
    print(f"  · {len(modules)} modules discovered")
    graph = module_graph(repo, modules)
    removed, kept = resolve_diet(graph)

    print(f"  · deleting {len(removed)} module(s) (build file removed → excluded from discovery)")
    for name in sorted(removed):
        build = modules[name] / "build.gradle"
        if not build.is_file():
            build = modules[name] / "build.gradle.kts"
        if dry:
            print(f"    · would remove {short(build, repo)}")
        else:
            build.unlink(missing_ok=True)
            print(f"    ✓ removed {short(build, repo)}")

    if kept:
        print(f"  ! keeping {len(kept)} candidate(s) because a surviving module needs them:")
        for name, blockers in kept.items():
            print(f"    - {name} ← {', '.join(blockers[:4])}{' …' if len(blockers) > 4 else ''}")

    # ---- strip references to what is gone (from survivors, including :app) ----
    gone = removed
    removed_lines = 0
    touched = 0
    for name, d in modules.items():
        if name in gone:
            continue
        build = d / "build.gradle" if (d / "build.gradle").is_file() else d / "build.gradle.kts"
        if not build.is_file():
            continue
        try:
            lines = read(build).splitlines(keepends=True)
        except (UnicodeDecodeError, OSError):
            continue
        out, changed = [], False
        for line in lines:
            if any(ref in gone for ref in PROJECT_REF.findall(line)):
                changed = True
                removed_lines += 1
                continue
            out.append(line)
        if changed:
            touched += 1
            write(build, "".join(out), dry, f"{name} cleaned")

    print(f"  · removed {removed_lines} dependency line(s) across {touched} module(s)")


def remove_gradle_block(s: str, name: str) -> str:
    """
    Remove a Groovy block by its name (`play { ... }`, `variantFilter { ... }`),
    including the name token. Brace-matched, so nested blocks survive.
    """
    m = re.search(r"\n[ \t]*" + re.escape(name) + r"[ \t]*\{", s)
    if not m:
        return s
    start = m.start() + 1                      # skip the leading newline
    brace = s.index("{", start)
    depth, i = 0, brace
    while i < len(s):
        if s[i] == "{":
            depth += 1
        elif s[i] == "}":
            depth -= 1
            if depth == 0:
                return s[:start] + s[i + 1:]
        i += 1
    return s


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
    ap.add_argument(
        "--diet",
        action="store_true",
        help="also delete telemetry/dead-weight modules (P1 — needs per-module verification)",
    )
    args = ap.parse_args()

    repo = Path(args.repo).resolve()
    global REPO_ROOT
    REPO_ROOT = repo
    if not (repo / "settings.gradle").exists():
        print(f"error: {repo} does not look like the duckduckgo/Android repo", file=sys.stderr)
        return 2

    dry = args.dry_run
    print(f"Lumina P0 rebrand — {repo}{' (dry run)' if dry else ''}\n")

    print("1/6 identity")
    rebrand_gradle(repo, dry)

    print("2/6 app name")
    rebrand_strings(repo, dry)

    print("3/6 module diet (dependency-aware)")
    if args.diet:
        apply_diet(repo, dry)
    else:
        print("  = skipped — P0 is rebrand-only. Pass --diet for the P1 module diet.")

    print("4/6 flavours")
    single_flavour(repo, dry)

    print("5/5 node asset stubs")
    node_stubs(repo, dry)

    print("\nnext: ./gradlew help -q   (cheap config check), then:")
    print("      ./gradlew :app:assembleInternalDebug -x lint -x test")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
