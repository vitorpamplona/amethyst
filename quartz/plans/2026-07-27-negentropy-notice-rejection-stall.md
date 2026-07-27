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
