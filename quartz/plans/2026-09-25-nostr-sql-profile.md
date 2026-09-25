# Nostr SQL profile: read-only SQL over websockets

Status: **prototype** (parser + compiler + cursor in `quartz/…/nipXXSql/`; no relay wiring, no NIP text yet).

## Goal

The most query power for the smallest thing relays and clients have to agree on.
The language can't be grown over time (many independent implementations), so it
has to be complete on day one.

## Decision: SQLite's SELECT *is* the spec

- **Semantics = SQLite.** No cross-engine behavior table (LIKE case, integer
  division, NULL ordering, type affinity…). A relay on another engine embeds
  SQLite (public domain, everywhere) instead of translating.
- **What the profile pins down is only what is reachable:**
  1. two virtual tables, `events` and `tags` (below);
  2. a SELECT-only grammar subset;
  3. an allowlist of functions.
- The relay **never runs the client's text.** It parses, resolves every table
  name itself, and re-emits SQL from the syntax tree. That is the whole
  security model: grammar + table resolution + function allowlist.

## Virtual schema

```sql
events(id, pubkey, created_at, kind, content, sig)
tags(event_id, idx, name, value, v2, v3, v4, rest, created_at, kind, pubkey)
```

- One row per tag. `value`, `v2`..`v4` = `tag[1..4]` (NULL if absent); `rest` =
  JSON array of `tag[5..]` (NULL if none); `idx` = position in the event's tag
  array. `created_at`/`kind`/`pubkey` are copied from the parent so tag
  aggregates need no join.
- Rows are what a no-filter REQ would return to *this session*: the relay
  supplies per-session `TableSource`s, and every `events`/`tags` reference is
  replaced with them. This is where access control (e.g. hiding kind 1059)
  lives: `EventStoreTableSources.build(hiddenKinds = …)`.

## Grammar (what's in)

`WITH [RECURSIVE]`, `SELECT [DISTINCT]`, `FROM` with subqueries, `JOIN` /
`LEFT JOIN` / `CROSS JOIN` / comma joins with `ON`, `WHERE`, `GROUP BY`,
`HAVING`, `UNION [ALL]` / `INTERSECT` / `EXCEPT`, `ORDER BY … [NULLS FIRST|LAST]`,
`LIMIT/OFFSET` (and SQLite's `LIMIT a, b`), `VALUES`, scalar / `IN` / `EXISTS`
subqueries, `CASE`, `CAST` (INTEGER/REAL/TEXT/NUMERIC), `LIKE … ESCAPE`, `GLOB`,
`BETWEEN`, `IS [NOT] [DISTINCT FROM]`, `ISNULL`/`NOTNULL`, aggregates with
`DISTINCT` and `FILTER (WHERE …)`, window functions with inline
`OVER (PARTITION BY … ORDER BY … ROWS|RANGE|GROUPS …)`, `?`, `?NNN`, `:name`
parameters.

Functions: see `SqlProfile` (aggregates, window functions, bounded scalar and
date functions). Out: `load_extension`, `sqlite_*`, `random*`, `zeroblob`,
`printf`/`format`, JSON functions.

**Out on purpose:** anything that isn't a single SELECT, `PRAGMA`, `ATTACH`,
schema-qualified names, table-valued functions, `INDEXED BY`, blob literals,
bitwise operators, `->`/`->>`. **Out for now** (easy to add *before* the spec
freezes): `COLLATE`, `NATURAL`/`USING`, `RIGHT`/`FULL` joins, named `WINDOW`
clauses, `EXCLUDE` frames, `MATCH`/`REGEXP`, row values.

## Emission rules worth knowing

- Every operator is re-emitted fully parenthesized, so the tree we parsed is
  exactly what runs. The parser copies SQLite's LALR quirks, each one found
  by the differential fuzzer: `NOT` as an operand (`1 = NOT 0`), a BETWEEN lower
  bound that runs to the first top-level AND (`x BETWEEN 1 = 1 AND 9`), and
  postfix results that continue with tighter operators
  (`5 NOTNULL - x < 1` = `((5 NOTNULL) - x) < 1`).
- Identifiers are emitted with **backticks**, not double quotes. SQLite turns a
  double-quoted name that matches no column into a string literal (the "DQS"
  legacy behavior). The spec should say DQS is off: `"x"` is always an identifier.
- CTEs are renamed to generated names; a CTE sees earlier CTEs and itself, never
  later ones, so a forward reference can't fall through to a physical table.
- Un-aliased result columns get an explicit alias equal to their source text, which
  is SQLite's own naming rule, so column names match what SQLite would report.

## Verification

- `SqlCompilerTest` (commonTest): rejections (writes, PRAGMA/ATTACH, stacked
  statements, physical tables, non-allowlisted functions, CTE escape attempts,
  pathological nesting) and emitted SQL shape.
- `SqlEventStoreQueryTest` (jvmTest): end to end on a real `EventStore`: group
  by, hashtag counts from `tags`, latest-per-author via window, follows-of-follows
  via recursive CTE, joins, hidden kinds, long tags, cursor paging, column names.
- `SqlDifferentialFuzzTest` (jvmTest): 3k random queries per run; the compiled
  statement must return the same columns, rows and errors as SQLite running the
  raw text against equivalent views. One-off runs with 2 more seeds × 40k
  queries: 0 mismatches.

## Open items (in order)

1. **Runaway queries.** Still unconfirmed whether androidx.sqlite exposes
   `sqlite3_interrupt` / progress handler. Without it a `WITH RECURSIVE` without
   a stop condition, or a large cross join, keeps a reader busy. Options: find
   the API, patch the bundled driver, or run SQL on a dedicated, killable pool.
   Memory: nested `replace()` can build big strings (bounded by
   `SQLITE_MAX_LENGTH`).
2. **Wire protocol + relay wiring:** `SQL` / `FETCH` / `SQL-CLOSE` → rows /
   `CLOSED`, a `SqlCursorRegistry` beside `NegSessionRegistry`, a dedicated
   `query_only` reader pool, cursor idle timeout and max lifetime (an open
   cursor holds a read transaction and blocks WAL checkpoints), NIP-11
   `limitation.sql`.
3. **Indexed tags.** `tags` is derived from the tag JSON with `json_each`
   (correct, full scan). Add a text tag table (or columns) so tag-driven queries
   can use an index; measure the write/storage cost with relayBench.
4. **Client side:** `INostrClient.sqlQuery()` in `relay/client/accessories/`,
   `amy sql`.
5. **NIP draft:** schema, EBNF of the grammar, function list, DQS-off rule,
   `invalid:` / `unsupported:` / `error:` prefixes, conformance corpus (the fuzzer's
   events + queries + expected rows).
