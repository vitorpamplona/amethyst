---
title: "feat: Relay Power Tools — Dashboard Screen + Compose Relay Picker"
type: feat
status: active
date: 2026-04-20
origin: docs/brainstorms/2026-04-20-relay-power-tools-brainstorm.md
deepened: 2026-04-20
---

# feat: Relay Power Tools — Dashboard Screen + Compose Relay Picker

## Enhancement Summary

**Deepened on:** 2026-04-20
**Review agents used:** compose-expert, desktop-expert, kotlin-coroutines, nostr-expert, performance-oracle, security-sentinel, architecture-strategist, code-simplicity-reviewer

### Key Improvements from Review
1. **Separate metrics from RelayStatus** — avoid StateFlow churn that caused subscription issues (March 2026 brainstorm)
2. **Kill RelayMetricsStore** — session-only metrics, no persistence (YAGNI, Preferences 8KB limit)
3. **Cut to 3 relay categories** — desktop only uses NIP-65, DM, connected today
4. **Reuse existing `publish()` API** — already accepts relay set, no new `publishTo()` needed
5. **Reuse existing `Note.addRelay()`** — relay source tracking already exists in cache layer
6. **Fix Nip11Fetcher Tor regression** — must use fail-closed HTTP client, not fail-open `getHttpClient()`
7. **Block DM fallback to all relays** — security: never fall back to all connected for DMs
8. **7 files for DeckColumnType, not 5** — add PinnedNavBarState + AppDrawer `category()`
9. **New ScreenCategory.NETWORK** — semantically correct vs IDENTITY

### Files Eliminated (YAGNI)
- `RelayMetricsStore.kt` — no UI consumes historical data
- `RelaySuggestions.kt` — deferred entirely, no demand signal
- `RelayBadge.kt` — per-event tracking overhead for nice-to-have

---

## Overview

Add full relay visibility and control to Amethyst Desktop through two features:

1. **Relay Dashboard** — New `DeckColumnType.Relays` screen with live session metrics, NIP-11 info, and per-category relay list management
2. **Compose Relay Picker** — Expandable relay section in compose dialogs for per-action relay selection

## Problem Statement / Motivation

Desktop currently has a minimal relay settings section (~70 lines in Main.kt) that only supports add/remove/reconnect with basic status cards. Users cannot:
- Monitor relay health (event counts, latency, NIP support)
- Configure per-category relay lists (NIP-65, DM)
- Choose which relays to publish to when composing notes

Gossip and noStrudel lead in relay management UX — this feature brings Amethyst Desktop ahead of both.

## Proposed Solution

Dashboard-first build order (see brainstorm: Key Decisions). Dashboard establishes shared state layer (session metrics, NIP-11, category management) that compose picker reuses.

## Technical Approach

### Architecture

```
New/Modified Files:
├── desktopApp/src/jvmMain/.../
│   ├── network/
│   │   ├── RelayStatus.kt              # UNCHANGED — connection state only
│   │   ├── RelayConnectionManager.kt   # MODIFY: add relayMetrics flow, 1Hz snapshot
│   │   └── Nip11Fetcher.kt             # NEW: Mutex-based HTTP GET + session cache
│   ├── ui/
│   │   ├── relay/
│   │   │   ├── RelayDashboardScreen.kt     # NEW: main dashboard screen
│   │   │   ├── RelayMetricsTab.kt          # NEW: live metrics tab
│   │   │   ├── RelayConfigTab.kt           # NEW: per-category editor tab
│   │   │   ├── RelayDetailPanel.kt         # NEW: NIP-11 detail expansion
│   │   │   ├── RelayMetricCard.kt          # NEW: enhanced status card w/ metrics
│   │   │   ├── RelayListEditor.kt          # NEW: add/remove for a category
│   │   │   └── RelayStatusCard.kt          # MODIFY: M3 colors
│   │   └── compose/
│   │       └── ComposeRelayPicker.kt       # NEW: expandable relay section
│   ├── ui/deck/
│   │   ├── DeckColumnType.kt           # MODIFY: add Relays
│   │   ├── ColumnHeader.kt             # MODIFY: add icon
│   │   ├── DeckColumnContainer.kt      # MODIFY: add routing + thread Nip11Fetcher
│   │   ├── AppDrawer.kt                # MODIFY: add to LAUNCHABLE_SCREENS + category()
│   │   ├── DeckState.kt                # MODIFY: add parse
│   │   └── PinnedNavBarState.kt        # MODIFY: add to DEFAULT_PINNED
│   ├── Main.kt                         # MODIFY: create Nip11Fetcher, MenuBar item
│   └── ComposeNoteDialog.kt            # MODIFY: integrate ComposeRelayPicker
```

### Design Decisions (consolidated from SpecFlow + all reviews)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Metrics vs status | **Separate `relayMetrics` StateFlow** | Avoids relayStatuses churn (March brainstorm). Metrics update 1Hz, status updates on connect/disconnect |
| Metrics persistence | **Session-only** (no disk) | YAGNI: no UI for historical data. Preferences has 8KB limit. Add SQLite later if needed |
| Config categories | **3: NIP-65, DM, Connected** | Desktop doesn't use search/trusted/proxy/indexer/key-package relays yet |
| Save semantics | Explicit "Save" button per tab | Avoid event spam from immediate publish on every toggle |
| Compose picker default | NIP-65 write relays, fallback to all connected | Correct event visibility for followers |
| DM picker fallback | **Block send if no kind 10050** | Security: never fall back to all relays for DMs (metadata leak) |
| Selective publish | **Use existing `publish(event, relays)`** | API already exists on RelayConnectionManager |
| Relay source tracking | **Use existing `Note.addRelay()`** | Already populated in cache consume methods |
| NIP-11 fetch client | **Fail-closed** via `currentClient()` | `getHttpClient()` fails open during Tor bootstrap |
| NIP-11 response | Limit body to 256KB | Prevent DoS from malicious relay |
| Drag-to-reorder | **Cut** | Order is display-only, no functional value |
| Relay order | Alphabetical sort | Simple, deterministic |
| AppDrawer category | **New `ScreenCategory.NETWORK`** | IDENTITY is wrong (user-centric), NETWORK is infrastructure |
| Tab identity | Enum `DashboardTab` | Not magic int (CMP compatible) |
| Status colors | M3 `colorScheme.primary/error/outline` | Not hardcoded Color.Green/Red |
| Card expansion | Hoisted `isExpanded` + `AnimatedVisibility` | Enables single-expanded-at-a-time, smooth animation |
| Collapsed chips | `LazyRow` with keys | Not `Row` with `horizontalScroll` |
| NIP-11 per card | `produceState` per card | Prevents parent recomposition cascade |
| NIP-11 dedup | `Mutex` per URL, double-check pattern | Not `ConcurrentHashMap.getOrPut` (not atomic for lambda) |
| Picker state | Fully hoisted to parent | Not split between internal mutableState + callback |

### Implementation Phases

#### Phase 1: State Layer + Dashboard Screen

**Goal:** Relay Dashboard as new DeckColumnType with live session metrics and NIP-11 info.

##### Phase 1a: Extend Relay Infrastructure

**1. Add separate `relayMetrics` StateFlow** (`RelayConnectionManager.kt`)

> **Research Insight (Performance/Coroutines):** DO NOT add eventCount/lastEventAt to `RelayStatus`. The `relayStatuses` StateFlow is used as a `remember` key in FeedScreen. Metrics change per-event (100+/sec). This creates the exact churn documented in the March 2026 brainstorm. Instead, accumulate in `ConcurrentHashMap`, emit snapshot at 1Hz.

```kotlin
// In RelayConnectionManager

@Immutable  // Compose skip optimization
data class RelayMetrics(
    val eventCount: Long = 0,
    val lastEventAt: Long? = null,
)

// Hot metrics — written on every event, no StateFlow emission
private val _rawMetrics = ConcurrentHashMap<NormalizedRelayUrl, RelayMetrics>()

// Throttled snapshot — 1Hz emission for UI
private val _metricsFlow = MutableStateFlow<Map<NormalizedRelayUrl, RelayMetrics>>(emptyMap())
val relayMetrics: StateFlow<Map<NormalizedRelayUrl, RelayMetrics>> = _metricsFlow.asStateFlow()

// In listener callback — atomic, no StateFlow emission
override fun onEvent(relay: IRelayClient, event: Event) {
    _rawMetrics.compute(relay.url) { _, m ->
        RelayMetrics(
            eventCount = (m?.eventCount ?: 0) + 1,
            lastEventAt = System.currentTimeMillis()
        )
    }
}

// 1Hz snapshot coroutine — started in init or connect()
private fun startMetricsSnapshot(scope: CoroutineScope) {
    scope.launch {
        while (isActive) {
            delay(1_000)
            _metricsFlow.value = HashMap(_rawMetrics)
        }
    }
}
```

**`RelayStatus` stays unchanged** — connection-only state.

**2. Create `Nip11Fetcher`** (`Nip11Fetcher.kt`)

> **Research Insight (Security):** `getHttpClient()` has a fail-open fallback — returns `directClient` when Tor is bootstrapping. NIP-11 fetch MUST use fail-closed client. Either fix `getHttpClient()` or use `currentClient()`.
>
> **Research Insight (Coroutines):** `ConcurrentHashMap.getOrPut` is NOT atomic for the lambda. Use `Mutex` per URL with double-check pattern.
>
> **Research Insight (Performance):** Limit response body to 256KB to prevent DoS from malicious relay.

```kotlin
class Nip11Fetcher(
    private val httpClient: DesktopHttpClient,
    private val scope: CoroutineScope,
) {
    private val cache = ConcurrentHashMap<NormalizedRelayUrl, Nip11RelayInformation>()
    private val locks = ConcurrentHashMap<NormalizedRelayUrl, Mutex>()

    companion object {
        private const val MAX_RESPONSE_BYTES = 256 * 1024L // 256KB
        private val SEMAPHORE = Semaphore(5) // max 5 concurrent fetches
    }

    suspend fun fetch(url: NormalizedRelayUrl): Nip11RelayInformation? {
        cache[url]?.let { return it }

        val mutex = locks.getOrPut(url) { Mutex() }
        return mutex.withLock {
            cache[url]?.let { return it } // double-check

            SEMAPHORE.withPermit {
                withContext(Dispatchers.IO) {
                    fetchFromNetwork(url)
                }
            }?.also { info ->
                cache[url] = info
                locks.remove(url)
            }
        }
    }

    private fun fetchFromNetwork(url: NormalizedRelayUrl): Nip11RelayInformation? {
        // FAIL-CLOSED: use currentClient() not getHttpClient()
        val client = httpClient.currentClient() ?: return null
        val httpUrl = url.toHttp()
        val request = Request.Builder()
            .url(httpUrl)
            .header("Accept", "application/nostr+json")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.source()
                        ?.readUtf8(MAX_RESPONSE_BYTES) ?: return null
                    Nip11RelayInformation.fromJson(body)
                } else null
            }
        } catch (_: Exception) { null }
    }

    fun getCached(url: NormalizedRelayUrl): Nip11RelayInformation? = cache[url]
    fun clearCache() { cache.clear(); locks.clear() }
}
```

> **Research Insight (Architecture):** Consider extracting `Nip11Retriever` from Android to commons later. For now, desktop-specific is fine since the caching layer differs (Android LruCache vs ConcurrentHashMap).

##### Phase 1b: Register DeckColumnType

**7 files to modify** (desktop-expert caught 2 missing):

| File | Change |
|------|--------|
| `DeckColumnType.kt` | Add `object Relays : DeckColumnType()`, add to `title()` = "Relays" and `typeKey()` = "relays" |
| `ColumnHeader.kt` | Add `DeckColumnType.Relays -> Icons.Default.Dns` in `icon()` |
| `DeckColumnContainer.kt` | Add `DeckColumnType.Relays -> RelayDashboardScreen(...)` in `RootContent()` |
| `AppDrawer.kt` | Add new `ScreenCategory.NETWORK("Network", Icons.Default.Dns)` enum value. Add `DeckColumnType.Relays` to `LAUNCHABLE_SCREENS`. Add to `category()` → `NETWORK` |
| `DeckState.kt` | Add `"relays" -> DeckColumnType.Relays` in `parseColumnType()` |
| `PinnedNavBarState.kt` | Add `DeckColumnType.Relays` to `DEFAULT_PINNED` list |
| `Main.kt` | Create `Nip11Fetcher` at app level. Thread through `DeckColumnContainer`. Add MenuBar item: `View > Relay Dashboard (Cmd+Shift+R)` |

> **Research Insight (Desktop):** `DeckColumnType` is a sealed class. ALL exhaustive `when` expressions MUST be updated or compilation fails. The exhaustive ones: `title()`, `typeKey()`, `icon()`, `category()`, `RootContent()`.

##### Phase 1c: Dashboard UI

**3. Create `RelayDashboardScreen`** (`RelayDashboardScreen.kt`)

> **Research Insight (Compose):** `mutableIntStateOf` is Android-only. Use `mutableStateOf(0)` or better, an enum.

```kotlin
enum class DashboardTab(val label: String) {
    MONITOR("Monitor"),
    CONFIGURE("Configure"),
}

@Composable
fun RelayDashboardScreen(
    relayManager: DesktopRelayConnectionManager,
    nip11Fetcher: Nip11Fetcher,
    accountRelays: DesktopAccountRelays,
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.MONITOR) }
    Column {
        TabRow(selectedTabIndex = DashboardTab.entries.indexOf(selectedTab)) {
            DashboardTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) },
                )
            }
        }
        when (selectedTab) {
            DashboardTab.MONITOR -> RelayMetricsTab(relayManager, nip11Fetcher)
            DashboardTab.CONFIGURE -> RelayConfigTab(relayManager, accountRelays)
        }
    }
}
```

**4. Create `RelayMetricsTab`** (`RelayMetricsTab.kt`)

- Summary header: "X of Y connected" + global reconnect button
- LazyColumn of `RelayMetricCard` per relay, **with `key = { it.url.url }`**
- Single-expanded-at-a-time behavior (parent tracks `expandedUrl: NormalizedRelayUrl?`)

> **Research Insight (Compose):** Apply `distinctUntilChanged()` on `relayStatuses` flow before collecting. Sort by URL for stable list ordering.

```kotlin
val statuses by relayManager.relayStatuses
    .map { it.values.toList().sortedBy { s -> s.url.url } }
    .distinctUntilChanged()
    .collectAsState(emptyList())

val metrics by relayManager.relayMetrics
    .collectAsState()  // already throttled to 1Hz
```

**5. Create `RelayMetricCard`** (`RelayMetricCard.kt`)

> **Research Insight (Compose):** Hoist expansion state. Use `AnimatedVisibility` for expand/collapse. Use `produceState` for NIP-11 per-card to avoid parent recomposition cascade. Use M3 colors via `RelayStatusColors` object.

```kotlin
@Composable
fun RelayMetricCard(
    status: RelayStatus,
    metrics: RelayMetrics?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    nip11Fetcher: Nip11Fetcher,
    modifier: Modifier = Modifier,
) {
    // NIP-11 fetched per-card, no parent recomposition
    val nip11 by produceState<Nip11RelayInformation?>(null, status.url) {
        value = nip11Fetcher.fetch(status.url)
    }

    Card(modifier) {
        // Connection status icon (M3 colors)
        // Relay URL + display name from NIP-11
        // Ping with Tor badge if .onion
        // Events count (session) from metrics
        // Last event relative time from metrics
        Row(Modifier.clickable { onToggleExpand() }) { /* ... */ }

        AnimatedVisibility(isExpanded) {
            RelayDetailPanel(nip11)
        }
    }
}

object RelayStatusColors {
    @Composable fun connected() = MaterialTheme.colorScheme.primary
    @Composable fun error() = MaterialTheme.colorScheme.error
    @Composable fun disconnected() = MaterialTheme.colorScheme.outline
}
```

> **Research Insight (Desktop):** Add right-click context menu on relay rows: Copy URL, Open in Browser, Disconnect, Remove. Add tooltips on all icon buttons. Support double-click to expand.

**6. Create `RelayDetailPanel`** (`RelayDetailPanel.kt`)

Simplified NIP-11 display (per simplicity review):
- Name + description
- Software + version
- Supported NIPs (comma-separated text, not clickable chips)
- Payment: free/paid badge

> **Research Insight (Simplicity):** Cut: geo, countries, languages, contact email, retention, fees. These are relay-operator info, not end-user info.

**7. Create `RelayConfigTab`** (`RelayConfigTab.kt`)

> **Research Insight (Simplicity):** Cut from 8 categories to 3. Desktop doesn't use search/trusted/proxy/indexer/key-package relay lists yet.

Collapsible sections:
- **NIP-65 Inbox/Outbox** (kind 10002) — with read/write toggle per relay
- **DM Relays** (kind 10050)
- **Connected Relays** — the flat list that already exists (add/remove/reconnect)

Each section uses `RelayListEditor`.

**8. Create `RelayListEditor`** (`RelayListEditor.kt`)

- Relay URL input field with validation (wss://, .onion allowed)
- Add button (Enter to add)
- LazyColumn of relay items with key, **alphabetically sorted**:
  - Relay URL + NIP-11 name
  - Connection status dot
  - Remove button (X)
  - Read/write toggle (NIP-65 only)
- "Save" button — publishes updated relay list event
- "Reset to defaults" option

> **Research Insight (Simplicity):** No drag-to-reorder. Order is display-only. Alphabetical sort is deterministic and simple.

URL validation:
- Must start with `wss://` or `ws://`
- Normalize via `RelayUrlNormalizer`
- Reject duplicates within same category

> **Research Insight (Nostr):** NIP-65 uses `"r"` tags. DM (10050) uses `"relay"` tags. Different tag formats — use the appropriate event builder from quartz.
>
> **Research Insight (Nostr):** After publishing, show "Published to X of Y relays" not just "Saved". Warn if fewer than 2 relays confirm.

#### Phase 2: Compose Relay Picker

**Goal:** Expandable relay section in note compose and DM compose dialogs.

**9. Create `ComposeRelayPicker`** (`ComposeRelayPicker.kt`)

> **Research Insight (Compose):** Fully hoist state to parent. Use `combine` on `connectedRelays` (NOT `relayStatuses`) + category relay list into `@Immutable` data class with `distinctUntilChanged()`.
>
> **Research Insight (Security):** DM action type must NOT fall back to all connected relays. Block send if no kind 10050 configured.

```kotlin
@Immutable
data class RelayPickerState(
    val allRelays: Set<NormalizedRelayUrl>,
    val connectedRelays: Set<NormalizedRelayUrl>,
) {
    companion object { val EMPTY = RelayPickerState(emptySet(), emptySet()) }
}

enum class RelayActionType { NOTE, DM }

@Composable
fun ComposeRelayPicker(
    pickerState: RelayPickerState,  // hoisted — parent combines flows
    selectedRelays: Set<NormalizedRelayUrl>,  // hoisted
    onToggleRelay: (NormalizedRelayUrl) -> Unit,
    onSaveAsDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Collapsed: "▸ Relays (3 of 5)" with chips
    Row(Modifier.clickable { expanded = !expanded }) {
        Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight)
        Text("Relays (${selectedRelays.size} of ${pickerState.allRelays.size})")
    }
    if (!expanded) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(selectedRelays.toList(), key = { it.url }) { url ->
                AssistChip(onClick = {}, label = { Text(url.displayUrl()) })
            }
        }
    }

    AnimatedVisibility(expanded) {
        Column {
            // Connected relays: checkboxes
            pickerState.allRelays.sortedBy { it.url }.forEach { url ->
                val connected = url in pickerState.connectedRelays
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = url in selectedRelays,
                        onCheckedChange = { onToggleRelay(url) },
                        enabled = connected,
                    )
                    Text(
                        url.displayUrl(),
                        color = if (connected) LocalContentColor.current
                                else MaterialTheme.colorScheme.outline,
                    )
                    if (!connected) Text(" (disconnected)", style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = onSaveAsDefault) { Text("Save as default") }
        }
    }
}
```

Default relay population per action type:
- `NOTE` → NIP-65 outbox relays, fallback to all connected
- `DM` → DM relay list (kind 10050), **NO FALLBACK — block send if empty**

> **Research Insight (Nostr):** `publish(event, relays)` already exists on `RelayConnectionManager`. No new `publishTo()` method needed. For non-DM events, always include NIP-65 write relays in the set to maintain outbox model compliance.

**10. Integrate into `ComposeNoteDialog`** (`ComposeNoteDialog.kt`)

- Parent combines `connectedRelays` + NIP-65 outbox into `RelayPickerState`
- Add `ComposeRelayPicker` above send button
- Replace `relayManager.broadcastToAll(signedEvent)` with `relayManager.publish(signedEvent, selectedRelays)`

**11. Integrate into DM compose**

- Same `ComposeRelayPicker` with DM relay list as source
- If no DM relay list (kind 10050): show warning "Configure DM relays first", disable Send

#### Phase 3: Future (Deferred — not in this PR)

> **Research Insight (Simplicity):** These are all nice-to-haves without demand signal. Defer entirely. If demand emerges, create separate feature tickets.

- Relay source badges on notes (Note.addRelay already tracks this)
- Relay suggestions from contacts' NIP-65 lists
- Additional relay categories (search, trusted, proxy, indexer, key package)
- Historical metrics with SQLite persistence
- Keyboard navigation refinements (Tab/arrows in relay lists)

## System-Wide Impact

### Interaction Graph

- `RelayConnectionManager.onEvent()` → atomic increment on `_rawMetrics` ConcurrentHashMap (no StateFlow emission)
- 1Hz snapshot coroutine → copies `_rawMetrics` to `_metricsFlow` StateFlow → dashboard recomposes at max 1fps
- `relayStatuses` StateFlow → **unchanged**, only emits on connect/disconnect/error (low frequency)
- `Nip11Fetcher.fetch()` triggered per-card via `produceState` → HTTP GET via fail-closed `currentClient()` → cached in memory
- `ComposeRelayPicker` reads `connectedRelays` + category relay list via `combine().distinctUntilChanged()` → user toggles → `publish(event, selectedRelays)` called
- Relay list save → signs event via signer → publishes to relays → shows "Published to X of Y"

### Error Propagation

| Error | Source | Handling |
|-------|--------|----------|
| NIP-11 fetch fails | HTTP timeout/404/parse/Tor not ready | Show "Info unavailable" in detail panel |
| NIP-11 body too large | Malicious relay sends >256KB | Truncated by `readUtf8(MAX_RESPONSE_BYTES)` |
| Relay list publish rejected | Auth required, rate limited | Snackbar "Published to X of Y relays" with warning |
| Signer timeout (NIP-46) | Remote bunker unresponsive | "Signing timed out" dialog, offer retry |
| All relays disconnected at compose | Network failure | Warning banner in picker, disable Send |
| No DM relay list configured | User never published kind 10050 | Block DM send, show "Configure DM relays" warning |
| Tor bootstrapping when NIP-11 fetched | Tor not ready yet | `currentClient()` returns null → fetch returns null → "Info unavailable" |

### State Lifecycle Risks

- **Relay list conflict**: User edits on desktop while mobile publishes newer version → desktop overwrites. Mitigation: show "last updated" timestamp, warn if remote version is newer.
- **NIP-11 cache stale within session**: Relay updates NIP-11 info after initial fetch → acceptable, session cache is intentional.
- **Metrics lost on restart**: Session-only by design. No persistence to corrupt.

## Acceptance Criteria

### Phase 1: Dashboard

- [ ] `DeckColumnType.Relays` registered in all 7 files, compiles
- [ ] New `ScreenCategory.NETWORK` in AppDrawer
- [ ] Relays appears in App Drawer under Network
- [ ] Can pin Relays to nav bar, open as deck column
- [ ] Added to `DEFAULT_PINNED` in PinnedNavBarState
- [ ] MenuBar: View > Relay Dashboard (Cmd+Shift+R)
- [ ] Monitor tab shows all relays with: status, ping, event count (1Hz), last event time
- [ ] Separate `relayMetrics` StateFlow (1Hz), `relayStatuses` unchanged
- [ ] NIP-11 detail panel shows: name, software, supported NIPs, paid/free
- [ ] NIP-11 fetched on-demand per-card via `produceState`, Mutex dedup, fail-closed Tor
- [ ] NIP-11 response limited to 256KB
- [ ] Configure tab shows 3 categories: NIP-65 inbox/outbox, DM, Connected
- [ ] Save button per category publishes appropriate event kind
- [ ] After save, shows "Published to X of Y relays"
- [ ] URL input validates wss:// format, rejects duplicates
- [ ] Relay list sorted alphabetically (no drag reorder)
- [ ] Tor relays show .onion badge next to latency
- [ ] Right-click context menu on relay rows (Copy URL, Remove)
- [ ] M3 colors for status indicators (not hardcoded Color.Green/Red)
- [ ] Card expansion hoisted + AnimatedVisibility

### Phase 2: Compose Picker

- [ ] Expandable "Relays (N of M)" section in ComposeNoteDialog
- [ ] Collapsed shows relay name chips via LazyRow, expanded shows checkboxes
- [ ] Default: NIP-65 outbox for notes
- [ ] DM: kind 10050 relays only, **no fallback** — block send if empty
- [ ] Disconnected relays shown greyed out, not checkable
- [ ] "Save as default" persists to appropriate relay list event
- [ ] Per-action selection resets on dialog reopen
- [ ] Uses existing `publish(event, relays)` API (no new method)
- [ ] State fully hoisted, combined via `combine().distinctUntilChanged()`

## Dependencies & Prerequisites

| Dependency | Status | Notes |
|------------|--------|-------|
| `Nip11RelayInformation` (quartz) | ✅ Exists | Full data model with `fromJson()` |
| `RelayConnectionManager` | ✅ Exists | Add `relayMetrics` flow + 1Hz snapshot |
| `RelayStatusCard` | ✅ Exists | Enhance with M3 colors |
| `DesktopAccountRelays` | ✅ Exists | DM relay state |
| `Nip65RelayListState` (commons) | ✅ Exists | Inbox/outbox state |
| `DesktopHttpClient.currentClient()` | ✅ Exists | Fail-closed Tor-aware client |
| `publish(event, relays)` | ✅ Exists | Already on RelayConnectionManager |
| `Note.addRelay()` | ✅ Exists | Relay source tracking in cache |
| Relay list event types (quartz) | ✅ Exists | Kind 10002, 10050 |
| `Nip11Fetcher` | 🆕 New | HTTP + session cache + Mutex dedup |

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| NIP-11 fetch leaks IP during Tor bootstrap | Medium | **High** | Use `currentClient()` (fail-closed), not `getHttpClient()` (fail-open) |
| DM published to non-DM relays | Low | **High** | Block DM send when no kind 10050 configured, never fall back to all relays |
| NIP-11 DoS via large response | Low | Medium | 256KB body limit via `readUtf8(MAX_RESPONSE_BYTES)` |
| Dashboard recomposition churn | Medium | Medium | Separate `relayMetrics` flow at 1Hz, `relayStatuses` unchanged |
| Relay list publish partially accepted | Low | Medium | Show "Published to X of Y relays", warn if < 2 confirm |
| Relay list conflict with mobile | Low | Medium | Show "last updated" timestamp |
| Metrics persistence when Tor active leaks patterns | N/A | N/A | **Eliminated** — session-only metrics, nothing persisted to disk |

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-04-20-relay-power-tools-brainstorm.md](docs/brainstorms/2026-04-20-relay-power-tools-brainstorm.md) — Key decisions: dashboard-first build order, expandable compose picker UX, per-session NIP-11 cache

### Internal References

- `RelayConnectionManager`: `desktopApp/.../network/RelayConnectionManager.kt`
- `RelayStatus`: `desktopApp/.../network/RelayStatus.kt`
- `DesktopHttpClient.currentClient()`: `desktopApp/.../network/DesktopHttpClient.kt:168`
- `publish(event, relays)`: `desktopApp/.../network/RelayConnectionManager.kt` (already accepts relay set)
- `Note.addRelay()`: relay source tracking already in cache consume methods
- `RelayStatusCard`: `desktopApp/.../ui/relay/RelayStatusCard.kt`
- `ComposeNoteDialog`: `desktopApp/.../ui/ComposeNoteDialog.kt`
- `DeckColumnType`: `desktopApp/.../ui/deck/DeckColumnType.kt`
- `AppDrawer`: `desktopApp/.../ui/deck/AppDrawer.kt`
- `PinnedNavBarState`: `desktopApp/.../ui/deck/PinnedNavBarState.kt`
- `Nip11RelayInformation`: `quartz/.../nip11RelayInfo/Nip11RelayInformation.kt`
- FeedScreen relay subscription strategy: `docs/brainstorms/2026-03-09-feedscreen-relay-subscription-strategy-brainstorm.md`

### External References

- [NIP-11 Relay Information](https://github.com/nostr-protocol/nips/blob/master/11.md)
- [NIP-65 Relay List Metadata](https://github.com/nostr-protocol/nips/blob/master/65.md)
- [NIP-17 Private Direct Messages](https://github.com/nostr-protocol/nips/blob/master/17.md)

## Unanswered Questions

1. Should `currentClient()` in DesktopHttpClient be the default for ALL non-websocket HTTP requests, or just NIP-11?
2. Should the relay config "Save" button compare `created_at` with the remote version before overwriting?
3. Should Relays be in `DEFAULT_PINNED` or just pinnable?
4. Does `DesktopHttpClient.currentClient()` support the `Accept: application/nostr+json` header routing, or does it need a separate method for HTTP GET vs WebSocket?
