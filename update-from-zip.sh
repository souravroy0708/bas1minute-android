#!/usr/bin/env bash
# Update this repo from a new bas1minute-android.zip.
# Copies new and changed files AND deletes files that newer versions removed.
# Keeps .git, your signing key, local.properties and build caches. Needs only unzip and git.
#
#   ./update-from-zip.sh ~/Downloads/bas1minute-android.zip
set -euo pipefail

ZIP="${1:?Usage: ./update-from-zip.sh path/to/bas1minute-android.zip}"
REPO="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q "$ZIP" -d "$TMP"
SRC="$TMP/bas1minute-android"
[ -d "$SRC" ] || { echo "The zip doesn't contain a bas1minute-android folder."; exit 1; }
# Never take Git history or build output from a zip: your repo's own .git must stay untouched.
rm -rf "$SRC/.git" "$SRC/dist" "$SRC/.gradle" "$SRC/app/build"

# Never touch these
keep='^\./(\.git/|\.gradle/|build/|app/build/|\.idea/|dist/|local\.properties$|secrets\.env$)|\.jks$|\.keystore$|\.b64$'

# 1. Delete files that are in the repo but not in the new version
( cd "$REPO" && find . -type f | grep -Ev "$keep" | sort ) > "$TMP/old.txt"
( cd "$SRC" && find . -type f | sort ) > "$TMP/new.txt"
removed=0
while IFS= read -r f; do
  rm -f "$REPO/$f"; echo "removed  ${f#./}"; removed=$((removed + 1))
done < <(comm -23 "$TMP/old.txt" "$TMP/new.txt")

# 2. Copy everything from the new version
cp -a "$SRC/." "$REPO/"
find "$REPO" -type d -empty -not -path "*/.git/*" -delete 2>/dev/null || true

cd "$REPO"
git add -A
echo
echo "Removed $removed old file(s). Changes ready to commit:"
git status --short
echo
echo "Next:  git commit -m \"Update Android app\" && git push"
