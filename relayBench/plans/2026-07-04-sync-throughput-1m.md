# 1M-event sync throughput: strfry↔strfry vs geode↔geode vs strfry→geode

Date: 2026-07-04

## Question

When syncing a full **1,000,000-event** corpus between two *local* relays,
what is the end-to-end throughput (events/second) for each pairing:

1. **strfry → strfry** (strfry's native `strfry sync`, NIP-77 negentropy)
2. **geode → geode** (geode's production `MirrorWorker`, REQ backfill)
3. **strfry → geode** (geode ingesting a strfry-served corpus)

Corpus: the latest ~1M events from `relay.damus.io`
(`relayBench/.corpus-cache/corpus-download-relay.damus.io-n1000000.ndjson`).
After strfry import + de-dup the source holds **997,980** events (a few
thousand of the raw 1M are duplicates / superseded replaceables).

## How each number is measured

Every run starts with an **empty sink** and pulls the whole corpus from a
**full source** over a real loopback WebSocket, timed from first byte to the
sink reaching the target (or plateauing). Sink = geode uses its real relay
config: deferred-FTS indexing + the live negentropy index on (i.e. the same
work a production geode does on every insert).

- **strfry→strfry**: `strfry sync ws://src --dir down` against a running
  source strfry. Native negentropy reconcile + strfry ingest.
  Script: `relayBench/sync-throughput-strfry.sh` (and the reuse variant that
  syncs a fresh sink from an already-running source).
- **geode→geode**: `MirrorSyncThroughputTest` default mode. An in-process
  `KtorRelay` preloaded with N events is mirrored by the production
  `MirrorWorker` (REQ backfill, trusted/skip-verify) into an empty geode.
- **strfry→geode**: `MirrorSyncThroughputTest -DsyncSourceUrl=ws://…`. See the
  "MirrorWorker can't do a bulk backfill from strfry" finding below — this mode
  drives `NostrServer.ingest` directly from a **backpressured** socket drain
  instead of `MirrorWorker`.

## Results (1M corpus, single 16 GB host)

| Pair          | Mechanism                        | Synced            | Time    | Throughput          | Completes? |
|---------------|----------------------------------|-------------------|---------|---------------------|------------|
| strfry→strfry | `strfry sync` (negentropy)       | 997,977 / 997,980 | ~391 s  | **~2,550 ev/s**     | ✅ |
| geode→geode   | MirrorWorker REQ (synthetic)     | 1,000,000 / 1M    | 76.0 s  | **13,161 ev/s**     | ✅ |
| **strfry→geode** | **negentropy (geode client)** | **994,936 / 997,980** | **171.0 s** | **5,818 ev/s** | ✅ |
| strfry→geode  | paged REQ drain (real corpus)    | ~310k / 997,980   | —       | ~7,000 ev/s *       | ❌ (strfry kills conn) |

\* the paged-REQ drain sustains ~7,000 ev/s (peaks ~11k warm) but **does not
finish** — see "strfry kills a slow REQ client" below. Switching geode to a
**NIP-77 negentropy client** (the same mechanism `strfry sync` uses) **does
finish**: reconcile against strfry's full set took **11.4 s / 64 rounds**
(empty local → 995,024 need-ids), then a client-paced fetch (2,000-id REQ
batches) + ingest completed at ~7k ev/s steady-state, **5,818 ev/s end-to-end**.
(The 994,936 vs 997,980 gap is the NEG-scope/expiry artifact — strfry's
negentropy set excludes a few thousand expired/superseded events it still
exports — same family as the strfry→strfry 3-event shortfall, not data loss.)

**The headline, apples-to-apples comparison** (both negentropy, same corpus,
empty→full): **strfry→geode 5,818 ev/s vs strfry→strfry ~2,550 ev/s — geode
ingests real content ≈2.3× faster than strfry, and finishes the pull.**

**Two things make the raw table misleading — read the caveats:**

1. **geode→geode's 13,161 is inflated by synthetic content.** That source
   preloads events with 4-byte content (`"e$i"`), empty tags, and a fake sig.
   Real damus.io events are ~2.4 KB on the wire with real content + tags, so
   FTS tokenization, tag indexing, and store growth all cost more. The
   real-content geode ingest rate is the **~7,000 ev/s** measured on
   strfry→geode, not 13k. So the honest **real-content ingest** comparison is
   **geode ~7,000 ev/s vs strfry ~2,550 ev/s** (≈2.7× — consistent with the
   earlier ingest benchmarks; strfry's figure also carries negentropy reconcile).
2. **strfry→strfry's 3-event shortfall** is the known NIP-62/09/40 harness
   artifact (`2026-07-04-sync-fairness-nip62-09-40.md`), not data loss.

Neither geode number is a like-for-like *protocol* comparison with
strfry→strfry (REQ streaming vs negentropy reconcile); they're each relay's
real "pull the whole corpus" path.

## Finding: MirrorWorker can't bulk-backfill from a fast foreign relay

The first strfry→geode attempts via the production `MirrorWorker` **stalled at
~500, then ~16k of 1M** and never converged. Root cause, in order of discovery:

1. **strfry's `maxFilterLimit` (default 500).** `MirrorWorker`'s down path is a
   plain REQ with no `limit`. strfry caps an unbounded REQ at `maxFilterLimit`
   stored events, sends EOSE, then streams only live events. geode-as-source
   has no such cap, which is why geode→geode fully converges. Fix for the
   benchmark: boot the source strfry with
   `--set relay.maxFilterLimit=5000000 --set relay.maxPendingOutboundBytes=0`.
   A raw REQ then returns all **997,986 events in 12.8 s (~78k ev/s)** — strfry
   is not the bottleneck.
2. **MirrorWorker's unbounded intake channel.** Its `SubscriptionListener`
   callback can't suspend, so events funnel through a `Channel.UNLIMITED` via
   `trySend` into a consumer that calls the (bounded, backpressured)
   `IngestQueue`. This is a *deliberate* trade — blocking the shared OkHttp
   reader would park other upstreams' backlogs (see `MirrorWorker` kdoc). For
   **live tailing** it's correct. For a **1M bulk backfill from a 78k-ev/s
   source into a ~7k-ev/s sink**, the unbounded channel balloons past the heap
   and the JVM dies GC-thrashing (observed: consumer stops at ~16k/1M).
   `IngestQueue` itself is bounded (capacity 1024) and `submit` suspends, so a
   *direct* feeder gets proper backpressure — the fragility is only the
   unbounded hop in front of it.

3. **…and even a correctly-backpressured, client-paced REQ can't finish.**
   The benchmark's external drain replaces MirrorWorker with a dedicated
   socket whose reader we *are* free to block (bounded hand-off channel),
   walking the corpus newest→oldest in bounded `limit=20000` windows. That
   pulls cleanly at ~7,000 ev/s… until ~310k events, where it **stalls every
   time** (index on/off, 2 GB heap or 10 GB — same wall). Root cause, from the
   **strfry source log**: every drain connection disconnects at exactly
   `Pending: 32.01M` (1006). geode's real-content ingest gradually slows as its
   in-memory store grows; once it drops below strfry's send rate, strfry's
   outbound buffer for the in-flight page crosses its
   `maxPendingOutboundBytes = 32 MB` cap and strfry **kills the connection**.
   The pager then waits forever for an EOSE that never arrives. Raising
   strfry's cap just moves the wall (it OOM-buffers gigabytes instead — the
   `Pending: 1.05G / Broken pipe` seen earlier).

### Conclusion: cross-relay bulk sync needs negentropy, not REQ

strfry's REQ serving is structurally hostile to any client slower than its
scan: it buffers outbound and hard-kills (or OOM-buffers) the laggard. No
amount of client-side backpressure or paging fixes this, because the failure
is on strfry's *send* side. **NIP-77 negentropy is the only mechanism that
completes**: it is pull/reconcile-based and *client-paced* (strfry's own sync
fetches the diff 50 events at a time), so a slow client is never killed. This
is exactly why `strfry sync` — the strfry→strfry path — is negentropy, and it
is the direct answer to "why aren't we using negentropy for geode too": we
should, and over plain REQ geode structurally *cannot* finish a 1M pull from
strfry.

### Recommendation (needs a design call)

A production geode that backfills a large foreign relay from empty should use
**NIP-77 negentropy** for the initial pull (reconcile → client-paced fetch),
not the live-tail REQ path. geode already has the negentropy client/server;
wiring a "reconcile then fetch the diff in bounded batches" backfill into
`MirrorWorker` would make strfry→geode both robust and directly comparable to
strfry→strfry. (A cheaper stop-gap — bound MirrorWorker's intake per-upstream
with a dedicated reader — fixes the OOM but not the strfry-kills-slow-clients
wall; only negentropy fixes both.)

Separately worth a look: geode's real-content ingest *decays* from ~11k→~7k
ev/s as the in-memory store grows to a few hundred k — expected B-tree/FTS
cost, but worth confirming it's not superlinear at 1M on a disk-backed store.

## Why the geode number is a *paged REQ*, not negentropy — and why it's the same

`strfry→strfry` already *is* negentropy (`strfry sync`). The natural question:
shouldn't strfry→geode use negentropy too, for parity?

NIP-77 sync is two phases: **(1) reconcile** — exchange range fingerprints to
compute the set difference, no events moved; **(2) transfer** — fetch the
missing events. Into an **empty sink** phase 1 trivially says "you're missing
everything," so the whole cost collapses onto phase 2: fetch + ingest all
~998k events in bounded batches (strfry's own sync pulls **50 at a time** —
`DOWN: 50 events` in its log). Therefore:

- strfry→strfry's ~2,600 ev/s is **ingest/transfer-bound**, not reconcile-bound.
- Into an empty geode, negentropy and the paged-REQ drain measure the **same
  thing** — geode's paced ingest rate. The paged drain *is* negentropy's
  transfer phase minus a near-free reconcile round.
- Negentropy's reconcile only wins when the sink is **already mostly full**
  (small diff). Geode's reconcile performance there is measured separately —
  at 1M, geode's negentropy (kmp-negentropy v1.2.0, PrefixSumStorageVector) is
  ~1.5–4× faster than strfry's C implementation (see the negentropy-speedup
  tasks / `2026-07-04-sync-fairness-nip62-09-40.md`).

So the paged-REQ figure is the correct empty-sink strfry→geode number; a full
negentropy-client run lands at the same place plus a cheap reconcile.
