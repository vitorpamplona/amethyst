# Version Catalog Guide

## Overview

AmethystMultiplatform uses Gradle's version catalog (`gradle/libs.versions.toml`) to centralize dependency management. This ensures version consistency across all modules and simplifies updates.

## Structure

### sections
```toml
[versions]     # Version numbers (referenced by libraries and plugins)
[libraries]    # Library dependencies
[plugins]      # Gradle plugins
```

## Version References

### Defining Versions
```toml
[versions]
kotlin = "2.3.0"
composeMultiplatform = "1.9.3"
okhttp = "5.3.2"
```

### Special Patterns

#### Android SDK Versions
```toml
android-compileSdk = "36"
android-minSdk = "26"
android-targetSdk = "36"
```
**Access in build.gradle.kts:**
```kotlin
compileSdk = libs.versions.android.compileSdk.get().toInt()
minSdk = libs.versions.android.minSdk.get().toInt()
```

#### Version Suffixes (Git Commits)
```toml
androidKotlinGeohash = "b481c6a64e"  # Jitpack commit hash
markdown = "f92ef49c9d"
```
**Why:** For GitHub dependencies via Jitpack that don't have semantic versions

## Library Declarations

### Basic Pattern
```toml
[libraries]
library-name = { group = "...", name = "...", version.ref = "..." }
```

### Examples

#### Version Reference
```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
```

#### Module Reference (for multi-artifact libs)
```toml
androidx-camera-core = { module = "androidx.camera:camera-core", version.ref = "androidxCamera" }
```

#### Without Group (shorthand)
```toml
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
```
**Note:** Inherits version from BOM (compose-bom)

### BOMs (Bill of Materials)

#### AndroidX Compose BOM
```toml
[versions]
composeBom = "2025.12.01"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
```

**Usage in build.gradle.kts:**
```kotlin
val composeBom = platform(libs.androidx.compose.bom)
implementation(composeBom)
implementation(libs.androidx.ui)        // Version from BOM
implementation(libs.androidx.material3) // Version from BOM
```

**Benefits:**
- All AndroidX Compose artifacts use compatible versions
- Update single BOM version, not individual libraries
- Prevents version conflicts

### Platform-Specific Variants

#### secp256k1 (KMP crypto library)
```toml
secp256k1KmpJniAndroid = "0.22.0"

[libraries]
secp256k1-kmp-common = { group = "fr.acinq.secp256k1", name = "secp256k1-kmp", version.ref = "secp256k1KmpJniAndroid" }
secp256k1-kmp-jni-android = { group = "fr.acinq.secp256k1", name = "secp256k1-kmp-jni-android", version.ref = "secp256k1KmpJniAndroid" }
secp256k1-kmp-jni-jvm = { group = "fr.acinq.secp256k1", name = "secp256k1-kmp-jni-jvm", version.ref = "secp256k1KmpJniAndroid" }
```

**Critical:** All three variants MUST share the same version

#### JNA (for LibSodium)
```toml
jna = "5.18.1"

[libraries]
jna = { group = "net.java.dev.jna", name = "jna", version.ref = "jna" }
```

**Usage in build.gradle.kts:**
```kotlin
// androidMain - AAR packaging
implementation("net.java.dev.jna:jna:5.18.1@aar")

// jvmMain - JAR packaging
implementation(libs.jna)
```

**Why:** Android needs AAR, JVM needs JAR (different artifact types)

## Plugin Declarations

### Basic Pattern
```toml
[plugins]
plugin-id = { id = "...", version.ref = "..." }
```

### Examples

#### Kotlin Plugins
```toml
[versions]
kotlin = "2.3.0"

[plugins]
jetbrainsKotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
jetbrainsKotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
jetbrainsComposeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
serialization = { id = 'org.jetbrains.kotlin.plugin.serialization', version.ref = 'kotlinxSerializationPlugin' }
```

**Critical:** All Kotlin plugins MUST use the same Kotlin version

#### Android Gradle Plugin
```toml
[versions]
agp = "8.13.2"

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
androidKotlinMultiplatformLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
```

#### Compose Multiplatform
```toml
[versions]
composeMultiplatform = "1.9.3"

[plugins]
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
```

### Plugin Application

```kotlin
// In build.gradle.kts
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
}
```

## Usage in Build Files

### Accessing Versions
```kotlin
// Direct version access
val kotlinVersion = libs.versions.kotlin.get()
val minSdk = libs.versions.android.minSdk.get().toInt()
```

### Accessing Libraries
```kotlin
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.secp256k1.kmp.common)
    implementation(libs.okhttp)
}
```

### Accessing Plugins
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}
```

## Version Catalog Benefits

### 1. Centralized Version Management
Update once, applies everywhere:
```toml
# Change one line
kotlin = "2.3.0"  → "2.4.0"

# Affects all usages
- kotlinMultiplatform plugin
- jetbrainsKotlinAndroid plugin
- kotlin-stdlib
- All Kotlin-related dependencies
```

### 2. Type-Safe Accessors
```kotlin
// Compile-time checked
implementation(libs.okhttp)  // ✅ IDE autocomplete

// vs string-based (error-prone)
implementation("com.squareup.okhttp3:okhttp:5.3.2")  // ❌ No autocomplete
```

### 3. Dependency Consistency
```kotlin
// All modules reference same catalog
:quartz  → libs.okhttp
:commons → libs.okhttp
:desktopApp → libs.okhttp

// Same version everywhere
```

### 4. Gradle Sync Improvements
- Faster IDE sync (pre-parsed catalog)
- Better dependency resolution
- Clearer error messages

## Common Patterns

### GitHub Dependencies (Jitpack)
```toml
[versions]
markdown = "f92ef49c9d"  # Git commit hash

[libraries]
markdown-ui = { group = "com.github.vitorpamplona.compose-richtext", name = "richtext-ui", version.ref = "markdown" }
```

**Repository config** (in settings.gradle):
```kotlin
repositories {
    maven { url = "https://jitpack.io" }
}
```

### Multi-Artifact Libraries
```toml
[versions]
media3 = "1.9.0"

[libraries]
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
```

**Why:** All media3 artifacts share same version for compatibility

### Test Dependencies
```toml
[libraries]
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinx-coroutines-test"}
```

## Version Update Strategy

### Check for Updates
```bash
# Using Gradle Versions Plugin (if installed)
./gradlew dependencyUpdates

# Manual check
# Browse to Maven Central for specific library
```

### Update Process
1. **Update version in catalog**
   ```toml
   okhttp = "5.3.2"  → "5.4.0"
   ```

2. **Test locally**
   ```bash
   ./gradlew clean build
   ```

3. **Check for breaking changes**
   - Review library changelog
   - Run full test suite

4. **Commit with clear message**
   ```
   chore: update okhttp 5.3.2 → 5.4.0
   ```

### Critical Version Alignments

#### Kotlin Ecosystem
```toml
kotlin = "2.3.0"
kotlinxCoroutinesCore = "1.10.2"
kotlinxSerialization = "1.9.0"
```
**Rule:** Kotlin version must be compatible with kotlinx libraries

#### Compose Ecosystem
```toml
composeMultiplatform = "1.9.3"
composeBom = "2025.12.01"
kotlin = "2.3.0"
```
**Rule:** Compose Multiplatform → Kotlin version (see compatibility matrix)

#### AGP & Gradle
```toml
agp = "8.13.2"
# Requires Gradle 8.9+
```
**Rule:** AGP version dictates minimum Gradle version

## Troubleshooting

### Issue 1: Unresolved Reference
**Error:** `Unresolved reference: libs`

**Cause:** Gradle version < 7.0 (version catalogs not supported)

**Fix:** Upgrade Gradle in `gradle/wrapper/gradle-wrapper.properties`

### Issue 2: Library Not Found
**Error:** `Could not find com.example:library:1.0.0`

**Cause:** Repository not configured or typo in catalog

**Fix:**
1. Check repository in settings.gradle
2. Verify group/name/version in libs.versions.toml

### Issue 3: Version Conflict
**Error:** `Conflict with dependency ... and ...`

**Cause:** Different versions of same library via transitive dependencies

**Fix:**
```kotlin
configurations.all {
    resolutionStrategy {
        force(libs.okhttp.get().toString())
    }
}
```

## Best Practices

### 1. Naming Conventions
```toml
# Hyphen-separated, hierarchical
androidx-compose-ui
androidx-compose-material3
kotlinx-coroutines-core

# Platform suffixes
secp256k1-kmp-jni-android
secp256k1-kmp-jni-jvm
```

### 2. Group Related Dependencies
```toml
# Camera APIs together
androidx-camera-core
androidx-camera-camera2
androidx-camera-view
```

### 3. Document Special Cases
```toml
# JNA requires @aar for Android (see build.gradle.kts)
jna = { group = "net.java.dev.jna", name = "jna", version.ref = "jna" }
```

### 4. Keep BOMs Updated
```toml
# Update BOM, individual libs follow
composeBom = "2025.12.01"  # Latest stable
```

### 5. Test Version Updates
```bash
# Before committing
./gradlew :quartz:test
./gradlew :commons:test
./gradlew :desktopApp:run
```
