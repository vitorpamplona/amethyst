---
title: Reuse Messaging Privacy Lock on Wallet
type: feat
status: active
date: 2026-07-07
origin: docs/brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md
depends_on: docs/plans/2026-06-30-feat-messaging-privacy-lock-plan.md
---

# Reuse Messaging Privacy Lock on Wallet

Extract the messaging-scoped pieces of the shipped **Desktop Privacy Lock**
(branch `feat/desktop-privacy-lock`) into a **scope-parameterised** privacy
lock, then apply the same gate — plus first-run banner and settings knobs —
to the Desktop **Wallet** deck column.

Goal in one line: **one master lock, one password**, gates Messages *and*
Wallet routes together, zero code duplication.

**Design finalised (2026-07-07):**
- Single master `lockEnabled` toggle protects both Messages and Wallet
  routes (per user decision — not per-scope enable).
- Single `firstRunCardSeen` flag (dismiss once = dismissed everywhere).
- `LockScope` enum exists only to route per-scope UI (lock-screen copy,
  independent idle timers, independent leave-route hooks). Settings
  surface is one flag.
- Wallet blur-on-unfocus blurs **text nodes only** (balance amount,
  addresses, invoice strings) — cards / structural layout stay visible.
- "No password set" branch in the Wallet gate **deep-links** to
  Settings → Privacy lock (not just an error message).
- Ships as **one PR** stacked on `feat/desktop-privacy-lock`.

> Explicitly called out as a follow-up in the messaging-privacy-lock plan:
> > **Wallet (NWC) gate** — reuse the same `MessagesLockGate` plumbing to
> > gate the Wallet deck column. Already on the feature backlog.
> (see plan: `docs/plans/2026-06-30-feat-messaging-privacy-lock-plan.md`
> §Future Considerations)

## Overview

Privacy-lock feature currently protects **Messages only**. Financial data
arguably more sensitive: passer-by seeing an NWC balance, a sats-in-flight
receipt, or a QR-linked lightning address is worse than a DM. Wallet also a
fast surface — opening the Wallet column loads the balance immediately, and
NWC receive/send dialogs display payloads on-screen.

This plan **reuses ~90 %** of the messaging-privacy-lock scaffolding by
turning `MessagesLockState` into a **scoped** state holder, splitting
`lockEnabled` and `firstRunCardSeen` by scope, and applying the gate to
`WalletColumnScreen`. Password + failed-attempts + lockout schedule stay
shared (one password unlocks either scope) — matches Signal/WhatsApp
mental model.

### Deliverables

1. `LockScope` enum (`Messages`, `Wallet`) — the single new type.
2. `PrivacyLockState` (renamed from `MessagesLockState`) parameterised by
   `LockScope`; one instance per scope, both provided via CompositionLocal at
   the App root.
3. `PrivacyLockSettings` gains **per-scope** `lockEnabled` and
   `firstRunCardSeen`. Password, inactivity timer, redaction level,
   failed-attempts, and lockout stay device-global.
4. Shared `LockScreen()` composable takes a scope; renders scope-aware title
   + subtitle strings.
5. `DesktopWalletLockGate` — 30-line wrapper mirroring
   `DesktopMessagesLockGate`; also drives `applyWindowCaptureBlock` and
   blur-on-unfocus overlay while the Wallet column is visible.
6. `WalletFirstRunBanner` — inline card at top of Wallet column, mirroring
   `MessagesFirstRunBanner`.
7. `PrivacyLockSettingsScreen` gets a second card ("Lock the Wallet tab") +
   shared subtree for password, inactivity, redaction.
8. Strings genericised: existing `messages_*` keys stay for Messages, new
   `wallet_*` mirrors added; a small set of neutral keys added under
   `privacy_lock_*` for shared UI (title bar, section header, password
   subtree).

### Out of scope for v1

- Android wallet gate — messaging lock does target Android, but wallet
  feature backlog emphasises Desktop; Android wallet gating trivial to add
  once `PrivacyLockState` scoped, but parked under Future Considerations to
  keep the PR bounded.
- Per-note wallet controls (ReactionsRow zap button, ZapCustomDialog,
  UpdateZapAmountDialog). Already prompt OS credentials via
  `authenticate()` in `UpdateZapAmountDialog.kt:394-490`. Gating them again
  would double-prompt. Called out under §System-Wide Impact.
- `amy` CLI `wallet` verbs — currently amy does not expose NWC actions. If
  they land, they should re-use `PrivacyLockPreferences` for parity.

## Problem Statement

Amethyst Desktop shows the wallet column with a single sidebar click.
Balance auto-fetches on open; NWC receive/send dialogs render invoices and
destination addresses inline. Anyone walking past a logged-in install can:

- Read the balance in sats.
- See past-payment counterparties in the on-chain zap gallery.
- Trigger the receive dialog and screenshot a lightning invoice belonging to
  the account owner.
- Trigger the send dialog and see recently-used destinations.

Messaging-privacy-lock ships a gate that closes exactly this class of leak
for DMs. Users asking for wallet protection (the driving ask that motivated
this plan) are asking for the *same* gate applied to the *same* fast surface
with the *same* UX contract:

- Off by default; opt-in via a first-run banner or Settings toggle.
- One shared OS credential / password already established for Messages.
- Idle-timer and leave-route re-lock.
- No extra friction for actions that already gate on OS credentials (nsec
  export, zap-amount changes).

App-wide lock rejected during the messaging brainstorm as too coarse.
Per-scope opt-in matches Signal (`Screen Lock`), WhatsApp (`Chat Lock`), and
the existing shipped behaviour.

## Proposed Solution

### One master lock, one password

Per user decision: **a single master `lockEnabled` toggle gates both
Messages and Wallet routes together.** No per-scope enable flags.

```
PrivacyLockSettings
├── lockEnabled : StateFlow<Boolean>                          UNCHANGED (single master flag)
├── firstRunCardSeen : StateFlow<Boolean>                     UNCHANGED (single, shared)
├── passwordHashed : StateFlow<String?>                       UNCHANGED (shared)
├── inactivityTimer : StateFlow<InactivityTimer>              UNCHANGED (shared)
├── dmRedactionLevel : StateFlow<DmRedactionLevel>            RENAMED from `redactionLevel` (Messages-only semantics)
├── failedUnlockAttempts : StateFlow<Int>                     UNCHANGED (shared)
└── lockedUntilEpochMs : StateFlow<Long?>                     UNCHANGED (shared)
```

**Cascade on password clear:** when `passwordHashed → null`,
`PrivacyLockSettings` sets `lockEnabled → false` automatically (per user
decision Q8). This closes the "toggle stays on but no credential exists"
edge case without a UI dance.

Rationale:

| Setting | Per-scope? | Why |
|---|---|---|
| `lockEnabled` | ❌ | Single master toggle per user decision — enabling protects both Messages and Wallet simultaneously. Simplifies settings surface and matches "one lock, everything sensitive" mental model. |
| `firstRunCardSeen` | ❌ | Dismiss once, dismissed everywhere. User already knows the feature exists after seeing it in either route. |
| `passwordHashed` | ❌ | One password unlocks any gated route. Matches OS-keychain / device-credential precedent. |
| `inactivityTimer` | ❌ | Timing is policy, not scope. Global. |
| `dmRedactionLevel` | ❌ | DM notification redaction — no wallet analogue on Desktop today. Keep Messages-scoped semantics. |
| `failedUnlockAttempts` / `lockedUntilEpochMs` | ❌ | Rate-limit is anti-brute-force — must be global counter. |

### Two lock states, one prompter

```
LocalPrivacyLockState[Messages]  ← MessagesLockGate reads
LocalPrivacyLockState[Wallet]    ← WalletLockGate reads
LocalCredentialPrompter          ← both gates share (unchanged)
LocalPrivacyLockSettings         ← both gates + settings screen share (unchanged)
```

`PrivacyLockState` is created twice at the App root — one per scope. Both
instances read the **same** `lockEnabled` and `firstRunCardSeen` flags.
Each has its own idle-timer Job and its own `LockState` StateFlow
(Locked ↔ Unlocked ↔ Disabled) so that:

- Unlocking Messages does *not* automatically unlock Wallet (each route
  demands its own credential prompt when the user enters it — this is a
  policy choice: the master lock protects *entry*, but re-entering a
  gated route is a fresh unlock).
- Idle timer runs per-scope so the currently-visible route drives the
  re-lock, and the *other* route stays Locked without a running timer.
- Leaving one route does not affect the other's state.

Writes to `failedUnlockAttempts` and `lockedUntilEpochMs` go through
shared `PrivacyLockSettings` and therefore apply to both gates
simultaneously — exactly the anti-brute-force property we want.

### Copy update

The existing `LockScreen()` in `MessagesLockGate.kt` hard-codes
`"Messages locked"` and `"Unlock to read or send messages"`. Refactor to
accept an `@StringRes` (Android) / string-key (Desktop) title and subtitle
so the same composable serves both scopes.

Wallet copies:

| Slot | Wallet copy |
|---|---|
| Title | *"Wallet locked"* |
| Subtitle | *"Unlock to see your balance and send or receive sats."* |
| First-run banner title | *"Lock the Wallet tab?"* |
| First-run banner body | *"Require a password before the Wallet column shows. Feed, profile, and Messages stay open."* |

Messages copies unchanged.

## Technical Approach

### Architecture

```
                  ┌───────────────────────────────────┐
                  │   PrivacyLockSettings              │  device-global (jvmAndroid)
                  │   ─ lockEnabled(scope) 2×         │  ← NEW: keyed by LockScope
                  │   ─ firstRunCardSeen(scope) 2×    │  ← NEW: keyed by LockScope
                  │   ─ passwordHashed                 │  shared
                  │   ─ inactivityTimer                │  shared
                  │   ─ failedUnlockAttempts           │  shared
                  │   ─ lockedUntilEpochMs             │  shared
                  └────────────────┬──────────────────┘
                                   │
             ┌─────────────────────┼──────────────────────┐
             │                                            │
┌────────────▼────────────┐              ┌────────────────▼────────────┐
│  PrivacyLockState        │              │   PrivacyLockState           │
│  (scope = Messages)      │              │   (scope = Wallet)           │
│  ─ state: StateFlow<Lock>│              │   ─ state: StateFlow<Lock>   │
│  ─ own idle-timer Job    │              │   ─ own idle-timer Job       │
└──────┬───────────────────┘              └───────────────┬──────────────┘
       │                                                  │
┌──────▼──────────────────────────┐        ┌─────────────▼────────────────┐
│  DesktopMessagesLockGate         │        │  DesktopWalletLockGate       │
│  (unchanged public API)          │        │  (NEW — 30 LOC mirror)       │
│  wraps DesktopMessagesScreen     │        │  wraps WalletColumnScreen    │
└──────────────────────────────────┘        └──────────────────────────────┘
```

Symmetry: code path from `WalletLockGate` to unlock is byte-for-byte
identical to `MessagesLockGate` — different scope enum, different string
keys.

### Reuse-vs-New Matrix

| Component | Status | Location | Action |
|---|---|---|---|
| `PrivacyLockSettings` interface | ♻️ Evolve | `commons/.../privacylock/` | Split enabled+seen into scope-accessor fns |
| `PreferencesPrivacyLockSettings` | ♻️ Evolve | `commons/jvmAndroid/.../privacylock/` | Add scope-suffixed prefs keys + legacy migration |
| `MessagesLockState` | 📦 Rename | `commons/.../privacylock/` | Rename → `PrivacyLockState`, add `scope: LockScope` |
| `LocalMessagesLockState` | 📦 Rename | (companion) | → `LocalPrivacyLockState: Map<LockScope, PrivacyLockState>` |
| `LockState` sealed hierarchy | ✅ Reuse | `commons/.../privacylock/` | Unchanged |
| `InactivityTimer` enum | ✅ Reuse | `commons/.../privacylock/` | Unchanged |
| `DmRedactionLevel` | ✅ Reuse | `commons/.../privacylock/` | Optional rename → `DmRedactionLevel` stays, semantics scoped-out to Messages |
| `CredentialPrompter` interface | ✅ Reuse | `commons/.../ui/privacylock/` | Unchanged |
| `PasswordHasher` | ✅ Reuse | `commons/.../privacylock/` | Unchanged |
| `IdleTimerModifier` | ✅ Reuse | `commons/.../ui/privacylock/` | Unchanged (Modifier already scope-agnostic) |
| `MessagesLockGate` composable | ♻️ Shrink | `commons/.../ui/privacylock/` | ~15 LOC wrapper reading `scope=Messages` |
| `WalletLockGate` composable | 🆕 New | `commons/.../ui/privacylock/` | ~15 LOC mirror |
| Shared `LockScreen(scope,title,subtitle,unlockLabel)` | 🆕 Extract | `commons/.../ui/privacylock/` | Extracted from MessagesLockGate |
| `DesktopMessagesLockGate` | ♻️ Consume | `desktopApp/.../security/` | Point at new shared `LockScreen` |
| `DesktopWalletLockGate` | 🆕 New | `desktopApp/.../security/` | ~60 LOC mirror of `DesktopMessagesLockGate` |
| `MessagesFirstRunBanner` | ♻️ Adjust | `desktopApp/.../security/` | Reads `firstRunCardSeen(Messages)` |
| `WalletFirstRunBanner` | 🆕 New | `desktopApp/.../security/` | Mirror; reads `firstRunCardSeen(Wallet)` |
| `SetPasswordDialog` | ✅ Reuse | `desktopApp/.../security/` | Unchanged (password stays shared) |
| `PrivacyLockSettingsScreen` | ♻️ Two toggles | `desktopApp/.../ui/settings/` | Add Wallet toggle card + section headers |
| `DeckColumnContainer` — Wallet branch | ♻️ Wrap | `desktopApp/.../ui/deck/` | 3-line change — wrap `WalletColumnScreen` with `DesktopWalletLockGate` |
| `WindowCaptureBlock` route set | ♻️ Extend | `desktopApp/.../platform/` | Set expanded to `{Messages, Wallet}` |
| `WalletColumnScreen` | ⚠️ Avoid rewriting | `desktopApp/.../ui/wallet/` | Only insert `WalletFirstRunBanner` at top of column; body unchanged |
| Android `AmethystApp` — provide both scopes | ♻️ Provider | `amethyst/` | 4-line change — `LocalPrivacyLockState` map with 2 entries |
| `Main.kt` App root — provide both scopes | ♻️ Provider | `desktopApp/jvmMain/` | 4-line change |
| Strings — new `wallet_*` keys, rename shared `messages_lock_*` → `privacy_lock_*` | ♻️ | Android + Desktop | +6 keys, ~4 renames |

**Legend:** ✅ Reuse · 📦 Rename · ♻️ Evolve · 🆕 New · ⚠️ Avoid

### Data Migration

Because `lockEnabled` and `firstRunCardSeen` stay single-key under the
master-lock model, **no prefs key migration is required**. The only
rename touching persisted state is `redaction_level_ordinal` (unchanged
key name; only the Kotlin-side identifier renames to `dmRedactionLevel`).

Existing prefs keys retained as-is:

```
lock_enabled            // master flag, unchanged
first_run_card_seen     // shared, unchanged
password_hashed         // unchanged
inactivity_timer_ordinal // unchanged
redaction_level_ordinal  // unchanged (Kotlin var renamed to dmRedactionLevel)
failed_unlock_attempts   // unchanged
locked_until_epoch_ms    // unchanged
```

This is a pure additive change from the persistence layer's point of
view — Wallet gate simply reads the same flag Messages gate already
reads.

### Implementation Phases

#### Phase 1 — Genericise the state holder (foundation)

Rename + parametrise **without** changing wire behaviour yet. Both
`MessagesLockGate` and Desktop wrapper still work; nothing else changes.

Files to create / modify:

- **NEW** `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/privacylock/LockScope.kt`
  ```kotlin
  package com.vitorpamplona.amethyst.commons.privacylock

  enum class LockScope { Messages, Wallet }
  ```
- **RENAME** `commons/.../privacylock/MessagesLockState.kt` →
  `PrivacyLockState.kt`
  - Rename class → `PrivacyLockState`, add constructor
    `scope: LockScope`.
  - Store `scope` on the instance; pass through to
    `settings.lockEnabled(scope)` /
    `settings.firstRunCardSeen(scope)`.
  - Companion: replace `LocalMessagesLockState:
    ProvidableCompositionLocal<MessagesLockState>` with
    `LocalPrivacyLockState:
    ProvidableCompositionLocal<Map<LockScope, PrivacyLockState>>`.
  - Add extension:
    `@Composable fun lockStateFor(scope: LockScope) =
    LocalPrivacyLockState.current.getValue(scope)`.
- **MODIFY** `commons/.../privacylock/PrivacyLockSettings.kt` interface:
  - Replace `val lockEnabled: StateFlow<Boolean>` with
    `fun lockEnabled(scope: LockScope): StateFlow<Boolean>`.
  - Same for `firstRunCardSeen`.
  - Same for setters: `setLockEnabled(scope, enabled)`,
    `setFirstRunCardSeen(scope, seen)`.
  - `passwordHashed`, `inactivityTimer`, `redactionLevel`,
    `failedUnlockAttempts`, `lockedUntilEpochMs` — unchanged.
  - Update `companion object` constants:
    - `KEY_LOCK_ENABLED = "lock_enabled_"` (prefix; scope name appended)
    - `KEY_FIRST_RUN_CARD_SEEN = "first_run_card_seen_"` (prefix)
    - `KEY_SCHEMA_VERSION = "schema_version"`
    - `CURRENT_SCHEMA_VERSION = 2`
- **MODIFY** `commons/jvmAndroid/.../privacylock/PreferencesPrivacyLockSettings.kt`:
  - Add per-scope `MutableStateFlow<Boolean>` maps:
    `Map<LockScope, MutableStateFlow<Boolean>>` for enabled and seen.
  - Seed each entry synchronously from prefs (respecting the deep-link
    race fix in the messaging-privacy-lock plan H1).
  - Add legacy-key migration in `init` block (see §Data Migration).
  - Setters write to the scope-suffixed key.
- **RENAME + EXTEND** `commons/commonTest/.../privacylock/MessagesLockStateTest.kt`
  → `PrivacyLockStateTest.kt`. Add tests:
  - `test_two_scopes_have_independent_state` — Messages Locked, Wallet
    Disabled, no cross-talk.
  - `test_shared_failed_unlock_counter` — a failure in Messages scope
    ticks the counter Wallet-scope reads.
  - `test_migration_from_legacy_prefs_keys` — write legacy keys, load
    settings, assert Messages scope has the value, Wallet default false,
    legacy keys removed, `schema_version = 2` written.

Ship this phase as its own commit — no UI changes; keeps `git bisect`
useful.

**Acceptance:**

- [x] `./gradlew :commons:jvmTest --tests "*PrivacyLockState*"` green (all 5 existing + 3 new)
- [x] `./gradlew :desktopApp:compileKotlin` green (only rename+delegate calls updated)
- [ ] `./gradlew :amethyst:assembleDebug` green

#### Phase 2 — Extract `LockScreen`, add `WalletLockGate`

- **NEW** `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/privacylock/LockScreen.kt`
  - Extract `@Composable private fun LockScreen()` currently inline in
    `MessagesLockGate.kt`.
  - Make `internal`, take
    `scope: LockScope, title: String, subtitle: String, unlockLabel: String`.
  - No behaviour change beyond parameterisation.
- **SHRINK** `commons/.../ui/privacylock/MessagesLockGate.kt` to a
  ~15-line wrapper that fetches `lockStateFor(LockScope.Messages)`,
  `DisposableEffect(onLeaveRoute)`, and delegates the locked branch to
  `LockScreen(LockScope.Messages, stringRes(R.string.privacy_lock_messages_title), …)`.
- **NEW** `commons/.../ui/privacylock/WalletLockGate.kt` — 15-line mirror.
  Scope = `Wallet`. Strings from
  `R.string.privacy_lock_wallet_title` /
  `R.string.privacy_lock_wallet_subtitle`.

Test coverage: unit tests on `PrivacyLockState` cover the state
transitions; the gate composable is minimal and Compose-tested only via
the manual sheet.

**Acceptance:**

- [ ] `MessagesLockGate` public signature unchanged (no caller changes)
- [ ] `WalletLockGate` exposes the same `content: @Composable () -> Unit` lambda
- [ ] Extracted `LockScreen` renders the correct title/subtitle for whichever scope invokes it

#### Phase 3 — Desktop: `DesktopWalletLockGate` + first-run banner + capture-block

- **NEW** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/security/DesktopWalletLockGate.kt`
  — mirror `DesktopMessagesLockGate.kt`. Only differences from Messages
  version:
  - Reads `lockStateFor(LockScope.Wallet)` instead of Messages.
  - Renders *"Wallet locked"* title, *"Enter your privacy-lock
    password to view the wallet."* subtitle.
  - `stored == null` branch: *"No password is set yet."* + button
    **"Open Settings"** that navigates via
    `SinglePaneState.navigate(DeckColumnType.Settings)` and (if the
    settings screen supports section anchors) deep-links to the
    Privacy-lock section. Falls back to plain Settings navigation if
    no anchor available (Q5 deep-link).
  - No independent password-hashing / lockout math — those come from
    shared `PrivacyLockSettings`.
- **NEW** `desktopApp/.../security/WalletFirstRunBanner.kt` — mirror
  `MessagesFirstRunBanner.kt`. Only differences:
  - Reads `firstRunCardSeen(LockScope.Wallet)`.
  - Enable button writes `setLockEnabled(LockScope.Wallet, true)` and
    marks scope=Wallet card seen.
  - Text as per §Copy update table.
  - Icon = `MaterialSymbols.Lock` (same as Messages) — no new codepoint,
    so no font-subset regeneration needed.
- `DesktopWalletLockGate` also drives capture-block and blur-on-unfocus
  the same way the Messages gate does — expand `WindowCaptureBlock.kt`
  so both routes flip the flag when the master lock is enabled AND the
  corresponding route is visible.
- **Wallet blur mode** — per user decision Q4, blur only sensitive text
  nodes, not the whole column. Implementation:
  - New `Modifier.privacyLockBlurWhenUnfocused()` extension in
    `desktopApp/.../platform/` that reads `LocalWindowFocus.current` and
    applies `Modifier.blur(radius = 16.dp)` only when unfocused AND
    `lockEnabled == true`.
  - Apply this Modifier to Text composables that display: balance sats
    amount, lightning invoice string, on-chain address, NWC connection
    URI, and any transaction memo. **Do NOT** apply to card containers,
    icons, or button rows — the visual layout stays intact.
  - Grep target: any `Text(text = ...sats...)`, `Text(text = invoice)`,
    `Text(text = address)` in `WalletColumnScreen.kt`,
    `OnchainSection.kt` (if reused in Desktop), and NWC dialogs.

Wire into `DeckColumnContainer.kt`:

```kotlin
DeckColumnType.Wallet -> {
    DesktopWalletLockGate {
        WalletColumnScreen(
            account = account,
            accountManager = accountManager,
            relayManager = relayManager,
            localCache = localCache,
            nwcConnection = nwcConnection,
            appScope = appScope,
            onZapFeedback = onZapFeedback,
        )
    }
}
```

Place `WalletFirstRunBanner` at the top of `WalletColumnScreen`'s Column
(mirroring where `MessagesFirstRunBanner` sits in the Messages column
entry).

**Acceptance:**

- [ ] Toggling `LockScope.Wallet` on in Settings → next Wallet column open shows the lock screen
- [ ] Correct password (verified against shared `passwordHashed`) unlocks
- [ ] Wrong password 5 times → lockout applies to **both** scopes (verified by observing Messages column also blocked)
- [ ] Leaving the Wallet column re-locks it
- [ ] Idle timer configured via shared `inactivityTimer` setting re-locks Wallet after N minutes
- [ ] Screen-capture protection engages while Wallet column visible (macOS: `NSWindowSharingNone`; Windows: `WDA_EXCLUDEFROMCAPTURE`)
- [ ] Blur-on-unfocus overlay renders over the Wallet column when the Amethyst window loses focus (16 dp radius, matches Messages)

#### Phase 4 — Settings screen: two toggles, shared subtree

Refactor `desktopApp/.../ui/settings/PrivacyLockSettingsScreen.kt`
minimally — with the single-master-lock design, the shipped screen
already has the right shape. Only cosmetic + copy changes:

- **Rename** the master-lock card header from *"Lock the Messages tab"*
  → *"Lock the app"* (or *"Enable privacy lock"* — pick one, see
  the strings table).
- **Update body copy** for the master-lock card to name what it protects:
  *"Require your password before Messages and Wallet columns show. Feed,
  profile, and search stay open."*
- **Update caveat text** at top of screen: replace
  *"This lock hides the Messages column…"* with *"This lock hides the
  Messages and Wallet columns on an unattended device. See the caveats
  below."*.
- Password / inactivity / redaction cards unchanged.

Layout order top-to-bottom (unchanged from shipped except copy):

```
Section header: "Privacy lock"
├── Card: "Privacy-lock password"       (shared — always visible)
├── Card: "Enable privacy lock"          (single master toggle)
├── Card: "Auto-lock after"              (visible when master toggle is on)
├── Card: "DM notification previews"     (visible when master toggle is on)
└── Card: "Caveats"                       (shared — always visible)
```

**Acceptance:**

- [ ] Toggling the master lock on with no password → prompts to set one (existing behaviour)
- [ ] Toggling the master lock on locks **both** Messages and Wallet on next entry
- [ ] Toggling the master lock off unlocks **both** immediately (transitions Locked → Disabled)
- [ ] Clearing the password auto-unsets the master toggle (Q8 cascade)

#### Phase 5 — Strings, migrations, docs, spotless

Strings to add / rename (Android `strings.xml` + Desktop
`messages.properties`):

Shared (renamed from `messages_lock_*` → `privacy_lock_*` where
applicable):

| Old key | New key | Notes |
|---|---|---|
| `messages_lock_setting_title` | `privacy_lock_settings_title` | section header |
| `messages_lock_screen_password_label` | `privacy_lock_screen_password_label` | shared |
| `messages_lock_screen_unlock_button` | `privacy_lock_screen_unlock_button` | shared |
| (new) | `privacy_lock_intro_body` | *"This lock hides the Messages column and/or the Wallet column on an unattended device."* |

Scope-specific (Messages keys stay verbatim; Wallet keys mirror them):

| Wallet key | Value |
|---|---|
| `privacy_lock_wallet_toggle_title` | *"Lock the Wallet tab"* |
| `privacy_lock_wallet_toggle_body` | *"Require a password before the Wallet column shows. Feed, profile, and Messages stay open."* |
| `privacy_lock_wallet_lockscreen_title` | *"Wallet locked"* |
| `privacy_lock_wallet_lockscreen_subtitle` | *"Unlock to see your balance and send or receive sats."* |
| `privacy_lock_wallet_firstrun_title` | *"Lock the Wallet tab?"* |
| `privacy_lock_wallet_firstrun_body` | *"Require a password before Wallet shows. Feed, profile, and Messages stay open."* |

Other tasks:

- Run legacy-key migration (Phase 1) on first startup after upgrade.
- Update `commons/ARCHITECTURE.md` — mention `LockScope` under the
  `privacylock/` package entry.
- Update `MEMORY.md` — add pointer to this plan alongside the
  messaging-privacy-lock pointer.
- `./gradlew spotlessApply`.
- Update manual testing sheet (see §Documentation Plan) — copy the
  Messages sheet, adjust for Wallet.
- Verify Crowdin sync propagates the new keys (existing PR pipeline
  already syncs; no new machinery needed).

**Acceptance:**

- [ ] `./gradlew :commons:jvmTest --tests "*privacylock*"` green
- [ ] `./gradlew :amethyst:assembleDebug` green
- [ ] `./gradlew :desktopApp:compileKotlin` green
- [ ] `./gradlew spotlessApply` clean
- [ ] Manual testing sheet passes (see §Documentation Plan)

## System-Wide Impact

### Interaction Graph

User clicks Wallet in sidebar →
`SinglePaneState.navigate(DeckColumnType.Wallet)` →
`DeckColumnContainer` composes Wallet branch →
`DesktopWalletLockGate` reads `lockStateFor(LockScope.Wallet).state` →

- If `Disabled` or `Unlocked` → `WalletColumnScreen` composes;
  `WalletFirstRunBanner` may render at top if user hasn't dismissed it
  and lock is disabled.
- If `Locked` → `LockScreen(scope = Wallet, title = "Wallet locked",
  subtitle = "Unlock to see your balance and send or receive sats.")`
  renders. On unlock success → `PrivacyLockState.onUnlockSuccess()` →
  `WalletColumnScreen` composes.

User leaves the Wallet column (navigates away, switches account, or
window closes) → `DesktopWalletLockGate.DisposableEffect.onDispose` →
`PrivacyLockState.onLeaveRoute()` for scope=Wallet only. Messages state
unaffected.

Cross-scope: if user is on Messages, unlocks, then navigates to Wallet,
the Wallet gate still shows (independent scopes). Same password →
Wallet unlocks. Matches settings UX: two toggles, one credential.

### Error Propagation

Wallet gate uses the identical `submit` path as `DesktopMessagesLockGate`
in the shipped code:

| Origin | Error | Handled at | Result |
|---|---|---|---|
| Wrong password | `PasswordHasher.verify → false` | `DesktopWalletLockGate.submit` | `showError = true`; `onFailedUnlockAttempt` increments **shared** counter |
| 5 consecutive failures | shared counter hits `LOCKOUT_TRIP_AFTER_FAILURES` | `PrivacyLockState.onFailedUnlockAttempt` | Shared `lockedUntilEpochMs` set → **both** scopes show the countdown supportingText |
| Password cleared while wallet Locked | `settings.passwordHashed → null` | `DesktopWalletLockGate.DesktopLockScreen` | *"No password is set yet"* branch renders; `Disable lock` button clears `lockEnabled(Wallet)` |
| Wallet toggle enabled but no password | Settings screen | Enable button triggers `SetPasswordDialog` first |
| Settings write fails (java.util.prefs full) | `PreferencesPrivacyLockSettings.setLockEnabled` | Existing best-effort semantics | Toggle reverts on next flow emit; user sees no confirmation |

### State Lifecycle Risks

| Risk | Mitigation |
|---|---|
| Wallet locked, incoming NWC balance/receipt event decrypts in background — plaintext held in memory | Same posture as messaging plan: cosmetic lock, not cryptographic. Balance StateFlow keeps last-known value. NWC responses continue to arrive on the coroutine scope; UI just doesn't render them until unlock. Honest and matches messaging behaviour. Called out in §Known Limitations. |
| App killed mid-unlock leaves Wallet stuck at Locked | State is in-memory; cold start re-reads `lockEnabled(Wallet)` from prefs → if enabled, starts Locked. Fail-safe. |
| User toggles Wallet off while Locked | `settings.lockEnabled(Wallet) → false` flows into `PrivacyLockState` which transitions `Locked → Disabled` on the next tick. Gate transparently shows content. Matches messaging behaviour. |
| Both scopes Locked, user in middle of a send-payment flow | Send-payment happens **inside** an already-unlocked scope; if idle timer fires mid-flow, the dialog stays composed (rememberSaveable) but the content behind is gated. Intentional — do not exempt in-flight payment dialogs from the timer. Manual test: `payment_flow_survives_timer.md`. |
| Concurrent leave-route events (Wallet + Messages navigating away simultaneously) | Each `PrivacyLockState` has its own idle-timer Job; no cross-scope races. |

### API Surface Parity

| Surface | Affected? | Notes |
|---|---|---|
| Android wallet UI (`OnchainSection`, `AddCashuWalletScreen`) | Deferred to v2 | Not touched in this plan — see §Future Considerations. |
| `amy` CLI | Not touched | CLI does not surface NWC actions today; when it does, use `PrivacyLockSettings.lockEnabled(Wallet)` for parity. |
| `UpdateZapAmountDialog.authenticate()` (nsec-key-guard biometric prompt) | Not affected | Separate OS-credential gate on the zap-amount-preferences change flow. Wallet gate is orthogonal — zap flow already gates OS credentials for a stronger reason. |
| One-click zap from a note (`ReactionsRow.RenderZapButton`) | Not gated | Wallet **column** is gated; zap **action** from feed context is not. Matches messaging: Messages **column** is gated; DM replies from a note thread are not (there aren't any). |
| Wallet notifications (NWC `success`, `failed`) | None on Desktop today | Desktop has no notification pipeline for wallet events. If added, use `redactionLevel` — but v1 keeps redaction Messages-only per §Proposed Solution. |
| Search results — NWC receipts / on-chain zaps | Not affected | `SearchBarViewModel` search-audit path from messaging plan already filters kinds 4/14/1059/443. NWC events (kind 23194/23195/23196) aren't searchable today. If they become searchable, add them to the audit list. |

### Integration Test Scenarios

1. **Cross-scope lockout**: Wallet locked. User enters 5 wrong
   passwords on Wallet screen. Then navigates to Messages (also locked
   via Messages toggle). Expected: Messages screen shows countdown
   supportingText, `Unlock` button disabled. Failure mode: counter
   scoped per-gate would defeat brute-force protection.
2. **Wallet lock + auto-fetched balance**: Wallet toggle just enabled;
   user has been on Wallet column with balance loaded. Setting flip →
   gate re-composes → balance is hidden behind the lock screen
   **immediately**. Failure mode: balance visible for one frame during
   transition.
3. **First-run banner interaction**: Fresh install → Messages column
   visited → Messages banner shown, dismissed. User visits Wallet →
   Wallet banner shown independently (not shared dismissal). Failure
   mode: shared `firstRunCardSeen` would suppress Wallet banner.
4. **Toggle-disable while Locked**: User is on the Wallet lock screen →
   opens Settings → Privacy lock → toggles Wallet off → returns to
   Wallet. Expected: content shows without unlock. Failure mode: state
   stays Locked because the settings update didn't cascade.
5. **NWC connect flow while locked**: New user, no NWC connected,
   Wallet toggle on. Expected: `WalletColumnScreen`'s connect UI is
   gated behind the lock — a Locked-gate does not let unauth users
   trigger NWC pairing. Desired security posture.
6. **Password change → shared re-verify**: User changes password while
   only Messages is locked. Then enables Wallet. Wallet lock screen
   accepts the **new** password (not the old one). Failure mode: two
   password hashes cached separately.
7. **Migration from legacy prefs**: User on a build with the shipped
   `feat/desktop-privacy-lock` (single `lock_enabled` key) upgrades to
   this build. Expected: Messages toggle preserved; Wallet toggle
   defaults off. Legacy prefs keys removed; `schema_version = 2`
   written. Failure mode: users get silently un-locked on upgrade, OR
   migration re-runs and clobbers a subsequent Wallet toggle.

## Acceptance Criteria

### Functional

- [ ] `LockScope` enum shipped in `commons/commonMain`
- [ ] `PrivacyLockState` replaces `MessagesLockState`; each scope has an
      independent `state: StateFlow<LockState>` and idle-timer Job
- [ ] `PrivacyLockSettings.lockEnabled` and `firstRunCardSeen` are
      scope-accessor functions
- [ ] Password / inactivity timer / redaction / failed-attempts / lockout
      remain device-global (shared)
- [ ] `MessagesLockGate` public signature unchanged; wired to
      `lockStateFor(Messages)`
- [ ] `WalletLockGate` composable shipped in
      `commons/.../ui/privacylock/`
- [ ] Shared `LockScreen(scope, title, subtitle, unlockLabel)` composable
      replaces the inlined lock screen inside MessagesLockGate; both
      gates render it
- [ ] `DesktopMessagesLockGate` unchanged in behaviour; consumes the new
      shared `LockScreen`
- [ ] `DesktopWalletLockGate` shipped; wraps `WalletColumnScreen` inside
      `DeckColumnContainer`
- [ ] `MessagesFirstRunBanner` unchanged in behaviour
- [ ] `WalletFirstRunBanner` shipped at top of `WalletColumnScreen`
- [ ] Settings screen renders two toggles + shared password subtree +
      shared inactivity timer + Messages-only redaction card
- [ ] Legacy prefs migration runs on first startup after upgrade — old
      `lock_enabled` value moved to `lock_enabled_Messages`, then old key
      removed; `schema_version = 2` written
- [ ] `applyWindowCaptureBlock(true)` engages when either lock is enabled
      AND the corresponding route is visible
- [ ] Blur-on-unfocus overlay renders over Wallet column when window
      loses focus AND `lockEnabled(Wallet) == true`

### Non-Functional

- [ ] No measurable startup regression (≤ +5 ms cold start on top of
      messaging-lock baseline)
- [ ] `PrivacyLockState.state` reads are constant-time regardless of
      scope count (Map lookup, no reflection)
- [ ] No new deps added — everything stays inside kotlinx.coroutines +
      Compose + the existing java.util.prefs / SharedPreferences setup
- [ ] Password comparison stays constant-time via `PasswordHasher.verify`
      (unchanged)
- [ ] No visible flash of Wallet content on cold start when
      `lockEnabled(Wallet) = true` — seeded synchronously (deep-link race
      fix H1 from messaging plan applies to both scopes)

### Quality Gates

- [ ] `./gradlew :commons:jvmTest --tests "*privacylock*"` green (8 tests)
- [ ] `./gradlew :amethyst:assembleDebug` green
- [ ] `./gradlew :desktopApp:compileKotlin` green
- [ ] `./gradlew :desktopApp:packageDmg` green on macOS host
- [ ] `./gradlew :desktopApp:packageMsi` green on Windows host (best effort)
- [ ] `./gradlew :desktopApp:packageDeb` green on Linux host
- [ ] `./gradlew spotlessApply` clean
- [ ] Manual testing sheet
      (`docs/plans/2026-07-07-wallet-lock-manual-testing.md`) executed
      and signed off

## Success Metrics

- **Adoption proxy**: after 30 days on nightly, at least half the users
  who enabled the Messages lock have also enabled the Wallet lock. If
  the ratio is far lower, the discoverability (first-run banner
  placement + settings copy) needs rework.
- **Stability proxy**: zero support reports of *"wallet stuck at
  locked"* or *"wrong password after change"* in the first 30 days.
- **Regression proxy**: no new issues on Messages lock after this PR
  merges — the refactor keeps behaviour identical for the Messages path.

## Dependencies & Prerequisites

- **Blocked on**: `feat/desktop-privacy-lock` merged into main. This
  plan builds on top of that shipped feature; extracting into a
  scope-parameterised state holder while the messaging code is still
  on a branch would create merge-conflict hell.
- **No new deps**: everything reuses the shipped `androidx.biometric.ktx`,
  `com.sun.jna:jna`, java.util.prefs, SharedPreferences, Compose
  Multiplatform.
- **No native shims added**: Touch ID `.dylib`, Windows credprompter,
  `NSWindowSharingNone` / `WDA_EXCLUDEFROMCAPTURE` shims — all already
  shipped by `feat/desktop-privacy-lock`. This plan just adds the Wallet
  route into the set that flips the flag.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Migration bug leaves a Messages user un-locked on upgrade | Medium | High (silent security regression) | Migration is copy-then-delete; version-gated by `schema_version = 2`; unit-tested; runs once and no-ops afterwards |
| Per-scope idle timers get out of sync (e.g. two timers on different Jobs miscoordinate) | Low | Low | Each `PrivacyLockState` is a self-contained state machine; no cross-scope coordination; unit test asserts independence |
| Users confused by two toggles + one password | Medium | Low (UX) | Settings copy: password card explicitly says *"One password. Applies to any tab you lock below."* Manual testing sheet includes a UX-clarity checkpoint |
| Wallet balance flashes visible on cold start | Low | High (privacy leak) | Same synchronous seed as messaging (H1). Compose-test asserts no-flash invariant on Wallet route too |
| Shared failed-attempts counter causes friction — a user mistyping in Wallet locks out Messages | Verified | Low | Intended behaviour — brute-force protection is a global property. Copy in the lockout supportingText clarifies: *"Too many failed attempts. Try again in ${countdown}."* — same message on both scopes |
| Refactor breaks `MessagesLockGate` on the shipped branch | Medium | High | Phase 1 is behaviour-preserving; Phase 2 preserves `MessagesLockGate`'s public signature; verified by a full manual pass on the shipped Messages testing sheet |
| ProGuard strips scope-based lookups | Low | Medium | `LockScope` is a simple enum — ProGuard-safe. Confirm during Phase 1 packaging |

## Future Considerations

- **Android wallet gate.** When Android wallet is elevated to a first-class
  destination (currently the wallet lives in a subscreen, not a tab), wrap
  its Compose entry point with `WalletLockGate` — no state-holder change
  required; `PrivacyLockState[Wallet]` already exists.
- **amy CLI wallet verbs.** If `amy wallet balance` / `amy wallet send`
  land, they should refuse to run when
  `PrivacyLockPreferences.lockEnabled(Wallet)` is `true` — closes the
  "run amy on a shared machine to snapshot the balance" gap.
- **Per-transaction OS-credential re-prompt on Wallet send.** Optional
  belt-and-suspenders: when a send-payment exceeds a user-configurable
  threshold (e.g. 10k sats), fire the same
  `UpdateZapAmountDialog.authenticate()` prompt. Tracks separately —
  this plan is about the column gate, not per-action gates.
- **Third scope: nsec / account settings.** Once we have `LockScope`, we
  could add `LockScope.Account` to gate the Account backup screen.
  Today that screen already uses OS-credential re-prompts, so added
  value is marginal.
- **`redactionLevel` extension for wallet notifications.** If Desktop gets
  a notification pipeline for NWC events (balance changes, incoming
  zaps), add a `WalletRedactionLevel` and gate the same way DM
  notifications are gated. Currently no such pipeline exists.

## Documentation Plan

- `commons/ARCHITECTURE.md` — update the `privacylock/` package entry:
  mention the `LockScope` enum and the "one settings, many scopes"
  contract.
- `docs/plans/2026-07-07-wallet-lock-manual-testing.md` — new manual
  testing sheet mirroring
  `docs/plans/2026-06-30-privacy-lock-manual-testing.md`. Include the 7
  integration test scenarios above as concrete steps.
- No changes needed to `desktopApp/CLAUDE.md` — no new native shim.
- `MEMORY.md` — index entry alongside the messaging-privacy-lock work.
- Release notes: extend the "Privacy & Security" section from
  messaging-privacy-lock with a one-line Wallet addition.

## Sources & References

### Origin

- **Brainstorm document**:
  [`docs/brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md`](../brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md)
  — where the "reuse for Wallet" follow-up was explicitly enumerated as
  a Future Consideration.
- **Predecessor plan**:
  [`docs/plans/2026-06-30-feat-messaging-privacy-lock-plan.md`](2026-06-30-feat-messaging-privacy-lock-plan.md)
  — carried-forward decisions: (a) OS credentials only, (b) device-global
  settings, (c) inactivity timer + leave-route re-lock, (d) synchronous
  initial-state seed for the deep-link race fix, (e) shared password
  hashing + exponential-backoff lockout.

### Internal References

- Extracted from:
  `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/privacylock/MessagesLockState.kt`
  (on branch `feat/desktop-privacy-lock`)
- Extracted from:
  `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/privacylock/MessagesLockGate.kt`
  (on branch `feat/desktop-privacy-lock`)
- Extracted from:
  `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/security/DesktopMessagesLockGate.kt`
- Wallet column entry:
  `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/wallet/WalletColumnScreen.kt:88`
- Deck integration site:
  `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/deck/DeckColumnContainer.kt:469-479`
- Settings screen:
  `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/settings/PrivacyLockSettingsScreen.kt`
- OS-credential biometric precedent:
  `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/UpdateZapAmountDialog.kt:394-490`
- CLAUDE.md — `commons/ARCHITECTURE.md` governs package taxonomy

### External References

- Signal Screen Lock (per-app opt-in, single credential):
  https://support.signal.org/hc/en-us/articles/360007059572
- WhatsApp Chat Lock (per-chat, single credential):
  https://about.fb.com/news/2023/05/whatsapp-chat-lock/
- Ledger Live "auto-lock all tabs" — this plan's per-scope model is
  weaker than Ledger's app-wide lock; intentional (matches Signal +
  WhatsApp UX and the brainstorm's explicit rejection of an app-wide
  lock).

### Related Work

- Messaging privacy lock plan (parent):
  `docs/plans/2026-06-30-feat-messaging-privacy-lock-plan.md`
- Desktop wallet + zapping (defines the surface being gated): memory
  pointer *"Desktop Wallet & Zapping"* — branch
  `feat/desktop-wallet-zapping`
- Account security hardening (concurrent work; `passwordHashed` storage
  lives in the same jvmAndroid source set that the account-security work
  touches — coordinate merge order):
  `docs/plans/2026-05-14-fix-account-security-hardening-plan.md`

## Open Questions — RESOLVED (2026-07-07)

1. **Merge order** — ✅ Solo PR stacked on `feat/desktop-privacy-lock`.
2. **Lock granularity** — ✅ **Single master lock** protects both Messages
   and Wallet. No per-scope enable flag. Single `firstRunCardSeen` too.
3. **Wallet first-run banner on empty NWC** — Show anyway (feature is
   valuable pre-connect).
4. **Blur-on-unfocus for Wallet** — ✅ Blur **text nodes only** (balance
   amount, addresses, invoices). Cards / structural layout stay visible.
   Implementation: apply `Modifier.blur(16.dp)` at the Text-composable
   level for sensitive strings, not the LazyColumn wrapper.
5. **"No password set" branch behaviour** — ✅ Deep-link to Settings →
   Privacy lock section (not just show the message).
6. **Rename `redactionLevel` → `dmRedactionLevel`** — ✅ Yes. Kotlin-side
   only; persisted key `redaction_level_ordinal` stays for compatibility.
7. **`LockScope` package** — ✅ Inside existing `privacylock/` package.
8. **Cascade `passwordHashed → null` unsets `lockEnabled`** — ✅ Yes.
   Implement in `PreferencesPrivacyLockSettings.setPasswordHashed(null)`
   → also `setLockEnabled(false)` atomically.
