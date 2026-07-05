# relayBench sync fairness: account for NIP-62 / NIP-09 / NIP-40

**Status: note / not yet implemented.** Captures a measurement bug found while
validating the negentropy work, so the next person doesn't re-derive it.

## The problem

`SyncBenchmark.effectiveEvents` builds the "reference" set the harness
reconciles against by collapsing **replaceable/addressable** kinds to their
newest version — and nothing else. But a spec-compliant relay stores strictly
*less* than that, because it also honors:

- **NIP-62 Request to Vanish** (kind 62). A `["relay","ALL_RELAYS"]` (or
  this-relay) vanish request means the relay deletes **all** events from that
  pubkey. geode implements this (`RightToVanishModule`); strfry does not.
- **NIP-09 deletions** (kind 5). Both geode and strfry apply these, but the
  harness reference keeps the deleted events *and* the deletion.
- **NIP-40 expiration** (`expiration` tag). geode drops expired events; strfry's
  support differs.

So the harness reference over-counts: it retains events a compliant relay is
*required* to have removed.

## The symptom

The sync pair is flagged **"did not converge ✗"** even when the relays are
behaving correctly. In the 1M-corpus run this showed as geode "missing" 28
events vs the reference — all traced to a **single NIP-62 vanish pubkey**
(`fa0778c1ccbb…`, `["relay","ALL_RELAYS"]`): geode correctly purged that user's
31 events, strfry kept them, and the harness expected them present.

Two concrete distortions:

1. **False non-convergence.** `converged` (SyncBenchmark ~line 273) compares
   the relays' stored sets to the unfiltered reference, so a NIP-62/40-compliant
   relay can never converge against a corpus that contains vanish/expiry events.
2. **Inflated reconcile cost.** The identical-set reconcile uses the unfiltered
   `effective` as the *initiator's local set*. Those phantom events are real
   leaves negentropy must hunt, so geode's steady-state reconcile ran **3 rounds
   / ~520 ms** instead of the **1 round / ~110 ms** it would take against a
   matching set — which masks negentropy speedups (v1.1.1/v1.2.0) at the wire
   behind the extra round-trips.

## The census (first 200k of the damus 1M corpus)

- NIP-62 vanish requests: **7 distinct pubkeys**
- NIP-09 deletions: **0**
- NIP-40 expiration-tagged events: **381**

## The fix (when someone picks this up)

Make the reference model what a compliant relay actually stores, in
`effectiveEvents` (or a new `compliantEffective`):

1. Collect vanish pubkeys (kind 62); drop every event from them, and the vanish
   request itself, whose `created_at` is at/under the vanish request's time (or
   unconditionally for `ALL_RELAYS`).
2. Apply kind-5 deletions: drop referenced `e`/`a` targets authored by the same
   pubkey.
3. Drop events whose `expiration` tag is in the past (needs a fixed "now").

Then a compliant relay (geode) converges cleanly, and a relay that ignores these
NIPs (strfry) is correctly flagged as retaining events it should have dropped —
which is the honest comparison, not a geode penalty. The steady-state reconcile
also becomes a real 1-round measurement, so negentropy improvements show at the
wire.

Until then, a clean wire-level sync measurement needs a **pre-filtered corpus**
(drop vanish-pubkey events + expiry-tagged events); see the geode↔geode run that
accompanies this note.

## Follow-up: a ~0.05% shortfall is in the harness delta transfer, not geode

Even on the pre-filtered corpus, the geode↔geode run showed one side ending ~19
of ~40k events short (all kind-1, empty tags, scattered across pubkeys). That is
**not** a geode event-loss bug — geode is proven lossless at every layer:

- `quartz …store.BatchInsertLossTest` — sequential `batchInsertEvents`, 199,612
  in / 199,612 stored.
- `quartz …relay.prodbench.ConcurrentIngestLossTest` — the full `IngestQueue`
  pipeline (parallel verify, greedy-drain group commit, a concurrent FTS
  catch-up worker on the pool writer, windowed concurrent submits) — 0 lost.
- `RelaySession.handleEvent` sends `OK true` only from the writer callback, i.e.
  **after** the row commits.
- `geode …mirror.MirrorSyncLossTest` — the **real** relay-to-relay path
  (upstream `KtorRelay` WebSocket ⇄ `MirrorWorker` OkHttp client, trusted
  `skipVerify` ingest) delivers **50,000 / 50,000**, 0 missing.

So the shortfall is an artifact of the benchmark's hand-rolled delta transfer
(`SyncBenchmark.fetchByIds` + `IngestBenchmark.publishSlice`'s windowed
publish/OK-counting), which stands in for what `strfry sync` does. geode's own
sync is lossless. The proper fix is to harden that harness path — or, better,
drive convergence through geode's real `MirrorWorker` instead of simulating it —
so the benchmark stops reporting phantom loss.
