---
title: Desktop DM Reliability
type: feat
status: active
date: 2026-06-10
origin: docs/brainstorms/2026-06-10-desktop-dm-reliability-brainstorm.md
---

# ✨ Desktop DM Reliability

## Overview

Two-track program to close the reliability gap between Amethyst Desktop's NIP-17 DMs and the reference clients **wisp.mobile** (Kotlin/Compose, github.com/barrydeen/wisp) and **nospeak.chat** (SvelteKit, github.com/psic4t/nospeak):

1. **Track A — Reliability plumbing.** Port the publish-path, AUTH, subscription, and discovery patterns that make wisp/nospeak feel reliable. Most are small surgical fixes; together they close the "messages silently disappear" failure modes.
2. **Track B — Bunker speed.** Spec + implement a NIP-46 `get_conversation_keys` batch RPC so bunker users decrypt N gift wraps in 1 round-trip instead of N — Vitor's stated direction, replacing the rejected NIP-4E path.

Carried forward from brainstorm:
- Explicitly out of scope: NIP-4E adoption, NIP-29 group chats, NIP-04 cleanup, new DM UX features (typing/read receipts, attachments redesign)
- NIP-04 stays visible with `legacy` badge (brainstorm Q1)
- Bunker SEND latency shown as live progress (brainstorm Q3) — see Deepening §6
- Tier-2 AUTH consent = inline chat-column banner `[Once] [Always] [Never]` (brainstorm Q5)
- Self-copy wrap → remote DM relays only, not local relay (brainstorm Q7)
- Desktop-first; Android inherits `commons/` changes (brainstorm Q8)

## Deepening Synthesis (2026-06-10)

Eleven parallel review passes (skills + reviewers) revealed substantial corrections. **Six P0 security blockers, ~30% scope compression, plus architectural fixes.** Apply BEFORE `/ce:work`.

### Desktop-only scope (2026-06-10 amendment)

**This plan ships desktop-only.** Android may incidentally benefit from `commons/` and `quartz/` changes (it shares those modules), but no Android-specific code changes, no Android UI work, no Android-side audits, no Android tests in acceptance. If a `commons/` change has Android-visible behavior change, that's a side effect — not a goal — and we don't gate this plan on Android validation.

**Removed from scope:**
- ~~Android `AccountGiftWrapsEoseManager.kt:55-61` `since` fix~~ — defer to Android pass
- ~~Android `Account.kt:1156-1167` security-fix audit~~ — same Android pass
- "Android inherits" framing in acceptance criteria
- Cross-platform `User.dmInboxRelays()` audit beyond desktop callers (still touch the commons helper; just don't validate Android consumers)
- Splitting `RetryQueueCoordinator` into commons-interface + desktop-impl — desktop-only, single file in `desktopApp/`
- Splitting `AccountAuthApprovals` for Android inheritance — keep desktop-side if simpler; if natural to put in commons it stays there but no Android UI ships

### Phase restructure (simplicity + scope)
- **R1 collapses to verification + KDoc.** Desktop already passes no `since` (`DesktopRelaySubscriptionsCoordinator.kt:345`). Add a regression test confirming wraps with `created_at = now - 1.5d` arrive; drop the `since` parameter from `FilterDMs.giftWrapsToMe` to lock the invariant. No longer a phase — one item under Phase 2.
- **Cut R6 (proactive window-focus re-AUTH)** as a separate coordinator. Replace with: use Compose-native `LocalWindowInfo.isWindowFocused` + `snapshotFlow`; let AUTH heal *lazily* on next `auth-required:` via the existing `RelayAuthenticator.checkAuthResults → syncFilters` path. **The plan's "force AUTH via benign kind:0 sub" trick is wrong** — most relays only AUTH-challenge on restricted REQs.
- **R10 (self-copy)**: already half-implemented via `BaseDMGroupEvent.groupMembers() = recipients.plus(pubKey)`. On desktop, port the pre-consume + alias-note pattern (Android has it; we replicate the *technique* in `DesktopIAccount`, not import the Android code) + route self-wrap to `account.dmInboxRelays()`, not `connectedRelays`.
- **R11 (relay hint on p-tag)**: a one-line change in `GiftWrapEvent.create:117-122`; keep but no separate sub-phase.
- **R12**: drop as standalone scope item — compress to a single regression test under Phase 5.
- **Phase 6 decouple**: spec PR + bunker batch RPC has external coordination dependencies (nsec.app/Amber/Keychat). Spin into its own plan file; Phases 1–5 ship independently.
- **Manual relay-entry dialog → simple error message** (Phase 4): replace `DmInboxRelayMissingDialog` UI with a Snackbar "Can't find DM relays for `<name>`. They need to publish their NIP-17 inbox first." Only re-introduce manual entry if security validation requirements (F-02) are met.

### Cross-cutting corrections

**P0 security blockers (must fix before merge):**

| ID | Issue | Fix |
|---|---|---|
| F-01 | Indexer fan-out uses authenticated client → identity-key leak to `purplepag.es` etc. | Open a dedicated `NostrClient` with `RelayAuthenticator` NOT attached; use it for all `RecipientRelayFetcher` calls. Add unit test: indexer sends AUTH → client sends NO AUTH event. |
| F-02 | Manual relay-entry has no URL validation | If we ship manual entry at all: hard-reject non-`wss://`; Levenshtein-1 typosquat warning vs curated set; "this DM will be visible to this relay operator" confirmation interstitial. **Default: drop the dialog entirely** per simplicity reviewer; use a Snackbar error. |
| F-03 | Current `RelayAuthenticator.authenticate()` (`quartz/.../auth/RelayAuthenticator.kt:81-94`) auto-signs **every** challenge with no rate limit + no tier check + across **all** logged-in accounts (multi-account linkage leak) | `shouldAutoAuth` must **REPLACE** the unconditional path, not be added in front. Per-account scoping. Rate-limit: max 1 AUTH/relay/60s, max M AUTHs/min account-wide. Default = do NOT sign unless tier-1. |
| F-04 | Retry queue stores plaintext recipient pubkey + relay URL + timestamp + last_error → social graph leak via disk forensics | Account-delete purges `retry_queue WHERE account_pubkey = ?`; 24h hard TTL on `created_at`; verify directory perms 0700; or store under `DesktopAccountStorage` AES-GCM wrapper. |
| F-07 | Plan says relay hint on "seal's p tag" — seal has NO p tag. NIP-17 spec puts hint on wrap (`["p", recipientPubkey, relay-url]`, GiftWrapEvent kind:1059). | Update plan to "wrap's p tag per NIP-17 spec"; regression test asserting it's there. (Security agent argued for rumor; but the rumor is encrypted, so other devices can't read the hint until AFTER decrypt — defeats its purpose. Spec is correct.) |
| F-10 | NIP-46 batch RPC response untrusted | Spec PR mandates: `result.length == request.pubkeys.length`, positions match, MAC self-test on first decrypt, bunker echoes `request.id`. Client validates on every call. |

**Architecture corrections (move/rename, no behavior change):**

| Subject | Plan says | Correct |
|---|---|---|
| Indexer-relay set | `commons/.../relayClient/dm/` | `commons/defaults/` |
| `relayClient/dm/` package | `dm/` | `nip17Dm/` (match siblings) |
| `AccountAuthApprovals` ViewModel | `commons/.../viewmodels/` | `commons/.../relayClient/auth/` (colocated with feature) |
| `ConversationKeyCache` | `commons/.../service/cache/` (path doesn't exist) | `quartz/.../nip46RemoteSigner/cache/` |
| `shouldAutoAuth` tier classifier | Quartz `RelayAuthenticator` | `commons/.../relayClient/auth/AuthApprovalPolicy.kt`; Quartz takes a `Set<RelayUrl>` of pre-approved relays via existing `signWithAllLoggedInUsers` lambda seam |
| `RetryQueueCoordinator` | `desktopApp/...` only | Split: `commons/.../service/RetryQueueCoordinator` (interface + no-op default for Android) + `desktopApp/.../SqliteRetryQueueCoordinator` (impl) |
| AUTH state shape | `MutableStateFlow<Map<RelayUrl, RelayAuthStatus>>` (status is a mutable holder — won't emit on inner change) | `MutableStateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>>` (immutable snapshot, identity changes on update) |
| `authCompleted` event | New `SharedFlow`/`Channel` | Derive from `authStatusFlow.scan` transitions; no new primitive needed |
| Path key | `pubkey8` (8-hex prefix, collision risk) | Full 64-hex pubkey; one-time rename migration |
| SQLite tables location | `LocalRelayStore.kt` events.db | **Sibling `outbox.db`** with `PRAGMA synchronous = NORMAL` (events.db has `synchronous = OFF` — unsafe for "durable send" semantics) |

**Data-integrity schema rewrite** (apply to Phase 3 + Phase 2):

```sql
-- ~/.amethyst/accounts/<FULL-pubkey>/outbox.db (sibling to events.db)
-- synchronous = NORMAL; journal_mode = WAL; foreign_keys = OFF

CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
-- bind file to account: INSERT meta('account_pubkey', '<full hex>')

CREATE TABLE auth_approvals (
  account_pubkey TEXT    NOT NULL,
  relay_url      TEXT    NOT NULL,
  scope          TEXT    NOT NULL CHECK (scope IN ('always','blocked')),
  granted_at     INTEGER NOT NULL,
  expires_at     INTEGER,
  PRIMARY KEY (account_pubkey, relay_url),
  CHECK (length(account_pubkey) = 64),
  CHECK (relay_url LIKE 'wss://%' OR relay_url LIKE 'ws://%')
) WITHOUT ROWID;
CREATE INDEX auth_approvals_expiry ON auth_approvals(expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE retry_queue (
  account_pubkey  TEXT    NOT NULL,
  gift_wrap_id    TEXT    NOT NULL,
  relay_url       TEXT    NOT NULL,
  rumor_id        TEXT    NOT NULL,
  event_json      TEXT    NOT NULL,
  attempt         INTEGER NOT NULL DEFAULT 0,
  max_attempts    INTEGER NOT NULL DEFAULT 12,   -- raised from 8; backoff seq 1,2,4,8,16,32,64,128,256,512,600,600s
  next_attempt_at INTEGER NOT NULL,
  last_error      TEXT,
  created_at      INTEGER NOT NULL,
  PRIMARY KEY (account_pubkey, gift_wrap_id, relay_url),
  CHECK (length(account_pubkey) = 64),
  CHECK (length(gift_wrap_id)   = 64),
  CHECK (length(rumor_id)       = 64),
  CHECK (attempt >= 0 AND attempt <= max_attempts),
  CHECK (max_attempts > 0 AND max_attempts <= 32),
  CHECK (length(event_json) < 200000),
  CHECK (relay_url LIKE 'wss://%' OR relay_url LIKE 'ws://%')
) WITHOUT ROWID;
CREATE INDEX retry_queue_due ON retry_queue(account_pubkey, next_attempt_at) WHERE attempt < max_attempts;
```

`LocalRelayMaintenance.kt` must purge expired AUTH approvals + dead-letter retry_queue rows older than 30d.

**Performance corrections (apply throughout):**

- **Per-rumor `StateFlow`, not global map** (Phase 3). Replace `MutableStateFlow<Map<EventId, MessageDeliveryState>>` with `LargeCache<EventId, MutableStateFlow<MessageDeliveryState>>` — each bubble subscribes to its own flow; 50 visible bubbles × global map churn = ~75k unnecessary recompositions otherwise. Non-optional.
- **Delete-bundler or periodic sweep for retry_queue** (Phase 3). Per-OK DELETEs fsync individually → 100 OKs = 100 transactions ≈ 5s of IO. Either add a 250ms bundler or have the coordinator sweep `attempt=0 AND created_at > 60s` every 30s.
- **Bunker concurrency cap** (until Phase 6 ships). Wrap `NIP17Factory.createWraps`' `mapNotNullAsync` in a `Semaphore(4)` when `signer is NostrSignerRemote`. Today: 5 recipients × 2 calls = 10 concurrent bunker round-trips saturate the bunker socket.
- **Indexer fan-out: first-result + pre-warm + persistent cache** (Phase 4). 8s `fetchAll` timeout → first-message latency 1-3s. Short-circuit on first non-empty result after 2s; pre-warm on conversation-list render; persist LRU cache across restart.
- **Retry-queue triggers reactive, not 1s polling** (Phase 3). `Channel<Unit>(CONFLATED)` + `select { wake.onReceive(); onTimeout(nextDue) }`. Triggered by enqueue, authCompleted, network reconnect.
- **`withTimeout(15s)` on `client.publish`** inside retry coordinator — wedged-socket protection.

**NIP-17 protocol corrections:**

- `User.dmInboxRelays()` at `commons/.../model/User.kt:115` **silently falls back to `inboxRelays()` (NIP-65 read marker)** when kind:10050 missing. This is the FIRST silent leak layer (before `DesktopIAccount.connectedRelays` fallback). Cross-platform bug. Fix: add `dmInboxRelaysStrict()` returning null on missing; audit all 4 callers. **Android `Account.kt:1156-1167` has the same fallback bug** — security fix applies cross-platform, NOT desktop-only.
- `RecipientRelayFetcher.fetchRelayLists` returns kind:10050 + 10051 + 10002. `DmInboxRelayResolver` must use **`lists.dmInbox` only**, NOT `dmInboxOrFallback` (which falls through to NIP-65 read).
- Shared `rumor.created_at` applies to rumor (kind 14) ONLY; seal (13) and wrap (1059) `created_at` MUST stay independently randomized per NIP-17 §"randomized up to 2 days back."
- Drop `purplepag.es` from indexer set (not authoritative for kind:10050); add `purplerelay.com`. Curated set: `relay.nos.social`, `relay.damus.io`, `nos.lol`, `relay.nostr.band`, `purplerelay.com`.
- Multi-indexer agreement: only trust a kind:10050 if ≥2 indexers return the same `event.id`. One compromised indexer can otherwise mass-redirect DMs.

**NIP-46 batch RPC corrections (Phase 6):**

- Method name: **`nip44_get_conversation_keys`** (consistent with `nip44_encrypt`/`nip44_decrypt`), not `get_conversation_keys`.
- Request params: variadic `[pk1, pk2, ..., pkN]` (matches existing `nip44_encrypt` shape), NOT one stringified JSON blob.
- Response: `result = JSON.stringify(["base64key1", ...])` (NIP-46 mandates single-string result). Errors = all-or-nothing; client falls back to per-call.
- **Drop `result.capabilities` mechanism.** Use optimistic probe + per-bunker-pubkey negative-cache for the session. Adding capabilities expands the spec PR surface.
- **2 sequential round-trips** (wrap layer keys → peel wraps → seal layer keys), not 1 parallel. Acceptance criterion: `≤4` round-trips for 200 wraps (100-pubkey spec cap).
- Two-tier cache: NO cache for ephemeral wrap pubkeys (single-use), LRU 1000 for sender-identity seal keys. Cache key = `(selfPubkey, peerPubkey)`. Wipe on logout AND account-switch.
- Bunker validation: assert `result.length == params.length`, position binding, MAC self-test on first decrypt.

### Open questions resolved during deepening

- Tier-3 silent drop → **dropped from design**. Only 2 tiers: auto / prompt (user picks `[Once|Always|Never]`).
- Indexer-relay Settings UI → **dropped**. Hardcoded curated 5; override via system property `-Damethyst.dmIndexers=...` if needed.
- Bunker progress "N of M" → **simplified to spinner + "Encrypting…"**. Counter requires extending `SigningOpState` with `current,total` fields; net UX win is small.
- `account_pubkey` in SQLite schema → **kept** but constraint-bound to `meta('account_pubkey')` for defense-in-depth.
- Conversation-key cache TTL → **session-only**, wipe on logout/switch, no disk.
- Self-copy fallback → **own DM relays only**; no NIP-65 fallback (avoids the same leak class).

## Problem Statement

The Amethyst lead's prompt cited cross-client NIP-17 working reliably between nospeak.chat ↔ wisp.mobile and asked whether NIP-4E was the missing piece for bunker users. Research showed three things:

1. **Neither nospeak nor wisp uses bunker, and neither implements NIP-4E.** Their reliability comes from publish-path semantics + AUTH retry + idempotency.
2. **Vitor (Amethyst maintainer) NACKs both NIP-4E PRs** (#1647, #2361) on five technical grounds — trial-decryption pathology, custody downgrade, no rotation story, nsec loses recovery, fragmentation across own devices. His counter is the NIP-46 batch RPC.
3. **Amethyst Desktop's DM path has concrete reliability gaps** the survey identified:
   - AUTH-walled subscriptions die silently when the 3-try cap in `PoolEventOutboxState.Tries.isDone()` is hit
   - No persistent retry queue — `DmSendTracker` 10s-timeouts and resets
   - **Security bug**: `DesktopIAccount.sendNip17PrivateMessage` falls back to `connectedRelays.value` when recipient has no kind:10050 — leaks DM to non-inbox relays (violates the 2026-04-20 "block DM fallback" decision)
   - No bubble-level per-message delivery feedback (DmSendTracker is global to the composer, not keyed by EventId)
   - Android's kind:1059 sub still passes `since`, silently dropping wraps with randomized 2-day-past timestamps
   - 10050 lookup never fans out to indexer relays — if user's `LocalCache` doesn't have the recipient's 10050, it falls through to the buggy fallback above

Goal: send a DM and have visible confirmation it landed (or visible reason it didn't), survive bunker timeouts and AUTH-walled relays without silent drops, and let bunker users open a 200-wrap inbox in seconds instead of minutes.

## Proposed Solution

Six phases. Phases 1–5 are Track A (Reliability), Phase 6 is Track B (Bunker speed, parallel). Each phase is independently shippable.

```
Phase 1 — Receive resilience (R1)            ┐
Phase 2 — AUTH end-to-end (R2,R3,R5,R6)      │ Track A
Phase 3 — Send visibility (R7,R8 + bunker UI)│ (sequential)
Phase 4 — Discovery hardening + security fix │
         (R4,R11 + 10050 fallback)           │
Phase 5 — Correctness (R9,R10,R12)           ┘

Phase 6 — NIP-46 batch RPC (spec + impl)     — Track B (parallel)
```

## Technical Approach

### Architecture

The bulk of the change lives in `quartz/.../nip01Core/relay/client/` (publish path, AUTH state) and `commons/.../relayClient/` (filter assemblers, subscriptions). UI hooks are in `desktopApp/` (window focus listener, AUTH banner, bubble delivery indicator). Persistence lands in `desktopApp/.../desktop/relay/LocalRelayStore.kt` (existing SQLite, add tables).

**Key reusable existing infrastructure** (survey-discovered):
- `RelayAuthenticator.kt` already calls `syncFilters` on AUTH success — re-publishes pending outbox + re-sends REQs. The path exists; we extend it.
- `RecipientRelayFetcher` (`quartz/.../marmot/`) already fans out kind:10050/10002 lookups against a relay set — wire it into the DM send path.
- `LocalRelayStore` (`~/.amethyst/accounts/<pubkey8>/events.db`) + `BasicBundledInsert` (250ms batching) — host the retry queue table.
- `SigningState` pattern (shipped 2026-03-20) — reuse for bunker SEND progress UI.
- `RelayInsertConfirmationCollector` — pattern for per-OK aggregation; lift into a per-message delivery `StateFlow`.
- `geode/.../KtorRelayTest.kt:208,254` — mock Ktor relay with real `auth-required:` round-trip support. Reusable test infra.

### Phase 1 — Receive resilience (R1)

**Goal:** stop silently dropping inbound gift wraps.

**Scope:**
- Verify desktop kind:1059 sub does not pass `since` (already true — `DesktopRelaySubscriptionsCoordinator.kt:345` passes nothing → `FilterDMs.giftWrapsToMe(userPubKeyHex)` default `since=null`).
- Fix Android: `amethyst/.../AccountGiftWrapsEoseManager.kt:55-61` currently passes `since?.get(relay)?.time`. Replace with `since=null` (or relax to a wide window — e.g. `since - 30 days` for users that want bounded backfill).
- Document the invariant in code: add a KDoc on `FilterDMs.giftWrapsToMe` explaining that seal timestamps are randomized up to 2 days back per NIP-17, so `since` is unsafe.
- **Belt-and-braces**: change `FilterDMs.giftWrapsToMe` signature to drop the `since` parameter entirely. Forces all callers to be explicit.

**Files:**
- `desktopApp/.../desktop/subscriptions/FilterDMs.kt:125-133` — remove `since` param
- `amethyst/.../service/relayClient/reqCommand/account/nip59GiftWraps/AccountGiftWrapsEoseManager.kt:55-61` — drop `since` arg
- `commons/.../relayClient/nip17Dm/FilterGiftWrapsToPubkey.kt:31-49` — same

**Acceptance:**
- [ ] `FilterDMs.giftWrapsToMe` has no `since` parameter
- [ ] All call sites updated; build green
- [ ] Add unit test: subscribe to kind:1059 → server returns wrap with `created_at = now - 1.5 days` → wrap is received
- [ ] Add KDoc explaining the NIP-17 randomized-timestamp invariant

### Phase 2 — AUTH end-to-end (R2, R3, R5, R6)

**Goal:** AUTH-walled relays never silently drop messages, user-consents to tier-2 relays once and remembers.

**Scope:**

1. **Lift the 3-try cap for `auth-required:` responses.** In `PoolEventOutboxState.kt:64-93 newResponse`, if the response message starts with `auth-required:`, do NOT count it toward the `Tries.isDone()` budget. The retry happens once `RelayAuthenticator.checkAuthResults` → `syncFilters` fires.

2. **Expose AUTH state as a public `StateFlow`.** Convert `RelayAuthenticator.authStatusCache: LargeCache<RelayUrl, RelayAuthStatus>` into a `MutableStateFlow<Map<RelayUrl, RelayAuthStatus>>` so UI can subscribe. Add a flow event `authCompleted(relayUrl)` for downstream wakeups (re-subscribe to kind:1059 explicitly, refresh retry queue, etc.).

3. **Tiered AUTH classification** in `RelayAuthenticator.shouldAutoAuth(relayUrl, account)`:
   - **Tier 1 (auto-sign):** relay is in `account.outboxRelays` OR `account.dmInboxRelays` (own NIP-17 inbox) OR was previously approved.
   - **Tier 2 (prompt):** relay is marked `dmDeliveryTarget` (set by `DesktopIAccount.sendNip17PrivateMessage` before publish — mirrors wisp's `markDmDeliveryTarget`) OR was never seen before.
   - **Tier 3 (silent drop):** anything else.
   - Persist tier-2 approvals: new SQLite table `auth_approvals(account_pubkey TEXT, relay_url TEXT, scope TEXT, expires_at INT)` in `LocalRelayStore`. Scope `"once"` is in-memory only; `"always"` rows persist.

4. **Inline AUTH banner UX** (desktop only — Android inherits classification, separate UI pass later):
   - Add `AccountAuthApprovals` ViewModel exposing `MutableStateFlow<List<PendingAuthApproval>>`.
   - In `ChatPane` (the right pane of `DesktopMessagesScreen`), render an inline banner above the message list when an approval is pending: `"<relay-url> requires authentication to deliver this message. [Once] [Always] [Never]"`.
   - `[Once]` → grants for the current session, no persistence. `[Always]` → writes `auth_approvals(scope="always")`. `[Never]` → writes `auth_approvals(scope="blocked")`, drops the wrap.
   - **Survey precedent**: check current behavior in Coracle, Damus, Primal, 0xchat before final mockup (open question Q5 in brainstorm).

5. **Re-subscribe to kind:1059 on `authCompleted`** (R3):
   - Wire `RelayAuthenticator.checkAuthResults` to emit `authCompleted(relayUrl)` after a successful AUTH-OK.
   - The desktop `DesktopRelaySubscriptionsCoordinator` already calls `syncFilters` indirectly via outbox sync. Verify the kind:1059 REQ is re-sent on that relay specifically. Add an integration test using `geode/KtorRelayTest.kt` pattern: client connects → relay sends `AUTH challenge` → client sends `AUTH event` → relay OKs → relay sends gift wrap → client receives it.

6. **Proactive re-AUTH on window focus** (R6 desktop-only):
   - Register `WindowFocusListener` on the `ComposeWindow` in `desktopApp/.../Main.kt:316`.
   - On `windowGainedFocus`, push to `MutableStateFlow<Boolean>(focused)`.
   - A coordinator (e.g. `DesktopFocusReAuthCoordinator` under `desktopApp/.../desktop/coordinators/`) collects this flow; on `false → true` transition, calls `relayManager.client.reconnect(true)` (forces reconnect of dead sockets) AND for each AUTHENTICATED relay older than 5 min, triggers a no-op AUTH challenge (subscribe to a benign `kind:0` filter on that relay — relays re-issue AUTH challenges on subsequent REQs).

**Files (touch list):**
- `quartz/.../nip01Core/relay/client/pool/PoolEventOutboxState.kt` — `newResponse` skip `auth-required:` from try budget
- `quartz/.../nip01Core/relay/client/auth/RelayAuthenticator.kt` — convert cache to StateFlow, add `authCompleted` event, add `shouldAutoAuth` tier logic
- `quartz/.../nip01Core/relay/client/auth/RelayAuthStatus.kt` — extend with `lastAuthSuccessAt`
- `desktopApp/.../desktop/relay/LocalRelayStore.kt` — new `auth_approvals` table + helpers
- `desktopApp/.../desktop/Main.kt` — wire `WindowFocusListener`
- `desktopApp/.../desktop/coordinators/DesktopFocusReAuthCoordinator.kt` — new file
- `desktopApp/.../ui/chats/ChatPane.kt` — inline AUTH banner
- `commons/.../viewmodels/AccountAuthApprovals.kt` — new ViewModel (commons, so Android inherits later)
- `desktopApp/.../desktop/model/DesktopIAccount.kt:179-208` — call `relayPool.markDmDeliveryTarget(url)` before publish

**Acceptance:**
- [ ] Test: mock relay returns `auth-required:` → client signs AUTH → publishes → message accepted on retry. Outbox tries counter not incremented by `auth-required:`.
- [ ] Test: account has 3 outbox relays, 1 DM-inbox relay. Sending to a recipient whose DM relay is unknown → AUTH banner appears. `[Always]` persists across app restart.
- [ ] Test: AUTH state flow emits `Authenticated(url)` when AUTH-OK received. Subscriber on kind:1059 receives a wrap delivered to that relay after AUTH.
- [ ] Test: window unfocused → focused. Stale connections reconnect. Verify via mock-relay log.
- [ ] No regression: nsec-local user can still send + receive in &lt;1s end-to-end (no extra round-trips introduced).

### Phase 3 — Send-path visibility (R7, R8 + bunker progress UI)

**Goal:** every outgoing message has a visible delivery state per relay; no fire-and-forget; persistent retry across app restart.

**Scope:**

1. **Per-message delivery state** (R8):
   - Replace transient `DmSendTracker` with a persistent `MutableStateFlow<Map<EventId, MessageDeliveryState>>` keyed by **rumor id** (not gift-wrap id — multiple wraps share one rumor). `MessageDeliveryState` = `{relayDeliverySet: Map<RelayUrl, Confirmation>, sentAt, lastAttemptAt, error?}`.
   - In `quartz/.../accessories/RelayInsertConfirmationCollector.kt`, add a `collectByRumor(rumorId)` overload that aggregates OKs across all gift wraps for a rumor.
   - Surface in `DmConversationViewModel` so chat bubble subscribes per-bubble: `messageBubbleState(rumorId): StateFlow<MessageDeliveryState>`.
   - Bubble UI shows: `✓` (≥1 relay accepted), `✓✓` (all relays accepted), `⟳` (in flight), `⚠` (zero relays accepted after retry exhausted).

2. **Persistent retry queue** (R7):
   - New SQLite table in `LocalRelayStore`:
     ```sql
     CREATE TABLE retry_queue (
       id TEXT PRIMARY KEY,            -- gift_wrap_event_id || ":" || relay_url
       account_pubkey TEXT NOT NULL,
       rumor_id TEXT NOT NULL,
       event_json TEXT NOT NULL,       -- serialized GiftWrapEvent
       relay_url TEXT NOT NULL,
       attempt INT NOT NULL DEFAULT 0,
       max_attempts INT NOT NULL DEFAULT 8,
       next_attempt_at INT NOT NULL,
       last_error TEXT,
       created_at INT NOT NULL
     );
     CREATE INDEX retry_queue_next_attempt ON retry_queue(next_attempt_at);
     ```
   - `RetryQueueCoordinator` (new, `desktopApp/.../desktop/relay/RetryQueueCoordinator.kt`): on app start, scan retry_queue for `next_attempt_at < now`; for each, attempt `client.publish(event, listOf(relayUrl))`. On OK → delete row. On AUTH-required → wait for AUTH (Phase 2's flow already handles). On other rejection → exp-backoff `next_attempt_at = now + min(30s, 2^attempt seconds)`, `attempt++`. On `attempt >= max_attempts` → delete row, surface to UI as permanent failure.
   - Enqueue path: `DesktopIAccount.sendNip17PrivateMessage` calls `retryQueue.enqueue(wrap, relays)` BEFORE `client.publish` so we don't lose anything if the app dies between send and confirmation.
   - On OK from publish path → `retryQueue.confirm(wrapId, relayUrl)`.

3. **Bunker SEND progress UI** (brainstorm Q3 = "Live progress in send button"):
   - Reuse existing `SigningState` pattern from 2026-03-20 plan.
   - In `DesktopIAccount.sendNip17PrivateMessage`, wrap each `signer.sign()` call with progress emission: `SigningState.InProgress(current=2, total=5, label="Encrypting via remote signer")`.
   - Compose-side: send button shows linear progress + label when state is `InProgress`.
   - Only active when `signer is NostrSignerRemote`; nsec users see no change.

**Files:**
- `quartz/.../accessories/RelayInsertConfirmationCollector.kt` — add `collectByRumor`
- `commons/.../viewmodels/DmConversationViewModel.kt` — expose `messageBubbleState(rumorId)`
- `desktopApp/.../desktop/relay/LocalRelayStore.kt` — `retry_queue` table + DAO methods
- `desktopApp/.../desktop/relay/RetryQueueCoordinator.kt` — new
- `desktopApp/.../desktop/model/DesktopIAccount.kt` — enqueue → publish → confirm pattern, signing-state emission
- `desktopApp/.../ui/chats/ChatMessageBubble.kt` — render delivery indicators
- `desktopApp/.../ui/chats/MessageComposer.kt` — bunker progress

**Acceptance:**
- [ ] Send DM, kill app mid-publish (during bunker sign). Restart → retry queue drains → message lands.
- [ ] Send DM to 3 relays; relay #2 returns `auth-required:` while #1 and #3 OK. Bubble shows `✓ 2/3` immediately; after AUTH completes, updates to `✓ 3/3`.
- [ ] Bunker user sends to 5 recipients. Send button shows "Encrypting via remote signer (3 of 5)" until last signature lands.
- [ ] No retry-queue table growth in nsec-local mode under normal conditions.
- [ ] Retry queue respects per-account isolation (multi-account users don't see each other's queued sends).

### Phase 4 — Discovery hardening + security fix (R4, R11)

**Goal:** kind:10050 lookup is robust against missing/stale data; stop the silent metadata-leak fallback.

**Scope:**

1. **Indexer-relay fan-out for kind:10050** (R4):
   - New `DmInboxRelayResolver` (commons, so Android inherits). API: `suspend fun resolveDmInboxRelays(pubkey: HexKey): Result<List<RelayUrl>>`.
   - Wraps `RecipientRelayFetcher` (already in `quartz/.../marmot/`). Configures it with a discovery set (curated indexer relays).
   - LRU cache (100 entries, TTL 1h) on `(pubkey → relays)` results.
   - Decoupled from `relayManager.connectedRelays` — uses ephemeral connections to indexers.

2. **Security fix: stop falling back to `connectedRelays.value`** in `DesktopIAccount.sendNip17PrivateMessage:179` and twin methods (sendNip17EncryptedFile, sendGiftWraps).
   - New flow: `dmInboxRelays()` → if null → `DmInboxRelayResolver.resolveDmInboxRelays(recipient)` → if empty → **block send and surface UI prompt** "Could not find DM relays for <name>. [Enter manually] [Cancel]".
   - Never silently fall back to user's connected relays for DMs (matches 2026-04-20 Relay Power Tools decision).
   - The "[Enter manually]" path is a one-shot dialog with relay-URL chips; user-entered relays are NOT persisted to recipient's 10050 (we don't publish on their behalf), only used for this send.

3. **Indexer-relay set** (open question Q2 in brainstorm — decided here):
   - Hardcoded curated list in `commons/.../relayClient/dm/DefaultIndexerRelays.kt`:
     - `wss://purplepag.es`
     - `wss://relay.nos.social`
     - `wss://relay.damus.io`
     - `wss://nos.lol`
     - `wss://relay.nostr.band`
   - Configurable in Settings → DMs → "Inbox-relay discovery" (advanced). Default = curated list.

4. **Relay hint on `p` tags** (R11):
   - In `NIP17Factory.createWraps`, when building the seal's `p` tag for the recipient, include the recipient's primary DM relay URL: `["p", recipientPubkey, primaryDmRelay]`.
   - "Primary" = first relay from `resolveDmInboxRelays(recipient)` result. Empty string if unknown.

**Files:**
- `commons/.../relayClient/dm/DmInboxRelayResolver.kt` — new
- `commons/.../relayClient/dm/DefaultIndexerRelays.kt` — new
- `desktopApp/.../desktop/model/DesktopIAccount.kt:179-261` — three send methods updated
- `desktopApp/.../ui/chats/DmInboxRelayMissingDialog.kt` — new
- `quartz/.../nip17Dm/NIP17Factory.kt` — relay hint on `p` tag
- `desktopApp/.../ui/settings/DmSettingsScreen.kt` — new (indexer-relay config)

**Acceptance:**
- [ ] Recipient has no kind:10050 in our cache and indexers return nothing → user sees "Enter manually" dialog. No silent send to non-inbox relays.
- [ ] Recipient has 10050 in cache → no indexer call. Cache TTL respected (1h).
- [ ] Recipient has no 10050 in cache, indexers return [r1, r2] → cache populated, send proceeds.
- [ ] `p` tag in seal contains recipient's primary DM relay URL when known.
- [ ] Settings allows custom indexer set.
- [ ] **Security regression test**: send DM where recipient has no 10050 anywhere → verify zero outbound traffic to user's own outbox/general relays.

### Phase 5 — Correctness (R9, R10, R12)

**Goal:** group DMs, cross-device sync, and dedupe behave correctly.

**Scope:**

1. **Shared `rumorCreatedAt` across recipient wraps** (R9):
   - In `NIP17Factory.createWraps` (currently `quartz/.../nip17Dm/NIP17Factory.kt:43-72`), compute `rumorCreatedAt = TimeUtils.now()` once before the per-recipient `mapNotNullAsync` loop. Pass into every `SealedRumorEvent.create(...)` so all seals encode the same rumor (same `rumor.id`).
   - Same `rumorId` becomes the dedupe anchor + receipt target across all recipients of a group message.

2. **Self-copy gift wrap to own DM relays** (R10):
   - In each `DesktopIAccount.sendNip17*` method, after building wraps for all recipients, also build one wrap addressed to self.
   - Route to `account.dmInboxRelays` (or write relays as fallback per wisp's pattern). **NOT to local relay** (brainstorm Q7).
   - Pre-mark `LocalCache.seenGiftWraps[selfWrap.id]` (or equivalent) to avoid double-render when it loops back from the relay.

3. **Persistent seen-index** (R12) — verify, don't re-build:
   - The existing `LocalCache.consume()` + write-through to `LocalRelayStore` (`DesktopLocalCache.kt:216-219`) already provides on-disk dedupe across restart.
   - Add a regression test: kill desktop app with N gift wraps in-cache; on restart, re-deliver same wraps from a mock relay; verify they're rejected as duplicates at `LocalCache.consume` (no decryption attempt → no bunker round-trip).

**Files:**
- `quartz/.../nip17Dm/NIP17Factory.kt:43-78` — shared rumor created_at
- `desktopApp/.../desktop/model/DesktopIAccount.kt` — add self-copy in three send methods
- `commons/.../service/LocalCache.kt` (or `desktopApp/.../desktop/cache/DesktopLocalCache.kt`) — pre-mark seenGiftWraps if not already supported
- Tests in `desktopApp/.../jvmTest/` and `quartz/.../commonTest/`

**Acceptance:**
- [ ] Group DM to 4 recipients: all seals share one rumor.id. Reaction event targeting that rumor.id by recipient #2 is correctly received by sender and other recipients.
- [ ] Send DM from desktop install A; open same account on desktop install B. Self-copy arrives via 10050 → conversation appears on B.
- [ ] Kill app with 50 wraps in cache. Mock relay re-broadcasts same 50 wraps. App restart → decryption attempted 0 times (verified via signer-call counter).

### Phase 6 — NIP-46 batch RPC (parallel track)

**Goal:** bunker users receive N gift wraps in 1–2 round-trips instead of N.

**Spec proposal:**
- File NIP-46 PR in `nostr-protocol/nips` proposing method `get_conversation_keys`:
  ```
  Request:  { id, method: "get_conversation_keys", params: [pubkeys_json_array] }
  Response: { id, result: keys_json_array, error?: string }
  ```
- `pubkeys_json_array` = JSON-encoded array of hex pubkeys. Result is parallel array of base64-encoded 32-byte NIP-44 conversation keys (same key the bunker would derive for the corresponding `nip44_encrypt`/`nip44_decrypt`).
- Bunker MAY rate-limit or reject (e.g. if more than 100 pubkeys). Client falls back to per-call `nip44_decrypt` if `get_conversation_keys` returns error or capability not advertised.
- Capability advertised via NIP-46 `connect` response: `result.capabilities: ["get_conversation_keys"]` (or via a `get_capabilities` method if spec evolves).

**Coordination:**
- Open NIPs PR + cross-post to bunker maintainers:
  - **nsec.app** (Yegor) — github.com/nostrband/nsec.app
  - **Amber** (greenart7c3) — github.com/greenart7c3/Amber
  - **Keychat** — github.com/keychat-io
- Resolve open semantics question (brainstorm Q4):
  - For NIP-17, the receiver needs `ecdh(self, ephemeral_pubkey_in_each_wrap)` for the wrap layer, AND `ecdh(self, sender_pubkey)` for the seal layer.
  - Wrap layer: N ephemeral pubkeys → N keys. Pass all in one batch call.
  - Seal layer: M unique sender pubkeys (often M << N). Pass all in one batch call.
  - Net: 2 bunker calls instead of 2N. Confirmed acceptable shape.

**Amethyst-side implementation:**
- Capability probe: on bunker `connect`, parse `result.capabilities` (or fall back to a feature-flag pref).
- `RemoteSignerManager.getConversationKeys(pubkeys: List<HexKey>): List<ByteArray>` — new method. Sends one NIP-46 request, awaits response, parses keys.
- Wire into NIP-17 receive path: when `LocalCache.consume` ingests a batch of kind:1059 events, before decrypting, collect all unique ephemeral pubkeys + sender pubkeys, call `getConversationKeys` once, then decrypt locally with the returned keys.
- Wire into NIP-17 send path: per-recipient conversation key fetched once (cached), used for seal encryption locally.
- Cache conversation keys in-memory (LRU 500); wipe on logout. Conversation keys are NOT persisted — re-derivable from bunker on next session.

**Files:**
- `quartz/.../nip46RemoteSigner/signer/RemoteSignerManager.kt` — add `getConversationKeys`
- `quartz/.../nip46RemoteSigner/dto/` — new request/response DTOs
- `quartz/.../nip46RemoteSigner/signer/NostrSignerRemote.kt` — expose batch path
- `quartz/.../nip17Dm/NIP17Factory.kt` — switch to batch path when signer is remote and capability available
- `quartz/.../nip17Dm/Nip17Receiver.kt` or wherever wraps are decrypted (likely under `commons/.../service/`) — batch-decrypt path
- `commons/.../service/cache/ConversationKeyCache.kt` — new LRU
- `quartz/.../commonTest/.../GetConversationKeysTest.kt` — round-trip test with mock bunker

**Acceptance (gated on spec PR being open at least; impl can land behind capability flag):**
- [ ] NIPs PR opened with discussion-ready spec.
- [ ] Mock bunker test: client calls `get_conversation_keys([10 pubkeys])` → receives 10 keys → uses them to decrypt 10 wraps with 0 further bunker calls.
- [ ] Capability fallback: bunker doesn't advertise capability → client falls back to per-call `nip44_decrypt`. No regression.
- [ ] Inbox-load benchmark: 200 wraps via bunker. Without batch RPC: ~200 round-trips. With batch RPC: ≤2 round-trips. Measured via signer-call counter.

## Alternative Approaches Considered

| Alternative | Why rejected |
|---|---|
| **Implement NIP-4E (PRs #1647/#2361)** | Vitor (maintainer) NACKed both with 5 technical objections. Externalizes trial-decryption cost on every legacy peer. Politically infeasible. |
| **Read-side NIP-4E compat only** (honor peers' kind:10044 + n-tag on receive) | Spec contested, no merge in sight. Adds receive-path complexity for unclear win — Jumble + Coop are small. Defer until spec lands. |
| **Migrate to MLS/Marmot for DMs** | Larger orthogonal program. Marmot already in tree (per `quartz/.../marmot/`). Separate track. Doesn't solve NIP-17 reliability for users on non-MLS peers. |
| **Drop bunker support for DMs entirely** | wisp + nospeak do this; works but regresses Amethyst's bunker UX. Better path is to make bunker fast (Phase 6) than to drop it. |
| **Per-relay outbox max-tries config without auth-required carve-out** | Half-measure; doesn't solve the silent-drop case where AUTH succeeds AFTER 3 retries exhausted. The carve-out is required regardless. |
| **In-memory only retry queue** | Loses messages on app crash / kill. Persistent SQLite is cheap given `LocalRelayStore` already exists. |
| **Self-copy wrap to embedded local relay** (instead of remote DM relays) | Brainstorm Q7: rejected to match wisp behavior + keep local relay's "cache only" role. |

## System-Wide Impact

### Interaction Graph

**Outgoing DM (post-Phase 3):**
```
ComposeUI(send button click)
  → DmConversationViewModel.send(text)
    → DesktopIAccount.sendNip17PrivateMessage(text, recipients)
      → DmInboxRelayResolver.resolveDmInboxRelays(recipient)        ── Phase 4
        → RecipientRelayFetcher.fetch([indexer relays])
      → NIP17Factory.createWraps(text, recipients, signer)           ── shared rumor_created_at (Phase 5)
        → for each recipient (parallel):
          → SealedRumorEvent.create(rumor, recipient, signer)
            → signer.sign(seal) (bunker round-trip if Remote)         ── batch via Phase 6 capability
            → signer.nip44Encrypt(rumor, recipient) (bunker round-trip)── batch via Phase 6 capability
          → GiftWrapEvent.create(seal, recipient, ephemeralKey)
      → for each wrap:
        → retryQueue.enqueue(wrap, relays)                            ── Phase 3
        → client.publish(wrap, relays)
          → relay returns OK → retryQueue.confirm(wrapId, relayUrl)
          → relay returns auth-required → RelayAuthenticator handles  ── Phase 2
          → relay rejects → retryQueue.scheduleRetry(wrapId, relayUrl)
      → self-copy wrap published to own DM relays                     ── Phase 5
ChatBubble subscribes to messageBubbleState(rumorId)                 ── Phase 3
  → renders ✓ ✓✓ ⟳ ⚠ based on state changes
```

**Incoming DM (post-Phase 6):**
```
RelayConnection.onIncomingMessage(EventMessage(kind=1059))
  → LocalCache.consume(wrap)
    → if seen → drop                                                  ── Phase 5 (already exists)
    → batch collected by ingestion buffer (250ms window)
  → batch decrypt path:
    → collect unique sender pubkeys from wraps in batch
    → if signer is Remote AND batch capability: getConversationKeys() ── Phase 6
    → for each wrap: decrypt locally with cached key
  → on rumor decrypted → DesktopMessagesScreen.conversationFlow updates
```

### Error & Failure Propagation

| Layer | Error class | Today | Post-plan |
|---|---|---|---|
| Relay socket | `WebSocketDisconnected` | reconnect attempt, in-flight events tries-counted | reconnect, queue preserves event, retries on reconnect |
| Relay OK | `auth-required:` | counts toward 3-try cap, often silently dropped | NOT counted; held until AUTH completes; user sees banner if tier-2 |
| Relay OK | `pow:` / `replaced:` / `invalid:` | discarded (correct) | unchanged |
| Bunker RPC | `BunkerTimeout` (65s) | request continuation removed; late response discarded | retry queue re-attempts on next coordinator tick; bubble shows ⚠ |
| Bunker RPC | `DecryptCache` poisoning (per 2026-05-04 plan) | permanent cache poison until app restart | unchanged this plan — covered by prior plan |
| Bunker RPC | `get_conversation_keys` not supported | N/A | fall back to per-call `nip44_decrypt` |
| 10050 lookup | recipient has no 10050 anywhere | **falls back to user's connected relays (metadata leak)** | Phase 4: blocks send + prompts user for manual relay entry |
| Signer | `signer.sign` returns null | DmSendTracker → Failed → resets in 3s | retry queue keeps the event, scheduler retries; bubble stays ⟳ |

### State Lifecycle Risks

1. **Retry queue rows must be deleted on permanent failure or success, never orphaned.** Coordinator deletes on `attempt >= max_attempts` even if no UI sees it. Alternative: archive to `retry_queue_dead_letter` table for diagnostics.
2. **AUTH-approvals table must be account-scoped.** Multi-account users share the SQLite store across accounts but each row carries `account_pubkey`. Test: account A approves relay X "always"; account B sending to relay X gets tier-2 prompt independently.
3. **Indexer cache invalidation.** If recipient publishes a new 10050, our 1h TTL hides it. Mitigation: on receiving a fresh kind:10050 event for any user via the normal relay feed, eagerly update the cache.
4. **Self-copy wrap can race the original.** If self-copy lands first, recipient #1's wrap arrives second and we already have the rumor — dedupe at rumor.id should handle it. Test.
5. **Retry queue + AUTH banner can dual-drive UI** — if a wrap is queued AND the relay's AUTH is pending, we don't want two notifications. Coordinator suppresses retry attempts on relays in `AUTHENTICATING` state.
6. **Conversation-key cache (Phase 6) lives in memory only.** On logout / account switch, must wipe to prevent cross-account leakage.

### API Surface Parity

| Surface | Effect |
|---|---|
| Desktop NIP-17 send | full plan |
| Android NIP-17 send | inherits all `commons/` + `quartz/` changes. Android-specific UI (AUTH banner) NOT in this plan — separate Android pass. Android keeps current AUTH-prompt-less behavior until then; the underlying classifier still works (silent drops for tier-3, auto for tier-1, **silent drop for tier-2** — Android users with bunker won't see banner; safer than current). |
| CLI (`amy`) | `commons/` changes apply. CLI doesn't render banners. Tier-2 AUTH approvals via a config file (out of scope, file follow-up). |
| Marmot/MLS DMs | unaffected (separate event kinds + path) |
| NIP-04 legacy DMs | unaffected (no AUTH retry, no retry queue — legacy path stays as-is per brainstorm Q1) |

### Integration Test Scenarios

1. **AUTH retry across restart.** Send DM to relay R that demands AUTH. Sign + send AUTH. Kill app before AUTH-OK arrives. Restart. Verify retry queue resumes, AUTH handshake completes, original wrap accepted.
2. **Tier-2 prompt persistence.** Recipient has 10050 pointing to a relay user has never seen. Send → banner appears → user clicks `[Always]`. Send another DM to same recipient → no banner; relay AUTH'd silently using stored approval.
3. **No-10050 security path.** Recipient has no kind:10050 in our cache and on indexers. Send → dialog shows "Enter manually". Cancel → zero outbound traffic to general relays. Verify via mock-relay sniffer.
4. **Bunker batch RPC inbox load.** 200-wrap inbox, bunker user. Pre-plan: ~200 round-trips (~minutes). Post-plan with capability: ≤2 round-trips (~seconds). Measured via signer-call counter.
5. **Group DM rumor coherence.** Send to [A, B, C]. Each receives a wrap. A reacts to message → reaction `e` tag references shared `rumorId`. B and C see the reaction associated with the right message. Sender sees it too.
6. **Self-copy cross-device.** Account on desktop install X sends DM to recipient. Open same account on install Y (cold cache). Y's first 10050 fetch returns sender's own DM relays → self-copy wrap arrives → conversation pre-populates.
7. **Window-focus re-AUTH.** Mac sleeps 1h. Wake → focus desktop app → mock relay's AUTH challenges fire → client AUTHs all stale connections within 5s. No user input required.

## Acceptance Criteria

### Functional

- [ ] Phase 1: kind:1059 sub on both Desktop and Android passes no `since` (or a 30-day default at most). Wraps with timestamps 2 days in the past arrive.
- [ ] Phase 2: All five sub-items shipping (tier classifier, persisted approvals, banner, re-sub-on-auth-completed, focus re-AUTH).
- [ ] Phase 3: Per-message bubble delivery indicator. Persistent retry queue. Bunker progress UI.
- [ ] Phase 4: No silent fallback to user's connected relays for DMs. Indexer fan-out + manual entry dialog.
- [ ] Phase 5: Shared rumor.created_at. Self-copy wrap. Persistent dedupe verified.
- [ ] Phase 6: NIPs PR open. Capability negotiation + fallback. Batch decrypt wired for receive path.

### Non-functional

- [ ] No regression for nsec-local users: end-to-end DM round-trip stays under 1s on healthy relays.
- [ ] Bunker inbox load (200 wraps): post-Phase 6 ≤10s vs current ≥120s.
- [ ] Retry queue size stays under 100 rows under normal use (i.e. high-success-rate publish path keeps it empty most of the time).
- [ ] No new secrets persisted: AUTH approvals carry no key material; only relay URLs + scope flags.

### Quality gates

- [ ] All new code passes `./gradlew spotlessApply` + `./gradlew test`.
- [ ] Integration test count: ≥1 per phase, ≥7 total.
- [ ] Mock relay infra (`geode/.../KtorRelayTest.kt` pattern) reused where possible; new mock-bunker for Phase 6.
- [ ] No new uses of `runBlocking` in publish path.
- [ ] Code-review pass with `compose-expert`, `relay-client`, `auth-signers`, `nostr-expert` skills before merge per phase.

## Success Metrics

| Metric | Pre-plan baseline | Target |
|---|---|---|
| Silent message drops on AUTH-walled relays | unknown (likely common) | 0 |
| Inbox load time, 200 wraps via bunker | ~2 min | ≤10 s |
| Successful delivery rate on first try (nsec, healthy network) | ~95% (estimated) | ≥99% |
| Successful delivery rate including retry queue, 24h window | unknown | ≥99.5% |
| User reports of "DM never arrived" / "DM never sent" | baseline TBD | 50% reduction over 3 months |
| Crash/ANR rate on DM screen | baseline TBD | no regression |

## Dependencies & Prerequisites

- **Phase 6 blocked on NIPs PR consensus.** Spec authors (nsec.app/Amber/Keychat) need to weigh in. If consensus stalls, Phase 6 implementation can still land **behind a feature flag** as a discussion prototype.
- Phases 1–5 are sequential within Track A but each is independently shippable.
- Phase 4's manual-entry dialog needs design pass (no Figma assumed; brainstorm spec is the source).
- Mock bunker test infra for Phase 6 — small new utility, no external deps.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Phase 2 AUTH classifier wrongly tier-3-drops a legit relay | M | High (silent drop) | Default tier-3 to "log warning" not "silently drop" during rollout; flip later. Tier-1 + tier-2 catch the common cases. |
| Retry queue grows unbounded (e.g. dead relay) | M | M | `max_attempts=8` + exp-backoff caps total relay-time per event to ~10 min. Dead-letter table for inspection. UI surfaces "permanent failure" ⚠. |
| Phase 6 NIPs PR rejected | M | M | Implement Vitor's design as a discussion prototype regardless; if PR rejected, ship as Amethyst↔nsec.app/Amber bilateral capability negotiation (slightly worse interop, equivalent end-user outcome). |
| Security fix (Phase 4) breaks existing users who relied on the leaky fallback | L | L | Surface dialog + provide manual-entry fallback. Document migration in release notes. Add telemetry for "no-10050" send attempts in 1.0 to size the affected population. |
| Window-focus listener leaks on close | L | L | Standard `addWindowFocusListener` / `removeWindowFocusListener` pairing; unit-test via headless ComposeWindow. |
| Conversation-key cache (Phase 6) leaks across account switch | L | High (cross-account decryption) | Wipe `ConversationKeyCache` in `AccountStateHolder.onAccountChanged`. Unit test. |
| Persistent AUTH approvals get out of sync with relay's actual AUTH state | L | L | TTL of 30 days on `auth_approvals.expires_at`; re-prompt after expiry. |
| Android inheriting `commons/` changes regresses Android DM screens | M | M | Run full Android test suite after each phase; manual smoke test on Android emulator before merge. |
| Spec change in NIP-46 mid-implementation | M | M | Capability negotiation isolates Amethyst from spec churn; fallback path always works. |

## Resource Requirements

- Solo engineer; estimated 4–6 weeks of focused work for Phases 1–5, plus indefinite coordination for Phase 6.
- No new infrastructure / hosting.
- New dev-dep on a mock-bunker test utility (small, in-tree).

## Future Considerations

- **Receipts UX**: read receipts in wisp + nospeak use a "high water mark" per conversation. Out of scope here, natural follow-up plan.
- **NIP-04 visibility cleanup**: brainstorm Q1 said "keep legacy badge"; revisit when NIP-17 adoption hits a threshold.
- **Marmot/MLS DMs**: separate program. The reliability plumbing (AUTH, retry queue) is reusable — Phase 6's batch RPC concept does NOT apply (MLS uses different keying).
- **WoT inbox relays** (e.g. pyramid.fiatjaf.com/inbox) — open question Q5 in brainstorm. AUTH plumbing makes us publishable. Surface relay rejection messages to UI as toast for diagnostics. No special WoT machinery needed for now.
- **Android UI parity** for AUTH banner and bunker progress — separate Android-only pass, plan TBD.
- **Telemetry** — add anonymous metric `dm.delivery.outcome = {ok, retry, dropped}` (opt-in only, behind Settings flag).

## Documentation Plan

- Update `desktopApp/.../README.md` (if any) with new DM reliability features.
- Update `MEMORY.md` summary at end of work.
- Add `commons/ARCHITECTURE.md` entries for `DmInboxRelayResolver` and `RetryQueueCoordinator`.
- KDoc on new public surfaces: `RelayAuthenticator.authCompleted`, `DesktopIAccount.messageBubbleState`, `RemoteSignerManager.getConversationKeys`.
- Release-notes entries per phase (`docs/release-notes/`?). User-facing: "DMs now show per-relay delivery status", "AUTH-walled relays handled automatically", "Bunker users can open large inboxes much faster".

## Sources & References

### Origin

- **Brainstorm document**: [docs/brainstorms/2026-06-10-desktop-dm-reliability-brainstorm.md](../brainstorms/2026-06-10-desktop-dm-reliability-brainstorm.md).
  Key decisions carried forward: two-track umbrella (reliability + bunker speed), R1–R12 inventory, NIP-04 stays legacy, bunker progress UI, AUTH inline banner, self-copy → remote only, desktop-first Android-inherits.

### Internal references

| Concern | File:line |
|---|---|
| Desktop kind:1059 sub site | `desktopApp/.../subscriptions/DesktopRelaySubscriptionsCoordinator.kt:338-349` |
| Android kind:1059 sub site (needs fix) | `amethyst/.../AccountGiftWrapsEoseManager.kt:55-61` |
| Filter assembler (commons) | `commons/.../relayClient/nip17Dm/FilterGiftWrapsToPubkey.kt:31-49` |
| Filter assembler (desktop) | `desktopApp/.../subscriptions/FilterDMs.kt:125-133` |
| Publish path entry | `quartz/.../nip01Core/relay/client/NostrClient.kt:233-245` |
| Per-event outbox | `quartz/.../nip01Core/relay/client/pool/PoolEventOutboxState.kt:64-108` |
| AUTH state cache | `quartz/.../nip01Core/relay/client/auth/RelayAuthenticator.kt:57-104` |
| AUTH event builder | `quartz/.../nip42RelayAuth/RelayAuthEvent.kt` |
| Bunker signer manager | `quartz/.../nip46RemoteSigner/signer/RemoteSignerManager.kt:44-102` |
| NIP-17 factory | `quartz/.../nip17Dm/NIP17Factory.kt:43-78` |
| Recipient-relay fetcher | `quartz/.../marmot/RecipientRelayFetcher.kt:38-114` |
| Desktop send path (security bug) | `desktopApp/.../model/DesktopIAccount.kt:179-261` |
| DmSendTracker (to replace) | `desktopApp/.../ui/chats/DmSendTracker.kt:32-86` |
| Window state on desktop | `desktopApp/.../Main.kt:250-316` |
| LocalRelayStore (retry queue host) | `desktopApp/.../desktop/relay/LocalRelayStore.kt` |
| Mock-relay AUTH test infra | `geode/.../KtorRelayTest.kt:208,254` |
| Server-side AUTH test | `quartz/.../commonTest/.../nip01Core/relay/server/NostrServerAuthTest.kt` |

### Related prior plans (carry constraints / reuse infra)

- **2026-04-20 Relay Power Tools** — shipped "block DM fallback to all relays" decision (Phase 4 enforces). `desktopApp/.../docs/plans/2026-04-20-feat-relay-power-tools-plan.md`.
- **2026-05-04 Bunker Timeouts & Decryption** — shipped DecryptCache poisoning fix. Retry queue (Phase 3) inherits the lesson: persist request state across timeouts. `docs/plans/2026-05-04-fix-bunker-timeouts-and-decryption-plan.md`.
- **2026-05-09 Embedded Local Relay** — shipped `LocalRelayStore` SQLite + `BasicBundledInsert`. Phase 3 retry queue uses the same store. `desktopApp/plans/2026-05-09-embedded-local-relay-plan.md`.
- **2026-03-20 Remote Signer Loading & Error UX** — shipped `SigningState` pattern. Phase 3 bunker progress UI reuses. `docs/plans/2026-03-20-feat-remote-signer-loading-error-ux-plan.md`.

### External references

| Source | URL |
|---|---|
| NIP-17 spec | https://github.com/nostr-protocol/nips/blob/master/17.md |
| NIP-42 spec | https://github.com/nostr-protocol/nips/blob/master/42.md |
| NIP-46 spec | https://github.com/nostr-protocol/nips/blob/master/46.md |
| NIP-4E PR #1647 (contested) | https://github.com/nostr-protocol/nips/pull/1647 |
| NIP-17 keys PR #2361 (contested) | https://github.com/nostr-protocol/nips/pull/2361 |
| wisp source | https://github.com/barrydeen/wisp |
| nospeak source | https://github.com/psic4t/nospeak |

### Files worth diffing line-by-line during implementation

- wisp: `app/src/main/kotlin/com/wisp/app/relay/RelayPool.kt:518-563` (tiered AUTH)
- wisp: `app/src/main/kotlin/com/wisp/app/viewmodel/StartupCoordinator.kt:353-364, 734-743` (re-sub on AUTH-OK, no-`since` 1059 filter)
- wisp: `app/src/main/kotlin/com/wisp/app/viewmodel/DmConversationViewModel.kt:654-665, 702-712, 766` (shared rumor_created_at, self-copy)
- wisp: `app/src/main/kotlin/com/wisp/app/repo/DmRelayLookup.kt` (indexer fan-out)
- nospeak: `src/lib/core/connection/RetryQueue.ts` (Dexie-backed retry queue)
- nospeak: `src/lib/core/connection/publishWithDeadline.ts:136` (AUTH retry inside publish)
- nospeak: `src/lib/core/connection/ConnectionManager.ts:345-410` (re-AUTH on visibilitychange)
- nospeak: `src/lib/stores/sending.ts` (per-relay delivery counter)

---

## Unanswered questions

Resolved during deepening (see Deepening Synthesis §"Open questions resolved"): tier-3 dropped (2 tiers only); indexer set hardcoded curated 5 + system-property override; conversation-key cache session-only; retry dead-letter 30d; self-copy own DM relays only; bunker progress = spinner not counter; NIP-46 capability via optimistic probe (no spec extension).

Still open:
- Android UI parity for tier-2 banner — separate plan or include here? Lean separate.
- Manual relay-entry: ship dialog with full validation (F-02) or drop entirely + Snackbar error only? Lean drop; revisit after dogfooding.
- Bunker batch RPC chunk size cap — spec authors to set (recommend 100 pubkeys/call).
- WoT relay rejection messages — toast all `:` -prefixed OK reasons or filter? Lean all.
- F-13 multi-indexer agreement — require ≥2 indexers, or accept ≥1 with NIP-11 pubkey pinning? Decide in Phase 4.
- NIP-09 deletion of self-copies on kind:10050 rotation (F-06) — implement now or release-note disclosure? Lean disclosure now, implement later.
- AUTH approval revoke UI placement (F-05 P1) — Settings → DMs → "Approved relays" list. Confirm during Phase 2 design.
- TLS SPKI + NIP-11 pubkey pinning for AUTH approvals (F-05) — defer or include in Phase 2? Lean include — protects against relay-ownership swap mid-TTL.
