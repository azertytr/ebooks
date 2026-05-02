#!/usr/bin/env bash
# setup.sh — prepare the EbookReader dev environment
# Safe to run multiple times.
set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

ok()   { echo -e "${GREEN}✔${RESET}  $*"; }
info() { echo -e "${CYAN}→${RESET}  $*"; }
warn() { echo -e "${YELLOW}⚠${RESET}  $*"; }
die()  { echo -e "${RED}✖${RESET}  $*" >&2; exit 1; }

echo -e "\n${BOLD}EbookReader — setup${RESET}\n"

# ── 1. Java 17+ ──────────────────────────────────────────────────────────────
info "Checking Java..."
if ! command -v java &>/dev/null; then
  die "Java not found. Install JDK 17: https://adoptium.net"
fi

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/{print $2}' | cut -d. -f1)
if [[ "$JAVA_VER" -lt 17 ]]; then
  die "Java $JAVA_VER detected — JDK 17+ is required."
fi
ok "Java $JAVA_VER"

# ── 2. Android SDK ───────────────────────────────────────────────────────────
info "Locating Android SDK..."

SDK_CANDIDATES=(
  "${ANDROID_HOME:-}"
  "${ANDROID_SDK_ROOT:-}"
  "$HOME/Android/Sdk"               # Linux (Android Studio default)
  "$HOME/Library/Android/sdk"       # macOS
  "/usr/local/lib/android/sdk"      # GitHub Actions ubuntu-latest
)

ANDROID_SDK=""
for candidate in "${SDK_CANDIDATES[@]}"; do
  if [[ -n "$candidate" && -d "$candidate/platforms" ]]; then
    ANDROID_SDK="$candidate"
    break
  fi
done

if [[ -z "$ANDROID_SDK" ]]; then
  die "Android SDK not found. Install via Android Studio or set ANDROID_HOME."
fi
ok "Android SDK at $ANDROID_SDK"

# ── 3. local.properties ──────────────────────────────────────────────────────
if [[ ! -f local.properties ]]; then
  info "Creating local.properties..."
  echo "sdk.dir=$ANDROID_SDK" > local.properties
  ok "local.properties created"
else
  # Ensure sdk.dir is set (update if pointing somewhere wrong)
  if ! grep -q "^sdk.dir=" local.properties; then
    echo "sdk.dir=$ANDROID_SDK" >> local.properties
    ok "sdk.dir added to existing local.properties"
  else
    ok "local.properties already present"
  fi
fi

# ── 4. Gradle wrapper ────────────────────────────────────────────────────────
info "Checking Gradle wrapper..."

if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  die "gradle/wrapper/gradle-wrapper.jar is missing. Run: gradle wrapper --gradle-version 8.7"
fi
ok "gradle-wrapper.jar present"

chmod +x gradlew
ok "gradlew is executable"

# ── 5. SDK platform 34 ───────────────────────────────────────────────────────
info "Checking Android platform 34..."
PLATFORM_DIR="$ANDROID_SDK/platforms/android-34"
if [[ ! -d "$PLATFORM_DIR" ]]; then
  warn "Platform 34 not found at $PLATFORM_DIR"
  warn "Install it via Android Studio SDK Manager or:"
  warn "  \$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager \"platforms;android-34\""
else
  ok "Platform 34 found"
fi

# ── 6. Build (optional — skip with NO_BUILD=1) ───────────────────────────────
if [[ "${NO_BUILD:-0}" != "1" ]]; then
  echo ""
  info "Building debug APK (set NO_BUILD=1 to skip)..."
  ./gradlew assembleDebug --no-daemon --quiet
  APK="app/build/outputs/apk/debug/app-debug.apk"
  if [[ -f "$APK" ]]; then
    SIZE=$(du -sh "$APK" | cut -f1)
    ok "Debug APK built — $APK ($SIZE)"
  else
    die "Build succeeded but APK not found at expected path."
  fi
fi

# ── 7. ADB device check (informational) ──────────────────────────────────────
echo ""
if command -v adb &>/dev/null; then
  DEVICES=$(adb devices 2>/dev/null | grep -v "^List" | grep -c "device$" || true)
  if [[ "$DEVICES" -gt 0 ]]; then
    ok "$DEVICES device(s) connected — install with: adb install $APK"
  else
    info "No ADB devices connected. Connect a device or start an emulator."
  fi
else
  info "adb not in PATH — skipping device check."
fi

# ── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}Setup complete.${RESET}"
echo -e "  Build:  ${CYAN}./gradlew assembleDebug${RESET}"
echo -e "  Test:   ${CYAN}./gradlew test${RESET}"
echo -e "  Lint:   ${CYAN}./gradlew lint${RESET}"
echo ""
