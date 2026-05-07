# Live broadcast: indexed filter matching for fanout

> **Status (2026-05-07):** Phase 1 (relay server) and Phase 2 (Amethyst client
> `LocalCache.observables`) are implemented. Phase 3 (per-projection dispatch
> from `ObservableEventStore.changes`) is left as future work — see "What's
> next" below.

## Problem

Every accepted EVENT runs through `LiveEventStore.newEventStream`
(`quartz/nip01Core/relay/server/LiveEventStore.kt`) — a
`MutableSharedFlow<Event>` that every active subscription collects.
Each subscriber's collector then calls:

```kotlin
if (filters.any { it.match(newEvent) }) onEach(newEvent)
```

That's **O(N_subscribers × N_filters_per_sub)** per published event.
With 5k connections × ~3 filters average that's 15k Filter.match
calls per EVENT — and each `Filter.match` itself walks `kinds`,
`authors`, tag prefixes, since/until, etc. At 2k EPS ingest that's
~30M comparisons/sec.

The same shape recurs in two more places:

1. **`LocalCache.observables`** in `amethyst/.../LocalCache.kt` —
   a `ConcurrentHashMap<Observable, Observable>` of feed observers.
   `refreshNewNoteObservers` iterates every observer for every
   accepted event; each observer's `new()` runs `filter.match`.
2. **`EventStoreProjection`** under `quartz/.../cache/projection/` —
   each projection collects every `StoreChange.Insert` from
   `ObservableEventStore.changes` and runs its own filter list.

All three follow the pattern "many filter-bearing observers, one
incoming event — find which observers match". Today that's a per-event
walk over N observers; with an inverted index it becomes a few hash
lookups followed by `Filter.match` only on the (small) candidate set.

## Solution

### `FilterIndex<S>`

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/filters/FilterIndex.kt`
is a generic, KMP-friendly inverted index parameterised by the
subscriber type. It lives next to `Filter.kt`, not under `relay/server/`,
because it isn't relay-specific.

API:

```kotlin
class FilterIndex<S : Any> {
    fun register(filter: Filter, subscriber: S)
    fun register(filters: List<Filter>, subscriber: S)
    fun registerUnindexed(subscriber: S)            // predicate-only callers
    fun unregister(subscriber: S)
    fun candidatesFor(event: Event): Set<S>          // index lookup
    fun forEach(action: (S) -> Unit)                 // full iteration (delete paths)
    fun size(): Int
    fun isEmpty(): Boolean
}
```

State is held in a single `AtomicReference<State<S>>` (BanStore-style)
so reads in the hot path are wait-free. Writes copy-on-write; that's
fine because writes are subscription-rate (rare) while reads are
event-rate (frequent).

Indexing strategy: each filter contributes entries to **one**
dimension — the most selective indexable field. Picking one dimension
instead of all of them avoids over-counting subscribers in
`candidatesFor` and minimises bucket churn:

1. `ids` (most selective; an id matches one event).
2. `authors`.
3. The first single-letter tag in `tags` (then `tagsAll`).
4. `kinds`.
5. None of the above → registered into the unindexed pool.

Multi-filter registrations OR the per-filter selections together,
which mirrors `filters.any { it.match(...) }`. Negative constraints
(`since` / `until` / `tagsAll` / saturated `limit`) stay in
`Filter.match` — the index produces a super-set, callers post-filter.

### Phase 1: `LiveEventStore`

`LiveEventStore.kt` no longer uses a `MutableSharedFlow`. Instead:

- One `FilterIndex<LiveSubscription>` shared across all REQs.
- `insert()` calls `index.candidatesFor(event)` then runs
  `Filter.match` on each candidate. Synchronous delivery —
  callers (the relay's `RelaySession`) keep the `deliver` callback
  cheap (queue-to-outbound).
- `query()` registers the subscription into the index *before* the
  historical replay starts (closes the same race the previous
  `onSubscription` handoff closed), runs the replay, signals EOSE,
  then `awaitCancellation()`. Live events arrive via index dispatch
  during the suspend; `finally { index.unregister(sub) }` cleans up.
- Dedupe set during the historical phase is held in an
  `AtomicReference<HashSet<String>?>` so the live-dispatch coroutine
  sees the post-EOSE handoff promptly.

### Phase 2: `LocalCache.observables`

`amethyst/.../LocalCache.kt` swapped its
`ConcurrentHashMap<Observable, Observable>` for a
`FilterIndex<Observable>`. `observeNotes` / `observeEvents` /
`observeNewEvents(filter: Filter)` now `register(filter, observer)`;
the predicate-only `observeNewEvents(predicate)` overload uses
`registerUnindexed` because the index can't introspect an opaque
predicate. Dispatch:

- `refreshNewNoteObservers` iterates `observables.candidatesFor(event)`
  instead of every observer.
- `refreshDeletedNoteObservers` still uses `observables.forEach { ... }`
  — the index doesn't help on the delete path because every observer
  might hold the deleted note in its result set, and there's no
  event-shape to consult.

### How to verify

`geode.perf.LoadBenchmark.fanoutScaling` (to be added):

- N connections, each subscribes to `{authors: [pk_i], kinds: [1]}`.
- Publish 10k EVENTs from a producer connection; each event matches
  exactly one subscriber.
- Measure end-to-end latency p50/p99 for N ∈ {100, 1000, 5000}.

Without the index, p99 grows roughly linearly with N. With the
index, p99 should be flat up to a much higher N.

For the LocalCache side, a similar benchmark would publish events
matching one of M observers and measure dispatch cost as M grows.

## Risks

- **Subscription churn**: re-subscribing on every page (the way some
  client features work) means many index insert/remove operations.
  COW on a single `AtomicReference` makes each write a full inner-map
  copy; benchmark this path on a busy account to confirm the constants
  stay reasonable.
- **Tag explosion**: an EVENT with many `e`/`p` tags hits many tag
  buckets. The single-dimension-per-filter selection caps how many
  buckets contribute candidates per event — registering on the
  *most* selective dimension means tag-keyed filters typically pick
  one specific tag value, so an event's tag walk only finds filters
  registered under that exact `(letter, value)`.
- **Memory**: the index is a per-bucket set of subscription handles.
  At 5k subs × average 1 dimension × a handful of values per filter,
  ~10k–20k entries — negligible.
- **Correctness fence**: `register` happens before historical replay
  on the relay side, and inside the `callbackFlow`'s `register` /
  `awaitClose { unregister }` pair on the client side. Events
  arriving mid-historical are deduped via `seenIds` (relay) or are a
  non-issue because the client `observe*` flow seeds via `init()`
  before any new event can fire.
- **Filters with no narrowing field** (e.g. `{since: X}`) fall into
  the unindexed pool and behave like today — every event reaches
  them. That's the worst case; it's not worse than the pre-index
  baseline.
- **AddressableEvent / replaceable v2 path**: an observer holding v1
  whose filter doesn't match v2 won't be in `candidatesFor(v2)`.
  Today such an observer wouldn't update its membership either
  (`filter.match(v2) == false` short-circuits before the
  re-emit branch). Pre-existing behaviour preserved.

## What's next (Phase 3)

`ObservableEventStore.changes` is still a `SharedFlow<StoreChange>`
that every projection collects. To use the index there, the dispatcher
between `_changes.emit` and the per-projection collectors would consult
a `FilterIndex<EventStoreProjection<*>>`-style index and only deliver
to interested projections. Doable, but the SharedFlow contract is
public; replacing it is a larger refactor than Phase 1/2 and the ROI
is lower (per-projection apply is already small). Treat as a follow-up.
