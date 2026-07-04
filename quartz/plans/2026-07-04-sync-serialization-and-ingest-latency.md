# Two more relayBench gaps: NEG-MSG serialization (fixed) + ingest latency (measured, not worth it)

Follow-up to the 1M relayBench run and the NIP-77 diagnosis
(`2026-07-04-negentropy-reconcile-profiling.md`). Both were measured in
isolation first so the fix could be judged on the delta.

## NEG-MSG wire serialization — **fixed** (~2.5–2.8×)

The server's per-round reconcile JFR put a large slice in serialization: a
reconcile frame is `Hex.encode`d to a ~1 MB hex string, then the outgoing
`NegMsgMessage` was turned into wire JSON by the generic (Jackson)
serializer, which wraps the giant hex string in a value node and **scans
every char for JSON escapes** a `[0-9a-f]` payload can never contain, then
re-copies.

`NegMsgMessage.toJson()` now builds `["NEG-MSG","<sub>","<hex>"]` directly —
no node tree, no escape scan of the hex. The fast path fires only when the
client-chosen `subId` is escape-free printable ASCII (the bytes the JSON
encoder emits verbatim); any exotic subId (quotes, control chars, non-ASCII)
falls back to the generic serializer, so output is **byte-identical**.
`RelaySession.send` now routes through `message.toJson()` (default is
unchanged for every other message type).

Measured (`NegMsgSerializationBenchmark`, toJson + UTF-8, per frame):

| frame | generic | fast | speedup |
|---|---:|---:|---:|
| 64 KiB | 0.47 ms | 0.19 ms | 2.5× |
| 250 KiB | 1.97 ms | 0.76 ms | 2.6× |
| 500 KiB (strfry cap) | 3.87 ms | 1.37 ms | 2.8× |

At the 500 KB frame cap that's ~2.5 ms saved per NEG-MSG, ~35 ms over a
14-round reconcile — server-side, on top of the (library-side) prefix-sum
fingerprint work. Correctness: a subId battery asserts byte-identity with
the generic path, and the `GeodeVsStrfryNegentropySyncTest` interop test
(real strfry) reconciles against the fast-built frames.

## Receipt➜queryable ingest latency — **measured, not worth fixing**

Hypothesis: geode's group-commit `IngestQueue` (two channel handoffs,
submit→verifier→writer) adds latency for a single event on an idle relay,
explaining the 1M gap (geode 4.68 ms vs strfry 2.32 ms p50).

`IngestLatencyBenchmark` timed `submit→onComplete` (fires after COMMIT =
queryable) vs a direct `batchInsert`:

| path | p50 | p90 | p99 |
|---|---:|---:|---:|
| IngestQueue (submit→OK) | 0.21 ms | 0.31 ms | 0.50 ms |
| direct batchInsert | 0.05 ms | 0.09 ms | 0.17 ms |
| **pipeline overhead** | **0.17 ms** | | |

The whole ingest trip is ~0.2 ms — the pipeline overhead (~0.17 ms) is <10%
of the ~2.4 ms gap. A single-event fast path in the (carefully-tuned) writer
can't close it. **The gap is in the cross-connection REQ-visibility/poll
path, not ingest** — the probe publishes on one connection and hammer-polls
`REQ {ids:[id]}` on another, so it's dominated by websocket round-trips +
how fast a committed row surfaces to a concurrent reader, not the write. A
real fix needs that path profiled; the writer is not the lever. Benchmark
kept as the evidence.
