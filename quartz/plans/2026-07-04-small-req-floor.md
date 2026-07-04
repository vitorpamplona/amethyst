# Small-REQ dispatch floor — investigated, inline fast path reverted

**Status: closed (negative result recorded).** Backlog item 2 of the
relay performance campaign.

## The gap

relayBench at 50k events: geode WINS most 500-event query scenarios
(hashtag 5.4 vs 8.4 ms, recent-window 4.1 vs 8.5) but loses ~2.5× on
small results — author-archive (19 events) 1.2–1.7 ms vs strfry's
~0.5–0.6, thread (23) likewise — and the @8conn throughput inverts
(strfry 2–3× geode). With ~20-row responses, throughput ≈ 1/latency:
there is a fixed per-REQ floor.

## Decomposition (SmallReqFloorBenchmark, kept in jvmTest)

In-process at 50k events, ~21 rows/REQ, medians of 400:

| stage | ms |
|---|---:|
| A raw store query (SQL + row decode) | 0.18 |
| B + live machinery (FilterIndex reg/unreg, dedupe set) | 0.36 |
| C + session dispatch (parse, launch, frames) | 0.60 |

Note: in-memory DBs have no reader pool (`useReader` falls back to the
writer mutex), so absolute numbers are conservative vs the file-DB
bench setup.

## What was tried and why it was reverted

An inline fast path (`SessionBackend.queryRawInline`): REQs with
provably bounded replays (limit or ids-count summing ≤ 512) ran their
stored replay on the receive coroutine and kept only a live-tail
handle — no per-REQ `launch`, no Job, no dispatcher handoffs. It cut
in-process time-to-EOSE ~17% (0.60 → 0.50 ms) with full wire-behavior
parity (stored→EOSE order, live tail, CLOSE, same-subId replacement).

Three relayBench runs (baseline, cap-256 [path not engaged — bench
filters carry limit=500 or no limit], cap-512 [engaged for
author-archive/by-ids/500-limit feeds]) showed **no movement outside
the container drift band** — strfry's own numbers drifted ±30% run to
run, and inline-eligible scenarios moved the same as ineligible ones.
Reverted per the keep-only-winners rule.

## Where the floor actually is (corrected after WireReqFloorBenchmark)

The follow-up wire benchmark (geode's `WireReqFloorBenchmark`, Ktor CIO
+ OkHttp on loopback) attributed the full path:

| leg | ms |
|---|---:|
| bare Ktor CIO echo round trip (1 or 22 frames — same) | ~0.9–1.0 |
| geode NOTICE (inline, full pump + Ktor send) | ~0.5 |
| geode empty REQ (launch + SQL, 0 rows) | ~0.6–0.8 |
| geode ~21-row REQ, wire | ~1.25 (= relayBench's number) |

**geode's websocket send path has no latency problem** — per-frame burst
cost is negligible (echo-22 ≈ echo-1), the pump adds ~nothing (NOTICE ≈
0.5 ms), and the residual vs strfry (~0.5 ms/REQ) is the per-REQ server
work already investigated above. Frame batching / permessage-deflate
would not move these numbers. Backlog item 6's remaining open angle is
the INGEST-side CPU share (13–25% in the JFR profile) — a throughput
question, not this latency one.

**The real find was client-side.** The first wire measurements showed a
flat 43.7 ms per REQ — which turned out to be the benchmark's own OkHttp
client: OkHttp does not set TCP_NODELAY, and the CLOSE-then-REQ pattern
(every feed/filter switch!) nagles the REQ behind the unACKed CLOSE
(relays never answer CLOSE) for the ~40 ms delayed-ACK window.
relayBench's harness client already carried a no-delay socket factory —
which is why bench numbers never showed it — but the production clients
(Android relay pool, Desktop, amy, geode's mirror) did not. Fixed by
`TcpNoDelaySocketFactory` (quartz jvmAndroid), now used by all of them.

**Do not retry** relay-side latency work for the small-REQ gap; the
addressable remainder is the ~0.4 ms of per-REQ dispatch machinery this
doc's revert already covers, and it does not show on the wire.
