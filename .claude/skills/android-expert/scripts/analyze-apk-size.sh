#!/bin/bash
#
# APK Size Analysis Script for Amethyst
#
# Usage:
#   ./analyze-apk-size.sh [apk-path]
#
# If no APK path provided, uses latest release build

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default APK path
DEFAULT_APK="amethyst/build/outputs/apk/release/amethyst-release.apk"
APK_PATH="${1:-$DEFAULT_APK}"

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Error: APK not found at $APK_PATH${NC}"
    echo "Build the APK first: ./gradlew :amethyst:assembleRelease"
    exit 1
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Amethyst APK Size Analysis${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo

# APK basic info
echo -e "${GREEN}APK Path:${NC} $APK_PATH"
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
APK_SIZE_BYTES=$(stat -f%z "$APK_PATH" 2>/dev/null || stat -c%s "$APK_PATH" 2>/dev/null)
echo -e "${GREEN}APK Size:${NC} $APK_SIZE ($APK_SIZE_BYTES bytes)"
echo

# Extract APK to temp directory
TEMP_DIR=$(mktemp -d)
echo -e "${YELLOW}Extracting APK to $TEMP_DIR...${NC}"
unzip -q "$APK_PATH" -d "$TEMP_DIR"

# Analyze APK contents
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Top-Level Contents${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
du -sh "$TEMP_DIR"/* | sort -hr

# Analyze DEX files
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  DEX Files (Code)${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
DEX_TOTAL=0
for dex in "$TEMP_DIR"/*.dex; do
    if [ -f "$dex" ]; then
        DEX_NAME=$(basename "$dex")
        DEX_SIZE=$(stat -f%z "$dex" 2>/dev/null || stat -c%s "$dex" 2>/dev/null)
        DEX_SIZE_MB=$(echo "scale=2; $DEX_SIZE / 1024 / 1024" | bc)
        echo -e "  $DEX_NAME: ${GREEN}${DEX_SIZE_MB} MB${NC}"
        DEX_TOTAL=$((DEX_TOTAL + DEX_SIZE))
    fi
done
DEX_TOTAL_MB=$(echo "scale=2; $DEX_TOTAL / 1024 / 1024" | bc)
echo -e "${YELLOW}Total DEX: $DEX_TOTAL_MB MB${NC}"

# Analyze resources
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Resources${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
if [ -d "$TEMP_DIR/res" ]; then
    RES_SIZE=$(du -sh "$TEMP_DIR/res" | cut -f1)
    echo -e "  res/: ${GREEN}$RES_SIZE${NC}"

    # Top resource folders
    echo "  Top resource folders:"
    du -sh "$TEMP_DIR/res"/* | sort -hr | head -10 | sed 's/^/    /'
fi

# Analyze assets
echo
if [ -d "$TEMP_DIR/assets" ]; then
    ASSETS_SIZE=$(du -sh "$TEMP_DIR/assets" | cut -f1)
    echo -e "  assets/: ${GREEN}$ASSETS_SIZE${NC}"

    # Top asset files
    echo "  Top asset files:"
    find "$TEMP_DIR/assets" -type f -exec du -h {} \; | sort -hr | head -10 | sed 's/^/    /'
fi

# Analyze native libraries
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Native Libraries${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
if [ -d "$TEMP_DIR/lib" ]; then
    LIB_SIZE=$(du -sh "$TEMP_DIR/lib" | cut -f1)
    echo -e "  lib/: ${GREEN}$LIB_SIZE${NC}"

    # By architecture
    for arch in "$TEMP_DIR/lib"/*; do
        if [ -d "$arch" ]; then
            ARCH_NAME=$(basename "$arch")
            ARCH_SIZE=$(du -sh "$arch" | cut -f1)
            echo -e "    $ARCH_NAME: ${GREEN}$ARCH_SIZE${NC}"

            # Top libraries in architecture
            find "$arch" -type f -name "*.so" -exec du -h {} \; | sort -hr | head -5 | sed 's/^/      /'
        fi
    done
else
    echo "  No native libraries"
fi

# Analyze Kotlin metadata
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Kotlin Metadata${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
if [ -d "$TEMP_DIR/kotlin" ]; then
    KOTLIN_SIZE=$(du -sh "$TEMP_DIR/kotlin" | cut -f1)
    echo -e "  kotlin/: ${GREEN}$KOTLIN_SIZE${NC}"
fi

# Method count (if dexdump available)
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Method Count${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

# Try to find dexdump
DEXDUMP=$(which dexdump 2>/dev/null || find "$ANDROID_HOME/build-tools" -name dexdump 2>/dev/null | head -1 || echo "")

if [ -n "$DEXDUMP" ] && [ -x "$DEXDUMP" ]; then
    TOTAL_METHODS=0
    for dex in "$TEMP_DIR"/*.dex; do
        if [ -f "$dex" ]; then
            DEX_NAME=$(basename "$dex")
            METHOD_COUNT=$("$DEXDUMP" -l xml "$dex" | grep -c "<method " || echo "0")
            echo -e "  $DEX_NAME: ${GREEN}$METHOD_COUNT methods${NC}"
            TOTAL_METHODS=$((TOTAL_METHODS + METHOD_COUNT))
        fi
    done
    echo -e "${YELLOW}Total methods: $TOTAL_METHODS${NC}"

    # Check multidex threshold
    if [ $TOTAL_METHODS -gt 65536 ]; then
        echo -e "${RED}  ⚠ Exceeded 64K method limit (multidex required)${NC}"
    else
        REMAINING=$((65536 - TOTAL_METHODS))
        echo -e "${GREEN}  ✓ Below 64K limit ($REMAINING methods remaining)${NC}"
    fi
else
    echo -e "${YELLOW}  dexdump not found - cannot analyze method count${NC}"
    echo "  Set ANDROID_HOME or install Android SDK build-tools"
fi

# Size breakdown percentages
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Size Breakdown${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

# Calculate percentages
CODE_SIZE=$(du -s "$TEMP_DIR"/*.dex 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo "0")
RES_SIZE_BYTES=$(du -s "$TEMP_DIR/res" 2>/dev/null | awk '{print $1}' || echo "0")
ASSETS_SIZE_BYTES=$(du -s "$TEMP_DIR/assets" 2>/dev/null | awk '{print $1}' || echo "0")
LIB_SIZE_BYTES=$(du -s "$TEMP_DIR/lib" 2>/dev/null | awk '{print $1}' || echo "0")

# Convert to KB for bc
APK_SIZE_KB=$((APK_SIZE_BYTES / 1024))
CODE_PCT=$(echo "scale=1; $CODE_SIZE * 100 / $APK_SIZE_KB" | bc 2>/dev/null || echo "0")
RES_PCT=$(echo "scale=1; $RES_SIZE_BYTES * 100 / $APK_SIZE_KB" | bc 2>/dev/null || echo "0")
ASSETS_PCT=$(echo "scale=1; $ASSETS_SIZE_BYTES * 100 / $APK_SIZE_KB" | bc 2>/dev/null || echo "0")
LIB_PCT=$(echo "scale=1; $LIB_SIZE_BYTES * 100 / $APK_SIZE_KB" | bc 2>/dev/null || echo "0")

echo -e "  Code (DEX):      ${CODE_PCT}%"
echo -e "  Resources:       ${RES_PCT}%"
echo -e "  Assets:          ${ASSETS_PCT}%"
echo -e "  Native libs:     ${LIB_PCT}%"

# Recommendations
echo
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Optimization Recommendations${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

# Check if resources are large
if [ $(echo "$RES_PCT > 30" | bc 2>/dev/null || echo "0") -eq 1 ]; then
    echo -e "${YELLOW}  • Resources are ${RES_PCT}% of APK - consider:${NC}"
    echo "    - Enable resource shrinking (shrinkResources = true)"
    echo "    - Use WebP for images instead of PNG/JPG"
    echo "    - Remove unused resources"
fi

# Check if native libs are large
if [ $(echo "$LIB_PCT > 40" | bc 2>/dev/null || echo "0") -eq 1 ]; then
    echo -e "${YELLOW}  • Native libraries are ${LIB_PCT}% of APK - consider:${NC}"
    echo "    - Use App Bundle to serve ABI-specific APKs"
    echo "    - Remove unused ABIs"
fi

# Check if code is large
if [ $(echo "$CODE_PCT > 40" | bc 2>/dev/null || echo "0") -eq 1 ]; then
    echo -e "${YELLOW}  • Code is ${CODE_PCT}% of APK - consider:${NC}"
    echo "    - Enable Proguard/R8 (minifyEnabled = true)"
    echo "    - Review dependencies for bloat"
    echo "    - Enable code shrinking"
fi

# General recommendations
echo -e "${GREEN}  General optimizations:${NC}"
echo "    - Use Android App Bundle (.aab) instead of APK"
echo "    - Enable R8 optimization (minifyEnabled = true)"
echo "    - Enable resource shrinking (shrinkResources = true)"
echo "    - Analyze with APK Analyzer in Android Studio"

# Cleanup
echo
echo -e "${YELLOW}Cleaning up temporary files...${NC}"
rm -rf "$TEMP_DIR"

echo
echo -e "${GREEN}✓ Analysis complete${NC}"
