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
- `commons/` = Shared UI components, icons, composables, **ViewModels**
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

## Skills

Specialized skills provide domain expertise with bundled resources and patterns:

| Skill | Expertise | When to Use |
|-------|-----------|-------------|
| `nostr-expert` | Nostr protocol (Quartz library) | Event types, NIPs, tags, signing, Bech32 |
| `kotlin-expert` | Advanced Kotlin patterns | StateFlow, sealed classes, @Immutable, DSLs |
| `kotlin-coroutines` | Advanced async patterns | supervisorScope, callbackFlow, relay pools, testing |
| `kotlin-multiplatform` | Platform abstraction | expect/actual, source sets, sharing decisions |
| `compose-expert` | Shared UI components | Material3, state hoisting, recomposition |
| `android-expert` | Android platform | Navigation, permissions, lifecycle, Material3 |
| `desktop-expert` | Desktop platform | Window, MenuBar, Tray, keyboard shortcuts |
| `gradle-expert` | Build system | Dependencies, versioning, packaging, optimization |

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
