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

- **OK ordering**: NIP-01 requires the OK reply to follow its EVENT.
  We cannot reply OK before the insert decision (the OK carries
  accepted/rejected + reason).
- **Durability semantics**: clients reasonably assume `OK true` means
  "stored." Batching must not make us reply OK before fsync.
- **Per-connection FIFO**: a publisher that sends three EVENTs in a
  row expects three OKs in that order. Reordering across connections
  is fine.

## Sketch

### Tier 1 — SQLite WAL + group commit (cheap win)

Confirm `PRAGMA journal_mode=WAL` + `PRAGMA synchronous=NORMAL` on the
event-store DB; group commits across the writer mutex's hold window.
Today each insert is its own transaction. Wrap N inserts (or a 5 ms
budget, whichever first) in a single transaction managed by the writer
coroutine. On commit, fan back N OK replies.

Implementation lives in quartz's `EventStore` / `SQLiteConnectionPool`,
not geode — but geode owns the benchmark and validates the gain.

Expected: **~5–10× write throughput** on a fast SSD. SQLite group
commit is well-trodden territory (nostr-rs-relay, strfry both do it).

### Tier 2 — pipelined OK over multiple in-flight EVENTs

`RelaySession.receive` is currently single-flight: one EVENT in,
process, OK out, next EVENT. Allow a connection to push N EVENTs
concurrently, dispatch them to a per-connection ingest pipeline, and
serialise OKs back in arrival order via a small commit log.

A `Channel<EventCmd> with capacity = INGEST_PIPELINE_DEPTH` per
connection, drained by a coroutine that batches into the group-commit
above. OK responses are written to an `outQueue.send()` already — so
the pipeline just needs to record arrival order and emit OKs in that
order after each batch commits.

Expected: hides the verify+insert latency behind another EVENT's
parse, gets us closer to network-bound throughput.

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
  intermediate OKs; measures end-to-end and OK-ordering correctness.

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
