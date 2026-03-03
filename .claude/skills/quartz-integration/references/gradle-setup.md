# Quartz Gradle Dependency Setup

## Current version

```
com.vitorpamplona.quartz:quartz:1.05.1
```

Check latest: https://central.sonatype.com/artifact/com.vitorpamplona.quartz/quartz

---

## KMP Project Setup

### `gradle/libs.versions.toml`

```toml
[versions]
quartz = "1.05.1"

[libraries]
quartz = { module = "com.vitorpamplona.quartz:quartz", version.ref = "quartz" }
```

### `build.gradle.kts` (library/app module)

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary) // or androidLibrary/androidApplication
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "com.example.myapp"
        compileSdk = 35
        minSdk = 21
    }
    // optional: iOS targets

    sourceSets {
        commonMain.dependencies {
            implementation(libs.quartz)
        }
        // No platform-specific deps needed — Quartz provides them transitively
    }
}
```

---

## Android-only Project

```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("com.vitorpamplona.quartz:quartz:1.05.1")
}
```

---

## JVM (Desktop) standalone

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.vitorpamplona.quartz:quartz:1.05.1")
    // JNA needed for libsodium (NIP-44) on JVM
    implementation("net.java.dev.jna:jna:5.18.1")
}
```

---

## Transitive Dependencies (what you get automatically)

### All platforms (`commonMain`)
- `org.jetbrains.kotlin:kotlin-stdlib`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core`
- `org.jetbrains.kotlinx:kotlinx-collections-immutable`
- `org.jetbrains.kotlinx:kotlinx-serialization-json`
- `androidx.collection:collection` (LruCache)
- `androidx.compose.runtime:runtime-annotation` (@Immutable/@Stable)
- `fr.acinq.secp256k1:secp256k1-kmp` (Schnorr crypto — common)

### JVM + Android (`jvmAndroid`)
- `com.github.anthonynsimon:rfc3986-normalizer` (URL normalization)
- `com.fasterxml.jackson.module:jackson-module-kotlin` (JSON)
- `com.linkedin.urls:url-detector` (URL extraction)
- `com.squareup.okhttp3:okhttp` (WebSocket)
- `ru.gildor.coroutines:kotlin-coroutines-okhttp`
- `nl.bommber:kchesslib` (NIP-64 chess, version pinned to 1.0.0)

### JVM only
- `fr.acinq.secp256k1:secp256k1-kmp-jni-jvm`
- `com.goterl:lazysodium-java` (NIP-44 encryption)
- `net.java.dev.jna:jna`

### Android only
- `fr.acinq.secp256k1:secp256k1-kmp-jni-android`
- `com.goterl:lazysodium-android`
- `net.java.dev.jna:jna` (aar)
- `androidx.core:core-ktx`

---

## Packaging / ProGuard

Quartz ships consumer ProGuard rules automatically (`publish = true` in the library).
You don't need to add manual keep rules for Quartz classes in your app.

For Android release builds, these classes are preserved:
- All `com.vitorpamplona.quartz.**` event and model classes
- Jackson serialization annotations

---

## Common Build Errors

### `Duplicate class kotlin.collections.jdk8`
Add to `gradle.properties`:
```properties
android.useFullClasspathForDexingTransform=true
```

### `Could not find lazysodium-java` on JVM
Ensure JNA is on the classpath:
```kotlin
implementation("net.java.dev.jna:jna:5.18.1")
```

### iOS: `Framework not found quartz-kmpKit`
Build the XCFramework first:
```bash
./gradlew :quartz:assembleQuartz-kmpKitReleaseXCFramework
```
Then drag `quartz/build/XCFrameworks/release/quartz-kmpKit.xcframework` into Xcode.
