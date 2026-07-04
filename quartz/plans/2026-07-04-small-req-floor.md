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

## Where the floor actually is

In-process REQ→EOSE is ~0.5–0.6 ms, but the wire-level p50 is
1.2–1.7 ms: the missing ~1 ms per REQ is transport-side — the Ktor CIO
frame write path, per-frame sends with no batching, and the client
round trip — which is backlog item 6 (websocket send path, 13–25% of
ingest CPU in the JFR profile) territory. strfry completes the whole
round trip in under 0.6 ms on a single event loop with uWebSockets.

**Do not retry** coroutine-dispatch shaving for this gap without first
measuring the transport side: instrument the time between
`RelaySession`'s `onSend` invocation and the frame actually leaving
the socket, and compare permessage-deflate/frame-batching settings
against strfry's uWS configuration.
