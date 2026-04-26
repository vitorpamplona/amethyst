# Amethyst

## Project Overview

Amethyst is a Nostr Client for Android that was made for Android-only and has been slowly switching
over to a Kotlin Multiplatform project. The main modules are: `quartz`, `commons`, `amethyst`,
`desktopApp`, `cli`, plus the audio-rooms transport stack `quic` + `nestsClient`. Quartz should
contain implementations of Nostr specifications and utilities to help implement them. Commons stores
shared code between Amethyst Android (`amethyst`) and Amethyst Desktop (`desktopApp`). The Desktop
App is designed to be mouse first and so uses a completely different screen and navigation
architecture while sharing the back end components with the android counterpart. `cli` ships `amy`,
a non-interactive JVM command-line client that drives the same `quartz` + `commons` code — used by
humans, agents, and interop tests. `quic` is a from-scratch pure-Kotlin QUIC v1 + HTTP/3 +
WebTransport client (no JNI, no BouncyCastle), built because no Android-compatible Java QUIC library
exists. `nestsClient` runs the MoQ-transport audio-room protocol on top of `:quic` for the NIP-53
audio-rooms feature.

## Architecture

```
amethyst/
├── quartz/         # Nostr KMP library (protocol only, no UI)
│   └── src/
│       ├── commonMain/    # Shared Nostr protocol, data models
│       ├── androidMain/   # Android-specific (crypto, storage)
│       ├── jvmMain/       # Desktop JVM-specific
│       └── iosMain/       # iOS-specific
├── commons/        # Shared UI components (convert to KMP)
│   └── src/
│       ├── commonMain/    # Shared composables, icons, state
│       ├── androidMain/   # Android-specific UI utilities
│       └── jvmMain/       # Desktop-specific UI utilities
├── quic/           # Pure-Kotlin QUIC v1 + HTTP/3 + WebTransport (audio-rooms transport)
│   └── src/
│       ├── commonMain/    # Protocol, frame/packet codecs, TLS state machine
│       ├── jvmAndroid/    # JCA-backed AEAD + UDP socket actuals
│       └── commonTest/    # RFC vector + adversarial tests
├── nestsClient/    # MoQ-transport audio-room client on top of :quic
│   └── src/
│       ├── commonMain/    # MoQ session, NestsListener, audio glue
│       └── jvmAndroid/    # Opus encode/decode, AudioRecord/AudioTrack
├── desktopApp/     # Desktop JVM application (layouts, navigation)
├── amethyst/       # Android app (layouts, navigation)
├── cli/            # Amy — non-interactive CLI (JVM only, no Compose)
└── ammolite/       # Support module (unused)
```

**Sharing Philosophy:**
- `quartz/` = Nostr business logic, protocol, data (no UI)
- `commons/` = Shared UI components, icons, composables, flows and ViewModels
- `quic/` = Transport library (QUIC + HTTP/3 + WebTransport); reusable for any
  KMP project that needs MoQ. Has no Android-framework dependencies.
- `nestsClient/` = MoQ + audio-rooms client; takes `:quic` as transport,
  Quartz for crypto, `MediaCodec` / `AudioRecord` / `AudioTrack` for audio.
- `amethyst/` & `desktopApp/` = Platform-native layouts and navigation
- `cli/` = Thin assembly layer over `quartz/` + `commons/` (no new logic allowed)

**Plans per module:** design docs for new subsystems live in the owning
module's `plans/YYYY-MM-DD-<slug>.md` (e.g. `cli/plans/`, `commons/plans/`).
The global `docs/plans/` folder is frozen — don't add new plans there.

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Core** | Quartz (Nostr KMP) |
| **UI** | Compose Multiplatform 1.10.3 |
| **Async** | kotlinx.coroutines + Flow |
| **Network** | OkHttp (JVM) |
| **Serialization** | Jackson |
| **DI** | Manual / Koin |
| **Build** | Gradle 8.x, Kotlin 2.3.20 |

## Skills

Specialized skills provide domain expertise with bundled resources and patterns:

| Skill | Expertise | When to Use |
|-------|-----------|-------------|
| `nostr-expert` | Nostr protocol (Quartz library) | Event types, NIPs, tags, signing, Bech32, NIP-44, LargeCache |
| `kotlin-expert` | Advanced Kotlin patterns | StateFlow, sealed classes, @Immutable, DSLs, common utilities |
| `kotlin-coroutines` | Advanced async patterns | supervisorScope, callbackFlow, relay pools, testing |
| `kotlin-multiplatform` | Platform abstraction | expect/actual, source sets, sharing decisions |
| `compose-expert` | Shared UI components | Material3, state hoisting, recomposition, rich-text parsing |
| `android-expert` | Android platform | Navigation, permissions, lifecycle, Material3, Coil image loading |
| `desktop-expert` | Desktop platform | Window, MenuBar, keyboard shortcuts, DeckLayout |
| `gradle-expert` | Build system | Dependencies, versioning, packaging, optimization |
| `account-state` | `Account` + `LocalCache` | Per-user StateFlows, event store, adding account-scoped settings |
| `relay-client` | Subscriptions & filter assembly | `ComposeSubscriptionManager`, assemblers, preloaders, EOSE |
| `feed-patterns` | Feeds & DAL | `FeedFilter`, `AdditiveComplexFeedFilter`, `FeedViewModel` family |
| `auth-signers` | `NostrSigner` implementations | Local, NIP-46 bunker, NIP-55 Android external signer |
| `quartz-integration` | Quartz as an external library | Gradle setup, `NostrClient`, `KeyPair`, for external projects |
| `amy-expert` | Amy CLI (`cli/` module) | Adding `amy <verb>` commands, JSON output contract, extracting logic from `amethyst/` into `commons/` so CLI can call it |
| `find-missing-translations` | Utility | Extract untranslated Android strings |
| `find-non-lambda-logs` | Utility | Audit Log calls for lambda overloads |

## Workflow

**When you ask for a feature:**

1. **Quick skill assessment** - I identify which skills are relevant
2. **Propose which skills** - I present which skills I'll use for the task
3. **Get approval** - You review and approve (or adjust) the skill selection
4. **Review plan using approved skills** - I invoke the approved skills to create detailed implementation plan
5. **Execute with skills** - Skills collaborate to implement the feature

**Example:**
```
You: "Add video support to notes"
Me: "I'll use:
     - /nostr-expert (NIP-71 video events)
     - /compose-expert (video player UI)
     - /android-expert (platform video APIs)
     Proceed?"
You: "yes"
Me: [invokes skills to create plan]
     "Plan from skills:
      1. nostr-expert: Use NIP-71 kind 34235 for video events...
      2. compose-expert: Create VideoPlayer composable in commons...
      3. android-expert: Use ExoPlayer for Android...
      Proceed with implementation?"
You: "yes"
Me: [implements using skill guidance]
```

## Commands

- `/desktop-run` - Build and run desktop app
- `/nip <number>` - Get NIP implementation guidance

## Feature Workflow

**CRITICAL: Always check existing implementations first before creating new code!**

When picking up a new task or feature, follow this process:

### Step 0: Survey Existing Implementation (MANDATORY)

**Before writing ANY code, thoroughly audit ALL modules:**

1. **Search for existing implementations across all modules:**
   ```bash
   # Search in quartz for protocol/business logic
   grep -r "class.*Manager\|object.*Cache\|class.*Filter" quartz/src/commonMain/

   # Search in commons for UI components
   grep -r "@Composable.*Card\|@Composable.*View\|@Composable.*Dialog" commons/src/

   # Search in amethyst for Android patterns
   grep -r "class.*ViewModel\|class.*Account\|class.*State" amethyst/src/main/java/

   # Search for specific functionality
   grep -r "fun isFollowing\|fun subscribe\|fun getMetadata" {quartz,commons,amethyst}/src/
   ```

2. **Understand existing architecture patterns:**
   - Event stores and caching systems
   - State management patterns (StateFlow, mutable states)
   - ViewModel patterns and lifecycle handling
   - Filter builders and relay subscription patterns
   - UI component hierarchies

3. **Key principle:** Most logic already exists! Your job is to:
   - **Reuse** existing protocol/business logic from quartz
   - **Extract** shareable UI components AND ViewModels from amethyst to commons
   - Create **platform-specific** layouts/navigation for Desktop
   - **NOT** duplicate existing managers, caches, or state systems

4. **Document findings in implementation plan as a matrix:**

   | File/Component | Status | Location | Action |
   |----------------|--------|----------|--------|
   | FilterBuilders | ✅ Exists | quartz/relay/filters/ | Reuse as-is |
   | NoteCard | 📦 Extract | amethyst/ui/note/ → commons/ | Extract to commons |
   | HomeFeedViewModel | 📦 Extract | amethyst/ → commons/commonMain/viewmodels/ | Extract to commons |
   | ProfileCache | ⚠️ Avoid | N/A | Already in User/Account pattern |

   **Legend:**
   - ✅ **Reuse** - Exists and can be used directly
   - 📦 **Extract** - Exists in Android, needs extraction to commons
   - 🆕 **New** - Doesn't exist, needs creation (platform-specific only)
   - ⚠️ **Avoid** - Duplicate functionality, use existing pattern instead

### Step 1: Analyze Android Implementation

After surveying (Step 0), deeply examine the Android implementation:
1. Find the relevant feature/component in `amethyst/` module
2. Understand the current implementation patterns
3. Identify dependencies and integrations
4. Map out what code can be shared vs platform-specific

### Step 2: Create Implementation Plan

Before coding, create a plan that categorizes work into three buckets:

| Category | Description | Location |
|----------|-------------|----------|
| **Android-Specific** | Platform-native layouts, navigation patterns | `amethyst/`, `androidMain/` |
| **Reusable (Shared)** | Business logic, UI components, **ViewModels**, state management | `quartz/commonMain/`, `commons/commonMain/` |
| **Desktop-Specific** | Desktop-native layouts, navigation patterns, platform APIs | `desktopApp/`, `jvmMain/` |

### Step 3: Code Sharing Strategy

**Share:**
- Business logic and data models → `quartz/commonMain/`
- Major UI components (cards, lists, dialogs) → `commons/commonMain/`
- **ViewModels** (state, business logic) → `commons/commonMain/viewmodels/`
- Icons and visual assets → `commons/commonMain/`

**Keep Platform-Native:**
- **Screen composables** (layout, scaffolding) - Desktop uses `Window`, Android uses `Activity`
- Navigation patterns (sidebar vs bottom nav)
- Platform-specific interactions (gestures, keyboard shortcuts)
- System integrations (notifications, file pickers)

**Rationale:** ViewModels contain platform-agnostic state management (StateFlow/SharedFlow) and business logic. Screens consume ViewModels but render differently (Desktop sidebar + content area vs Android bottom nav).

### Step 4: Extract Shared Components

When extracting UI components:
1. Identify reusable composables in Android code
2. Move to `commons/commonMain/` (consult `/compose-expert` for patterns)
3. Create expect/actual declarations for platform-specific behavior (consult `/kotlin-multiplatform`)
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

## Code Formatting
After completing any task that modifies Kotlin files, always run:
```
./gradlew spotlessApply
```
Do this before considering the task complete.

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
