# GrapeRank score parity with NosFabrica Brainstorm

Goal: `amy graperank` should output scores **numerically very close** to
NosFabrica's Brainstorm service, the reference GrapeRank implementation.

Sources analysed:
- `NosFabrica/brainstorm_graperank_algorithm` — the Java scoring worker.
- `NosFabrica/brainstorm_server` — the Python orchestration server.

## How Brainstorm builds its service

A four-stage pipeline:

1. **Ingest.** `app/nostr_event_transferer/nostr_event_transferer.py` copies raw
   social-graph events — **kinds 0, 3, 10000, 1984** (profiles, follows, mutes,
   reports) — from a strfry relay into the server. Same four kinds we crawl.
2. **Graph.** Events land in **Neo4j** as a directed graph of follow / mute /
   report edges between pubkeys. Redis + Postgres back the job queue and config.
3. **Score.** The Java worker (`grape/GrapeRankAlgorithm.java`) runs GrapeRank
   from an observer, producing a **`ScoreCard`** per user
   (`rank/ScoreCard.java`): `observer, observee, hops, averageScore, input,
   confidence, influence, verified, trustedFollowers, trustedReporters`.
   **There is no `rank` field — the trust value is `influence` ∈ [0,1].**
4. **Serve / publish.** Presets are tunable per deployment
   (`DEFAULT` / `PERMISSIVE` / `RESTRICTIVE`, `graperank_preset` table, validated
   by `GrapeRankPresetParams`). Java `GrapeRankParams` mirrors the Python model
   field-for-field; the README states Python is the source of truth and both
   repos must stay in sync.

## The algorithm (their `grape/GrapeRankAlgorithm.java`)

```
rigority   = -log(rigor)
confidence(sumWeights) = 1 - exp(-sumWeights * rigority)     # weight -> confidence
per edge:  weight = edgeConfidence * influenceOfRater * attenuationFactor
           wxr    = weight * edgeRating
averageScore = sumWxR / sumWeights          (0 if sumWeights == 0)
influence    = max(averageScore * confidence(sumWeights), 0)
```

- Observer seeded at `influence = 1.0` (fixed authority).
- Non-observers seeded by hop distance, then **iterated until every user's
  influence delta < 0.0001** (`loopBreakDelta`). Seeding only affects the
  starting guess; attenuation < 1 makes the update a contraction, so the fixed
  point is unique.
- The rater weight uses the rater's **`influence`**, and
  `influence = max(weightToConfidence(sumW) * sumWR/sumW, 0)`.

## Side-by-side: Brainstorm DEFAULT vs `commons/wot`

`Constants.java` `DEFAULT_PARAMS` (== the Pydantic `GrapeRankPresetParams`
DEFAULT) against our `GrapeRankParams` defaults:

| Brainstorm field | value | our field | value | match |
|---|---|---|---|---|
| `attenuationFactor` | 0.85 | `attenuation` | 0.85 | ✅ |
| `rigor` | 0.5 | `rigor` | 0.5 | ✅ |
| `followRating` | 1.0 | `FOLLOW.rating` | 1.0 | ✅ |
| `muteRating` | -0.1 | `MUTE.rating` | -0.1 | ✅ |
| `reportRating` | -0.1 | `REPORT.rating` | -0.1 | ✅ |
| `followConfidenceOfObserver` | 0.5 | `directFollowConfidence` | 0.5 | ✅ |
| `followConfidence` | 0.03 | `indirectFollowConfidence` | 0.03 | ✅ |
| `muteConfidence` | 0.5 | `muteConfidence` | 0.5 | ✅ |
| `reportConfidence` | 0.5 | `reportConfidence` | 0.5 | ✅ |
| `loopBreakDelta` | 0.0001 | `convergence` | 0.0001 | ✅ |

The three `verified*InfluenceCutoff`s (followers 0.02, reporters 0.1,
muters 0.01) only flag a derived `verified` boolean; they do **not** affect the
score.

**Conclusion: our formula is identical and every scoring parameter matches
DEFAULT.** Our `score` *is* their `influence`
(`max(weightToConfidence(sumW) * sumWR/sumW, 0)`), propagated as the rater
weight — the exact same quantity. On the same input graph the two produce the
same influence to floating-point precision. Our published `rank = round(score *
100)` is a presentation choice on top of that influence (their `ScoreCard`
exposes `influence` as a raw float via the API).

## Where divergence can still come from — and why it's small

It is **data**, not math:

1. **Graph completeness.** Brainstorm ingests the whole strfry graph into Neo4j;
   we crawl outward from the observer via the outbox model. **This matters less
   than it seems:** a mute/report contributes `confidence * influenceOfRater *
   attenuation`, so a signal from a user with **zero influence** (someone outside
   the observer's trust graph) contributes **zero**. Only follows/mutes/reports
   authored by users *inside* the follow graph move a score — and those are
   exactly the users our crawl discovers and whose kind 3/10000/1984 we fetch.
   So the effective scoring input is the same, as long as the crawl actually
   checks every discovered user's outbox — which it now does exhaustively (no
   user cap, retrying an unreachable outbox up to `--max-attempts` times).
2. **Fringe users / crawl gaps.** A relay timeout that drops a contact list
   removes edges and shifts nearby scores. The injector mitigates this with a
   two-stage model mirroring the app's `pickRelaysToLoadUsers`, plus a
   completeness loop that retries until every user's outbox has been checked:
   - **Relay-list discovery** (kind:10002) queries the account's relays +
     bootstrap + event-finder + **indexer relays** (purplepag.es, coracle, …).
     Indexers aggregate kind:10002 (and kind:0) for the whole network, so this is
     where a stranger's outbox is found — the biggest completeness lever.
   - **Content** (kind:3/10000/1984/0) is fetched from each user's **own outbox**
     write relays, with harvested **relay hints** (from the `p`-tag hints in
     contact lists we crawl) and general-purpose relays as a best-effort fallback
     when the outbox is unknown/down. **Indexers are not used for content** — they
     don't serve those kinds; kind:3/mutes/reports live only on the user's outbox.
   The crawl loops round by round, retrying any member whose contact list still
   didn't arrive (up to `--max-attempts`), until every discovered user's outbox
   has been checked. Remaining mitigation lever: a generous `--timeout`.
3. **Convergence precision.** Both stop at delta 0.0001; residual error is
   < ~0.0001 in influence ⇒ < ~0.01 rank points ⇒ identical integer `rank`.
4. **Seeding.** Their hop-distance seed vs our zero seed — same fixed point, no
   effect on the result.

## Recommendations

- **Keep the current DEFAULT params** — they are byte-for-byte the Brainstorm
  DEFAULT preset. No change needed for parity.
- **The crawl is exhaustive by default** (no user cap; every reachable user's
  outbox is checked, unreachable outboxes retried up to `--max-attempts`). An
  incomplete crawl is the single biggest source of drift, so avoid capping it.
- **Optional, for fuller parity (not required for close scores):**
  - Add `--preset default|permissive|restrictive`. DEFAULT is confirmed; the
    PERMISSIVE / RESTRICTIVE numbers are DB-seeded in `brainstorm_server` (an
    alembic seed migration) and were not extractable from the public tree —
    pull them from a running instance before hard-coding.
  - Optionally expose `influence` as a raw float alongside `rank` in `--json`,
    and compute the `verified` flag from the cutoffs, to mirror their
    `ScoreCard` shape for interop diffing.

## Verification idea

Point `amy graperank <observer> --offline` at a store seeded from the same
strfry snapshot Brainstorm ingested, and diff our `score` against their
`ScoreCard.influence` for the same observer. Expect agreement to ~1e-4.
