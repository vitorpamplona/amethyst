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

## Dispatch stage (post-parse, pre-verify) microbenchmark

**Harness:** `quartz/src/jvmTest/.../relay/prodbench/DispatchStageBenchmark.kt`
(offline, ungated: `./gradlew :quartz:jvmTest --tests "*.DispatchStageBenchmark"`)

Measures everything between the JSON parse and where verification would start:
`NostrClient.onIncomingMessage` → `PoolRequests` (spin lock + sub state) →
listeners → id-dedup → handoff to a verify stage. 30k unique synthetic events,
2 overlapping subs per relay, delivered by 1 feeder thread (one busy relay) and
by 4 (four relays bursting into the shared client). JVM 21, 4 cores.

| variant | 1 feeder (deliveries/s) | 4 feeders (aggregate deliveries/s) |
|---|---|---|
| PoolRequests-only | 11.1M | 3.6M |
| full dispatch, no-op sink | 10.2M | 4.3M |
| + dedup (CHM keySet) | 6.8M | 3.5M |
| + dedup + per-event channel handoff | 3.6M | 3.1M |
| + dedup + batched handoff (64) | 6.8M | 2.8M |
| **early dedup + batched handoff** | 7.2M | **11.4M** |

Findings:

1. **Uncontended, the dispatch stage is ~100ns/message** — 300× cheaper than
   parse (32µs) and 750× cheaper than verify (75µs). One relay can never
   saturate it; nothing to fix for the single-relay case.
2. **It scales negatively under concurrency.** Four feeder threads deliver
   *less* aggregate throughput (3.6–4.3M/s) than one thread alone (10–11M/s):
   the `PoolRequests` busy-wait spin lock serializes every EVENT frame from
   every relay and burns the other cores spinning. Isolated `PoolRequests`
   shows the same collapse, so the lock (not listeners or dedup) is the cause.
   At production message rates (~3k msg/s/relay) this is not yet the
   bottleneck, but it wastes cores the verify pool would want, and it is the
   structural ceiling once verify moves off the receiver.
3. **Handoff granularity matters:** a per-event `Channel.send` costs ~180ns
   extra per event (halves single-feeder throughput); a 64-event batch makes
   the handoff essentially free — same conclusion the server's `IngestQueue`
   already embodies.
4. **Early dedup is the biggest lever under multi-relay load:** checking the
   seen-ids set right after parse — before entering the locked dispatch path —
   let duplicate frames (75% of deliveries in the 4-relay setup; production
   showed 14–57% dups) skip the contended section entirely: 2.7–4× the
   aggregate throughput of every other 4-feeder variant. Semantic caveat: a
   short-circuited duplicate no longer bumps that sub's per-relay
   `onNewEvent`/stats counters, so paging/EOSE bookkeeping would need the
   cheap counters kept ahead of the skip.

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
3. **Fix the `PoolRequests` spin lock's negative scaling** — the dispatch
   microbenchmark shows 4 concurrent relay consumers deliver *less* than one
   thread through it. Either replace the busy-wait with `synchronized`/a
   parking lock, or shard the state by subId, and consider the early-dedup
   short-circuit so duplicate frames never enter the locked path at all.
4. Re-run this harness on-device (the same class compiles for Android
   instrumentation with minor changes) before/after any fix; the offline
   ceilings section gives the numbers to compare.
