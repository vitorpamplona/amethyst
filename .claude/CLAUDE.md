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
exists. `nestsClient` runs the audio-room protocol on top of `:quic` for the NIP-53
audio-rooms feature. It implements both IETF `draft-ietf-moq-transport-17` (under
`moq/`) and **moq-lite Lite-03** (kixelated's variant, under `moq/lite/`); the
production listener AND speaker paths both run on moq-lite to interop with the
nostrnests reference relay. The IETF code is kept as a reference + unit-test
implementation for any future IETF target; see
`nestsClient/plans/2026-04-26-moq-lite-gap.md`.

Canonical NIP specs live at <https://github.com/nostr-protocol/nips> — use
`/nip <number>` to pull a specific one (it fetches the spec file directly).

## Verify, Don't Guess (standing instruction)

A plausible-sounding explanation is cheap; being right is not. Before
asserting what a problem is or how something behaves:

1. **State hypotheses as hypotheses.** If you haven't run it, say "I'm
   guessing" or "haven't verified" — never dress an untested guess up as a
   diagnosis. Use "I verified X by running Y" only when you actually did.
2. **Reproduce before diagnosing.** If a claim is checkable in under a
   minute, check it before stating it. This repo gives you the means:
   `./gradlew test`, the per-module tests, and `amy` (the CLI exists partly
   to drive `quartz`/`commons` for interop checks). Write the failing case
   first, watch it fail, *then* explain. For non-trivial bugs use `/bugfix`
   (reproduce-first) or `/investigate` (competing hypotheses + refutation).
3. **Predict, then run.** Before running a command, state the output you
   expect. A mismatch is the cheapest signal that your model is wrong.
4. **Don't commit to one cause.** A single immediate explanation stops you
   from looking. Hold 2–3 candidates and a discriminating test for each.

If you find yourself writing paragraphs to defend a theory, that effort
almost always should have been one test.

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
├── nestsClient/    # Audio-room client (production runs on moq-lite; IETF MoQ kept as reference)
│   └── src/
│       ├── commonMain/    # MoQ session, NestsListener, audio glue
│       └── jvmAndroid/    # Opus encode/decode, AudioRecord/AudioTrack
├── desktopApp/     # Desktop JVM application (layouts, navigation)
├── amethyst/       # Android app (layouts, navigation)
└── cli/            # Amy — non-interactive CLI (JVM only, no Compose)
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

Exact versions live in `gradle/libs.versions.toml` (the source of truth — check
there rather than trusting a number copied here).

| Layer | Technology |
|-------|------------|
| **Core** | Quartz (Nostr KMP) |
| **UI** | Compose Multiplatform |
| **Async** | kotlinx.coroutines + Flow |
| **Network** | OkHttp (JVM) |
| **Serialization** | Jackson |
| **DI** | Manual / Koin |
| **Build** | Gradle + Kotlin Multiplatform |

## Skills

The full list of available skills (with descriptions and triggers) is injected
into every session, so it isn't duplicated here. Two kinds exist and are meant
to be used together:

- **Codebase-oriented** skills (`nostr-expert`, `compose-expert`, `feed-patterns`,
  `account-state`, `amy-expert`, …) answer "where is X in Amethyst, what pattern
  do we use here."
- **Technique-oriented** skills (vendored from `chrisbanes/skills`, e.g.
  `compose-slot-api-pattern`, `kotlin-flow-state-event-modeling`) answer "what is
  the correct Compose/Kotlin design." They complement, not replace, the codebase
  skills: `compose-expert` tells you where shared composables live;
  `compose-slot-api-pattern` tells you how to shape their public API.

## Workflow

**When you ask for a feature:**

1. **Quick skill assessment** - I identify which skills are relevant
2. **Propose which skills** - I present which skills I'll use for the task
3. **Get approval** - You review and approve (or adjust) the skill selection
4. **Review plan using approved skills** - I invoke the approved skills to create detailed implementation plan
5. **Execute with skills** - Skills collaborate to implement the feature

## Feature Workflow

**CRITICAL: Check existing implementations first — most logic already exists.**
Before writing code, survey all modules (use Grep/Explore) for managers, caches,
state systems, filters, ViewModels, and composables that already do the job. Your
job is usually to **reuse** (`quartz` protocol/business logic), **extract**
(Android UI/ViewModels → `commons`), and add **platform-specific** layouts/nav —
not to duplicate existing managers, caches, or state.

Capture the survey as a matrix in your plan:

| File/Component | Status | Location | Action |
|----------------|--------|----------|--------|
| FilterBuilders | ✅ Reuse | quartz/relay/filters/ | Use as-is |
| NoteCard | 📦 Extract | amethyst/ui/note/ → commons/ | Extract to commons |
| ProfileCache | ⚠️ Avoid | N/A | Already in User/Account pattern |

**Legend:** ✅ Reuse (exists, use directly) · 📦 Extract (exists in Android, move
to `commons`) · 🆕 New (doesn't exist — platform-specific only) · ⚠️ Avoid
(duplicate; use existing pattern).

**Share vs keep platform-native:**

- **Share** → `quartz/commonMain/` (business logic, data models, protocol) and
  `commons/commonMain/` (major UI components, **ViewModels** under
  `viewmodels/`, icons). ViewModels are platform-agnostic state + logic
  (StateFlow/SharedFlow), so they belong in `commons`.
- **Keep native** → screen composables/scaffolding (Desktop `Window` vs Android
  `Activity`), navigation (sidebar vs bottom nav), platform interactions
  (gestures, keyboard shortcuts), system integrations (notifications, file
  pickers).

When extracting a composable: move it to `commons/commonMain/` (see
`/compose-expert`), add expect/actual for any platform behavior (see
`/kotlin-multiplatform`), then point both Android and Desktop at the shared
version. `quartz/` is protocol-only — no composables.

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

Quartz uses expect/actual for platform-specific implementations (e.g. crypto
backed by `secp256k1-kmp-jni-android` on Android and `secp256k1-kmp-jni-jvm` on
JVM). See `/kotlin-multiplatform` for the expect/actual and source-set patterns.

## Icons

The Material Symbols font bundled at
`commons/src/commonMain/composeResources/font/material_symbols_outlined.ttf`
is a **subset** that only contains the glyphs referenced from
`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/icons/symbols/MaterialSymbols.kt`.

**MANDATORY:** Whenever you add a new icon — i.e. introduce a
`MaterialSymbol("\uXXXX")` codepoint that wasn't already referenced anywhere in
`MaterialSymbols.kt` — you MUST regenerate the subset font by running:

```bash
./tools/material-symbols-subset/subset.sh
```

Commit the regenerated `material_symbols_outlined.ttf` alongside your
`MaterialSymbols.kt` change. Without this step the new icon renders as tofu (□)
at runtime because the glyph is not in the bundled font.

Reusing a codepoint already present in `MaterialSymbols.kt` does NOT require
regenerating. See `tools/material-symbols-subset/README.md` for details and
prerequisites (`pip install fonttools brotli`).

## Code Formatting
After completing any task that modifies Kotlin files, always run:
```
./gradlew spotlessApply
```
Do this before considering the task complete.

### Kotlin Style

- **Never write fully-qualified class names inline in function bodies.** Add an
  `import` for the class and reference it by its simple name. Write
  `Event` (with `import com.vitorpamplona.quartz...Event`), not
  `com.vitorpamplona.quartz...Event` in the middle of code.
- The only acceptable inline fully-qualified names are: a genuine name
  collision (prefer `import ... as Alias` instead), or where the language
  requires it. Comments, KDoc, and string literals are exempt.

### Navigation Shell
- **Desktop**: Sidebar + main content area
- **Android**: Bottom navigation

## Git Workflow

- Commits: Conventional commits (`feat:`, `fix:`, etc.)
- Never use `--no-verify`
