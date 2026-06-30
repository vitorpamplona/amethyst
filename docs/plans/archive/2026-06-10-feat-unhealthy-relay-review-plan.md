---
title: Unhealthy Relay Review (banner + sheet/popover)
type: feat
status: active
date: 2026-06-10
origin: docs/brainstorms/2026-06-10-unhealthy-relay-review-brainstorm.md
---

# Unhealthy Relay Review

> **Status:** shipped — Shipped — commons RelayHealthStore + UnhealthyRelayBanner/Row, desktop UnhealthyRelayBannerHost/Popup.
> _Audited 2026-06-30._


## Enhancement Summary (deepen-plan, 2026-06-10)

Seven parallel review agents critiqued the original plan. The implementation below follows the refined design; the original sections are kept for context but **superseded** where they conflict.

### Critical fixes
1. **Bug — `firstSeenAt == 0L` newcomer-grace bypass**: drop the per-relay `firstSeenAt` field. Use a single global `firstScanAt` (set once at first store init for the account). Newcomer grace = "global firstScanAt within 7d ⇒ skip all flagging."
2. **Bug — `didScan by remember` doesn't reset on account switch**: key the LaunchedEffect on `pubKeyHex` so each account gets one scan; no separate guard needed.
3. **Bug — snooze never expires without a periodic ticker**: feed a `flow { while(true){emit(Unit); delay(60_000)} }` into `combine(records, userLists, ticker)` so the snapshot reclassifies once per minute and expired snoozes drop.
4. **Stability — `Set<RelayListKind>` is unstable**: use `kotlinx.collections.immutable.PersistentSet` and annotate the value types `@Immutable`.
5. **Visual — `tertiaryContainer` is purple in this theme** (`PlatformColorScheme.kt:50-53,88-91`): use `MaterialTheme.colorScheme.errorContainer.copy(alpha=0.5f)` (matches `ChessSyncBanner`, `ProfileBroadcastBanner` precedent for soft warnings).

### Architecture changes
6. **Drop `expect class RelayHealthStore`**: replace with a single `class RelayHealthStore(persistence: RelayHealthPersistence, …)` in `commonMain`. Persistence interface lives in commonMain; platform impls (`AccountSettingsRelayHealthPersistence`, `PreferencesRelayHealthPersistence`) live in `amethyst/` and `desktopApp/` respectively.
7. **Drop `RelayListMutator` expect/actual**: define `interface RelayListMutator` in commons with `suspend fun removeFromAllUserLists(url): RemovalResult`. Android impl in `amethyst/` (delegates to `Account.send*RelayList`); Desktop impl in `desktopApp/` (delegates to `*State.saveRelayList` + `broadcastToAll`). Multi-list sign requests run in parallel via `async { … }.awaitAll()`.
8. **Package placement**: move `commons/.../relayhealth/` → `commons/.../relays/health/` per `ARCHITECTURE.md`. Files: `commons/.../relays/health/` (non-UI) + `commons/.../relays/health/ui/` (banner, row).
9. **Tor mode gap**: `TorRelayEvaluation` exists; v1 conservative behavior is to skip classification entirely whenever `TorSettings.torType != OFF`. Documented as a Risk row; v2 can be smarter.

### Simplifications (YAGNI)
10. Drop `UnhealthyRelaysSnapshot` wrapper — emit `PersistentList<UnhealthyRelay>` directly. Drop `slowCount` (no v1 reader). Drop `pruneRemovedRelays` (classifier already gates on "in user list"). Drop `observeRelay(url)` (no longer needed without `firstSeenAt`). Drop `RemovalPlan` (mutator returns `RemovalResult` directly). Drop 8KB Preferences fallback `.dat` file (speculative).

### Performance
11. **Debounce 5s** for persistence (was 1s; timestamps are seconds-granularity). **Parallel** sign-requests on remove. `@Immutable` on all UI state classes. `derivedStateOf` for banner count read. `flowOn(Dispatchers.Default)` for `classifyRelayHealth`.

### Other
12. **No `pluralStringResource` in commons** (zero precedent + zero `<plurals>` entries). Pass pre-resolved count text into the shared banner; platforms format using their native plural infra. Banner accepts `text: String` + `onClick` only.
13. **`pubKeyHex.take(8)`** for keying (matches existing precedent `<pubkey8>`; was 16 in original plan, inconsistent).
14. **`RelayHealthStore` lifecycle**: scope owned by store (`SupervisorJob + Dispatchers.Default`), cancelled on account switch via `close()`. One debouncer per store instance.

## Overview

Surface relays that have not been responsive in 7+ days so users can review and remove them in two taps, across Android and Desktop. UI = a persistent banner above the main content area whenever ≥1 unhealthy relay exists; tapping it opens a per-relay list (Android `ModalBottomSheet`, Desktop anchored `Popup`) with `Remove`, `Open Relay Dashboard`, and `Snooze 7d` actions, plus a banner-level `Snooze all 7d`. Detection runs once per app launch from persisted "last activity" timestamps.

Scope is the user's NIP-65-style relay lists: kinds **10002** (read/write), **10050** (DMs), **10007** (search). **10006** (blocked) is intentionally excluded from detection but included in the "remove from all lists" action (see brainstorm: docs/brainstorms/2026-06-10-unhealthy-relay-review-brainstorm.md — deviation noted under Risks).

## Problem Statement

Users accumulate relays over time. When a relay goes offline permanently (operator shuts down, domain expires, infra rot), the client still tries to connect, wasting connection budget, polluting metrics, and silently degrading event reach. The existing Relay Dashboard exposes per-relay state but requires the user to *go look* — there is no proactive prompt to clean up dead entries. Result: stale relay lists drift indefinitely.

## Proposed Solution

A non-modal banner that appears whenever any relay in the user's monitored lists has been silent for ≥ 7 days, with a one-tap drill-in surface that turns the maintenance task into a couple of taps. The detection layer extends `RelayStat` with two new timestamps; persistence mirrors existing account-scoped patterns per platform; the banner reuses `OfflineBanner`'s structure; the sheet/popover reuses existing templates (`AddToCalendarSheet` on Android, `Popup` from `NoteActions.kt` on Desktop).

## Technical Approach

### Architecture

```
quartz/             commons/                              amethyst/                desktopApp/
─────────           ──────────────────────────            ──────────────           ──────────────
RelayStat (extend)  RelayHealthStore (new, expect/actual) HomeScaffold (wire)      DeckColumnContainer (wire)
  ↓ updates from    ├── classify(): UnhealthyRelay set    ├── UnhealthyBanner      ├── UnhealthyBanner
RelayStats listener ├── snooze APIs                       │   (commons)            │   (commons)
                    ├── persists to disk via actual       ├── UnhealthyRelaySheet  ├── UnhealthyRelaysPopup
                    │   ├── jvmMain → java.util.prefs     │   (Android-specific)   │   (Desktop-specific)
                    │   └── androidMain → AccountSettings └── nav to EditRelays    └── set DeckColumnType.Relays
                    └── RemoveFromAllLists helper
                        (uses *State.saveRelayList APIs)
```

Three layers:

1. **Tracking layer (quartz)** — extend `RelayStat` with `lastEventAt` and `lastConnectAt`. Wire from existing `RelayStats` listener (already has `onConnected` + `onIncomingMessage` taps).
2. **Health state layer (commons)** — new `RelayHealthStore` (`expect class` with `actual` on Android via `AccountSettings`, on Desktop via `java.util.prefs.Preferences`). Owns persistence, classification (`classify(relayList): Set<UnhealthyRelay>`), and snooze map. Exposes `StateFlow<UnhealthyRelaysSnapshot>`.
3. **UI layer** — banner shared in `commons/`, sheet/popover platform-specific, wired into the existing scaffold slots.

### Detection algorithm (v1)

A relay `R` is **unhealthy** iff **all** of:

1. `R` appears in at least one of the *monitored* user lists: kinds 10002, 10050, 10007. (10006 excluded.)
2. `now - max(R.lastEventAt, R.lastConnectAt) > 7d`.
3. `now - lastSeenAny > 7d` is **false** — i.e. at least one relay in the user's set responded within the last 7d (offline-grace gate).
4. `R` was first seen in any list at least 7d ago (newcomer grace).
5. `R.snoozedUntil < now`.

**Removed from v1 (require new tracking infra):**

- "Persistent errors" — needs a windowed error counter. RelayStat's `errorCounter` is lifetime-cumulative; can't distinguish "30 errors yesterday" from "30 errors over 2 years."
- "No EOSE / high latency" — EOSE is per-subscription, not per-relay. `pingInMs` is tracked but slow ≠ dead; surfacing it in the banner would be noisy. Both deferred to v2; see Future Considerations.

(See brainstorm: docs/brainstorms/2026-06-10-unhealthy-relay-review-brainstorm.md — brainstorm listed all four signals; v1 reduces to the two timestamp-based ones for honesty + simplicity. Documented as v1 scope.)

### Persistence schema

Per-account, keyed by `NormalizedRelayUrl`:

| Field | Type | Purpose |
|---|---|---|
| `lastEventAt` | `Long` (epoch ms) | Updated on `EventMessage` received from this relay. |
| `lastConnectAt` | `Long` (epoch ms) | Updated on `onConnected` for this relay. |
| `firstSeenAt` | `Long` (epoch ms) | First time we observed this relay in any user list. Newcomer-grace gate. |
| `snoozedUntil` | `Long` (epoch ms) | `0` = not snoozed. |

Plus a single global `lastSeenAny: Long` for offline-grace.

**Desktop** (`actual` in jvmMain): `java.util.prefs.Preferences.userNodeForPackage(RelayHealthStore::class)`, key `"health_${pubKeyHex.take(16)}_${fieldName}"`. Map serialized as `url|lastEvent|lastConnect|firstSeen|snoozedUntil` lines joined with `\n` (8KB Preferences limit → ~150 relays at 50B/row; if hit, fall back to a `.dat` file under `~/.amethyst/accounts/<pubkey8>/relay_health.dat`).

**Android** (`actual` in androidMain): `AccountSettings.relayHealth: MutableStateFlow<Map<NormalizedRelayUrl, RelayHealthRecord>>` mirroring the `viewedPollResultNoteIds: Map<String, Long>` precedent (`amethyst/.../model/AccountSettings.kt:1135-1155`). Persisted via existing `EncryptedSharedPreferences`-backed `LocalPreferences` flow.

Writes are **debounced 1s** in commons to avoid disk thrash from a noisy `onIncomingMessage` storm. Pattern: same `BasicBundledInsert` already used by `LocalRelayStore`.

### "Remove from all lists" helper

Lives in `commons/` as `RelayListMutator` (new). Inputs: `NormalizedRelayUrl`, the set of lists it's in. Outputs: a `RemovalPlan` listing the signed events that need publishing.

Per platform:
- **Android**: `RelayListMutator.execute()` delegates to `Account.sendNip65RelayList` / `saveDMRelayList` / `saveSearchRelayList` / `saveBlockedRelayList` (`amethyst/.../model/Account.kt:3274/3294/3349/3424`).
- **Desktop**: calls the relevant commons `*State.saveRelayList(...)` and forwards each signed event to `relayManager.broadcastToAll(event)` (mirrors `DeckColumnContainer.kt:476` pattern).

Hidden behind a single `suspend fun removeRelayFromAllUserLists(url, account, accountRelays): RemovalResult` so the sheet/popover doesn't branch on platform.

### Implementation Phases

#### Phase 1 — Tracking layer (`quartz/`)

Pure-data extension; no behavior change.

- **Files**:
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/client/stats/RelayStat.kt` — add `@Volatile var lastConnectAt: Long = 0` and `@Volatile var lastEventAt: Long = 0`.
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/client/stats/RelayStats.kt` — in the existing `RelayConnectionListener`:
    - `onConnected(...)` → `relayStat.lastConnectAt = TimeUtils.nowInMs()`.
    - `onIncomingMessage(msgStr, msg)` → on `msg is EventMessage` → `relayStat.lastEventAt = TimeUtils.nowInMs()`.
- **Tests**: `quartz/src/commonTest/.../RelayStatTest.kt` — unit-cover both setters fire on simulated messages.

#### Phase 2 — Health state (`commons/`)

- **New files**:
  - `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayhealth/RelayHealthRecord.kt` — data class (4 timestamps).
  - `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayhealth/UnhealthyRelay.kt` — { url, lastEventAt, lastConnectAt, lists: Set<RelayListKind> }.
  - `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayhealth/UnhealthyRelaysSnapshot.kt` — { unhealthy: List<UnhealthyRelay>, slowCount: Int }.
  - `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayhealth/RelayHealthClassifier.kt` — pure function `classify(records, userLists, now): UnhealthyRelaysSnapshot`.
  - `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayhealth/RelayHealthStore.kt` — `expect class`, exposes:
    ```kotlin
    val snapshot: StateFlow<UnhealthyRelaysSnapshot>
    fun observeRelay(url: NormalizedRelayUrl)               // marks firstSeenAt
    fun recordEvent(url: NormalizedRelayUrl, atMs: Long)    // bumps lastEventAt
    fun recordConnect(url: NormalizedRelayUrl, atMs: Long)  // bumps lastConnectAt
    fun snooze(url: NormalizedRelayUrl, until: Long)
    fun snoozeAll(until: Long)
    fun pruneRemovedRelays(currentUrls: Set<NormalizedRelayUrl>)
    suspend fun scanNow(): UnhealthyRelaysSnapshot          // recompute + emit
    ```
  - `commons/src/androidMain/.../RelayHealthStore.kt` — actual, backed by `AccountSettings.relayHealth` JSON map (mirror `viewedPollResultNoteIds`).
  - `commons/src/jvmMain/.../RelayHealthStore.kt` — actual, backed by `java.util.prefs.Preferences`.
  - `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayhealth/RelayListMutator.kt` — `suspend fun removeRelayFromAllUserLists(url, account, accountRelays)` (expect/actual).
- **Wire-up**:
  - In `commons` ViewModel or service that already observes `RelayStats`, watch each `RelayStat` and forward `lastEventAt` / `lastConnectAt` changes into `RelayHealthStore`. Debounce 1s. Closest existing host: the place that already constructs `RelayStats` (cite during impl).
  - `observeRelay(url)` called from the place that adds a relay to any list (Android `Account.send*RelayList`, Desktop `DesktopAccountRelays` setters).
- **Tests**:
  - `commons/src/commonTest/.../RelayHealthClassifierTest.kt` — table-driven cases covering: dead relay, snoozed relay, newcomer (within 7d of firstSeenAt), offline-grace (lastSeenAny > 7d → nothing flagged), 10006-only relay (excluded), relay in multiple lists.
  - `commons/src/jvmTest/.../RelayHealthStoreJvmTest.kt` — write → read round-trip via Preferences.

#### Phase 3 — Shared UI in `commons/`

- **New files**:
  - `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayhealth/ui/UnhealthyRelayBanner.kt` — visual twin of `OfflineBanner`. Yellow-amber (`tertiaryContainer`) to differentiate from `OfflineBanner`'s red. Copy via `pluralStringResource`.
  - `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayhealth/ui/UnhealthyRelayRow.kt` — reusable row with `Remove`, `Open Dashboard`, `Snooze 7d` slots. Used by both Android sheet + Desktop popover.
- **String resources**: add to `commons/src/commonMain/composeResources/values/strings.xml`:
  - `unhealthy_relays_banner_title` (plural)
  - `unhealthy_relays_review_action`
  - `unhealthy_relay_remove`
  - `unhealthy_relay_open_dashboard`
  - `unhealthy_relay_snooze_7d`
  - `unhealthy_relays_snooze_all_7d`
  - `unhealthy_relay_lists_label` (e.g. "in: Read/Write, DMs")
- **Tests**: snapshot/composable tests can wait for first-render review.

#### Phase 4 — Android wiring

- **Sheet**: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/relays/health/UnhealthyRelaysSheet.kt` — modeled on `AddToCalendarSheet.kt:64-80`. `ModalBottomSheet(skipPartiallyExpanded = true)` + scrollable `Column` of `UnhealthyRelayRow`s + footer `Snooze all 7d` button.
- **Placement**: inside `DisappearingScaffold` content slot, above the feed content. Banner is sticky-top sibling to the feed `LazyColumn`. Concrete site: above the `LazyColumn` in `HomeScreen.kt:224`. Reuse for other top-level screens deferred (banner shown only on Home for v1; matches "review at app start" UX).
- **App-start trigger**: `LaunchedEffect(accountViewModel.account)` inside `AppNavigation` near `AppNavigation.kt:220-239` — call `relayHealthStore.scanNow()` once per process (guard with `var didScan by remember`).
- **Nav**: "Open Relay Dashboard" → `nav.nav(Route.EditRelays)` (existing route — `Routes.kt:366`).
- **Snackbar feedback** (for Remove): use existing `accountViewModel.toast(...)` channel; copy: "Removed `relay.url`".

#### Phase 5 — Desktop wiring

- **Popup**: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/relay/health/UnhealthyRelaysPopup.kt` — anchored under the banner via `androidx.compose.ui.window.Popup` with `PopupProperties(focusable = true)` (pattern from `NoteActions.kt:73-74` + `:492-496`). Scrollable column, max height `400.dp`, dismiss on outside click.
- **Banner placement**: inside `DeckColumnContainer.kt` directly below the per-column header (above `OfflineBanner` if both present), and inside `SinglePaneLayout.kt:100-103` for the single-pane mode. Show only on home/feed columns to avoid noise on settings.
- **App-start trigger**: `Main.kt:860 LaunchedEffect(accountState)` — inside the `is LoggedIn` branch already there, call `relayHealthStore.scanNow()` once. Guard with `var didScan by remember`.
- **Nav**: "Open Relay Dashboard" → set the active sidebar item / column type to `DeckColumnType.Relays` (existing — see `DeckColumnContainer.kt:468-477`).
- **Feedback**: existing Desktop `SnackbarHost`.

#### Phase 6 — Polish

- Spotless + lint.
- Manual smoke test matrix (see Quality Gates).
- Update relay-related docs in `commons/ARCHITECTURE.md` if new package warrants it.

## Alternative Approaches Considered

| Approach | Why rejected |
|---|---|
| Modal launch dialog | Too intrusive for periodic housekeeping; dismissable means it's gone on relaunch. (see brainstorm — Approach A) |
| Snackbar + dashboard badge | Auto-dismisses; multi-step "review and remove" defeats the easy-maintenance goal. (see brainstorm — Approach C) |
| Add windowed error counter to RelayStat now | Adds infrastructure for a marginal v1 signal; the two timestamp checks already capture "dead" cleanly. Deferred to v2. |
| Track lastEoseAt per subscription | Out of scope — EOSE is per-subscription, not per-relay; signal is noisy for relays the user rarely queries from. |
| Encrypt health timestamps | No PII / no secret material — timestamps of public relay URLs. Skip the overhead. |

## System-Wide Impact

### Interaction Graph

```
Relay receives event
  → RelayStats.RelayConnectionListener.onIncomingMessage
    → RelayStat.lastEventAt = now            [new]
      → (debounced 1s) RelayHealthStore.recordEvent
        → updates StateFlow<UnhealthyRelaysSnapshot>
          → UnhealthyRelayBanner recomposes (count - 1 if previously flagged)

User taps banner
  → opens ModalBottomSheet (Android) / Popup (Desktop)
    → tap Remove
      → RelayListMutator.removeRelayFromAllUserLists
        → 1..4 *State.saveRelayList(...) calls
        → Account.send*RelayList (Android) / relayManager.broadcastToAll (Desktop)
        → DesktopAccountRelays / Account StateFlow emits new list
          → RelayHealthStore.pruneRemovedRelays(currentUrls)
            → banner recomposes
```

### Error & Failure Propagation

| Failure | Where caught | Behavior |
|---|---|---|
| Sign fails (NIP-46 bunker timeout) on remove | `RelayListMutator.removeRelayFromAllUserLists` | Returns `RemovalResult.Failure(lists: List<Kind>)`; sheet shows "Removed from X of Y lists" snackbar. No partial-remove undo (matches "no undo" decision). |
| Broadcast fails (no relays connected) | `relayManager.broadcastToAll` | Signed event still persisted locally; will replay when online (existing behavior). |
| Persistence write fails (Preferences full / IO error) | `RelayHealthStore.actual` | Log + swallow. Next scan recomputes from in-memory state. |
| Snooze write fails | same | Snooze degrades to in-memory for this session — acceptable. |

No new exception classes. All existing relay-edit error paths re-used.

### State Lifecycle Risks

- **Orphan timestamps** when a relay is removed from all lists → `pruneRemovedRelays` drops them. Triggered in the StateFlow collector of the user's list set.
- **Account switch mid-action** → `RelayHealthStore.snapshot` is per-account-scoped via the same scoping as `AccountSettings` / `DesktopAccountRelays`. New instance per account.
- **Multiple relays flapping** → debounce on write side; classifier is pure → idempotent on read.
- **First-run grace** — `firstSeenAt` is set lazily on first observation. If we ship into an empty store, every existing relay gets `firstSeenAt = now` on first launch → no flags for 7d, matching the brainstorm decision (see brainstorm: Resolved Questions).
- **Partial remove** → if 2 of 3 list-edits succeed and the third fails (signer crash), local state shows the relay only in the remaining list. Next health scan will still flag it (or not) based on its timestamps. User can re-tap Remove.

### API Surface Parity

| Surface | Status |
|---|---|
| Android Home (`HomeScreen`) | Banner shown |
| Android other feeds (Notifications, DMs, Discover) | Not in v1 — Home only |
| Desktop deck columns | Banner shown only on feed columns (Home, Notifications, DMs) |
| Desktop SinglePaneLayout | Banner shown |
| `RelayDashboardScreen` (Desktop) / `AllRelayListScreen` (Android) | No banner inside — would be redundant with the screen content. Optional follow-up: an inline "Unhealthy" section/filter. |
| `amy` CLI | New `amy relays health` subcommand — listed under Future Considerations, not v1. |

### Integration Test Scenarios

1. **Dead relay flagged on next launch**: seed records so `lastEventAt = lastConnectAt = now - 8d` and `lastSeenAny = now - 1h`; assert snapshot contains 1 relay; assert banner composable renders with count = 1.
2. **Snooze hides relay then re-shows**: snooze for 1ms; advance clock 2ms; assert it reappears.
3. **Offline-grace gate**: seed all relays as `lastEventAt = now - 10d`, `lastSeenAny = now - 10d`; assert snapshot is empty (we appear to be offline; not the relays' fault).
4. **Multi-list Remove publishes 1..4 events**: seed relay in 10002 + 10050; assert mutator publishes exactly 2 signed events; assert `pruneRemovedRelays` drops the record.
5. **Newcomer grace**: `firstSeenAt = now - 1d`; even if `lastEventAt = now - 8d`, not flagged.

## Acceptance Criteria

### Functional Requirements

- [ ] Banner appears on Android Home (`HomeScreen`) and on Desktop feed columns whenever ≥1 relay is unhealthy (per detection algorithm above).
- [ ] Banner copy uses plural string resource and shows count.
- [ ] Tapping banner opens `ModalBottomSheet` (Android) / `Popup` (Desktop) listing each unhealthy relay with: URL, list-membership chips, last-seen relative time, `Remove`, `Open Dashboard`, `Snooze 7d`.
- [ ] Banner footer (or sheet-top) has `Snooze all 7d`.
- [ ] Remove deletes the relay from every list it appears in (10002 / 10050 / 10007 / 10006). No confirmation, no undo.
- [ ] Open Dashboard navigates to existing relay dashboard for the platform (no pre-focus).
- [ ] Snooze (per-relay) suppresses that relay's flag until `now + 7d`.
- [ ] Snooze all suppresses every currently-flagged relay until `now + 7d`.
- [ ] Detection runs once on app start (per-process) and recomputes whenever the user's list set changes or a snooze expires.
- [ ] First-run: no relay flagged for 7d after install (no `firstSeenAt` history).
- [ ] Offline grace: if `lastSeenAny > 7d` (i.e. no relay anywhere has responded recently), nothing is flagged.

### Non-Functional Requirements

- [ ] Persistence write debounced ≥ 1s; no measurable disk-write hot loop under sustained relay traffic.
- [ ] RelayStat extension adds ≤ 16 bytes per relay (two Longs).
- [ ] Classifier is pure and side-effect free (testable without I/O).
- [ ] Health store actuals account-scoped — switching accounts loads a fresh store within 1 frame.
- [ ] No new permissions, no network changes.

### Quality Gates

- [ ] Unit tests for classifier (table-driven, ≥ 8 scenarios incl. the 5 listed in Integration Test Scenarios).
- [ ] Unit tests for `RelayStat` setter wiring (Phase 1).
- [ ] Round-trip tests for both `actual` persistence implementations.
- [ ] `./gradlew spotlessApply` clean.
- [ ] Manual smoke matrix:
  | Platform | Steps |
  |---|---|
  | Android | Seed dead relay → relaunch → banner shows → tap → sheet → Remove → snackbar → relay gone from `AllRelayListScreen`. |
  | Android | Seed dead relay → Snooze → banner gone → advance clock 7d → banner returns. |
  | Desktop | Same flows with `Popup` + `DeckColumnContainer`. |
  | Desktop | Account switch with stale records on prior account → fresh account shows none / its own. |

## Success Metrics

- (Telemetry-light project — qualitative.) Anecdotal: users report cleaner relay lists / removed dead URLs on first 1–2 launches after upgrade.
- Zero crash reports from `RelayListMutator` over 30d post-ship.
- No regressions in `OfflineBanner` placement / paint cost (banner is sibling; if both present they should stack cleanly).

## Dependencies & Prerequisites

- None new. Reuses existing `RelayStats`, `RelayConnectionListener`, `AccountSettings`, `DesktopAccountRelays`, `Account.send*RelayList`, `*State.saveRelayList`, `OfflineBanner`, `AddToCalendarSheet`, `Popup` patterns.
- `pluralStringResource` already available via Compose Multiplatform.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| User offline for >7d → all relays flagged on relaunch | Medium | High (mass false positive) | **Offline-grace gate** in classifier (`lastSeenAny > 7d` → skip). |
| Newly-added relays get flagged before they have history | High without mitigation | Medium | **Newcomer-grace gate** via `firstSeenAt`. |
| Bunker signer slow → 1–4 sign requests for Remove pile up | Medium | Medium | Issue sign requests sequentially; first failure short-circuits remaining and the `RemovalResult.Failure(lists)` snackbar tells user which lists remain. |
| Persistence layer corruption | Low | Low | Treat as empty store on parse failure (existing pattern in `AccountSettings`); user gets newcomer-grace and rebuilds. |
| 10006 (blocked) excluded from detection but included in Remove may surprise users | Low | Low | Sheet row chips show *all* lists the relay is in, including "Blocked" — Remove behavior is transparent. (Deviation from brainstorm — see Open Questions resolved.) |
| Banner stacks with `OfflineBanner` and consumes feed height | Medium | Low | Both use the same compact height (~36 dp). Acceptable. If a third banner ever needs stacking, refactor to a `BannerStack` then. Not now. |
| `Preferences` 8 KB limit hit for users with 100+ relays | Very low | Medium | Fall back to a flat file under `~/.amethyst/accounts/<pubkey8>/relay_health.dat`. Implemented in Phase 2. |

## Future Considerations

- **v2 signals**: windowed error counter (`errorsLast24h`), per-subscription EOSE latency. Adds a "Slow" classification surfaced in the dashboard, not the banner.
- **Inline "Unhealthy" section** in `RelayDashboardScreen` / `AllRelayListScreen` for users who go looking before the banner triggers.
- **`amy relays health`** CLI command — fits the thin-assembly-layer rule (calls into `RelayHealthClassifier` from commons; produces JSON under `--json`). Per the `amy-expert` skill.
- **Configurable threshold** in settings (3d / 7d / 14d / 30d) — deliberately deferred to v1.
- **Replacement suggestions** — surface "popular healthy relays" from observed metrics when removing a relay. Separate feature.
- **Background scan when the app is open** — current trigger is launch-only; could re-scan every N hours via a coroutine. Not needed for v1 because `recordEvent` / `recordConnect` already update `lastSeenAt` live, so a flagged relay coming back drops out of the banner immediately.

## Documentation Plan

- Update `commons/ARCHITECTURE.md` to mention the new `commons/.../relayhealth/` package and its CLI-safe / UI split.
- Brief note in the CLAUDE.md "feed-patterns" / "account-state" sections if maintainers want it surfaced (optional).
- No user-facing changelog beyond the standard release notes.

## Sources & References

### Origin

- **Brainstorm document**: [docs/brainstorms/2026-06-10-unhealthy-relay-review-brainstorm.md](../brainstorms/2026-06-10-unhealthy-relay-review-brainstorm.md) — Carried forward: banner+sheet/popover approach (vs modal dialog / snackbar), all-platforms shared via commons, scope=10002/10050/10007/10006 (with 10006 detection-exclusion adjustment), immediate-Remove-no-undo, per-relay + global snooze, plain Open Dashboard (no pre-focus), first-run 7d quiet period.

### Internal References

- Tracking: `quartz/src/commonMain/.../client/listeners/RelayConnectionListener.kt:27-73`, `quartz/.../stats/RelayStat.kt:27-86`, `quartz/.../stats/RelayStats.kt:37-127`, `quartz/.../commands/toClient/EventMessage.kt:25`.
- Relay-list mutation: `commons/.../nip65RelayList/Nip65RelayListState.kt:127`, `amethyst/.../model/Account.kt:3274/3294/3349/3424`, `desktopApp/.../ui/relay/Nip65RelayEditor.kt:73,239`, `desktopApp/.../DeckColumnContainer.kt:476`.
- Persistence: `desktopApp/.../model/DesktopAccountRelays.kt:38,61,92,108,237`, `amethyst/.../LocalPreferences.kt:169,296`, `amethyst/.../model/AccountSettings.kt:1135-1155` (snooze precedent: `viewedPollResultNoteIds`).
- Banner: `desktopApp/.../ui/components/OfflineBanner.kt:44-101`, placement at `desktopApp/.../SinglePaneLayout.kt:100-103`, `desktopApp/.../DeckColumnContainer.kt:195-198`.
- Sheet/Popup: `amethyst/.../calendars/detail/AddToCalendarSheet.kt:64-80`, `desktopApp/.../ui/NoteActions.kt:73-74,492-496`.
- Nav: `amethyst/.../navigation/routes/Routes.kt:366,474`, `amethyst/.../AppNavigation.kt:391,412`, `desktopApp/.../DeckColumnContainer.kt:468-477`.
- App-start hooks: `desktopApp/.../desktop/Main.kt:860-870`, `amethyst/.../AppNavigation.kt:220-239`.
- Keys: `quartz/.../nip01Core/relay/normalizer/NormalizedRelayUrl.kt:25-30`.

### External References

- Material 3 — Banner & BottomSheet usage guidance: https://m3.material.io/components/banners/overview, https://m3.material.io/components/bottom-sheets/overview
- NIP-65 (Relay List Metadata): https://github.com/nostr-protocol/nips/blob/master/65.md
- NIP-17 / NIP-51 relay list kinds context (10050 DM relays, 10007 search, 10006 blocked).

### Related Work

- Embedded Local Relay plan: `desktopApp/plans/2026-05-09-embedded-local-relay-plan.md` — `OfflineBanner` and `BasicBundledInsert` debounce pattern come from this work.
- User memory notes: `~/.claude-account1/projects/.../memory/MEMORY.md` — `java.util.prefs.Preferences` desktop persistence, `rememberSubscription`-inside-AlertDialog caveat, relay-callbacks-on-background-threads note (informs the debounce design).
