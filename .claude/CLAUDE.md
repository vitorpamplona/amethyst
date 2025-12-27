# Amethyst Desktop Fork

## Project Overview

Fork of [Amethyst](https://github.com/vitorpamplona/amethyst) adding Compose Multiplatform Desktop support. Quartz library converted to full KMP for code sharing between Android and Desktop JVM.

## Architecture

```
amethyst/
├── quartz/         # Nostr KMP library (protocol only, no UI)
│   └── src/
│       ├── commonMain/    # Shared Nostr protocol, data models
│       ├── androidMain/   # Android-specific (crypto, storage)
│       └── jvmMain/       # Desktop JVM-specific
├── commons/        # Shared UI components (convert to KMP)
│   └── src/
│       ├── commonMain/    # Shared composables, icons, state
│       ├── androidMain/   # Android-specific UI utilities
│       └── jvmMain/       # Desktop-specific UI utilities
├── desktopApp/     # Desktop JVM application (layouts, navigation)
├── amethyst/       # Android app (layouts, navigation)
└── ammolite/       # Support module
```

**Sharing Philosophy:**
- `quartz/` = Business logic, protocol, data (no UI)
- `commons/` = Shared UI components, icons, composables
- `amethyst/` & `desktopApp/` = Platform-native layouts and navigation

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

## Feature Workflow

When picking up a new task or feature, follow this process:

### Step 1: Analyze Android Implementation

Start by examining the existing Android Amethyst codebase:
1. Find the relevant feature/component in `amethyst/` module
2. Understand the current implementation patterns
3. Identify dependencies and integrations

### Step 2: Create Implementation Plan

Before coding, create a plan that categorizes work into three buckets:

| Category | Description | Location |
|----------|-------------|----------|
| **Android-Specific** | Platform APIs, navigation, layouts | `amethyst/`, `androidMain/` |
| **Reusable (Shared)** | Business logic, UI components, state | `quartz/commonMain/`, `commons/` (convert to KMP) |
| **Desktop-Specific** | Desktop navigation, layouts, platform APIs | `desktopApp/`, `jvmMain/` |

### Step 3: Code Sharing Strategy

**Share:**
- Business logic and data models → `quartz/commonMain/`
- Major UI components (cards, lists, dialogs) → `commons/` (convert to KMP as needed)
- State management and ViewModels → shared
- Icons and visual assets → `commons/commonMain/`

**Keep Platform-Native:**
- Navigation patterns (sidebar vs bottom nav)
- Screen layouts and scaffolding
- Platform-specific interactions (gestures, keyboard shortcuts)
- System integrations (notifications, file pickers)

### Step 4: Extract Shared Components

When extracting UI components:
1. Identify reusable composables in Android code
2. Use `/extract <component>` to move to `commons/commonMain/`
3. Create expect/actual declarations for platform-specific behavior
4. Update both Android and Desktop to use shared component

**Note:** `quartz/` is protocol-only (no composables). Shared UI goes in `commons/` after converting it to KMP.

## Build Commands

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
