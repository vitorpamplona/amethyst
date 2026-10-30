# Amethyst

## Project Overview

Amethyst is a Nostr Client for Android that was made for Android-only and has been slowly switching
over to a Kotlin Multiplatform project. The main modules are: `commons`, `commonsUI`, `amethyst`,
`desktopApp`, `cli`, plus the audio-rooms client `nestsClient`. The protocol code is **not** in this
repo: Nostr, MLS, the two MLS bindings, QUIC and the crypto primitives are libraries in their own
repos, consumed from Maven Central (see [Libraries](#libraries-their-own-repos)). Commons stores
shared code between Amethyst Android (`amethyst`) and Amethyst Desktop (`desktopApp`). Android now
also ships on laptops, so the direction is **one UI**: every screen and the navigation shell at every
window size move to `commonsUI`, `amethyst` shrinks to an Android shim, and `desktopApp` is a JVM
shim that renders the same UI (`desktopApp/…/desktop/app/`, see
`commons/plans/2026-09-27-one-ui-android-desktop.md`). The legacy desktop app (its own screens,
deck and navigation) is gone: its desktop-only features were rebuilt in the shared UI or dropped
(`commons/plans/2026-10-09-legacy-desktop-retirement.md`). `cli` ships `amy`,
a non-interactive JVM command-line client that drives the same `quartz` + `commons` code — used by
humans, agents, and interop tests. `geode` is a standalone JVM Nostr relay (Ktor) built on quartz's
relay-server code; smaller modules are `benchmark` (Android macrobenchmarks) and
`relayBench` (head-to-head relay benchmark — boots geode, strfry and other
relay binaries, replays a shared deterministic corpus, measures ingest/query/
NIP-77 sync; `./relayBench/run.sh`, see `relayBench/README.md`). `nestsClient` runs
the audio-room protocol on top of quic-kmp for the NIP-53 audio-rooms feature. It implements both IETF `draft-ietf-moq-transport-17` (under
`moq/`) and **moq-lite Lite-03** (kixelated's variant, under `moq/lite/`); the
production listener AND speaker paths both run on moq-lite to interop with the
nostrnests reference relay. The IETF code is kept as a reference + unit-test
implementation for any future IETF target; see
`nestsClient/plans/2026-04-26-moq-lite-gap.md`.

## Libraries (their own repos)

On Maven Central; `-P<name>Dir=../<repo>` builds a local checkout instead.

| Repo (`libs.*`) | Holds | `-P…Dir` |
|---|---|---|
| quartz (`quartz`) | Nostr: NIPs (`nipXX`), protocol families (`contextvm/`, `concord/`, …), signers, relay client + accessories, relay-server code, secp256k1, **Marmot's event classes** (`quartz.marmot`) | `quartzDir` |
| crypto-kmp (`crypto`) | SHA, HMAC, HKDF, AES, ChaCha20-Poly1305, X25519, Ed25519, random. No Nostr | `cryptoKmpDir` |
| mls-kmp (`mls.kmp`) | The RFC 9420 engine. No Nostr, no binding | `mlsKmpDir` |
| marmot-kmp (`marmot`, `marmot.quic`) | Marmot logic needing MLS state, `MarmotManager`, store interfaces, agent-stream codecs + raw-QUIC transport, MDK bench | `marmotKmpDir` |
| cordn-kmp (`cordn`) | cordn specs, `CordnGroupManager`, `CordnSyncLoop`, the registry, store interfaces | `cordnKmpDir` |
| quic-kmp (`quic`) | QUIC v1/v2 client and server, TLS 1.3, HTTP/3, QPACK, WebTransport (JVM, Android) | `quicKmpDir` |
| kiko-kmp (`kiko`) | The 128x128 fantasy-console cart runtime (Lua dialect, graphics, sound, `.p8.png`) behind the note cart player. No Nostr, stdlib only | `kikoKmpDir` |

Dependencies: crypto-kmp ← quartz, mls-kmp, quic-kmp; quartz + mls-kmp ← marmot-kmp, cordn-kmp
(never each other); quic-kmp ← `nestsClient`, marmot-quic; kiko-kmp ← `commons` (the cart player).
Quartz depends on no MLS.

**What goes where.** If another client of that protocol (`amy`, White Noise, a bot) would need the
code, it goes in a library. Signed Nostr kinds, tags, codecs → quartz. Primitives → crypto-kmp. RFC
9420 → mls-kmp. A binding's rules, MLS state or not → its repo: group operations, and the shape and
validation of the unsigned events members send inside a group (edits, polls, emoji, reports). QUIC →
quic-kmp; MoQ → `nestsClient`. Running a cart (Lua, graphics, sound, cart formats) → kiko-kmp; finding
carts in notes, the player screen and saves stay here. A test or vector of one library → that library. **Stays here:** at-rest
stores, account wiring (relays, signer, LocalCache; not protocol steps inside `AccountMarmotActions`
or `CordnRuntime`), chatrooms, feeds, UI, `amy` verbs, tests needing both bindings.

**Changing one:** edit its repo (own `AGENTS.md`), build here with its `-P…Dir`, release, then bump
`gradle/libs.versions.toml` (keep `cryptoKmp`/`mlsKmp` at what quartz and the bindings use). A
`quartz/…` path in these docs names a file in that repo.

Canonical NIP specs live at <https://github.com/nostr-protocol/nips> — fetch
`<NN>.md` from there for a specific one (quartz's `nostr-expert` skill shows how to map
it to Quartz).

## Verify, Don't Guess

Don't assert a diagnosis you haven't reproduced. This repo gives you cheap
verification tools: `./gradlew test`, per-module test suites, and the `amy`
CLI (built partly to drive `quartz`/`commons` for interop checks). If a
claim is checkable in under a minute, check it before stating it — write
the failing case first, watch it fail, then explain.

### Run the app before calling a UI change done

Compiling and unit tests don't show a screen that crashes on open, a missing
string or a collapsed layout. The desktop app renders the **same shared UI**
as Android (`AmethystApp` from `commonsUI`: same screens, shell and feeds), so
running it on the JVM checks the shared code path without a phone. Any change
under `commonsUI`, `commons` or `desktopApp` that a user can see gets this
check before you report it done:

```bash
tools/desktop-run/run-headless.sh start   # Xvfb + :desktopApp:run, waits for paint, screenshots
# open the screenshot it names, click/type/screenshot with tools/desktop-run/Drive.java
tools/desktop-run/run-headless.sh logs    # exceptions first
tools/desktop-run/run-headless.sh stop    # before any rebuild
```

It works in a headless container (Xvfb is enough; no xdotool, no window
manager). Log in with a public `npub` for a read-only session, and pick the
screen size to see the phone (bottom bar) or wide (drawer) shell.
`tools/desktop-run/README.md` has the driver commands, accounts, expected
noise and traps, and `desktop-expert` covers the rest. Say in your report what you
looked at; if you could not run it, say that instead of implying it works.
Android-only code (`amethyst/`) still needs a device or emulator.

## Architecture

```
amethyst/
├── commons/        # Shared HEADLESS layer (models, state, ViewModels, relay client) — CLI-safe
│   └── src/
│       ├── commonMain/    # Domain models, state holders, ViewModels, services
│       ├── jvmAndroid/    # JVM-bound services shared by Android + Desktop
│       ├── androidMain/   # Android-specific actuals (Keystore, DataStore)
│       └── jvmMain/       # Desktop-specific actuals (keyring, upload pipeline)
├── commonsUI/      # Shared Compose UI on top of commons (composables, icons, theme, Coil, resources)
│   └── src/
│       ├── commonMain/    # Shared composables, icons, theme, composeResources (strings/fonts)
│       ├── jvmAndroid/    # Markdown renderer, Coil OkHttp fetchers
│       ├── androidMain/   # Android Coil bridge
│       └── jvmMain/       # Desktop Coil bridge (+ skikoMain shared with iOS)
├── nestsClient/    # Audio-room client (production runs on moq-lite; IETF MoQ kept as reference)
│   └── src/
│       ├── commonMain/    # MoQ session, NestsListener, audio glue
│       └── jvmAndroid/    # Opus encode/decode, AudioRecord/AudioTrack
├── geode/          # Standalone JVM Nostr relay (Ktor) on quartz's relay-server code
├── relayBench/     # geode vs strfry and other relays, head to head
├── desktopApp/     # Desktop JVM application (layouts, navigation)
├── amethyst/       # Android app (layouts, navigation)
└── cli/            # Amy — non-interactive CLI (JVM only, no Compose)
```

**Sharing Philosophy:**
- The libraries above = protocol, crypto and headless group clients (no UI, no app state,
  no platform storage)
- `commons/` = Shared **headless** code for every front end (Android, Desktop,
  iOS, and the headless `cli`): domain models, state holders, ViewModels, the
  relay client, shared services. It may use the Compose *runtime*
  (`@Stable`/`@Immutable`, snapshot state) but never Compose UI, Coil or
  Compose resources — the build enforces this: `commons` has no such deps.
- `commonsUI/` = Shared **Compose UI** that ≥1 GUI front end renders
  (composables, `ui/theme`, icons, robohash, Coil fetchers, markdown, the
  `composeResources` strings/fonts and the generated `Res` class). Depends on
  `commons` (as `api`); `cli` never depends on it. Files keep their
  `com.vitorpamplona.amethyst.commons.*` packages — the split is a module
  boundary, not a package rename. The package taxonomy, the CLI-safe / UI
  boundary, and a "where does my code go?" guide are documented in
  **`commons/ARCHITECTURE.md`** (+ `commonsUI/ARCHITECTURE.md`) — read them
  before adding a new package or dropping code into either module.
- `nestsClient/` = MoQ + audio-rooms client; takes quic-kmp (`libs.quic`) as transport,
  Quartz for crypto, `MediaCodec` / `AudioRecord` / `AudioTrack` for audio.
- `amethyst/` & `desktopApp/` = Platform shims: process/window entry points, services, system
  integrations and the actuals of shared ports. Screens and navigation are shared UI and are
  moving to `commonsUI`.
- `cli/` = Thin assembly layer over `quartz/` + `commons/` (no new logic
  allowed). May also depend on `:geode` (for `amy serve`, which embeds the
  standalone relay); never on `:commonsUI`, `:amethyst` or `:desktopApp`.

**Plans per module:** design docs for new subsystems live in the owning
module's `plans/YYYY-MM-DD-<slug>.md` (e.g. `cli/plans/`, `commons/plans/`).
The global `docs/plans/` folder is frozen — don't add new plans there.

## Android Runtime Processes (IMPORTANT — the app runs in TWO processes)

The one `Amethyst` Application class runs in **main** (UI, account, signer, `LocalCache`,
relays; builds `Amethyst.instance`) and in **`:napplet`**, the sandboxed WebView host for
NIP-5D napplets / NIP-5A nSites, which holds no account or keys and leaves `Amethyst.instance`
**unset** (touching it throws). The sandbox runtime is the `:nappletHost` module, which never
depends on `:amethyst`. So: every `object` is a separate copy per process (`LocalCache` is
populated only in main), code reachable from `:napplet` (including Application callbacks like
`onTrimMemory`) must guard on the process, and cross-process state goes over Messenger IPC,
never a shared singleton. Details: `.claude/skills/android-expert/references/two-processes.md`.

## Tech Stack

Exact versions live in `gradle/libs.versions.toml` (the source of truth — check
there rather than trusting a number copied here).

| Layer | Technology |
|-------|------------|
| **Core** | Quartz (Nostr KMP) |
| **UI** | Compose Multiplatform |
| **Async** | kotlinx.coroutines + Flow |
| **Network** | OkHttp (JVM) |
| **Serialization** | kotlinx.serialization; Nostr JSON uses quartz's Kite (no Jackson) |
| **DI** | Manual / Koin |
| **Build** | Gradle + Kotlin Multiplatform |

## Skills

Skills live once, in `.claude/skills/<name>/SKILL.md`; `.agents/skills/<name>` is a
symlink to the same directory so Codex reads the identical file. Each tool shows
the model a list of skills with their descriptions, so the list isn't duplicated
here. Claude Code lists every description. Codex's list is bounded (at most 2% of
the context window, or 8,000 characters when the window is unknown, shared with
your user-level skills): past that it shortens descriptions first and may leave
skills out of the list. If a skill you need isn't in the list, open
`.agents/skills/<name>/SKILL.md` directly. Load the focused skill for the area you
are changing. They cover the repo-specific knowledge only (where things live,
house conventions, traps), not general Kotlin, Compose, Android or Nostr:
`account-state`, `amy-expert`, `android-expert`, `auth-signers`, `compose-expert`,
`desktop-expert`, `find-missing-translations`,
`kotlin-coroutines`, `kotlin-multiplatform`, `ngit-pr`, `nip85-trusted-assertions`,
`relay-client`. Protocol skills (`nostr-expert`, `event-store-semantics`) live in the
quartz repo with the code they describe.

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

- **Share** → quartz's `commonMain` (business logic, data models, protocol; a change
  in the quartz repo),
  `commons/commonMain/` (**ViewModels** — cross-feature ones under
  `viewmodels/`, a feature's own in `<feature>/` — plus state holders, relay
  client, services — headless) and `commonsUI/commonMain/` (major UI
  components, icons, theme). ViewModels are platform-agnostic state + logic
  (StateFlow/SharedFlow), so they belong in `commons`, **never under a `ui.*`
  package**. A ViewModel goes in `commonsUI` only when it can't compile
  headless: it holds Compose UI state (`TextFieldValue`, `LazyListState`),
  reads `Res`, or needs `AccountViewModel.toastManager`. The account ViewModel
  is split: `BaseAccountViewModel` (`commons`) holds every member the screens
  call and reaches the platform through `AccountViewModelHost`;
  `AccountViewModel` (`commonsUI`) only adds the `ToastManager` and the
  platform bridges. A ViewModel takes `Account` when it only needs the account,
  `BaseAccountViewModel` when it needs `launchSigner` and friends. To tell the
  user something from headless code, report a typed `UserNotice`
  (`commons.notices`) to `notices`, never a `Res` string; `commonsUI` words it
  in `notices/ui/UserNoticeText.kt`. Anything that imports
  `androidx.compose.ui`/`foundation`/`material3`, Coil, or `Res` belongs in
  `commonsUI`.
  **Screens and navigation are shared too**: screen composables, the nav host,
  and the navigation chrome for every window size (bottom bar, rail, permanent
  drawer) belong in `commonsUI`, because Android runs on laptops and the new
  Desktop app renders the same UI. Adapt to the window with `ScreenLayoutSpec`
  / `LocalScreenLayout`, not with a per-platform screen.
- **Keep native** → only what is the platform itself: the process/window entry
  point (Android `Activity`/`Service`, Desktop `Window`/tray/menu bar),
  system integrations (notifications, file pickers, share sheets, camera,
  media3, WebView, keyring/Keystore), and the platform `actual`s or port
  implementations the shared UI calls. A new screen goes in `commonsUI` when
  its dependencies allow. A
  screen that still needs an app-only helper may live in `amethyst/`, but keep
  Android APIs out of it (behind a port or slot) so it can move later.

When extracting a composable: move it to `commonsUI/commonMain/` (see
`/compose-expert`), add expect/actual for any platform behavior (see
`/kotlin-multiplatform`), then point both Android and Desktop at the shared
version. Quartz is protocol-only — no composables.

## Build Commands

```bash
# Run desktop app (the shared UI)
./gradlew :desktopApp:run
# ...headless, for agents: virtual display, screenshots, Robot driver
tools/desktop-run/run-headless.sh start

# Run Android app
./gradlew :amethyst:installDebug

# Build against a local quartz checkout (../quartz) instead of the published artifact.
# Switching -PquartzDir on or off changes every module's classpath: stop the daemons first
# (`./gradlew --stop` leaves the Kotlin daemon running), or one daemon ends up holding both.
./gradlew -PquartzDir=../quartz :commons:jvmTest
./gradlew --stop; pkill -f '[o]rg.jetbrains.kotlin.daemon.KotlinCompileDaemon'

# Run the JVM tests of every module, KMP ones included. KMP modules register no
# `test` task of their own; the root build aliases it onto their jvmTest.
./gradlew test

# A single module. For a KMP module name jvmTest directly:
./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.model.*"

# NOT covered by `test`: each KMP module's OTHER targets (androidHostTest, the
# native targets). CI names the ones it runs, e.g. :commonsUI:testAndroidHostTest.

# Format code
./gradlew spotlessApply
```

## Dependency Licensing

**MANDATORY whenever you introduce a new third-party dependency** — in *any*
module (`commons`, `commonsUI`, `amethyst`, `desktopApp`, `cli`,
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

## Quartz

App modules declare the secp256k1 JNI variant they ship (`-jni-android`, `-jni-jvm`), at quartz's
version. Quartz client tests needing a socket relay (`KtorRelay`) live in `geode/src/test`.

## Strings

**MANDATORY: every new user-visible string goes in `commonsUI` Compose resources**,
`commonsUI/src/commonMain/composeResources/values/strings.xml` — **never** in
`amethyst/src/main/res/values/strings.xml`, even for an Android-only screen. A
screen cannot move to `commonsUI` (or be rendered by Desktop) while its labels
live in Android `res/`, and every key added there has to be migrated back out
later. (The 2026-09-22 move emptied Android `res/` down to ~190 keys; the next two
features put 447 back.)

- Reference it as `Res.string.my_key` / `Res.plurals.my_key`, importing
  `com.vitorpamplona.amethyst.commons.resources.Res` **and** the per-key accessor
  `com.vitorpamplona.amethyst.commons.resources.my_key` (Compose resources generates
  each key as an extension property).
- Read it with `stringRes(Res.string.x)` / `pluralStringRes(Res.plurals.x, n, …)`
  in composition — the app-side `ui.stringRes` overloads delegate to the
  commonsUI bridge (`commons/ui/StringRes.kt`), so one import serves both kinds.
- Outside composition there is **no blocking accessor**: use `loadStringRes(…)`
  from a coroutine (or make the function `suspend`), or resolve the label in
  composition and pass it down as a `String`. A `runBlocking` bridge is ruled out.
- Hold a string reference as `StringResource` / `PluralStringResource`, not a
  `@StringRes Int`.
- Format arguments must be positional (`%1$s`, `%2$d`) — compose-resources does
  not format bare `%s`/`%d`.
- Escaping is not Android's: write `'` and `"` bare (no `\'`), and never wrap a
  value in quotes to keep its whitespace. `tools/agent-hooks/compose_escaping_check.py`
  checks this before a push.

The **only** strings that stay in Android `res/` are the ones a platform API
reads synchronously under a deadline, where a `suspend` read cannot run: a
foreground service's `startForeground` notification, a notification channel
created during service startup, PiP `RemoteAction`s, napplet capability labels
read in the sandbox process, and `AndroidManifest.xml` / `res/xml` references.
Most of those are *copies* of a key that also lives in `commonsUI`. See
`commons/plans/2026-09-22-strings-to-compose-resources.md` ("the
synchronous-platform tier"). If you think a new string belongs in that tier,
say why in the PR.

To move keys that already landed in Android `res/`, use
`tools/strings-migrate/migrate.py <keys…>`. It moves every locale byte-for-byte,
so Crowdin sees a pure move. Then repoint `R.string.x` → `Res.string.x`.

## Icons

The Material Symbols font bundled at
`commonsUI/src/commonMain/composeResources/font/material_symbols_outlined.ttf`
is a **subset** that only contains the glyphs referenced from
`commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/icons/symbols/MaterialSymbols.kt`.

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
regenerating.

### Amethyst's own icons are also a font

The icons in `commonsUI/.../commons/icons/*.kt` (Like, Reply, Reposted, Zap, …) are
**also** compiled into a font, `composeResources/font/amethyst_icons.ttf`, and drawn
as glyphs via `AmethystIconGlyph`. Drawing an `ImageVector` rasterises its paths into
a per-instance cached layer, so a feed re-rasterised the same glyph once per card;
a glyph is a blit from the shared text atlas. Measured: frame P90 **-10.7%**,
overrun P90 **-17.4%** on the feed scroll benchmark.

**MANDATORY:** whenever you add or change an icon under `commonsUI/.../commons/icons/`,
regenerate the font *and* its codepoint table together:

```bash
python3 tools/icon-font/build_icon_font.py \
  commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/icons \
  commonsUI/src/commonMain/composeResources/font/amethyst_icons.ttf \
  commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/icons/symbols/AmethystIcons.kt
```

Both outputs must be committed together: codepoints are assigned in filename order,
so adding an icon renumbers the ones after it, and a stale `AmethystIcons.kt` then
points at the wrong glyph. Needs `fonttools` (`pip install fonttools`). The script
prints any icon it could not convert — an icon that is skipped must keep using its
`ImageVector`.
 See `tools/material-symbols-subset/README.md` for details and
prerequisites (`pip install fonttools brotli`).

## Code Formatting
After completing any task that modifies Kotlin files, always run:
```
./gradlew spotlessApply
```
Do this before considering the task complete.

### Kotlin Style

- **Log through Quartz's `Log`** (`quartz/…/utils/Log.kt`), never `android.util.Log`.
  Interpolated messages use the lambda overload (`Log.d(TAG) { "…$x" }`) so they cost
  nothing below `Log.minLevel`; in a catch block pass the throwable
  (`Log.w(TAG, "…", e)`) instead of interpolating `${e.message}`.
- **Act on the `Boolean` from `File.delete()`, `mkdirs()`, `renameTo()` and
  `setExecutable()`/`setWritable()`**: they fail by returning `false`, not by throwing,
  and Sonar flags an ignored result (`kotlin:S899`). Throw if the step is required,
  else log: `if (!f.delete() && f.exists()) Log.w(TAG) { "could not delete $f" }`.
- **Never write fully-qualified class names inline in function bodies**: import the
  class and use its simple name (`Event`, not `com.vitorpamplona.quartz...Event`).
  The only exceptions are a genuine name collision (prefer `import ... as Alias`) or
  where the language requires it; comments, KDoc and string literals are exempt.
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
- **Never put raw invisible/bidirectional Unicode characters in source files**
  — write them as `\uXXXX` escapes instead (`'\u202E'`, `Regex("[\u200B-\u200D\uFEFF]")`).
  This covers the bidi family Sonar's Trojan-Source rule (CVE-2021-42574)
  flags — U+202A–U+202E, the isolates U+2066–U+2069, U+200E/U+200F, U+061C —
  plus zero-width characters (U+200B–U+200D, U+FEFF, U+2060). The escape
  compiles to the same codepoint; the point is a visually unambiguous file. Applies even when the
  character is *intentional* (sanitizer strip-lists, adversarial test
  payloads) — that's data, and escapes express it just as well. Exceptions:
  U+200D as part of a real emoji ZWJ sequence in test data (👩‍👧 — functional,
  not a bidi control), and LRM/RLM inside Crowdin-managed `strings.xml`
  translations (legitimate RTL typography; don't touch those files by hand
  anyway). Note the tooling trap: the Edit tool may normalise a typed
  `\uXXXX` back into the raw character — if that happens, do the replacement
  at byte level (`perl -CSD -pe 's/\x{202E}/\\u202E/g'`).

### Navigation Shell
One shell, picked by window size rather than by platform: `ScreenLayoutSpec`
chooses the bottom bar (compact), the rail, or the permanent drawer (wide,
landscape, tall enough), and docks the notification panel on very wide windows.
It lives in `commonsUI` (`AppShellLayout`, run by `AmethystApp`), and both Android
and the desktop window use it. The opt-in deck (Settings › App › Deck) adds columns beside the
main screen on windows of 1,200 dp and up (`DeckArea`).

## Git Workflow

- Commits: Conventional commits (`feat:`, `fix:`, etc.)
- Never use `--no-verify`
- The git pre-push hook (`.git-hooks/pre-push`) runs the tests, then Android lint
  (`:amethyst:lintPlayDebug`). With `PREPUSH_KMP_METADATA=1` it also
  compiles the shared code like CI's `lint` job, catching JVM-only APIs in
  `commonMain`/`skikoMain` that the JVM and Android builds accept. `.git/hooks/` is a
  copy: run `./gradlew installGitHook` after a fresh clone or a hook change. Before
  `ngit send` or any merge run `PREPUSH_KMP_METADATA=1 bash .git-hooks/pre-push` by
  hand. Merge only when the PR's CI is green. See the `ngit-pr` skill.
- Never open a PR (GitHub or nostr proposal) on your own. Open one only when
  the human you work for has reviewed the diff, tested the app by hand on a
  device, and asks you to. Until then, hand them the branch and a test plan.
  See `CONTRIBUTING-WITH-AI.md`.
- Keep signers/key storage, release workflows/signing, and NIP interpretation
  changes within the maintainer-approved scope that `CONTRIBUTING-WITH-AI.md`
  describes.

### Remotes & pull requests

A PR can go out through GitHub (`gh`) or as a nostr proposal through the
git-over-nostr remote (`ngit`, reviewed on gitworkshop.dev). Remote names vary per
clone and a collaborator may have only one, so identify them by URL. Before
opening, revising or merging a PR by either path, use the **`ngit-pr`** skill: it
has the remote table, both command sets and the three-mains alignment gate the
nostr path depends on.

## Agent Configuration (Claude Code + Codex)

Both agents read the same rules, skills and checks. Keep it that way:

- **This file is the single source of rules.** Codex reads `AGENTS.md` directly;
  Claude Code reads `.claude/CLAUDE.md`, which imports it with `@../AGENTS.md` and
  adds only Claude-specific notes. A directory with its own guidance gets an
  `AGENTS.md`, plus a `CLAUDE.md` that is just `@AGENTS.md`. Never write a rule in a
  `CLAUDE.md`.
- **Skills are written once**, in `.claude/skills/<name>/`, and symlinked from
  `.agents/skills/<name>`. Never copy or fork a skill into `.agents/`.
- **A skill's `description` is how both agents decide to load it.** Keep the
  concrete class names, paths, commands and "NOT for …" scope it carries; a
  generic one-liner stops the skill from triggering. Stay under 1,024 characters.
  Put the strongest triggers (the module, class or command names) in the first
  sentence and keep it to about 250 characters.
- **The skill list has a budget.** Codex shows the model at most 2% of the context
  window, or 8,000 characters when the window is unknown, of names, descriptions
  and paths, shared with the user's own skills; past that it shortens descriptions
  and then drops skills. So a skill must earn its place: repo-specific knowledge
  only, no textbook material, and a new topic goes into an existing skill's
  `references/` before it becomes a new skill.
- **Shared checks live in `tools/agent-hooks/`.** `.claude/settings.json` and
  `.codex/hooks.json` both run the same pre-push scripts there (spotless, orphaned
  translations, Compose escaping), and CI runs the same Python checks.
- `tools/agent-hooks/agent_config_check.py` enforces the above (symlinks, imports,
  descriptions, their combined size, and that every path a skill cites exists; a
  `quartz/…` path is checked against `../quartz` when that checkout exists).
  It runs before every push and in CI.
