#!/usr/bin/env bash
#
# Run all secp256k1 benchmarks and produce a comparison table.
# Run from the amethyst root directory:
#
#   ./quartz/benchmarks/run_all.sh
#
# Runs: C native, Kotlin/Native, JVM (always), and Android (if device connected).
#
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BENCH_DIR="$ROOT/quartz/benchmarks"
SO_NAME="libsecp256k1-jni.so"

# ============================================================================
# 1. C native benchmark
# ============================================================================
echo "=== Building C native benchmark ==="

SO_PATH=$(find "$HOME/.gradle" -path "*/secp256k1-kmp-jni-jvm-linux*" -name "*.jar" 2>/dev/null | head -1)
if [ -z "$SO_PATH" ]; then
    echo "ACINQ secp256k1-kmp-jni-jvm-linux JAR not found in Gradle cache."
    echo "Run './gradlew :quartz:jvmTest --tests *.Secp256k1Test' first to download it."
    exit 1
fi

WORK=$(mktemp -d)
trap "rm -rf $WORK" EXIT

(cd "$WORK" && jar xf "$SO_PATH") 2>/dev/null || true
SO_FILE=$(find "$WORK" -name "$SO_NAME" -path "*linux-x86_64*" 2>/dev/null | head -1)
if [ -z "$SO_FILE" ]; then
    echo "Could not find $SO_NAME for linux-x86_64 in JAR."
    exit 1
fi
cp "$SO_FILE" "$WORK/"

if ! gcc -O2 -o "$WORK/bench" "$BENCH_DIR/secp256k1_native_bench.c" \
    -L"$WORK" -lsecp256k1-jni -Wl,-rpath,"$WORK" 2>&1; then
    echo "gcc compilation failed"
fi

echo "=== Running C native benchmark ==="
C_OUT=""
if [ -f "$WORK/bench" ]; then
    C_OUT=$("$WORK/bench" 2>&1)
else
    echo "(skipped — compilation failed)"
fi
echo "$C_OUT"
echo ""

# ============================================================================
# 2. Kotlin/Native benchmark
# ============================================================================
echo "=== Running Kotlin/Native benchmark ==="
"$ROOT/gradlew" -p "$ROOT" :quartz:linuxX64Test --tests "*.Secp256k1NativeBenchmark" \
    2>/dev/null || true

KN_XML="$ROOT/quartz/build/test-results/linuxX64Test/TEST-linuxX64Test.com.vitorpamplona.quartz.utils.secp256k1.Secp256k1NativeBenchmark.xml"
KN_OUT=""
if [ -f "$KN_XML" ]; then
    KN_OUT=$(sed -n '/<!\[CDATA\[/,/\]\]>/p' "$KN_XML" | grep -v 'CDATA\|]]>')
    echo "$KN_OUT"
else
    echo "K/Native benchmark XML not found."
fi
echo ""

# ============================================================================
# 3. JVM benchmark
# ============================================================================
echo "=== Running JVM benchmark ==="
"$ROOT/gradlew" -p "$ROOT" :quartz:jvmTest --tests "*.Secp256k1Benchmark" \
    2>/dev/null || true

JVM_XML="$ROOT/quartz/build/test-results/jvmTest/TEST-com.vitorpamplona.quartz.utils.secp256k1.Secp256k1Benchmark.xml"
JVM_OUT=""
if [ -f "$JVM_XML" ]; then
    JVM_OUT=$(sed -n '/<!\[CDATA\[/,/\]\]>/p' "$JVM_XML" | grep -v 'CDATA\|]]>')
    echo "$JVM_OUT"
else
    echo "JVM benchmark XML not found."
fi
echo ""

# ============================================================================
# 4. Android benchmark (if device/emulator connected)
# ============================================================================
ANDROID_OUT=""
HAS_ANDROID=""
if command -v adb &>/dev/null && adb devices 2>/dev/null | grep -q "device$"; then
    HAS_ANDROID="1"
    echo "=== Running Android benchmark (device detected) ==="
    "$ROOT/gradlew" -p "$ROOT" :benchmark:connectedBenchmarkAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.quartz.benchmark.Secp256k1Benchmark \
        2>/dev/null || true

    # AndroidX Benchmark writes test results XML
    ANDROID_XML=$(find "$ROOT/benchmark/build" -path "*test-results*" -name "*.xml" \
        -newer "$ROOT/quartz/benchmarks/run_all.sh" 2>/dev/null | head -1)
    if [ -n "$ANDROID_XML" ] && [ -f "$ANDROID_XML" ]; then
        ANDROID_OUT=$(cat "$ANDROID_XML")
        echo "Android benchmark results found."
    else
        # Try to find results from the sdcard benchmark output
        BENCH_JSON=$(adb shell "ls /sdcard/Download/*benchmark*json 2>/dev/null" 2>/dev/null | head -1 | tr -d '\r')
        if [ -n "$BENCH_JSON" ]; then
            ANDROID_OUT=$(adb shell "cat '$BENCH_JSON'" 2>/dev/null)
            echo "Android benchmark JSON found: $BENCH_JSON"
        else
            echo "Android benchmark ran but results not found."
        fi
    fi
    echo ""
else
    echo "=== Skipping Android benchmark (no device/emulator connected) ==="
    echo ""
fi

# ============================================================================
# 5. Build comparison table
# ============================================================================

# Parse functions
parse_c() {
    local op="$1"
    [ -z "$op" ] && return
    echo "$C_OUT" | grep -E "$op" | head -1 | awk '{print $(NF-1)}' | tr -d ','
}

parse_kn() {
    local op="$1"
    [ -z "$op" ] && return
    echo "$KN_OUT" | grep -E "$op" | head -1 | awk '{print $(NF-1)}' | tr -d ','
}

parse_jvm_jni() {
    local op="$1"
    [ -z "$op" ] && return
    echo "$JVM_OUT" | grep -E "$op" | head -1 | \
        sed 's/.*Native:[[:space:]]*//' | awk '{print $1}' | tr -d ','
}

parse_jvm_kotlin() {
    local op="$1"
    [ -z "$op" ] && return
    echo "$JVM_OUT" | grep -E "$op" | head -1 | \
        sed 's/.*Kotlin:[[:space:]]*//' | awk '{print $1}' | tr -d ','
}

# Android benchmark: parse ns from XML test results
# Format: <testcase name="verifySchnorrOurs" ...>
# AndroidX Benchmark puts the median ns in the test output
parse_android() {
    local op="$1"
    [ -z "$op" ] || [ -z "$ANDROID_OUT" ] && return
    # Try to extract ns/op from the benchmark output and convert to ops/sec
    local ns
    ns=$(echo "$ANDROID_OUT" | grep -i "${op}" | grep -oP '[\d,]+(?=\s*ns)' | head -1 | tr -d ',')
    if [ -n "$ns" ] && [ "$ns" != "0" ]; then
        awk "BEGIN { printf \"%d\", 1000000000 / $ns }"
    fi
}

fmt() {
    local v="$1"
    if [ -z "$v" ] || [ "$v" = "--" ]; then
        echo "--"
    else
        echo "$v" | sed ':a;s/\B[0-9]\{3\}\>/,&/;ta'
    fi
}

ratio() {
    local a="$1" b="$2"
    if [ -n "$a" ] && [ -n "$b" ] && [ "$b" != "0" ] && [ "$a" != "0" ]; then
        awk "BEGIN { printf \"%.1fx\", $a / $b }"
    else
        echo "—"
    fi
}

# Operation definitions: label|c_pattern|kn_pattern|jvm_pattern|android_pattern|quartz_only
# quartz_only=1: no C/JNI equivalent, blank those columns
OPS=(
    "verifySchnorr|verifySchnorr[[:space:]]|verifySchnorr[[:space:]]|verifySchnorr[[:space:]]|verifySchnorrOurs|"
    "verifySchnorrFast||verifySchnorrFast|verifySchnorrFast|verifySchnorrFastOurs|1"
    "signSchnorr|signSchnorr[[:space:]]|signSchnorr[[:space:]]|signSchnorr[[:space:]]|signSchnorrOurs|"
    "signSchnorr (cached)||signSchnorr .cached|signSchnorr .cached|signSchnorrCachedPkOurs|1"
    "compressedPubKeyFor|compressedPubKeyFor|compressedPubKeyFor|compressedPubKeyFor|compressedPubKeyForOurs|"
    "secKeyVerify|secKeyVerify|secKeyVerify|secKeyVerify|secKeyVerifyOurs|"
    "privKeyTweakAdd|privKeyTweakAdd|privKeyTweakAdd|privKeyTweakAdd|privateKeyAddOurs|"
    "ecdh/tweakMul|ecPubKeyTweakMul|ecdhXOnly|ecdhXOnly|ecdhXOnlyOurs|"
)

# Determine column count based on Android availability
if [ -n "$HAS_ANDROID" ]; then
    COL_FMT="%-24s %14s %14s %14s %14s %14s\n"
    SEP_FMT="%-24s %14s %14s %14s %14s %14s\n"
else
    COL_FMT="%-24s %14s %14s %14s %14s\n"
    SEP_FMT="%-24s %14s %14s %14s %14s\n"
fi

echo ""
echo "=============================================================================================="
echo "COMPARISON TABLE — ops/sec (higher is better)"
echo "=============================================================================================="
echo ""

if [ -n "$HAS_ANDROID" ]; then
    printf "$COL_FMT" "" "libsecp256k1" "libsecp256k1" "Quartz" "Quartz" "Quartz"
    printf "$COL_FMT" "Operation" "C (no JVM)" "JVM (C+JNI)" "JVM Kotlin" "K/Native" "Android"
    printf "$SEP_FMT" "——————————————————————————" "——————————————" "——————————————" "——————————————" "——————————————" "——————————————"
else
    printf "$COL_FMT" "" "libsecp256k1" "libsecp256k1" "Quartz" "Quartz"
    printf "$COL_FMT" "Operation" "C (no JVM)" "JVM (C+JNI)" "JVM Kotlin" "K/Native"
    printf "$SEP_FMT" "——————————————————————————" "——————————————" "——————————————" "——————————————" "——————————————"
fi

for entry in "${OPS[@]}"; do
    IFS='|' read -r label c_pat kn_pat jvm_pat android_pat qonly <<< "$entry"

    c_ops=$(parse_c "$c_pat")
    kn_ops=$(parse_kn "$kn_pat")
    jvm_jni_ops=$(parse_jvm_jni "$jvm_pat")
    jvm_k_ops=$(parse_jvm_kotlin "$jvm_pat")
    android_ops=$(parse_android "$android_pat")

    # Quartz-only: no C/JNI equivalent
    [ "$qonly" = "1" ] && c_ops="" && jvm_jni_ops=""

    if [ -n "$HAS_ANDROID" ]; then
        printf "$COL_FMT" \
            "$label" \
            "$(fmt "${c_ops:---}")" \
            "$(fmt "${jvm_jni_ops:---}")" \
            "$(fmt "${jvm_k_ops:---}")" \
            "$(fmt "${kn_ops:---}")" \
            "$(fmt "${android_ops:---}")"
    else
        printf "$COL_FMT" \
            "$label" \
            "$(fmt "${c_ops:---}")" \
            "$(fmt "${jvm_jni_ops:---}")" \
            "$(fmt "${jvm_k_ops:---}")" \
            "$(fmt "${kn_ops:---}")"
    fi
done

echo ""
echo "——————————————————————————————————————————————————————————————————————————————————————————————"
echo ""
echo "Ratios — lower is closer to native (1.0x = parity):"
echo ""

if [ -n "$HAS_ANDROID" ]; then
    printf "%-24s %14s %14s %14s\n" \
        "" "C (no JVM) vs" "JVM (C+JNI) vs" "libsecp C+JNI vs"
    printf "%-24s %14s %14s %14s\n" \
        "Operation" "K/Native" "JVM Kotlin" "Android"
    printf "%-24s %14s %14s %14s\n" \
        "——————————————————————————" "——————————————" "——————————————" "——————————————"
else
    printf "%-24s %14s %14s\n" \
        "" "C (no JVM) vs" "JVM (C+JNI) vs"
    printf "%-24s %14s %14s\n" \
        "Operation" "K/Native" "JVM Kotlin"
    printf "%-24s %14s %14s\n" \
        "——————————————————————————" "——————————————" "——————————————"
fi

for entry in "${OPS[@]}"; do
    IFS='|' read -r label c_pat kn_pat jvm_pat android_pat qonly <<< "$entry"

    # Skip Quartz-only in ratio table
    [ "$qonly" = "1" ] && continue

    c_ops=$(parse_c "$c_pat")
    kn_ops=$(parse_kn "$kn_pat")
    jvm_jni_ops=$(parse_jvm_jni "$jvm_pat")
    jvm_k_ops=$(parse_jvm_kotlin "$jvm_pat")
    android_ops=$(parse_android "$android_pat")

    r_c_kn=$(ratio "${c_ops:-0}" "${kn_ops:-0}")
    r_jni_jvm=$(ratio "${jvm_jni_ops:-0}" "${jvm_k_ops:-0}")

    if [ -n "$HAS_ANDROID" ]; then
        # For Android ratio, compare native C JNI benchmark on same device
        # We don't have a separate C-on-Android number, so use the JNI native from JVM
        # as an approximation. Better: parse the native Android benchmark results.
        r_android=$(ratio "${jvm_jni_ops:-0}" "${android_ops:-0}")
        printf "%-24s %14s %14s %14s\n" "$label" "$r_c_kn" "$r_jni_jvm" "$r_android"
    else
        printf "%-24s %14s %14s\n" "$label" "$r_c_kn" "$r_jni_jvm"
    fi
done

echo ""
echo "=============================================================================================="
