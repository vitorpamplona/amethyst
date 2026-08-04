---
name: event-store-semantics
description: The authoritative behavioral contract of Quartz's event stores — `IEventStore` and its reference SQLite implementation (`nip01Core/store/sqlite/`). Use when implementing or asserting parity with a Quartz event store (external engines like Vespa, the filesystem store, geode), answering filter-semantics questions (since/until inclusivity, tag OR/AND, multi-filter limits, ordering tiebreaks), or working on the write-path rules for replaceable/addressable supersession, NIP-09 deletions, NIP-40 expiration, NIP-62 vanish, NIP-45 counts, or NIP-50 search inside the store. Every behavior has a named rule id (STORE-Fxx/Wxx/Dxx/Sxx/Cxx) so downstream implementations can annotate divergences precisely.
---

# Event Store Semantics — the `IEventStore` / SQLite-store contract

The SQLite `EventStore` (`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/`)
is the de-facto **reference implementation** of what a Quartz event store must do. Other
implementations — the in-repo filesystem store (`nip01Core/store/fs/`, held to parity by
`quartz/src/jvmTest/.../store/fs/FsParityTest.kt`) and external engines (e.g. a Vespa-backed
store) — reimplement its *observable behavior* and assert parity in CI. This skill states that
behavior as **named, numbered decisions** so a parity divergence becomes a lookup, not an
archaeology session through `QueryBuilder`/`MergeQueryExecutor`.

Every rule below was verified against the code as of this skill's last update. When you change
store behavior, **update the rule here in the same PR** and add a line to the
[Semantics changelog](#semantics-changelog) — downstream implementations pin Quartz by commit and
review pin bumps against this file.

## Key files

| Concern | File |
|---|---|
| Public contract (KDoc is normative) | `nip01Core/store/IEventStore.kt` |
| High-level store (owns pool + planner) | `sqlite/EventStore.kt`, `sqlite/SQLiteEventStore.kt` |
| Filter → SQL, ordering, limits, counts | `sqlite/QueryBuilder.kt` |
| k-way merge fast path (feed shapes) | `sqlite/MergeQueryExecutor.kt` |
| Schema, tag hashing, immutability | `sqlite/EventIndexesModule.kt`, `sqlite/TagNameValueHasher.kt`, `sqlite/SeedModule.kt` |
| Replaceable / addressable supersession | `sqlite/ReplaceableModule.kt`, `sqlite/AddressableModule.kt` |
| NIP-09 / NIP-40 / NIP-62 / ephemeral | `sqlite/DeletionRequestModule.kt`, `sqlite/ExpirationModule.kt`, `sqlite/RightToVanishModule.kt`, `sqlite/EphemeralModule.kt` |
| NIP-50 FTS | `sqlite/FullTextSearchModule.kt` (see also the `searchable-events` skill) |
| Index/feature toggles | `sqlite/IndexingStrategy.kt` (client default) and geode's `RelayIndexingStrategy.kt` (relay preset) |
| Operational README | `sqlite/README.md` (concurrency, pragmas, maintenance) |

Executable spec: the test suites in
`quartz/src/commonTest/.../store/sqlite/` (`BasicTest`, `ReplaceableTest`, `AddressableTest`,
`DeletionTest`, `ExpirationTest`, `RightToVanishTest`, `SearchTest`, `SearchRelevanceOrderTest`,
`MergeQueryCorrectnessTest`, `TagMergeCorrectnessTest`, `QueryAssemblerTest`,
`SnapshotIdsForNegentropyTest`, `FilterMatcherTest`, …). If a rule here ever contradicts a test,
the test wins — and this file has a bug to fix.

## Kind classes (used throughout)

- **Replaceable**: kind `0`, kind `3`, and `10000 ≤ kind < 20000`.
- **Ephemeral**: `20000 ≤ kind < 30000`.
- **Addressable**: `30000 ≤ kind < 40000`.
- Everything else is a regular event.

---

## Filter matching (STORE-F)

**STORE-F01 — `since`/`until` are both inclusive.** `since` compiles to
`created_at >= ?`, `until` to `created_at <= ?` (`QueryBuilder` uses
`greaterThanOrEquals`/`lessThanOrEquals` everywhere). An event with
`created_at == since == until` matches.

**STORE-F02 — `ids` and `authors` are exact-match only.** They compile to `=`/`IN` against the
full 64-char hex columns. **NIP-01 prefix matching is NOT supported** anywhere in the store.
(`Filter`'s constructor logs an error for non-64-char ids/authors but still sends them; they
simply never match.)

**STORE-F03 — tag filter combination.** Within one tag name, values are **OR**
(`tag_hash IN (…)`). Across different tag names in the same filter, conditions are **AND**
(each extra name becomes another `event_tags` self-join). `tagsAll` (NIP-91 `&x` syntax) demands
**every listed value** be present on the event — one join + equality per value — and composes by
AND with any plain `tags` in the same filter.

**STORE-F04 — only single-letter tag names are indexed (by default).**
`DefaultIndexingStrategy.shouldIndex` indexes a tag iff `tag.size >= 2 && tag[0].length == 1`.
A filter on a multi-letter tag name (`#title`, `#alt`) matches **nothing** in the SQLite store.
Deployments can widen `shouldIndex`, but the stock contract is single-letter-only.

**STORE-F05 — `d` is special-cased out of the tag index.** `#d` values are matched against the
`event_headers.d_tag` column, not `event_tags` (`Filter.toFilterWithDTags()`). Consequences:
`#d` works on addressable events (which populate `d_tag`); when all `kinds` are addressable the
query adds `kind >= 30000 AND kind < 40000` to pin the addressable index. **Only use `#d` via
plain `tags`.** A `#d` under `tagsAll` is handled inconsistently: on the simple (no other
tags/search) path it degrades to OR semantics (`toFilterWithDTags` folds it into `dTags`), and
when `tags["d"]` is also present it is dropped entirely; on the tag-join path it is ignored.
(An event has one d-tag, so AND-across-values could never match anyway.)

**STORE-F06 — tag and author matching in the tag path is hash-based.** `event_tags` stores a
64-bit MurmurHash3 of `(tag name, value)` keyed by a per-database random seed (`SeedModule`,
`TagNameValueHasher`); the p/e/a-owner columns are hashes too. There is **no post-verification**
of hash matches, so a hash collision would return a false positive. Probability is negligible in
practice but nonzero — a parity harness comparing against an exact-match engine should know this
is the one place the reference can (theoretically) over-match.

**STORE-F07 — multiple filters are a union with dedup; `limit` is per-filter.** Each filter
becomes its own row-id subquery with its **own** `ORDER BY … LIMIT`; branches are combined with
SQL `UNION` (dedup by row). There is **no global limit** — a 3-filter query with limits
10/20/30 can return up to 60 events, presented in one merged `created_at DESC` ordering. NIP-45
counts and negentropy snapshots dedup the same way (`SELECT DISTINCT` / `UNION`).

**STORE-F08 — result ordering.** Non-search queries order `created_at DESC`. The `id ASC`
tiebreak on equal `created_at` is applied **only when
`IndexingStrategy.useAndIndexIdOnOrderBy = true`** — which is `false` in the client default
**and** in geode's relay preset. So by default, same-second ordering is unspecified (SQLite
returns them in storage order). Any newest-N is valid; a parity suite must not assert
same-`created_at` order unless it configures the flag. One extra caveat with the flag ON: the
`MergeQueryExecutor` tag-stream path still yields same-second ties in rowid order (its cursors
run off `event_tags`, which has no id column) — a valid newest-N that may differ byte-for-byte
from the single-SQL ordering.

**STORE-F09 — the merge fast path returns the same *set*.** Single-filter queries of the shape
"authors (+kinds) + limit" or "one `#x` IN-list (+kinds) + limit" (≤2048 streams) route through
`MergeQueryExecutor`, a k-way newest-first merge over per-(kind,author) / per-(tag-value,kind)
index cursors with dedup by id on the tag shape. This is an optimization, not a semantics
change — `MergeQueryCorrectnessTest`/`TagMergeCorrectnessTest` assert set-equality with the
single-SQL plan (ordering caveat per STORE-F08).

**STORE-F10 — empty filter.** `query(Filter())` / `count(Filter())` match **everything**
(`Filter.isEmpty()` → the "everything" query). `delete(Filter())` is deliberately asymmetric:
it deletes **nothing** and returns 0, so a stray empty filter can't wipe the store (documented
on `QueryBuilder.delete`).

**STORE-F11 — empty lists (`kinds = emptyList()` etc.) are a client error with inconsistent
handling; don't rely on either outcome.** On the single-filter simple path an empty list
renders as `1 = 0` → matches nothing. But `Filter.isEmpty()` treats empty lists the same as
`null`, so on the multi-filter union path such a filter contributes no subquery — and a list of
*only* empty-list filters degrades to the match-everything query. Known quirk; treat
empty-list filters as invalid input rather than replicating this shape.

**STORE-F12 — `limit` edge cases.** `limit = 0` compiles to `LIMIT 0` → zero rows.
`limit = null` means unbounded. Negative limits are not defended against (don't send them).

**STORE-F13 — the in-memory matcher is a separate (simpler) implementation.**
`Filter.match(event)` (`FilterMatcher`) is used for live-stream matching, not storage queries;
it checks ids/authors/kinds/tags/tagsAll/since/until but not `search` or `limit`. Parity work
targets the SQL semantics above, not `FilterMatcher`.

---

## Write path (STORE-W)

Inserts run every module in one transaction: header+tags → NIP-09 side effects → expiration
row → FTS row → vanish side effects. A trigger `RAISE(ABORT, …)` rejects the whole row with the
messages quoted below (they surface as the NIP-01 `OK false` reason).

**STORE-W01 — replaceable supersession.** Unique index on `(kind, pubkey)` for replaceable
kinds. A `BEFORE INSERT` trigger deletes any stored version that is *older* — meaning
`created_at` smaller, **or equal `created_at` with lexicographically larger id** (NIP-01
lowest-id-wins). Inserting a version that is *not* newer under that ordering leaves the stored
row in place and fails the unique index → rejected (`UNIQUE constraint failed`). Net contract:
exactly one version stored; newest wins; ties broken by lowest id; older re-inserts blocked.

**STORE-W02 — addressable supersession.** Same as W01 with unique index
`(kind, pubkey, d_tag)` over `30000 ≤ kind < 40000`. Nuance: `d_tag` is populated from the
*parsed* event class (`AddressableEvent.dTag()`); an addressable-range kind whose class doesn't
parse as `AddressableEvent` stores `d_tag NULL`, and SQLite treats NULLs as distinct in unique
indexes — such events don't supersede each other. An event with no `d` tag parses as `dTag() = ""`
(empty string), which *does* dedupe normally.

**STORE-W03 — ephemeral events are never stored but are acked as accepted.**
`insert()` returns silently and `batchInsert` reports `Accepted` for `20000 ≤ kind < 30000`
without writing (the live relay stream still broadcasts them). A DB-level backstop trigger
(`blocked: cannot store ephemeral events`) rejects any that sneak past the app-level check.

**STORE-W04 — expired events are rejected at insert.** App-level check
(`event.isExpired()`) plus a trigger on the expiration-row insert
(`blocked: this event is expired` when `expiration <= unixepoch()`). Single-event `insert`
**throws**; `batchInsert` returns `Rejected`.

**STORE-W05 — expiry is enforced at insert and by sweep, NOT at query time.** Events with a
future `expiration` store a row in `event_expirations`. Nothing filters them out of queries
after the timestamp passes: **a query between expiry and the next `deleteExpiredEvents()` sweep
returns the expired event.** Operators run the sweep periodically (README recommends ~15 min).
Re-inserting an already-expired event after the sweep is rejected per W04.

**STORE-W06 — GiftWrap ownership is the recipient.** For kind 1059 the store computes
`pubkey_owner_hash` from the `p`-tag recipient (falling back to the random signer key if
absent). All owner-scoped machinery — NIP-09 re-insert blocking, NIP-62 vanish deletion and
blocking — operates on that owner hash, so **a user's deletions/vanish remove giftwraps
addressed to them**, even though the wrap's `pubkey` is a one-time key. (Consequently GiftWraps
are also excluded from `authorsMissingOutbox()`.)

**STORE-W07 — immutability.** `event_headers`/`event_tags` rows are never updated
(`BEFORE UPDATE` triggers abort). All supersession is delete + insert; `event_tags`,
`event_expirations`, `event_vanish`, and the FTS row follow the header by
`ON DELETE CASCADE` / trigger.

**STORE-W08 — batch insert.** One outer transaction, one SAVEPOINT per row: a bad row rolls
back alone and reports `Rejected(reason)`; the rest commit. If the **outer commit** fails, every
entry is treated as `Rejected` (the `IEventStore.batchInsert` contract). Outcomes are returned
in input order; OK frames pair by event id, not order.

---

## Deletion lifecycle — NIP-09 / NIP-62 (STORE-D)

**STORE-D01 — delete by id.** A kind-5's `e` tags delete stored events with those ids **whose
owner is the kind-5's author** (`pubkey_owner_hash` match — recipient for giftwraps per W06).
The id path has **no timestamp condition**: it deletes the target regardless of the relative
`created_at` values.

**STORE-D02 — delete by address.** A kind-5's `a` tags delete events at that
`(kind, pubkey, d_tag)` coordinate with `created_at <= deletion.created_at` — **inclusive**; a
version newer than the deletion survives. Only coordinates whose pubkey equals the kind-5's
author are honored. Replaceable coordinates (`kind:pubkey:` with no d-tag) get the same
`created_at <=` treatment against `(kind, pubkey)`.

**STORE-D03 — cross-author kind-5s are stored but inert.** A deletion naming someone else's
events is inserted like any regular event (it may be useful to other relays/clients) but its
delete pass removes zero rows and creates no blocking.

**STORE-D04 — re-insert blocking.** A `BEFORE INSERT` trigger rejects
(`blocked: a deletion event exists`) any event whose id (`e`-hash) **or** address (`a`-hash) is
named by a stored kind-5 from the same owner with `deletion.created_at >= event.created_at`.
Note the asymmetry with D01: a *backdated* id-deletion (older `created_at` than its target)
still deletes on arrival, but would not block a later re-insert.

**STORE-D05 — a kind-5 CAN delete another kind-5, and doing so un-blocks its targets.**
Nothing excludes kind 5 from the id path (D01). Deleting a deletion removes its tombstone rows
from `event_tags`, so events it had deleted become re-insertable. **Status: known quirk, not a
considered decision.** NIP-09 leaves it open; at least one external implementation
(vespa-eventstore) deliberately diverges by treating deletion-of-a-deletion as a no-op, which is
the safer reading (tombstones shouldn't be revocable). If you change this, update this rule and
the changelog — parity suites key off it.

**STORE-D06 — NIP-62 vanish is relay-scoped.** A kind-62 only cascades when
`shouldVanishFrom(relay)` — its `relay` tags name this store's `relay` URL or `ALL_RELAYS`.
(A store constructed with `relay = null` matches only `ALL_RELAYS` requests.) Out-of-scope
vanish events are stored as regular events with no side effects.

**STORE-D07 — vanish scope and horizon.** An in-scope vanish deletes every event whose
**owner** (W06) is the vanishing pubkey with `created_at < vanish.created_at` (strict — the
vanish event itself survives), and blocks inserts of owned events with
`created_at <= vanish.created_at` (`blocked: a request to vanish event exists`; note blocking is
inclusive where deletion is strict). Newer vanish requests supersede older ones per pubkey
(unique on `pubkey_hash`).

**STORE-D08 — manual deletes.** `delete(id)` removes one row unconditionally (no blocking
created). `delete(filter)` deletes matching rows honoring per-filter limits, with the F10
empty-filter no-op guard. Neither creates re-insert blocking — only stored kind-5/kind-62
events do that.

---

## NIP-45 count (STORE-C)

**STORE-C01 — count = size of the deduped match set, honoring per-filter limits.** Single
filter: `COUNT(*)` over that filter's row-id subquery (including its `LIMIT`, so
`count(Filter(kinds=…, limit=10))` is at most 10). Multiple filters: branches are `UNION`ed
(dedup) **before** counting — an event matching several filters counts once. FTS-off + search
term → 0 (F-series search rules apply).

---

## NIP-50 search inside the store (STORE-S)

The indexing surface (which kinds are searchable, what text they contribute) is the
`searchable-events` skill; these rules are the store's query-side contract.

**STORE-S01 — extension stripping at the store boundary.** Every filter-accepting method runs
`strippingSearchExtensions()`: NIP-50 `key:value` tokens (`include:spam`, `domain:…`, …) are
removed before FTS. Unsupported extensions are **ignored, never matched as literal text and
never match-nothing** — an extensions-only search collapses to an unconstrained query. Stores
that *do* implement extensions receive the raw string through the relay layer and parse it with
`nip50Search.SearchQuery.parse` (see the `IEventStore` KDoc).

**STORE-S02 — relevance ordering.** Search results order by FTS5 `bm25` rank (best match
first), with `created_at DESC` only as tiebreak; the `LIMIT` keeps the most *relevant* N, not
the newest N. A multi-filter REQ is relevance-ordered only when **every** filter carries a
search term (best/min rank per event across branches); mixing search and non-search filters
falls back to `created_at DESC`.

**STORE-S03 — search combines by AND with the structural parts** (ids/authors/kinds/tags/
since/until) of the same filter — an FTS `MATCH` join on top of the normal conditions.

**STORE-S04 — search grammar is SQLite FTS5 `MATCH`.** The raw (post-strip) string is passed to
FTS5, so implicit-AND terms, `"phrase queries"`, `OR`, and `prefix*` follow FTS5 semantics.
Tokenization details live in `FullTextSearchModule` (see `searchable-events`).

**STORE-S05 — FTS off.** With `IndexingStrategy.indexFullTextSearch = false`: a filter with a
non-empty search term matches **nothing** (query/count/delete alike); an empty-string search
imposes no constraint. Everything else is unchanged.

**STORE-S06 — deferred FTS.** Relays may set `deferFullTextSearchIndexing = true` (geode does):
tokenization moves off the insert path to a watermark-driven catch-up
(`needsFtsCatchUp`/`ftsCatchUp`), and search queries drain the backlog first — so NIP-50
results are exactly as fresh as the synchronous path.

---

## Negentropy / NIP-77 (STORE-N)

**STORE-N01 —** `snapshotIdsForNegentropy(filters)` returns `(created_at, id)` pairs under the
**same filter semantics as `query`** (per-filter limits included, multi-filter dedup), order
unspecified (negentropy re-sorts). `maxEntries` returns up to `maxEntries + 1` as an overflow
sentinel. `liveNegentropySnapshot` serves full-corpus NEG-OPENs from an in-memory index when
`maintainLiveNegentropyIndex` is on; the delta plumbing in `SQLiteEventStore` keeps it exact
across replaceable displacement, kind-5s, and vanish (invalidate-and-rebuild for the
non-itemizable cases).

---

## Configuration presets

- **Client default** (`DefaultIndexingStrategy()`): FTS on (synchronous), optional indexes off,
  `useAndIndexIdOnOrderBy` off, no live negentropy index.
- **Relay preset** (geode's `relayIndexingStrategy()`): adds created_at-alone, pubkey-alone and
  tag+kind+pubkey indexes, defers FTS, maintains the live negentropy index — still leaves
  `useAndIndexIdOnOrderBy` off.
- Flag-gated indexes are runtime config, not schema: flipping one on an existing DB builds the
  index on next open (`ensureOptionalIndexes`), no migration.

## For parity implementers

- Treat the rule ids above as the vocabulary for divergence notes
  (e.g. "diverges from STORE-D05: we no-op deletion-of-a-deletion").
- The commonTest suites are the executable spec; `FsParityTest` shows the in-repo pattern for
  holding a second engine to it.
- Remember F06 (hash-based tag matching) and F08 (unordered same-second ties by default) when
  diffing results byte-for-byte — both are places where a "divergence" may be the reference's
  own slack, not your bug.

## Semantics changelog

Add one line per behavior change, newest first: `YYYY-MM-DD <short sha> <rule id> — what changed`.

- 2026-08-04 (baseline) — rules F01–F13, W01–W08, D01–D08, C01, S01–S06, N01 written from the
  code at the time this skill was introduced. Changes before this date are not itemized;
  archaeology starts at `git log` on `nip01Core/store/`.
