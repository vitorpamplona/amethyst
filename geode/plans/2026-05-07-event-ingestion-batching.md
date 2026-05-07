# Event ingestion: write batching + pipelined OK

## Problem

EVENT acceptance is the hot path on a busy relay — every published note,
every reaction, every DM lands here. Today the per-event flow is fully
serial:

1. `RelaySession.handleEvent` (`quartz/nip01Core/relay/server/RelaySession.kt:131`)
   awaits `policy.accept(cmd)` (Schnorr verify if `VerifyPolicy` is in
   the stack — ~0.1 ms on JVM).
2. Awaits `store.insert(cmd.event)` — a single SQLite write, guarded by
   the connection-pool writer mutex (`SQLiteConnectionPool`).
3. Sends `OkMessage` back through the writer coroutine.

`LoadBenchmark.publishThroughputSingleClient` measured **~760 EPS**;
the concurrent variant **~2000 EPS** (limited by SQLite writer mutex
contention, not WS throughput).

## Constraints we must keep

- **OK pairs by event id, not by order**: the OK frame carries the
  event id, so clients pair replies to publishes by id. OKs can be
  emitted in any order — including reordered against the EVENT
  stream, and against each other on the same connection. This frees
  us to fan OKs out as soon as the writer has a per-row decision.
- **OK semantics = accepted, not fsynced**: NIP-01 treats `OK true`
  as "accepted by the relay," not "durably on disk." We can reply as
  soon as SQLite returns success for the row (inside the open
  transaction, before commit/fsync). Group commit can batch the
  fsync without holding OKs back.
- **Per-row decision still required**: the OK reason field is
  per-event (duplicate, blocked, invalid sig, pow, etc.), so we
  cannot fan out a single batch-level OK. Each row's OK must reflect
  that row's outcome.

## Sketch

### Tier 1 — SQLite WAL + group commit (cheap win)

Confirm `PRAGMA journal_mode=WAL` + `PRAGMA synchronous=NORMAL` on the
event-store DB; group commits across the writer mutex's hold window.
Today each insert is its own transaction. Wrap N inserts (or a 5 ms
budget, whichever first) in a single transaction managed by the writer
coroutine.

Because OK reflects acceptance not durability, each row can fan an OK
as soon as the per-row INSERT statement returns inside the
transaction — we do not need to wait for the batch's commit. The
fsync is hidden from the publisher latency budget entirely.

Implementation lives in quartz's `EventStore` / `SQLiteConnectionPool`,
not geode — but geode owns the benchmark and validates the gain.

Expected: **~5–10× write throughput** on a fast SSD. SQLite group
commit is well-trodden territory (nostr-rs-relay, strfry both do it).

### Tier 2 — pipelined OK over multiple in-flight EVENTs

`RelaySession.receive` is currently single-flight: one EVENT in,
process, OK out, next EVENT. Allow a connection to push N EVENTs
concurrently and dispatch them to a per-connection ingest pipeline.

A `Channel<EventCmd>` with capacity = `INGEST_PIPELINE_DEPTH` per
connection, drained by a coroutine that feeds the group-commit writer
above. OKs go straight to `outQueue.send()` the moment each row
returns from INSERT — no ordering bookkeeping needed, since the OK
frame already carries the event id and the spec doesn't require
order. A pipelined publisher keying on event id will pair replies
correctly.

Expected: hides verify+insert latency behind the next EVENT's parse,
gets us closer to network-bound throughput.

### Tier 3 — eager Schnorr verify off the writer thread

`VerifyPolicy` is in the policy stack and runs synchronously on
`receive`. Move it into the ingest pipeline so verification of EVENT N+1
runs concurrently with the SQLite commit of EVENT N. secp256k1 verify
is parallelisable; the writer should never block on it.

## How to verify

Add to `geode.perf.LoadBenchmark`:

- `publishGroupCommitSingleClient` — same workload as the current
  single-client benchmark, asserts >5000 EPS.
- `publishPipelinedSingleClient` — sends 100 EVENTs without awaiting
  intermediate OKs; measures end-to-end throughput and verifies that
  every event id receives exactly one OK (in any order).

Existing benchmarks stay as the regression floor.

## Risks

- **Group commit windows**: if a single bad event in the batch fails
  validation, we must not roll back the good ones. The batch needs
  per-row commit semantics (row-level errors → row-level OK false).
- **Backpressure on slow disks**: deeper pipelines on slow storage
  amplify out-of-memory pressure. Cap the in-flight queue depth and
  apply existing slow-client backpressure if it fills.
- **Replay protection**: the existing dedupe table needs to see the
  event before commit, not after — keep that check inside the writer
  coroutine.
