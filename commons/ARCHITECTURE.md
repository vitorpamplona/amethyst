# `commons` — Shared Module Architecture & Goals

`commons` is the **shared layer** between every Amethyst front end:

| Consumer       | Kind                         | Uses from `commons`                          |
|----------------|------------------------------|----------------------------------------------|
| `amethyst`     | Android app (touch-first)    | everything (models, state, ViewModels) + `commonsUI` |
| `desktopApp`   | Desktop JVM app (mouse-first)| everything (models, state, ViewModels) + `commonsUI` |
| `cli` (`amy`)  | Headless JVM CLI (no UI)     | everything — `commons` is headless by construction; it never sees `commonsUI` |
| `nappletHost`  | Android WebView sandbox      | napplet contract + `commonsUI` (for the shell/shim Compose resources) |
| iOS (future)   | iOS app                      | everything + `commonsUI`; expected to share most UI with Android |

`commons` sits **above** `quartz` (the protocol-only Nostr KMP library) and
**below** the apps. The split between the three is:

- **`quartz/`** — Nostr protocol: events, NIPs, crypto, relay framing. No app
  state, no UI, no caches of "what this user follows."
- **`commons/`** — everything an Amethyst *client* needs that isn't a
  platform-native screen, navigation shell, **or Compose UI**: domain models
  (`Note`, `User`), in-memory state holders, ViewModels, the relay-subscription
  client, shared business services.
- **`commonsUI/`** — the Compose UI components that more than one front end
  renders, plus everything only they need (icons, theme, Coil fetchers,
  markdown, the `composeResources` strings/fonts and the generated `Res`).
  Depends on `commons` as `api`. See `commonsUI/ARCHITECTURE.md`.
- **`amethyst/` & `desktopApp/`** — platform-native screens, navigation
  (bottom-nav vs sidebar), gestures, system integration. They assemble
  `commons` pieces; they should not re-implement them.

> The goal is **"write it once in `commons`"**. Before adding a manager, cache,
> filter, ViewModel, or composable to an app module, check whether it already
> exists here or belongs here. See the "Where does my code go?" guide below.

---

## 1. The one rule that shapes the package tree: the **UI / non-UI boundary**

The shared layer is **two modules** with one package tree:

> **`commons` = CLI-safe code.** It does not depend on Compose UI. It may use
> the `androidx.compose.runtime` *annotations* `@Stable` / `@Immutable` (they
> are just stability tags) and snapshot state (`mutableStateOf`, `State`), but
> it must **not** import `androidx.compose.ui`, `androidx.compose.foundation`,
> `androidx.compose.material3`, Coil, the generated `Res`, declare
> `@Composable` functions, or build `ImageVector`s. Its `build.gradle.kts`
> simply has none of those dependencies, so a violation fails to compile.
>
> **`commonsUI` = UI code.** Anything that does the above. It is only usable
> by the GUI front ends (Android, Desktop, iOS), never by `cli`.

Both modules share the **same `com.vitorpamplona.amethyst.commons.*` package
tree** — the split is a module boundary, not a package rename, so a file moves
between `commons/src/…` and `commonsUI/src/…` without changing its package or
any consumer's imports. Kotlin resolves same-package declarations across
modules without imports; the only thing that stops working across the boundary
is `internal` visibility (a UI file cannot see an `internal` declaration in
`commons` — make it public or move it).

This boundary is **not** a top-level `ui/` vs `logic/` partition of the package
tree (we chose to stay feature-oriented, §3). It is a property of each file:
a feature keeps its logic in `commons/…/<feature>/` and its composables in
`commonsUI/…/<feature>/ui/` (or `commonsUI/…/<feature>/` for the historical
flat packages), and the table in §2 says which module each package lives in.

---

## 2. Package taxonomy

Top-level packages under
`commonMain/.../commons/`, grouped by concern. **UI?** marks whether the
package contains Compose UI (and therefore lives in **`commonsUI`**, not
here). "mixed" means the feature's logic is in `commons` and its composables
in `commonsUI`, under the same package.

### Domain models & data
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `model`        | no¹ | Core domain types (`Note`, `User`, `Channel`), thread assembly, and per-NIP event model extensions in `model/nipNN…` subpackages. `model/cache` holds the in-memory event-store interfaces + `UserMetadataCache`. `model/account`, `model/observables`. The largest package; keep it organized by NIP. |
| `defaults`     | no  | Static bootstrap data (default relays, channels). |

¹ `model` uses only the `@Stable`/`@Immutable` runtime annotations — CLI-safe.

### Protocol-adjacent business logic (CLI-safe)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `actions`      | no  | Event builders for user actions (follow, zap…). The canonical entry point for non-UI callers. |
| `account`      | no  | New-account bootstrap events. |
| `onchain`      | no  | On-chain zap splitting/broadcasting. |
| `marmot`       | no  | MLS group-chat event processing. |
| `nip53LiveActivities` | mixed | Live-activity zapper aggregation (logic) + the stream card in `nip53LiveActivities/ui`. |
| `search`       | no  | Event search filtering/ranking, kind registry. |
| `preview`      | no  | OpenGraph / meta-tag link-preview parsing. |
| `emojicoder`   | no  | Variation-selector emoji encode/decode. |
| `richtext`     | no  | URL/media/pattern parsing for rich text. |
| `blurhash`     | no  | BlurHash encode/decode (pure math; platform image bridge in platform sets). |
| `thumbhash`    | no  | ThumbHash encode/decode. |
| `nipACWebRtcCalls` | no | NIP-AC WebRTC **call state machine** + peer-session abstraction. See `nipACWebRtcCalls/ARCHITECTURE.md`. (Mirrors `quartz/.../nipACWebRtcCalls`.) |

### State holders & ViewModels
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `state`        | no² | Small feature `StateFlow` machines (`FollowState`, `UserMetadataState`, `LoadingState`). |
| `viewmodels`   | no² | Larger list/feed-backed ViewModels (`androidx.lifecycle.ViewModel`). Shared by all GUI front ends; `cli` usually drives the layers below instead. The few that hold Compose UI state (`ChatNewMessageState` — `TextFieldValue`; `thread/LevelFeedViewModel` — `LazyListState`) live in `commonsUI` under the same package. |
| `feeds`        | no  | `FeedDefinitionRepository` — custom-feed definitions & ordering. |
| `profile`      | mixed | `ProfileBroadcastStatus` (state) + `EditProfileFields` at the root; the `ProfileBroadcastBanner` composable lives in `commonsUI` `profile/ui`. |
| `privacylock`  | mixed | Lock state machine + settings here; `LocalPrivacyLockState`/`lockStateFor` (CompositionLocal accessor) in `commonsUI`. |

² may touch `compose.runtime` state types (snapshot state, `@Stable`); they
are shared across the GUI apps. A state holder that needs a `foundation`/`ui`
type (`LazyListState`, `TextFieldValue`, `TextFieldState`) goes to `commonsUI`.

### Relay client
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `relayClient`  | mixed | Compose-scoped subscription managers, filter assemblers, EOSE managers, preloaders. (Despite a `composeSubscriptionManagers` subpackage name, this is subscription-lifecycle logic, not UI.) The `@Composable` entry points — `relayClient/user/` (`observeUser*` — kind-0 metadata), `relayClient/event/` (`EventFinderFilterAssemblerSubscription`/`observeNote*`), the other `*FilterAssemblerSubscription`s, `KeyDataSourceSubscription`, `auth/AuthApprovalBanner` — are in `commonsUI` under the same packages. See the `relay-client` skill. |
| `relays`       | no  | Low-level EOSE/relay-timing bookkeeping (`EOSECache`, `EOSERelayList`). |

### Platform abstractions (`expect`/`actual`)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `util`         | no  | KMP primitives: `KmpLock`, `WeakReference`, number/URL/codepoint helpers, list/debug helpers. **This is the only general-utility package** — there is no `utils`. |
| `keystorage`   | no  | Secure key storage interface (Keystore / keychain / keyring actuals). |
| `tor`          | no  | Tor manager interface + settings. |
| `service`      | no  | Cross-cutting services: `BundledUpdate` batching (common); `service/upload` (JVM), `service/nwc`, `service/lnurl` (jvmAndroid). **Singular `service`** — there is no `services`. |

### UI (Compose — lives in **`commonsUI`**)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `ui`           | yes | **Cross-cutting** shared composables only, organized by area: `ui/components`, `ui/theme`, `ui/signing`, `ui/thread`, `ui/note`, `ui/richtext`, `ui/search`, `ui/notifications`, `ui/screens`, `ui/layouts`, `ui/markdown`, `ui/privacylock`, plus Compose helpers in `ui/state` (cached-state) and `ui/text` (TextField extensions). Feature-specific UI lives in `<feature>/ui`, **not** here. **Exception:** `ui/feeds` in *this* module holds the headless feed DAL (`FeedFilter`, `AdditiveFeedFilter`, `ChangesFlowFilter`, `FeedContentState`, `RepostRenderability`…) — see debt §4; the `ui/feeds` composables (`NewPostsChip`, `RelayReachMarker`…) are in `commonsUI`. Likewise `ui/note/ParentNote`+`ReplyContext` (pure thread logic) stay here. |
| `nip23LongContent` | yes | Long-form (NIP-23) article UI: `nip23LongContent/ui/article` (reader) + `…/ui/editor` (authoring). The model lives in `model/nip23LongContent` (here). |
| `icons`        | yes | `ImageVector` icon definitions + builders, Material Symbols codepoints, the icon-font glyph tables. |
| `hashtags`     | yes | Custom hashtag `ImageVector`s. |
| `robohash`     | yes | Procedural robohash avatar `ImageVector` assembly. |
| `audio`        | mixed | Spectrum/visualizer *data* (`AudioSpectrum`, `SpectrumAnalyzer`…) here; the `VisualizerRenderer`s, `VisualizerRegistry` and the canvas composables in `commonsUI`. |
| `service/image` | mixed | `CoilImageBridge` + the BlurHash/ThumbHash/Base64/Blossom Coil fetchers are `commonsUI` (they are Coil); the headless image helpers stay here. |
| `napplet`      | mixed | Protocol/permission logic here; `NappletWebContract` (serves the shell/shim from `composeResources`) in `commonsUI`. |
| `favorites`, `nip30CustomEmojis`, `nip34Git`, `nip85TrustedAssertions`, `nip53LiveActivities` | mixed | Logic here; each feature's `ui/` (or the flat `FavoriteAppIcon`, `EmojiSuggestionState`) in `commonsUI`. |

### Mixed (documented debt — see §4)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `nip64Chess`   | mixed | Live-chess feature: game/lobby/subscription logic (here) **and** board/lobby composables (`commonsUI`) in one flat package. Still wants a `nip64Chess/ui` sub-package rename. (Mirrors `quartz/.../nip64Chess`.) |
| `domain`       | no  | Currently only `domain/nip46` (Nostr Connect signer flows). Sparse; candidate to fold into a clearer home. |

---

## 3. Conventions

### Feature-oriented, not layer-partitioned
We keep a feature's model, state, and UI **together** under one feature
package rather than splitting the whole module into top-level `ui/` /
`viewmodels/` / `model/` layers. Within a feature, separate UI from logic with a
`ui` **subpackage** (e.g. `profile/ProfileBroadcastStatus` vs
`profile/ui/ProfileBroadcastBanner`) so the CLI-safe boundary (§1) stays
visible.

The big shared cross-feature packages (`model`, `ui`, `relayClient`, `util`,
`icons`) are the exception — they are organized by layer because many features
share them.

### Naming
- **Singular, no synonyms.** `util` (not `utils`), `service` (not `services`).
  One concept → one package name.
- **Feature UI vs cross-cutting UI — the deciding test.** A composable goes in
  `<feature>/ui` if it renders/edits *one* feature's content (it would make no
  sense outside that feature) — e.g. `profile/ui`, `nip53LiveActivities/ui`,
  `nip23LongContent/ui`. It goes in `ui/<area>` only if it is reusable across
  features (theme, avatars, buttons, layouts, markdown rendering, shimmer…).
  When in doubt, ask "could a second, unrelated feature reuse this as-is?" —
  yes → `ui/<area>`, no → `<feature>/ui`. The top-level `ui/` package holds
  **no** feature-specific composables.

### NIP as the second axis (mirror `quartz`)
`quartz` is ~94% organized by NIP (`nipNN<slug>` per spec), and that is correct
*there* — the protocol layer is naturally NIP-partitioned. `commons` is **not**
organized by NIP at the top level, and should not be: most of it is
cross-cutting infrastructure (`ui`, `relayClient`, `viewmodels`, `feeds`,
`util`…) that serves many NIPs at once, and the load-bearing UI/non-UI boundary
(§1) cuts *across* NIPs, so a NIP-first top level would just nest the same
problem one level down.

Instead, **layer is the primary axis, NIP is the secondary axis**:

- The big shared layers stay layer-organized (`model`, `relayClient`, the
  cross-cutting `ui`, …).
- **Inside a layer, NIP-specific code goes in a `nipNN<slug>` subpackage whose
  name matches `quartz` exactly** — e.g. `model/nip57Zaps`. This gives a clean
  trace: `quartz/nip57Zaps` → `commons/model/nip57Zaps`.
- **A top-level package that *is* a single self-contained NIP feature takes the
  same name as its `quartz` counterpart**, and owns its own UI under
  `<feature>/ui`: `nip64Chess`, `nipACWebRtcCalls`, `nip53LiveActivities`,
  `nip23LongContent`, `marmot`. (`marmot` is un-numbered in `quartz` too.)
  Generic/multi-NIP packages keep their concern name (`search`, `preview`,
  `actions`, `richtext`…).
- **Feature UI is never under `ui/`.** A single-NIP feature's composables live
  in `<feature>/ui` (e.g. `nip53LiveActivities/ui`), not `ui/nip53LiveActivities`.
  `ui/` is exclusively cross-cutting (§2, Naming).

### Source sets
| Source set    | For |
|---------------|-----|
| `commonMain`  | KMP code for **all** targets (Android, JVM, iOS). Gated by `verifyKmpPurity` — no Jackson/OkHttp/`System.currentTimeMillis`/`java.util.UUID`/JVM `@Synchronized`/`@Volatile`. Use the KMP replacements. |
| `jvmAndroid`  | Shared by Android + Desktop, **not** iOS. Where JVM-bound deps live (`nestsClient`, OkHttp, NWC/LNURL). |
| `jvmMain`     | Desktop-only (keyring, EXIF, `service/upload`, OS notifications). `dependsOn(jvmAndroid)`. |
| `androidMain` | Android-only (Keystore, DataStore, the Android `R` string resources used by the napplet host). `dependsOn(jvmAndroid)`. |
| `iosMain`     | iOS `actual`s. Compile-only spike today. |

`commonsUI` mirrors the same source-set layout (plus `skikoMain`, shared by
desktop JVM + iOS for `org.jetbrains.skia` pixel helpers); Coil-OkHttp,
markdown and the `viewModel()` helper live in its `jvmAndroid`.

When adding platform code, prefer the **most common** source set that still
compiles: `commonMain` → `jvmAndroid` → platform-specific. See
`/kotlin-multiplatform`.

### Where does my code go? (quick guide)
1. **Pure Nostr protocol** (events/NIPs/crypto)? → not here, it's `quartz`.
2. **A composable** rendered by ≥2 front ends, or that you want iOS to share? →
   `commonsUI`, in `ui/<area>` or `<feature>/ui` (same package tree as here).
   Also anything that imports Coil, `Res`, or a `foundation`/`ui` state type.
3. **A ViewModel / `StateFlow` state holder**? → `viewmodels` or `state` (or
   `<feature>` if feature-scoped). Keep it CLI-safe where practical.
4. **Relay subscription / filter assembly**? → `relayClient`.
5. **A domain model or per-NIP event wrapper**? → `model` (`model/nipNN…`).
6. **A platform capability behind `expect`/`actual`** (storage, crypto)? → the matching abstraction package + actuals in platform sets.
7. **A generic helper**? → `util`. (Resist creating a new top-level package for
   one file.)

---

## 4. Known debt / follow-ups

These are intentionally *documented*, not silently tolerated. Fix opportunistically.

- **`nip64Chess` is UI+logic in one flat package.** The composables now sit in
  `commonsUI` (module split), but they keep the flat `nip64Chess` package;
  renaming them into `nip64Chess/ui/` is the remaining step.
- **`ui/feeds` (in `commons`) holds the feed data-access layer** (`FeedFilter`,
  `ChangesFlowFilter`, `FeedContentState`), which is logic, not UI, and overlaps
  conceptually with the top-level `feeds` (custom-feed definitions). Since the
  module split it is the one `ui.*` package that is *also* in `commons`. Move
  the DAL out of `ui/` (a package rename touching app imports) when convenient.
- **Same package tree in two modules.** Intentional (zero-import-churn split),
  but it means a package's module is not visible from its name. Rule of
  thumb: if it imports Compose UI it is in `commonsUI`; check §2 when unsure.
- **`domain` is sparse** (only `nip46`). Either grow it as the home for
  use-case/flow types or rename it to the matching `nip46RemoteSigner` per the
  NIP-second-axis rule.
- **`relays` vs `relayClient`** are coherent but close in name; `relays` is
  low-level EOSE bookkeeping, `relayClient` is the subscription client. Keep the
  distinction in mind when adding files.
- Several **single-file feature packages** (`account`, `marmot`,
  `nip53LiveActivities`, `keystorage`) are kept as feature/abstraction
  namespaces expected to grow; do not fold them into `util` just for size.
- **`onchain`** (on-chain zap splitting) is `quartz`-adjacent but un-numbered;
  leave readable unless a clear NIP number lands.

---

## 5. See also
- `nipACWebRtcCalls/ARCHITECTURE.md` — WebRTC call state machine deep-dive.
- Root `.claude/CLAUDE.md` — module overview, sharing philosophy, build commands.
- `/kotlin-multiplatform`, `/compose-expert`, `/feed-patterns`, `/relay-client`,
  `/account-state` skills for the patterns referenced above.
