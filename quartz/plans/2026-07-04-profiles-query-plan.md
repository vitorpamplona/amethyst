# The `profiles` query: why kind-0 + authors scans, and how to fix it

**Status: shipped — Fix B applied** (the order-preserving one, chosen over
Fix A because dropping the ORDER BY changed observable result ordering — it
broke `FsParityTest`'s ordered-parity assertions, i.e. it's client-visible).
`QueryBuilder.makeSimpleQuery` now pins `INDEXED BY query_by_kind_pubkey_created`
for the exact broken shape — multi-author (`pubkey IN (…)`) + `kinds`, no
`limit`, no d-tags — keeping the newest-first ORDER BY. The REQ plan for
`profiles` seeks the composite index instead of scanning, ~8× at 42k profiles
and growing with profile count (the ~100× at 1M), with **zero behavior
change** (identical rows, identical order). Guarded by
`ProfilesQueryPlanBenchmark`. Follow-up to the 1M relayBench run, where
`profiles`
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

**Fix A (rejected):** emit `ORDER BY` only when `limit != null || authors ==
null`. Fastest (0.15 ms) and no hint, but dropping the order for multi-author
no-limit queries is **client-visible** — it broke `FsParityTest`'s
ordered-parity assertions, i.e. it changes what a REQ returns on the wire.
Not worth a semantic change for this.

**Fix B (applied):** in `makeSimpleQuery`, pin `INDEXED BY
query_by_kind_pubkey_created` when the filter is multi-author (`authors.size >
1`) with `kinds`, no `ids`, no d-tags, and **no `limit`** — keeping the ORDER
BY. SQLite seeks the authors and sorts the (small) result: same rows, same
newest-first order, ~8× here / ~100× at 1M. The scoping is exact:

- **single author** (`pubkey = ?`) is already costed right and seeks — no pin;
- **with a limit** (the 150-author home feed) the `created_at` scan + early
  `LIMIT` is the better plan — no pin;
- **d-tag / addressable** filters have their own index — no pin;
- **authors-only, no kinds** keeps the planner's choice (needs the
  flag-gated `query_by_pubkey_created`; it was only a ~3× minor loss, not the
  100× regression) — left for later.

`query_by_kind_pubkey_created` is created unconditionally, so the hint never
dangles. No existing test changed — the only multi-author cases in
`QueryAssemblerTest` carry a `limit` or a `search`, both excluded.

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
