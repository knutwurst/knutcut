#!/usr/bin/env bash
#
# Knutcut release: run tests, build the release APK, and publish it to the self-update repo
# (knutwurst/knutcut-releases). The APK is uploaded as a GitHub release asset (the primary host,
# referenced by latest.json's "apkUrl") and, during the transition, also committed to the repo so
# installs that predate apkUrl keep updating via the raw URL. The app then offers the update OTA.
#
# Before running: bump versionCode + versionName in android/app/build.gradle.kts and (optionally)
# add a CHANGELOG.md entry. Commit/push the code repo yourself — this script only publishes the APK.
#
# Usage:
#   ./release.sh "Optional release notes shown in the update dialog"
#
set -euo pipefail

NOTES="${1:-}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID="$ROOT/android"
DIST="$ROOT/dist"
RELEASES_REPO="https://github.com/knutwurst/knutcut-releases.git"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"

GRADLE="$ANDROID/app/build.gradle.kts"
VERSION_NAME="$(grep -E 'versionName *= *"' "$GRADLE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
VERSION_CODE="$(grep -E 'versionCode *= *[0-9]+' "$GRADLE" | head -1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')"
APK_NAME="Knutcut-v${VERSION_NAME}-release.apk"
APK="$DIST/$APK_NAME"

echo "==> Knutcut v$VERSION_NAME (versionCode $VERSION_CODE)"

# Warn (don't block) if the code repo has uncommitted changes — the published binary should match
# what's committed.
if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then
    echo "    warning: code repo has uncommitted changes — commit + push them too."
fi

echo "==> Unit tests"
( cd "$ANDROID" && ./gradlew :svgcore:test :app:testReleaseUnitTest )

echo "==> Build release APK"
( cd "$ANDROID" && ./gradlew :app:assembleRelease )

[ -f "$APK" ] || { echo "error: $APK not found"; exit 1; }

echo "==> Sign APK with rotation lineage (debug -> release)"
"$ROOT/scripts/sign_release.sh" "$APK"

SHA="$(shasum -a 256 "$APK" | cut -d' ' -f1)"
echo "==> sha256 $SHA"

REPO_SLUG="knutwurst/knutcut-releases"
TAG="v${VERSION_NAME}"
ASSET_URL="https://github.com/${REPO_SLUG}/releases/download/${TAG}/${APK_NAME}"

# Primary host: a GitHub release asset (doesn't bloat the repo, proper download CDN). The app prefers
# this URL via latest.json's "apkUrl".
echo "==> Publish APK as a GitHub release asset ($TAG)"
if gh release view "$TAG" --repo "$REPO_SLUG" >/dev/null 2>&1; then
    gh release upload "$TAG" "$APK" --repo "$REPO_SLUG" --clobber
else
    gh release create "$TAG" "$APK" --repo "$REPO_SLUG" --title "$TAG" --notes "${NOTES:-$TAG}"
fi

# Transition dual-hosting: also keep the APK in the repo (raw) so installs that predate "apkUrl" can
# still update via BASE + apk. Retire this (stop committing the APK, delete old ones) once enough
# users run an apkUrl-aware build — latest.json keeps pointing at the release asset.
echo "==> Update latest.json (+ raw dual-host) in $REPO_SLUG"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git clone -q "$RELEASES_REPO" "$TMP/rel"
cp "$APK" "$TMP/rel/"

python3 - "$TMP/rel/latest.json" "$VERSION_CODE" "$VERSION_NAME" "$APK_NAME" "$SHA" "$NOTES" "$ASSET_URL" <<'PY'
import json, sys
path, code, name, apk, sha, notes, apk_url = sys.argv[1:8]
data = {"versionCode": int(code), "versionName": name, "apk": apk, "sha256": sha, "notes": notes, "apkUrl": apk_url}
with open(path, "w", encoding="utf-8") as f:
    f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
PY

( cd "$TMP/rel" \
    && git config user.name "Knutwurst" \
    && git config user.email "36196269+knutwurst@users.noreply.github.com" \
    && git add -A && git commit -q -m "Release $VERSION_NAME" && git push -q )

echo "==> Published $APK_NAME to knutcut-releases: release asset $TAG + raw dual-host + latest.json."
echo "    Devices on an older version will be offered v$VERSION_NAME."
