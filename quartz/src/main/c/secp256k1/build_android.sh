#!/bin/bash
#
# Cross-compile secp256k1 C library for Android ARM64 and package
# into the benchmark APK's jniLibs directory.
#
# Run from your development machine (macOS or Linux) with Android NDK installed.
#
# Usage:
#   cd quartz/src/main/c/secp256k1
#   ./build_android.sh
#   # Then run the benchmark:
#   cd ../../../../..
#   ./gradlew :benchmark:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.quartz.benchmark.Secp256k1CBenchmark
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../../../../.."
JNILIB_DIR="$PROJECT_ROOT/benchmark/src/main/jniLibs"

# Find NDK
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/$(ls ${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/ 2>/dev/null | sort -V | tail -1)}"
if [ ! -d "$NDK" ]; then
    echo "ERROR: Android NDK not found."
    echo "Set ANDROID_NDK_HOME or install NDK via: sdkmanager 'ndk;27.2.12479018'"
    exit 1
fi

# Find clang for ARM64
HOST_TAG="linux-x86_64"
if [[ "$(uname)" == "Darwin" ]]; then HOST_TAG="darwin-x86_64"; fi
CC="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin/aarch64-linux-android26-clang"
CC_X86="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin/x86_64-linux-android26-clang"

if [ ! -f "$CC" ]; then
    echo "ERROR: ARM64 clang not found at: $CC"
    echo "NDK path: $NDK"
    exit 1
fi

echo "NDK: $NDK"
echo "Output: $JNILIB_DIR"
echo ""

SOURCES="field.c scalar.c point.c schnorr.c sha256.c jni_bridge.c"

# Build ARM64 shared library
echo "Building arm64-v8a..."
mkdir -p "$JNILIB_DIR/arm64-v8a"
$CC -O3 -march=armv8-a+crypto -fomit-frame-pointer \
    -shared -fPIC \
    -I"$SCRIPT_DIR" \
    $(cd "$SCRIPT_DIR" && echo $SOURCES) \
    -o "$JNILIB_DIR/arm64-v8a/libsecp256k1_amethyst_jni.so" \
    -lm
echo "  → $(wc -c < "$JNILIB_DIR/arm64-v8a/libsecp256k1_amethyst_jni.so") bytes"

# Build x86_64 shared library (for emulator)
if [ -f "$CC_X86" ]; then
    echo "Building x86_64..."
    mkdir -p "$JNILIB_DIR/x86_64"
    $CC_X86 -O3 -march=x86-64-v2 -mbmi2 -msha -msse4.1 -fomit-frame-pointer \
        -shared -fPIC \
        -I"$SCRIPT_DIR" \
        $(cd "$SCRIPT_DIR" && echo $SOURCES) \
        -o "$JNILIB_DIR/x86_64/libsecp256k1_amethyst_jni.so" \
        -lm
    echo "  → $(wc -c < "$JNILIB_DIR/x86_64/libsecp256k1_amethyst_jni.so") bytes"
fi

echo ""
echo "Done! Native libraries placed in:"
echo "  $JNILIB_DIR/"
echo ""
echo "Now run the benchmark:"
echo "  ./gradlew :benchmark:connectedAndroidTest \\"
echo "    -Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.quartz.benchmark.Secp256k1CBenchmark"
