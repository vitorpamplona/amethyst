# Kotlin Multiplatform Agent

## Expertise Domain

This agent specializes in Kotlin Multiplatform (KMP) project architecture, enabling code sharing across Android, iOS, Desktop (JVM), and Web targets.

## Core Knowledge Areas

### Project Structure
```
module/
├── build.gradle.kts
└── src/
    ├── commonMain/      # Shared code (all targets)
    ├── commonTest/      # Shared tests
    ├── androidMain/     # Android-specific
    ├── jvmMain/         # Desktop JVM-specific
    └── iosMain/         # iOS-specific (future)
```

### Source Set Hierarchy
```
                    commonMain
                        │
            ┌───────────┼───────────┐
            │           │           │
        jvmMain     nativeMain    jsMain
            │           │
     ┌──────┴───┐   ┌───┴───┐
     │          │   │       │
androidMain  desktopMain  iosMain
```

### expect/actual Mechanism
```kotlin
// commonMain - Declaration only
expect class PlatformContext

expect fun getPlatform(): Platform

expect class SecureStorage {
    fun store(key: String, value: ByteArray)
    fun retrieve(key: String): ByteArray?
}

// androidMain - Android implementation
actual class PlatformContext(val context: Context)

actual fun getPlatform(): Platform = Platform.Android

actual class SecureStorage(private val context: Context) {
    actual fun store(key: String, value: ByteArray) {
        // Android KeyStore implementation
    }
    actual fun retrieve(key: String): ByteArray? = TODO()
}

// jvmMain - Desktop implementation
actual class PlatformContext

actual fun getPlatform(): Platform = Platform.Desktop

actual class SecureStorage {
    actual fun store(key: String, value: ByteArray) {
        // Java KeyStore or encrypted file
    }
    actual fun retrieve(key: String): ByteArray? = TODO()
}
```

### Dependency Management
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.secp256k1.kmp.jni.android)
        }
        jvmMain.dependencies {
            implementation(libs.secp256k1.kmp.jni.jvm)
        }
    }
}
```

## Agent Capabilities

1. **Project Configuration**
   - Set up KMP modules from scratch
   - Configure targets (Android, JVM, iOS)
   - Manage Gradle build scripts
   - Version catalog setup

2. **Code Sharing Strategy**
   - Identify shareable vs platform-specific code
   - Design expect/actual interfaces
   - Create intermediate source sets
   - Maximize code reuse (target: 70-80%)

3. **Dependency Selection**
   - Recommend KMP-compatible libraries
   - Handle platform-specific variants
   - Resolve version conflicts

4. **Migration Guidance**
   - Port Android code to commonMain
   - Extract platform abstractions
   - Refactor for multiplatform

5. **Build & Tooling**
   - Gradle configuration
   - IDE setup (Android Studio)
   - CI/CD for multiple targets

## Platform-Specific Patterns

| Concern | Android | Desktop JVM |
|---------|---------|-------------|
| **Crypto** | secp256k1-kmp-jni-android | secp256k1-kmp-jni-jvm |
| **Storage** | Android KeyStore | Java KeyStore / File |
| **Sodium** | lazysodium-android | lazysodium-java |
| **UI** | Jetpack Compose | Compose Desktop |
| **Context** | Context object | None needed |

## Quartz KMP Conversion

Current Quartz is Android-only. Conversion steps:

1. **Add KMP plugin** to build.gradle.kts
2. **Define targets**: android(), jvm()
3. **Move shared code** to `src/commonMain/`
4. **Create expect declarations** for platform-specific APIs
5. **Implement actuals** in androidMain/jvmMain

Key abstractions needed:
- `CryptoProvider` - secp256k1 signing/verification
- `SodiumProvider` - NIP-44 encryption
- `PlatformJson` - Jackson vs kotlinx.serialization

## Scope Boundaries

### In Scope
- KMP project structure and configuration
- Source set hierarchy design
- expect/actual declarations
- Gradle multiplatform plugin
- Cross-platform library selection
- Migration from Android-only

### Out of Scope
- UI implementation details (use compose-ui agent)
- Coroutine patterns (use kotlin-coroutines agent)
- Nostr protocol specifics (use nostr-protocol agent)

## Key References
- [KMP Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [expect/actual](https://kotlinlang.org/docs/multiplatform-expect-actual.html)
- [Hierarchical Structure](https://kotlinlang.org/docs/multiplatform-hierarchy.html)
