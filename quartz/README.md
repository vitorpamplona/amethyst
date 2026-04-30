# Quartz

A Kotlin Multiplatform Nostr library — protocol, signing, relay client, event store, and reactive projections. No UI, no Android dependencies in `commonMain`. Targets Android, JVM/Desktop, iOS, and Linux.

## Layered architecture

Build apps by composing layers from durable storage at the bottom up to UI projections at the top:

```
┌──────────────────────────────────────────┐
│  UI / ViewModel                          │
│  observable.project<T>(filter)           │
│      .stateIn(scope, ...)                │  ← reactive list of MutableStateFlow<Event>
└──────────────────────────────────────────┘
                  ▲
┌──────────────────────────────────────────┐
│  ObservableEventStore                    │
│  publishes StoreChange on `changes`      │  ← bus for reactive consumers
└──────────────────────────────────────────┘
                  ▲
┌──────────────────────────────────────────┐
│  InterningEventStore                     │
│  one Event instance per id, weak refs    │  ← shared identity across reads
└──────────────────────────────────────────┘
                  ▲
┌──────────────────────────────────────────┐
│  EventStore (SQLite) / FsEventStore      │
│  durable, NIP-01/09/40/62 enforced       │  ← persistence + Nostr semantics
└──────────────────────────────────────────┘
                  ▲
┌──────────────────────────────────────────┐
│  NostrClient                             │
│  relay subscriptions, NIP-01 messages    │  ← network
└──────────────────────────────────────────┘
```

Each layer is optional — you can use just `NostrClient` (see [CLIENT.md](CLIENT.md)), just `EventStore` (see [the SQLite store README](src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/README.md)), or compose the full pipeline below.

## Wiring relays into the store

`NostrClient` delivers every event it receives through a connection-level message stream. Quartz ships an [`EventCollector`](src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/client/accessories/EventCollector.kt) that taps into this stream once and forwards every `EventMessage` into a sink — typically the store. Wire it once at app init and the cache-write path is decoupled from "what we ask for":

```kotlin
// Application init — one set of these per process. Construct before any UI mounts.
val sqlite = EventStore(dbName = "events.db", relay = "wss://relay.damus.io".normalizeRelayUrl())
val db = ObservableEventStore(InterningEventStore(sqlite))

val client = NostrClient(websocketBuilder = ktorBuilder)

// Connection-wide drain: every EventMessage on every subscription on every
// relay lands in the store, regardless of which subscription pulled it.
val collector = EventCollector(client) { event, _ ->
    appScope.launch { runCatching { db.insert(event) } }
}

// Periodic NIP-40 sweep — projections drop expired events when this fires.
appScope.launch {
    while (isActive) { delay(15.minutes); db.deleteExpiredEvents() }
}
```

`db.insert(event)` is idempotent under NIP-01 supersession (older replaceables / addressables are rejected by the inner store) and validates expiration / vanish tombstones, so it's safe to fire-and-forget for every relay arrival.

`InterningEventStore` keeps one `Event` instance alive per id (weakly, via `EventInterner.Default`), so events that re-arrive from multiple relays — or get re-read by query — share the same object reference as long as some projection holds them.

`NostrClient` connects on-demand: the first `subscribe(...)` or `publish(...)` to a relay triggers the socket. There's no need to call `client.connect()` at startup — it's only useful for resuming after a prior `disconnect()`.

## Building a reactive feed UI

A feed reads from the store via `ObservableEventStore.project()`, which returns a cold `Flow<ProjectionState<T>>`. The recommended pattern is to construct it inside a `ViewModel` so `viewModelScope` owns the `stateIn` lifecycle, and to tie the relay-side `subscribe` / `unsubscribe` to the projection's collection lifetime via `onStart` / `onCompletion`:

```kotlin
@Stable
class HomeFeedViewModel(
    private val db: ObservableEventStore,
    private val client: NostrClient,
    private val relays: Set<NormalizedRelayUrl>,
    followedAuthors: List<HexKey>,
) : ViewModel() {
    private val subId = newSubId()
    private val filter = Filter(kinds = listOf(1), authors = followedAuthors, limit = 200)

    val state: StateFlow<ProjectionState<TextNoteEvent>> =
        db
            .project<TextNoteEvent>(filter)
            // Optional: narrow the live projection without re-querying the store.
            // ProjectionState.filterItems / mapItems lift over Loading / Loaded.
            .filterItems { it.value.replyingTo() == null }
            .onStart { client.subscribe(subId, relays.associateWith { listOf(filter) }) }
            .onCompletion { client.unsubscribe(subId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectionState.Loading)
}

@Composable
fun HomeFeedScreen(vm: HomeFeedViewModel) {
    val state by vm.state.collectAsState()
    when (val s = state) {
        is ProjectionState.Loading -> SpinnerScaffold()
        is ProjectionState.Loaded -> {
            // Layer 1: list reference is stable while membership is unchanged.
            // LazyColumn only invalidates structure on inserts / removals.
            LazyColumn {
                items(s.items, key = { it.value.id }) { handle ->
                    NoteRow(handle)
                }
            }
        }
    }
}

@Composable
fun NoteRow(handle: MutableStateFlow<TextNoteEvent>) {
    // Layer 2: only collectors of THIS handle re-render when the
    // event mutates in place (addressable supersession, etc.).
    val event by handle.collectAsState()
    Column {
        Text(event.content)
        // Layer 3: derived flows — e.g. counters reactive to OTHER projections.
        ReactionRow(event.id)
    }
}
```

The three layers map cleanly to Compose's recomposition model:

- `state` re-renders the screen scaffolding (`Loading` vs `Loaded`).
- `s.items` re-emits only when membership changes (insert, NIP-09 deletion, NIP-62 vanish, NIP-40 expiration sweep, manual `delete(filter)`, or a `filterItems` predicate flip).
- `handle` re-emits only when its specific event mutates in place (e.g. a new version of a kind-30023 long-form post supersedes the previous one).

`SharingStarted.WhileSubscribed(5_000)` ties the relay subscription's lifetime to the UI: when the last collector goes away (composable leaves the tree, window minimised, etc.), `onCompletion` fires after a 5 s debounce and `client.unsubscribe(subId)` runs. A new collector restarts the upstream and `onStart` re-opens the subscription — the cache stays warm in SQLite the whole time.

## Publishing from the UI

Sign locally, drop on the bus, then broadcast to relays:

```kotlin
val signer = NostrSignerInternal(KeyPair())

fun send(text: String) {
    viewModelScope.launch {
        val signed = signer.sign<TextNoteEvent>(TextNoteEvent.build(text))
        db.insert(signed)              // hits the bus → all open projections see it
        client.publish(signed, relays) // broadcast to relays
    }
}
```

`db.insert(signed)` enforces NIP-01 supersession, NIP-09 author checks, NIP-62 vanish scoping, and NIP-40 expiration on the way in; the projection layer applies the same rules on top. UI code never needs to know any of these details.

## Where to read more

- [`CLIENT.md`](CLIENT.md) — building a relay client with `NostrClient` and Ktor.
- [`RELAY.md`](RELAY.md) — running a relay server with Quartz.
- [SQLite event store README](src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/README.md) — query planner, indexing strategies, NIP-09/40/62 enforcement details.
- KDoc on `EventStoreProjection`, `ObservableEventStore`, `EventInterner` — package-level reference for the projection layer.
