# Nostr SQL profile: read-only SQL over websockets

Status: **prototype, wired into the relay.** Parser, compiler and cursor are in `quartz/…/nipXXSql/`. `SQL` / `FETCH` / `SQL-CLOSE` are always on in `NostrServer` over a file-backed SQLite store (so geode too), with no settings. No NIP text yet.

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
- Rows are every event the store holds; `EventStoreTableSources` maps both tables onto `event_headers`.

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
  via recursive CTE, joins, long tags, cursor paging, column names.
- `SqlDifferentialFuzzTest` (jvmTest): 3k random queries per run; the compiled
  statement must return the same columns, rows and errors as SQLite running the
  raw text against equivalent views. One-off runs with 2 more seeds × 40k
  queries: 0 mismatches.
- `SqlRelayTest` (jvmTest): SQL/FETCH/SQL-CLOSE through a real `NostrServer`
  session and the production JSON path. Covers paging, params, value types, error
  prefixes, the default page size, the REQ policy gate, in-memory stores, id
  replacement, close/disconnect, and round trips through both serializers.
- geode `NipXXSqlTest`: over a real `ws://` connection.

## Wire protocol

```
C→R  ["SQL", <id>, <sql>, {"params": [..] | {..}, "page": n}?]
R→C  ["SQL-COLS", <id>, [<column>, …]]
R→C  ["SQL-ROWS", <id>, [[<value>, …], …], "more" | "done"]
C→R  ["FETCH", <id>, <maxRows>]
C→R  ["SQL-CLOSE", <id>]
R→C  ["CLOSED", <id>, "<prefix>: <reason>"]
```

- The first page is sent right after `SQL-COLS`, with no round trip. Its size is
  the client's `page`, else the relay's default REQ limit (`RelayLimits.defaultLimit`),
  else every row. After `done`
  the relay has already released the cursor. After `more`, the client either
  `FETCH`es or sends `SQL-CLOSE`.
- A value is a JSON string, a number (integers stay integers), or null.
  SQLite's ±Infinity become the strings `"Inf"` / `"-Inf"`, which is how SQLite
  itself renders them as text.
- `CLOSED` prefixes:
  - `invalid:` the query doesn't parse, or names a table or column that doesn't exist.
  - `unsupported:` outside the profile, or the relay has no SQL.
  - `error:` the engine failed, or the cursor id is unknown.
  - `closed: cursor expired`.
  - Whatever the relay's REQ policy answers (e.g. `auth-required:`).
- Reusing an id replaces the previous cursor, as REQ does.

## Relay wiring

- Always on, no settings. `NostrServer` builds `SqlQueryService.forStore(store)`
  itself; it serves SQL when the store is a file-backed SQLite `EventStore` and
  answers `unsupported` otherwise (an in-memory database can't be opened by a
  second connection).
- `SqlQueryService` hands out **read-only** connections (`SQLITE_OPEN_READONLY`
  plus `PRAGMA query_only`), separate from the store's REQ readers, and reuses
  them when cursors end. Each open cursor owns one.
- `SqlCursorRegistry` is per connection, alongside `NegSessionRegistry`. It steps
  cursors on `Dispatchers.IO`; each cursor has a mutex because a disconnect can
  race a `FETCH`.
- Access follows the relay's normal config: a `SQL` frame goes through the same
  `policy.accept(ReqCmd)` gate a REQ does (as NEG-OPEN already does), so
  `require_auth`, allow/deny lists and id limits apply unchanged.
## No interrupt

androidx.sqlite 2.7.1 does **not** expose `sqlite3_interrupt` or a progress
handler. Checked against the jar: `BundledSQLiteConnection` has only `prepare`,
`inTransaction` and `close`. A statement that is running can't be stopped: a
recursive CTE without a stop condition runs until the client disconnects and
beyond, and a large join takes as long as the data makes it.
## Open items (in order)

1. **Stopping a running statement.** This needs a driver that exposes
   `sqlite3_interrupt` or `sqlite3_progress_handler`: patch or fork the bundled
   driver, or ask upstream. Until then, large joins and aggregates finish in
   their own time. A related open item: nested `replace()` can build large
   strings (up to `SQLITE_MAX_LENGTH`).
2. **Advertising it** in NIP-11 once the NIP has a number.
3. **Indexed tags.** `tags` is derived from the tag JSON with `json_each`
   (correct, full scan). Add a text tag table (or columns) so tag-driven queries
   can use an index; measure the write/storage cost with relayBench.
4. **Client side:** `INostrClient.sqlQuery()` in `relay/client/accessories/`,
   and `amy sql`. The frames already parse on the client (`Message.fromJson`).
5. **NIP draft:** schema, EBNF of the grammar, function list, DQS-off rule,
   `invalid:` / `unsupported:` / `error:` prefixes, conformance corpus (the fuzzer's
   events + queries + expected rows).
