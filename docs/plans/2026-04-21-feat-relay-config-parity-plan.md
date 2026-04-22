---
title: "feat: Relay Config Parity — All Desktop-Relevant Categories"
type: feat
status: draft
date: 2026-04-21
parent: docs/plans/2026-04-20-feat-relay-power-tools-plan.md
---

# feat: Relay Config Parity — All Desktop-Relevant Categories

## Context

Phase 1-2 of Relay Power Tools shipped a dashboard with Monitor tab, a Configure tab with only Connected Relays (NIP-65/DM as placeholders), and a compose relay picker. This plan fills in all relay categories that desktop features actually consume.

## Scope: Feature-Driven Categories

Only categories where desktop has a feature that reads/writes them:

| Category | Kind | Desktop Feature | State Class | Priority |
|----------|------|----------------|-------------|----------|
| **NIP-65 Inbox/Outbox** | 10002 | Feeds, notifications, outbox publishing | `Nip65RelayListState` (commons) | P0 |
| **DM Relays** | 10050 | DMs (DesktopMessagesScreen) | `DesktopAccountRelays._dmRelayList` | P0 |
| **Search Relays** | 10007 | Search (SearchScreen, NIP-50) | — (new) | P1 |
| **Blocked Relays** | 10006 | Privacy/moderation | — (new) | P2 |
| **Connected Relays** | runtime | Everything (fallback pool) | `RelayConnectionManager` | ✅ Done |

### Explicitly Out of Scope

| Category | Kind | Why |
|----------|------|-----|
| Private Outbox | 10013 | Desktop drafts are local-only (`DesktopDraftStore`) |
| Indexer Relays | 10086 | Desktop uses connected relays for indexing |
| Proxy Relays | 10087 | No proxy relay feature on desktop |
| Broadcast Relays | 10088 | Compose picker already handles per-action relay selection |
| Trusted Relays | 10089 | No trust-scoring UI on desktop |
| Key Package | MIP-00 | No MLS/group messaging on desktop |
| Relay Sets | 30002 | No custom grouping UI |
| Wiki Relays | 10102 | No wiki feature |
| Relay Feeds | 10012 | No relay feeds feature |
| Local Relays | custom | No local relay feature |

## Technical Approach

### Architecture

```
Existing (Phase 1-2):
├── RelayConfigTab.kt          # MODIFY: replace placeholders with real editors
├── RelayListEditor.kt         # REUSE: generic add/remove/validate
├── DesktopAccountRelays.kt    # MODIFY: add NIP-65 + search relay state
├── DesktopIAccount.kt         # Already has Nip65RelayListState

New:
├── Nip65RelayEditor.kt        # NEW: inbox/outbox editor with read/write toggles
├── DmRelayEditor.kt           # NEW: DM relay editor with block-send-if-empty
├── SearchRelayEditor.kt       # NEW: search relay editor
├── BlockedRelayEditor.kt      # NEW: blocked relay editor
└── DesktopSearchRelayState.kt # NEW: search relay state (kind 10007)
```

### Phase 3a: NIP-65 Inbox/Outbox Editor (P0)

**Why P0:** Feeds, notifications, and publishing all depend on NIP-65. Without this, desktop uses hardcoded default relays.

**State:** `Nip65RelayListState` already exists in commons and is instantiated in `DesktopIAccount`. Desktop already has `outboxFlow` and `inboxFlow`.

**UI: `Nip65RelayEditor.kt`**

```kotlin
@Composable
fun Nip65RelayEditor(
    nip65State: Nip65RelayListState,
    relayManager: RelayConnectionManager,
    onPublish: (AdvertisedRelayListEvent) -> Unit,
)
```

- Two-column or tagged list: each relay has Read/Write/Both toggle
- Uses `AdvertisedRelayInfo` with `type` (READ, WRITE, BOTH)
- "Save" button calls `nip65State.saveRelayList(relays)` → returns signed event → `onPublish` broadcasts
- Shows "Published to X of Y relays" confirmation
- "Reset to defaults" option restores `defaultOutboxRelays`/`defaultInboxRelays`

**Data flow:**
1. Read current: `nip65State.getNIP65RelayList()?.relays()` → list of `AdvertisedRelayInfo`
2. User edits in mutable local state
3. Save: `nip65State.saveRelayList(editedRelays)` → signed `AdvertisedRelayListEvent`
4. Broadcast: `relayManager.publish(event, connectedRelays)`

**Integration:**
- Replace "NIP-65 Inbox/Outbox — coming soon" placeholder in `RelayConfigTab`
- Thread `nip65State` from `DesktopIAccount` through to `RelayConfigTab`

### Phase 3b: DM Relay Editor (P0)

**Why P0:** Desktop has full DM support. DM relays control where encrypted messages are sent/received.

**State:** `DesktopAccountRelays._dmRelayList` already tracks kind 10050.

**UI: `DmRelayEditor.kt`**

```kotlin
@Composable
fun DmRelayEditor(
    dmRelays: StateFlow<Set<NormalizedRelayUrl>>,
    connectedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    signer: NostrSigner,
    onPublish: (Event) -> Unit,
)
```

- Simple relay list (no read/write split — DM relays are all-or-nothing)
- Warning banner if empty: "No DM relays configured — DMs will use connected relays as fallback"
- "Save" builds `ChatMessageRelayListEvent` → sign → publish
- Security: highlight that these relays see your DM metadata

**Data flow:**
1. Read current: `accountRelays.dmRelayList` StateFlow
2. Save: build `ChatMessageRelayListEvent.create(relays, signer)` → publish

**Integration:**
- Replace "DM Relays — coming soon" placeholder in `RelayConfigTab`
- Thread `accountRelays` + `signer` through to `RelayConfigTab`

### Phase 3c: Search Relay Editor (P1)

**Why P1:** Desktop has a full search screen with NIP-50 support but currently searches all connected relays. Dedicated search relays improve result quality.

**State:** New `DesktopSearchRelayState` needed — simple `StateFlow<Set<NormalizedRelayUrl>>` backed by kind 10007 events.

**UI: `SearchRelayEditor.kt`**

- Same pattern as DM relay editor
- Explain to user: "These relays support NIP-50 full-text search"
- Default suggestion: `wss://relay.nostr.band` (common NIP-50 relay)
- "Save" builds `SearchRelayListEvent` → sign → publish

**Integration:**
- New section in `RelayConfigTab` after DM Relays
- Wire search relay state into `DesktopRelaySubscriptionsCoordinator` for search queries

### Phase 3d: Blocked Relay Editor (P2)

**Why P2:** Privacy feature — user can maintain a list of relays they don't want to connect to. Lower priority but simple to implement since pattern is identical.

**State:** New — kind 10006 `BlockedRelayListEvent`.

**UI: `BlockedRelayEditor.kt`**

- List of blocked relay URLs
- "Save" publishes kind 10006 event
- Integration: filter blocked relays from connection pool (future)

### Implementation Order

| Phase | What | Files | Depends On |
|-------|------|-------|------------|
| 3a | NIP-65 editor | `Nip65RelayEditor.kt`, modify `RelayConfigTab`, modify `DeckColumnContainer` (thread state) | `Nip65RelayListState` (exists) |
| 3b | DM relay editor | `DmRelayEditor.kt`, modify `RelayConfigTab` | `DesktopAccountRelays` (exists) |
| 3c | Search relay editor | `SearchRelayEditor.kt`, `DesktopSearchRelayState.kt`, modify `RelayConfigTab`, modify subscriptions coordinator | New state class |
| 3d | Blocked relay editor | `BlockedRelayEditor.kt`, modify `RelayConfigTab` | `BlockedRelayListEvent` (exists in quartz) |

### State Threading

`RelayConfigTab` currently receives only `relayManager`. It needs:

```kotlin
@Composable
fun RelayConfigTab(
    relayManager: DesktopRelayConnectionManager,
    nip65State: Nip65RelayListState,        // Phase 3a
    accountRelays: DesktopAccountRelays,     // Phase 3b (DM relays)
    signer: NostrSigner,                     // For signing relay list events
    onPublish: (Event) -> Unit,              // Broadcast signed events
    modifier: Modifier = Modifier,
)
```

Thread through: `Main.kt` → `MainContent` → `DeckLayout`/`SinglePaneLayout` → `DeckColumnContainer` → `RootContent` → `RelayDashboardScreen` → `RelayConfigTab`

Alternative: pass `DesktopIAccount` (already threaded) which has `nip65State` + `signer`, and `accountRelays` (already created alongside).

### Shared `RelayListEditor` Pattern

All editors follow the same pattern — reuse `RelayListEditor` for the add/remove/validate part. Each editor wraps it with:
1. Category-specific header + description
2. Optional per-relay toggles (read/write for NIP-65)
3. Save button that builds the right event kind
4. Publish feedback

### Event Kind Reference (from quartz)

| Kind | Event Class | Tag Format |
|------|------------|------------|
| 10002 | `AdvertisedRelayListEvent` | `["r", "wss://...", "read"\|"write"]` |
| 10050 | `ChatMessageRelayListEvent` | `["relay", "wss://..."]` |
| 10007 | `SearchRelayListEvent` | `["relay", "wss://..."]` |
| 10006 | `BlockedRelayListEvent` | `["relay", "wss://..."]` |

## Acceptance Criteria

### Phase 3a: NIP-65
- [ ] NIP-65 section shows current inbox/outbox relays from user's kind 10002
- [ ] Each relay has Read/Write/Both toggle
- [ ] Can add/remove relays
- [ ] Save signs + publishes AdvertisedRelayListEvent
- [ ] Shows "Published to X of Y" confirmation
- [ ] Reset to defaults option

### Phase 3b: DM Relays
- [ ] DM section shows current kind 10050 relays
- [ ] Warning if empty
- [ ] Save signs + publishes ChatMessageRelayListEvent
- [ ] Shows publish confirmation

### Phase 3c: Search Relays
- [ ] Search section shows current kind 10007 relays
- [ ] Suggests relay.nostr.band if empty
- [ ] Save signs + publishes SearchRelayListEvent
- [ ] Search screen uses configured search relays

### Phase 3d: Blocked Relays
- [ ] Blocked section shows kind 10006 relays
- [ ] Save signs + publishes BlockedRelayListEvent

## Unanswered Questions

1. Should NIP-65 editor show relays from the user's existing event, or from `defaultOutboxRelays`/`defaultInboxRelays` if no event exists?
2. Should DM relay editor block fallback to connected relays (per original plan security decision) or just warn?
3. For search relays — should we auto-detect NIP-50 support via NIP-11 `supported_nips` field and suggest capable relays from the connected pool?
4. Should publishing relay list events use `connectedRelays` or the NIP-65 outbox relays? (Bootstrap problem if NIP-65 is empty.)
5. How to handle the threading of 4+ state objects through the composable tree — pass `DesktopIAccount` directly or keep explicit params?
