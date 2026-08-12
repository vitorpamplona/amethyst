# PoolRequests subscription-state lock: what a suspending Mutex would cost

**Date:** 2026-08-03
**Status:** analysis + measurements; recommendation is NOT to use `Mutex`
**Harness:** `quartz/src/jvmTest/.../prodbench/LockDesignComparisonBenchmark.kt`
(`./gradlew :quartz:jvmTest --tests "*.LockDesignComparisonBenchmark"`)
**Context:** follows the Pixel 8 ANR (`anr_2026-08-03-12-55-26-256`) that replaced
`RequestSubscriptionState`'s busy-wait with a parking `PlatformLock`.

## The question

The parking lock removed the CPU burn, but it still **blocks** rather than suspends.
Relay consumers run as coroutines on `Dispatchers.IO` (limitedParallelism 64) and
production has ~191 live relays, so a blocked waiter occupies one of 64 dispatcher
threads. kotlinx's scheduler treats IO tasks as blocking and grows the pool when they
block — which is why the on-device fix moved CPU but left thread count flat (+7%).

Would `kotlinx.coroutines.sync.Mutex` (a waiter *suspends*, freeing its thread) be
better?

## What Mutex actually costs

The lock sits at the bottom of a call chain that is **entirely non-suspend**:

```
OkHttpWebSocket:  scope.launch { for (m in incomingMessages) out.onMessage(m) }   <- coroutine
  WebSocketListener.onMessage                     (non-suspend)
    BasicRelayClient.MyWebsocketListener.onMessage
      RelayPool.onIncomingMessage
        NostrClient.onIncomingMessage             (RelayConnectionListener)
          PoolRequests.onIncomingMessage
            RequestSubscriptionState.withLock     <- the lock
              SubscriptionListener.onEvent        (non-suspend, fans out to the app)
```

`Mutex.lock()` is `suspend`, so every frame above it must become `suspend`. Measured
blast radius:

| interface / API | count |
|---|---|
| `override fun onEvent(` | 59 |
| `override fun onEose(` | 52 |
| `override fun onIncomingMessage(` | 35 |
| `override fun onCannotConnect(` | 32 |
| `override fun onClosed(` | 25 |
| `override fun onDisconnected(` | 17 |
| `override fun onConnected(` | 16 |
| `override fun onSent(` | 14 |
| `override fun onConnecting(` | 11 |
| others (`onSubscriptionStarted`, …) | 1 |
| **total overrides to convert** | **262** |
| `.subscribe(` / `.unsubscribe(` call sites | **110** |

Worse than the count: the *entry points* are not all coroutines. `INostrClient` is
non-suspend by design — `subscribe`, `unsubscribe`, `publish`, `syncFilters`,
`connect` — and is called from ViewModels, filter assemblers and Compose effects.
`PoolRequests.addOrUpdate` / `remove` / `sendToRelayIfChanged` reach the lock from
those paths. Making them suspend pushes coroutine scoping into every call site that
today just calls `subscribe(...)` synchronously.

## The cheaper alternative: stripe the lock per relay

**Enabling invariant (verified by reading all 11 `withLock` bodies):** *every*
critical section in `PoolRequests` is scoped to exactly one relay. Each one takes
`url` / `relay` / `relay.url` as its key:

| line | body | key |
|---|---|---|
| 204 | `state.connecting(url)` | url |
| 218 | `state.onOpenReq(relay, cmd.filters)` | relay |
| 228 | `state.onSubscriptionClosed(relay)` | relay |
| 249 | `onNewEvent` / `currentState` / `lastKnownFilterStates` | relay.url |
| 267 | `onEose` + `decideCommandLocked` | relay.url |
| 296 | `onClosed` + `recordRefusalIfStructural` + `decideCommandLocked` | relay.url |
| 325 | `state.disconnected(url)` | url |
| 354 | `isStructurallyRefused` + `onOpenReq` | relay |
| 382 | `lastKnownFilterStates(url)` | url |
| 403 | `decideCommandLocked(state, subId, relay)` | relay |

Every field in `RequestSubscriptionState` is a `Map<T, …>` keyed by relay
(`subStates`, `filterStates`, `lastKnownFilterStates`, `refusedFilters`,
`refusalCounts`). The single cross-relay accessor, `currentFilters()` (no-arg,
returns the whole map), has **zero usages** — dead code, delete it.

So the lock is only shared across relays as an artifact of `mutableMapOf` not being
thread-safe. One lock per `(subId, relay)` is semantically equivalent and drops
contention from ~191 threads to ~1–2 (that relay's consumer, plus the occasional app
thread in `sendToRelayIfChanged`).

Blast radius: `RequestSubscriptionState` + `PoolRequests` only. Used by 4 production
files (`PoolRequests`, `RelayActiveRequestStates`, `RelayReqRefusals`) and 2 tests.
**No public API change.**

## Measurements

191 relay coroutines on a 64-thread limited-parallelism dispatcher, 3s windows. Each
iteration yields, modelling the real per-message suspension point of
`for (message in incomingMessages)` — without it the tight loops monopolise the
dispatcher and the harness measures itself, not the lock.

`bystander` = a task that never touches the lock, on the same dispatcher. Its latency
answers "is the lock stealing dispatcher threads from unrelated work?"

| subs | design | ops/s | bystander p50 | p99 |
|---|---|---|---|---|
| 1 | PER_SUB_BLOCKING (today) | 548,608 | 196.5µs | 770.9µs |
| 1 | PER_SUB_MUTEX | 190,672 | **1.9µs** | 30.4µs |
| 1 | **STRIPED** | **1,543,037** | 88.8µs | 237.6µs |
| 4 | PER_SUB_BLOCKING | 833,090 | 136.1µs | 507.6µs |
| 4 | PER_SUB_MUTEX | 523,440 | **3.1µs** | 26.0µs |
| 4 | **STRIPED** | **1,499,757** | 92.3µs | 337.5µs |
| 16 | PER_SUB_BLOCKING | 1,198,581 | 94.3µs | 444.3µs |
| 16 | PER_SUB_MUTEX | 749,487 | **5.0µs** | 16.5µs |
| 16 | **STRIPED** | **1,789,509** | 85.7µs | 219.8µs |

Reading:

- **STRIPED gives the most throughput** — 2.8× / 1.8× / 1.5× over today — and roughly
  halves bystander p50. It removes the contention rather than tolerating it.
- **MUTEX is the slowest of the three** (0.35× / 0.63× / 0.63× of today): per-acquisition
  suspend/resume is not free at these rates.
- **MUTEX does win bystander latency decisively** (1.9–5.0µs vs 85–196µs, and it collected
  626k vs 24k samples). But read that honestly — part of the win is that its coroutines
  spend their time *suspended waiting for the mutex instead of doing work*. Better
  thread-yielding is partly a symptom of lower throughput, not purely a win.

## On-device result (SM-T220, playBenchmark, same account, n=3 per design)

Cold-start burst, 75s window, exact per-thread CPU accounting from `/proc/<tid>/stat`.
Warmups matched (~220-255 established sockets, ~200 relay reader threads each time).

| design | DefaultDispatcher CPU (ms/75s) mean / median / range | GC ms | threads |
|---|---|---|---|
| spin lock (pre-fix) | 136,743 / 117,750 / 112,230–180,250 | 33,777 | 475 |
| parking, one lock per sub | 103,780 / 99,160 / 86,120–130,680 | 30,235 | 510 |
| **striped, per (sub, relay)** | **88,953 / 82,160 / 72,860–111,840** | **27,687** | 489 |

- **Striped vs spin: −35% mean, −30% median CPU, −18% GC — and the ranges do not
  overlap** (striped max 111,840 < spin min 112,230). That separation is what the
  parking-only change could not show; its range overlapped the baseline heavily.
- **Striped vs parking: −14% mean / −17% median**, ranges still overlap — directional,
  not conclusive at n=3.
- **Thread count is unchanged across all three** (475 / 510 / 489). Expected: the lock
  is blocking, and kotlinx marks IO tasks blocking and grows the pool. Striping reduces
  how *often* threads block, not the fact that they can.
- Not a false win from broken subscriptions: the feed rendered live notes 3-15 min old
  with avatars and reaction counts, on 223 sockets / 200 relay reader threads.

## Recommendation

**Do the striping; do not convert to `Mutex`.**

Striping attacks the cause — the lock is contended only because it is shared across
relays that touch disjoint keys. Once contention is ~0, the blocking-vs-suspending
question is moot: *a lock that is never contended never blocks a thread*. It also
happens to be the fastest option and is contained to two files, versus 262 overrides
and 110 call sites for a design that measures slower.

`Mutex` only becomes the right answer if a future critical section must genuinely span
relays (or do I/O), which none does today.

## The lock must not live inside a removable entry

The obvious shape — `ConcurrentMap<T, PerRelayState>` where `PerRelayState` owns both
the fields *and* its lock — is **wrong as soon as entries can be removed**.
`connecting()` / `disconnected()` genuinely mean "forget this relay's wire state", so
they want removal, and then:

```
T1: getOrPut(R) -> stateA ; stateA.lock.lock()      // in a critical section
T2: disconnected(R) -> remove(R)                     // stateA is now orphaned
T3: getOrPut(R) -> stateB (NEW lock) ; stateB.lock.lock()   // acquires immediately
    -> T1 and T3 are both "in" relay R's critical section, excluding nothing.
```

Two ways out:

- **(A) never remove; null the fields.** Lock identity is stable, but entries
  accumulate for every relay a sub has *ever* seen. Bounded but monotonic — and slow
  monotonic growth in a long-lived process is precisely the class of bug this whole
  investigation was about.
- **(B) stable stripe locks + removable state.** A fixed `Array(N) { PlatformLock() }`
  indexed by `relay.hashCode()`, never mutated, so lock identity can't change; the
  `ConcurrentMap<T, RelayState>` entries are then free to be added and removed with
  exact `connecting`/`disconnected` semantics and no growth.

**(B) is the recommendation.** With N = 32 and ~191 relays, ~6 relays share a stripe —
still a ~32x contention reduction versus today's single lock per sub, with none of the
lock-lifetime hazard. `ConcurrentMap.remove` (added 2026-08-03, with tests in
`ConcurrentCollectionsTest`) is what makes (B) possible.

## Implementation sketch

1. Delete the dead `currentFilters()` (no-arg).
2. In `RequestSubscriptionState`, replace the five relay-keyed maps with a single
   `ConcurrentMap<T, RelayState>` holding the five fields — **no lock inside**.
3. Add a fixed `private val stripes = Array(32) { PlatformLock() }` and
   `withLock(reference)` = `stripes[reference.hashCode().absoluteValue % 32].withLock { }`.
   The array is never mutated, so lock identity is stable for the object's life.
4. `connecting()` / `disconnected()` become `map.remove(reference)` — exact current
   semantics, no residue.
5. Update the 11 `withLock` call sites in `PoolRequests` to pass the relay.
   `decideCommandLocked`'s "MUST hold the lock" contract becomes "MUST hold *that
   relay's stripe*" — tighten the kdoc.
6. Extend `PoolRequestsRefusalTest` with concurrent multi-relay access on one subId,
   and add a striped variant to `LockDesignComparisonBenchmark` to confirm the
   measured win survives stripe collisions.

## Risks

- **Stripe collisions serialize unrelated relays.** With 32 stripes and 191 relays this
  is ~6-way sharing; it is a contention *reduction*, not elimination. If a future
  profile shows it mattering, raise N — it is a one-line change with no semantic effect.
- **Two relays on one stripe must never be locked simultaneously by one thread** — that
  would self-deadlock (`PlatformLock` is reentrant on JVM/Apple, so same-thread
  re-entry is survivable, but the invariant should be stated). `PoolRequests` already
  locks one relay at a time.
- The state machine's atomicity comments are load-bearing; the per-relay scoping must
  be re-verified against any new `withLock` body added between now and the change.
- Striping is NOT a fix for a critical section that does I/O or spans relays. If one is
  ever added, this analysis must be redone.
