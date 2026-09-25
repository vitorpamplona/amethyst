# Nostr SQL profile: read-only SQL over websockets

Status: **prototype, wired into the relay.** Parser, compiler and cursor are in `quartz/…/nipXXSql/`. `SQL` / `FETCH` / `SQL-CLOSE` run through `NostrServer`, and geode has an opt-in `[sql]` section. No NIP text yet.

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
- `SqlRelayTest` (jvmTest): SQL/FETCH/SQL-CLOSE through a real `NostrServer`
  session and the production JSON path. Covers paging, params, value types, error
  prefixes, the recursion bound, hidden kinds, the policy gate, relays without
  SQL, pool sharing and release on disconnect, the per-connection cap and id
  replacement, idle expiry, and round trips through both serializers.
- geode `NipXXSqlTest`: over a real `ws://` connection, plus `[sql]` config parsing.

## Wire protocol

```
C→R  ["SQL", <id>, <sql>, {"params": [..] | {..}, "page": n}?]
R→C  ["SQL-COLS", <id>, [<column>, …]]
R→C  ["SQL-ROWS", <id>, [[<value>, …], …], "more" | "done"]
C→R  ["FETCH", <id>, <maxRows>]
C→R  ["SQL-CLOSE", <id>]
R→C  ["CLOSED", <id>, "<prefix>: <reason>"]
```

- The first page is sent right after `SQL-COLS`, with no round trip. After `done`
  the relay has already released the cursor. After `more`, the client either
  `FETCH`es or sends `SQL-CLOSE`.
- A value is a JSON string, a number (integers stay integers), or null.
  SQLite's ±Infinity become the strings `"Inf"` / `"-Inf"`, which is how SQLite
  itself renders them as text.
- `CLOSED` prefixes:
  - `invalid:` the query doesn't parse, or names a table or column that doesn't exist.
  - `unsupported:` outside the profile, or the relay has no SQL.
  - `blocked:` a pool or per-connection cap was hit.
  - `error:` the engine failed, or the cursor id is unknown.
  - `closed: cursor expired`.
  - The policy may also return its own reason (e.g. `auth-required:`).
- Reusing an id replaces the previous cursor, as REQ does.

## Relay wiring

- `NostrServer(sql = SqlQueryService.forStore(store))` enables it. Leaving `sql`
  as null answers every SQL frame `unsupported`.
- `SqlQueryService` holds a separate pool of **read-only** connections
  (`SQLITE_OPEN_READONLY` plus `PRAGMA query_only`). Each open cursor owns one
  connection. A slow query can therefore block other SQL cursors, but never REQs.
- `SqlCursorRegistry` is per connection, alongside `NegSessionRegistry`. It steps
  cursors on `Dispatchers.IO`, cuts a page short when its time budget runs out,
  and expires cursors that sit idle or live too long. Each cursor has its own
  mutex, because the expiry timer can race a `FETCH`.
- `IRelayPolicy.acceptSql` is only a gate on opening a cursor. What a query can
  see comes from the service's per-session `SqlTableSources`. REQ policies do
  **not** apply to SQL, so anything they hide has to be hidden in the table
  sources too. `SqlAccessPolicy` gates SQL on NIP-42 auth or a pubkey allowlist.
- geode's `[sql]` section: `enabled` (default false), `require_auth`,
  `allowed_pubkeys`, `hidden_kinds` (default `[1059]`), plus the cursor and page
  limits. It fails at boot if the database isn't a SQLite file.

## Bounding work (there is no interrupt)

androidx.sqlite 2.7.1 does **not** expose `sqlite3_interrupt` or a progress
handler. Checked against the jar: `BundledSQLiteConnection` has only `prepare`,
`inTransaction` and `close`. So nothing can stop a statement that is running.
What bounds the work instead:

- **Recursion.** This is the only way a SELECT can run forever. Every recursive
  CTE must end with `LIMIT <integer literal> ≤ maxRecursiveRows`. SQLite stops
  adding rows to a recursive table when it reaches its LIMIT (verified). The
  compiler detects self-references and enforces the rule.
- **Everything else finishes**, but it can take as long as the data is large
  (e.g. a 3-way cross join). What limits it: a dedicated pool (it can't starve
  REQs), a per-connection cursor cap, the page time budget (applied between rows
  only), the idle and lifetime timeouts, and the policy gate. A truly
  interruptible engine needs a driver patch; see open items.

## Open items (in order)

1. **Stopping a running statement.** This needs a driver that exposes
   `sqlite3_interrupt` or `sqlite3_progress_handler`: patch or fork the bundled
   driver, or ask upstream. Until then, large joins and aggregates finish in
   their own time. A related open item: nested `replace()` can build large
   strings (up to `SQLITE_MAX_LENGTH`).
2. **Advertising it:** a NIP-11 `limitation.sql` block (page and cursor limits,
   `max_recursive_rows`) once the NIP has a number.
3. **Indexed tags.** `tags` is derived from the tag JSON with `json_each`
   (correct, full scan). Add a text tag table (or columns) so tag-driven queries
   can use an index; measure the write/storage cost with relayBench.
4. **Client side:** `INostrClient.sqlQuery()` in `relay/client/accessories/`,
   and `amy sql`. The frames already parse on the client (`Message.fromJson`).
5. **NIP draft:** schema, EBNF of the grammar, function list, DQS-off rule,
   `invalid:` / `unsupported:` / `error:` prefixes, conformance corpus (the fuzzer's
   events + queries + expected rows).
