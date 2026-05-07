# Live broadcast: indexed filter matching for fanout

## Problem

Every accepted EVENT runs through `LiveEventStore.newEventStream`
(`quartz/nip01Core/relay/server/LiveEventStore.kt:43`) — a
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

Two specific cost shapes:

1. **Filters that almost never match.** Most subscriptions are scoped
   to a small author list. Today every published EVENT walks every
   such subscription to learn that. A `HashMap<HexKey, MutableList<Sub>>`
   keyed by author would cut this to O(1) average for the dominant case.
2. **Pseudo-broadcast filters** (`{kinds: [1]}` with no other
   constraint) match almost everything. There's no avoiding the
   per-subscriber notification, but at least the index lookup is
   cheap.

`LoadBenchmark.fanoutLatency` already measures this — current
results are not yet noted in tree, but back-of-envelope says fanout
becomes the dominant cost above ~2k subscribers.

## Sketch

A new `LiveBroadcastIndex` inside `LiveEventStore`:

```kotlin
private val byAuthor = ConcurrentHashMap<HexKey, MutableSet<Subscription>>()
private val byKind   = ConcurrentHashMap<Int, MutableSet<Subscription>>()
private val byTag    = ConcurrentHashMap<TagKey, MutableSet<Subscription>>()
private val unindexed = CopyOnWriteArraySet<Subscription>()  // subs with no
                                                             // narrowing field
```

Each `RelaySession.handleReq` registers its `Subscription` (a tuple of
filters + the existing `EventMessage` send callback) into whichever
buckets each filter narrows on. A filter with `kinds=[1] and
authors=[a,b]` registers into `byKind[1]` AND `byAuthor[a]`,
`byAuthor[b]` — broadcast unions the resulting candidate sets.

On EVENT arrival:

1. Build the candidate set: union of `byAuthor[event.pubkey]`,
   `byKind[event.kind]`, every `byTag[(letter, value)]` for the
   event's single-letter tags, plus `unindexed`.
2. Run the existing `Filter.match` on each candidate to handle
   negative constraints (`since`, `until`, `limit` already-reached,
   composite predicates).
3. Send.

Expected: **>10× speedup** on fanout for realistic subscriptions.
Worst case (all filters in `unindexed`) degrades to current behaviour.

## Where it lives

`quartz/nip01Core/relay/server/LiveBroadcastIndex.kt` — protocol-level,
reusable by any relay embed. `RelaySession.handleReq` registers/
unregisters; `LiveEventStore.insert` calls
`index.candidatesFor(event)`.

## How to verify

Add `geode.perf.LoadBenchmark.fanoutScaling`:

- N connections, each subscribes to `{authors: [pk_i], kinds: [1]}`.
- Publish 10k EVENTs from a producer connection; each event matches
  exactly one subscriber.
- Measure end-to-end latency p50/p99 for N ∈ {100, 1000, 5000}.

Without the index, p99 grows roughly linearly with N. With the
index, p99 should be flat up to a much higher N.

## Risks

- **Subscription churn**: re-subscribing on every page (the way some
  client features work) means many index insert/remove operations.
  `ConcurrentHashMap` value-set operations need to be lock-free or
  finely locked; benchmark this path explicitly.
- **Tag explosion**: an EVENT with many `e`/`p` tags hits many tag
  buckets. Cap candidate-set union work or short-circuit when the
  union saturates.
- **Memory**: the index is a per-bucket set of subscription handles.
  At 5k subs × average 3 narrowing fields, ~15k entries — negligible.
- **Correctness fence**: the index must see new subscriptions before
  the next EVENT broadcast. Today `RelaySession.handleReq` writes its
  `Job` into a `LargeCache` then launches the collector. Order of
  operations needs to be revisited so the index is updated atomically
  with the collector being ready.
