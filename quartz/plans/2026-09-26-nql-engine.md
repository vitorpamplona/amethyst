# NQL engine: NIP-FF in Quartz

Status: **implemented.** `quartz/…/nipXXSql/` implements NIP-FF (Nostr Query
Language) end to end: the `NQL` command in `NostrServer` (so geode too), the client
extensions, and `IEventStore.nql`. It passes all 272 of the NIP's conformance
vectors over every backend in the tests.

## Why an interpreter

The prototype re-emitted the client's query as SQLite SQL. NIP-FF's semantics are
PostgreSQL-like, not SQLite's: overflow, underflow, division by zero and domain
errors fail the query; `CAST` from text never fails; `LIKE` is case-sensitive;
NULLs sort last; `round` ties to even; and the types are static. The bundled SQLite
driver has no user-defined functions, so SQLite can't raise those errors or run
those casts. Quartz now evaluates the query itself. This is the design the NIP
recommends for LMDB-style stores, and every store answers the same way.

## Pipeline

| Step | File | What it does |
|---|---|---|
| Lex + parse | `NqlParser.kt` | Exactly NIP-FF's grammar; anything else is `invalid` |
| Check | `NqlChecker.kt` | Scopes and names, static types (numeric promotion only, NULL typed by context), result names, grouping, GROUP BY / ORDER BY by alias, no positional terms, LIMIT / OFFSET |
| Plan + run | `NqlExecutor.kt` | Native aggregates, per-source scans, joins, WHERE, groups, HAVING, results, DISTINCT, ORDER BY, LIMIT |
| Values | `NqlValues.kt` | Checked 64-bit arithmetic, finite binary64, code-point text, `LIKE`, `CAST`, the functions, 128-bit exact `sum` |
| Entry | `Nql.kt` | `Nql.run(query, params, backend, maxRows)`, `NqlResult`, `NqlEngine` |

## How a store is read

Stores keep implementing `SqlStoreBackend`, which is unchanged since the prototype.
vespa-eventstore's `VespaSqlBackend` compiles against it as is.

1. A single-source aggregate or DISTINCT whose conditions `ScanAnalyzer` captures
   exactly goes to `aggregate(AggregatePlan)`. The executor then orders and slices
   the store's rows.
2. Otherwise each `events` / `tags` source is scanned with the predicates that
   hold for its rows alone. These are the WHERE conjuncts that read only that
   source (none for the right side of a LEFT JOIN) plus its own ON conjuncts. A
   source that the store refuses (`acceptsScan`) is fetched by the join keys of a
   source already loaded (`ScanLink`), in chunks of 500.
3. A plain newest-first listing pushes `LIMIT + OFFSET` down and fetches the whole
   tie group at the boundary.
4. An `events` source read only for `id` / `created_at` becomes an id walk
   (`idsAndTimes`).
5. Joins hash on an equality between the joined-so-far and the new source, and
   fall back to a nested loop otherwise.
6. Subqueries run once per distinct value of the outer columns they read.

## Protocol

`["NQL", id, query, params?]` is answered by
`["NQL", id, {columns, rows, truncated}]` or `CLOSED`. `RelayLimits.maxNqlRows`
caps the rows and is advertised in NIP-11 as `max_nql_rows`. The session gates the
command like a `REQ`.

On the client:
- `nql()` is one exchange. It retries after AUTH and re-sends when the socket drops
  before the answer.
- `nqlIdsAndTimes`, `nqlCount` and `nqlQuery` answer a NIP-01 filter through
  `FilterSql`, paging with keysets past the relay's cap.
- NQL only shows `tag[0..4]`. `nqlQuery` checks each rebuilt event's id hash, and
  fetches an event with a longer (or empty) tag whole with a `REQ` by id.

## Tests

- `NqlConformanceTest` runs the NIP's vectors (a copy in
  `jvmTest/resources/nql/FF-conformance.json`) over three backends: the SQLite
  store, id walks, and a store that refuses unselective scans.
- `NqlDifferentialFuzzTest` runs 400 random queries (joins, subqueries, grouping,
  NULL ordering) on those three backends against SQLite over plain tables.
- `NqlPushdownTest` covers what the store is asked for per query shape.
- `FilterSqlTest`, `NqlRelayTest`, `NostrClientNqlTest` and geode's `NipXXSqlTest`
  cover the protocol and the client.

The vectors are built and checked against PostgreSQL 16 and SQLite by
`tools/nql-conformance/build.py`.

## Costs and open items

- Scans materialize their rows in memory. On the SQLite store, an unconstrained
  aggregate reads every event. The prototype ran such queries natively in SQLite.
  A relay can refuse broad scans through `acceptsScan`. A SQLite fast path for
  plain `count` / `GROUP BY` over bare columns is a possible follow-up.
- An aggregate whose arguments read only outer columns belongs to the query it is
  written in, not to the outer query as in PostgreSQL. The NIP does not say which,
  and no vector tests it.
