---
title: "feat: Desktop Advanced Search with Query Operators and Form UI"
type: feat
status: active
date: 2026-03-10
deepened: 2026-03-10
origin: docs/brainstorms/2026-03-10-advanced-search-brainstorm.md
---

# Desktop Advanced Search

## Enhancement Summary

**Deepened on:** 2026-03-10
**Agents used:** kotlin-expert, compose-expert, kotlin-coroutines, nostr-expert, desktop-expert, kmp-expert, best-practices-researcher, architecture-strategist, performance-oracle, code-simplicity-reviewer, security-sentinel

### Key Improvements
1. **Bidirectional sync loop prevention** — `sourceOfChange` discriminator (TEXT/FORM/INIT) breaks parse→serialize→parse cycles
2. **Performance** — batch result accumulation via `channelFlow` + 100ms windows, cap OR to 3 terms (not 5), `@Immutable` SearchQuery
3. **Parser architecture** — hand-written recursive descent tokenizer + parser, error recovery via literal text degradation
4. **Module corrections** — SearchFilterFactory stays in desktopApp (needs SubscriptionConfig); SearchResultFilter and SearchHistoryStore can move to commons
5. **Compose patterns** — `FilterChip` for kind presets, `expandVertically(Alignment.Top)` + `fadeIn`, sticky section headers, shimmer loading
6. **Coroutine patterns** — `flatMapLatest` for auto-canceling old subscriptions, `merge()` for OR queries, `supervisorScope` for relay isolation
7. **Simplicity guidance** — MVP can cut OR queries, lang:/domain:, saved searches to ~350 LOC / 5 files. Full plan phases appropriately.

### New Considerations Discovered
- NIP-50 extensions go inline in search string (`"bitcoin language:en"`), not as separate filter fields
- Pseudo-kinds (reply, media) need separate handling from real kinds — they're client-side post-filters, not relay filters
- `TextFieldValue` (not raw String) needed for cursor position stability during bidirectional sync
- Use `query.hashtags` → `Filter.tags["t"]` (more reliable than putting hashtags in search string)
- OR cap: 3 terms max (not 5) — 5 terms × 3 groups × 3 relays = 45 subs is too many

---

## Overview

Full-featured search for Amethyst Desktop: Twitter-style query operators (`from:`, `kind:`, `since:`, etc.), expandable form panel below search bar, bidirectional sync between text and form, extensible kind presets, OR queries, search history + saved searches. Relay-first via NIP-50.

Current desktop search only handles kind 0 (people) + kind 1 (notes) with plain text. Android searches 30+ kinds. This closes that gap and adds capabilities neither platform has.

## Problem Statement

Desktop search (`SearchScreen.kt`) is minimal:
- Only `searchPeople()` (kind 0) wired to relay subscription
- `searchNotes()` exists in `FeedSubscription.kt` but not connected
- No kind filtering, no author filtering, no date ranges
- No query language — users can only type plain text or bech32 identifiers
- `SearchBarState` in commons only returns `User` results, no notes/channels

Users can't find content they've seen, discover new content by topic, or filter by author/type/date.

## Proposed Solution

### Query Operator Language

Client-side query language that maps to `Filter` fields. Not a Nostr standard — a UX convention.

```
from:npub1abc kind:note since:2025-01-01 bitcoin OR lightning -spam #nostr
```

| Operator | Maps To | Relay-side? |
|----------|---------|-------------|
| `from:<npub\|name>` | `Filter.authors` | Yes |
| `kind:<number\|alias>` | `Filter.kinds` | Yes |
| `since:<date>` | `Filter.since` | Yes |
| `until:<date>` | `Filter.until` | Yes |
| `#<tag>` | `Filter.tags["t"]` | Yes |
| `"exact phrase"` | Quoted in `Filter.search` | Yes (relay-dependent) |
| `lang:<code>` | NIP-50 extension in search string | Relay-dependent |
| `domain:<nip05>` | NIP-50 extension in search string | Relay-dependent |
| `-<term>` | Client-side exclusion post-filter | No |
| `OR` | Parallel subscriptions, merged | Multiple queries |

#### Research Insights: NIP-50 Protocol Details

**NIP-50 extension placement:** Extensions go *inline in the search string*, not as separate filter fields. The relay parses them out:
```json
{"kinds": [1], "search": "bitcoin language:en domain:nostr.com"}
```

**Hashtag handling:** Use `tags = {"t": ["bitcoin"]}` in the filter (more reliable across relays) rather than putting `#bitcoin` in the search string. Hashtags in `Filter.tags` are protocol-level, not NIP-50 dependent.

**Quoted phrase search:** Not standardized — relay-dependent. Some relays treat quotes literally, others ignore them. Degrade gracefully.

**All filter fields AND together** within a single filter. OR requires separate subscriptions.

### Dual UI: Text Bar + Expandable Form Panel

```
[  from:npub1abc kind:note bitcoin          ] [Advanced v]
+----------------------------------------------------------+
| Content: [x] Notes [ ] Articles [ ] Media [ ] All       |
| Author:  [ npub or name...         ] [+ Add]            |
| Since:   [ 2025-01-01 ]  Until: [ today ]               |
| Tags:    [ #bitcoin ] [+ Add]                            |
| Exclude: [ spam ] [+ Add]                                |
| Language:[ Any v ]                                       |
|                                                          |
| [Clear]                              [Search]            |
+----------------------------------------------------------+
```

Bidirectional: typing `kind:article` checks "Articles"; checking "Notes" inserts `kind:note`.

#### Research Insights: Bidirectional Sync

**Critical: `sourceOfChange` discriminator.** Without this, parse→serialize→parse loops will occur. Track who initiated the change:

```kotlin
enum class ChangeSource { TEXT, FORM, INIT }

fun updateFromText(rawText: String) {
    _changeSource = ChangeSource.TEXT
    _query.value = QueryParser.parse(rawText)
}

fun updateKinds(kinds: List<Int>) {
    _changeSource = ChangeSource.FORM
    _query.value = _query.value.copy(kinds = kinds)
}

// In the composable, only update text field when source != TEXT
val displayText by remember {
    state.query.map { query ->
        if (state.changeSource != ChangeSource.TEXT) {
            QuerySerializer.serialize(query)
        } else {
            // Keep user's raw text as-is
            state.rawText
        }
    }
}
```

**Use `TextFieldValue` (not raw String)** for the text bar to preserve cursor position during form-driven updates. When form changes update the serialized text, set `TextFieldValue(text = newText, selection = TextRange(newText.length))`.

## Technical Approach

### Architecture

```
Text Bar ──parse──> SearchQuery <──serialize── Form Panel
                        |
                    FilterBuilder
                        |
                  List<Filter>  (split by kind groups, ~10 kinds each)
                        |
              Relay Subscriptions (NIP-50)
                        |
              Client-side Post-filter (exclusions, reply/media detection)
                        |
                    Results Display (grouped: People, Notes, Articles, Channels)
```

**Single source of truth:** `MutableStateFlow<SearchQuery>`. Both text bar and form read from it. Text bar changes → `QueryParser` → `SearchQuery`. Form changes → mutate `SearchQuery` directly. `QuerySerializer` regenerates text string. `sourceOfChange` discriminator prevents update loops.

**Debounce strategy:** Text input debounced 300ms (existing pattern). Form toggle changes trigger immediate search (no debounce).

#### Research Insights: Kotlin State Patterns

**`@Immutable` on SearchQuery** — enables Compose to skip recomposition when query hasn't changed:
```kotlin
@Immutable
data class SearchQuery(
    val text: String = "",
    val authors: ImmutableList<String> = persistentListOf(),
    val kinds: ImmutableList<Int> = persistentListOf(),
    // ...
) {
    companion object {
        val EMPTY = SearchQuery()
    }
}
```

Use `kotlinx.collections.immutable` (`ImmutableList`, `ImmutableSet`, `persistentListOf()`) for all collection fields. This gives Compose structural stability guarantees.

**Granular derived StateFlows** with `distinctUntilChanged()` to prevent unnecessary recomposition:
```kotlin
val kindsForUI: StateFlow<ImmutableList<Int>> = _query
    .map { it.kinds }
    .distinctUntilChanged()
    .stateIn(scope, SharingStarted.WhileSubscribed(5000), persistentListOf())
```

### Data Flow Detail

```
SearchQuery (SSOT)
  │
  ├─ text bar reads: QuerySerializer.serialize(query) → displayed string
  │   └─ on text change: QueryParser.parse(rawText) → new SearchQuery
  │   └─ GUARD: only serialize→display when changeSource != TEXT
  │
  ├─ form panel reads: query.kinds, query.authors, query.since, etc.
  │   └─ on form change: query.copy(kinds = ...) → new SearchQuery
  │
  └─ relay layer reads: SearchFilterFactory.createFilters(query) → List<Filter>
      └─ subscription created per filter group
      └─ OR queries: parallel subscriptions via merge(), results deduped by event ID
```

### Implementation Phases

#### Phase 1: Query Engine (commons/commonMain) — Foundation

Pure Kotlin, no UI, exhaustively unit-tested.

**Step 1.1: `SearchQuery` data class**

```kotlin
// commons/src/commonMain/.../search/SearchQuery.kt
@Immutable
data class SearchQuery(
    val text: String = "",                                      // Free text for NIP-50 search field
    val authors: ImmutableList<String> = persistentListOf(),    // Hex pubkeys
    val authorNames: ImmutableList<String> = persistentListOf(), // Unresolved names (for display)
    val kinds: ImmutableList<Int> = persistentListOf(),         // Empty = all searchable kinds
    val since: Long? = null,                                    // Unix timestamp
    val until: Long? = null,
    val hashtags: ImmutableList<String> = persistentListOf(),   // Without # prefix
    val excludeTerms: ImmutableList<String> = persistentListOf(), // Client-side exclusion
    val language: String? = null,                               // ISO 639-1
    val domain: String? = null,                                // NIP-05 domain
    val orTerms: ImmutableList<String> = persistentListOf(),   // Terms joined by OR
) {
    val isEmpty get() = text.isBlank() && authors.isEmpty() && kinds.isEmpty()
        && since == null && until == null && hashtags.isEmpty()
        && orTerms.isEmpty()

    companion object {
        val EMPTY = SearchQuery()
    }
}
```

#### Research Insights: Pseudo-Kinds

**Separate pseudo-kinds from real kinds.** `kind:reply` and `kind:media` are NOT relay filter kinds — they require client-side post-filtering:
- `kind:reply` = kind 1 events WITH `e` tag
- `kind:media` = kind 1 events WITH `imeta` tag or image URLs

The `SearchQuery` should track these separately or the `KindRegistry` should flag them. Recommended: a `pseudoKinds: Set<PseudoKind>` field or handle in `SearchResultFilter`.

**Step 1.2: Kind alias registry**

```kotlin
// commons/src/commonMain/.../search/KindRegistry.kt
object KindRegistry {
    // Import quartz KIND constants instead of hardcoding numbers
    val aliases: Map<String, List<Int>> = mapOf(
        "note" to listOf(1),
        "article" to listOf(30023),
        "repost" to listOf(6),
        "profile" to listOf(0),
        "channel" to listOf(40, 41, 42),
        "live" to listOf(30311),
        "community" to listOf(34550),
        "wiki" to listOf(30818),
        "video" to listOf(34235),
        "classified" to listOf(30402),
        "highlight" to listOf(9802),
        "poll" to listOf(6969),
    )

    // Pseudo-kinds: client-side post-filters, not relay kinds
    val pseudoKinds: Set<String> = setOf("reply", "media")

    val presets: Map<String, List<Int>> = mapOf(
        "Notes" to listOf(1),
        "Articles" to listOf(30023),
        "Media" to listOf(1), // post-filtered for imeta tag
        "Channels" to listOf(40, 41, 42),
        "Communities" to listOf(34550),
        "Wiki" to listOf(30818),
    )

    fun resolve(alias: String): List<Int>? = aliases[alias.lowercase()]
    fun isPseudoKind(alias: String): Boolean = alias.lowercase() in pseudoKinds
    fun nameFor(kind: Int): String? = aliases.entries.find { kind in it.value }?.key
}
```

**Step 1.3: `QueryParser`**

#### Research Insights: Parser Architecture

**Hand-written recursive descent parser** (not regex, not parser generators). Two-phase:

1. **Tokenizer** (state machine): Walks characters, emits tokens: `OperatorToken(name, value)`, `TextToken(value)`, `OrToken`, `QuotedToken(value)`, `NegationToken(value)`, `HashtagToken(value)`
2. **Parser** (recursive descent): Consumes tokens, builds `SearchQuery`

**Key principles:**
- **Preserve raw text in tokens** for roundtrip fidelity (`serialize(parse(input))` ≈ `input`)
- **Error recovery**: malformed operators degrade to literal text, never throw
- **OR precedence**: OR binds to adjacent text terms only. Operators are always AND.
  - `from:vitor bitcoin OR lightning kind:note` = `from:vitor AND kind:note AND (bitcoin OR lightning)`
- **Performance**: sub-microsecond parsing, not a concern

```kotlin
// commons/src/commonMain/.../search/QueryParser.kt
object QueryParser {
    fun parse(input: String): SearchQuery {
        val tokens = tokenize(input)
        return buildQuery(tokens)
    }

    private fun tokenize(input: String): List<Token> { /* state machine */ }
    private fun buildQuery(tokens: List<Token>): SearchQuery { /* recursive descent */ }
}

sealed interface Token {
    data class Operator(val name: String, val value: String, val raw: String) : Token
    data class Text(val value: String) : Token
    data object Or : Token
    data class Quoted(val value: String, val raw: String) : Token
    data class Negation(val term: String) : Token
    data class Hashtag(val tag: String) : Token
}
```

Rules:
- Case-insensitive operator matching (`FROM:` = `from:`)
- `from:<value>` → if bech32 npub, decode to hex and add to `authors`; else add to `authorNames` (async resolution)
- `kind:<value>` → resolve via `KindRegistry.resolve()` or parse as int. Flag pseudo-kinds separately.
- `since:<date>` / `until:<date>` → parse ISO 8601 (`2025-01-01`, `2025-01`, `2025`) to unix timestamp
- `#tag` → add to `hashtags`
- `"quoted phrase"` → keep in `text` as quoted
- `-term` → add to `excludeTerms`, strip from relay search string
- `OR` → split adjacent free text terms. `bitcoin OR lightning` → `orTerms = ["bitcoin", "lightning"]`
- Multiple `from:` → AND (multiple authors)
- Multiple `kind:` → union (combined kinds)
- Incomplete operators (`from:` with no value) → treat as literal text

**Step 1.4: `QuerySerializer`**

```kotlin
// commons/src/commonMain/.../search/QuerySerializer.kt
object QuerySerializer {
    fun serialize(query: SearchQuery): String { ... }
}
```

Regenerates the canonical text representation from `SearchQuery`. Used to update text bar when form changes. Ordering: operators first (`from:`, `kind:`, `since:`, `until:`, `lang:`, `domain:`), then hashtags, then free text / OR terms, then exclusions.

**Step 1.5: Unit tests**

```kotlin
// commons/src/commonTest/.../search/QueryParserTest.kt
// commons/src/commonTest/.../search/QuerySerializerTest.kt
// commons/src/commonTest/.../search/KindRegistryTest.kt
```

Test matrix:
- Single operator of each type
- Combined operators
- OR with operators
- Malformed/incomplete (`from:`, `kind:invalid`, `since:not-a-date`)
- Special characters, emoji, unicode in free text
- Roundtrip: `serialize(parse(input)) == normalized(input)`
- Multiple `from:` authors
- Multiple `kind:` (union)
- Quoted phrases
- Exclusion terms
- Pseudo-kind detection (`kind:reply`, `kind:media`)
- Edge: empty string, whitespace only, very long query
- OR precedence: `from:x a OR b kind:note` → operators AND, text OR

**Consider property-based testing** with Kotest for roundtrip fidelity.

#### Phase 2: Filter Factory + Relay Integration (desktopApp)

**Step 2.1: `SearchFilterFactory`**

```kotlin
// desktopApp/src/jvmMain/.../subscriptions/SearchFilterFactory.kt
object SearchFilterFactory {
    fun createFilters(query: SearchQuery): List<Filter> { ... }
}
```

- If `query.kinds` specified → use those kinds directly
- If `query.kinds` empty → use default searchable kinds (align with Android's 3 groups)
- Split kinds into groups of ~10 (relay `max_filters` limit safety)
- Build NIP-50 search string: `query.text` + inline NIP-50 extensions (`language:en`, `domain:x`)
- Strip exclusion terms from search string (don't send `-spam` to relay)
- `query.authors` → `Filter.authors` (only resolved hex keys)
- `query.since` / `query.until` → `Filter.since` / `Filter.until`
- `query.hashtags` → `Filter.tags["t"]` (not in search string — more reliable)
- OR queries: return separate filter lists per OR term

#### Research Insights: Module Placement

**SearchFilterFactory stays in desktopApp** — it depends on `SubscriptionConfig` and relay topology, which are desktop-specific. Correct as planned.

**SearchResultFilter can move to commons/commonMain** — pure Kotlin, no platform dependencies. Android can reuse it later.

**SearchHistoryStore can move to commons as expect/actual** — follows `SecureKeyStorage` pattern. `expect class SearchHistoryStore`, with `actual` implementations using `java.util.prefs.Preferences` on desktop and SharedPreferences/DataStore on Android.

**Step 2.2: Default searchable kind groups**

Port from Android's `SearchPostsByText.kt` to desktop. Reference the same kinds:

```kotlin
// Group 1: TextNote, LongText, Badge, PeopleList, BookmarkList, AudioHeader, AudioTrack, PinList, PollNote, ChannelCreate
// Group 2: ChannelMetadata, Classifieds, Community, EmojiPack, Highlight, LiveActivities, PublicMessage, NNS, Wiki, Comment
// Group 3: InteractiveStory (2 kinds), FollowList, NipText, Poll, PollResponse
```

Kind group splitting is relay-imposed (`max_filters` limits), not protocol. Use the quartz KIND constants, don't hardcode numbers.

**Step 2.3: Search subscription factory**

```kotlin
// desktopApp/src/jvmMain/.../subscriptions/FeedSubscription.kt (extend)
fun createAdvancedSearchSubscription(
    relays: Set<NormalizedRelayUrl>,
    query: SearchQuery,
    onEvent: ...,
    onEose: ...,
): List<SubscriptionConfig>
```

#### Research Insights: Subscription Management

**Use `flatMapLatest`** on debouncedQuery to auto-cancel old subscriptions when query changes:
```kotlin
val results: Flow<List<Event>> = debouncedQuery
    .flatMapLatest { query ->
        if (query.isEmpty) flowOf(emptyList())
        else channelFlow {
            supervisorScope {
                val filters = SearchFilterFactory.createFilters(query)
                // Launch independent subscription per filter group
                filters.forEach { filter ->
                    launch { subscribeAndEmit(filter, relays) }
                }
            }
        }
    }
```

**`supervisorScope`** for independent relay subscription failure isolation — one relay failure doesn't cancel others.

**`merge()` (not `combine()`)** for OR query result flows — emit results as they arrive from any term.

**Batch filters per OR term** in a single REQ (not per kind group), reducing subscription count:
- 3 OR terms × 1 batched REQ × 3 relays = 9 subscriptions (vs 45 if unbatched)

**Cap: max 3 OR terms** (not 5) — subscription fan-out gets expensive.

**Step 2.4: Client-side post-filter**

```kotlin
// commons/src/commonMain/.../search/SearchResultFilter.kt
object SearchResultFilter {
    fun filter(events: List<Event>, query: SearchQuery): List<Event>
}
```

- `-term` exclusion: check `event.content` doesn't contain term (case-insensitive)
- `kind:reply` detection: kind 1 with `e` tag
- `kind:media` detection: kind 1 with `imeta` tag or URL patterns
- Deduplication by event ID (for OR query merges)

#### Research Insights: Performance

**Batch result accumulation** — current Amethyst pattern of `_results.value = _results.value + item` is O(n^2). Use channel-based batching:

```kotlin
channelFlow {
    val batch = mutableSetOf<Event>() // Set for O(1) dedup
    var lastEmit = 0L

    onEvent = { event ->
        batch.add(event)
        val now = System.currentTimeMillis()
        if (now - lastEmit > 100) { // 100ms batch window
            send(batch.toList())
            lastEmit = now
        }
    }
}
```

**Apply post-filter at batch emission time**, not per-event.

**Result ordering:** dedup by event ID, sort by `createdAt` descending (match Amethyst Android behavior).

#### Phase 3: Advanced Search State (commons/commonMain)

**Step 3.1: `AdvancedSearchBarState`**

New state holder that extends/replaces `SearchBarState`. Manages the `SearchQuery` as SSOT.

```kotlin
// commons/src/commonMain/.../viewmodels/AdvancedSearchBarState.kt
class AdvancedSearchBarState(
    private val cache: ICacheProvider,
    private val scope: CoroutineScope,
) {
    private val _query = MutableStateFlow(SearchQuery.EMPTY)
    val query: StateFlow<SearchQuery> = _query.asStateFlow()

    // Track who initiated the change (prevents sync loops)
    private var _changeSource: ChangeSource = ChangeSource.INIT
    val changeSource get() = _changeSource

    // Raw text from user typing (preserved when source=TEXT)
    private val _rawText = MutableStateFlow("")
    val rawText: StateFlow<String> = _rawText.asStateFlow()

    // Derived: text representation for the search bar
    val displayText: StateFlow<String> = combine(_query, _rawText) { query, raw ->
        if (_changeSource == ChangeSource.TEXT) raw
        else QuerySerializer.serialize(query)
    }.stateIn(scope, SharingStarted.Eagerly, "")

    // For relay subscriptions to observe (300ms debounce)
    val debouncedQuery: StateFlow<SearchQuery> = _query
        .debounce(300)
        .stateIn(scope, SharingStarted.Eagerly, SearchQuery.EMPTY)

    // Results
    val peopleResults: StateFlow<ImmutableList<User>>
    val noteResults: StateFlow<ImmutableList<Event>>
    val isSearching: StateFlow<Boolean>

    // Text bar input (parses into SearchQuery)
    fun updateFromText(rawText: String) {
        _changeSource = ChangeSource.TEXT
        _rawText.value = rawText
        _query.value = QueryParser.parse(rawText)
    }

    // Form panel input (mutates SearchQuery directly)
    fun updateKinds(kinds: List<Int>) {
        _changeSource = ChangeSource.FORM
        _query.value = _query.value.copy(kinds = kinds.toImmutableList())
    }

    fun addAuthor(hexOrName: String) { ... }
    fun removeAuthor(hex: String) { ... }
    fun updateDateRange(since: Long?, until: Long?) { ... }
    fun addHashtag(tag: String) { ... }
    fun removeHashtag(tag: String) { ... }
    fun addExcludeTerm(term: String) { ... }
    fun updateLanguage(lang: String?) { ... }

    // Name resolution (async)
    fun resolveAuthorName(name: String, onResolved: (String) -> Unit)

    // History
    fun addToHistory(query: SearchQuery)
    fun getHistory(): List<SearchQuery>
    fun saveSearch(query: SearchQuery, label: String)
    fun getSavedSearches(): List<SavedSearch>
    fun deleteSavedSearch(id: String)
}

enum class ChangeSource { TEXT, FORM, INIT }
```

**Step 3.2: Name resolution**

- Check local cache first: `cache.findUsersStartingWith(name, 5)`
- If multiple matches → expose as `authorSuggestions: StateFlow<List<User>>` for autocomplete dropdown
- If single match → auto-resolve to hex key
- If no local match → keep as `authorNames` (display in form as "unresolved: vitor")
- Keep name resolution **out of QueryParser** — parser returns raw strings, platform layer resolves
- No NIP-05 resolution in v1. Follow-up.

#### Phase 4: Desktop UI (desktopApp)

**Step 4.1: Rewrite `SearchScreen.kt`**

```kotlin
// desktopApp/src/jvmMain/.../ui/SearchScreen.kt
@Composable
fun SearchScreen(
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    ...
) {
    val state = remember { AdvancedSearchBarState(localCache, scope) }
    val query by state.query.collectAsState()
    val displayText by state.displayText.collectAsState()
    var panelExpanded by remember { mutableStateOf(false) }

    Column {
        // Search bar row
        Row {
            OutlinedTextField(
                value = TextFieldValue(
                    text = displayText,
                    selection = TextRange(displayText.length),
                ),
                onValueChange = { state.updateFromText(it.text) },
                placeholder = { Text("Search notes, people, tags... or use operators") },
                ...
            )
            TextButton(onClick = { panelExpanded = !panelExpanded }) {
                Text(if (panelExpanded) "Advanced ^" else "Advanced v")
            }
        }

        // Expandable advanced panel
        AnimatedVisibility(
            visible = panelExpanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            AdvancedSearchPanel(
                query = query,
                onKindsChanged = { state.updateKinds(it) },
                onAuthorAdded = { state.addAuthor(it) },
                onAuthorRemoved = { state.removeAuthor(it) },
                onDateRangeChanged = { since, until -> state.updateDateRange(since, until) },
                ...
            )
        }

        // Results
        SearchResultsList(state = state, ...)
    }
}
```

#### Research Insights: Compose UI Patterns

**Panel animation:** `expandVertically(expandFrom = Alignment.Top)` + `fadeIn()` — panel slides down from search bar, feels natural.

**Kind preset chips:** Use `FilterChip` (not ElevatedFilterChip or AssistChip):
```kotlin
KindRegistry.presets.forEach { (name, kinds) ->
    FilterChip(
        selected = query.kinds.containsAll(kinds),
        onClick = { onKindsChanged(toggleKinds(query.kinds, kinds)) },
        label = { Text(name) },
    )
}
```

**Author autocomplete:** `DropdownMenu` (not `Popup`) — handles dismissal, positioning, focus correctly.

**Date input:** Text fields with `YYYY-MM-DD` format (no native date picker on desktop). Validate on blur.

**Keyboard events:** `onPreviewKeyEvent` for Escape (before children), `onKeyEvent` for `/` (after children).

**Step 4.2: `AdvancedSearchPanel` composable**

```kotlin
// desktopApp/src/jvmMain/.../ui/search/AdvancedSearchPanel.kt
@Composable
fun AdvancedSearchPanel(
    query: SearchQuery,
    onKindsChanged: (List<Int>) -> Unit,
    onAuthorAdded: (String) -> Unit,
    ...
)
```

Components:
- **Content type row**: `FilterChip` per preset from `KindRegistry.presets`. Checked state derived from `query.kinds`.
- **Author field**: `OutlinedTextField` + `DropdownMenu` autocomplete dropdown (from `authorSuggestions`). Shows chips for added authors.
- **Date range**: Two date text fields (`yyyy-MM-dd` format). Validate on blur.
- **Hashtags**: Chip group with add button.
- **Exclude terms**: Chip group with add button.
- **Language dropdown**: `DropdownMenu` with common ISO 639-1 codes.
- **Clear / Search buttons**: Clear resets `SearchQuery.EMPTY`. Search is implicit (debounced).
- **Tooltips**: `TooltipBox` + `PlainTooltip` for operator hint text on hover.

**Step 4.3: `SearchResultsList` composable**

```kotlin
// desktopApp/src/jvmMain/.../ui/search/SearchResultsList.kt
@Composable
fun SearchResultsList(state: AdvancedSearchBarState, ...)
```

#### Research Insights: Results Display

- Single `LazyColumn` with **sticky section headers**: `stickyHeader { Surface(color = background) { ... } }`
- Sections: **People** (kind 0), **Notes** (kind 1), **Articles** (kind 30023), **Other** (everything else)
- Each section shows top 5 results with "Show all N" expand link
- Note results: content preview (first 200 chars), author name, timestamp, kind badge
- Progressive loading: results stream in as relay responds, sections update live
- **Shimmer loading**: Custom shimmer via `Brush.linearGradient` + `InfiniteTransition` (reusable, put in commons)
- Empty state: "No results found. Try broader terms or fewer filters."
- **Stable keys** for LazyColumn items: `key = { "section-${event.id}" }` to prevent recomposition flicker

**Step 4.4: Relay subscription wiring**

In `SearchScreen.kt`, use `rememberSubscription()` with the debounced query:

```kotlin
val debouncedQuery by state.debouncedQuery.collectAsState()
val configuredRelays by remember {
    relayManager.relayStatuses
        .map { it.keys }
        .distinctUntilChanged() // Prevent churn (FeedScreen pattern)
}.collectAsState(emptySet())

// Create subscriptions from query
val filters = remember(debouncedQuery) { SearchFilterFactory.createFilters(debouncedQuery) }
// ... wire up rememberSubscription per filter group
```

#### Research Insights: Desktop-Specific Patterns

**Keyboard shortcuts:**
- `Ctrl+K` or `/` → focus search bar. Use `Window.onKeyEvent` for `/` (after children process), `onPreviewKeyEvent` for Escape.
- `Escape` → close advanced panel / clear search
- `Enter` → execute search immediately (skip debounce)
- Register `Ctrl+K` in `MenuBar { Item("Search", KeyShortcut(Key.K, ctrl = true)) { focusSearch() } }`

**Clipboard:** Support pasting npub/note/nevent directly into search bar — already handled by `QueryParser` treating bech32 as `from:` equivalent.

#### Phase 5: Search History + Saved Searches

**Step 5.1: Local persistence**

```kotlin
// desktopApp/src/jvmMain/.../storage/SearchHistoryStore.kt
class SearchHistoryStore(private val appDataDir: Path) {
    private val historyFile = appDataDir / "search_history.json"
    private val savedFile = appDataDir / "saved_searches.json"

    // In-memory cache, async persist on Dispatchers.IO
    private var historyCache: MutableList<SearchQuery> = mutableListOf()

    fun addToHistory(query: SearchQuery) // Dedup by serialized text, max 20 entries
    fun getHistory(): List<SearchQuery>
    fun clearHistory()

    fun saveSearch(query: SearchQuery, label: String)
    fun getSavedSearches(): List<SavedSearch>
    fun deleteSavedSearch(id: String)
}

data class SavedSearch(
    val id: String,       // UUID
    val label: String,
    val query: SearchQuery,
    val createdAt: Long,
)
```

JSON serialization via kotlinx.serialization (already in project).

**Platform data dirs:** macOS `~/Library/Application Support/Amethyst/`, Linux `~/.config/amethyst/`, Windows `%APPDATA%\Amethyst\`. Use existing `DesktopPreferences.kt` pattern or `java.util.prefs.Preferences`.

**Step 5.2: History UI**

When search bar is empty → show recent history + saved searches below the bar.
- History items: click to load query into search bar
- Saved searches: click to load, X to delete
- "Clear history" button at bottom

#### Phase 6: Integration + Polish

**Step 6.1: Search hints update**

Update empty state hints to show operator examples:
```
from:npub1...     Filter by author
kind:article      Long-form content
since:2025-01     After January 2025
#bitcoin          Hashtag search
"exact phrase"    Exact match
bitcoin OR nostr  Either term
```

**Step 6.2: Keyboard shortcuts**

- `Ctrl+K` or `/` → focus search bar (desktop convention)
- `Escape` → close advanced panel / clear search
- `Enter` → execute search immediately (skip debounce)

**Step 6.3: Search relay configuration**

- Desktop settings page: list of search relays (editable)
- Default: `relay.nostr.band`, `nostr.wine`, `relay.damus.io` (curated, don't auto-probe NIP-11)
- Future: read from kind 10007 `SearchRelayListEvent`

## System-Wide Impact

### Interaction Graph

1. User types in search bar → `AdvancedSearchBarState.updateFromText()` → `QueryParser.parse()` → `_query` updates
2. `_query` change → `displayText` recomputes (serialized, guarded by `changeSource`) → text bar updates
3. `_query` change → `debouncedQuery` emits after 300ms → `flatMapLatest` cancels old subscriptions → new relay subscriptions created
4. Subscription creation → `relayManager.subscribe()` → relay receives REQ
5. Relay responds → `onEvent` callback → events batched (100ms windows) → stored in cache + state
6. Post-filter applied at batch emission → results displayed in `SearchResultsList`

### Error Propagation

- Relay timeout → `onEose` fires → `isSearching` set to false → "No results" shown
- Name resolution failure → name stays in `authorNames` as unresolved → user sees "unresolved: vitor" chip
- Parse error → malformed operators treated as literal text → no crash, graceful degradation
- Non-NIP-50 relay → relay ignores `search` field, returns nothing useful → handled by showing results from other relays
- Individual relay failure → `supervisorScope` isolates failure → other relays continue

### State Lifecycle Risks

- **Subscription churn**: Mitigated by `distinctUntilChanged()` on relay statuses (proven pattern from FeedScreen)
- **Bidirectional update loop**: Prevented by `sourceOfChange` discriminator — TEXT changes don't trigger re-serialization
- **Stale results**: Session-only cache cleared on restart. `flatMapLatest` clears previous subscription results on new query.
- **Memory pressure from result batching**: Bounded by LRU cache (500 entries) and batch window (100ms)

### API Surface Parity

- `SearchBarState` in commons is used by both Android and Desktop today. `AdvancedSearchBarState` extends this pattern but is new.
- `QueryParser`, `SearchQuery`, `KindRegistry`, `SearchResultFilter` placed in commons so Android can adopt later.
- Desktop `FilterBuilders` gets new `searchAdvanced()` methods but existing methods unchanged.

## Acceptance Criteria

### Functional

- [x] Query operators parse correctly: `from:`, `kind:`, `since:`, `until:`, `#tag`, `"phrase"`, `-exclude`, `OR`, `lang:`, `domain:`
- [x] Kind aliases resolve: `kind:note` → kind 1, `kind:article` → kind 30023, etc.
- [x] Pseudo-kinds handled: `kind:reply` and `kind:media` flagged for client-side post-filtering
- [x] Advanced panel expands/collapses below search bar with `expandVertically` + `fadeIn` animation
- [x] Bidirectional sync: text changes update form, form changes update text, no loops (sourceOfChange guard)
- [x] Default search (no kind filter) queries 30+ kinds across 3 filter groups (Android parity)
- [x] OR queries (`bitcoin OR lightning`) send parallel subscriptions, merge + dedup results (max 3 terms)
- [x] Results grouped by type: People, Notes, Articles, Other with sticky section headers
- [x] Note results show content preview, author, timestamp, kind badge
- [x] Search history persists last 20 queries locally
- [x] Saved searches persist across sessions
- [x] Exclusion terms (`-spam`) filtered client-side, not sent to relay
- [x] Empty search bar shows history + saved searches + operator hints
- [x] `Escape` closes panel / clears search (Ctrl+K deferred — needs window-level handler)

### Non-Functional

- [x] Search debounce: 300ms for text, immediate for form toggles
- [x] Max 3 OR terms, 10 authors per query
- [x] Relay subscription churn prevented via `distinctUntilChanged()`
- [x] Result accumulation uses set-based dedup
- [x] `@Immutable` SearchQuery with `ImmutableList` fields
- [x] Session cache cleared on restart
- [x] All query parsing logic unit-tested in commons (roundtrip, edge cases, malformed input)

### Quality Gates

- [x] `QueryParser` + `QuerySerializer` roundtrip tests pass
- [x] `KindRegistry` tests for all aliases + pseudo-kinds
- [x] `SearchFilterFactory` compiles + filter generation correct
- [x] `SearchResultFilter` handles exclusion, reply detection, media detection
- [x] Desktop search screen renders results for all kind types
- [x] `spotlessApply` passes

## Dependencies & Prerequisites

| Dependency | Status | Notes |
|-----------|--------|-------|
| `Filter.search` field | Exists | `quartz/.../Filter.kt` |
| `SearchRelayListEvent` | Exists | `quartz/.../SearchRelayListEvent.kt` (kind 10007) |
| `SearchBarState` | Exists | `commons/.../SearchBarState.kt` — will be extended |
| `SearchParser` | Exists | `commons/.../SearchParser.kt` — bech32 parsing, kept as-is |
| `FilterBuilders` | Exists | `desktopApp/.../FilterBuilders.kt` — extended |
| `rememberSubscription()` | Exists | `desktopApp/.../SubscriptionUtils.kt` |
| `DesktopLocalCache` | Exists | Needs `findNotesStartingWith()` for local note search |
| kotlinx.serialization | In project | For search history JSON persistence |
| kotlinx.collections.immutable | **Add** | For `ImmutableList`/`persistentListOf()` in SearchQuery |

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Bidirectional sync loops | Medium | High | `sourceOfChange` discriminator, TEXT changes preserve raw text |
| Relay subscription explosion (OR + many relays) | Medium | Medium | Cap: 3 OR terms, batch filters per term. Total max ~27 subs |
| NIP-50 relay variability | High | Medium | Graceful degradation — show whatever relays return |
| Name resolution UX confusion | Medium | Medium | Show "unresolved" indicator, autocomplete dropdown |
| O(n^2) result accumulation | Medium | Medium | Batch + set-based dedup via channelFlow |
| Large result sets from broad queries | High | Low | Client-side pagination, "Show more" per section |
| Android `SearchBarState` compatibility | Low | Medium | New `AdvancedSearchBarState`, old class untouched |

## Simplicity Guidance (MVP Scoping)

The full plan is comprehensive. If time-constrained, a minimal viable version can ship with:

**MVP (Phase 1+2+4 subset, ~350 LOC, 5 files):**
- `SearchQuery` data class (no `@Immutable` yet, plain lists)
- `QueryParser` with 5 operators: `from:`, `kind:`, `since:`, `until:`, `#tag`
- `SearchFilterFactory` for filter generation
- Rewritten `SearchScreen.kt` with form panel (no bidirectional sync — form→text only)
- Unit tests for parser

**Cut for MVP:**
- OR queries, `lang:`, `domain:`, `-exclude`
- `QuerySerializer` (not needed without bidirectional sync)
- Saved searches (history only)
- Shimmer loading states
- Keyboard shortcuts beyond Enter/Escape

**Add incrementally:** OR queries → bidirectional sync → saved searches → keyboard shortcuts → NIP-50 extensions

## File Matrix

| File | Status | Module | Action |
|------|--------|--------|--------|
| `SearchQuery.kt` | New | commons/commonMain | Create data class with `@Immutable` |
| `QueryParser.kt` | New | commons/commonMain | Create recursive descent parser |
| `QuerySerializer.kt` | New | commons/commonMain | Create serializer |
| `KindRegistry.kt` | New | commons/commonMain | Create kind alias registry |
| `AdvancedSearchBarState.kt` | New | commons/commonMain | Create state holder with `sourceOfChange` |
| `SearchResultFilter.kt` | New | commons/commonMain | Create post-filter (reusable) |
| `QueryParserTest.kt` | New | commons/commonTest | Create tests |
| `QuerySerializerTest.kt` | New | commons/commonTest | Create tests |
| `KindRegistryTest.kt` | New | commons/commonTest | Create tests |
| `SearchFilterFactory.kt` | New | desktopApp | Create filter factory |
| `AdvancedSearchPanel.kt` | New | desktopApp | Create form panel composable |
| `SearchResultsList.kt` | New | desktopApp | Create results list composable |
| `SearchHistoryStore.kt` | New | desktopApp | Create persistence |
| `SearchScreen.kt` | Rewrite | desktopApp | Integrate advanced search |
| `FeedSubscription.kt` | Extend | desktopApp | Add `createAdvancedSearchSubscription()` |
| `FilterBuilders.kt` | Extend | desktopApp | Add search filter methods |
| `SearchBarState.kt` | Keep | commons/commonMain | Untouched (backward compat) |
| `SearchParser.kt` | Keep | commons/commonMain | Untouched (bech32 parsing still used) |

## Future Considerations

- **AI natural language → query** (deferred) — parse "notes about bitcoin from Jack since January" to operators
- **Local SQLite FTS5 index** — offline search for desktop
- **NIP-90 DVM search** — pay-per-query via Lightning
- **Search relay auto-discovery** — NIP-11 `supported_nips` check for NIP-50
- **NIP-05 name resolution** — async resolve `from:vitor@nostr.com`
- **Saved searches as Nostr events** — portable across devices
- **Search analytics** — trending topics, popular queries

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-03-10-advanced-search-brainstorm.md](docs/brainstorms/2026-03-10-advanced-search-brainstorm.md) — Key decisions: relay-first approach, Twitter-style operators + form UI, extensible kind presets, AI deferred, session-only caching

### Internal References

- Current search screen: `desktopApp/.../ui/SearchScreen.kt`
- Search state: `commons/.../viewmodels/SearchBarState.kt`
- Bech32 parser: `commons/.../search/SearchParser.kt`
- Filter class: `quartz/.../nip01Core/relay/filters/Filter.kt`
- Android kind groups: `amethyst/.../searchCommand/subassemblies/SearchPostsByText.kt`
- FilterBuilders: `desktopApp/.../subscriptions/FilterBuilders.kt`
- Feed subscriptions: `desktopApp/.../subscriptions/FeedSubscription.kt`
- Relay subscription utils: `desktopApp/.../subscriptions/SubscriptionUtils.kt`
- Relay churn fix: `desktopApp/.../ui/FeedScreen.kt:163-167` (`distinctUntilChanged()` pattern)

### External References

- [NIP-50 Search](https://nips.nostr.com/50)
- [NIP-50 extensions](https://github.com/nostr-protocol/nips/blob/master/50.md): `language:`, `domain:`, `sentiment:`, `nsfw:`, `include:spam`
- [kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable) — `ImmutableList`, `persistentListOf()`

## Unanswered Questions

None — all resolved in brainstorm. Implementation details clarified by research agents.
