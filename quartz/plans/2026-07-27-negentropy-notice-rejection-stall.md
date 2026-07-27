# NIP-77 client stalls against relays that refuse negentropy via NOTICE

**Status: root-caused + fixed (reproduced live, regression-tested offline).**

## Symptom

`INostrClient.negentropySyncOrFetch` / `negentropyReconcile` hang forever against
some relays that advertise NIP-77 in NIP-11:

- `wss://relay.ditto.pub` → works (events download, call returns).
- `wss://relay.primal.net` (strfry) → stalls: `onEvent` never fires, `downloaded`
  stays 0, the suspend fun never returns and never hits the idle timeout.
- `wss://purplepag.es` → stalls, same shape.

Only an external `withTimeout` wall clock unblocked the caller.

## Reproduction

`quartz/.../prodbench/NegentropyStallRepro.kt` (gated on `NEG_STALL_REPRO=1`)
builds the exact reported client — `NostrClient(BasicOkHttpWebSocket.Builder { okHttpClient })`
— wraps the socket to log every frame both ways, and runs
`negentropySyncOrFetch(relay, Filter(kinds=[0]), localEntries=emptyList())` against
all three relays under a 60 s external wall clock.

Wire trace (the decisive lines):

```
DITTO       -> NEG-OPEN {kinds:[0]}
            <- NEG-ERR  "blocked: query matches too many records (2988225 > 1000000)"   # overflow -> window split
            <- NEG-MSG  <120 KB id frames> ...                                           # reconciles, streams ids
            => 53,811 events delivered in 60 s (working; would finish given time)

PRIMAL      -> NEG-OPEN {kinds:[0]}
            <- NOTICE   "ERROR: bad msg: negentropy disabled"                            # refusal, NO subId
            => 0 events, STALLED (only the 60 s wall clock freed it)

PURPLEPAGES -> NEG-OPEN {kinds:[0]}
            <- CLOSED   "blocked: filters must specify at least one kind"                # rejects the keep-alive REQ
            <- NOTICE   "failed to parse envelope: unknown envelope label"               # doesn't know NEG-OPEN
            => 0 events, STALLED
```

So it is **not** strfry-specific, not a large-corpus reconcile-convergence
problem, and not the fetch stage: the relays never enter reconciliation at all.
They **refuse negentropy** — one has it switched off, the other never implemented
the envelope — and both signal the refusal with a connection-level `NOTICE`
(strfry) / `NOTICE`+`CLOSED` (purplepag.es). NIP-11 advertising NIP-77 is not a
runtime guarantee.

## Root cause (a quartz client bug)

`reconcileStreaming` (in `NostrClientNegentropySyncExt.kt`) installs a
`RelayConnectionListener` that only routes two message types into its driver
channel:

```kotlin
is NegMsgMessage -> if (msg.subId == subId) incoming.trySend(NegFrame.Msg(...))
is NegErrMessage -> if (msg.subId == subId) incoming.trySend(NegFrame.Err(...))
else -> Unit
```

A `NOTICE` carries **no subId** (it is a connection-level message), so it can
never match and falls into `else -> Unit`. The driver sits in
`receiveWithinIdle(clock, idleTimeoutMs)` waiting for a frame that never comes.

Why the idle watchdog didn't save it: the connection-level `IdleClock` was bumped
on **every** message the relay sent. In isolation the relay goes silent after the
`NOTICE`, so the 120 s idle *would* eventually fire (the repro just used a shorter
60 s wall clock) — but in concurrent/real use the same connection keeps chattering
(the rejected keep-alive REQ being re-`CLOSED` on re-sync, other subscriptions'
traffic), and each such frame reset the watchdog, so it never fired. Net effect:
`negentropySync` never throws → `negentropySyncOrFetch` never reaches its paging
fallback → caller hangs.

## Fix

In `reconcileStreaming`'s listener:

1. **Route a terminal `CLOSED` for our NEG subId** into the driver as a failure.
2. **Treat a negentropy-refusal `NOTICE` as terminal**, bound to this session by
   *phase + wording*: only before the first valid `NEG` frame (`sawNegFrame`),
   and only when the text looks like a negentropy/parse refusal
   (`isNegentropyRejectionNotice`: contains `negentropy` / `envelope` /
   `NEG-OPEN` / `NEG-MSG`). Both conditions keep it narrow so an unrelated NOTICE
   on a healthy relay mid-reconcile can never abort an otherwise-progressing sync.
3. **Stop bumping the idle clock on `NOTICE`/`CLOSED`.** The watchdog now advances
   only on real progress — this session's own `NEG` frames and the download REQs'
   `EVENT`/`EOSE` — so error chatter can no longer keep a dead sync alive. This is
   the "make the idle timeout fire on no-download-progress" ask, scoped safely.

The refusal surfaces as `NegFrame.Err` → `isOverflow` is false (no
`too many`/`too large`/`max_sync_events`) → `ReconcileOutcome.Failed` →
`NegentropySyncException(UNAVAILABLE)`. `negentropySync` throws promptly;
`negentropySyncOrFetch` catches it and pages the same filter (both primal and
purplepag.es answer ordinary REQs fine, so paging delivers the events).

## Tests

- `NegentropyRejectionFallbackTest` (offline, deterministic): a scripted fake
  relay answers `NEG-OPEN` with each observed `NOTICE`; asserts `negentropySync`
  throws `UNAVAILABLE` fast and `negentropySyncOrFetch` sets `pagedFallback`.
- `NegentropyStallRepro` (gated live): end-to-end proof against the real relays.

## Not changed / follow-ups

- The keep-alive subscription filter `Filter(ids=[f*64])` is `CLOSED` by relays
  that require a `kinds` (purplepag.es). Harmless now that the reconcile fails
  fast and unsubscribes it, but a keep-alive that every relay accepts would be
  tidier.
- A relay that refuses via an *unrecognized* signal (neither NEG-ERR, nor a
  matching NOTICE, nor CLOSED-for-subId, just silence) still relies on the idle
  watchdog — which now fires correctly because the refusal chatter no longer
  resets it.

## Follow-up audit (same PR)

A read-through of the whole negentropy accessories package surfaced a few more
issues; the actionable ones are fixed here.

- **`isOverflow` was too broad → split-storm (fixed).** It matched a bare
  `"too many"` / `"too large"`, so a *non-shrinking* error — `"too many
  requests"`, `"too many concurrent subscriptions"` — was read as a
  set-too-large overflow. Because such an error doesn't shrink with the window,
  every split re-triggers it and `reconcileWindows` walks toward 1-second leaves,
  queueing up to ~2³¹ `Filter`s (OOM + relay hammering). Tightened to
  result-set-qualified phrases (`too many records`, `too many query results`,
  `result set too large`, `max_sync_events`), so a rate/quota error now fails
  over to paging. Added a `MAX_WINDOWS` (100k) backstop in `reconcileWindows` —
  wording-independent — that bails to paging if a split ever fails to converge.
- **NOTICE matcher hardened (fixed).** The first-cut `isNegentropyRejectionNotice`
  matched bare `"envelope"` / `"NEG-OPEN"` / `"NEG-MSG"`; since a `NOTICE` has no
  subId and every connection listener sees it, an unrelated notice on a shared
  connection could abort a healthy reconcile mid-handshake. Narrowed to
  `"negentropy"` / `"unknown envelope"` (phrases a NIP-77-speaking relay never
  emits for a well-formed client); the now-un-defeated idle watchdog is the
  wording-independent backstop, so under-matching here is safe.
- **Window split dropped future-dated events (fixed).** On overflow the upper
  child was `copy(until = hi)` with `hi = until ?: now()`, so once any split
  happened, events with `created_at > now()` (clock skew) were excluded though
  the un-split path included them. The upper child now keeps the window's
  original `until` (may be null = unbounded); the split *math* still uses `now()`
  so it converges.
- **`NegentropyStoreSync` up-direction memory (fixed).** `haveBatches` was an
  UNLIMITED channel drained by a single network-bound uploader, so a first push
  of a large store buffered O(local-set) ids. Bounded it like `needBatches` so
  the have-direction back-pressures the reconcile.
- **`negentropySyncOrFetch` O(delivered) memory (documented).** The cross-phase
  dedup set is inherent to the combinator's contract; added a KDoc note steering
  unbounded bulk mirrors to `negentropySync` / `negentropyReconcile` directly.

Noted but not changed (low severity / would cost more than they save):

- `fetchByIds` returns an `ArrayList` mutated on the relay reader thread; on the
  idle-timeout path there's no channel happens-before, so a late in-flight event
  could race the worker's iteration. Near-impossible for by-id filters (needs a
  live event on a specific 32-byte id after the idle deadline); a fix would add
  per-event synchronization on the download hot path.
- Per-batch `ArrayList(needIds.subList(...))` copy and the fan-out's no-op
  `sendHaveBatch` chunk-then-discard are minor allocation churn.
- `negentropySync`'s "exactly once, no dedup" holds only because relays send the
  overflow NEG-ERR up-front (before streaming any ids); a relay that streamed
  partial rounds then overflowed would double-deliver. Latent, not triggered.
