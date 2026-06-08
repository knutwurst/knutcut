#!/usr/bin/env bash
# Re-sign a release APK with APK Signature Scheme v3 KEY ROTATION (debug -> release).
#
# Why: installs up to and including the last debug-signed build are signed with the Android debug key.
# A rotation lineage (debug -> release) lets the new APK install as an update over those builds on
# Android 9+ (API 28+) without a manual uninstall. On older API the v1/v2 debug key signature still
# matches so they can update too.
#
# The lineage file (android/signing-lineage.bin) is created once and kept local (gitignored alongside
# the keystore). The APK is re-signed in-place: Gradle's v2/v3 release-key signature is replaced by
# a dual-signer block — debug key for v1/v2, release key for v3 with the rotation lineage.
#
# Usage: scripts/sign_release.sh <path-to-release.apk>
set -euo pipefail
cd "$(dirname "$0")/.."

APK="${1:?usage: scripts/sign_release.sh <apk>}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"

ANDROID_SDK="${ANDROID_SDK:-/Users/OKoester/IDEAProjects/cricut-export/tools/android-sdk}"
APKSIGNER="$(ls "$ANDROID_SDK/build-tools/"/*/apksigner 2>/dev/null | sort -V | tail -1)"
[ -x "$APKSIGNER" ] || { echo "error: apksigner not found under $ANDROID_SDK/build-tools/"; exit 1; }

DBG="$HOME/.android/debug.keystore"      # debug key that signed all old builds
REL="android/release.keystore"
LIN="android/signing-lineage.bin"
PROPS="android/keystore.properties"

SP="$(grep '^storePassword=' "$PROPS" | cut -d= -f2-)"
ALIAS="$(grep '^keyAlias=' "$PROPS" | cut -d= -f2-)"

# Create the debug->release lineage on first run. Kept local alongside the keystore.
if [ ! -f "$LIN" ]; then
    echo "    creating signing lineage (debug -> release)..."
    "$APKSIGNER" rotate --out "$LIN" \
        --old-signer --ks "$DBG" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android \
        --new-signer --ks "$REL" --ks-pass "pass:$SP" --ks-key-alias "$ALIAS" --key-pass "pass:$SP"
fi

# Re-sign: debug key covers v1/v2 (old debug-signed installs), release key is the v3 rotated signer.
"$APKSIGNER" sign \
    --ks "$DBG" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android \
    --next-signer --ks "$REL" --ks-pass "pass:$SP" --ks-key-alias "$ALIAS" --key-pass "pass:$SP" \
    --lineage "$LIN" --min-sdk-version 26 \
    "$APK"

"$APKSIGNER" verify "$APK" >/dev/null
echo "    rotation-signed: $(basename "$APK")"
