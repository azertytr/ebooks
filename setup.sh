#!/bin/bash
# setup.sh — Build & download EbookReader APK (one-shot, no questions)
#
# Usage:
#   ./setup.sh              # Build v1.0.0
#   ./setup.sh 1.2.3        # Build v1.2.3
#   ./setup.sh 1.2.3 42     # Build v1.2.3, code=42
#

set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────────────────────

VERSION_NAME="${1:-1.0.0}"
VERSION_CODE="${2:-1}"
OUTPUT_DIR="${HOME}/.ebooks-apk"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Ensure version name starts with 'v'
if [[ ! "$VERSION_NAME" =~ ^v ]]; then
    VERSION_NAME="v${VERSION_NAME}"
fi

# ─────────────────────────────────────────────────────────────────────────────
# Setup
# ─────────────────────────────────────────────────────────────────────────────

mkdir -p "$OUTPUT_DIR"
cd "$SCRIPT_DIR"

# ─────────────────────────────────────────────────────────────────────────────
# Build
# ─────────────────────────────────────────────────────────────────────────────

echo "🐳 Building EbookReader APK…"
docker build \
    --build-arg VERSION_CODE="$VERSION_CODE" \
    --build-arg VERSION_NAME="$VERSION_NAME" \
    -t ebook-reader:latest \
    -f Dockerfile \
    . > /dev/null 2>&1

# ─────────────────────────────────────────────────────────────────────────────
# Extract
# ─────────────────────────────────────────────────────────────────────────────

TEMP_CONTAINER=$(docker run -d ebook-reader:latest sleep 999)
trap "docker rm -f $TEMP_CONTAINER > /dev/null 2>&1" EXIT

docker cp "$TEMP_CONTAINER":/out/. "$OUTPUT_DIR/" 2>/dev/null || true
docker rm -f "$TEMP_CONTAINER" > /dev/null 2>&1

# ─────────────────────────────────────────────────────────────────────────────
# Verify & Report
# ─────────────────────────────────────────────────────────────────────────────

APK_FILE=$(find "$OUTPUT_DIR" -name "*.apk" -type f -print -quit 2>/dev/null || echo "")

if [ -z "$APK_FILE" ]; then
    echo "❌ Build failed — APK not found"
    exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
# Install
# ─────────────────────────────────────────────────────────────────────────────

# Check if device is connected
DEVICES=$(adb devices 2>/dev/null | grep -v "List" | grep "device$" | wc -l || echo "0")

if [ "$DEVICES" -gt 0 ]; then
    echo "📱 Android device detected — installing…"
    adb install -r "$APK_FILE" > /dev/null 2>&1 && \
        echo "✅ Installed on device" || \
        echo "⚠️  Install failed (check device)"
fi

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────

APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
APK_NAME=$(basename "$APK_FILE")

echo ""
echo "✅ Build complete"
echo ""
echo "📦 APK: $APK_NAME ($APK_SIZE)"
echo "📂 Path: $APK_FILE"
echo "💾 Folder: $OUTPUT_DIR"
echo ""
echo "📲 To install manually:"
echo "   adb install \"$APK_FILE\""
echo ""
echo "🔗 Or copy to phone via:"
echo "   file://$APK_FILE"
