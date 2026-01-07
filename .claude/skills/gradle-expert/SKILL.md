---
name: gradle-expert
description: Build optimization, dependency resolution, and multi-module KMP troubleshooting for AmethystMultiplatform. Use when working with: (1) Gradle build files (build.gradle.kts, settings.gradle), (2) Version catalog (libs.versions.toml), (3) Build errors and dependency conflicts, (4) Module dependencies and source sets, (5) Desktop packaging (DMG/MSI/DEB), (6) Build performance optimization, (7) Proguard/R8 configuration, (8) Common KMP + Android Gradle issues (Compose conflicts, secp256k1 JNI variants, source set problems).
---

# Gradle Expert

Build system expertise for AmethystMultiplatform's 4-module KMP architecture. Focus: practical troubleshooting, dependency resolution, and project-specific optimizations.

## Build Architecture Mental Model

Think of this project as **4 layers**:

```
┌─────────────┬─────────────┐
│ :amethyst   │ :desktopApp │  ← Platform apps (navigation, layouts)
│ (Android)   │    (JVM)    │
└──────┬──────┴──────┬──────┘
       │             │
       └──────┬──────┘
              ▼
      ┌─────────────┐
      │  :commons   │           ← Shared UI (KMP with jvmAndroid)
      │  (KMP UI)   │
      └──────┬──────┘
             ▼
      ┌─────────────┐
      │  :quartz    │           ← Core library (KMP: Android/JVM/iOS)
      │(KMP Library)│
      └─────────────┘
```

**Key insight:** Dependencies flow DOWN. Lower modules never depend on upper modules. This enables code sharing without circular dependencies.

**The jvmAndroid pattern:** Unique to this project. A custom source set between commonMain and {androidMain, jvmMain} for JVM-specific code shared by Android and Desktop. Not standard KMP, but critical for this architecture.

## Version Catalog Philosophy

All dependencies centralized in `gradle/libs.versions.toml`. Think "single source of truth."

**Pattern:**
```toml
[versions]
kotlin = "2.3.0"

[libraries]
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
```

**Usage:**
```kotlin
dependencies {
    implementation(libs.okhttp)  // Type-safe, IDE-autocompleted
}
```

**Critical alignments:**
- **Kotlin ecosystem:** All Kotlin plugins MUST share same version
- **Compose ecosystem:** Compose Multiplatform version → Kotlin version (check compatibility matrix)
- **secp256k1 variants:** All three variants (common, jni-android, jni-jvm) MUST share same version

See [references/version-catalog-guide.md](references/version-catalog-guide.md) for comprehensive patterns.

## Common Build Tasks

### Quick Reference

```bash
# Full builds
./gradlew build                    # All modules
./gradlew clean build              # Clean build

# Desktop
./gradlew :desktopApp:run          # Run desktop app
./gradlew :desktopApp:packageDmg   # macOS package

# Module-specific
./gradlew :quartz:build            # KMP library only
./gradlew :commons:build           # Shared UI only

# Analysis
./gradlew dependencies             # Dependency tree
./gradlew build --scan             # Online diagnostics
```

See [references/build-commands.md](references/build-commands.md) for comprehensive command reference.

## Module Structure & Dependencies

### Dependency Flow

**Desktop build chain:**
```
:desktopApp → :commons (jvmMain) → :quartz (jvmMain → jvmAndroid → commonMain)
```

**Android build chain:**
```
:amethyst → :commons (androidMain) → :quartz (androidMain → jvmAndroid → commonMain)
```

**Key source set pattern (quartz & commons):**
```
commonMain           # Truly cross-platform code
    │
    ├─ jvmAndroid    # JVM-specific, shared by Android + Desktop
    │   ├─ androidMain
    │   └─ jvmMain
    │
    └─ iosMain       # iOS-specific (quartz only)
```

**Dependency config types:**
- Use `api` when types appear in module's public API or expect/actual declarations
- Use `implementation` for internal implementation details
- Example: quartz exposes secp256k1 (`api`), but hides okhttp (`implementation`)

See [references/dependency-graph.md](references/dependency-graph.md) for module visualization and transitive dependency flow.

## Critical Dependency Patterns

### 1. secp256k1 (Crypto Library)

**The problem:** KMP library with platform-specific JNI bindings. Wrong variant = runtime crash.

**Pattern:**
```kotlin
// commonMain - API only
api(libs.secp256k1.kmp.common)

// androidMain - Android JNI
api(libs.secp256k1.kmp.jni.android)

// jvmMain - Desktop JVM JNI
implementation(libs.secp256k1.kmp.jni.jvm)
```

**Why api in androidMain?** Types leak to consumers (:amethyst).

**Common error:** Desktop using jni-android variant → `UnsatisfiedLinkError: no secp256k1jni in java.library.path`

**Fix:** Check source set dependencies. jvmMain must use jni-jvm, never jni-android.

### 2. JNA (for LibSodium Encryption)

**The problem:** Android needs AAR packaging, JVM needs JAR. Same library, different artifact types.

**Pattern:**
```kotlin
// androidMain
implementation("com.goterl:lazysodium-android:5.2.0@aar")  // @aar explicit
implementation("net.java.dev.jna:jna:5.18.1@aar")

// jvmMain
implementation(libs.lazysodium.java)  // JAR implicit
implementation(libs.jna)
```

**Critical:** Never put JNA in jvmAndroid or commonMain. Platform-specific packaging only.

### 3. Compose Versions

**The problem:** Two Compose ecosystems (Multiplatform + AndroidX) must align, or duplicate classes.

**Current project config:**
```toml
composeMultiplatform = "1.9.3"  # Plugin + runtime
composeBom = "2025.12.01"       # AndroidX Compose BOM
kotlin = "2.3.0"
```

**Rule:** Compose Multiplatform version must be compatible with Kotlin version. Check: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html

**In KMP modules (quartz, commons):**
```kotlin
// ✅ Use Compose Multiplatform
implementation(compose.ui)
implementation(compose.material3)

// ❌ DON'T use AndroidX BOM in KMP modules
// implementation(libs.androidx.compose.bom)
```

**In Android-only modules (amethyst):**
```kotlin
// Can use AndroidX BOM
val composeBom = platform(libs.androidx.compose.bom)
implementation(composeBom)
```

## Desktop Packaging Basics

**TargetFormat options:**
```kotlin
// In desktopApp/build.gradle.kts
nativeDistributions {
    targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

    packageName = "Amethyst"
    packageVersion = "1.0.0"

    macOS {
        bundleID = "com.vitorpamplona.amethyst.desktop"
        iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
    }
}
```

**Package tasks:**
```bash
./gradlew :desktopApp:packageDmg  # macOS
./gradlew :desktopApp:packageMsi  # Windows
./gradlew :desktopApp:packageDeb  # Linux
```

**Output locations:**
- macOS: `desktopApp/build/compose/binaries/main/dmg/`
- Windows: `desktopApp/build/compose/binaries/main/msi/`
- Linux: `desktopApp/build/compose/binaries/main/deb/`

**Icon requirements:**
- macOS: `.icns` (multi-resolution: 512, 256, 128, 32)
- Windows: `.ico` (256, 128, 64, 32, 16)
- Linux: `.png` (512x512)

**Common issues:**
- Main class not found → Verify `mainClass = "...MainKt"` (Kotlin adds `Kt` suffix)
- Native libs missing → Ensure secp256k1-kmp-jni-jvm in dependencies
- Icon not found → Check file exists at path, use absolute path if needed

## Build Performance Optimization

**Add to `gradle.properties`:**
```properties
# Daemon (faster subsequent builds)
org.gradle.daemon=true
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g

# Parallel execution (multi-module speedup)
org.gradle.parallel=true
org.gradle.workers.max=8

# Caching (incremental builds)
org.gradle.caching=true
org.gradle.configuration-cache=true

# Kotlin daemon
kotlin.incremental=true
kotlin.daemon.jvmargs=-Xmx2g
```

**Impact:** Typically 30-50% faster builds after first run.

**Measure impact:**
```bash
./gradlew clean build --profile
# Report: build/reports/profile/profile-<timestamp>.html
```

**When to clean build:**
- After changing version catalog
- After adding/removing source sets
- When seeing unexplained errors

**When NOT to clean:**
- Regular development iteration
- Small code changes
- Incremental compilation works fine

Use script: `scripts/analyze-build-time.sh` for automated profiling.

## Troubleshooting: Practical Patterns

### Pattern 1: Version Conflict

**Symptom:** `Duplicate class` or `NoSuchMethodError`

**Diagnosis:**
```bash
./gradlew dependencyInsight --dependency <library-name>
```

**Fix options:**
1. Align versions in libs.versions.toml (preferred)
2. Force resolution:
```kotlin
configurations.all {
    resolutionStrategy {
        force(libs.okhttp.get().toString())
    }
}
```

### Pattern 2: Source Set Issues

**Symptom:** `Unresolved reference` to JVM library in shared code

**Diagnosis:** Check source set hierarchy. JVM-only libs (jackson, okhttp) can't be in commonMain.

**Fix:** Move to jvmAndroid or platform-specific source set.

```kotlin
// ❌ Wrong
commonMain {
    dependencies {
        implementation(libs.jackson.module.kotlin)  // JVM-only!
    }
}

// ✅ Correct
val jvmAndroid = create("jvmAndroid") {
    dependsOn(commonMain.get())
    dependencies {
        api(libs.jackson.module.kotlin)  // JVM code, shared by Android + Desktop
    }
}
```

### Pattern 3: Proguard Stripping Native Libs

**Symptom:** `NoClassDefFoundError` for secp256k1, JNA, or LibSodium in release builds

**Fix:** Update proguard rules in `quartz/proguard-rules.pro`:
```proguard
# Native libraries
-keep class fr.acinq.secp256k1.** { *; }
-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }

# Jackson (reflection-based)
-keep class com.vitorpamplona.quartz.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
```

### Pattern 4: Compose Compiler Mismatch

**Symptom:** `IllegalStateException: Version mismatch: runtime 1.10.0 but compiler 1.9.0`

**Fix:** Update Compose Multiplatform version in libs.versions.toml to match Kotlin version compatibility.

Check: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html

### Pattern 5: Wrong JVM Target

**Symptom:** `Unsupported class file major version 65`

**Fix:** Ensure Java 21 everywhere:
```bash
# Check current Java
java -version  # Should show 21

# Set JAVA_HOME
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Stop Gradle daemon to pick up new Java
./gradlew --stop
```

Verify all build files use JVM 21:
```kotlin
kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
```

## Comprehensive Error Guide

For detailed troubleshooting of specific errors, see [references/common-errors.md](references/common-errors.md). Covers:
- Compose version conflicts
- secp256k1 JNI errors
- Source set dependency issues
- Proguard/R8 problems
- Desktop packaging errors
- Kotlin compilation errors
- Dependency resolution failures
- JVM/JDK version issues

Each error includes: symptom, cause, solution, verification steps.

## Quick Diagnostic Commands

```bash
# Check dependencies for specific module
./gradlew :quartz:dependencies

# Find specific library in dependency tree
./gradlew dependencyInsight --dependency okhttp

# Build with detailed logging
./gradlew build --info

# Generate interactive build scan (best diagnostics)
./gradlew build --scan

# Profile build performance
./gradlew clean build --profile

# Stop all Gradle daemons (fresh start)
./gradlew --stop

# Check Gradle version
./gradlew --version
```

## Scripts & References

### Diagnostic Scripts
- `scripts/analyze-build-time.sh` - Profile build performance, generate optimization report
- `scripts/fix-dependency-conflicts.sh` - Diagnose common dependency conflicts, suggest fixes

### Reference Docs
- `references/build-commands.md` - Comprehensive command reference for all tasks
- `references/dependency-graph.md` - Module dependencies, source set hierarchy, transitive deps
- `references/version-catalog-guide.md` - Version catalog patterns, usage, best practices
- `references/common-errors.md` - Troubleshooting guide for frequent build issues

## Workflow Examples

### Example 1: Adding New Dependency

**Task:** Add kotlinx.datetime to quartz

**Steps:**
1. **Update version catalog** (gradle/libs.versions.toml):
```toml
[versions]
kotlinxDatetime = "0.6.0"

[libraries]
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
```

2. **Add to build file** (quartz/build.gradle.kts):
```kotlin
sourceSets {
    commonMain {
        dependencies {
            implementation(libs.kotlinx.datetime)  // KMP library, goes in commonMain
        }
    }
}
```

3. **Sync & verify:**
```bash
./gradlew :quartz:dependencies | grep datetime
```

### Example 2: Fixing secp256k1 Error on Desktop

**Error:** `UnsatisfiedLinkError: no secp256k1jni in java.library.path` when running desktop app

**Diagnosis:**
```bash
./gradlew :desktopApp:dependencies --configuration runtimeClasspath | grep secp256k1
# Shows: secp256k1-kmp-jni-android  ← WRONG!
```

**Fix:**
```kotlin
// In quartz/build.gradle.kts
jvmMain {
    dependencies {
        // Change from:
        // implementation(libs.secp256k1.kmp.jni.android)  ❌

        // To:
        implementation(libs.secp256k1.kmp.jni.jvm)  // ✅
    }
}
```

**Verify:**
```bash
./gradlew :desktopApp:dependencies --configuration runtimeClasspath | grep secp256k1
# Now shows: secp256k1-kmp-jni-jvm  ✅

./gradlew :desktopApp:run  # Should work
```

### Example 3: Optimizing Build Time

**Current:** Clean build takes 5 minutes

**Steps:**
1. **Baseline measurement:**
```bash
./gradlew clean build --profile
# Check: build/reports/profile/profile-*.html
```

2. **Add optimizations** to gradle.properties:
```properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.jvmargs=-Xmx4g
kotlin.incremental=true
```

3. **Re-measure:**
```bash
./gradlew clean build --profile
```

**Expected improvement:** 30-50% faster on subsequent builds (incremental builds much faster).

## Delegation Patterns

**When to delegate to other skills:**
- **Source set architecture** (jvmAndroid pattern, expect/actual) → Use `kotlin-multiplatform` skill
- **Compose UI issues** (composables, state management) → Use `compose-expert` skill (when available)
- **Kotlin language issues** (Flow, sealed classes, DSLs) → Use `kotlin-expert` skill
- **Desktop-specific features** (Window management, MenuBar, tray) → Use `desktop-expert` skill

**This skill handles:** Build system, dependencies, versioning, module structure, packaging, performance.

## Core Principles for This Build System

1. **Centralize versions:** Never hardcode versions in build.gradle.kts. Always use libs.versions.toml.

2. **Respect source set hierarchy:** Dependencies flow downward. jvmAndroid depends on commonMain, never the reverse.

3. **Platform-specific variants matter:** secp256k1, JNA must use correct variant per platform. Check when errors occur.

4. **Clean builds are expensive:** Use incremental compilation. Only clean when truly needed (source set changes, version updates).

5. **Compose alignment is critical:** Compose Multiplatform version must match Kotlin version. Check compatibility matrix.

6. **Proguard for native libs:** All JNI libraries need explicit `-keep` rules in release builds.

7. **Java 21 everywhere:** All modules, all targets, consistent JVM version.
