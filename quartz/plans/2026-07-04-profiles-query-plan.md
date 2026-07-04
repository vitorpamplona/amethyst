# The `profiles` query: why kind-0 + authors scans, and how to fix it

**Status: diagnosed + two fixes measured, not yet applied** (the fix is a
core-QueryBuilder behavior change with an ordering trade-off — wants a
maintainer call). Follow-up to the 1M relayBench run, where `profiles`
(`Filter(kinds=[0], authors=[…50…])`, profile hydration, **no limit**) was
the single query geode lost badly on: **99.5 ms p50 vs strfry 0.94 ms
(~100×)**, while geode won or tied the other nine scenarios.

## Root cause

The REQ query path (`QueryBuilder.makeSimpleQuery`, `project = true`)
appends `ORDER BY created_at DESC` **unconditionally** — even when the
filter has no `limit`. For `kind = 0 AND pubkey IN (…50…) ORDER BY
created_at DESC`, SQLite has two options:

- `query_by_kind_created (kind, created_at)` — seek `kind = 0`, then walk it
  in `created_at` order. Satisfies the ORDER BY **for free**, but walks
  *every kind-0 row* (every profile on the relay) and filters 50 authors out
  of them.
- `query_by_kind_pubkey_created (kind, pubkey, created_at)` — 50 seeks
  (one per author), but the results arrive grouped by author, so the ORDER
  BY needs a **sort**.

SQLite picks the first: avoiding the sort looks cheaper than 50 seeks in its
cost model. That's fine when there are 2k profiles; at 1M events with tens
of thousands of profiles the scan *is* the 99.5 ms. strfry seeks its
`(pubkey, kind)` index and merges — the 0.94 ms.

`EXPLAIN QUERY PLAN` at 2k profiles (see `ProfilesQueryPlanBenchmark`):

```
current (ORDER BY, no limit):  SEARCH … USING INDEX query_by_kind_created (kind=?)         1.24 ms
```

## `ANALYZE` does not fix it

Verified decisively: running `ANALYZE`, and even **reopening the store** so
fresh connections read `sqlite_stat1`, leaves the plan unchanged — still the
kind scan. With the ORDER BY present, the scan genuinely avoids a sort, so
accurate stats don't change SQLite's mind. This is not a stale-stats
problem; it's the ORDER BY constraining the plan.

## Two fixes (both measured, both correct — identical result sets)

At 2k profiles / 42k events (the gap widens with profile count):

| | plan | time | ordering |
|---|---|---|---|
| current | scan `query_by_kind_created` | 1.24 ms | newest-first |
| **Fix A** — drop `ORDER BY` when `limit == null` | planner picks `query_by_kind_pubkey_created` itself | **0.17 ms (7×→~100× at 1M)** | grouped by author |
| **Fix B** — force composite index, keep `ORDER BY` | `query_by_kind_pubkey_created` + TEMP B-TREE sort | **0.19 ms** | newest-first (unchanged) |

**Fix A** (recommended): in `makeSimpleQuery`, emit `ORDER BY created_at
DESC` only when `limit != null`. Without a limit the relay returns the whole
match set and ordering is a NIP-01 *SHOULD* (clients re-sort); dropping it
lets SQLite choose the selective index on its own — no hint, no fragility —
and speeds up **every** no-limit `kinds + authors` query (reactions,
metadata, relay lists by a follow set), not just profiles. Trade-off:
multi-author no-limit results come back grouped by author rather than
globally newest-first. Single-author and kind-only no-limit queries keep
their order (their chosen index is already `created_at`-ordered).

**Fix B** (zero behavior change): when the filter has `kinds` + `authors`
and **no limit**, add `INDEXED BY query_by_kind_pubkey_created` and keep the
ORDER BY — SQLite seeks the authors then sorts the (already-materialized)
result. Preserves newest-first exactly. Costs a hard index hint (safe: the
index is created unconditionally) that must be scoped to no-limit filters —
forcing it on limited queries (e.g. the 150-author home feed) would regress
them, since there the `created_at` scan + early `LIMIT` is the better plan.

Either way the scoping is the same: **`kinds` + `authors`, no `limit`.**
Limited feeds (home, global) are untouched and already fast.

## Artifact

`quartz …prodbench.ProfilesQueryPlanBenchmark` — seeds a kind-0-heavy store,
prints the three plans + timings, asserts all shapes return identical rows.
Runs in CI (~seconds at 42k).
