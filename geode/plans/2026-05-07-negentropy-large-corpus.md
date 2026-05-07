# NIP-77 negentropy at scale: snapshot memory + chunked replay

## Problem

`RelaySession` delegates NEG-OPEN to `NegSessionRegistry.open`
(`quartz/nip01Core/relay/server/NegSessionRegistry.kt`), which calls
`store.snapshotQuery(filters)` and feeds the **entire** result list
into `NegentropyServerSession`. For a relay holding 5 M events that
match a broad NEG-OPEN filter (`{kinds: [1, 7]}`), this is 5 M
`Event` objects materialised in memory before the first NEG-MSG goes
out.

The negentropy library itself is fine — it pivots into a sealed
`StorageVector` (id + createdAt only, ~40 bytes/entry). But the
`store.query<Event>(f)` step that produces the input materialises full
`Event` objects with content, tags, sig — call it ~1 KB/event. 5 M ×
1 KB = 5 GB transient pressure per concurrent NEG-OPEN.

Two operator-visible symptoms:

1. NEG-OPEN with a broad filter spikes JVM heap; under load, GC pause
   stalls every other handler on the same process.
2. NEG-OPEN latency before the first NEG-MSG response is O(N) — for
   large stores the client waits seconds for what should be a
   millisecond round-trip.

## Sketch

### A — id-and-time-only snapshot path

Negentropy only needs `(createdAt, id)` pairs. Add a streaming
`IEventStore.queryIdAndTime(filter)` that returns
`Sequence<Pair<Long, ByteArray>>` (or a `Flow` of small chunks) —
no content/tags/sig, no Event allocation. SQLite path is a SELECT
on `event_headers` (the `created_at`, `id` columns are already
indexed for query plans).

```kotlin
suspend fun snapshotIdsForNegentropy(filter: Filter): IdTimeStream
```

`NegentropyServerSession` is rewritten to take that stream and feed
it directly into the `StorageVector`. Memory drops from O(N × 1 KB)
to O(N × 40 B) — a 25× reduction; for 5 M events, ~200 MB instead
of 5 GB.

### B — bounded-window subscriptions

Most NEG-OPENs from real Nostr clients want the last 30 days, not
"everything." If the client doesn't supply `since`, the server can
default to a configurable horizon (e.g. 90 days) and surface this in
the NIP-11 `limitation.negentropy_max_lookback_seconds` field.
Operators can lift the cap; clients reading the doc know the bound.

This is a NIP-spec-adjacent question more than a code change — needs
a comment on whether the spec allows it. nostr-rs-relay does this
already.

### C — frame-size cap on NEG-MSG

`NegentropyServerSession` is constructed with `frameSizeLimit = 0`
(no limit). At very large reconciliations the message can grow large.
Set a default `frameSizeLimit = 64 * 1024` (matching the typical WS
frame budget) so NEG-MSGs don't blow past `[limits].max_ws_frame_bytes`.

The library already supports this — pure config change in
`NegSessionRegistry.open`.

### D — concurrent NEG-OPEN cap

A NEG-OPEN holds session state until NEG-CLOSE (or connection close).
Today nothing caps the number of concurrent open negentropy sessions
per connection. A misbehaving (or hostile) client could open thousands
and pin RAM. Add `MAX_NEG_SESSIONS_PER_CONNECTION = 16`, send NEG-ERR
on overflow.

## How to verify

Add to `geode.perf.LoadBenchmark`:

- `negentropyOpenLatencyLargeCorpus` — preload 1 M events (use
  fixtures), measure NEG-OPEN → first NEG-MSG latency. Target <100 ms.
- `negentropyMemoryPressure` — open 10 concurrent NEG-OPENs on the
  same large corpus; measure RSS delta, target <500 MB.

## Risks

- **`Sequence`/`Flow` over SQLite cursor**: holding a cursor open
  across the full sync is fragile if the client stalls. Materialise
  to a smaller in-memory list (just (id, createdAt)) once, reuse for
  the lifetime of the session. Memory bound is the same.
- **Defaulting `since` is a behaviour change**: existing clients that
  expect "everything" silently get a bounded window. Either (a) make
  it opt-in via `RelayConfig.NegentropySection.default_lookback_seconds
  = null`, (b) advertise the cap in NIP-11 so well-behaved clients
  read it.
- **Frame-size cap can break older clients**: the NIP-77 reference
  implementation (kmp-negentropy) handles this gracefully — multi-frame
  reconciliation is in spec — but field-test against a known-working
  client (e.g. nstart, primal-cache) before flipping the default.
