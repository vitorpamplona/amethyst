# Amethyst Desktop Fork

## Project Overview

Fork of [Amethyst](https://github.com/vitorpamplona/amethyst) adding Compose Multiplatform Desktop support. Quartz library converted to full KMP for code sharing between Android and Desktop JVM.

## Architecture

```
amethyst/
├── quartz/         # Nostr KMP library (converted to multiplatform)
│   └── src/
│       ├── commonMain/    # Shared Nostr protocol
│       ├── androidMain/   # Android-specific (crypto, storage)
│       └── jvmMain/       # Desktop JVM-specific
├── desktopApp/     # Desktop JVM application
├── amethyst/       # Android app (existing)
├── ammolite/       # Support module
└── commons/        # Utilities
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Core** | Quartz (Nostr KMP) |
| **UI** | Compose Multiplatform 1.7.x |
| **Async** | kotlinx.coroutines + Flow |
| **Network** | OkHttp (JVM) |
| **Serialization** | Jackson |
| **DI** | Manual / Koin |
| **Build** | Gradle 8.x, Kotlin 2.1.0 |

## Agents

Use these specialized agents for domain expertise:

| Agent | Expertise | When to Use |
|-------|-----------|-------------|
| `nostr-protocol` | NIPs, events, relays, crypto | Protocol questions, NIP implementation |
| `kotlin-multiplatform` | KMP, source sets, expect/actual | Project structure, code sharing |
| `compose-ui` | Composables, Desktop features | UI components, navigation |
| `kotlin-coroutines` | Flows, async, concurrency | Data streams, async operations |

## Commands

- `/desktop-run` - Build and run desktop app
- `/extract <component>` - Move composable to shared code
- `/nip <number>` - Get NIP implementation guidance

## Development Workflow

```bash
# Run desktop app
./gradlew :desktopApp:run

# Run Android app
./gradlew :amethyst:installDebug

# Build Quartz for all targets
./gradlew :quartz:build

# Run tests
./gradlew test

# Format code
./gradlew spotlessApply
```

## Quartz KMP Structure

The Quartz library uses expect/actual for platform-specific implementations:

```kotlin
// commonMain - shared protocol logic
expect class CryptoProvider {
    fun sign(message: ByteArray, privateKey: ByteArray): ByteArray
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}

// androidMain - uses secp256k1-kmp-jni-android
actual class CryptoProvider { /* Android implementation */ }

// jvmMain - uses secp256k1-kmp-jni-jvm
actual class CryptoProvider { /* JVM implementation */ }
```

## Key Patterns

### Platform Abstraction
```kotlin
// commonMain
expect fun openExternalUrl(url: String)

// androidMain
actual fun openExternalUrl(url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

// jvmMain (Desktop)
actual fun openExternalUrl(url: String) {
    Desktop.getDesktop().browse(URI(url))
}
```

### Navigation Shell
- **Desktop**: Sidebar + main content area
- **Android**: Bottom navigation

## Git Workflow

- Branch: `feat/desktop-<feature>` or `fix/desktop-<issue>`
- Commits: Conventional commits (`feat:`, `fix:`, etc.)
- Never use `--no-verify`

## Resources

- [Nostr NIPs](https://github.com/nostr-protocol/nips)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [KMP Documentation](https://kotlinlang.org/docs/multiplatform.html)
