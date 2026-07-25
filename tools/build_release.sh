#!/bin/bash
set -e

# Change directory to the android project root (where this script's directory is located)
cd "$(dirname "$0")/.."

echo "=================================================="
echo "Retrieving release signing secrets from Keychain..."
echo "=================================================="

export CLAWLINK_RELEASE_STORE_FILE=$(security find-generic-password -s clawlink-android-release-v2-store-file -w)
export CLAWLINK_RELEASE_STORE_PASSWORD=$(security find-generic-password -s clawlink-android-release-v2-store-password -w)
export CLAWLINK_RELEASE_KEY_ALIAS=$(security find-generic-password -s clawlink-android-release-v2-key-alias -w)
export CLAWLINK_RELEASE_KEY_PASSWORD=$(security find-generic-password -s clawlink-android-release-v2-key-password -w)

if [ -z "$CLAWLINK_RELEASE_STORE_FILE" ] || [ -z "$CLAWLINK_RELEASE_STORE_PASSWORD" ] || [ -z "$CLAWLINK_RELEASE_KEY_ALIAS" ] || [ -z "$CLAWLINK_RELEASE_KEY_PASSWORD" ]; then
    echo "Error: Failed to retrieve one or more signing secrets from macOS Keychain."
    exit 1
fi

echo "Secrets successfully retrieved."
echo "Keystore Path: $CLAWLINK_RELEASE_STORE_FILE"
echo "Key Alias: $CLAWLINK_RELEASE_KEY_ALIAS"

echo "=================================================="
echo "Starting Gradle Release Build..."
echo "=================================================="

./gradlew clean :app:assembleRelease --console=plain

echo "=================================================="
echo "Gradle Build Completed Successfully!"
echo "=================================================="

APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_SOURCE" ]; then
    echo "Error: Release APK not found at $APK_SOURCE"
    exit 1
fi

# Set destination details
VERSION=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
VERSION=${VERSION:-"1.0.11"}
DATE=$(date +%Y%m%d)
APK_DEST="dist/Claw-Link-${VERSION}-release-v2-${DATE}.apk"

mkdir -p dist
cp "$APK_SOURCE" "$APK_DEST"
echo "APK copied to: $APK_DEST"

# Optionally copy v4 signature idsig file if it exists
IDSIG_SOURCE="app/build/outputs/apk/release/app-release.apk.idsig"
if [ -f "$IDSIG_SOURCE" ]; then
    IDSIG_DEST="${APK_DEST}.idsig"
    cp "$IDSIG_SOURCE" "$IDSIG_DEST"
    echo "Signature file copied to: $IDSIG_DEST"
fi

echo "=================================================="
echo "Android Release Build finished successfully!"
echo "=================================================="
