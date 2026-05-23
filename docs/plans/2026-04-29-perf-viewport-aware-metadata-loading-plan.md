---
title: "perf: Viewport-aware feed metadata loading"
type: perf
status: active
date: 2026-04-29
origin: docs/brainstorms/2026-04-29-feed-metadata-loading-optimization-brainstorm.md
---

# perf: Viewport-aware feed metadata loading

## Enhancement Summary

**Deepened on:** 2026-04-29
**Research agents:** compose-expert, kotlin-coroutines, nostr-expert, relay-client

### Key Improvements
1. Concrete `snapshotFlow` + `debounce` pattern for viewport detection (zero recomposition)
2. `loadMetadataBatched()` implementation with EOSE close (follows Chess helper pattern)
3. Nostr filter sizing: 100 authors max per filter, CLOSE after EOSE
4. `collectLatest` for cancelling stale fetches on scroll change

---

## Overview

Feed metadata (display names, avatars) takes 5+ seconds because the pipeline loads metadata for ALL notes (100+), rate-limits at 20/sec, and creates individual subscriptions per author. Fix with viewport-aware loading + batched author filter.

## Problem Statement

Pre-existing on `main`. Pipeline:
1. `visibleNotes()` returns ALL notes (not viewport-filtered)
2. `MetadataRateLimiter`: 20 pubkeys/sec with 1-sec batch delays
3. Each author gets individual `client.subscribe()` call
4. All subscriptions broadcast to 7 relays

(see brainstorm: `docs/brainstorms/2026-04-29-feed-metadata-loading-optimization-brainstorm.md`)

## Technical Approach

### Phase 1: Viewport-Aware Note Selection

**Goal:** Only fetch metadata for visible notes + 10-item buffer.

**Tasks:**
- [ ] Replace the existing `LaunchedEffect(feedState, subscriptionsCoordinator)` in `FeedScreen.kt:354` with a viewport-aware version using `snapshotFlow`
- [ ] Use `lazyListState.layoutInfo.visibleItemsInfo` inside `snapshotFlow` (NOT in composition — avoids per-frame recomposition)
- [ ] Buffer ±10 items with `coerceIn(0, list.lastIndex)` to prevent IndexOutOfBounds
- [ ] Debounce at 500ms via `.debounce(500)`
- [ ] Use `collectLatest` to cancel stale fetches when scroll position changes

**Implementation pattern (compose-expert + kotlin-coroutines):**

```kotlin
// In FeedScreen, sibling to LazyColumn (not inside it)
LaunchedEffect(lazyListState, feedNotes) {
    snapshotFlow {
        val info = lazyListState.layoutInfo
        if (info.visibleItemsInfo.isEmpty() || feedNotes.isEmpty()) {
            return@snapshotFlow emptyList<Note>()
        }
        val first = (info.visibleItemsInfo.first().index - 10).coerceAtLeast(0)
        val last = (info.visibleItemsInfo.last().index + 10).coerceAtMost(feedNotes.lastIndex)
        feedNotes.subList(first, last + 1)
    }
        .distinctUntilChanged()
        .debounce(500)
        .collectLatest { viewportNotes ->
            if (viewportNotes.isNotEmpty()) {
                // Fast path: batched metadata for visible authors
                val authors = viewportNotes.mapNotNull { it.author?.pubkeyHex }.distinct()
                subscriptionsCoordinator.loadMetadataBatched(authors)
                // Also load reactions for visible notes
                subscriptionsCoordinator.loadMetadataForNotes(viewportNotes)
            }
        }
}
```

**Key insights:**
- `snapshotFlow` reads layout info outside composition → zero recomposition cost
- `collectLatest` cancels in-flight `loadMetadataBatched` when scroll changes
- `distinctUntilChanged` skips if same indices visible after debounce
- `feedNotes` captured in `LaunchedEffect` key ensures re-launch when feed data changes

**Files:**
- `desktopApp/.../ui/FeedScreen.kt` — replace metadata LaunchedEffect

### Phase 2: Batched Author Subscription

**Goal:** One relay subscription per batch instead of N individual ones.

**Tasks:**
- [ ] Add `loadMetadataBatched(pubkeys)` to `FeedMetadataCoordinator`
- [ ] Bypass rate limiter — subscribe directly via `client.subscribe()`
- [ ] Single `Filter(kind:0, authors:pubkeys, limit:pubkeys.size)` sent to index relays
- [ ] Close subscription after EOSE from all relays (one-shot fetch)
- [ ] 5-second timeout to prevent hanging on slow relays
- [ ] Deduplicate against `queuedPubkeys` to avoid double-fetch with background path

**Implementation pattern (relay-client + nostr-expert):**

```kotlin
// In FeedMetadataCoordinator — follows ChessRelayFetchHelper pattern
fun loadMetadataBatched(pubkeys: List<HexKey>, timeoutMs: Long = 5_000L) {
    val newPubkeys = pubkeys.filter { it !in queuedPubkeys }.distinct()
    if (newPubkeys.isEmpty()) return
    queuedPubkeys.addAll(newPubkeys)

    scope.launch {
        val filter = Filter(
            kinds = listOf(MetadataEvent.KIND),
            authors = newPubkeys.take(100), // max 100 per filter
            limit = newPubkeys.size,
        )
        val filterMap = indexRelays.associateWith { listOf(filter) }
        val subId = newSubId()
        val eoseReceived = mutableSetOf<NormalizedRelayUrl>()
        val allEose = CompletableDeferred<Unit>()

        val listener = object : SubscriptionListener {
            override fun onEvent(event: Event, isLive: Boolean, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                onEvent?.invoke(event, relay)
            }
            override fun onEose(relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                eoseReceived.add(relay)
                if (eoseReceived.size >= indexRelays.size) allEose.complete(Unit)
            }
        }

        client.subscribe(subId, filterMap, listener)
        withTimeoutOrNull(timeoutMs) { allEose.await() }
        client.unsubscribe(subId)
    }
}
```

**Key insights (nostr-expert):**
- Kind 0 is replaceable — relay returns one event per pubkey, bulk lookup is efficient
- 100 authors per filter is safe for index relays (purplepag.es, profiles.nostr1.com)
- CLOSE after EOSE — transient viewport set doesn't need live updates
- No `since` on cold load — relay returns latest replaceable event regardless
- Pattern matches existing `ChessRelayFetchHelper.fetchEvents()` idiom in codebase

**Files:**
- `commons/.../relayClient/assemblers/FeedMetadataCoordinator.kt` — add `loadMetadataBatched()`

### Phase 3: Progressive Background Loading

**Goal:** Pre-warm cache for off-screen notes.

**Tasks:**
- [ ] After viewport metadata loads via batched path, queue remaining feed authors through existing `loadMetadataForNotes()` (rate-limited, low priority)
- [ ] This runs in background — no UI impact

**Files:**
- `desktopApp/.../ui/FeedScreen.kt` — secondary background pass after viewport pass

## Acceptance Criteria

- [ ] Visible note metadata loads within 1-2 seconds of feed render
- [ ] Scrolling to new notes triggers metadata fetch within 500ms
- [ ] No per-frame recomposition from scroll observation (verify with Layout Inspector)
- [ ] No regression for off-screen notes (still loads via background path)
- [ ] `./gradlew :desktopApp:compileKotlin` succeeds
- [ ] `./gradlew :commons:compileKotlinJvm` succeeds
- [ ] `./gradlew spotlessApply` passes
- [ ] Existing tests pass

## Key Files

| File | Purpose |
|------|---------|
| `desktopApp/.../ui/FeedScreen.kt:354` | LaunchedEffect trigger (replace) |
| `commons/.../relayClient/assemblers/FeedMetadataCoordinator.kt` | Add `loadMetadataBatched()` |
| `commons/.../relayClient/preload/MetadataRateLimiter.kt` | Bypassed for viewport path |
| `commons/.../chess/ChessRelayFetchHelper.kt:82` | Reference pattern for one-shot fetch |

## Sources & References

### Origin
- **Brainstorm:** [docs/brainstorms/2026-04-29-feed-metadata-loading-optimization-brainstorm.md](docs/brainstorms/2026-04-29-feed-metadata-loading-optimization-brainstorm.md)

### Internal References
- ChessRelayFetchHelper (one-shot pattern): `commons/.../chess/ChessRelayFetchHelper.kt:82-131`
- MetadataFilterAssembler (persistent sub): `commons/.../relayClient/assemblers/MetadataFilterAssembler.kt`
- FeedContentState.visibleNotes: `commons/.../ui/feeds/FeedContentState.kt:77`
- DefaultIndexerRelayList: `amethyst/.../model/Constants.kt`
