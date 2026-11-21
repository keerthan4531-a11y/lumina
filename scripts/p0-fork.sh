#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Lumina P0 — fork duckduckgo/Android, rebrand it, strip telemetry, config check.
#
#   ./scripts/p0-fork.sh                 # clone develop + rebrand
#   ./scripts/p0-fork.sh v5.240.0        # clone a tag
#   SKIP_NPM=1 ./scripts/p0-fork.sh      # skip `npm ci` (offline)
#
# Requires: git, node 20+, JDK 21, Android SDK 36.
# The clone lands in ./app-fork (gitignored) so the plan/docs repo stays small.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FORK="$ROOT/app-fork"
UPSTREAM="https://github.com/duckduckgo/Android.git"
REF="${1:-develop}"

say() { printf '\n\033[1m▸ %s\033[0m\n' "$*"; }

say "toolchain check"
command -v git >/dev/null || { echo "git missing"; exit 1; }
if ! command -v java >/dev/null; then
  echo "java missing — P0 needs JDK 21 (Metro compiler plugin)"; exit 1
fi
java -version 2>&1 | head -1
node -v 2>/dev/null || echo "node missing (npm assets will be stubbed)"

say "clone $REF → app-fork"
if [ -d "$FORK/.git" ]; then
  echo "already cloned, reusing"
else
  git clone --depth 1 --branch "$REF" "$UPSTREAM" "$FORK"
fi

cd "$FORK"
git switch -c lumina 2>/dev/null || git switch lumina

say "npm assets (content-scope-scripts, privacy dashboard)"
if [ "${SKIP_NPM:-0}" = "1" ]; then
  echo "skipped"
else
  npm ci || echo "npm ci failed — continuing, the rebrand script stubs missing asset dirs"
fi

say "rebrand + telemetry strip"
python3 "$ROOT/scripts/p0_rebrand.py" --repo "$FORK"

say "gradle configuration check (cheap — no compile)"
./gradlew help -q || {
  echo
  echo "configuration failed. Usual causes:"
  echo "  · a module you deleted is still referenced → grep -rn \"project(':\" --include=build.gradle"
  echo "  · missing node assets → rerun this script with SKIP_NPM=0"
  echo "  · DuckSans / GitHub Packages 401 → unset GITHUB_TOKEN or use the font stub path"
  exit 1
}

cat <<'EOF'

──────────────────────────────────────────────────────────────
P0 done. Next:

  cd app-fork
  ./gradlew :app:assembleInternalDebug -x lint -x test --build-cache --parallel

First build is 10–25 min (cold Gradle + AGP + Metro). Then:

  ./gradlew :app:installInternalDebug -x lint -x test
  adb shell am start -n app.lumina.browser/com.duckduckgo.app.browser.BrowserActivity

Verify the strip (should print nothing):

  strings app/build/outputs/apk/internal/debug/*.apk | grep -iE "duckduckgo.*pixel|amplitude|sentry"
  adb shell dumpsys netstats detail | grep -iE "pixel|amplitude|sentry"

Next phase → docs/00-MASTER-PLAN.md §P2 (glass design system)
──────────────────────────────────────────────────────────────
EOF
