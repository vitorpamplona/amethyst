# 1M-event sync throughput: strfry↔strfry vs geode↔geode vs strfry→geode

Date: 2026-07-04

## Four-pair NEGENTROPY comparison (2026-07-05, apples-to-apples)

All four source→sink pairings, **all NIP-77 negentropy**, empty sink pulls the
same ~1M damus.io corpus, run sequentially (no contention). geode sink
configured like strfry: **verify ON, FTS OFF** (`-DsyncVerify=true -DsyncFts=false`);
`strfry sync` verifies too and has no FTS. Sinks: geode = its negentropy client;
strfry = `strfry sync --dir down`. Sources: a geode relay seeded with
`geode import <corpus.ndjson> --db …` then served normally, and a strfry relay
(`strfry import` + serve) — each holding the corpus.

| # | source → sink | sink engine | synced | time | throughput |
|---|---------------|-------------|--------|------|------------|
| A | geode → geode  | geode neg client | 994,162 / 994,832 | 142.6 s | **6,971 ev/s** |
| B | strfry → geode | geode neg client | 994,828 / 994,917 | 148.7 s | **6,690 ev/s** |
| C | strfry → strfry| `strfry sync`    | 893,000           | 333.0 s | **2,682 ev/s** |
| D | geode → strfry | `strfry sync`    | 994,151           | 467.3 s | **2,127 ev/s** |

**Reads:**
- **The SINK sets the rate, not the source.** geode sink ≈ 6.7–7.0k ev/s from
  either source; strfry sink ≈ 2.1–2.7k ev/s from either source. Sync throughput
  is an *ingest* property.
- **geode ingests ≈ 2.6–3.3× faster than strfry** via negentropy, verifying.
- **Full geode↔strfry NIP-77 interop, both directions**: B (geode client ↔
  strfry server) and D (strfry client ↔ geode server) both complete. geode's
  negentropy server and client are wire-compatible with strfry's.
- **Count artifacts** (throughput is unaffected): C synced 893k, not ~994k,
  because strfry's negentropy snapshot excludes NIP-40-expired events, so a
  strfry *source* only offers ~893k. geode has no expiration cutoff ("no limits"),
  so a geode source offers its full ~994k (D transfers all of it). Same
  NIP-62/09/40 fairness family as `2026-07-04-sync-fairness-nip62-09-40.md`.

### REQ-mode counterpart (2026-07-05)

Same corpus + same geode sink config (verify ON, FTS OFF), but a plain **paged
`REQ`** pull instead of negentropy (`-DsyncMode=req`, 20k-event `until` windows).

| # | source → sink | mechanism | synced | time | throughput |
|---|---------------|-----------|--------|------|------------|
| A | geode → geode  | geode paged REQ | 994,144 / 994,832 | 200.3 s | **4,963 ev/s** ✅ |
| B | strfry → geode | geode paged REQ | 994,813 / 994,898 | 171.5 s | **5,801 ev/s** ✅ |
| C | strfry → strfry| — | — | — | **n/a** |
| D | geode → strfry | — | — | — | **n/a** |

- **strfry has no REQ bulk sync.** Its REQ path is `strfry stream`, which sends
  `["REQ","sub",{"limit":0}]` — live-only, no historical replay; `strfry sync` is
  negentropy. So the strfry-*sink* REQ pairs (C, D) have no strfry-native
  mechanism — that gap is the finding: geode can bulk-pull over REQ, strfry can't.
- **strfry→geode over REQ now completes** (B) — earlier it stalled at ~310k
  because FTS-on ingest fell behind strfry's page serving and strfry killed the
  slow client at its 32 MB pending cap. With FTS off the sink keeps pace, so
  strfry never trips the cap. (Completion over REQ is contingent on the sink
  out-running the source; negentropy is immune because it's pull-paced.)
- **REQ is ~15–30% slower than negentropy** for the geode sink (4,963–5,801 vs
  6,690–6,971): the paged `until` windows re-fetch boundary duplicates and range-
  scan the source, vs negentropy's direct id fetch. And geode-as-a-REQ-*source*
  is slower than strfry-as-a-source (A 4,963 < B 5,801) — SQLite range scans vs
  strfry's LMDB.

---


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

### Implemented: MirrorWorker now mirrors strfry's two-phase model

`MirrorWorker` gained a NIP-77 **"sync" catch-up** phase (geode's equivalent of
`strfry sync --dir down`) that runs once per down/both upstream before the live
REQ tail:

- **Catch-up** reconciles the local set against the upstream over the historical
  `[now - backfill_seconds, now]` window and downloads only the diff, via the
  ready-made `INostrClient.negentropySyncOrFetch` — client-paced, so strfry can't
  overrun/kill us, and it **completes** the bulk pull. A small
  `localEntries` param was added to the public `negentropySync`/
  `negentropySyncOrFetch` so the reconcile diffs against what we already hold
  (incremental, like `strfry sync`) instead of re-downloading.
- **Either mode, transparently**: `negentropySyncOrFetch` auto-falls back to
  paged REQ for an upstream that doesn't speak NIP-77 — no config toggle.
- **Live tail** unchanged: the REQ subscription now starts at `now` when catch-up
  is on (history is the sync's job); the two windows overlap at `now` and the
  store's unique-id constraint dedups the seam.

**Both directions**, matching `strfry sync --dir both` (which strfry's source
confirms is bidirectional negentropy — `doUp = both||up`, `doDown = both||down`):

- **down** catch-up pulls what the upstream has and we lack
  (`negentropySyncOrFetch`, with paged fallback), then the live REQ sub tails.
- **up** catch-up reconciles and pushes what we have and the upstream lacks
  (streaming `negentropyReconcile`, publishing each `onHaveIds` batch straight
  to `client.publish`), then the live up-session tails. Negentropy-only (no
  paged fallback needed — the live up-session covers a non-NIP-77 upstream). The
  streaming reconcile keeps peak memory at one id batch rather than materializing
  the whole `have`/`need` diff (an important distinction at 1M — the full diff is
  ~100 MB of id strings). The push runs as a **reconcile→push convergence loop**:
  `client.publish`'s outbox is best-effort under a bulk burst (each publish also
  churns a reconnect; measured ~1–2% dropped per pass), so each round
  re-reconciles — the reconcile *is* the delivery check — and re-pushes only the
  stragglers until the `have` diff is empty. Observed on the 3000-event up test:
  3000 → 69 → 2 → 0 across 3 rounds, lossless.

Same vocabulary as strfry throughout — one `[[mirror]]` entry, one `dir`
(down/up/both) driving both phases; negentropy-vs-REQ is an internal transport
detail. The geode binary opts in (`Main` passes `negentropyBackfill = true` +
the store); the `MirrorWorker` default stays off so existing live-REQ tests are
unchanged. See `MirrorNegentropyCatchUpTest`: the down test isolates catch-up
from the live tail by preloading *historical* events a live-only sub can't
deliver (3000 + 1 live); the up test pushes 3000 local events to an empty sink.

Structural note: strfry keeps these as *separate commands* (`sync` = negentropy
both-ways; `router`/`stream` = REQ live both-ways). geode folds both into one
`MirrorWorker` under a single `dir` — more integrated, same semantics.

Remaining follow-ups: `liveNegentropySnapshot`-based local enumeration for very
large mirrors (the up path's `snapshotIdsForNegentropy` currently scans); a
single-pass reconcile for `dir=both` (today it runs one reconcile per
direction); and optionally bounding the live-tail intake per-upstream.

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
