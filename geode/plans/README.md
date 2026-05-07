# geode plans

Performance-focused design docs for future work. Each file is a
self-contained sketch — problem statement, observed numbers, proposed
fix, how to verify, risks. None of these are committed work; they're
the queue.

Ordered roughly by expected impact:

| Plan | Headline gain |
| ---- | ------------- |
| [2026-05-07-event-ingestion-batching.md](2026-05-07-event-ingestion-batching.md) | 5–10× write EPS via SQLite group commit + ingest pipelining |
| [2026-05-07-live-broadcast-fanout-index.md](2026-05-07-live-broadcast-fanout-index.md) | >10× fanout speedup at >2 000 subscribers |
| [2026-05-07-connection-scaling.md](2026-05-07-connection-scaling.md) | 2 000 → 10 000+ concurrent connections |
| [2026-05-07-negentropy-large-corpus.md](2026-05-07-negentropy-large-corpus.md) | 25× lower memory + faster NEG-OPEN on M-event corpora |

Verification target for each plan is a new method on
`geode.perf.LoadBenchmark` (gated by `-DrunLoadBenchmark=true`) so
regressions show up in the regular CI matrix once they're enabled.
