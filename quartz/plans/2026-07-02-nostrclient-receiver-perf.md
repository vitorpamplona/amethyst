# NostrClient receiver performance — review + production measurements

**Date:** 2026-07-02
**Status:** findings + measurement harness; no production code changed yet
**Harness:** `quartz/src/jvmTest/.../relay/prodbench/ProductionReceiverBenchmark.kt`
(run with `./gradlew :quartz:jvmTest --tests "*.ProductionReceiverBenchmark" -PprodRelayBench=1`)

## Question

Is the NostrClient's single-threaded receiver making us significantly slower?
And how do our filters actually behave against production relays?

## What the receiver actually is (review)

The receive path is single-threaded **per relay connection**, not globally:

```
OkHttp reader thread (per socket)
  └─ trySendBlocking → Channel(UNLIMITED)            [BasicOkHttpWebSocket / app OkHttpWebSocket]
       └─ ONE consumer coroutine per connection (Dispatchers.IO)
            ├─ OptimizedJsonMapper.fromJsonToMessage  [JSON parse]        ~32µs/msg (JVM)
            ├─ PoolRequests.onIncomingMessage         [spin-lock + state]
            │    └─ SubscriptionListener.onEvent      [assemblers, inline]
            └─ NostrClient.listeners.forEach          [EventCollector, loggers…]
                 └─ LocalCache.justConsume(…, wasVerified = false)
                      └─ dedup by id → justVerify()   [Schnorr verify]    ~75µs/event (JVM)
                      └─ note creation, index updates, flow emissions
```

Everything downstream of the channel — parse, subscription state machine, every
listener, signature verification, and LocalCache consumption — runs serially on
that one consumer coroutine. Different relays run on different coroutines
(cross-relay parallelism exists), but they contend on shared state: the
`PoolRequests` spin lock, `LocalCache`'s maps, and (on Android) the same
`Dispatchers.IO` pool as everything else.

Notably, the **relay-server side already solved this** on the write path:
`IngestQueue` batches incoming EVENTs and runs Schnorr verification for a batch
in parallel on `Dispatchers.Default` (`parallelVerify`), precisely because "an
event's `verify()` is CPU-bound and parallelisable". The client receiver has no
equivalent.

## Production measurements (2026-07-02)

Environment: JVM 21, 4-core x86 cloud container (server CPU — phones are
5–10× slower per core), relays `relay.damus.io`, `nos.lol`, `relay.primal.net`,
`nostr.wine`. All scenarios ran after a discarded warmup pass (JIT warm).
`INLINE` = verify on the receiver coroutine (what the app does today via
`CacheClientConnector`); `PARALLEL` = verify offloaded to a
`Dispatchers.Default` pool.

### Filter behavior

| Scenario | received | unique | duplicates | notes |
|---|---|---|---|---|
| kind-1 firehose, limit 500 ×4 relays | 2013 | 1738 | 14% | all relays honored limit; EOSE 0.3–1.0s after REQ |
| notifications (p=busy pubkey, kinds 1,6,7,9735) | 1552 | 986 | 36% | primal returned only 52 (thin index for this query) |
| metadata burst (kinds 0,10002, 300 authors) | 1146 | 491 | 57% | each profile fetched ~2.3× across 4 relays |

- Time from `subscribe()` to REQ-on-the-wire is dominated by connect/TLS
  (~0.7–1.5s cold); local dispatch is negligible.
- Event sizes vary wildly by relay: damus kind-1 averaged **8.7 KB**/event
  (4.4 MB for 500 events) vs ~0.8 KB on nos.lol — parse cost tracks size
  (p90 proc 1.5ms on damus vs 0.18ms on nos.lol).
- Duplicate factor grows with fan-out: the metadata pattern wastes >half the
  received bytes on duplicates. Dedup happens *before* verify (LocalCache
  checks the id first), so duplicates cost parse + dedup lookup, not a verify.

### Receiver queue behavior (the actual question)

Queue delay = how long a frame sat in the per-connection channel before the
consumer picked it up. This is the direct symptom of a saturated receiver.

kind-1 firehose (large events), per relay:

| | INLINE p50 / p90 / max | PARALLEL p50 / p90 / max |
|---|---|---|
| relay.damus.io | 6.8ms / 26.6ms / 77.9ms | 5.4ms / 38.1ms / 41.5ms |
| nos.lol | 4.3ms / 11.2ms / 15.9ms | 1.3ms / 3.8ms / 4.5ms |
| relay.primal.net | 5.4ms / 24.5ms / 28.1ms | 1.2ms / 1.8ms / 7.7ms |
| nostr.wine | 9.4ms / 30.3ms / 34.8ms | 2.4ms / 8.4ms / 20.7ms |

metadata burst (small events, fastest arrival — damus delivered at 3300–8400 msg/s):

| | INLINE p50 / max | PARALLEL p50 / max |
|---|---|---|
| relay.damus.io | 3.0ms / 10.7ms | 0.14ms / 0.48ms |

Consumer busy fraction peaked at **53%** (nostr.wine, INLINE, 3000 msg/s) on
this fast CPU — i.e. a single relay bursting at production speed already
consumes half of one core with inline verification, on hardware much faster
than a phone.

### Single-thread ceilings (offline, captured production frames)

- JSON parse: **31,000 msg/s** (32µs/msg, mixed sizes incl. damus 8.7KB events)
- Schnorr verify: **13,200 events/s** sequential (75µs/event);
  **34,400 events/s** across 4 cores (2.6–3.0× speedup)
- Combined parse+verify ceiling for one receiver coroutine: **~9,000 events/s**
  on this CPU. On a phone core (5–10× slower): **~1,000–2,000 events/s**, and
  that is *before* LocalCache's note creation, index updates and flow emissions,
  which the harness does not model and which run on the same coroutine.

## Answer

1. **The receiver is not globally single-threaded** — it is one consumer per
   relay. Cross-relay parallelism already exists. The per-relay serialization
   is what caps throughput.

2. **For steady-state browsing the receiver is not the bottleneck.** Queue
   delays with today's inline design are single-digit-to-tens of ms; connect
   latency and relay EOSE times (300ms–1.5s) dominate what the user perceives.

3. **For burst ingest (app start, feed switch, big profile sync) it is a real
   cost, and it scales linearly with burst size.** A 5,000-event burst from one
   fast relay serializes ~375ms of parse+verify on this benchmark machine —
   plausibly **2–4s per relay on a mid-range phone**, during which that relay's
   EOSE, OKs and live events all sit behind the backlog. Verification is
   60–70% of that inline cost and is embarrassingly parallel (measured 2.6–3×
   on 4 cores; phones have 8).

4. **Offloading verification cuts queue latency 2–5× and raises the per-relay
   ceiling ~3×** with no protocol or API change — the exact pattern
   `IngestQueue.parallelVerify` already uses on the server side.

## Recommendations (in order of value/risk)

1. **Move Schnorr verification off the receiver coroutine** in the app's
   consume path (`CacheClientConnector` → `LocalCache.justConsume`): a bounded
   verify stage on `Dispatchers.Default` (batch + `async` per batch, or a small
   worker pool), mirroring `IngestQueue`. Ordering constraint: dedup must stay
   before verify (it already is), and consumers of `justConsume`'s return value
   need an async-tolerant path.
2. **Keep parse on the receiver coroutine** (32µs/msg is cheap and keeps
   message ordering per subscription), but consider skipping re-serialization
   work for duplicates (57% of metadata-burst traffic never needs more than an
   id lookup).
3. **Replace the `PoolRequests` spin lock's busy-wait** with a short-critical-
   section `Mutex`/`synchronized` if profiling on-device shows contention —
   with dozens of relay consumers on a phone, spinning burns cores the verify
   pool needs. (Not measurable as a problem in this 4-relay harness.)
4. Re-run this harness on-device (the same class compiles for Android
   instrumentation with minor changes) before/after any fix; the offline
   ceilings section gives the numbers to compare.
