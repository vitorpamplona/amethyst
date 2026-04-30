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

`NostrClient` delivers events through a `SubscriptionListener.onEvent` callback. Pipe each event into the `ObservableEventStore`, which persists it (via the inner store) and publishes it to every open projection:

```kotlin
// Application init — one set of these per process.
val sqlite = EventStore(dbName = "events.db", relay = "wss://relay.damus.io".normalizeRelayUrl())
val observable = ObservableEventStore(InterningEventStore(sqlite))

val client = NostrClient(websocketBuilder = ktorBuilder)
client.connect()

// Open a relay subscription that pumps every arriving event into the store.
client.subscribe(
    subId = "home",
    filters = mapOf(
        "wss://relay.damus.io".normalizeRelayUrl() to listOf(
            Filter(kinds = listOf(1), authors = followedAuthors, limit = 500),
        ),
    ),
    listener = object : SubscriptionListener {
        override fun onEvent(event: Event, isLive: Boolean, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
            applicationScope.launch { observable.insert(event) }
        }
    },
)

// Periodic NIP-40 sweep — projections drop expired events when this fires.
applicationScope.launch {
    while (isActive) { delay(15.minutes); observable.deleteExpiredEvents() }
}
```

`observable.insert(event)` is idempotent under NIP-01 supersession (older replaceables / addressables are rejected by the inner store) and validates expiration / vanish tombstones, so it's safe to fire-and-forget for every relay arrival.

`InterningEventStore` keeps one `Event` instance alive per id (weakly, via `EventInterner.Default`), so events that re-arrive from multiple relays — or get re-read by query — share the same object reference as long as some projection holds them.

## Building a reactive feed UI

A feed screen reads from the store via `ObservableEventStore.project()`, which returns a cold `Flow<ProjectionState<T>>`. Wrap it with `stateIn(...)` in a ViewModel and collect from Compose:

```kotlin
class HomeFeedViewModel(
    observable: ObservableEventStore,
    followedAuthors: List<HexKey>,
) : ViewModel() {
    val state: StateFlow<ProjectionState<TextNoteEvent>> =
        observable
            .project<TextNoteEvent>(Filter(kinds = listOf(1), authors = followedAuthors, limit = 200))
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
- `s.items` re-emits only when membership changes (insert, NIP-09 deletion, NIP-62 vanish, NIP-40 expiration sweep, manual `delete(filter)`).
- `handle` re-emits only when its specific event mutates in place (e.g. a new version of a kind-30023 long-form post supersedes the previous one).

## Publishing from the UI

```kotlin
val signer = NostrSignerInternal(KeyPair())
val signed = signer.sign(TextNoteEvent.build("hello nostr", createdAt = TimeUtils.now()))
observable.insert(signed)             // hits the bus → all open projections see it
client.send(signed, relays = ...)     // also publish to relays
```

The projection runs NIP-01 supersession, NIP-09 author checks, NIP-62 vanish scoping, and NIP-40 expiration filtering automatically. UI code never needs to know any of those rules.

## Where to read more

- [`CLIENT.md`](CLIENT.md) — building a relay client with `NostrClient` and Ktor.
- [`RELAY.md`](RELAY.md) — running a relay server with Quartz.
- [SQLite event store README](src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/README.md) — query planner, indexing strategies, NIP-09/40/62 enforcement details.
- KDoc on `EventStoreProjection`, `ObservableEventStore`, `EventInterner` — package-level reference for the projection layer.
