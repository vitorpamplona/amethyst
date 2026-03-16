# Brainstorm: Advanced Search for Desktop

**Date:** 2026-03-10
**Status:** Draft
**Branch:** TBD (`feat/desktop-advanced-search`)

## What We're Building

Full-featured advanced search for Amethyst Desktop with:
1. **Twitter-style query operators** (`from:`, `kind:`, `since:`, etc.) that map to NIP-50 Filter fields
2. **Form-based UI** (expandable panel below search bar) for users who don't want to learn syntax
3. **Bidirectional sync** between text operators and form controls — editing one updates the other
4. **Extensible kind presets** — toggle groups like Notes, Articles, Media, Communities
5. **Future AI bridge** (follow-up) — natural language → structured query conversion

**Philosophy:** Relay-first, pragmatic. Desktop users have resources; we prioritize completeness over privacy. Privacy controls available but not default friction.

## Why This Approach

- **No standard Nostr query language exists** — we define one that maps cleanly to `Filter` fields
- **Dual interface (text + form)** — power users get speed, casual users get discoverability
- **Current desktop search is minimal** — only kind 0 + 1, no operators, no kind filtering
- **Android has 30+ kinds** but no query language — desktop leapfrogs with both
- **AI deferred** — core query system must work standalone first; AI is a parsing layer on top

## How Other Clients Do Search

| Client | Approach | Operators | Notable |
|--------|----------|-----------|---------|
| **Amethyst Android** | Local cache + NIP-50, 30+ kinds | None (plain text) | Search relay list (kind 10007) |
| **Primal** | Proprietary caching server | Form UI only | Event type, time range, scope dropdowns |
| **Coracle** | NIP-50 + configurable relays | None | Also supports DVM requests (NIP-90) |
| **Damus** | NIP-50 relay search | None | User/profile focused |
| **noStrudel** | Local relay + NIP-50 | None | IndexedDB local indexing |
| **Gossip** | NIP-50 relay search | None | Desktop Rust client |
| **Noogle.lol** | NIP-90 DVM search | `from:npub` `from:me` | Pay-per-query via Lightning |

**Key insight:** No client ships a query operator language. This is greenfield.

## Proposed Query Schema

Operators map directly to `Filter` fields and NIP-50 extensions:

### Core Operators

| Operator | Maps To | Example | Notes |
|----------|---------|---------|-------|
| `from:<npub\|name>` | `Filter.authors` | `from:npub1abc...` | Resolve names via NIP-05/local cache |
| `kind:<number\|name>` | `Filter.kinds` | `kind:note` or `kind:1` | Named aliases for common kinds |
| `since:<date>` | `Filter.since` | `since:2025-01-01` | ISO 8601 date parsing |
| `until:<date>` | `Filter.until` | `until:2025-06-30` | ISO 8601 date parsing |
| `#<tag>` | `Filter.tags["t"]` | `#bitcoin` | Hashtag filter |
| `"exact phrase"` | Quoted in `Filter.search` | `"lightning network"` | Relay-dependent support |
| `-<term>` | Client-side exclusion | `-spam` | Post-filter after relay results |

### NIP-50 Extension Operators

| Operator | NIP-50 Extension | Example |
|----------|-----------------|---------|
| `lang:<code>` | `language:xx` | `lang:en` |
| `domain:<nip05>` | `domain:xx` | `domain:nostr.com` |
| `nsfw:<bool>` | `nsfw:xx` | `nsfw:false` |

### Kind Name Aliases

| Alias | Kind(s) | Description |
|-------|---------|-------------|
| `note` | 1 | Short text note |
| `article` | 30023 | Long-form content |
| `repost` | 6 | Reposts |
| `reply` | 1 (with `e` tag) | Replies (client-side filter) |
| `media` | 1 (with `imeta` tag) | Notes with media |
| `channel` | 40, 41, 42 | Public channels |
| `live` | 30311 | Live activities |
| `community` | 34550 | Communities |
| `wiki` | 30818 | Wiki pages |
| `video` | 34235 | Video events |
| `classified` | 30402 | Classifieds |
| `profile` | 0 | Metadata/profiles |

### Boolean Logic

| Syntax | Behavior |
|--------|----------|
| `bitcoin lightning` | AND (default) |
| `bitcoin OR lightning` | OR — requires multiple relay queries |
| `-spam` | NOT — client-side exclusion |

## UX Design

### Search Bar (Default State)
```
[  Search notes, people, tags...  ] [Advanced v]
```

### Expanded Advanced Panel
```
[  from:npub1abc kind:note since:2025-01 bitcoin          ] [Advanced ^]
+------------------------------------------------------------------+
| Content Type: [x] Notes [ ] Articles [ ] Media [ ] All           |
| Author:       [ npub or name...              ] [+ Add]           |
| Date Range:   [ 2025-01-01 ] to [ today ]                       |
| Language:     [ Any v ]                                          |
| Hashtags:     [ #bitcoin ] [+ Add]                               |
| Exclude:      [ spam, nsfw...                ] [+ Add]           |
|                                                                  |
| [Clear Filters]                          [Search]                |
+------------------------------------------------------------------+
```

### Bidirectional Sync
- Typing `from:npub1abc` in search bar → Author field populates in panel
- Selecting "Articles" checkbox → `kind:article` appears in search bar
- Editing either side updates the other in real-time
- Form is the "visual representation" of the query string

### Result Display
- Results grouped by type: People, Notes, Articles, Channels
- Each result shows: content preview, author, timestamp, kind badge
- Infinite scroll with "Load more from relays" button
- Sort: Relevance (default, relay-determined) or Chronological

## Architecture

### Query Pipeline

```
User Input (text or form)
    |
    v
QueryParser (commons/commonMain)
    |-- Tokenize operators: from:, kind:, since:, etc.
    |-- Resolve names → hex pubkeys (local cache + NIP-05)
    |-- Parse dates → unix timestamps
    |-- Map kind aliases → kind numbers
    |
    v
SearchQuery (data class in commons)
    |-- text: String (free text for NIP-50 search field)
    |-- authors: List<HexKey>
    |-- kinds: List<Int>
    |-- since: Long?
    |-- until: Long?
    |-- tags: Map<String, List<String>>
    |-- excludeTerms: List<String>
    |-- language: String?
    |-- nip50Extensions: Map<String, String>
    |
    v
FilterBuilder (desktop)
    |-- Convert SearchQuery → List<Filter>
    |-- Split by kind groups (like Android: 3 filters, ~10 kinds each)
    |-- Inject NIP-50 extensions into search string
    |
    v
Relay Subscription
    |-- Use search relay list (kind 10007) or connected relays
    |-- Send REQ with filters
    |-- Aggregate results
    |
    v
Client-side Post-filter
    |-- Apply exclusions (-term)
    |-- Apply "reply" detection (has e tag)
    |-- Apply "media" detection (has imeta tag)
    |
    v
Results Display
```

### Module Placement

| Component | Module | Rationale |
|-----------|--------|-----------|
| `QueryParser` | `commons/commonMain` | Reusable for Android later |
| `SearchQuery` | `commons/commonMain` | Shared data model |
| `QuerySerializer` | `commons/commonMain` | SearchQuery ↔ string conversion |
| `AdvancedSearchPanel` | `desktopApp` | Desktop-specific UI |
| `SearchScreen` (updated) | `desktopApp` | Desktop layout |
| `FilterBuilder` (updated) | `desktopApp` | Desktop filter assembly |
| Kind alias registry | `commons/commonMain` | Shared kind name mapping |

### Search Relay Management

- Use existing `SearchRelayListEvent` (kind 10007) from quartz
- Desktop UI to configure search relays (settings page)
- Default fallback: `relay.nostr.band`, `nostr.wine`, `relay.damus.io`
- Future: auto-discover NIP-50 capable relays via NIP-11

## Privacy Considerations

| Concern | Mitigation | Default |
|---------|-----------|---------|
| Relay sees search queries | User-configurable search relay list | On (kind 10007) |
| Relay sees IP + query | VPN/Tor support (system-level) | Not enforced |
| Query history stored | No server-side history; client-side optional | Off |
| NIP-05 resolution leaks interest | Cache NIP-05 lookups locally | On |
| Author search reveals social graph | Already visible via follow lists | N/A |

**Stance:** Relay-first, pragmatic. Desktop users accept relay visibility for better results. Advanced users can configure search relays or use Tor.

## Key Decisions

1. **Query language is NOT a Nostr standard** — it's a client-side UX convention that maps to `Filter` fields
2. **Bidirectional sync** between text bar and form panel — single source of truth (`SearchQuery` data class)
3. **Kind presets with extensibility** — start with core groups, users can toggle individual kinds
4. **Relay-first execution** — NIP-50 search as primary, local cache as supplement
5. **AI deferred** — follow-up feature, will parse natural language → `SearchQuery`
6. **Query parser in commons** — shared module so Android can adopt later
7. **Client-side post-filtering** for operators relays can't handle (exclusions, reply detection, media detection)

## Resolved Questions

1. **Name resolution** — Async + refine. Search immediately with text, resolve `from:name` in background, refine results when pubkey resolved. No blocking.
2. **OR queries** — Yes in v1. `bitcoin OR lightning` sends parallel relay subscriptions, merges results.
3. **Search history** — Recent history (last 20) stored locally.
4. **Saved searches** — Yes in v1. Pin/save queries. Future: persist as Nostr events.
5. **Result caching** — Session cache only (in-memory). Cleared on app restart. No disk persistence.

## Open Questions

None — all resolved.

## Follow-up Features (Out of Scope)

- AI natural language → query parsing (local Ollama / cloud API / NIP-90 DVM)
- Local SQLite FTS5 index for offline search
- NIP-90 DVM search integration
- Search analytics / trending topics
- Collaborative search (shared saved searches via Nostr events)
