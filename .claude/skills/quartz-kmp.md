# Quartz KMP Conversion Skill

When working with Quartz library conversion to Kotlin Multiplatform:

## Current Structure (Android-only)
```
quartz/
├── build.gradle
└── src/
    ├── main/kotlin/          # All Nostr code here
    ├── test/
    └── androidTest/
```

## Target Structure (KMP)
```
quartz/
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/    # Shared protocol code
    ├── commonTest/kotlin/    # Shared tests
    ├── androidMain/kotlin/   # Android crypto, storage
    ├── androidTest/kotlin/
    ├── jvmMain/kotlin/       # Desktop crypto, storage
    └── jvmTest/kotlin/
```

## Platform Abstractions Required

### 1. Cryptography (expect/actual)
```kotlin
// commonMain
expect object Secp256k1 {
    fun sign(data: ByteArray, privateKey: ByteArray): ByteArray
    fun verify(data: ByteArray, signature: ByteArray, pubKey: ByteArray): Boolean
    fun pubKeyCreate(privateKey: ByteArray): ByteArray
}

// androidMain - uses secp256k1-kmp-jni-android
actual object Secp256k1 {
    actual fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        return fr.acinq.secp256k1.Secp256k1.sign(data, privateKey)
    }
    // ...
}

// jvmMain - uses secp256k1-kmp-jni-jvm
actual object Secp256k1 {
    actual fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        return fr.acinq.secp256k1.Secp256k1.sign(data, privateKey)
    }
    // ...
}
```

### 2. NIP-44 Encryption (Sodium)
```kotlin
// commonMain
expect object Nip44 {
    fun encrypt(plaintext: String, sharedSecret: ByteArray): String
    fun decrypt(ciphertext: String, sharedSecret: ByteArray): String
}

// androidMain - lazysodium-android
// jvmMain - lazysodium-java or libsodium-jni
```

### 3. Secure Random
```kotlin
// commonMain
expect fun secureRandomBytes(size: Int): ByteArray

// androidMain
actual fun secureRandomBytes(size: Int): ByteArray {
    return SecureRandom().let { random ->
        ByteArray(size).also { random.nextBytes(it) }
    }
}

// jvmMain
actual fun secureRandomBytes(size: Int): ByteArray {
    return java.security.SecureRandom().let { random ->
        ByteArray(size).also { random.nextBytes(it) }
    }
}
```

## Build Configuration

```kotlin
// quartz/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.collections.immutable)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.secp256k1.kmp.jni.android)
                implementation(libs.lazysodium.android)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.secp256k1.kmp.jni.jvm)
                // lazysodium-java or alternative
            }
        }
    }
}

android {
    namespace = "com.vitorpamplona.quartz"
    compileSdk = 35
    defaultConfig.minSdk = 26
}
```

## Migration Steps

1. **Convert build.gradle to build.gradle.kts** with KMP plugin
2. **Move pure Kotlin code** to `commonMain/`
3. **Identify platform dependencies** (crypto, JNA, Android APIs)
4. **Create expect declarations** for platform-specific APIs
5. **Implement actuals** in androidMain and jvmMain
6. **Update imports** in amethyst module
7. **Test on both platforms**

## Files to Move to commonMain

Most of Quartz can be shared:
- Event classes and parsing
- Filter definitions
- Relay message types
- NIP implementations (logic only)
- Utilities (hex encoding, bech32)

## Files Needing expect/actual

- `Secp256k1.kt` - Signature operations
- `Nip04.kt` - Legacy encryption (uses AES)
- `Nip44.kt` - Modern encryption (uses ChaCha)
- `KeyPair.kt` - Key generation
