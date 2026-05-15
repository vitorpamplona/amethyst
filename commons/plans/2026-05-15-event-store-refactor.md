# Event Store + Projection Refactor

**Status**: Design proposal (2026-05-15)
**Branch**: `claude/redesign-event-caching-VPFQi`
**Scope**: Replace `LocalCache` + fat `Note` + bespoke `Channel` indexes with a layered store-first / projection-second / window-third architecture. Rollout strategy is intentionally deferred — see [§13](#13-rollout-deferred).

---

## 1. Why

Amethyst's in-memory cache is the limiting factor on memory footprint, cold-start latency, and architectural agility. The current shape has three structural problems:

1. **Fat `Note` objects live forever for every event seen.** A 50,000-event home feed materialises 50,000 `Note` instances (`commons/src/commonMain/.../Note.kt:1`, 1,081 LOC). Each holds mutable lists for replies, reactions (grouped by emoji), boosts, zaps, reports, relays, plus `flowSet` invalidation hooks. Most of that state is never read because the user only sees ~30 events at a time. The same is true for `User`.
2. **No layered cache.** `CachedRichTextParser` has its own LRU keyed by content hash (not event id). `Channel` has its own `LargeCache<HexKey, Note>` per public chat / live activity. Decrypted DMs live in per-account ViewModel-scoped containers. Each subsystem reinvents lifecycle and eviction.
3. **No preload window.** When a row scrolls in, supporting state (zaps, reactions, edits, OTS, quoted-event resolution, rich text) is either already-loaded eagerly or not-loaded and shows late. There is no explicit "active window" that says "these 30 ids need their bundles ready by next frame."

Quartz already ships the substrate we want — `EventStore` (SQLite), `InterningEventStore`, `ObservableEventStore`, `EventCollector` — documented in `quartz/CLIENT.md:1` and prescribed in `quartz/README.md:1` as the canonical client setup. The app simply hasn't adopted it.

---

## 2. Goals

- **One persistent source of truth per scope.** Shared public SQLite for all received events; one private SQLite per logged-in account for decrypted events. No parallel in-memory caches duplicating storage.
- **Fat `Note` is dead.** Per-event bundles (reactions, zaps, replies, OTS, edits, …) live in dimension coordinators and are materialised only for events in an active screen window.
- **O(window) bundle memory.** A 50,000-event feed costs O(feed) lightweight headers plus O(window) bundles, not O(feed) bundles.
- **Compose ergonomics preserved.** A row composable still receives a `Note`; mechanically it is a refcounted handle valid for the lifetime of the active window. Screens do not deal with raw event ids unless they want to.
- **Uniform handling of attached events.** Same machinery for every kind that attaches to another event: replies, reposts, quotes, reactions, zaps, reports, comments, highlights, community approvals, OTS, edits.
- **Rich text parse keyed by event id**, derived from the resolved (post-edit) event, cached LRU under memory pressure, refcounted to active windows.

## 3. Non-Goals

- Full Nostr re-implementation. Quartz stays the protocol layer.
- Backwards-compatibility with `LocalCache` API. The new graph replaces it wholesale.
- Search-index rewrite. SQLite FTS remains as today.
- Audio rooms / MoQ rewrite. `nestsClient` is unaffected.

---

## 4. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ Screen / ViewModel                                              │
│   reads Note handles from window.note(id) and User handles      │
│   from window.user(pk); never sees a coordinator directly.      │
└─────────────────────────────────┬───────────────────────────────┘
                                  │  visible ids ± lookahead
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│ FeedPreloadOrchestrator (per screen window)                     │
│   diff + debounce setActive(ids); constructs/disposes Note      │
│   handles; same for UserPreloadOrchestrator(authors).           │
└─────────────────────────────────┬───────────────────────────────┘
                                  │  setActive(ids) / setActive(pks)
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│ Dimension coordinators (singletons; refcounted by id/pk)        │
│   Edits, Reactions, Zaps, Replies, Reposts, Quotes, Reports,    │
│   Comments, Highlights, Approvals, Ots,                         │
│   Metadata, RelayList, Nip05, UserStatus, Reports-of-user,      │
│   QuoteResolution, RichText (derived from Edits).               │
└─────────────────────────────────┬───────────────────────────────┘
                                  │  Filter (union of active ids)
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│ AccountScopedView (per logged-in account)                       │
│   merges public + private projection streams                     │
└─────────────────────┬───────────────────────────────┬───────────┘
                      │                               │
                      ▼                               ▼
       ObservableEventStore                ObservableEventStore
       InterningEventStore                 InterningEventStore
       SQLite (events.db, shared)          SQLite (events-priv-<pk>.db)
              ▲                                       ▲
              │ EventCollector                        │ PrivateEventDecryptor
              │                                       │ (listens to public
              NostrClient                             │  StoreChange, inserts
              + SubscriptionManager                   │  decrypted rumors)
```

Three load-bearing ideas:

1. **The store is authoritative.** All event mutations go through `ObservableEventStore.insert`. UI never holds events that the store does not also hold.
2. **Bundles are window-scoped.** `Note` and `User` instances exist only while an orchestrator declares the underlying id/pubkey "active". Their `StateFlow` fields are lazy delegates to coordinators; an unread field allocates nothing.
3. **Coordinators are dimensions, not aggregates.** Each coordinator handles one kind/relationship across all active ids, with one multiplexed relay subscription and one in-memory index. There is no per-Note set of `MutableSharedFlow`s.

---

## 5. Storage Layer

### 5.1 Public store

One process-wide store, shared across accounts:

```kotlin
object StoreGraph {
    private val sqlite     = EventStore(dbName = "events.db")
    private val interned   = InterningEventStore(sqlite)
    val publicStore        = ObservableEventStore(interned)
    val client             = NostrClient(websocketBuilder = …)
    val collector          = EventCollector(client) { ev, _ ->
        runCatching { publicStore.insert(ev) }
    }
}
```

Addressable / replaceable supersession and NIP-09 deletes are enforced by SQLite triggers shipped in Quartz today (`AddressableModule`, `ReplaceableModule`, `DeletionRequestModule`). No in-memory bookkeeping replaces them.

A `scope.launch` runs `publicStore.deleteExpiredEvents()` every 15 minutes per the Quartz template (`quartz/README.md:38`).

### 5.2 Private store per account

For each logged-in account, a separate SQLite file holds decrypted rumours from NIP-04 / NIP-17 wraps, plus any other privacy-sensitive derived events (decoded zap requests where they contained private content, etc.):

```kotlin
class AccountStore(
    val account: Account,
    val publicStore: ObservableEventStore,
) {
    private val sqlite     = EventStore(
        dbName = "events-priv-${account.pubkey.take(8)}.db",
        passphrase = AccountDbKey.derive(account.signer),
    )
    private val interned   = InterningEventStore(sqlite)
    val privateStore       = ObservableEventStore(interned)

    val view: AccountScopedView = AccountScopedView(publicStore, privateStore)

    private val decryptor = PrivateEventDecryptor(
        publicStore = publicStore,
        privateStore = privateStore,
        signer = account.signer,
        watermarkStore = …,    // last seen wrap created_at, persisted in privateStore
    )

    fun start() = decryptor.start()
    fun stop()  = decryptor.stop()
}
```

**Encryption at rest.** SQLCipher (or platform-equivalent AEAD page codec on JVM) keyed by `AccountDbKey.derive(signer)`. For `NostrSignerInternal` the key is derived deterministically via NIP-44 conversation key against an app-scoped fixed pubkey, so the same login always unlocks the same file. For `NostrSignerExternal` / `NostrSignerRemote` the key is a random blob wrapped at first login (NIP-44 encrypt to self), stored in the public DB, unwrapped on subsequent logins via one remote-signer call.

**Decryptor.** Listens to `publicStore.changes` for `Insert(event)` where `event.kind in {1059, 1060, 4, …}` and `event.tags("p").any { it == account.pubkey }`. Decrypts, unwraps seal+rumor, stores the rumor with `id = wrap.id` (idempotent: same wrap always produces same private row).

**Watermark.** On app start, the decryptor scans `publicStore` for wraps newer than the last watermarked `created_at` and replays them. Backfill is O(wraps-since-last-login), not O(all-wraps-ever).

### 5.3 `AccountScopedView`

A thin union that exposes a single `changes: SharedFlow<StoreChange>` interleaving public and private store events, and `query(Filter)` calls that read both. Coordinators bind to this view, not to either store directly, so DMs participate in `RepliesCoordinator`, `ReactionsCoordinator`, etc. exactly like public notes.

```kotlin
class AccountScopedView(
    val public: ObservableEventStore,
    val private: ObservableEventStore,
) {
    val changes: SharedFlow<StoreChange> =
        merge(public.changes, private.changes).shareIn(...)

    suspend fun query(filter: Filter): List<Event> =
        public.query(filter) + private.query(filter)

    suspend fun queryHeaders(filter: Filter): List<EventHeader> = …
}
```

Account switch tears down the current `AccountScopedView` (closes private DB, cancels decryptor, lets coordinators detach all ids), and brings up a new one. The public store is untouched.

---

## 6. Projection Layer (Coordinators)

### 6.1 Generic shape

Every "kind X attached to event Y via tag" follows the same template:

```kotlin
abstract class AttachedEventsCoordinator<T>(
    private val view: AccountScopedView,
    private val subscriptions: SubscriptionManager,
    private val scope: CoroutineScope,
) {
    protected abstract val attachTag: String                  // "e" usually
    protected abstract val filterTemplate: (Set<HexKey>) -> List<Filter>
    protected abstract fun fold(prev: T, event: Event): T
    protected abstract fun unfold(prev: T, deletedId: HexKey): T
    protected abstract val empty: T

    private val states = ConcurrentHashMap<HexKey, MutableStateFlow<T>>()
    private val refCounts = ConcurrentHashMap<HexKey, Int>()
    private val activeIds = MutableStateFlow<Set<HexKey>>(emptySet())

    fun stateFor(id: HexKey): StateFlow<T> {
        refCounts.compute(id) { _, n -> (n ?: 0) + 1 }
        states.computeIfAbsent(id) { MutableStateFlow(empty) }
        recomputeActive()
        return states[id]!!.asStateFlow()
    }

    fun release(id: HexKey) {
        val n = refCounts.computeIfPresent(id) { _, n -> (n - 1).takeIf { it > 0 } }
        if (n == null) {
            states.remove(id)
            recomputeActive()
        }
    }

    private fun recomputeActive() { activeIds.value = refCounts.keys.toSet() }

    init {
        // One multiplexed subscription whose filter is rebuilt
        // from the active set, debounced.
        scope.launch {
            activeIds
                .debounce(80.milliseconds)
                .distinctUntilChanged()
                .collectLatest { ids ->
                    if (ids.isEmpty()) {
                        subscriptions.close(subId)
                    } else {
                        subscriptions.open(subId, filterTemplate(ids))
                    }
                    // Hot-fill from store, then live updates via changes.
                    ids.forEach { id ->
                        val seed = view.query(filterTemplate(setOf(id)).first())
                        states[id]?.update { acc -> seed.fold(empty, ::fold) }
                    }
                }
        }
        // Live updates from store
        scope.launch {
            view.changes.collect { change ->
                when (change) {
                    is StoreChange.Insert -> dispatchInsert(change.event)
                    is StoreChange.DeleteByFilter -> dispatchDelete(change.filters)
                    is StoreChange.DeleteExpired -> dispatchExpiry(change.asOf)
                }
            }
        }
    }
}
```

Key properties:

- `states` and `refCounts` size is O(union of active windows across screens), not O(feed).
- One subscription per coordinator, regardless of how many ids are attached.
- `release(id)` is idempotent and fast; orchestrator calls it in bulk on window-leave.
- `fold` is dimension-specific: `ZapsCoordinator` sums amounts and groups by zapper; `ReactionsCoordinator` buckets by emoji; default for the rest is "append id to list".

### 6.2 Coordinator catalog

| Coordinator | Kind(s) | Attached via | Aggregation |
|---|---|---|---|
| `EditsCoordinator` | edit kinds (TBD per NIP) | `e` | Resolve "latest" by `created_at` |
| `RepliesCoordinator` | 1 | `e` with marker `reply` | List of ids, sorted by `created_at` |
| `QuotesCoordinator` | 1 | `q` tag or `e` mention marker | List of ids |
| `RepostsCoordinator` | 6, 16 | `e` | List of (reposter, ts) |
| `ReactionsCoordinator` | 7 | `e` | Map<emoji, Set<author>> |
| `ZapsCoordinator` | 9735 | `e` | Sum sats; list of (zapper, amount, comment) |
| `ReportsCoordinator` | 1984 | `e` | List of (reporter, reason) |
| `CommentsCoordinator` | 1111 | `e` (NIP-22) | List of ids |
| `HighlightsCoordinator` | 9802 | `e` | List of ids |
| `ApprovalsCoordinator` | 4550 | `e` (NIP-72) | List of approvals per community |
| `OtsCoordinator` | 1040 | `e` | Latest OTS proof |
| `MyReactionCoordinator` | 7 | `e`, `authors=[me]` | Current emoji or null |
| `MyZapCoordinator` | 9735 | `e`, sender=me | My zap (amount, status) |
| `MetadataCoordinator` | 0 | `authors` | Latest metadata per pubkey |
| `RelayListCoordinator` | 10002, 10050 | `authors` | Latest per pubkey |
| `Nip05Coordinator` | derived | resolves NIP-05 of metadata | Verification state |
| `UserStatusCoordinator` | 30315 | `authors` | Latest status per pubkey |
| `ReportsOfUserCoordinator` | 1984 | `p` | List of reports |
| `QuoteResolutionCoordinator` | any | resolves nevent/naddr refs in content | Map<bech32, Event?> |
| `RichTextCoordinator` | — | derived over `EditsCoordinator` + `QuoteResolutionCoordinator` | `RichTextViewerState` |

`RichTextCoordinator` is the only derived one: its input is the resolved event from `EditsCoordinator`, not the raw event. It re-parses only when the resolved event changes, not on every reaction/zap.

### 6.3 Channels collapse to projections

`PublicChatChannel`, `LiveActivitiesChannel`, `EphemeralChannel`, and community streams are no longer subclasses of anything. A channel is just an event in the store (kind 40 / 30311 / 34550) plus a projection:

```kotlin
class ChannelMessagesProjection(
    val channelId: Address,
    private val view: AccountScopedView,
): … {
    val headers: StateFlow<List<EventHeader>> =
        view.queryHeadersAsFlow(Filter(kinds = listOf(42), tags = mapOf("e" to listOf(channelId.toTagValue()))))
}
```

Channel metadata (name, picture, about) is `MetadataCoordinator` over the channel's defining event. Participants list is a tiny dedicated projection. The 226-line `Channel.kt`, `PublicChatChannel.kt`, `LiveActivitiesChannel.kt`, etc. are deleted.

---

## 7. Window / Preload Orchestration

### 7.1 Three tiers of in-memory weight

| Tier | Contents | Size (50k feed example) | Owner |
|---|---|---|---|
| Storage | Full event bodies + tag indexes in SQLite | on disk + OS page cache | kernel |
| Feed projection | `EventHeader(id, kind, author, created_at, parent_id, flags)` rows | ~80 B × 50k ≈ 4 MB | `FeedViewModel` per screen |
| Active bundles | `Note` handles with lazy `StateFlow` fields | ~2 KB × ~30 ≈ 60 KB | `FeedPreloadOrchestrator` |

The 50k figure is just feed headers. Full `Event` bodies are loaded on window-attach (cheap; interned and page-cache hot).

### 7.2 `FeedPreloadOrchestrator`

```kotlin
class FeedPreloadOrchestrator(
    val view: AccountScopedView,
    val coordinators: Coordinators,
    val scope: CoroutineScope,
    val lookahead: Int = 20,
) {
    private val active = MutableStateFlow<Set<HexKey>>(emptySet())
    private val notes  = ConcurrentHashMap<HexKey, Note>()

    fun setActive(ids: Set<HexKey>) {
        val prev = active.value
        val toRelease = prev - ids
        val toAttach  = ids - prev
        toRelease.forEach { id -> disposeNote(id) }
        toAttach.forEach  { id -> createNote(id) }
        active.value = ids
    }

    fun note(id: HexKey): Note? = notes[id]

    private fun createNote(id: HexKey) {
        notes.computeIfAbsent(id) { Note(id, coordinators, view, scope) }
    }
    private fun disposeNote(id: HexKey) {
        notes.remove(id)?.dispose()
    }
}
```

The orchestrator owns the lifecycle. Screens never `new Note(…)` directly.

### 7.3 Compose API

```kotlin
@Composable
fun rememberFeedWindow(
    lazyState: LazyListState,
    feedIds: List<HexKey>,
    lookahead: Int = 20,
): FeedWindow {
    val orchestrator = LocalOrchestrator.current
    val window = remember(feedIds) { FeedWindow(orchestrator, lookahead) }
    LaunchedEffect(lazyState, feedIds) {
        snapshotFlow {
            val info = lazyState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last  = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val from  = (first - lookahead).coerceAtLeast(0)
            val to    = (last + lookahead).coerceAtMost(feedIds.lastIndex)
            feedIds.subList(from, to + 1).toSet()
        }.debounce(80.milliseconds)
         .distinctUntilChanged()
         .collect { orchestrator.setActive(it) }
    }
    DisposableEffect(window) { onDispose { window.dispose() } }
    return window
}

@Composable
fun NoteFeed(feedIds: List<HexKey>) {
    val lazyState = rememberLazyListState()
    val window = rememberFeedWindow(lazyState, feedIds)
    LazyColumn(state = lazyState) {
        items(feedIds, key = { it }) { id ->
            when (val note = window.note(id)) {
                null -> NoteRowSkeleton(id)
                else -> NoteRow(note)
            }
        }
    }
}
```

`window.note(id)` is the only public way to get a `Note`. There is no `LocalCache.getOrCreateNote(id)`. A `rememberSingleNote(id)` helper exists for non-feed contexts (thread root, deep-linked event) — internally it is a degenerate window of size 1.

### 7.4 User windows

`UserPreloadOrchestrator` mirrors the feed orchestrator for pubkeys. A profile screen, a follow list, an authors-in-viewport set — each declares its active pubkeys via `userWindow.setActive(pks)`. `MetadataCoordinator`, `RelayListCoordinator`, `Nip05Coordinator`, `UserStatusCoordinator`, `ReportsOfUserCoordinator` all share the same shape as the event coordinators.

In a feed, the feed orchestrator derives the active pubkey set from the active event set (`activeIds.flatMap { headers[it]?.author }.toSet()`) and forwards to the user orchestrator. One declaration, two windows kept in sync.

---

## 8. `Note` Redesign

`Note` survives as an ergonomic, but its constraints are different:

```kotlin
class Note internal constructor(
    val id: HexKey,
    private val coords: Coordinators,
    private val view: AccountScopedView,
    private val scope: CoroutineScope,
) {
    val event: StateFlow<Event?>      by lazy { coords.edits.stateFor(id) }   // resolved
    val author: StateFlow<User?>      by lazy { coords.authorFor(id) }

    val richText: StateFlow<RichTextViewerState> by lazy { coords.richText.stateFor(id) }
    val reactions: StateFlow<ReactionsBundle>    by lazy { coords.reactions.stateFor(id) }
    val zaps: StateFlow<ZapBundle>               by lazy { coords.zaps.stateFor(id) }
    val replies: StateFlow<List<HexKey>>         by lazy { coords.replies.stateFor(id) }
    val reposts: StateFlow<RepostsBundle>        by lazy { coords.reposts.stateFor(id) }
    val quotes: StateFlow<List<HexKey>>          by lazy { coords.quotes.stateFor(id) }
    val reports: StateFlow<List<ReportRef>>      by lazy { coords.reports.stateFor(id) }
    val comments: StateFlow<List<HexKey>>        by lazy { coords.comments.stateFor(id) }
    val highlights: StateFlow<List<HexKey>>      by lazy { coords.highlights.stateFor(id) }
    val approvals: StateFlow<List<ApprovalRef>>  by lazy { coords.approvals.stateFor(id) }
    val ots: StateFlow<OtsBundle?>               by lazy { coords.ots.stateFor(id) }
    val myReaction: StateFlow<String?>           by lazy { coords.myReaction.stateFor(id) }
    val myZap: StateFlow<MyZapBundle?>           by lazy { coords.myZap.stateFor(id) }

    internal fun dispose() {
        // Release whichever coordinators were actually accessed
        accessed.forEach { it.release(id) }
    }
}
```

Constraints that prevent regression to god-object:

1. **Lifetime is the window, not the app.** A `Note` is created by an orchestrator when its id enters an active set and disposed when it leaves. There is no app-global note registry.
2. **References to other events are ids, not Notes.** `replies: StateFlow<List<HexKey>>`, not `List<Note>`. Rendering a reply tree requires declaring those ids in another window.
3. **No setters, no mutators.** `Note` exposes only `StateFlow`s. The old `addReply`, `addZap`, `addReaction` mutator API is gone; writes go to the store, the store emits, projections reduce, Compose recomposes.
4. **`AddressableNote` collapses.** Addressable identity is just `Address(kind, pubkey, dTag)`; supersession is enforced by SQLite. A `noteForAddress(addr)` helper resolves to the current event id via store query, then proceeds as for any other id.
5. **Lazy fields.** A field that is never read allocates nothing. A `Note` rendered without ever asking for `ots` never instantiates `OtsBundle` state.

`User` follows the identical pattern (`UserMetadata`, `Nip05`, `RelayList`, `Status`, `Reports`).

---

## 9. Subscription Manager

The window/coordinator design depends on a small `SubscriptionManager` distinct from Quartz's `NostrClient`:

```kotlin
class SubscriptionManager(val client: NostrClient) {
    fun open(subId: SubscriptionId, filters: List<Filter>, relays: Set<NormalizedRelayUrl>? = null)
    fun close(subId: SubscriptionId)
}
```

Responsibilities:

- Per-coordinator subscription id is stable for the lifetime of the coordinator.
- Filter rebuilds on every active-set change are diffed; if the resulting `REQ` is byte-identical (modulo subId reuse, no-op), the manager skips the round-trip.
- Relay set defaults to the account's NIP-65 outbox/inbox; per-coordinator overrides are allowed (e.g., `RelayListCoordinator` queries only inbox relays of the target pubkey).
- Connection lifecycle, reconnection, OK/EOSE handling stay in `NostrClient` (`quartz/CLIENT.md:303`).

This is essentially a generalisation of `commons/.../relayClient/assemblers/FeedMetadataCoordinator.kt:1`.

---

## 10. File Layout

New code lives in `commons/src/commonMain/.../store/`:

```
commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/store/
├── StoreGraph.kt                       # public + per-account wiring
├── PrivateEventDecryptor.kt
├── AccountStore.kt
├── AccountScopedView.kt
├── coordinators/
│   ├── AttachedEventsCoordinator.kt    # base class
│   ├── EditsCoordinator.kt
│   ├── RepliesCoordinator.kt
│   ├── QuotesCoordinator.kt
│   ├── RepostsCoordinator.kt
│   ├── ReactionsCoordinator.kt
│   ├── ZapsCoordinator.kt
│   ├── ReportsCoordinator.kt
│   ├── CommentsCoordinator.kt
│   ├── HighlightsCoordinator.kt
│   ├── ApprovalsCoordinator.kt
│   ├── OtsCoordinator.kt
│   ├── MyReactionCoordinator.kt
│   ├── MyZapCoordinator.kt
│   ├── MetadataCoordinator.kt
│   ├── RelayListCoordinator.kt
│   ├── Nip05Coordinator.kt
│   ├── UserStatusCoordinator.kt
│   ├── ReportsOfUserCoordinator.kt
│   ├── QuoteResolutionCoordinator.kt
│   └── RichTextCoordinator.kt          # derived
├── preload/
│   ├── FeedPreloadOrchestrator.kt
│   ├── UserPreloadOrchestrator.kt
│   ├── FeedWindow.kt
│   └── WindowState.kt
├── handles/
│   ├── Note.kt                         # window-scoped handle (replaces old Note)
│   └── User.kt                         # window-scoped handle (replaces old User)
├── subscriptions/
│   ├── SubscriptionManager.kt
│   └── FilterMerge.kt
└── compose/
    ├── rememberFeedWindow.kt
    ├── rememberUserWindow.kt
    └── rememberSingleNote.kt
```

Platform-specific glue (SQLCipher on Android, JVM AEAD page codec on Desktop) goes in `androidMain` / `jvmMain` via expect/actual `AccountDbKey` and `EventStore` builders.

---

## 11. Kill List

Files and APIs that disappear once cutover completes:

- `amethyst/src/main/java/.../LocalCache.kt` (3,427 LOC) — entire file.
- `commons/src/commonMain/.../Note.kt` (1,081 LOC) — replaced by `store/handles/Note.kt`.
- `commons/src/commonMain/.../User.kt` (153 LOC) — replaced by `store/handles/User.kt`.
- `commons/src/commonMain/.../Channel.kt` + `PublicChatChannel.kt` + `LiveActivitiesChannel.kt` + `EphemeralChannel.kt` — replaced by per-channel projections over the store.
- `amethyst/.../CachedRichTextParser.kt` (113 LOC) — replaced by `RichTextCoordinator`. `RichTextParser` stays as a pure parser (now invoked by the coordinator).
- The DeletionIndex / FilterIndex inner machinery of `LocalCache` — replaced by SQLite tag index + Quartz's `DeletionRequestModule`.
- Per-account ad-hoc decrypted DM containers — replaced by `AccountStore`.

`LargeCache` stays — it is used by code outside this refactor (e.g. internal caches inside Quartz). `HintIndexer` stays — it is orthogonal.

---

## 12. Open Design Questions

These don't block writing the plan but must be answered before code lands:

1. **Edit kind.** Which NIP / kind does Amethyst use for kind-1 edits today? (To be determined by spelunking `consume(TextNoteEvent…)` paths in `LocalCache`.) The `EditsCoordinator` is identical regardless; only its filter changes.
2. **Drafts vs published edits.** The user mentioned "prepare edits for kind 1s." If this also means an authoring-side draft cache (typed-but-unsent edits), that's a separate concern from inbound edit resolution and may live in `AccountStore` (private store, ephemeral kinds).
3. **Search.** SQLite FTS over the public store is straightforward, but search across the private store needs care (per-account FTS index inside the private DB). Defer to a follow-up plan.
4. **Negentropy.** `EventStore.snapshotIdsForNegentropy` exists; how do we wire it into the new subscription manager for relay sync? Defer.
5. **Image / blob caching.** Coil/equivalent caches stay as today; not in scope. `MetadataCoordinator` will continue to feed avatar URLs into the existing image prefetch path.
6. **OTS verification scheduling.** `OtsCoordinator` materialises the latest 1040 attestation; actual verification (cryptographic OTS proof check) is async and may run in a different scope. Verification result cache could live in private store as a synthetic kind.
7. **Feed header source.** `EventHeader` lists come from SQLite. A reactive header flow (`view.queryHeadersAsFlow(filter)`) is needed; Quartz's `project<T>(filter)` returns events, not headers — we may need a header-projection variant for memory-cheap feed scans.
8. **Per-screen vs global orchestrators.** Currently sketched as per-screen, but a global registry that shards by screen tag has marginal advantages (cross-screen dedup). Trade-off: simpler lifecycle (per-screen) vs less re-fetching (global). Recommendation: per-screen for now, observe if dedup is meaningful in practice.

---

## 13. Rollout (deferred)

Per direction, we are designing the ideal architecture first and figuring out rollout afterward. Realistic options when we revisit:

- **Build flag in `main`, off by default** — new graph lives alongside old, every PR reviewable in isolation, flip is one commit. Avoids long-lived-branch rebase load.
- **Long-lived feature branch** — clean `main` during dev, single big merge, expect heavy rebase load.
- **Hybrid: flag in main, on for debug builds** — internal/dogfood builds use new store, releases use old until parity is met.

Cross-cutting parity bar (must work on the new store before flipping to users):

- Home feed, profile, hashtag, bookmarks, notifications, mentions
- DMs (NIP-04 + NIP-17), public chats (NIP-28), communities (NIP-72)
- Reactions, zaps (NIP-57 + nutzaps), reposts, quotes, replies, reports, comments, highlights, approvals, edits, OTS
- Long-form (NIP-23), live activities (NIP-53), audio rooms (NIP-53 client-side reuse)
- Mute / block lists, follow lists, relay lists (NIP-65), drafts
- Multi-account switch, login (internal / NIP-46 bunker / NIP-55 external)
- Search

Choice of mechanism is the first decision when we resume rollout planning.

---

## 14. Appendix: Coordinator Memory Math

For a 50k home feed scrolled to position 1,000 with a window of 30 + lookahead 20:

- Headers: 50,000 × ~80 B ≈ **4 MB** (constant; one allocation per feed reload).
- Active `Note` handles: 50 × ~2 KB ≈ **0.1 MB** (lazy field StateFlows allocated on first read).
- Coordinator per-id state, summed across 14 coordinators × 50 active ids: 700 entries × ~120 B ≈ **0.08 MB**.
- One `Filter` per coordinator with up to 50 ids: 14 × small ≈ **0.02 MB**.

Total active in-memory cache for this feed: **~4.2 MB**. Today's `LocalCache` + fat `Note` design holds order-of-100-MB+ for the same workload (50k full `Note` objects with their mutable sets, plus 50k entries in `LocalCache.notes`, plus `FilterIndex` fan-out registrations, plus per-channel `LargeCache` overlap). Conservatively a **20–50× reduction**.
