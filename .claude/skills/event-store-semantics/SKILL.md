---
name: event-store-semantics
description: Quartz IEventStore/SQLite store contract (STORE-* rules) and NIP-50 search: filter semantics, replaceable/deletion/expiry writes, counts, FTS, which kinds are SearchableEvent. Use for store parity, geode, or making a kind searchable.
---

# Event store semantics and NIP-50 search

The SQLite `EventStore` ([store-sqlite/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/](https://github.com/vitorpamplona/quartz/tree/main/store-sqlite/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite))
is the reference implementation of a Quartz event store. The filesystem store
(`store-sqlite/src/jvmMain/.../nip01Core/store/fs/`, held to parity by
`store-sqlite/src/jvmTest/.../store/fs/FsParityTest.kt`), geode and external engines (e.g. a
Vespa-backed store) reimplement its observable behavior. Every behavior is a numbered rule
in **`references/store-rules.md`** so a divergence is a lookup. When you change store
behavior, update that rule and its changelog in the same PR.

## Key files (under `nip01Core/store/`)

| Concern | File |
|---|---|
| Public contract (KDoc is normative) | `IEventStore.kt`, `RejectionReason.kt` |
| High-level store (pool + planner) | `sqlite/EventStore.kt`, `sqlite/SQLiteEventStore.kt` |
| Filter → SQL, ordering, limits, counts | `sqlite/QueryBuilder.kt` |
| k-way merge fast path (feed shapes) | `sqlite/MergeQueryExecutor.kt` |
| Schema, tag hashing, immutability | `sqlite/EventIndexesModule.kt`, `sqlite/TagNameValueHasher.kt`, `sqlite/SeedModule.kt` |
| Replaceable / addressable supersession | `sqlite/ReplaceableModule.kt`, `sqlite/AddressableModule.kt` |
| NIP-09 / NIP-40 / NIP-62 / ephemeral | `sqlite/DeletionRequestModule.kt`, `sqlite/ExpirationModule.kt`, `sqlite/RightToVanishModule.kt`, `sqlite/EphemeralModule.kt` |
| NIP-50 FTS | `sqlite/FullTextSearchModule.kt` |
| Index / feature toggles | `sqlite/IndexingStrategy.kt` (client default); geode's `geode/src/main/kotlin/com/vitorpamplona/geode/RelayIndexingStrategy.kt` (relay preset) |
| Operations (concurrency, pragmas, maintenance) | `sqlite/README.md` |

Executable spec: `store-sqlite/src/commonTest/.../nip01Core/store/sqlite/` (`BasicTest`,
`ReplaceableTest`, `AddressableTest`, `DeletionTest`, `ExpirationTest`, `RightToVanishTest`,
`SearchTest`, `SearchRelevanceOrderTest`, `NoFullTextSearchTest`, `MergeQueryCorrectnessTest`,
`TagMergeCorrectnessTest`, `QueryAssemblerTest`, `SnapshotIdsForNegentropyTest`,
`InsertOutcomeClassificationTest`) plus `nip01Core/relay/filters/FilterMatcherTest`. A test
beats a rule; fix the rule.

## Rule index (full text in `references/store-rules.md`)

- **F01** `since`/`until` inclusive · **F02** ids/authors exact 64-hex only, no prefix match ·
  **F03** tag values OR within a name, AND across names; `tagsAll` = every value ·
  **F04** only single ASCII-letter tag names indexed (`isIndexableTagName`) ·
  **F05** `#d` matched on `event_headers.d_tag`, use it only via plain `tags` ·
  **F06** tag matching is 64-bit hash, no post-verify · **F07** multi-filter = UNION, limit
  per filter, no global limit · **F08** `created_at DESC`; same-second order unspecified
  unless `useAndIndexIdOnOrderBy` · **F09** merge fast path returns the same set ·
  **F10** empty filter matches all, but `delete(Filter())` deletes nothing ·
  **F11** empty lists are invalid input · **F12** `limit = 0` → none · **F13**
  `FilterMatcher` is separate and simpler.
- **W01/W02** replaceable/addressable: newest wins, tie → lowest id, stale version rejected
  `REPLACED` (`OK false`) · **W03** ephemeral acked, never stored · **W04** expired rejected
  at insert · **W05** expiry not enforced at query time (sweep) · **W06** gift-wrap owner =
  `p` recipient · **W07** rows immutable · **W08** batch: savepoint per row · **W09** failures
  classified by querying the DB, not driver text.
- **D01** kind-5 by id, same owner, no time check · **D02** by address, `created_at <=` ·
  **D03** cross-author kind-5 inert · **D04** re-insert blocking · **D05** kind-5 can delete a
  kind-5 (known quirk) · **D06/D07** NIP-62 vanish relay-scoped, strict delete / inclusive block
  · **D08** manual deletes create no blocking.
- **C01** count = deduped match set honoring per-filter limits.
- **S01** search extensions stripped before FTS · **S02** bm25 relevance order · **S03** search
  ANDs with structure · **S04** FTS5 `MATCH` grammar · **S05** FTS off → search matches nothing
  · **S06** deferred FTS catches up before a search.
- **N01** negentropy snapshots use query semantics.

Kind classes everywhere: replaceable = 0, 3, 10000–19999; ephemeral = 20000–29999;
addressable = 30000–39999.

## Searchable events (NIP-50 indexing surface)

`nip50Search/SearchableEvent.kt`:

```kotlin
interface SearchableEvent {
    fun indexableContent(): String                                   // write path: stores call it once per insert
    fun forEachIndexableField(visitor: IndexableFieldVisitor) { … }  // read path: allocation-free cache scans (EventSearchMatcher)
    fun indexableSeparator(): String = "\n"                          // what indexableContent joins with
}
```

A kind is searchable iff its class implements this **and** is registered with `EventFactory`
through its Quartz module's `EventKindProvider` (the stores probe by kind). Its search roles
(title, summary, body) come from `indexableFields()`, overridden in the class with
`SearchFieldExtractor.tiers(...)`. The set is `nip50Search/SearchableKinds.kt` `ALL` — 201 kinds today,
one row each in **`references/searchable-kinds.md`** with the exact `indexableContent()`
expression.

Implementation rules:

- Plain text out: human fields joined with `"\n"` (a few metadata kinds use `" "` and override
  `indexableSeparator`). JSON-content kinds (kind 0, stalls, channel info) parse first and join
  the fields, never the raw JSON. Markdown goes in raw.
- Never throw, never null — a throw aborts the insert transaction. Use `?.let { … } ?: ""`.
- Public data only: never index NIP-44 payloads or private tags.
- Kinds that join several fields should override `forEachIndexableField` to visit the same
  fields in the same order (the golden test rejoins them and compares).
- Eligibility: human-authored text (titles, bodies, names, descriptions). Reactions, follow
  lists, relay lists and zaps (minus the receipt's comment) stay out.

**MANDATORY when you add/remove `SearchableEvent` or change an `indexableContent()` body:**

1. Update `references/searchable-kinds.md` in the same PR — external search engines mirror it
   at pin bumps.
2. List the kind in `SearchableKinds.ALL` (`SearchableKindsTest` sweeps kinds 0–65535 through
   `EventFactory` and fails otherwise) and register the class in its module's
   `EventKindProvider`, or
   `FullTextSearchModule.isSearchableKind` and the reindex scan never see it.
3. Regenerate [test-resources/indexable-content.golden](https://github.com/vitorpamplona/quartz/blob/main/test-resources/indexable-content.golden)
   (`IndexableContentGoldenTest`).
4. Existing databases don't reindex themselves: old rows keep their text until
   `IEventStore.reindexFullTextSearch()` runs (its KDoc is the contract); app-side, schedule the
   resumable overload after shipping.

Surprises: kind 9735 indexes the embedded zap request's comment; kinds 0/31990 index many
profile fields space-joined; kind 30063 indexes release notes only for NIP-82 releases (NIP-51
`content` may be encrypted); poll kinds append each option label.

## How the stores index

- **SQLite** (`FullTextSearchModule.kt`): contentless FTS5 table `event_fts`, `rowid` =
  `event_headers.row_id`, kept in sync by a delete trigger; default `unicode61` tokenizer;
  queries are always a bound `event_fts MATCH ?`, ordered by bm25 `rank` then `created_at DESC`
  (rules S01–S06).
- **Filesystem** (`jvmMain/.../store/fs/FsIndexer.kt`, `FsSearchTokenizer.kt`): tokenizes
  `indexableContent()` itself, approximating `unicode61`; the same tokenizer runs on queries.

## NIP-50 client side

- `nip50Search/SearchQuery.kt` — `SearchQuery.parse(search)` splits `terms` from
  `extensions`. A token is an extension iff it looks like `lowercasekey:value` with the value not
  starting with `//` (URLs stay text); duplicate keys keep the last; unknown keys are kept
  (`extension(key)`). Typed: `includeSpam`, `domain`, `language`, `sentiment`, `nsfw`.
  `stripExtensions()` / `Filter.strippingSearchExtensions()` is what the built-in stores apply:
  unsupported extensions are ignored, so an extensions-only search is unconstrained. A store
  that implements extensions gets the raw string and should parse it with `SearchQuery.parse`.
- `SearchRelayListEvent` (`nip50Search/SearchRelayListEvent.kt`) — **kind 10007**, the user's
  search relays (public + NIP-44 private tags; not itself searchable). Used by
  `commons/.../commons/actions/SearchActions.kt`; defaults in
  `commons/.../commons/account/AccountBootstrapEvents.kt`.
- Not the same thing: `commons/.../commons/search/SearchQuery.kt` is an app-level local-feed
  query model.

Search tests: `commonTest/.../nip50Search/SearchQueryTest.kt`, `SearchableKindsTest.kt`,
`jvmTest/.../nip50Search/IndexableContentGoldenTest.kt`, the sqlite `SearchTest` /
`SearchRelevanceOrderTest` / `NoFullTextSearchTest`, and `jvmTest/.../store/fs/FsSearchTest.kt`.

## For parity implementers

Cite rule ids in divergence notes ("diverges from STORE-D05: we no-op deletion-of-a-deletion").
F06 (hash-based tags) and F08 (unordered same-second ties) are where the reference itself has
slack, so a byte-for-byte diff there may not be your bug.
