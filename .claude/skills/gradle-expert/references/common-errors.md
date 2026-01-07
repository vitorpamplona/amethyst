# Common Build Errors & Solutions

## Table of Contents
- [Compose Version Conflicts](#compose-version-conflicts)
- [secp256k1 JNI Errors](#secp256k1-jni-errors)
- [Source Set Dependency Issues](#source-set-dependency-issues)
- [Proguard/R8 Issues](#proguardr8-issues)
- [Desktop Packaging Errors](#desktop-packaging-errors)
- [Kotlin Compilation Errors](#kotlin-compilation-errors)
- [Dependency Resolution Failures](#dependency-resolution-failures)
- [JVM/JDK Version Issues](#jvmjdk-version-issues)

---

## Compose Version Conflicts

### Error 1: Compose Runtime Mismatch

```
java.lang.IllegalStateException: Version mismatch: Compose runtime is 1.10.0 but compiler is 1.9.0
```

**Cause:** Compose Compiler plugin version doesn't match Compose Runtime

**Solution:**
```kotlin
// In gradle/libs.versions.toml
composeMultiplatform = "1.9.3"  // Must align with Kotlin version
kotlin = "2.3.0"

// Check compatibility matrix:
// https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html
```

**Verification:**
```bash
./gradlew :commons:dependencies | grep compose
```

### Error 2: AndroidX Compose BOM Conflict

```
Duplicate class androidx.compose.ui.platform.AndroidCompositionLocalMap found in modules...
```

**Cause:** Both Compose Multiplatform and AndroidX Compose BOM providing same classes

**Solution:**
```kotlin
// In commons/build.gradle.kts (KMP module)
// Use Compose Multiplatform, NOT AndroidX BOM
dependencies {
    implementation(compose.ui)              // ✅ Compose Multiplatform
    implementation(compose.material3)

    // Don't use in KMP modules:
    // implementation(libs.androidx.compose.bom)  // ❌ Android-only
}

// In amethyst/build.gradle.kts (Android-only module)
// Can use AndroidX BOM
dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
}
```

### Error 3: Material3 WindowSizeClass Not Found

```
Unresolved reference: WindowSizeClass
```

**Cause:** Using Android's WindowSizeClass in shared KMP code

**Solution:**
```kotlin
// Don't use in commonMain or jvmAndroid:
// import androidx.compose.material3.windowsizeclass.WindowSizeClass  // ❌

// Use in androidMain only, or create expect/actual:
// commonMain
expect class WindowSizeClassAdapter

// androidMain
actual typealias WindowSizeClassAdapter = androidx.compose.material3.windowsizeclass.WindowSizeClass

// jvmMain (desktop)
actual class WindowSizeClassAdapter { /* Custom impl */ }
```

---

## secp256k1 JNI Errors

### Error 1: JNI Library Not Found (Desktop)

```
java.lang.UnsatisfiedLinkError: no secp256k1jni in java.library.path
```

**Cause:** Desktop using wrong secp256k1 variant (Android JNI instead of JVM JNI)

**Solution:**
```kotlin
// In quartz/build.gradle.kts
sourceSets {
    jvmMain {
        dependencies {
            // ✅ Correct - JVM variant
            implementation(libs.secp256k1.kmp.jni.jvm)

            // ❌ Wrong - Android variant
            // implementation(libs.secp256k1.kmp.jni.android)
        }
    }
}
```

**Verification:**
```bash
./gradlew :quartz:dependencies --configuration jvmRuntimeClasspath | grep secp256k1
# Should show: secp256k1-kmp-jni-jvm, NOT jni-android
```

### Error 2: Version Mismatch Between Variants

```
java.lang.NoSuchMethodError: fr.acinq.secp256k1.Secp256k1.sign
```

**Cause:** Common, Android, and JVM variants have different versions

**Solution:**
```toml
# In gradle/libs.versions.toml
# All three MUST use same version
secp256k1KmpJniAndroid = "0.22.0"

[libraries]
secp256k1-kmp-common = { ..., version.ref = "secp256k1KmpJniAndroid" }
secp256k1-kmp-jni-android = { ..., version.ref = "secp256k1KmpJniAndroid" }
secp256k1-kmp-jni-jvm = { ..., version.ref = "secp256k1KmpJniAndroid" }
```

### Error 3: Android JNI Not Loaded

```
java.lang.UnsatisfiedLinkError: dalvik.system.PathClassLoader couldn't find "libsecp256k1jni.so"
```

**Cause:** Proguard stripping JNI classes

**Solution:**
```proguard
# In quartz/proguard-rules.pro
-keep class fr.acinq.secp256k1.** { *; }
```

---

## Source Set Dependency Issues

### Error 1: jvmAndroid Defined After androidMain

```
Could not get unknown property 'jvmAndroid' for source set container
```

**Cause:** Source sets must be defined in dependency order

**Solution:**
```kotlin
// ✅ Correct order
sourceSets {
    commonMain { }

    // Define jvmAndroid BEFORE androidMain and jvmMain
    val jvmAndroid = create("jvmAndroid") {
        dependsOn(commonMain.get())
    }

    androidMain {
        dependsOn(jvmAndroid)  // Now jvmAndroid exists
    }

    jvmMain {
        dependsOn(jvmAndroid)
    }
}
```

### Error 2: Dependency in Wrong Source Set

```
Unresolved reference: ObjectMapper (Jackson)
```

**Cause:** JVM-only library in commonMain

**Solution:**
```kotlin
sourceSets {
    commonMain {
        // ❌ Jackson is JVM-only, can't use here
        // implementation(libs.jackson.module.kotlin)
    }

    val jvmAndroid = create("jvmAndroid") {
        dependsOn(commonMain.get())
        // ✅ Jackson in jvmAndroid (shared JVM code)
        api(libs.jackson.module.kotlin)
    }
}
```

### Error 3: Platform-Specific Code in Shared Source Set

```
java.lang.NoClassDefFoundError: android.content.Context
```

**Cause:** Android-specific API in jvmAndroid or commonMain

**Solution:**
```kotlin
// Use expect/actual pattern

// commonMain
expect class PlatformContext

// androidMain
actual typealias PlatformContext = android.content.Context

// jvmMain
actual class PlatformContext {
    // Custom desktop implementation
}
```

---

## Proguard/R8 Issues

### Error 1: Native Library Classes Stripped

```
java.lang.NoClassDefFoundError: com.goterl.lazysodium.Sodium
```

**Cause:** R8/Proguard removing JNA/LibSodium classes

**Solution:**
```proguard
# In quartz/proguard-rules.pro
-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }
-keep class fr.acinq.secp256k1.** { *; }
```

### Error 2: Reflection-Based Libraries Broken

```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance of ...
```

**Cause:** Jackson uses reflection, R8 strips class metadata

**Solution:**
```proguard
# Preserve reflection metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Keep all Quartz event classes
-keep class com.vitorpamplona.quartz.** { *; }
```

### Error 3: Enum Values Missing

```
java.lang.IllegalArgumentException: No enum constant ...
```

**Cause:** R8 obfuscating enum names

**Solution:**
```proguard
# Keep all enums
-keep enum ** { *; }
-keepnames class ** { *; }
```

---

## Desktop Packaging Errors

### Error 1: Icon Not Found

```
FAILURE: Build failed with an exception.
* What went wrong: Cannot find icon file: src/jvmMain/resources/icon.icns
```

**Cause:** Icon file missing or wrong path

**Solution:**
```kotlin
// In desktopApp/build.gradle.kts
nativeDistributions {
    macOS {
        // Ensure file exists at this path
        iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
    }

    // Check file exists:
    // ls -la desktopApp/src/jvmMain/resources/
}
```

**Icon Requirements:**
- macOS: `.icns` (512x512, 256x256, 128x128, 32x32)
- Windows: `.ico` (256x256, 128x128, 64x64, 32x32, 16x16)
- Linux: `.png` (512x512 recommended)

### Error 2: Main Class Not Found

```
Error: Could not find or load main class com.vitorpamplona.amethyst.desktop.MainKt
```

**Cause:** Wrong mainClass path or Main.kt doesn't have main()

**Solution:**
```kotlin
// In desktopApp/build.gradle.kts
compose.desktop {
    application {
        mainClass = "com.vitorpamplona.amethyst.desktop.MainKt"
        //                                                  ^^^^
        //                                                  Kotlin compiler adds "Kt" suffix
    }
}

// In src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/Main.kt
fun main() = application {
    // ...
}
```

### Error 3: Native Library Missing in Package

```
java.lang.UnsatisfiedLinkError: no secp256k1jni in java.library.path
```

**Cause:** Native libraries not bundled in distribution

**Solution:**
```kotlin
// Native libs are automatically included via dependencies
// Verify secp256k1-kmp-jni-jvm is in dependencies:
dependencies {
    implementation(libs.secp256k1.kmp.jni.jvm)  // ✅ Includes native libs
}

// Test packaged app:
./gradlew :desktopApp:createDistributable
# Run from: desktopApp/build/compose/binaries/main/app/
```

---

## Kotlin Compilation Errors

### Error 1: Expect/Actual Mismatch

```
'actual' declaration has no corresponding expected declaration
```

**Cause:** Signature mismatch or missing expect

**Solution:**
```kotlin
// commonMain - expect declaration
expect class CryptoProvider {
    fun sign(message: ByteArray, privateKey: ByteArray): ByteArray
}

// androidMain & jvmMain - actual must match EXACTLY
actual class CryptoProvider {
    actual fun sign(message: ByteArray, privateKey: ByteArray): ByteArray {
        // Implementation
    }
}

// Common mistakes:
// - Different parameter names  ❌
// - Different return types     ❌
// - Missing 'actual' modifier  ❌
```

### Error 2: Target JVM Version Mismatch

```
Compilation failed: module was compiled with an incompatible version of Kotlin
```

**Cause:** Different JVM targets across modules

**Solution:**
```kotlin
// Ensure ALL modules use same JVM target

// In quartz/build.gradle.kts
kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)  // ✅ Java 21
        }
    }
}

// In android {} block
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
```

### Error 3: Compose Compiler Plugin Missing

```
This declaration needs opt-in. Please use @OptIn(ComposeApi::class) or @Composable
```

**Cause:** Compose compiler plugin not applied

**Solution:**
```kotlin
// In build.gradle.kts
plugins {
    alias(libs.plugins.jetbrainsComposeCompiler)  // ✅ Add this
    alias(libs.plugins.composeMultiplatform)
}
```

---

## Dependency Resolution Failures

### Error 1: Repository Not Found

```
Could not find com.github.vitorpamplona.compose-richtext:richtext-ui:f92ef49c9d
```

**Cause:** Jitpack or custom Maven repository not configured

**Solution:**
```kotlin
// In settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = "https://jitpack.io" }  // ✅ Add Jitpack
    }
}
```

### Error 2: Gradle Version Too Old

```
Version catalogs are not supported in this version of Gradle
```

**Cause:** Gradle < 7.0

**Solution:**
```properties
# In gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

Then: `./gradlew wrapper --gradle-version=8.9`

### Error 3: Dependency Variant Not Found

```
No matching variant of fr.acinq.secp256k1:secp256k1-kmp-jni-android:0.22.0 was found
```

**Cause:** Wrong dependency configuration for target

**Solution:**
```kotlin
// In androidMain (Android library module)
dependencies {
    // For AAR packaging
    implementation("net.java.dev.jna:jna:5.18.1@aar")  // ✅ Specify @aar

    // secp256k1 works without @aar (auto-detects)
    api(libs.secp256k1.kmp.jni.android)
}
```

---

## JVM/JDK Version Issues

### Error 1: Unsupported Class File Version

```
Unsupported class file major version 65
```

**Cause:** Compiled with Java 21, running with older Java

**Solution:**
```bash
# Check Java version
java -version  # Should show 21

# Set JAVA_HOME if needed
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Or in gradle.properties
org.gradle.java.home=/path/to/jdk-21
```

### Error 2: JVM Toolchain Not Found

```
No matching toolchain found for requested JvmVersion
```

**Cause:** Java 21 not installed or not detected

**Solution:**
```bash
# macOS (Homebrew)
brew install openjdk@21

# Ubuntu
sudo apt install openjdk-21-jdk

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # macOS
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk      # Linux

# Verify
./gradlew -version
```

### Error 3: Gradle Daemon Using Wrong Java

```
Daemon will be stopped at the end of the build because JVM version has changed
```

**Cause:** Daemon started with different Java version

**Solution:**
```bash
# Stop all daemons
./gradlew --stop

# Start with correct JAVA_HOME
export JAVA_HOME=/path/to/jdk-21
./gradlew build

# Or set in gradle.properties permanently
org.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

---

## General Troubleshooting Steps

### Step 1: Clean Build
```bash
./gradlew clean
./gradlew --stop  # Stop daemon
./gradlew build
```

### Step 2: Check Dependencies
```bash
./gradlew :moduleName:dependencies
./gradlew dependencyInsight --dependency libraryName
```

### Step 3: Enable Debug Logging
```bash
./gradlew build --info     # Info logging
./gradlew build --debug    # Debug logging (verbose)
./gradlew build --stacktrace
```

### Step 4: Invalidate Caches
```bash
# Clear Gradle cache
rm -rf ~/.gradle/caches/

# Clear build outputs
./gradlew clean

# Clear Gradle wrapper cache
rm -rf ~/.gradle/wrapper/
```

### Step 5: Build Scan
```bash
./gradlew build --scan
# Opens interactive diagnostics in browser
```

## Quick Reference: Error Keywords → Solution

| Error Keyword | Likely Cause | Quick Fix |
|---------------|--------------|-----------|
| `UnsatisfiedLinkError` | Wrong JNI variant | Check secp256k1/JNA variants by platform |
| `IllegalStateException` (Compose) | Version mismatch | Align Compose Multiplatform + Kotlin versions |
| `NoClassDefFoundError` | Proguard stripping | Add `-keep` rule for class |
| `Unresolved reference` | Wrong source set | Move to appropriate source set (jvmAndroid) |
| `Duplicate class` | BOM conflict | Remove AndroidX BOM from KMP modules |
| `Version mismatch` | Plugin/runtime version mismatch | Update libs.versions.toml |
| `No matching variant` | Repository or packaging issue | Add repository or @aar suffix |
| `Could not find` (dependency) | Missing repository | Add maven/jitpack to repositories |
| `Unsupported class file` | Java version mismatch | Update JAVA_HOME to Java 21 |

---

## Getting Help

1. **Check Build Scan**: `./gradlew build --scan` for detailed diagnostics
2. **Gradle Forums**: https://discuss.gradle.org/
3. **Kotlin Slack**: #multiplatform channel
4. **Stack Overflow**: Tags `gradle`, `kotlin-multiplatform`, `compose-multiplatform`
