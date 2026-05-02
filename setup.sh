#!/bin/bash
# setup.sh — Build & download EbookReader APK (one-shot, no questions)
#
# Usage:
#   ./setup.sh              # Build v1.0.0 (quiet)
#   ./setup.sh 1.2.3        # Build v1.2.3 (quiet)
#   ./setup.sh 1.2.3 42     # Build v1.2.3, code=42 (quiet)
#   DEBUG=1 ./setup.sh 1.2.3 # Build v1.2.3 (verbose, show all output)
#

set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────────────────────

VERSION_NAME="${1:-1.0.0}"
VERSION_CODE="${2:-1}"
OUTPUT_DIR="${HOME}/.ebooks-apk"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEBUG="${DEBUG:-0}"

# Ensure version name starts with 'v'
if [[ ! "$VERSION_NAME" =~ ^v ]]; then
    VERSION_NAME="v${VERSION_NAME}"
fi

# Helper: log with timestamp
log_info() {
    echo "[$(date '+%H:%M:%S')] ℹ️  $*"
}

log_error() {
    echo "[$(date '+%H:%M:%S')] ❌ $*" >&2
}

log_success() {
    echo "[$(date '+%H:%M:%S')] ✅ $*"
}

# ─────────────────────────────────────────────────────────────────────────────
# Setup
# ─────────────────────────────────────────────────────────────────────────────

log_info "Creating output directory: $OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cd "$SCRIPT_DIR"

if [ "$DEBUG" = "1" ]; then
    log_info "DEBUG mode enabled"
    log_info "Version: $VERSION_NAME"
    log_info "Code: $VERSION_CODE"
    log_info "Script directory: $SCRIPT_DIR"
    log_info "Output directory: $OUTPUT_DIR"
fi

# ─────────────────────────────────────────────────────────────────────────────
# Build
# ─────────────────────────────────────────────────────────────────────────────

echo "🐳 Building EbookReader APK ($VERSION_NAME)…"
log_info "Docker build starting (this may take a few minutes)…"

if ! command -v docker &> /dev/null; then
    log_error "Docker is not installed"
    echo ""
    echo "📝 To use this script, install Docker from: https://www.docker.com/"
    echo ""
    echo "🔨 OR build locally without Docker:"
    echo "   ./gradlew assembleDebug"
    echo "   # APK: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    exit 1
fi

if ! docker ps &> /dev/null; then
    log_error "Docker daemon is not running"
    echo ""
    echo "💡 Start Docker and try again:"
    echo "   # On macOS/Windows: Open Docker Desktop"
    echo "   # On Linux: sudo systemctl start docker"
    echo ""
    echo "🔨 OR build locally without Docker:"
    echo "   ./gradlew assembleDebug"
    echo "   # APK: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    exit 1
fi

if [ "$DEBUG" = "1" ]; then
    # Show full Docker output
    docker build \
        --build-arg VERSION_CODE="$VERSION_CODE" \
        --build-arg VERSION_NAME="$VERSION_NAME" \
        -t ebook-reader:latest \
        -f Dockerfile \
        .
else
    # Quiet mode
    docker build \
        --build-arg VERSION_CODE="$VERSION_CODE" \
        --build-arg VERSION_NAME="$VERSION_NAME" \
        -t ebook-reader:latest \
        -f Dockerfile \
        . > /dev/null 2>&1
fi

log_success "Docker image built"

# ─────────────────────────────────────────────────────────────────────────────
# Extract
# ─────────────────────────────────────────────────────────────────────────────

log_info "Extracting APK from Docker container…"
TEMP_CONTAINER=$(docker run -d ebook-reader:latest sleep 999)
trap "docker rm -f $TEMP_CONTAINER > /dev/null 2>&1" EXIT

if [ "$DEBUG" = "1" ]; then
    log_info "Container ID: $TEMP_CONTAINER"
fi

docker cp "$TEMP_CONTAINER":/out/. "$OUTPUT_DIR/" 2>/dev/null || true
log_success "APK extracted to $OUTPUT_DIR"

log_info "Cleaning up container…"
docker rm -f "$TEMP_CONTAINER" > /dev/null 2>&1

# ─────────────────────────────────────────────────────────────────────────────
# Verify & Report
# ─────────────────────────────────────────────────────────────────────────────

log_info "Verifying APK…"
APK_FILE=$(find "$OUTPUT_DIR" -name "*.apk" -type f -print -quit 2>/dev/null || echo "")

if [ -z "$APK_FILE" ]; then
    log_error "APK not found in $OUTPUT_DIR"
    log_error "Build may have failed. Run with DEBUG=1 for more info:"
    log_error "  DEBUG=1 ./setup.sh $VERSION_NAME"
    echo ""
    echo "Files in $OUTPUT_DIR:"
    ls -lah "$OUTPUT_DIR" 2>/dev/null || echo "  (directory empty)"
    exit 1
fi

log_success "APK found: $(basename "$APK_FILE")"

# ─────────────────────────────────────────────────────────────────────────────
# Install
# ─────────────────────────────────────────────────────────────────────────────

log_info "Checking for connected Android devices…"
DEVICES=$(adb devices 2>/dev/null | grep -v "List" | grep "device$" | wc -l || echo "0")

if [ "$DEVICES" -gt 0 ]; then
    echo "📱 Android device detected ($DEVICES device(s)) — installing…"
    if [ "$DEBUG" = "1" ]; then
        adb devices
    fi

    if adb install -r "$APK_FILE"; then
        log_success "APK installed on device"
    else
        echo "⚠️  Install failed (check device is unlocked and developer mode enabled)"
    fi
else
    log_info "No Android device detected (that's OK, manual install available)"
fi

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────

APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
APK_NAME=$(basename "$APK_FILE")

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ BUILD COMPLETE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📦 APK Name:     $APK_NAME"
echo "📊 Size:         $APK_SIZE"
echo "📂 Full Path:    $APK_FILE"
echo "💾 Folder:       $OUTPUT_DIR"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📲 INSTALL OPTIONS:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "1️⃣  Command line (if device connected):"
echo "    adb install -r \"$APK_FILE\""
echo ""
echo "2️⃣  File manager (copy to phone):"
echo "    $APK_FILE"
echo ""
echo "3️⃣  Open directly on file manager:"
echo "    file://$APK_FILE"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ "$DEBUG" = "1" ]; then
    echo ""
    log_info "Directory contents:"
    ls -lah "$OUTPUT_DIR"
fi
