#!/usr/bin/env bash
#
# Run all secp256k1 benchmarks.
# Run from the amethyst root directory:
#
#   ./quartz/benchmarks/run_all.sh
#
# Runs: C native, Kotlin/Native, JVM (always), and Android (if device connected).
#
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BENCH_DIR="$ROOT/quartz/benchmarks"

# ============================================================================
# Platform detection
# ============================================================================
OS_NAME="$(uname -s)"
ARCH="$(uname -m)"

case "$OS_NAME" in
    Linux)
        SO_NAME="libsecp256k1-jni.so"
        JAR_PLATFORM="linux"
        case "$ARCH" in
            aarch64|arm64) SO_PATH_PATTERN="*linux-aarch64*" ;;
            *)             SO_PATH_PATTERN="*linux-x86_64*" ;;
        esac
        ;;
    Darwin)
        SO_NAME="libsecp256k1-jni.dylib"
        JAR_PLATFORM="darwin"
        SO_PATH_PATTERN="*darwin*"
        ;;
    *)
        echo "Unsupported OS: $OS_NAME"
        exit 1
        ;;
esac

# ============================================================================
# 1. C native benchmark
# ============================================================================
echo "=== Building C native benchmark ==="

SO_PATH=$(find "$HOME/.gradle" -path "*/secp256k1-kmp-jni-jvm-${JAR_PLATFORM}*" -name "*.jar" 2>/dev/null | head -1)
if [ -z "$SO_PATH" ]; then
    echo "ACINQ secp256k1-kmp-jni-jvm-${JAR_PLATFORM} JAR not found in Gradle cache."
    echo "Run './gradlew :quartz:jvmTest --tests *.Secp256k1Test' first to download it."
    exit 1
fi

WORK=$(mktemp -d)

(cd "$WORK" && jar xf "$SO_PATH") 2>/dev/null || true
SO_FILE=$(find "$WORK" -name "$SO_NAME" -path "$SO_PATH_PATTERN" 2>/dev/null | head -1)
if [ -z "$SO_FILE" ]; then
    echo "Could not find $SO_NAME for $OS_NAME-$ARCH in JAR."
    exit 1
fi
cp "$SO_FILE" "$WORK/"

if ! gcc -O2 -o "$WORK/bench" "$BENCH_DIR/secp256k1_native_bench.c" \
    -L"$WORK" -lsecp256k1-jni -Wl,-rpath,"$WORK" 2>&1; then
    echo "gcc compilation failed"
fi

echo "=== Running C native benchmark ==="
if [ -f "$WORK/bench" ]; then
    "$WORK/bench" 2>&1
else
    echo "(skipped — compilation failed)"
fi
echo ""

# ============================================================================
# 2. Kotlin/Native benchmark (Linux only — only linuxX64 target exists)
# ============================================================================
if [ "$OS_NAME" = "Linux" ]; then
    echo "=== Running Kotlin/Native benchmark ==="
    "$ROOT/gradlew" -p "$ROOT" :quartz:linuxX64Test --tests "*.Secp256k1NativeBenchmark" \
        2>/dev/null || true

    KN_XML="$ROOT/quartz/build/test-results/linuxX64Test/TEST-linuxX64Test.com.vitorpamplona.quartz.utils.secp256k1.Secp256k1NativeBenchmark.xml"
    if [ -f "$KN_XML" ]; then
        sed -n '/<!\[CDATA\[/,/\]\]>/p' "$KN_XML" | grep -v 'CDATA\|]]>'
    else
        echo "K/Native benchmark XML not found."
    fi
else
    echo "=== Skipping Kotlin/Native benchmark (only linuxX64 target available) ==="
fi
echo ""

# ============================================================================
# 3. JVM benchmark
# ============================================================================
echo "=== Running JVM benchmark ==="
"$ROOT/gradlew" -p "$ROOT" :quartz:jvmTest --tests "*.Secp256k1Benchmark" \
    2>/dev/null || true

JVM_XML="$ROOT/quartz/build/test-results/jvmTest/TEST-com.vitorpamplona.quartz.utils.secp256k1.Secp256k1Benchmark.xml"
if [ -f "$JVM_XML" ]; then
    sed -n '/<!\[CDATA\[/,/\]\]>/p' "$JVM_XML" | grep -v 'CDATA\|]]>'
else
    echo "JVM benchmark XML not found."
fi
echo ""

# ============================================================================
# 4. Android benchmark (if device/emulator connected)
# ============================================================================
if command -v adb &>/dev/null && adb devices 2>/dev/null | grep -q "device$"; then
    echo "=== Running Android benchmark (device detected) ==="
    "$ROOT/gradlew" -p "$ROOT" :benchmark:connectedBenchmarkAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.quartz.benchmark.Secp256k1Benchmark \
        2>/dev/null || true

    # AndroidX Benchmark writes test results XML
    ANDROID_XML=$(find "$ROOT/benchmark/build" -path "*test-results*" -name "*.xml" \
        -newer "$ROOT/quartz/benchmarks/run_all.sh" 2>/dev/null | head -1)
    if [ -n "$ANDROID_XML" ] && [ -f "$ANDROID_XML" ]; then
        cat "$ANDROID_XML"
    else
        # Try to find results from the sdcard benchmark output
        BENCH_JSON=$(adb shell "ls /sdcard/Download/*benchmark*json 2>/dev/null" 2>/dev/null | head -1 | tr -d '\r')
        if [ -n "$BENCH_JSON" ]; then
            adb shell "cat '$BENCH_JSON'" 2>/dev/null
        else
            echo "Android benchmark ran but results not found."
        fi
    fi
    echo ""
else
    echo "=== Skipping Android benchmark (no device/emulator connected) ==="
    echo ""
fi
