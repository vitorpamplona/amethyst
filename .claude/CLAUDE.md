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
exists. `geode` is a standalone JVM Nostr relay (Ktor) built on quartz's
relay-server code; smaller modules are `benchmark` (Android macrobenchmarks),
`relayBench` (head-to-head relay benchmark — boots geode, strfry and other
relay binaries, replays a shared deterministic corpus, measures ingest/query/
NIP-77 sync; `./relayBench/run.sh`, see `relayBench/README.md`) and
`quic-interop` (QUIC interop runner, lives at `quic/interop`). `nestsClient` runs
the audio-room protocol on top of `:quic` for the NIP-53 audio-rooms feature. It implements both IETF `draft-ietf-moq-transport-17` (under
`moq/`) and **moq-lite Lite-03** (kixelated's variant, under `moq/lite/`); the
production listener AND speaker paths both run on moq-lite to interop with the
nostrnests reference relay. The IETF code is kept as a reference + unit-test
implementation for any future IETF target; see
`nestsClient/plans/2026-04-26-moq-lite-gap.md`.

Canonical NIP specs live at <https://github.com/nostr-protocol/nips> — use
`/nip <number>` to pull a specific one (it fetches the spec file directly).

## Verify, Don't Guess

Don't assert a diagnosis you haven't reproduced. This repo gives you cheap
verification tools: `./gradlew test`, per-module test suites, and the `amy`
CLI (built partly to drive `quartz`/`commons` for interop checks). If a
claim is checkable in under a minute, check it before stating it — write
the failing case first, watch it fail, then explain.

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
├── geode/          # Standalone JVM Nostr relay (Ktor) on quartz's relay-server code
├── desktopApp/     # Desktop JVM application (layouts, navigation)
├── amethyst/       # Android app (layouts, navigation)
└── cli/            # Amy — non-interactive CLI (JVM only, no Compose)
```

**Sharing Philosophy:**
- `quartz/` = Nostr business logic, protocol, data (no UI)
- `commons/` = Shared code for every front end (Android, Desktop, iOS, and the
  headless `cli`): domain models, state holders, ViewModels, the relay client,
  shared services, **and** the Compose UI that ≥1 GUI front end renders. The
  package taxonomy, the CLI-safe / UI boundary, and a "where does my code go?"
  guide are documented in **`commons/ARCHITECTURE.md`** — read it before adding
  a new package or dropping code into `commons`.
- `quic/` = Transport library (QUIC + HTTP/3 + WebTransport); reusable for any
  KMP project that needs MoQ. Has no Android-framework dependencies.
- `nestsClient/` = MoQ + audio-rooms client; takes `:quic` as transport,
  Quartz for crypto, `MediaCodec` / `AudioRecord` / `AudioTrack` for audio.
- `amethyst/` & `desktopApp/` = Platform-native layouts and navigation
- `cli/` = Thin assembly layer over `quartz/` + `commons/` (no new logic
  allowed). May also depend on `:geode` (for `amy serve`, which embeds the
  standalone relay); never on `:amethyst` or `:desktopApp`.

**Plans per module:** design docs for new subsystems live in the owning
module's `plans/YYYY-MM-DD-<slug>.md` (e.g. `cli/plans/`, `commons/plans/`).
The global `docs/plans/` folder is frozen — don't add new plans there.

## Android Runtime Processes (IMPORTANT — the app runs in TWO processes)

The Android app is **not single-process**. Android instantiates the one `Amethyst`
Application class (there is no per-process Application in the manifest) in **both**:

- **main** — the normal app: UI, account, signer, `LocalCache`, relay client.
  `Amethyst.instance` (`AppModules`) is built here.
- **`:napplet`** — the sandboxed WebView host for NIP-5D napplets / NIP-5A nSites
  (`NappletHostActivity`, declared `android:process=":napplet"`). It holds **no**
  account or keys; `Amethyst.onCreate()` early-returns here so `Amethyst.instance`
  is **left unset** (touching it throws `UninitializedPropertyAccessException`).
  The sandbox runtime lives in its own module **`:nappletHost`** (depends only on
  `:commons` + `:quartz`, **never** `:amethyst`) so it *cannot* import
  `Amethyst`/`LocalCache`/`Account` — the broker-side (signer, gateways, registry)
  stays in `:amethyst` and the two halves talk over Messenger IPC.

Consequences — don't get caught assuming one process:

- **Processes don't share memory.** Every `object`/companion/`static` is a
  *separate copy per process*: `LocalCache` (an `object`), `NappletLaunchRegistry`,
  etc. The populated `LocalCache` lives only in **main**; the sandbox neither
  builds nor should reference it (a stray reference would lazily create a second,
  empty cache there).
- **Don't assume `Amethyst.instance` exists.** Any code reachable from `:napplet`
  (the host activity, content server, or an Application lifecycle callback like
  `onTrimMemory`) must guard on the process and never reach for `instance`.
- **Cross-process state goes over Messenger IPC**, never a shared singleton — this
  is why the broker (main) owns `NappletLaunchRegistry` and the sandbox only relays
  an opaque token. See `amethyst/plans/2026-06-22-napplet-nsite-security.md`.

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

## Feature Workflow

**CRITICAL: Check existing implementations first — most logic already exists.**
Before writing code, survey all modules (use Grep/Explore) for managers, caches,
state systems, filters, ViewModels, and composables that already do the job. Your
job is usually to **reuse** (`quartz` protocol/business logic), **extract**
(Android UI/ViewModels → `commons`), and add **platform-specific** layouts/nav —
not to duplicate existing managers, caches, or state.

Summarize the survey in your plan: for each component, note whether it's
reused as-is, extracted from `amethyst/` to `commons/`, genuinely new
(platform-specific only), or a duplicate of an existing pattern to avoid.

**Relay client ops already exist — don't hand-roll subscribe/REQ/publish loops.**
One-shot and high-level relay operations (fetch a set, fetch one, page past the
relay cap, publish-and-confirm, NIP-45 count, NIP-77 sync/reconcile) are
`INostrClient` **extension functions** in
`quartz/…/nip01Core/relay/client/accessories/` (+ `…/reqs/` for the flow/subscribe
helpers). Because they're extensions, they don't surface under "usages of
`NostrClient`" or in completion — grep that package (or read its `README.md`, which
catalogs them) before writing a new subscription/collect loop. Reuse `fetchAll`,
`fetchFirst`, `fetchAllPages`, `publishAndConfirm`, `count`, `negentropyReconcile`,
etc. instead of re-implementing them.

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

## Dependency Licensing

**MANDATORY whenever you introduce a new third-party dependency** — in *any*
module (`quartz`, `commons`, `amethyst`, `desktopApp`, `cli`, `quic`,
`nestsClient`, …), whether you add it to `gradle/libs.versions.toml` or to a
module's `build.gradle.kts`: determine its license **before** wiring it in.
Amethyst ships under the **MIT** license, so a copyleft dependency linked into a
distributed artifact (APK, desktop binary) can force that artifact's
combined-work terms onto the whole project.

Verify against the dependency's actual `LICENSE`/`COPYING` file or its published
POM — **not from memory**. Then classify and act:

- **Permissive** (MIT, Apache-2.0, BSD, ISC, MPL-2.0, zlib, …) → **OK**,
  proceed.
- **LGPL, or GPL/EPL with a linking / Classpath exception** → **WARN.**
  Acceptable to link (the exception keeps our own code MIT), but call it out in
  your summary so the human knows. Confirm the exception actually exists in the
  LICENSE text — don't assume it does.
- **Stricter than LGPL** — GPL/AGPL **without** a linking exception, SSPL,
  proprietary/commercial-only, or anything where the linking-exception check is
  "no" → **STRONGLY WARN and STOP.** Do not add it silently. Surface it
  prominently and **require an explicit call-out in the PR description** so a
  maintainer makes the decision. Prefer a permissive alternative, a clean-room
  implementation, or dropping the feature.

For any GPL-family hit the decisive question is always **"is there a linking
(LGPL/Classpath) exception?"** — that is what separates a WARN from a STOP.
(Example: TarsosDSP, GPLv3 with no exception, was removed from `amethyst` and
replaced with an in-house pitch shifter for exactly this reason.)

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
- **Prefer the `androidx.core` KTX extension over the raw platform Java call**
  when one exists — this is what Android Lint's `UseKtx` flags. Common swaps:
  `Bitmap.createBitmap(w, h, cfg)` → `createBitmap(w, h)`,
  `Bitmap.createScaledBitmap(src, w, h, f)` → `src.scale(w, h, f)`,
  `Uri.parse(s)` → `s.toUri()`, and `prefs.edit()…apply()` → `prefs.edit { }`.
  Only adopt the KTX form when it's behaviour-preserving: keep any explicit
  argument that differs from the extension's default (a non-`ARGB_8888`
  `Bitmap.Config`, `scale(filter = false)`), and leave calls the KTX has no
  equivalent for (e.g. the `createBitmap` pixels/matrix overloads, or a
  conditional-`apply()` editor loop) untouched.
- **This "prefer the KTX sugar" rule does NOT extend to collection operators.**
  The KTX preference is about platform wrappers (`Bitmap`/`Uri`/`SharedPreferences`),
  which compile to the identical call. Collections are the opposite: in hot
  event/parse paths Quartz deliberately uses raw JVM arrays (`TagArray =
  Array<Array<String>>`) and the inline `fast*` operators (`fastForEach`,
  `fastAny`, `fastFirstOrNull`, `fastFirstNotNullOfOrNull`, … in
  `nip01Core/core/TagArray.kt`) instead of Kotlin `List` + stdlib
  `forEach`/`map`/`filter`/`any` — the `fast*` variants allocate no iterator,
  no intermediate list, and no lambda object. Don't "modernize" those into
  stdlib collection calls; match the surrounding hot-path style.

### Navigation Shell
- **Desktop**: Sidebar + main content area
- **Android**: Bottom navigation

## Git Workflow

- Commits: Conventional commits (`feat:`, `fix:`, etc.)
- Never use `--no-verify`

### Remotes & pull requests

A PR can be published two ways, via two **kinds** of remote — identify them by **URL** (`git remote -v`), because the names vary per clone and **a collaborator may have only one**:

- a **GitHub** remote (`github.com/vitorpamplona/amethyst`) — the **canonical** `main`; moves constantly. Standard `gh` PR flow.
- a **git-over-nostr** remote (`nostr://…/relay.ngit.dev/amethyst`, via `ngit`) — pushing fans out to GitHub **and** the GRASP git servers; **PRs here are nostr proposals** (the `pr/feat/*` branches), reviewed on **gitworkshop.dev** — *not* GitHub PRs. (In the maintainer's checkout these happen to be named `upstream` and `origin` respectively, but don't rely on that.)

Before opening, revising, or merging a PR by **either** path, use the **`ngit-pr`** skill. It covers when to use which, identifying your remotes by URL, the `gh` and `ngit` commands, and — critically for the nostr path — the three-mains alignment gate (GitHub main vs the lagging nostr `main` vs local `main`) that its create/revise/merge flows depend on. Skipping it leads to rejected pushes and PRs that don't show up as revisions.
