#!/usr/bin/env bash
# ONE-TIME setup. Creates your Android signing key and uploads it to GitHub as the 4 repository
# secrets the build needs. No copy-pasting, no GitHub website steps.
#
#   ./scripts/setup-signing.sh
#
# Needs: keytool (JDK), GitHub CLI (gh), base64, openssl.
#   sudo apt install default-jdk-headless gh openssl
#
# The key and its password are saved in ~/b1m-signing (OUTSIDE the repo). BACK THAT FOLDER UP.
set -euo pipefail

cd "$(dirname "$0")/.."

DIR="${B1M_SIGNING_DIR:-$HOME/b1m-signing}"
KEYSTORE="$DIR/bas1minute-release.jks"
SECRETS="$DIR/secrets.env"
ALIAS="bas1minute"
DNAME="${B1M_DNAME:-CN=Bas1Minute, OU=Mobile, O=Bas1Minute, L=Hyderabad, ST=Telangana, C=IN}"

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
fail() { printf '\n\033[31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# ---- 1. Check tools -------------------------------------------------------------------------
for tool in keytool gh base64 openssl; do
  command -v "$tool" >/dev/null 2>&1 || fail "'$tool' is not installed. Try: sudo apt install default-jdk-headless gh openssl"
done

# ---- 2. GitHub login (browser opens only if you are not logged in yet) ----------------------
if ! gh auth status >/dev/null 2>&1; then
  say "Logging in to GitHub (follow the prompts)..."
  gh auth login --hostname github.com --git-protocol https --web --scopes repo,workflow
fi
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null)" \
  || fail "Run this inside your bas1minute-android git repo (it needs the GitHub remote)."
say "Repository: $REPO"

# ---- 3. Create the key (or reuse the one from an earlier run) -------------------------------
mkdir -p "$DIR"
chmod 700 "$DIR"

if [ -f "$KEYSTORE" ] && [ -f "$SECRETS" ]; then
  say "Reusing existing key in $DIR"
  # shellcheck disable=SC1090
  . "$SECRETS"
elif [ -f "$KEYSTORE" ]; then
  fail "$KEYSTORE exists but $SECRETS is missing, so the password is unknown. Restore secrets.env or move the old key away."
else
  say "Creating a new signing key..."
  STORE_PASS="$(openssl rand -base64 24 | tr -d '/+=\n' | cut -c1-24)"
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
    -dname "$DNAME" >/dev/null
  umask 077
  {
    echo "STORE_PASS='$STORE_PASS'"
    echo "KEY_ALIAS='$ALIAS'"
  } > "$SECRETS"
  chmod 600 "$SECRETS" "$KEYSTORE"
fi

# ---- 4. Upload the 4 secrets to GitHub ------------------------------------------------------
say "Uploading the 4 secrets to $REPO ..."
base64 < "$KEYSTORE" | tr -d '\n' | gh secret set B1M_KEYSTORE_BASE64   --repo "$REPO"
printf '%s' "$STORE_PASS"          | gh secret set B1M_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "${KEY_ALIAS:-$ALIAS}" | gh secret set B1M_KEY_ALIAS         --repo "$REPO"
printf '%s' "$STORE_PASS"          | gh secret set B1M_KEY_PASSWORD      --repo "$REPO"

say "Done. Key fingerprint (SHA-256), for your records:"
keytool -list -keystore "$KEYSTORE" -storepass "$STORE_PASS" 2>/dev/null | grep -i "fingerprint" || true

cat <<MSG

 Your key and password are in:  $DIR
 >>> BACK UP THAT FOLDER (password manager or private cloud). Never commit it. <<<

 Next:  ./scripts/get-play-bundle.sh      (builds on GitHub and downloads the .aab for you)
MSG
