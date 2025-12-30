#!/bin/bash
# Diagnose and suggest fixes for common dependency conflicts

set -e

PROJECT_ROOT="${1:-.}"
cd "$PROJECT_ROOT"

echo "🔍 Analyzing dependency conflicts..."
echo "===================================="
echo ""

# Run dependency report
echo "Generating dependency insight report..."
./gradlew dependencies --configuration runtimeClasspath > /tmp/gradle-dependencies.txt 2>&1 || true

# Check for common conflict patterns
echo ""
echo "🔎 Checking for common issues:"
echo "------------------------------"

# Check 1: Compose version conflicts
if grep -q "compose" /tmp/gradle-dependencies.txt; then
    echo "✓ Compose dependencies found"
    echo "  Tip: Ensure Compose Multiplatform and AndroidX Compose versions align"
    echo "  Current project uses:"
    echo "  - Compose Multiplatform BOM"
    echo "  - AndroidX Compose BOM"
fi

# Check 2: secp256k1 variants
if grep -q "secp256k1" /tmp/gradle-dependencies.txt; then
    echo "✓ secp256k1 dependencies found"
    echo "  Ensure correct variant:"
    echo "  - Android: secp256k1-kmp-jni-android"
    echo "  - JVM/Desktop: secp256k1-kmp-jni-jvm"
    echo "  - Common: secp256k1-kmp (transitive)"
fi

# Check 3: Kotlin version alignment
KOTLIN_VERSION=$(grep "kotlin =" gradle/libs.versions.toml | cut -d'"' -f2)
echo "✓ Kotlin version: $KOTLIN_VERSION"
echo "  All Kotlin plugins should use the same version"

# Check 4: Multiple versions of same library
echo ""
echo "🔍 Checking for version conflicts..."
./gradlew dependencyInsight --configuration runtimeClasspath --dependency okhttp || true

echo ""
echo "💡 Common fixes:"
echo "---------------"
echo "1. Compose conflicts:"
echo "   - Align compose-multiplatform plugin version with runtime"
echo "   - Use BOM for AndroidX Compose to enforce consistency"
echo ""
echo "2. secp256k1 conflicts:"
echo "   - Use 'api' instead of 'implementation' in source sets"
echo "   - Ensure androidMain uses jni-android, jvmMain uses jni-jvm"
echo ""
echo "3. Kotlin version conflicts:"
echo "   - Update all kotlin plugins to same version in libs.versions.toml"
echo "   - Check for transitive Kotlin dependencies"
echo ""
echo "Run './gradlew dependencyInsight --dependency <name>' for specific conflicts"
