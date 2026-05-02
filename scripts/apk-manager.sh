#!/bin/bash
# APK Manager - Manage stored APK files
#
# Usage:
#   ./scripts/apk-manager.sh list              # List all stored APKs
#   ./scripts/apk-manager.sh clean             # Remove old APKs
#   ./scripts/apk-manager.sh latest            # Show latest APK
#   ./scripts/apk-manager.sh size              # Show total storage used

set -euo pipefail

APK_DIR="./apk"
DAYS_OLD=7

# Color codes
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() { echo -e "${BLUE}ℹ️  $*${NC}"; }
log_success() { echo -e "${GREEN}✅ $*${NC}"; }
log_warning() { echo -e "${YELLOW}⚠️  $*${NC}"; }
log_error() { echo -e "${RED}❌ $*${NC}"; }

# Ensure APK directory exists
mkdir -p "$APK_DIR"

list_apks() {
    log_info "APK Storage ($APK_DIR):"
    echo ""

    if [ ! "$(ls -A "$APK_DIR")" ]; then
        log_warning "No APKs found"
        return 0
    fi

    ls -lh "$APK_DIR"/*.apk 2>/dev/null | awk '{
        printf "   📦 %-40s %8s\n", $9, $5
    }' | sed "s|$PWD/||g"

    echo ""
    return 0
}

latest_apk() {
    local latest
    latest=$(ls -t "$APK_DIR"/*.apk 2>/dev/null | head -1)

    if [ -z "$latest" ]; then
        log_warning "No APK found"
        return 1
    fi

    echo "$latest"
}

show_latest() {
    log_info "Latest APK:"
    echo ""

    if latest=$(latest_apk); then
        local size
        size=$(ls -lh "$latest" | awk '{print $5}')
        local modified
        modified=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" "$latest" 2>/dev/null || stat --format="%y" "$latest" 2>/dev/null | cut -d' ' -f1-2)

        echo "   📦 $(basename "$latest")"
        echo "   📏 Size: $size"
        echo "   🕒 Modified: $modified"
        echo ""
        return 0
    else
        return 1
    fi
}

total_size() {
    local total_bytes=0

    if [ ! "$(ls -A "$APK_DIR" 2>/dev/null)" ]; then
        echo "0"
        return 0
    fi

    while IFS= read -r file; do
        if [ -f "$file" ]; then
            total_bytes=$((total_bytes + $(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null)))
        fi
    done < <(find "$APK_DIR" -name "*.apk" -type f 2>/dev/null)

    echo $total_bytes
}

show_size() {
    local bytes
    bytes=$(total_size)

    log_info "Storage Usage:"
    echo ""
    echo "   Total: $(numfmt --to=iec-i --suffix=B $bytes 2>/dev/null || printf '%d bytes' $bytes)"
    echo ""
    return 0
}

clean_old() {
    log_info "Cleaning APKs older than $DAYS_OLD days..."
    echo ""

    local count=0
    while IFS= read -r file; do
        if [ -f "$file" ]; then
            log_warning "Removing: $(basename "$file")"
            rm -f "$file"
            ((count++)) || true
        fi
    done < <(find "$APK_DIR" -name "*.apk" -type f -mtime +$DAYS_OLD 2>/dev/null)

    if [ $count -eq 0 ]; then
        log_success "No old APKs to clean"
    else
        log_success "Removed $count APK(s)"
    fi

    echo ""
    return 0
}

main() {
    local command="${1:-list}"

    case "$command" in
        list)
            list_apks
            ;;
        latest)
            show_latest
            ;;
        size)
            show_size
            ;;
        clean)
            clean_old
            ;;
        *)
            echo "APK Manager"
            echo ""
            echo "Usage: $0 <command>"
            echo ""
            echo "Commands:"
            echo "  list              List all stored APKs"
            echo "  latest            Show latest APK details"
            echo "  size              Show total storage used"
            echo "  clean             Remove APKs older than $DAYS_OLD days"
            echo ""
            return 1
            ;;
    esac
}

main "$@"
