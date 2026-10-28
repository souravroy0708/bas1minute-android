#!/usr/bin/env bash
# Builds the app on GitHub Actions and downloads the results into ./dist
#   dist/.../Bas1Minute-v0.3.N.aab   <- upload this to Google Play Console
#   dist/.../Bas1Minute-v0.3.N.apk   <- for installing on your own phone
#
#   ./scripts/get-play-bundle.sh            start a new build, wait for it, download
#   ./scripts/get-play-bundle.sh --latest   skip building, download the latest successful build
#
# Needs: GitHub CLI (gh), logged in (run ./scripts/setup-signing.sh first if you haven't).
set -euo pipefail

cd "$(dirname "$0")/.."

WORKFLOW="build-apk.yml"
OUT="dist"
MODE="${1:-build}"

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
fail() { printf '\n\033[31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# Work out "owner/repo" from the git remote (override with B1M_REPO=owner/repo if needed)
resolve_repo() {
  local url
  REPO="${B1M_REPO:-}"
  if [ -z "$REPO" ]; then
    url="$(git remote get-url origin 2>/dev/null || true)"
    REPO="$(printf '%s' "$url" | sed -E 's#^(https?://([^@/]+@)?github\.com/|git@github\.com:|ssh://git@github\.com/)##; s#\.git$##; s#/$##')"
  fi
  case "$REPO" in
    */*) ;;
    *) fail "Could not find the GitHub repo from 'git remote'. Run:  git remote -v
       Or set it yourself:  B1M_REPO=souravroy0708/bas1minute-android $0" ;;
  esac
}

command -v gh >/dev/null 2>&1 || fail "GitHub CLI 'gh' is not installed. Try: sudo apt install gh"
# ---- GitHub CLI must be recent enough (Ubuntu's apt version is too old) -------------------------
MIN_GH="2.20.0"
GH_VER="$(gh --version | head -1 | awk '{print $3}')"
if [ "$(printf '%s\n' "$MIN_GH" "$GH_VER" | sort -V | head -1)" != "$MIN_GH" ]; then
  fail "GitHub CLI $GH_VER is too old (need $MIN_GH or newer). Run:  ./scripts/install-gh.sh"
fi
gh auth status >/dev/null 2>&1 || fail "Not logged in. Run: gh auth login   (or ./scripts/setup-signing.sh)"
resolve_repo

latest_id() { gh run list --repo "$REPO" --workflow "$WORKFLOW" --limit 1 --json databaseId -q '.[0].databaseId // empty' 2>/dev/null || true; }

if [ "$MODE" = "--latest" ]; then
  RUN_ID="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --status success --limit 1 --json databaseId -q '.[0].databaseId // empty')"
  [ -n "$RUN_ID" ] || fail "No successful build found yet."
  say "Using latest successful build #$RUN_ID"
else
  PREV="$(latest_id)"
  say "Starting a build on GitHub ($REPO, branch main)..."
  gh workflow run "$WORKFLOW" --repo "$REPO" --ref main
  RUN_ID=""
  for _ in $(seq 1 30); do
    sleep 3
    NOW="$(latest_id)"
    if [ -n "$NOW" ] && [ "$NOW" != "$PREV" ]; then RUN_ID="$NOW"; break; fi
  done
  [ -n "$RUN_ID" ] || fail "The build did not start. Check the Actions tab on GitHub."
  say "Waiting for build #$RUN_ID (takes about 2 to 4 minutes)..."
  if ! gh run watch "$RUN_ID" --repo "$REPO" --exit-status >/dev/null; then
    echo "Build failed. Log: https://github.com/$REPO/actions/runs/$RUN_ID" >&2
    echo "Show just the errors:  gh run view $RUN_ID --log-failed | grep -E 'e: |What went wrong|Error:' | head -30" >&2
    exit 1
  fi
fi

say "Downloading build #$RUN_ID into ./$OUT ..."
rm -rf "$OUT/run-$RUN_ID"
gh run download "$RUN_ID" --repo "$REPO" --dir "$OUT/run-$RUN_ID"

echo
find "$OUT/run-$RUN_ID" -type f \( -name '*.aab' -o -name '*.apk' \) -print
echo
if find "$OUT/run-$RUN_ID" -name '*.aab' | grep -q .; then
  echo "Upload the .aab to Play Console: Testing > Closed testing > Create new release."
else
  echo "No .aab in this build. The signing secrets are probably missing: run ./scripts/setup-signing.sh"
fi
