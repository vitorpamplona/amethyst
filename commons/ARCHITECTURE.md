# `commons` — Shared Module Architecture & Goals

`commons` is the **shared layer** between every Amethyst front end:

| Consumer       | Kind                         | Uses from `commons`                          |
|----------------|------------------------------|----------------------------------------------|
| `amethyst`     | Android app (touch-first)    | everything (models, state, ViewModels, UI)   |
| `desktopApp`   | Desktop JVM app (mouse-first)| everything (models, state, ViewModels, UI)   |
| `cli` (`amy`)  | Headless JVM CLI (no UI)     | **non-UI only** — models, actions, relay, services |
| iOS (future)   | iOS app                      | everything; expected to share most UI with Android |

`commons` sits **above** `quartz` (the protocol-only Nostr KMP library) and
**below** the apps. The split between the three is:

- **`quartz/`** — Nostr protocol: events, NIPs, crypto, relay framing. No app
  state, no UI, no caches of "what this user follows."
- **`commons/`** — everything an Amethyst *client* needs that isn't a
  platform-native screen or navigation shell: domain models (`Note`, `User`),
  in-memory state holders, ViewModels, the relay-subscription client, shared
  business services, **and** the Compose UI components that more than one front
  end renders.
- **`amethyst/` & `desktopApp/`** — platform-native screens, navigation
  (bottom-nav vs sidebar), gestures, system integration. They assemble
  `commons` pieces; they should not re-implement them.

> The goal is **"write it once in `commons`"**. Before adding a manager, cache,
> filter, ViewModel, or composable to an app module, check whether it already
> exists here or belongs here. See the "Where does my code go?" guide below.

---

## 1. The one rule that shapes the package tree: the **UI / non-UI boundary**

`commons` is a single module that contains **both** Compose UI and headless
logic. That is deliberate (it keeps a feature's model, state, and UI together —
see §3), but it creates one hard constraint, because **`cli` and any headless
consumer cannot use Compose**:

> **CLI-safe code** = does not depend on Compose UI. It may use the
> `androidx.compose.runtime` *annotations* `@Stable` / `@Immutable` (they are
> just stability tags) and snapshot state, but it must **not** import
> `androidx.compose.ui`, `androidx.compose.foundation`,
> `androidx.compose.material3`, declare `@Composable` functions, or build
> `ImageVector`s.
>
> **UI code** = anything that does. It is only usable by the GUI front ends
> (Android, Desktop, iOS), never by `cli`.

Compose is an `implementation` dependency of `commonMain`, so `cli` pulling in
`commons` does **not** force it to render anything — but a `cli` command must
only reach for CLI-safe packages. When you add code, know which side of this
line it is on, and put it in a package that matches (§2).

This boundary is **not** a top-level `ui/` vs `logic/` partition of the whole
module (we chose to stay feature-oriented, §3). It is a property of each file
that you keep track of via package placement and the table in §2.

---

## 2. Package taxonomy

Top-level packages under
`commonMain/.../commons/`, grouped by concern. **UI?** marks whether the
package contains Compose UI (and is therefore *not* CLI-safe).

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
| `nip53LiveActivities` | no | Live-activity zapper aggregation. |
| `search`       | no  | Event search filtering/ranking, kind registry. |
| `preview`      | no  | OpenGraph / meta-tag link-preview parsing. |
| `emojicoder`   | no  | Variation-selector emoji encode/decode. |
| `richtext`     | no  | URL/media/pattern parsing for rich text. |
| `blurhash`     | no  | BlurHash encode/decode (pure math; platform image bridge in platform sets). |
| `thumbhash`    | no  | ThumbHash encode/decode. |
| `call`         | no  | NIP-AC WebRTC **call state machine** + peer-session abstraction. See `call/ARCHITECTURE.md`. |

### State holders & ViewModels
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `state`        | no² | Small feature `StateFlow` machines (`FollowState`, `UserMetadataState`, `LoadingState`). |
| `viewmodels`   | no² | Larger list/feed-backed ViewModels (`androidx.lifecycle.ViewModel`). Shared by all GUI front ends; `cli` usually drives the layers below instead. |
| `feeds`        | no  | `FeedDefinitionRepository` — custom-feed definitions & ordering. |
| `profile`      | mixed | `ProfileBroadcastStatus` (state) + `EditProfileFields` at the root; the `ProfileBroadcastBanner` composable lives in `profile/ui`. |

² may touch `compose.runtime`/`foundation` state types (e.g. `LazyListState`);
they are shared across the GUI apps. Treat as GUI-shared, not strictly headless.

### Relay client
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `relayClient`  | no  | Compose-scoped subscription managers, filter assemblers, EOSE managers, preloaders. (Despite a `composeSubscriptionManagers` subpackage name, this is subscription-lifecycle logic, not UI.) |
| `relays`       | no  | Low-level EOSE/relay-timing bookkeeping (`EOSECache`, `EOSERelayList`). |

### Platform abstractions (`expect`/`actual`)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `util`         | no  | KMP primitives: `KmpLock`, `WeakReference`, number/URL/codepoint helpers, list/debug helpers. **This is the only general-utility package** — there is no `utils`. |
| `keystorage`   | no  | Secure key storage interface (Keystore / keychain / keyring actuals). |
| `threading`    | no  | `checkNotInMainThread` assertion. |
| `tor`          | no  | Tor manager interface + settings. |
| `service`      | no  | Cross-cutting services: `BundledUpdate` batching (common); `service/upload` (JVM), `service/nwc`, `service/lnurl` (jvmAndroid). **Singular `service`** — there is no `services`. |

### UI (Compose — **not** CLI-safe)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `ui`           | yes | All shared composables, organized by area: `ui/components`, `ui/theme`, `ui/signing`, `ui/thread`, `ui/feeds` (feed DAL + filters — see debt §4), `ui/notifications`, `ui/screens`, `ui/article`, `ui/editor`, `ui/elements`, `ui/layouts`, `ui/markdown`, `ui/nip53LiveActivities`, plus Compose helpers in `ui/state` (cached-state) and `ui/text` (TextField extensions). |
| `icons`        | yes | `ImageVector` icon definitions + builders. |
| `hashtags`     | yes | Custom hashtag `ImageVector`s. |
| `robohash`     | yes | Procedural robohash avatar `ImageVector` assembly. |

### Mixed (documented debt — see §4)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `chess`        | mixed | Live-chess feature: game/lobby/subscription logic **and** board/lobby composables in one flat package. Needs a `chess/ui` split. |
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
- Per-NIP model code goes in `model/nipNN<slug>` (e.g. `model/nip51Lists`).
- Composables that belong to a feature go in `<feature>/ui`; cross-cutting
  composables go under `ui/<area>`.

### Source sets
| Source set    | For |
|---------------|-----|
| `commonMain`  | KMP code for **all** targets (Android, JVM, iOS). Gated by `verifyKmpPurity` — no Jackson/OkHttp/`System.currentTimeMillis`/`java.util.UUID`/JVM `@Synchronized`/`@Volatile`. Use the KMP replacements. |
| `jvmAndroid`  | Shared by Android + Desktop, **not** iOS. Where JVM-bound deps live (`nestsClient`, Coil-OkHttp, markdown, `viewModel()` helper, NWC/LNURL). |
| `jvmMain`     | Desktop-only (keyring, EXIF, `service/upload`). `dependsOn(jvmAndroid)`. |
| `androidMain` | Android-only (Keystore, DataStore). `dependsOn(jvmAndroid)`. |
| `iosMain`     | iOS `actual`s. Compile-only spike today. |

When adding platform code, prefer the **most common** source set that still
compiles: `commonMain` → `jvmAndroid` → platform-specific. See
`/kotlin-multiplatform`.

### Where does my code go? (quick guide)
1. **Pure Nostr protocol** (events/NIPs/crypto)? → not here, it's `quartz`.
2. **A composable** rendered by ≥2 front ends, or that you want iOS to share? →
   `ui/<area>` or `<feature>/ui`. Never in `cli`.
3. **A ViewModel / `StateFlow` state holder**? → `viewmodels` or `state` (or
   `<feature>` if feature-scoped). Keep it CLI-safe where practical.
4. **Relay subscription / filter assembly**? → `relayClient`.
5. **A domain model or per-NIP event wrapper**? → `model` (`model/nipNN…`).
6. **A platform capability behind `expect`/`actual`** (storage, crypto,
   threading)? → the matching abstraction package + actuals in platform sets.
7. **A generic helper**? → `util`. (Resist creating a new top-level package for
   one file.)

---

## 4. Known debt / follow-ups

These are intentionally *documented*, not silently tolerated. Fix opportunistically.

- **`chess` is UI+logic in one flat package.** `LiveChessGame.kt` mixes a state
  class with composables. Split into `chess/` (logic) + `chess/ui/`
  (composables); this needs file-level surgery (extracting composables out of
  logic files), not just moves, so it is deferred.
- **`ui/feeds` holds the feed data-access layer** (`FeedFilter`,
  `ChangesFlowFilter`, `FeedContentState`), which is logic, not UI, and overlaps
  conceptually with the top-level `feeds` (custom-feed definitions). Consider
  moving the DAL out of `ui/`.
- **`domain` is sparse** (only `nip46`). Either grow it as the home for
  use-case/flow types or fold `nip46` into a clearer location.
- **`relays` vs `relayClient`** are coherent but close in name; `relays` is
  low-level EOSE bookkeeping, `relayClient` is the subscription client. Keep the
  distinction in mind when adding files.
- Several **single-file feature packages** (`account`, `marmot`,
  `nip53LiveActivities`, `keystorage`, `threading`) are kept as feature/abstraction
  namespaces expected to grow; do not fold them into `util` just for size.

---

## 5. See also
- `call/ARCHITECTURE.md` — WebRTC call state machine deep-dive.
- Root `.claude/CLAUDE.md` — module overview, sharing philosophy, build commands.
- `/kotlin-multiplatform`, `/compose-expert`, `/feed-patterns`, `/relay-client`,
  `/account-state` skills for the patterns referenced above.
</content>
</invoke>
