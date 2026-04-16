#!/bin/bash
#
# Cross-compile libschnorr256k1 as a static library for Android.
#
# Usage:
#   cd libschnorr256k1
#   ./scripts/build_android.sh [OUTPUT_DIR]
#
# The output directory defaults to ./build/android/
#
# Requires: ANDROID_NDK_HOME or ANDROID_HOME with NDK installed.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
SRC_DIR="$PROJECT_ROOT/src"
OUTPUT_DIR="${1:-$PROJECT_ROOT/build/android}"

# Find NDK
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/$(ls ${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/ 2>/dev/null | sort -V | tail -1)}"
if [ ! -d "$NDK" ]; then
    echo "ERROR: Android NDK not found."
    echo "Set ANDROID_NDK_HOME or install NDK via: sdkmanager 'ndk;27.2.12479018'"
    exit 1
fi

# Find clang
HOST_TAG="linux-x86_64"
if [[ "$(uname)" == "Darwin" ]]; then HOST_TAG="darwin-x86_64"; fi
CC_ARM64="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin/aarch64-linux-android26-clang"
CC_X86="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin/x86_64-linux-android26-clang"

if [ ! -f "$CC_ARM64" ]; then
    echo "ERROR: ARM64 clang not found at: $CC_ARM64"
    echo "NDK path: $NDK"
    exit 1
fi

echo "NDK: $NDK"
echo "Sources: $SRC_DIR"
echo "Output: $OUTPUT_DIR"
echo ""

SOURCES="field.c scalar.c point.c schnorr.c sha256.c"

# Build ARM64 static library
echo "Building arm64-v8a..."
mkdir -p "$OUTPUT_DIR/arm64-v8a"
$CC_ARM64 -O3 -march=armv8-a+crypto -fomit-frame-pointer -fPIC -c \
    -I"$SRC_DIR" -I"$PROJECT_ROOT/include" \
    $(cd "$SRC_DIR" && for f in $SOURCES; do echo "$SRC_DIR/$f"; done) \
cd "$OUTPUT_DIR/arm64-v8a"
ar rcs "$OUTPUT_DIR/arm64-v8a/libschnorr256k1.a" *.o 2>/dev/null || \
    (cd "$OUTPUT_DIR/arm64-v8a" && ar rcs libschnorr256k1.a field.o scalar.o point.o schnorr.o sha256.o)
echo "  → $(wc -c < "$OUTPUT_DIR/arm64-v8a/libschnorr256k1.a") bytes"

# Build x86_64 static library (for emulator)
if [ -f "$CC_X86" ]; then
    echo "Building x86_64..."
    mkdir -p "$OUTPUT_DIR/x86_64"
    $CC_X86 -O3 -march=x86-64-v2 -mbmi2 -msha -msse4.1 -fomit-frame-pointer -fPIC -c \
        -I"$SRC_DIR" -I"$PROJECT_ROOT/include" \
        $(cd "$SRC_DIR" && for f in $SOURCES; do echo "$SRC_DIR/$f"; done) \
    cd "$OUTPUT_DIR/x86_64"
    ar rcs "$OUTPUT_DIR/x86_64/libschnorr256k1.a" field.o scalar.o point.o schnorr.o sha256.o
    echo "  → $(wc -c < "$OUTPUT_DIR/x86_64/libschnorr256k1.a") bytes"
fi

echo ""
echo "Done! Static libraries placed in:"
echo "  $OUTPUT_DIR/"
