# Module Dependency Graph

## Visual Hierarchy

```
┌─────────────────────────────────────────────────────────┐
│                    Root Project                          │
│                     (Amethyst)                           │
└─────────────────────────────────────────────────────────┘
                          │
         ┌────────────────┼────────────────┬────────────┐
         │                │                │            │
         ▼                ▼                ▼            ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐
│  :amethyst  │  │ :desktopApp │  │ :benchmark  │  │:ammolite │
│  (Android)  │  │   (JVM)     │  │  (Android)  │  │ (Support)│
└─────────────┘  └─────────────┘  └─────────────┘  └──────────┘
       │                │                │
       │                │                │
       └────────────────┼────────────────┘
                        │
                        ▼
                ┌─────────────┐
                │  :commons   │
                │  (KMP UI)   │
                │             │
                │ jvmAndroid  │
                │  /      \   │
                │ jvm   android│
                └─────────────┘
                        │
                        │
                        ▼
                ┌─────────────┐
                │  :quartz    │
                │(KMP Library)│
                │             │
                │ commonMain  │
                │     │       │
                │ jvmAndroid  │
                │  /  |  \    │
                │jvm and ios  │
                └─────────────┘
```

## Module Details

### :quartz (KMP Nostr Library)
**Type:** Kotlin Multiplatform Library
**Targets:** JVM, Android, iOS (iosX64, iosArm64, iosSimulatorArm64)
**Dependencies:**
- External: secp256k1, jackson, okhttp, kotlinx.coroutines, kotlinx.collections.immutable
- Source sets: commonMain → jvmAndroid → {androidMain, jvmMain}, iosMain

**Role:** Core Nostr protocol implementation, shared across all platforms

### :commons (Shared UI Components)
**Type:** Kotlin Multiplatform Library
**Targets:** JVM, Android
**Dependencies:**
- Module: `:quartz`
- External: Compose Multiplatform, Material3, kotlinx.collections.immutable
- Source sets: commonMain → jvmAndroid → {androidMain, jvmMain}

**Role:** Shared Compose UI components for Desktop and Android

### :desktopApp (Desktop Application)
**Type:** JVM Application
**Targets:** JVM (Desktop)
**Dependencies:**
- Modules: `:commons`, `:quartz`
- External: Compose Desktop, kotlinx.coroutines.swing

**Role:** Desktop-specific navigation, layouts, and entry point

### :amethyst (Android Application)
**Type:** Android Application
**Targets:** Android
**Dependencies:**
- Modules: `:commons`, `:quartz`, `:ammolite`
- External: Android SDK, AndroidX, Firebase, Tor

**Role:** Android-specific navigation, layouts, and entry point

### :benchmark (Android Benchmark)
**Type:** Android Library
**Targets:** Android
**Dependencies:**
- Modules: `:commons`, `:quartz`
- External: AndroidX Benchmark

**Role:** Performance benchmarking for Android builds

### :ammolite (Support Module)
**Type:** Android Library
**Targets:** Android
**Dependencies:** Android-specific utilities

**Role:** Android support utilities for amethyst

## Dependency Flow Patterns

### Desktop Build Chain
```
:desktopApp → :commons (jvmMain) → :quartz (jvmMain)
                                         ↓
                                   jvmAndroid
                                         ↓
                                   commonMain
```

### Android Build Chain
```
:amethyst → :commons (androidMain) → :quartz (androidMain)
    ↓                                      ↓
:ammolite                            jvmAndroid
                                         ↓
                                   commonMain
```

## Source Set Dependencies

### :quartz Source Sets
```
commonMain (base)
    ├─ jvmAndroid (shared JVM code)
    │   ├─ androidMain (Android platform)
    │   └─ jvmMain (Desktop platform)
    └─ iosMain (iOS platform)
        ├─ iosX64Main
        ├─ iosArm64Main
        └─ iosSimulatorArm64Main
```

**Key Dependencies per Source Set:**
- **commonMain**: secp256k1-kmp, kotlinx.coroutines, collection, immutable collections
- **jvmAndroid**: jackson, okhttp, url-detector, rfc3986
- **androidMain**: secp256k1-kmp-jni-android, lazysodium-android, jna (aar)
- **jvmMain**: secp256k1-kmp-jni-jvm, lazysodium-java, jna (jar)

### :commons Source Sets
```
commonMain (base UI)
    └─ jvmAndroid (shared JVM UI)
        ├─ androidMain (Android UI utilities)
        └─ jvmMain (Desktop UI utilities)
```

**Key Dependencies per Source Set:**
- **commonMain**: Compose Multiplatform, Material3, :quartz
- **jvmAndroid**: url-detector
- **androidMain**: AndroidX Compose tooling
- **jvmMain**: Compose Desktop

## Critical Dependency Patterns

### 1. secp256k1 Variants
```kotlin
// commonMain - API only
api(libs.secp256k1.kmp.common)

// androidMain - JNI Android
api(libs.secp256k1.kmp.jni.android)

// jvmMain - JNI JVM
implementation(libs.secp256k1.kmp.jni.jvm)
```
**Why:** Different JNI bindings for Android vs Desktop JVM

### 2. JNA Variants (for LibSodium)
```kotlin
// androidMain
implementation("com.goterl:lazysodium-android:5.2.0@aar")
implementation("net.java.dev.jna:jna:5.18.1@aar")

// jvmMain
implementation(libs.lazysodium.java)
implementation(libs.jna)  // JAR variant
```
**Why:** Android needs AAR packaging, JVM needs JAR

### 3. Compose Alignment
```kotlin
// commons/build.gradle.kts
implementation(compose.ui)              // Compose Multiplatform BOM
implementation(compose.material3)

// Version catalog alignment
composeMultiplatform = "1.9.3"
composeBom = "2025.12.01"  // AndroidX Compose
```
**Why:** Two Compose ecosystems (Multiplatform + AndroidX) must align

## Dependency Configuration Types

### API vs Implementation

**Use `api` when:**
- Dependency types appear in module's public API
- Used in expect/actual declarations visible to consumers
- Example: `secp256k1-kmp-common` in quartz (public types)

**Use `implementation` when:**
- Internal implementation detail
- Not exposed to module consumers
- Example: `okhttp` in quartz (internal network client)

### Example from quartz
```kotlin
// Public API - exposed to consumers
api(libs.secp256k1.kmp.common)
api(libs.jackson.module.kotlin)  // Event serialization public

// Internal implementation
implementation(libs.okhttp)
implementation(libs.kotlinx.coroutines.core)
```

## Transitive Dependency Impact

### When :desktopApp depends on :commons
- Gets `:quartz` transitively (via :commons)
- Gets `secp256k1-kmp-jvm` transitively (via :quartz jvmMain)
- Does NOT get Android-specific dependencies (scoped to androidMain)

### When :amethyst depends on :commons
- Gets `:quartz` transitively (via :commons)
- Gets `secp256k1-kmp-jni-android` transitively (via :quartz androidMain)
- Does NOT get JVM/Desktop-specific dependencies (scoped to jvmMain)

## Verifying Dependencies

### Check Module Dependencies
```bash
./gradlew :desktopApp:dependencies
./gradlew :amethyst:dependencies
```

### Check Specific Library
```bash
./gradlew dependencyInsight --dependency secp256k1
./gradlew dependencyInsight --dependency compose-ui
```

### Visualize with Build Scan
```bash
./gradlew :desktopApp:dependencies --scan
# Opens interactive dependency graph in browser
```

## Common Dependency Issues

### Issue 1: Wrong secp256k1 Variant in Desktop
**Symptom:** `UnsatisfiedLinkError: no secp256k1jni in java.library.path`
**Cause:** Desktop using Android JNI variant
**Fix:** Ensure jvmMain uses `secp256k1-kmp-jni-jvm`

### Issue 2: Compose Version Mismatch
**Symptom:** `IllegalStateException: Version mismatch`
**Cause:** Compose Multiplatform plugin vs runtime version mismatch
**Fix:** Align `composeMultiplatform` version in libs.versions.toml with Kotlin plugin

### Issue 3: Duplicate JNA Classes
**Symptom:** `DuplicateClassException: com.sun.jna.Native`
**Cause:** Both JAR and AAR JNA variants in classpath
**Fix:** Use AAR (@aar) in androidMain, JAR in jvmMain (never in shared source sets)
