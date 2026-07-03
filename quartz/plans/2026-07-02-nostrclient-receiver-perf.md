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

## Bulk download: N-million events from one relay (download + parse only)

**Harness:** `quartz/src/jvmTest/.../relay/prodbench/BulkDownloadBenchmark.kt`
(gated: `./gradlew :quartz:jvmTest --tests "*.BulkDownloadBenchmark" -PprodRelayBench=1`)

### Local ceilings (geode on localhost TCP, 100k seeded events, 4 cores)

| strategy | events/s | wall for 100k |
|---|---|---|
| quartz, 1 conn, one giant REQ | 476 | 210s (and 2 events silently missing) |
| quartz, 4 conns time-sharded, giant REQs | 2,001 | 50s |
| quartz, 1 conn, paged 1000/page | 12,480 | 8.0s |
| **quartz, 4 conns time-sharded + paged** | **24,210** | **4.1s** |
| raw socket (no parse), one giant REQ | ~676 | timed out at 120s (81k/100k) |

- **Giant single REQs are a trap.** The raw (no-parse) variant proves it's
  server-side: geode streams a 100k-event response at ~700 events/s
  (per-frame cost in the session pump / query streaming path — needs its own
  investigation, `WebSocketSessionPump`), and the quartz run came back 2
  events short. Public relays are worse: they clamp limits and may drop
  frames or the connection under output backpressure. Paged cursors through
  the very same server ran 26× faster.
- **Sharding the `created_at` range across connections stacks with paging:**
  4 sharded + paged connections ≈ 2× one paged connection locally (24k/s),
  and each connection gets its own receiver coroutine, so parse parallelizes
  for free.

### Per-frame strategies, offline (50k frames, ~580B each)

| strategy | events/s | µs/frame |
|---|---|---|
| full `fromJsonToMessage`, 1 thread | 276k | 3.6 |
| full parse, 4 threads | 657k | 1.5 (aggregate) |
| id-scan only (archive raw, parse lazily) | 2.96M | 0.34 |

Parse of small events is ~3.6µs; the earlier production capture (mixed sizes,
damus 8.7KB events) measured 32µs. Either way **parse is not the bottleneck
for bulk download**: one core parses 10M small events in ~36s, and a 4-core
fan-out does it in ~15s. The wire and the relay's page cadence dominate.

### Production test case: kinds=[30382] on nip85.nosfabrica.com (cap 20k)

| strategy | wall | events/s | notes |
|---|---|---|---|
| paged download (until-cursor) | 5.4s | 3,711 | 41 pages ≈ 500/page (relay clamp) |
| NIP-77 negentropy sync | 12.7s | 1,575 | full set = 31,241 ids, 9 reconcile windows |

Negentropy correctly enumerated the whole 31,241-id set (splitting 9 windows
around the relay's `max_sync_events` cap) and streamed id-batch REQs — but
for a **cold** download it was 2.4× slower than plain paging: the reconcile
rounds and by-id lookups cost more than sequential pages when you need
*everything anyway*. Negentropy's win is **incremental re-sync**: once the
10M events are local, the next sync transfers only fingerprints + the diff
instead of re-paging the world. (kind 30382 events carry empty `content` —
all data in tags — so the MB/s column reads 0.)

### Per-connection wall: relay pacing, not client parse (2026-07-03)

A user report proposed "parallel parse + strictly-ordered dispatch" on the
theory that the per-connection serial parse caps a single connection at
~4k events/s (fetch-by-id, 12 idle cores, no gain past ~4 concurrent REQs,
throughput scaling only with connections). The discriminating test
(`BulkDownloadBenchmark.perConnectionWall`) pages the same production query
on one connection twice — once through a raw socket that does NO JSON parse
(only an `indexOf` scan for `created_at`), once through the full quartz
stack:

| | events/s (nip85.nosfabrica.com, kinds 30382, 20k cap) |
|---|---|
| raw socket, no parse | 3,382 |
| full quartz stack, parse + dispatch | 3,754 |

Identical within noise. **The ~3.5–4k/s single-connection ceiling is the
relay's per-connection page/response cadence, not client CPU** — at 465B
events the parser is ~1% busy at this rate (single-thread parse ceiling:
276k/s small events, 31k/s for the large-frame production mix). The same
relay served our negentropy fetch-by-id at only 1.6k/s with 8 concurrent
REQs and an idle client — also server-side. This matches every symptom in
the report: per-connection pacing explains "more REQs on one connection
don't help" and "throughput scales with connections" just as well as a
client-side serial stage would, and "CPU idle" is what a network wall looks
like.

Implications for the proposed parallel-parse pipeline:

- It will NOT lift the reported 4k/s ceiling on relays with this pacing; the
  proven lever is **more connections** (created_at- or id-range sharding).
- It IS still worth having for the cases where the consumer genuinely
  saturates: large-frame relays (32µs/frame mix) on phones (5–10× slower
  cores → ~3–6k/s parse ceiling) being streamed by fast relays, and any
  setup where verify/consume stays inline on the consumer.
- The proposal's ordering constraints are correct and non-negotiable:
  NEG-MSG/NEG-ERR handshake order, EOSE-only-after-its-EVENTs,
  per-subscription EVENT order, OK/CLOSED/NOTICE/AUTH stream order. A
  sequence-numbered reorder buffer after a parallel parse pool satisfies all
  four (dispatch order = receive order).
- The bounded-pipeline half of the proposal is worth doing regardless — the
  UNLIMITED per-connection channel is the unbounded-memory risk under any
  slow consumer (see below), and bounding it converts overload into TCP
  backpressure. One caveat: blocking OkHttp's reader thread also delays its
  PING/PONG handling, so sustained backpressure can trip relay-side ping
  timeouts — the bound should be sized generously.

### Parallel fetch-by-id matrix — full corpus, no cap (2026-07-03)

**Harness:** `quartz/src/jvmTest/.../relay/prodbench/ByIdFetchBenchmark.kt`.
Enumerates every kind-30382 id on nip85.nosfabrica.com (33,000 that day), then
each cell re-downloads the FULL corpus by 250-id REQ batches from a shared
queue. Connections are pre-established before timing; every cell completed
with zero missing ids and zero timed-out batches.

| cell | events/s |
|---|---|
| 1 conn × 1 REQ | 1,970 |
| 1 conn × 2 REQs | 3,423 |
| 1 conn × 4 REQs | 8,026 |
| 1 conn × 8 REQs | 17,214 |
| 1 conn × 16 REQs | **30,735** |
| 2 conns × 4 REQs | 15,557 |
| 4 conns × 4 REQs | 27,251 |
| 8 conns × 4 REQs | **40,402** |

This corrects both the user report AND this doc's own earlier "relay
per-connection pacing" phrasing:

1. **No wall at ~4 concurrent REQs** — one connection scales near-linearly to
   16 in-flight REQs (1,970 → 30,735 events/s). The relay is happy to serve
   30k+/s down a single socket when the client keeps requests in flight.
2. **Connections and per-connection REQs are interchangeable**: 1×16 ≈ 4×4.
   The real variable is **total in-flight REQs** (Little's law: throughput ≈
   in-flight ÷ per-REQ latency; a 250-id batch serves in ~130ms here).
   Diminishing returns start around 32 in-flight (~40k/s) — likely relay
   query capacity.
3. **Every slow configuration measured so far is a serialization problem,
   not a bandwidth/CPU one**: until-cursor paging is 1-in-flight by
   construction (~2–3.7k/s ≈ the 1×1 cell); `negentropySync` measured
   1.6k/s with `maxConcurrentReqs = 8` because its download workers starve —
   the reconcile rounds pace id production (bounded `idBatches` buffer of
   `workerCount` batches) and overflow windows recurse **sequentially**
   (`syncWindow` lo-half then hi-half).
4. The reported "stops helping past ~4 REQs" is consistent with
   `negentropySync`'s internal throttling (or a relay-side subscription cap,
   or a slow per-event sink) — not with independent by-id REQs on this relay.
5. Client parse at 30.7k/s through ONE connection's consumer coroutine was
   ~11% of a core (3.6µs × 30.7k) on this machine — still not the wall. On a
   phone (5–10× slower) 15–30k/s IS where the single consumer saturates, so
   the parallel-parse proposal becomes relevant on mobile at exactly the
   rates this fan-out unlocks.

**Concrete quartz improvements this implies** (in `negentropySync`):
raise `maxConcurrentReqs` (16 measured safe here), deepen the `idBatches`
buffer so downloads don't starve on reconcile cadence, and reconcile
overflow-split windows concurrently instead of recursing sequentially.
Together these should move it from 1.6k/s toward the ~30k/s the same
connection demonstrably sustains. At 40k/s, 10M events by id is ~4 minutes
(plus id enumeration).

### Answer for "10M events from one relay, fastest"

At nosfabrica's measured page cadence, one connection ≈ 3.7k events/s → 10M
in ~45 min. To improve, in order:

1. **Page with until-cursors — never one giant REQ** (silent drops, server
   slow paths, relay clamps).
2. **Shard the `created_at` range across K connections** to the same relay
   (each shard pages independently; no cursor dependency between shards).
   Local: 2× at K=4; WAN, where RTT dominates page turnaround, closer to
   linear until the relay rate-limits per-IP.
3. **Don't optimize parse first** — it's 2–5% of the budget. If the goal is
   archival, id-scan + store raw frames (0.34µs/frame) and parse lazily
   in parallel later.
4. **Use negentropy for the second sync onward**, not the first.
5. **Memory:** the per-connection channel is UNLIMITED — at 10M events a
   sink slower than the socket accumulates heap without bound. A bulk
   downloader should bound the channel (blocking the OkHttp reader thread is
   fine — that's TCP backpressure doing its job) and stream events to disk,
   never hold the set.

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
