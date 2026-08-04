---
name: searchable-events
description: The NIP-50 indexing surface of Quartz — the `SearchableEvent` interface, which event kinds are searchable, exactly what text each kind's `indexableContent()` contributes, how the SQLite/filesystem stores consume it, and the NIP-50 `SearchQuery` extension grammar plus `SearchRelayListEvent` (kind 10007). Use when making a kind searchable, changing what a kind indexes, diffing the searchable set at a Quartz version bump (external search engines mirror this table), debugging why an event is or isn't found by search, or working with search extensions (`include:spam`, `domain:`, …).
---

# Searchable Events — the NIP-50 indexing surface

## The contract

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip50Search/SearchableEvent.kt`:

```kotlin
interface SearchableEvent {
    fun indexableContent(): String
}
```

One method; marker and extractor in one. An event kind is searchable **iff** its event class
implements this interface **and** the class is wired into `EventFactory` (the stores probe
searchability by kind through `EventFactory.create` — an unwired implementor is invisible).

Rules every implementation follows (keep them when adding one):

- **Plain text out.** Return the human-meaningful fields joined with `"\n"` (a handful of
  metadata-ish kinds use `" "`); no markup stripping is performed — markdown/asciidoc content
  goes in raw, JSON-content kinds (kind 0 metadata, marketplace stalls, channel info) **parse
  first and join the extracted fields**, never the raw JSON.
- **Never throw, never null.** There is no defensive wrapper at any call site; a throw aborts
  the insert transaction. Parsed-JSON implementations use `?.let { … } ?: ""`.
- **Only public data.** Encrypted content stays out (e.g. kind 30382 contact cards index only
  the public petname/summary/topics, never the NIP-44 payload).
- Typical shapes: `content` alone (~33 kinds); `listOfNotNull(title(), content)`;
  `listOfNotNull(title(), summary(), content)`; lists index `title() + description()`.

## The full kind table

**`references/searchable-kinds.md`** in this skill holds the authoritative table — every
implementor with its kind number, class, and the exact `indexableContent()` expression
(126 concrete classes / 129 kind values as of 2026-08). Diff that file at a version bump to
answer "did the searchable set or any kind's indexed text change?".

Notables that surprise people:

- **Kind 9735 (zap receipt) indexes the embedded zap request's content**
  (`zapRequest?.content.orEmpty()`) — receipts are searchable by the zapper's comment.
- **Kind 0 / 31990** index many profile fields space-joined (name, about, nip05, lud16,
  website, picture URL, …).
- **Kind 30063 is claimed twice** (`ReleaseArtifactSetEvent` in nip51Lists and the experimental
  `SoftwareReleaseEvent`); `EventFactory` resolves 30063 to `ReleaseArtifactSetEvent`, so
  `title()\ndescription()` is what actually gets indexed — `SoftwareReleaseEvent.indexableContent()`
  is dead on the store path.
- Poll kinds (1068, 6969) append each option label on its own line.

## MANDATORY maintenance when you touch this surface

Adding `SearchableEvent` to a kind, removing it, or changing any `indexableContent()` body:

1. **Update `references/searchable-kinds.md`** in the same PR (external search engines — e.g.
   the Vespa-backed store's `SearchExtractors` — mirror this table at pin bumps; a silent
   change ships them stale search results).
2. **Remember existing databases don't reindex themselves.** Old rows keep their old (or
   missing) FTS text until `IEventStore.reindexFullTextSearch()` runs — the KDoc on that method
   is the contract. App-side, schedule the resumable overload after shipping such a change.
3. New implementors must be **registered in `EventFactory`** or the reindex scan and kind
   pre-filter (`FullTextSearchModule.isSearchableKind`) will never see them.

Eligibility policy: a kind becomes searchable when it carries human-authored, human-meaningful
text (titles, bodies, names, descriptions). Pure-machine kinds (reactions, follow lists, zaps
minus their comment, relay lists) stay out to keep the index small.

## How the stores consume it

**SQLite** (`nip01Core/store/sqlite/FullTextSearchModule.kt`):
`CREATE VIRTUAL TABLE event_fts USING fts5(content, content='', contentless_delete=1)` —
contentless, `rowid` = `event_headers.row_id`, an `AFTER DELETE` trigger keeps it in sync. On
insert (when FTS is on and not deferred): `if (event is SearchableEvent)` → bind
`event.indexableContent()` — the only method ever called. Tokenization is entirely SQLite's
default FTS5 `unicode61`; queries are always a bound `event_fts MATCH ?` (never concatenated),
ordered by bm25 `rank` then `created_at DESC`. Query-side semantics (relevance ordering,
extension stripping, FTS-off behavior, deferred catch-up) are rules STORE-S01…S06 in the
`event-store-semantics` skill.

**Filesystem store** (`jvmMain/.../store/fs/FsIndexer.kt` + `FsSearchTokenizer.kt`): tokenizes
`indexableContent()` itself, approximating `unicode61` (split on non-letter/digit, lowercase);
the same tokenizer runs on queries so drift cancels.

## NIP-50 client side

**`SearchQuery`** (`nip50Search/SearchQuery.kt`) — typed parse of the `search` filter string
into `terms` + `extensions`. A whitespace token is an extension iff it looks like
`lowercasekey:value` (the value not starting with `//`, so URLs stay free text); duplicate keys
keep the last; unknown extensions are preserved (`extension(key)`). Typed accessors:
`includeSpam`, `domain`, `language`, `sentiment`, `nsfw`. `stripExtensions()` /
`Filter.strippingSearchExtensions()` is the bridge the built-in stores use — unsupported
extensions are **ignored** (NIP-50), so an extensions-only search collapses to an unconstrained
query, never match-nothing. A server-side store that implements its own extensions
(`observer:`, `sort:rank`, …) receives the raw string (see the `IEventStore` KDoc) and should
parse with `SearchQuery.parse` so its syntax stays compatible with what clients send.

**`SearchRelayListEvent`** — **kind 10007**, the user's search-relay list (NIP-51-style, public
tags + NIP-44 private tags; *not* a `SearchableEvent` itself). Client consumption:
`commons/.../actions/SearchActions.kt`, bootstrap defaults in
`commons/.../account/AccountBootstrapEvents.kt`.

Don't confuse it with `commons/.../commons/search/SearchQuery.kt` — an app-level local-feed
query model (authors/kinds/hashtags/or-terms), unrelated to the NIP-50 wire string.

## Tests (executable spec)

- `commonTest/.../nip50Search/SearchQueryTest.kt` — the extension grammar, token by token.
- `commonTest/.../store/sqlite/SearchTest.kt` — per-kind indexing (kind 0 profile fields,
  40/41 channel JSON, 31924/30617), extension-token ignoring, reindex/resumable-reindex,
  FTS cleanup on replaceable rotation.
- `commonTest/.../store/sqlite/SearchRelevanceOrderTest.kt` — bm25-before-recency ordering,
  limit-after-score, multi-filter rank union.
- `commonTest/.../store/sqlite/NoFullTextSearchTest.kt` — FTS-off contract.
- `jvmTest/.../store/fs/FsSearchTest.kt` — tokenizer parity for the filesystem store.
