# Embedded Local Relay for Amethyst Desktop

## Enhancement Summary

**Deepened on:** 2026-05-09
**Sections enhanced:** 5 phases + architecture
**Research agents used:** LocalRelayClient patterns, EventWriteBuffer, Hydration strategy, NIP-09/maintenance, Settings UI + Offline, Account lifecycle

### Key Improvements
1. Discovered `BasicBundledInsert` already exists — reuse for write buffer instead of custom Channel
2. Full IRelayClient interface mapped — LocalRelayClient skeleton ready
3. Account lifecycle hook points identified (Main.kt lines 771-792, 830-866)
4. UI patterns catalogued — CollapsibleSection, DmBroadcastBanner, SearchSyncBanner all reusable
5. SQLite triggers already handle replaceables, deletions, expiration — no app-level logic needed

## Overview
Add an in-process local relay to Amethyst Desktop using quartz's existing `NostrServer` + `SQLiteEventStore`. No WebSocket server — the relay lives in-process as a `LocalRelayClient` added to `RelayPool`. All remote relay events are persisted to SQLite. On startup, the local store hydrates `DesktopLocalCache` for instant feed rendering.

## Architecture

```
App Startup
  |
  v
EventStore("~/.amethyst/accounts/<pubkey8>/events.db")
  |
  v
NostrServer(store, policyBuilder={VerifyPolicy})
  |
  v
LocalRelayClient : IRelayClient  (url = "local://amethyst")
  |                                    |
  v                                    v
RelayPool treats it like any relay    DesktopLocalCache.consume() writes through
```

### Data Flow (Steady State)
```
Remote Relay event arrives
  -> DesktopRelaySubscriptionsCoordinator.consumeEvent()
    -> localCache.consume(event, relay)  [existing]
    -> localRelayStore.enqueue(event)    [NEW: write-through]
      -> BasicBundledInsert batches (250ms)
        -> store.transaction { batch.forEach { insert(it) } }
```

### Data Flow (Startup Hydration)
```
Account login (pubKeyHex available)
  -> LocalRelayStore.openForAccount(pubKeyHex)
  -> LocalRelayStore.hydrate(localCache)
    -> Phase 1: Query kind 3 (contact list) for own pubkey
    -> Phase 2: Query kind 0 (metadata) for followed users
    -> Phase 3: Query kinds 1,6,7,16,1111 since 7 days, limit 5000
    -> Each event -> localCache.consume(event, localRelayUrl)
  -> relayManager.connect()  [remote relays start after hydration]
```

## Phases

### Phase 1: Core Infrastructure
**Goal**: Wire EventStore + write-through buffer + account lifecycle

#### Files to Create

**`desktopApp/.../relay/LocalRelayStore.kt`** — Manages EventStore lifecycle per account

```kotlin
class LocalRelayStore(
    private val scope: CoroutineScope,
) : AutoCloseable {
    private var store: EventStore? = null
    val localRelayUrl = "local://amethyst".normalizeRelayUrl()

    // Write buffer using existing BasicBundledInsert pattern
    private val writeBundler = BasicBundledInsert<Event>(
        delay = 250,  // Same as desktop event bundler
        dispatcher = Dispatchers.IO,
        scope = scope,
    )

    fun openForAccount(pubKeyHex: String) {
        close()
        val dbDir = File(System.getProperty("user.home"), ".amethyst/accounts/${pubKeyHex.take(8)}")
        dbDir.mkdirs()
        store = EventStore(
            dbName = File(dbDir, "events.db").absolutePath,
            relay = localRelayUrl,
        )
    }

    fun enqueue(event: Event) {
        writeBundler.invalidateList(event) { batch ->
            store?.transaction {
                batch.forEach { insert(it) }
            }
        }
    }

    override fun close() {
        store?.close()
        store = null
    }
}
```

**Key decisions:**
- DB path: `~/.amethyst/accounts/<pubkey8>/events.db` (8-char hex prefix)
- `EventStore` wraps `SQLiteEventStore` with `BundledSQLiteDriver()` — no JNI needed
- `BasicBundledInsert` already battle-tested in `DesktopRelaySubscriptionsCoordinator`
- SQLite triggers handle replaceables, deletions, expiration automatically

#### Files to Modify

**`DesktopLocalCache.kt`** — Add write-through hook in `consume()`

```kotlin
// After line ~208 (after route(event, relay)):
// Add: localRelayStore?.enqueue(event)
```

Interception point: `consume()` already verifies signatures. After `route()` succeeds, enqueue to local store.

**`Main.kt`** — Account lifecycle integration

Hook points identified:
- Line 697: Create `LocalRelayStore` alongside `localCache`
- Line 771-792 (`LaunchedEffect(accountState)`):
  - `LoggedOut` → `localRelayStore.close()`
  - `LoggedIn` with pubkey change → `localRelayStore.openForAccount(newPubKeyHex)`
- Line 859-866 (`DisposableEffect` cleanup): `localRelayStore.close()`

```kotlin
// Line ~697:
val localRelayStore = remember { LocalRelayStore(scope) }

// Line ~785 (after clear, before metadata):
localRelayStore.openForAccount(pubKeyHex)
```

#### No LocalRelayClient in Phase 1

Research revealed the write-through + hydration approach doesn't need a full `IRelayClient` implementation. The local store is a persistence layer, not a relay client. Events flow through the existing relay pool from remote relays and get persisted as a side effect. On startup, hydration reads from the store directly.

Implementing `IRelayClient` would require handling JSON serialization/deserialization roundtrips through `NostrServer` which adds overhead for no benefit in Approach A.

**Simplified architecture:**
- `LocalRelayStore` = `EventStore` + write buffer + hydration
- No `NostrServer` needed (saves serialization overhead)
- No `IRelayClient` needed (local store is not a relay peer)

---

### Phase 2: Startup Hydration
**Goal**: On login, populate DesktopLocalCache from local store before remote relays connect

#### Implementation in `LocalRelayStore`

```kotlin
suspend fun hydrate(cache: DesktopLocalCache) {
    val s = store ?: return
    val relay = localRelayUrl

    // Phase 1: Own contact list (need follow list for feed filtering)
    // Note: can't filter by own pubkey without it being passed in
    val contactFilter = Filter(kinds = listOf(3), limit = 1)
    s.query<ContactListEvent>(contactFilter).forEach { event ->
        cache.consume(event, relay, wasVerified = true)
    }

    // Phase 2: Metadata for followed users
    val followed = cache.followedUsers.value
    if (followed.isNotEmpty()) {
        // Batch metadata requests (max 500 authors per query for performance)
        followed.chunked(500).forEach { chunk ->
            val metaFilter = Filter(
                kinds = listOf(0),
                authors = chunk,
                limit = chunk.size,
            )
            s.query<MetadataEvent>(metaFilter).forEach { event ->
                cache.consume(event, relay, wasVerified = true)
            }
        }
    }

    // Phase 3: Recent content events
    val since = (System.currentTimeMillis() / 1000) - (7 * 24 * 3600)
    val contentFilter = Filter(
        kinds = listOf(1, 6, 7, 16, 1111, 9735), // notes, reposts, reactions, comments, zaps
        since = since,
        limit = 5000,
    )
    s.query<Event>(contentFilter).forEach { event ->
        cache.consume(event, relay, wasVerified = true)
    }
}
```

#### Memory Impact
- 5000 events at ~600 bytes avg = ~3 MB (trivial for desktop JVM)
- Hydration time: SQLite query + cache insertion = <500ms for 5000 events
- `wasVerified = true` skips signature verification (already verified on first insert)

#### Main.kt Integration

```kotlin
// After localRelayStore.openForAccount(pubKeyHex), before relayManager.connect():
scope.launch(Dispatchers.IO) {
    localRelayStore.hydrate(localCache)
    // Then connect remote relays (they'll fill gaps)
}
```

**Decision: Async hydration** — Show UI immediately, hydrate in background. Feed renders from local store data within ~200ms, then remote relay events fill in gaps.

---

### Phase 3: Event Lifecycle
**Goal**: Handle deletions, pruning, VACUUM, disk monitoring

#### File to Create

**`desktopApp/.../relay/LocalRelayMaintenance.kt`**

```kotlin
class LocalRelayMaintenance(
    private val store: LocalRelayStore,
    private val scope: CoroutineScope,
) {
    private var maintenanceJob: Job? = null
    private val _diskWarning = MutableStateFlow(false)
    val diskWarning: StateFlow<Boolean> = _diskWarning
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    fun start(dbPath: String) {
        maintenanceJob = scope.launch(Dispatchers.IO) {
            // Startup maintenance
            try {
                store.deleteExpiredEvents()
                maybeVacuum(dbPath)
            } catch (e: Exception) {
                _lastError.value = "Startup maintenance: ${e.message}"
            }

            // Periodic maintenance (every 6 hours)
            while (isActive) {
                delay(6 * 60 * 60 * 1000L)
                try {
                    store.deleteExpiredEvents()
                    store.pruneOldEvents(maxAgeDays = 30)
                    checkDiskSpace(dbPath)
                } catch (e: Exception) {
                    _lastError.value = "Periodic maintenance: ${e.message}"
                }
            }
        }
    }

    private fun checkDiskSpace(dbPath: String) {
        val usable = File(dbPath).parentFile?.usableSpace ?: return
        _diskWarning.value = usable < 100 * 1024 * 1024 // < 100MB
        if (_diskWarning.value) {
            store.disableWrites()
        }
    }

    private fun maybeVacuum(dbPath: String) {
        val prefs = Preferences.userRoot().node("amethyst/localrelay")
        val lastVacuum = prefs.getLong("lastVacuum", 0)
        val sevenDays = 7 * 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastVacuum > sevenDays) {
            store.vacuum()
            prefs.putLong("lastVacuum", System.currentTimeMillis())
        }
    }

    fun stop() {
        maintenanceJob?.cancel()
    }
}
```

#### NIP-09 Deletion
SQLiteEventStore's `DeletionRequestModule` already handles this via BEFORE INSERT triggers:
- When kind 5 event inserted → trigger deletes referenced events
- Trigger also prevents re-insertion of deleted events

**Desktop hook**: In `DesktopLocalCache.consume()`, when a `DeletionEvent` is consumed, the write-through to `LocalRelayStore.enqueue()` handles it — the store's trigger does the rest.

#### Pruning Strategy
Add to `LocalRelayStore`:
```kotlin
suspend fun pruneOldEvents(maxAgeDays: Int) {
    val cutoff = (System.currentTimeMillis() / 1000) - (maxAgeDays * 24 * 3600L)
    val filter = Filter(until = cutoff)
    store?.delete(filter)
}
```

#### DB Corruption Recovery
```kotlin
fun openForAccount(pubKeyHex: String) {
    close()
    try {
        store = EventStore(dbName = dbPath, ...)
    } catch (e: Exception) {
        // Corrupt DB — delete and recreate
        File(dbPath).delete()
        listOf("-wal", "-shm", "-journal").forEach {
            File(dbPath + it).delete()
        }
        store = EventStore(dbName = dbPath, ...)
        _lastError.value = "Database recreated: ${e.message}"
    }
}
```

---

### Phase 4: Settings UI
**Goal**: Local Relay Settings screen with stats, controls, error display

#### File to Create

**`desktopApp/.../ui/settings/LocalRelaySettingsScreen.kt`**

Uses established patterns:
- `CollapsibleSection` from `RelayConfigTab.kt`
- `AnimatedVisibility` with `expandVertically()` + `fadeIn()/fadeOut()`
- Material3 colors: `surfaceContainerHigh`, `errorContainer`, `onSurfaceVariant`
- Layout: `ReadingColumn` wrapper, 12.dp horizontal padding, 16.dp section spacers
- Controls: `Switch` for toggles, `Button` for actions, `OutlinedTextField` for config

#### Sections

**1. Status Section** (always visible)
- Switch: enabled/disabled toggle
- Status indicator: green dot (active) / gray (disabled)
- DB path display (read-only)

**2. Statistics** (CollapsibleSection, initially expanded)
- DB size on disk: `File(dbPath).length()` formatted as KB/MB/GB
- Total event count: `store.count(Filter())`
- Events by kind: breakdown table (kind 0, 1, 3, 7, etc.)
- Last write time

**3. Storage Management** (CollapsibleSection)
- Current disk usage
- Prune button: "Delete events older than 30 days" → `pruneOldEvents(30)`
- Clear all button: "Delete all cached events" → delete DB + recreate
- VACUUM button: "Reclaim disk space" → `store.vacuum()`

**4. Export/Import** (CollapsibleSection)
- Export button: JSONL file (one event JSON per line)
  - Uses `DesktopFilePicker` for save dialog
  - Streams events from store → write to file
- Import button: Read JSONL file → `store.transaction { events.forEach { insert(it) } }`
  - Uses `DesktopFilePicker` for open dialog

**5. Errors** (CollapsibleSection, collapsed by default)
- Recent errors from `LocalRelayMaintenance.lastError`
- Disk full warning from `LocalRelayMaintenance.diskWarning`
- Toggleable via `AnimatedVisibility`
- Error entries with timestamp + message
- "Clear errors" button

**6. Disk Full Warning** (conditional banner)
- Pattern: `DmBroadcastBanner` style
- Colors: `MaterialTheme.colorScheme.errorContainer`
- Actions: "Prune Now" button, "Clear Cache" button

#### Navigation Integration
- Add `LocalRelaySettings` to `DesktopScreen` sealed class
- Route from existing Settings screen (add a "Local Relay" row that navigates to it)
- Or add as tab in `RelayDashboardScreen` alongside Monitor/Configure

---

### Phase 5: Offline Mode
**Goal**: Show offline indicator when no remote relays connected

#### File to Create

**`desktopApp/.../ui/components/OfflineBanner.kt`**

```kotlin
@Composable
fun OfflineBanner(
    connectedRelayCount: Int,
    hasLocalData: Boolean,
) {
    AnimatedVisibility(
        visible = connectedRelayCount == 0,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = if (hasLocalData)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (hasLocalData) Icons.Default.CloudOff else Icons.Default.Warning,
                    contentDescription = null,
                )
                Text(
                    text = if (hasLocalData)
                        "Offline — showing cached events"
                    else
                        "Offline — no cached events available",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
```

**Pattern**: Follows `DmBroadcastBanner` style (AnimatedVisibility + Surface + Row with icon/text).

#### Integration
- Place in `DeckColumnContainer` root content, above feed content
- Observe `relayManager.connectedRelays` StateFlow
- `hasLocalData = localRelayStore.eventCount() > 0`

---

## File Summary

| File | Action | Phase |
|------|--------|-------|
| `desktopApp/.../relay/LocalRelayStore.kt` | Create | 1+2 |
| `desktopApp/.../relay/LocalRelayMaintenance.kt` | Create | 3 |
| `desktopApp/.../ui/settings/LocalRelaySettingsScreen.kt` | Create | 4 |
| `desktopApp/.../ui/components/OfflineBanner.kt` | Create | 5 |
| `desktopApp/.../cache/DesktopLocalCache.kt` | Modify (write-through hook) | 1 |
| `desktopApp/.../Main.kt` | Modify (lifecycle + hydration) | 1+2 |
| `desktopApp/.../ui/deck/DeckColumnContainer.kt` | Modify (offline banner) | 5 |
| `desktopApp/.../Main.kt` (DesktopScreen) | Modify (add LocalRelaySettings) | 4 |

## Non-Goals (Punt)
- WebSocket server for external apps (Approach B — future)
- Sync between devices
- Full-text search UI against local store
- NostrServer / IRelayClient implementation (unnecessary for Approach A)

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Slow startup hydration | Cap at 5000 events, async (don't block UI) |
| Write buffer data loss on crash | Accept — events exist on remote relays |
| DB corruption | Detect on open, delete + recreate, log to settings errors |
| Disk full | Monitor `File.usableSpace`, disable writes at <100MB, surface in UI |
| Tag indexing overhead (80% of insert) | BasicBundledInsert batches amortize transaction cost |
| Hydration before follow list available | Query kind 3 first, then use result for metadata + content filters |

## Testing Strategy
- Unit tests for write buffer batching behavior
- Unit tests for LocalRelayStore (open/close/account-switch lifecycle)
- Integration test: write events -> close -> reopen -> hydrate -> verify cache populated
- Integration test: NIP-09 deletion -> verify event removed from store
- Manual: launch app, browse feeds, restart, verify instant load from cache
- Manual: export/import round-trip
- Manual: settings screen — prune, vacuum, clear, toggle
- Manual: disconnect network, verify offline banner + cached events display
