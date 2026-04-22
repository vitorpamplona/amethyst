---
title: "feat: Relay Config Persistence, Correct Counts, Per-Screen Picker"
type: feat
status: active
date: 2026-04-22
origin: docs/brainstorms/2026-04-22-relay-config-persistence-and-per-screen-editing-brainstorm.md
---

# feat: Relay Config Persistence, Correct Counts, Per-Screen Picker

## Overview

Three fixes to make relay management work end-to-end: persist config across restarts, show correct per-category relay counts, and add inline relay editing per screen.

## Problem Statement

1. **Config lost on restart/reopen**: Search/DM/blocked relay lists vanish — no Preferences persistence, NIP-51 private tags not decrypted
2. **Wrong relay counts**: Search shows "0 of 7 relays responded" against all connected relays, not the 1 configured search relay
3. **No per-screen editing**: Must navigate to full Dashboard to change which relays a feature uses

(see brainstorm: `docs/brainstorms/2026-04-22-relay-config-persistence-and-per-screen-editing-brainstorm.md`)

## Technical Approach

### Design Decisions (from brainstorm)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Persistence format | Raw event JSON per kind | Preserves `created_at` for dedup, matches Android `backupXxxRelayList` pattern |
| Preferences keys | `relay_<kind>_<pubkey-prefix>` | Per-account isolation, avoids key collision |
| NIP-51 decryption | Decrypt lazily when signer available, persist encrypted | Don't leak private relay info to disk |
| Relay count source | Category-specific `searchRelays.size`, `feedRelays.size` | Not `allRelayUrls.size` |
| Picker type | Subscribe-FROM picker (changes which relays screen uses) | Different from compose picker (publish-TO) |
| NIP-65 picker | Read/write toggles in expandable form | Other categories are simple add/remove |
| Publish on save | Immediately with loading/error state | User confirmed |
| Connection dots | Yes | User confirmed |

### Phase 1: Persistence + NIP-51 Decryption

**`DesktopRelayListPersistence.kt`** (new):

```kotlin
object DesktopRelayListPersistence {
    private val prefs = Preferences.userNodeForPackage(DesktopRelayListPersistence::class.java)

    fun saveEvent(kind: Int, pubKeyHex: String, event: Event) {
        prefs.put(key(kind, pubKeyHex), event.toJson())
    }

    fun loadEvent(kind: Int, pubKeyHex: String): Event? {
        val json = prefs.get(key(kind, pubKeyHex), "")
        if (json.isBlank()) return null
        return try { Event.fromJson(json) } catch (_: Exception) { null }
    }

    private fun key(kind: Int, pubKeyHex: String) = "relay_${kind}_${pubKeyHex.take(8)}"
}
```

**`DesktopAccountRelays.kt`** changes:
- Accept `NostrSigner` in constructor
- Accept `scope: CoroutineScope` for async decryption
- `consumeIfRelevant` becomes `suspend` — calls `event.relays(signer)` for NIP-51 kinds
- On every state change → `DesktopRelayListPersistence.saveEvent(kind, pubKeyHex, event)`
- `loadFromPersistence()` method — loads events, decrypts NIP-51 in coroutine
- Call `loadFromPersistence()` in `init {}` block

**`Main.kt`** changes:
- Pass `signer` to `DesktopAccountRelays` constructor
- Bootstrap `LaunchedEffect` launches `consumeIfRelevant` in coroutine (now suspend)

### Phase 2: Correct Per-Screen Relay Counts

**`SearchScreen.kt`** changes:
- Replace `state.initRelayStates(allRelayUrls)` with `state.initRelayStates(searchRelays)`
- `searchRelays` already from `LocalRelayCategories.current.searchRelays.collectAsState()`
- Banner shows "0 of 1 relays responded" when 1 search relay configured

**`FeedScreen.kt`** changes:
- Replace `"${connectedRelays.size} relays connected"` with `"${feedRelays.size} feed relays"`
- `feedRelays` already from `LocalRelayCategories.current.feedRelays.collectAsState()`

**`AdvancedSearchBarState.kt`** changes:
- `initRelayStates` takes `Set<NormalizedRelayUrl>` instead of `Set<Any>`

### Phase 3: Per-Screen Relay Picker Dialog

**`RelayPickerDialog.kt`** (new):

```kotlin
@Composable
fun RelayPickerDialog(
    title: String,                    // "Search Relays", "Feed Relays", etc.
    currentRelays: List<NormalizedRelayUrl>,  // or List<AdvertisedRelayInfo> for NIP-65
    connectedRelays: Set<NormalizedRelayUrl>,
    signer: NostrSigner,
    isNip65: Boolean = false,         // show read/write toggles
    onSave: suspend (List<NormalizedRelayUrl>) -> Event,  // returns signed event
    onPublish: (Event) -> Unit,
    onDismiss: () -> Unit,
)
```

UI:
- Modal dialog with category title
- Relay list with connection status dots (green/gray)
- Add relay input with validation
- NIP-65 mode: expandable read/write/both toggles per relay
- Save button with loading spinner + error text
- Flow: Save → `isLoading = true` → `onSave()` → `onPublish()` → persist → `isLoading = false` → `onDismiss()`

**Screen integration — relay icon buttons:**

| Screen | Location | Category | Picker type |
|--------|----------|----------|-------------|
| SearchScreen | Next to search bar | Search relays | Simple add/remove |
| FeedScreen | Next to "X feed relays" text | NIP-65 outbox | Read/write toggles |
| DM screen | Header area | DM relays | Simple add/remove |

Each screen:
```kotlin
var showRelayPicker by remember { mutableStateOf(false) }
// Relay icon button
IconButton(onClick = { showRelayPicker = true }) { Icon(Icons.Default.Dns, ...) }
// Dialog
if (showRelayPicker) {
    RelayPickerDialog(
        title = "Search Relays",
        currentRelays = searchRelays.toList(),
        connectedRelays = connectedRelays,
        signer = signer,
        onSave = { relays -> SearchRelayListEvent.create(relays, signer) },
        onPublish = { event -> relayManager.broadcastToAll(event); accountRelays.setSearchRelays(relays) },
        onDismiss = { showRelayPicker = false },
    )
}
```

## Files Modified/Created

| File | Change |
|------|--------|
| `DesktopRelayListPersistence.kt` | NEW: save/load event JSON per kind per account |
| `DesktopAccountRelays.kt` | Add signer, suspend consumeIfRelevant, persistence, NIP-51 decrypt |
| `RelayPickerDialog.kt` | NEW: modal per-screen relay editor with loading/error |
| `SearchScreen.kt` | Fix relay count init, add relay picker icon |
| `FeedScreen.kt` | Fix relay count text, add relay picker icon |
| `Main.kt` | Pass signer to accountRelays, update bootstrap for suspend |
| `AdvancedSearchBarState.kt` | `initRelayStates` takes typed Set |

## Acceptance Criteria

### Phase 1: Persistence
- [ ] Relay configs survive app restart (save to Preferences, load on startup)
- [ ] NIP-51 events decrypted via signer when available
- [ ] Bootstrap overwrites persisted data only if newer (`created_at`)
- [ ] Per-account key isolation (pubkey prefix)
- [ ] Graceful fallback on corrupt/missing persistence data

### Phase 2: Relay Counts
- [ ] SearchScreen shows "X of Y" against search relay set size, not all connected
- [ ] FeedScreen shows feed relay count, not connected relay count
- [ ] Counts update when relay set changes

### Phase 3: Per-Screen Picker
- [ ] Relay icon on SearchScreen, FeedScreen, DM screen opens picker dialog
- [ ] Picker shows current category relays with connection status dots
- [ ] Add/remove with validation (domain check, wss:// required)
- [ ] NIP-65 picker has expandable read/write/both toggles
- [ ] Save publishes immediately with loading spinner
- [ ] Error handling: signing failure, publish failure shown in dialog
- [ ] Screen resubscribes after picker save

## Dependencies & Risks

| Risk | Mitigation |
|------|-----------|
| Preferences 8KB limit per key | Relay list JSON is typically <2KB — safe. Monitor for large lists |
| NIP-46 signer timeout on decrypt | Persist encrypted event, decrypt lazily. Show relays as "loading..." |
| Picker save echoes back via bootstrap | Dedup by `created_at` — same or newer event is no-op |
| `consumeIfRelevant` now suspend | Bootstrap already runs in coroutine scope |

## Sources

- **Origin brainstorm:** [docs/brainstorms/2026-04-22-relay-config-persistence-and-per-screen-editing-brainstorm.md](docs/brainstorms/2026-04-22-relay-config-persistence-and-per-screen-editing-brainstorm.md) — Key decisions: persist raw event JSON, decrypt NIP-51 lazily, publish immediately from picker
- Android persistence pattern: `amethyst/LocalPreferences.kt` lines 113-128
- Android AccountSettings: `amethyst/model/AccountSettings.kt` lines 196-202
- Desktop Preferences pattern: `desktopApp/.../DesktopPreferences.kt`
