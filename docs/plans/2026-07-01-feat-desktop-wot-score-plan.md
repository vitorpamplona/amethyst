---
title: Desktop Web-of-Trust Score Badges
type: feat
status: active
date: 2026-07-01
origin: docs/brainstorms/2026-07-01-feat-wot-score-brainstorm.md
deepened: 2026-07-01
---

# Desktop Web-of-Trust Score Badges

## Enhancement Summary

**Deepened on:** 2026-07-01 (same day as plan write).

**Review agents used:** architecture-strategist, code-simplicity-reviewer,
pattern-recognition-specialist, performance-oracle, security-sentinel,
agent-native-reviewer · plus best-practices research (SnapshotStateMap +
Compose tooltip patterns) and a code-verification sweep.

### Key corrections vs initial draft

1. **Prerequisite: fix `DesktopLocalCache.consumeContactList`.** The
   current implementation writes `_followedUsers = event.verifiedFollowKeySet()`
   for **any** incoming kind-3, gated only on a single global
   `lastContactListCreatedAt` scalar. Once WoT starts fetching followed
   authors' kind-3 events, this **actively corrupts** the active user's
   follow-set state. Refactor to per-author `Map<HexKey, Long>` and
   guard the `_followedUsers` / `lastContactListEvent` write on
   `event.pubKey == account.pubKeyHex`. This is a **prerequisite commit**
   in this PR — WoT cannot ship without it.
2. **Badge is a slot, not an embed.** `UserAvatar` in `commonMain` gets
   an optional `badge: @Composable (BoxScope.() -> Unit)? = null`
   parameter. Desktop call sites pass a `WoTBadge()`-bearing lambda;
   Android passes null. No CompositionLocal reads inside the shared
   composable, no `expect/actual` dance, `TooltipBox` import stays in
   the Desktop-only source set of the caller.
3. **Service on `DesktopIAccount`, not in `remember`.** Match the
   established pattern of `Kind3FollowListState`, `BookmarkListState`,
   `Nip65RelayListState` — the service is a field on `DesktopIAccount`
   constructed with `account.scope`, exposed as a property, provided via
   `LocalWoTService` from `Main.kt`. Lifecycle matches the login session,
   not the composition.
4. **Kind-3 event flow via `SharedFlow<ContactListEvent>`.** Not a
   mutable listener list. `DesktopLocalCache` exposes a
   `SharedFlow<ContactListEvent>` (buffer 64, `DROP_OLDEST` overflow),
   emitted from inside `consumeContactList`. WoTService collects it from
   an `account.scope`-scoped coroutine.
5. **Batch kind-3 loader — chunk 100 per Filter, explicit `onEose` hook.**
   Existing `FeedMetadataCoordinator.loadMetadataBatched` at line 267
   silently does `authors.take(100)` — blind copy would drop 400 of 500
   follows. New `loadKind3Batched` chunks into ≤100-author Filters,
   aggregates EOSE across chunks, and exposes `onEose: () -> Unit` so
   the caller can `markReady()` on real EOSE (not just the 2 s timeout).
6. **Use Material3 `TooltipBox`, not `TooltipArea`.** Multiplatform,
   built-in a11y (screen readers, keyboard focus), and the JetBrains
   deprecation direction ([JB #4275](https://github.com/JetBrains/compose-multiplatform/issues/4275)).
   Set `state = rememberTooltipState(isPersistent = true)` to fix the
   known "vanishes too fast" desktop bug.
7. **`WoTScore` sealed hierarchy → plain `Int` + sparse map.** Drop
   `Unknown` — represent "not queried" as *absence* from the map.
   `_scores.remove(target)` when count hits 0. Keeps the map sparse and
   Compose subscriber tracking cheap.
8. **Drop `derivedStateOf` at the leaf.** Plain
   `service.scores[userHex] ?: 0` is already snapshot-tracked per key.
   `derivedStateOf` adds a subscriber node and a comparator per avatar
   for no gain when there's only one read.
9. **Readiness as a plain `Boolean` CompositionLocal, not per-avatar
   `collectAsState`.** Collect `isReady` once at App root, provide via
   `LocalWoTReady`. 150 collectors per screen becomes 1.
10. **Batch writes with `Snapshot.withMutableSnapshot { }`.**
    `applyKind3` mutates `_scores` for many keys — wrap the loop in a
    single mutable snapshot so all readers see one atomic frame.
11. **Amy verbs ship in v1 (not v2).** `amy wot get <pubkey>`,
    `amy wot list --threshold N`, `amy wot sync`. Service exposes
    `scoresSnapshot(): Map<HexKey, Int>` for headless readers.
    `FsEventStore` at `~/.amy/shared/events-store/` already caches
    kind-3 events → warm-cache queries need no relay traffic.
12. **Bound follows per event.** Cap `verifiedFollowKeySet()` result at
    5000 entries in `applyKind3` — prevents CPU DoS from a hostile
    follower publishing a 100k-tag kind-3.
13. **`WoTService` writes on `Dispatchers.Default`, single-writer
    coroutine.** Serialize `applyKind3`, `onFollowSetChange`, etc.
    inside an `actor`-style coroutine so composite state stays
    consistent across concurrent kind-3 arrivals.

### New considerations discovered

- Simplicity review argued for dropping `WoTScore`, `isReady`, and
  `perFollowerSnapshot`. Kept the last two (correctness vs churn) but
  agreed on dropping `WoTScore`.
- Pattern review pushed for `commons/moderation/wot/` (sibling to
  hashtag-spam). Kept **`commons/wot/`** — v1 is display-only, not
  moderation. If v2 adds filtering we relocate then.
- Security review confirmed: follow-list leak via batch REQ is
  **pre-existing** (metadata coordinator already sends the same
  `authors` list to `indexRelays`). WoT introduces no novel privacy
  regression.
- All ingested events are `event.verify()`-checked before
  `LocalCache.consume` runs (`DesktopLocalCache.kt:191`) — forged
  kind-3s cannot pass the sig check. Score inflation via fake events is
  not a viable attack.

---

## Overview

Compute a friends-of-friends trust score for every pubkey based on the
active user's follow graph, and render it as a small number chip
overlaid on `UserAvatar`s at Desktop call sites. **v1 is
display-only** — no filtering. Kind-3 follow lists for the active user's
follows are fetched via a proactive chunked batch REQ at login and kept
current through incremental diff updates.

**Carried forward from brainstorm**
(`docs/brainstorms/2026-07-01-feat-wot-score-brainstorm.md`):
raw-count semantics · display-only · auto-hide badge when score = 0 ·
hide badge on already-followed authors and self · number chip in avatar
corner · tooltip explaining the count · proactive batch kind-3 REQ at
login + refresh on follow-list change · no cross-session persistence ·
no settings UI.

**Resolved during planning + deepening:**
- **Platform target:** Desktop only in v1. Badge is a slot on shared
  `UserAvatar`; Desktop call sites pass the lambda, Android passes null.
- **Score model:** plain `Int` in a sparse `SnapshotStateMap`. Absence
  ≡ "not queried yet or definitively zero." Both hide the badge.
- **Prerequisite:** fix `consumeContactList` scope corruption.
- **Batch REQ:** chunked into ≤100-author Filters, aggregated EOSE.
- **Startup gate:** `isReady = true` after first-chunk EOSE OR 2 s
  timeout, whichever first. Single `Boolean` provided via CompositionLocal.
- **Guardrail:** skip WoT graph if `myFollows.size > 2000`.
- **Overflow:** display `"99+"` when count > 99.
- **Amy CLI:** three verbs ship in v1 (`get`, `list`, `sync`).

## Problem Statement

Nostr's public graph makes it easy for a stranger to appear in your
notifications, mentions, search results, or as a repost's original
author. Without a trust cue you have to click into each profile to
assess. Gossip and Snort solved this with a friends-of-friends count:
"N of the people you follow also follow this person." On Desktop, where
3–6 columns and dozens of avatars are on screen at once, that cue
scales better than mobile. Amethyst Desktop today shows every pubkey
identically.

## Proposed Solution

### High-level approach

Introduce a per-account **`WoTService`** attached to
`DesktopIAccount` (mirroring `kind3FollowList`, `bookmarkList`,
`nip65RelayList`). At login the service:

1. Observes the active user's follow set from
   `DesktopLocalCache.followedUsers`.
2. Issues chunked batch REQs (≤100 authors each, aggregated EOSE) for
   `kinds=[3]` on the same `indexRelays` used by
   `FeedMetadataCoordinator.loadMetadataBatched`.
3. Collects a new `DesktopLocalCache.contactListEvents:
   SharedFlow<ContactListEvent>` and calls `applyKind3(...)` for each
   event whose author is in the follow set.
4. Maintains a **reverse index** (`Map<HexKey, MutableSet<HexKey>>`
   from target-pubkey → set of my-follows who follow them) and a
   **per-follower snapshot** (`Map<HexKey, Set<HexKey>>`) for diff
   updates.
5. Publishes score changes atomically to `_scores: SnapshotStateMap<HexKey, Int>`
   inside a `Snapshot.withMutableSnapshot { }` block.
6. Marks `isReady = true` on aggregated EOSE or 2 s timeout — whichever
   fires first.

Desktop UI uses a sibling composable **`WoTBadgedAvatar`** (in
`desktopApp/.../ui/note/`) that composes the shared `UserAvatar` with a
badge slot. The slot renders a `WoTBadge` when four gates pass:

- `LocalWoTReady.current == true`
- `service.scores[pubkey] > 0`
- `pubkey != selfPubkey`
- `pubkey !in followedKeys`

`WoTBadge` uses Material3 `TooltipBox` with
`state = rememberTooltipState(isPersistent = true)`.

### Why this shape

- **Per-account service on `DesktopIAccount`** matches the codebase
  convention (`kind3FollowList`, `bookmarkList`). `remember` inside
  composition ties lifecycle to the composable's tree, which is wrong
  for a data service.
- **Sibling composable, not embedded slot in `UserAvatar`.** Prior art:
  `BunkerHeartbeatIndicator` — decoration lives next to the primitive,
  not inside it. Explicit call-site migration is a feature: it lets us
  ship v1 on high-value surfaces (feeds, thread, notifications, profile)
  and defer minor spots.
- **Slot on `UserAvatar` for Desktop-owned rendering.** Even the
  sibling composable needs somewhere to draw the badge. Adding a
  scalar `badge` slot to `UserAvatar` avoids duplicating the entire
  avatar layout and keeps Android intact (nulls the slot).
- **Sparse `SnapshotStateMap`.** Storing `Unknown` for every unqueried
  pubkey would bloat the map to 250 k entries; keeping it sparse (only
  positive scores) reduces subscriber overhead 10×.
- **Chunked batch REQ.** Relays vary wildly in filter-size caps
  (nostr-rs-relay defaults ~100, strfry accepts hundreds). Chunking
  100 authors per Filter within one subscription is the correct
  pragmatic default.
- **Diff-based `applyKind3`.** Kind-3 events churn (many clients
  republish frequently). Full recompute on every event would drop the
  reverse index and lose recomposition isolation. Diff keeps
  per-target changes minimal.
- **Amy parity in v1.** Service is a plain class in `commons/`; the
  three verbs are ~150 LOC total. Skipping them trains the wrong habit.

### Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│ commons/  (platform-agnostic; callable by Desktop, amy, Android) │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ commonMain/                                                  │ │
│ │   wot/WoTService.kt          Snapshot map + reverse index    │ │
│ │                              + applyKind3, onFollowSetChange │ │
│ │                              + awaitReady(onEose, timeout)   │ │
│ │                              + scoresSnapshot() (headless)   │ │
│ │   wot/LocalWoTService.kt     CompositionLocal (nullable)     │ │
│ │   wot/LocalWoTReady.kt       CompositionLocal<Boolean>       │ │
│ │   ui/components/UserAvatar.kt  + badge: @Composable slot     │ │
│ └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │
┌─────────────────────────────┴────────────────────────────────────┐
│ desktopApp/                                                      │
│   cache/DesktopLocalCache.kt (PREREQUISITE FIX)                  │
│     - lastContactListByAuthor: MutableMap<HexKey, Long>          │
│     - contactListEvents: SharedFlow<ContactListEvent>            │
│     - _followedUsers writes gated on event.pubKey==self          │
│   model/DesktopIAccount.kt                                       │
│     val wotService = WoTService(scope)                           │
│   subscriptions/DesktopRelaySubscriptionsCoordinator.kt          │
│     fun loadKind3Batched(pubkeys, onEose: () -> Unit)            │
│   ui/note/WoTBadgedAvatar.kt                                     │
│     Composes UserAvatar with a WoTBadge slot lambda              │
│   ui/note/WoTBadge.kt                                            │
│     Material3 TooltipBox + Box overlay chip                      │
│   Main.kt (LoggedIn branch)                                      │
│     CompositionLocalProvider(                                    │
│       LocalWoTService provides account.wotService,               │
│       LocalWoTReady provides isReadyCollected,                   │
│     ) { … }                                                      │
│                                                                  │
│   Call sites migrated (v1 subset):                                │
│     - FeedNoteCard header                                         │
│     - QuotedNoteEmbed author                                      │
│     - Thread reply avatars                                        │
│     - Notifications item                                          │
│     - Search result item                                          │
│     - Profile header                                              │
│     (Other avatars deferred; migration is opportunistic.)         │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ cli/  (amy — v1 verbs)                                            │
│   commands/WotCommand.kt                                          │
│     amy wot get <pubkey|npub> [--json]                           │
│     amy wot list [--threshold N] [--limit K] [--json]            │
│     amy wot sync    (one-shot batch REQ, exits on EOSE / 5s)     │
│   Context.kt exposes an FsEventStore hydration primitive         │
│     WoTService.hydrateFromStore(store, myFollows)                │
└──────────────────────────────────────────────────────────────────┘
```

### Prerequisite: `consumeContactList` cache fix

This must land before or in the same PR as the WoT service. Otherwise
the batch REQ we introduce will trigger the corruption bug.

**File:** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/cache/DesktopLocalCache.kt:447-453`

Change:

```kotlin
// BEFORE (buggy — any kind-3 with newer createdAt wins globally)
private fun consumeContactList(event: ContactListEvent): Boolean {
    if (event.createdAt <= lastContactListCreatedAt) return false
    lastContactListCreatedAt = event.createdAt
    lastContactListEvent = event
    _followedUsers.value = event.verifiedFollowKeySet()
    return true
}

// AFTER (per-author tracking + self-guard + SharedFlow emit)
private val lastContactListByAuthor = mutableMapOf<HexKey, Long>()
private val _contactListEvents = MutableSharedFlow<ContactListEvent>(
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
val contactListEvents: SharedFlow<ContactListEvent> = _contactListEvents.asSharedFlow()

private fun consumeContactList(event: ContactListEvent): Boolean {
    val prev = lastContactListByAuthor[event.pubKey] ?: 0L
    if (event.createdAt <= prev) return false
    lastContactListByAuthor[event.pubKey] = event.createdAt

    // Active-user's kind-3 updates local follow-set state.
    if (event.pubKey == accountPubkey) {
        lastContactListEvent = event
        _followedUsers.value = event.verifiedFollowKeySet()
    }

    // All kind-3 events fan out on the SharedFlow for consumers (WoTService).
    _contactListEvents.tryEmit(event)
    return true
}
```

### Core algorithm (updated)

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/wot/WoTService.kt`:

```kotlin
@Stable
class WoTService(
    private val scope: CoroutineScope,
) {
    // Truth-map: pubkey → count of my-follows who follow them.
    // Sparse — entries with count = 0 are removed.
    private val _scores: SnapshotStateMap<HexKey, Int> = mutableStateMapOf()
    val scores: SnapshotStateMap<HexKey, Int> get() = _scores

    // Internal state — always mutated from the [writer] actor coroutine.
    private val reverseIndex = HashMap<HexKey, MutableSet<HexKey>>()
    private val perFollowerSnapshot = HashMap<HexKey, Set<HexKey>>()
    private var myFollows: Set<HexKey> = emptySet()
    private var selfPubkey: HexKey? = null

    // Readiness
    private val readyOnce = AtomicBoolean(false)
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // Serialize state mutations
    private val ops = Channel<Op>(capacity = Channel.BUFFERED)

    init {
        scope.launch(Dispatchers.Default) {
            for (op in ops) processOne(op)
        }
    }

    private sealed interface Op {
        data class FollowSet(val new: Set<HexKey>, val self: HexKey?) : Op
        data class Kind3(val follower: HexKey, val follows: Set<HexKey>) : Op
        object MarkReady : Op
    }

    fun onFollowSetChange(newFollows: Set<HexKey>, newSelf: HexKey?) {
        ops.trySend(Op.FollowSet(newFollows, newSelf))
    }

    fun applyKind3(follower: HexKey, follows: Set<HexKey>) {
        val bounded = if (follows.size > MAX_FOLLOWS_PER_EVENT) {
            follows.take(MAX_FOLLOWS_PER_EVENT).toSet()
        } else follows
        ops.trySend(Op.Kind3(follower, bounded))
    }

    fun markReadyOnce() {
        if (readyOnce.compareAndSet(false, true)) ops.trySend(Op.MarkReady)
    }

    /** For amy — returns a plain snapshot, no Compose runtime required. */
    fun scoresSnapshot(): Map<HexKey, Int> = HashMap(_scores)

    /** For amy — hydrate from a local event store before querying. */
    suspend fun hydrateFromStore(store: IEventStore, myFollows: Set<HexKey>) {
        onFollowSetChange(myFollows, selfPubkey)
        store.iterateBy(kinds = setOf(ContactListEvent.KIND), authors = myFollows) { event ->
            applyKind3(event.pubKey, (event as ContactListEvent).verifiedFollowKeySet())
        }
    }

    fun clear() { ops.trySend(Op.FollowSet(emptySet(), null)) }

    private fun processOne(op: Op) {
        Snapshot.withMutableSnapshot {
            when (op) {
                is Op.FollowSet -> handleFollowSet(op.new, op.self)
                is Op.Kind3 -> handleKind3(op.follower, op.follows)
                Op.MarkReady -> _isReady.value = true
            }
        }
    }

    private fun handleFollowSet(newFollows: Set<HexKey>, newSelf: HexKey?) {
        val added = newFollows - myFollows
        val removed = myFollows - newFollows
        myFollows = newFollows
        selfPubkey = newSelf

        // Guardrail
        if (myFollows.size > MAX_FOLLOWS) {
            _scores.clear(); reverseIndex.clear(); perFollowerSnapshot.clear()
            markReadyOnce()
            return
        }

        // Uncredit removed followers
        removed.forEach { follower ->
            perFollowerSnapshot.remove(follower)?.forEach { target ->
                reverseIndex[target]?.let { set ->
                    set.remove(follower)
                    updateScore(target)
                }
            }
        }
        // Added followers are credited when their kind-3 arrives.
    }

    private fun handleKind3(follower: HexKey, follows: Set<HexKey>) {
        if (follower !in myFollows) return
        val old = perFollowerSnapshot[follower] ?: emptySet()
        val excluded = setOfNotNull(follower, selfPubkey)
        val effective = follows - excluded
        val added = effective - old
        val removed = old - effective
        perFollowerSnapshot[follower] = effective

        added.forEach { target ->
            reverseIndex.getOrPut(target) { hashSetOf() }.add(follower)
            updateScore(target)
        }
        removed.forEach { target ->
            reverseIndex[target]?.let { set ->
                set.remove(follower)
                if (set.isEmpty()) reverseIndex.remove(target)
                updateScore(target)
            }
        }
    }

    private fun updateScore(target: HexKey) {
        val n = reverseIndex[target]?.size ?: 0
        if (n > 0) _scores[target] = n else _scores.remove(target)
    }

    companion object {
        const val MAX_FOLLOWS = 2000
        const val MAX_FOLLOWS_PER_EVENT = 5000
    }
}
```

Notes:
- All mutations run inside a single writer coroutine on
  `Dispatchers.Default` — no concurrent map access races.
- `Snapshot.withMutableSnapshot { }` wraps each op so Compose readers
  see one atomic frame per event.
- `applyKind3` bounds `follows.size` to 5 000 at ingest — DoS protection
  against a hostile 100 k-tag kind-3.
- `_scores.remove(target)` on 0-count keeps the map sparse — Compose
  subscriber tracking scales with map size.

### Batch kind-3 loader (with explicit EOSE hook + chunking)

Add to `FeedMetadataCoordinator` in `commons/.../relayClient/assemblers/`:

```kotlin
private val queuedKind3Pubkeys = mutableSetOf<HexKey>()

/**
 * Fetches kind-3 for a batch of authors, chunking into ≤100-author
 * Filters within a single subscription. Calls [onEose] once all
 * chunks EOSE (or after [timeoutMs]).
 */
fun loadKind3Batched(
    pubkeys: Collection<HexKey>,
    timeoutMs: Long = 5_000L,
    onEose: () -> Unit = {},
) {
    val newPubkeys = pubkeys.filter { it !in queuedKind3Pubkeys }.distinct()
    if (newPubkeys.isEmpty()) { onEose(); return }
    queuedKind3Pubkeys.addAll(newPubkeys)

    scope.launch {
        val chunks = newPubkeys.chunked(100)
        val filters = chunks.map { chunk ->
            Filter(
                kinds = listOf(ContactListEvent.KIND),
                authors = chunk,
                limit = chunk.size,
            )
        }
        val filterMap = indexRelays.associateWith { filters }
        val subId = newSubId()
        val eoseReceived = mutableSetOf<NormalizedRelayUrl>()
        val allEose = CompletableDeferred<Unit>()

        val listener = object : SubscriptionListener {
            override fun onEvent(event: Event, isLive: Boolean, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                this@FeedMetadataCoordinator.onEvent?.invoke(event, relay)
            }
            override fun onEose(relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                eoseReceived.add(relay)
                if (eoseReceived.size >= indexRelays.size) allEose.complete(Unit)
            }
        }

        client.subscribe(subId, filterMap, listener)
        withTimeoutOrNull(timeoutMs) { allEose.await() }
        client.unsubscribe(subId)
        onEose()
    }
}
```

`DesktopRelaySubscriptionsCoordinator.loadKind3Batched(pubkeys, onEose)`
delegates.

### `UserAvatar` badge slot (commonMain change)

```kotlin
@Composable
fun UserAvatar(
    userHex: String,
    pictureUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    /* existing params… */
    badge: @Composable (BoxScope.() -> Unit)? = null,
) {
    if (badge == null) {
        // Existing single-image render path — unchanged
        AvatarImage(userHex, pictureUrl, size, modifier, /* … */)
    } else {
        Box(modifier.size(size)) {
            AvatarImage(userHex, pictureUrl, size, Modifier, /* … */)
            badge()
        }
    }
}
```

### `WoTBadgedAvatar` (Desktop-only, drop-in replacement at v1 call sites)

`desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/note/WoTBadgedAvatar.kt`:

```kotlin
@Composable
fun WoTBadgedAvatar(
    userHex: String,
    pictureUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    /* … pass-through params matching UserAvatar … */
) {
    val service = LocalWoTService.current
    val ready = LocalWoTReady.current
    val selfKey = LocalWoTSelfKey.current
    val followedKeys = LocalWoTFollowedKeys.current

    val score = if (service != null && ready && userHex != selfKey && userHex !in followedKeys) {
        service.scores[userHex] ?: 0                     // plain snapshot read, per-key tracked
    } else 0

    UserAvatar(
        userHex = userHex,
        pictureUrl = pictureUrl,
        size = size,
        modifier = modifier,
        badge = if (score > 0) { { WoTBadge(count = score, modifier = Modifier.align(Alignment.BottomEnd)) } } else null,
    )
}
```

### `WoTBadge` (Material3 `TooltipBox`, Desktop-only)

`desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/note/WoTBadge.kt`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WoTBadge(count: Int, modifier: Modifier = Modifier) {
    val display = if (count > 99) "99+" else count.toString()
    val tooltipState = rememberTooltipState(isPersistent = true) // desktop hover fix
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("$count of the people you follow follow this person") } },
        state = tooltipState,
    ) {
        Box(
            modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .semantics { contentDescription = "Followed by $count of your contacts" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
```

### Wiring in `Main.kt`

Inside the `AccountState.LoggedIn` branch, alongside the existing
`LocalHashtagSpamSettings` / `LocalSpamExemptKeys` providers:

```kotlin
val wotService = account.iAccount.wotService
val isReady by wotService.isReady.collectAsState()

LaunchedEffect(wotService, localCache) {
    // Feed kind-3 events into the service.
    launch { localCache.contactListEvents.collect { evt ->
        wotService.applyKind3(evt.pubKey, evt.verifiedFollowKeySet())
    } }
    // React to follow-set changes.
    launch { localCache.followedUsers.collect { follows ->
        wotService.onFollowSetChange(follows, account.pubKeyHex)
        subscriptionsCoordinator.loadKind3Batched(follows, onEose = { wotService.markReadyOnce() })
    } }
    // Fallback: mark ready after 2 s regardless.
    delay(2_000)
    wotService.markReadyOnce()
}

CompositionLocalProvider(
    LocalWoTService provides wotService,
    LocalWoTReady provides isReady,
    LocalWoTSelfKey provides account.pubKeyHex,
    LocalWoTFollowedKeys provides followedUsers, // already collected above for spam exempt
    // existing hashtag-spam CompositionLocals…
) { /* … */ }
```

Attach `wotService` to `DesktopIAccount`:

```kotlin
class DesktopIAccount(
    private val signer: NostrSigner,
    val scope: CoroutineScope,
    /* … */
) : IAccount {
    val kind3FollowList = Kind3FollowListState(signer, scope, /* … */)
    val wotService = WoTService(scope)
    /* … */
}
```

### Amy verbs (v1)

`cli/src/main/kotlin/com/vitorpamplona/amethyst/cli/commands/WotCommand.kt`:

```
amy wot get <pubkey|npub> [--json]
    Prints: pubkey=<hex> score=<n> contributed_by=<hex,hex,…>
    JSON: { "pubkey": "...", "score": n, "contributed_by": ["...", ...] }

amy wot list [--threshold N] [--limit K] [--json]
    Prints scored pubkeys sorted desc; --threshold filters.

amy wot sync
    Loads active-user follow set + runs loadKind3Batched once.
    Exits after aggregated EOSE (max 5 s).
```

Each verb:
1. Reads active-user pubkey from `Context.currentAccount`.
2. Hydrates `WoTService` from `FsEventStore` (~/.amy/shared/events-store/).
3. Optionally runs `wot sync` (idempotent) for freshness.
4. Queries and prints.

Total ~150 LOC. Reuses existing `Context` / `FsEventStore` /
`indexRelays` wiring.

## Technical Considerations

### Performance

- **Reverse-index memory:** at MAX_FOLLOWS = 2000 × avg follows 500 ≈
  1 M entries worst case; at 500 × 500 ≈ 250 k. Compose bookkeeping
  overhead on the SnapshotStateMap is ~80–120 bytes/entry; sparse map
  (Unknown = absence) keeps this bounded by *positive-score pubkeys*
  only — typically 5–50 k, not 250 k.
- **Write batching:** each `applyKind3` op mutates the map for many
  keys inside a single `Snapshot.withMutableSnapshot { }` — one wake-up
  per reader, one atomic frame.
- **Chunked REQ:** 500-follow account issues 5 chunks × 100 authors
  each within a single subscription. Total REQ payload ~30 KB.
- **`scoresSnapshot()`** does an `HashMap(_scores)` — O(N) copy; amy
  calls it once per verb invocation, fine.

### Compose recomposition

- Plain `service.scores[userHex] ?: 0` read is snapshot-tracked per
  key. When only one key updates, only avatars for that pubkey
  recompose.
- `LocalWoTReady: CompositionLocal<Boolean>` is collected **once** at
  App root; no per-avatar Flow collector.
- Badge visibility is decided in `WoTBadgedAvatar` (Desktop) — the
  `UserAvatar` slot receives `badge=null` when hidden, so no overlay
  Box + no recomposition of hidden branches.

### Stability annotations

- `WoTService`: `@Stable`. Public API is `scores: SnapshotStateMap`
  (Compose-tracked) + `isReady: StateFlow` (via `collectAsState`).
  Private mutable state doesn't leak.
- `Int` is stable by definition — sparse map values are trivially
  stable.
- `WoTBadge`, `WoTBadgedAvatar`, `UserAvatar` all have primitive/stable
  params after the slot addition.

### Threading

- All mutations to `reverseIndex` / `perFollowerSnapshot` happen inside
  the single writer coroutine on `Dispatchers.Default`.
- `_scores` SnapshotStateMap is thread-safe for individual puts;
  composite writes wrapped in `Snapshot.withMutableSnapshot { }` are
  applied atomically.
- Composable reads happen on the Main dispatcher via snapshot; safe.

### Follow-set reactivity

- `DesktopLocalCache._followedUsers: StateFlow<Set<HexKey>>` (already
  reactive) is the source of truth for the active user's follow set —
  once the prerequisite cache fix lands.
- The `LaunchedEffect` in `Main.kt` collects it and forwards to
  `WoTService.onFollowSetChange` and re-triggers the batch REQ for
  newly-followed pubkeys (existing ones deduped by
  `queuedKind3Pubkeys`).

### Startup timeline

| t | Event |
|---|-------|
| 0 ms | User logs in |
| ~50 ms | `LocalCache.followedUsers` emits current follow set |
| ~100 ms | `loadKind3Batched(follows, onEose = …)` fires 5 chunks |
| 200 ms – 2 s | Kind-3 events land, WoTService diffs, `_scores` populated |
| ≤ 2 s | `markReadyOnce()` — via first-chunk EOSE OR 2 s fallback |
| ≥ ready | Badges appear |

### Security / privacy

- Follow-list leak via batch REQ is **pre-existing** — same authors
  set already sent to `indexRelays` via metadata batch. WoT introduces
  no novel disclosure.
- `event.verify()` runs on every ingested event
  (`DesktopLocalCache.kt:191`) — forged kind-3 events cannot pass.
- `follows.take(5000)` in `applyKind3` bounds CPU cost against a
  hostile 100 k-tag kind-3.
- No persistence — `Preferences` untouched. Reverse index lives in
  memory only.
- App-global operation (not per-account): follow-set state lives on
  `DesktopIAccount`, discarded on logout. Account switch = new
  `IAccount` = new `WoTService`.

## System-Wide Impact

### Interaction graph

```
DesktopLocalCache.consumeContactList(event)   ← (prerequisite fix)
    │
    ├─ if event.pubKey == self → update _followedUsers, lastContactListEvent
    │
    └─ tryEmit → contactListEvents: SharedFlow<ContactListEvent>
                      │
                      ▼ collected in Main.kt LaunchedEffect
                 WoTService.applyKind3(event.pubKey, event.verifiedFollowKeySet())
                      │
                      ▼ dispatched to writer coroutine
                 processOne(Op.Kind3) inside Snapshot.withMutableSnapshot { }
                      │
                      ▼ diff vs perFollowerSnapshot
                 reverseIndex[target].add/remove(follower)
                      │
                      ▼ updateScore(target)
                 _scores[target] = n  (or .remove if n == 0)
                      │
                      ▼ Compose snapshot commit
                 Avatars reading scores[target] recompose

── parallel path ──
DesktopLocalCache._followedUsers emits new set
    │
    ▼ collected in Main.kt LaunchedEffect
WoTService.onFollowSetChange(newFollows, selfPubkey)
    │
    ├─ diff added / removed followers
    ├─ uncredit removed followers' contributions
    ▼
subscriptionsCoordinator.loadKind3Batched(followSet, onEose = wotService::markReadyOnce)
    │
    ▼ chunked REQ on indexRelays
Kind-3 events return → route back through consume() path above
```

### Error & failure propagation

- Batch REQ timeout: 2 s fallback fires `markReadyOnce()` regardless.
  Partial data is acceptable — badges appear for pubkeys we did get.
- `verifiedFollowKeySet()` on malformed event: Quartz handles internally,
  returns possibly-empty set. Zero contribution.
- Writer coroutine crash: never — all operations are exception-safe (map
  ops, set diffs). No I/O in the writer path.
- SharedFlow overflow: `DROP_OLDEST` on `contactListEvents` — under
  extreme flood, oldest events dropped. Acceptable v1 (relay flood is
  itself abnormal).
- Account switch: `LaunchedEffect` cancelled → collect on old
  `contactListEvents` stops. Old `WoTService` referenced only via the
  cancelled effect and old `IAccount` — GC'd cleanly.

### State lifecycle risks

- **Prerequisite cache fix** is the highest lifecycle risk. Without it,
  the WoT batch REQ actively corrupts `_followedUsers` — cascading
  bugs in every FeedFilter, mute list, and account-relay logic.
  Prevention: land the fix as a separate commit in this PR, gate by
  unit test in `DesktopLocalCacheTest`.
- Reverse-index size grows with `myFollows.size × avg-follows-of-follows`.
  Guardrail at 2000 clears the map; entering guardrail mid-session is
  a supported transition.
- SharedFlow subscribers must be scoped to `account.scope` — a leak
  from a stale coroutine would collect old events into a stale
  service. Enforced by `LaunchedEffect(wotService, localCache)`
  keying.

### API surface parity

- **commons:** `WoTService`, `LocalWoTService`, `LocalWoTReady`,
  `LocalWoTSelfKey`, `LocalWoTFollowedKeys` (CompositionLocals).
  `UserAvatar` gains optional `badge` slot (backward compatible —
  default null).
- **desktopApp:** `WoTBadge`, `WoTBadgedAvatar` composables; call-site
  migration at 6 v1 surfaces (feed, quote-embed, thread reply,
  notification, search result, profile header). Coordinator gets
  `loadKind3Batched`. `DesktopLocalCache` gets `contactListEvents:
  SharedFlow` + per-author `lastContactListByAuthor` map.
  `DesktopIAccount` gets `wotService` property.
- **amethyst (Android):** no changes v1. `UserAvatar` badge slot is
  optional; Android call sites pass no lambda.
- **cli (amy):** three verbs (`wot get`, `wot list`, `wot sync`),
  ~150 LOC in `commands/WotCommand.kt`, wired into `Main.kt` dispatch.

### Integration test scenarios

1. **Cold start with 250 follows.** Login → batch REQ fires with 3
   chunks of 100 authors → badges appear within ≤ 2 s.
2. **Follow a new person mid-session.** New pubkey added to
   `_followedUsers` → `onFollowSetChange` credits nothing yet; new
   `loadKind3Batched(followSet)` picks up their kind-3 (deduped by
   `queuedKind3Pubkeys`); score for their followers ticks up as their
   kind-3 lands.
3. **Unfollow a follower.** `onFollowSetChange(removed)` uncredits;
   affected pubkeys' scores decrement; some may drop to 0 and lose
   badges (map entry removed).
4. **Kind-3 churn.** Same follower republishes with +5 / -2 diff →
   `applyKind3` computes diff correctly, no double-counting.
5. **Account switch.** New `IAccount` → new `WoTService` → old service
   GC'd. Old service's map does not leak into new UI.
6. **Guardrail trip.** 3000-follow account → guardrail clears state,
   marks ready, no batch REQ.
7. **Empty graph.** 0 follows → onFollowSetChange with empty set,
   nothing to fetch, `markReadyOnce()` fires from fallback timeout.
8. **99+ overflow.** Simulate score 200 → badge shows "99+".
9. **Self exemption.** Own avatar in profile header never shows a
   badge even if some follower's kind-3 lists self.
10. **Follow-list exemption.** Followed author's avatar never shows a
    badge even when others in follow-list follow them.
11. **Kind-3 malformed.** 100 k-tag kind-3 → `applyKind3` truncates
    to 5 000, service stays responsive.
12. **Prerequisite fix verification.** Send a kind-3 event whose author
    is not the active user — verify `_followedUsers` unchanged, but
    `contactListEvents` emits.
13. **Amy `wot get`.** `amy wot get <npub>` after
    `amy wot sync` returns correct score.
14. **Amy warm cache.** Second `amy wot get <npub>` invocation
    without `sync` uses `FsEventStore` hydration, no relay traffic.

## Acceptance Criteria

### Functional

- [ ] **Prerequisite:** `DesktopLocalCache.consumeContactList` refactored
      with per-author `lastContactListByAuthor: Map<HexKey, Long>` and
      guards `_followedUsers` / `lastContactListEvent` writes on
      `event.pubKey == accountPubkey`. Also emits every kind-3 to a new
      `contactListEvents: SharedFlow<ContactListEvent>`.
- [ ] `WoTService` in
      `commons/commonMain/.../wot/WoTService.kt` — sparse
      `SnapshotStateMap<HexKey, Int>`, single-writer coroutine actor,
      `Snapshot.withMutableSnapshot { }` for atomic frames,
      `onFollowSetChange`, `applyKind3` (with `MAX_FOLLOWS_PER_EVENT`
      bound), `markReadyOnce`, `clear`, `scoresSnapshot`,
      `hydrateFromStore`, `isReady: StateFlow<Boolean>`.
- [ ] `LocalWoTService`, `LocalWoTReady`, `LocalWoTSelfKey`,
      `LocalWoTFollowedKeys` CompositionLocals in
      `commons/commonMain/.../wot/`.
- [ ] `UserAvatar` in `commons/commonMain/.../ui/components/UserAvatar.kt`
      gains optional `badge: @Composable (BoxScope.() -> Unit)? = null`
      parameter. Backward compatible — default null.
- [ ] `WoTBadge` in `desktopApp/src/jvmMain/.../ui/note/WoTBadge.kt` —
      Material3 `TooltipBox` with `isPersistent = true`, `PlainTooltip`,
      `Box` overlay chip, `contentDescription` for a11y, 99+ clamp.
- [ ] `WoTBadgedAvatar` in `desktopApp/src/jvmMain/.../ui/note/WoTBadgedAvatar.kt` —
      composes `UserAvatar` with a `WoTBadge` slot lambda; reads gates
      from CompositionLocals.
- [ ] `FeedMetadataCoordinator.loadKind3Batched(pubkeys, timeoutMs = 5s, onEose)` —
      chunks into ≤100-author Filters, aggregates EOSE across chunks,
      dedupes against `queuedKind3Pubkeys`.
- [ ] `DesktopRelaySubscriptionsCoordinator.loadKind3Batched(pubkeys, onEose)`
      delegate.
- [ ] `DesktopIAccount.wotService: WoTService` — constructed with
      `account.scope` at IAccount init.
- [ ] `Main.kt` inside `LoggedIn` branch:
      - Collects `wotService.isReady` once → `isReadyCollected: Boolean`.
      - Collects `localCache.contactListEvents` → `wotService.applyKind3`.
      - Collects `localCache.followedUsers` →
        `wotService.onFollowSetChange` + `loadKind3Batched`.
      - Fallback `delay(2_000)` → `markReadyOnce()`.
      - Provides all four `LocalWoT*` CompositionLocals to descendants.
- [ ] Call-site migration to `WoTBadgedAvatar` at 6 v1 surfaces:
      `FeedNoteCard` header, `QuotedNoteEmbed` author,
      `ThreadScreen` reply avatars, `NotificationsScreen` items,
      `SearchResultsList` avatars, `UserProfileScreen` header.
- [ ] Amy verbs:
      - `amy wot get <pubkey|npub> [--json]`
      - `amy wot list [--threshold N] [--limit K] [--json]`
      - `amy wot sync`

### Non-functional

- [ ] No measurable frame-time regression during scroll in a 250-note
      deck column with badges rendering (manual profiler smoke test).
- [ ] Spotless clean: `./gradlew spotlessApply` produces no diff.
- [ ] Compiles cleanly: `./gradlew :commons:compileKotlinJvm
      :desktopApp:compileKotlin :cli:build`.
- [ ] No `Preferences` writes — feature is stateless across restarts.
- [ ] Badge overlay uses absolute positioning inside a `Box` sized to
      the avatar; no layout shift when a score arrives mid-scroll.
- [ ] Batch REQ payload chunked ≤ 100 authors per Filter.

### Quality gates

- [ ] Unit tests for `WoTService`:
  - Empty graph → onFollowSetChange with empty set → nothing to
    compute, still fires `markReadyOnce()` via caller.
  - `handleFollowSet` from empty → 3 follows, then `handleKind3` for
    each with overlapping follow sets → verify scores.
  - Removing a follower decrements every pubkey they contributed to;
    sparse-map guarantee (removed at 0-count).
  - Kind-3 churn: same follower publishes new set → diff applied
    correctly, no double-counting, no leaked entries in
    `perFollowerSnapshot`.
  - Self-exclusion: kind-3 including active-user pubkey doesn't
    inflate self-score.
  - Follower-self-exclusion: kind-3 including follower's own pubkey
    doesn't inflate their own score.
  - Guardrail: 3000-follow input yields empty map + `_isReady = true`.
  - `MAX_FOLLOWS_PER_EVENT`: 6000-follow kind-3 truncated to 5000.
  - `scoresSnapshot()` returns a plain HashMap equal to
    `_scores.toMap()`.
- [ ] Unit test for `DesktopLocalCache.consumeContactList` prerequisite
      fix — non-self kind-3 doesn't mutate `_followedUsers`; both
      events emit to `contactListEvents`.
- [ ] Unit test for `loadKind3Batched` filter shape — 300 authors
      chunked into 3 Filters, one subscription, aggregated EOSE,
      onEose callback fired.
- [ ] Amy verb integration tests (in `cli/tests/wot/`):
      `wot get`, `wot list`, `wot sync` produce correct output and
      JSON schemas.
- [ ] Manual testing sheet at
      `desktopApp/plans/2026-07-01-wot-score-manual-testing-sheet.md`
      covering the 14 integration scenarios above.

## Success Metrics

- Users report badges give useful trust cues on strangers without
  cluttering follows / self.
- Batch REQ completes within 5 s for accounts with ≤ 500 follows on
  typical index relays.
- No frame drops > 16 ms during scroll or startup.
- `amy wot get` returns within 100 ms on a warm `FsEventStore`.

## Dependencies & Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Prerequisite cache fix breaks existing feed filters that depend on `_followedUsers` mutation semantics | medium | Prerequisite fix has its own unit test in this PR; audit all readers of `_followedUsers` (Kind3FollowListState, feed filters) — behavior unchanged for the *active-user* code path, only the *other-user* path stops overwriting |
| `verifiedFollowKeySet()` allocation cost during batch flood | low | Not cached in Quartz; measured ~1 ms per event; 500 events × 1 ms = 500 ms on a background thread — acceptable |
| `TooltipBox` visual defaults differ from `TooltipArea` | low | Test both hover and long-press behaviour manually; adjust padding/positioning if needed |
| Amy verb tests miss a `SnapshotStateMap` initialization edge | low | Amy verb test explicitly constructs `WoTService`, populates via `hydrateFromStore`, reads via `scoresSnapshot()` |
| Merge conflict with in-flight hashtag-spam PR (#3431) | medium | Both features touch `Main.kt` CompositionLocalProvider block. If hashtag-spam merges first, rebase; the two providers stack (independent locals) |
| Compose runtime version pinning for the 2026 deadlock fix | low | Verify `libs.versions.toml` compose runtime version ≥ current stable; upgrade if needed |
| Follow-set leak via batch REQ to index relays | pre-existing (metadata already leaks same info) | No new leak. Follow-up ticket: NIP-65 outbox routing for both kind-0 and kind-3 batches; do not gate WoT on it |

## Out of Scope (deferred)

- **Threshold-based filtering** (notifications, DMs, feeds, search) — v2.
- **Persistence of scores across sessions** — cached kind-3 events give
  fast cold start via `hydrateFromStore`; no separate cache.
- **Settings UI** (toggle to hide badges entirely, threshold picker) —
  v2.
- **Mutual-follow drilldown** ("click chip to see who") — v2.
- **Colored-ring alternative rendering** — v2 experiment.
- **Android UI** — v2. `UserAvatar` badge slot is Android-safe today
  (default null).
- **Weighting** (zap-weighted, mutual-weighted, decay over time) —
  YAGNI.
- **NIP-65 outbox routing** for batch REQ (correctness > simplicity
  trade-off) — cross-cutting follow-up ticket, applies to metadata
  coordinator too.
- **Cross-account aggregation** — YAGNI.

## Sources & References

### Origin

- **Brainstorm:** `docs/brainstorms/2026-07-01-feat-wot-score-brainstorm.md`

### Internal references

- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/cache/DesktopLocalCache.kt:447-453`
  — **prerequisite fix target** — `consumeContactList` scope corruption.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/cache/DesktopLocalCache.kt:191`
  — `event.verify()` gate confirms signature integrity.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/assemblers/FeedMetadataCoordinator.kt:255-301`
  — `loadMetadataBatched` blueprint; note the `.take(100)` on line 267
  (we chunk explicitly instead).
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/nip02FollowList/Kind3FollowListState.kt`
  — service-on-account pattern; `signer`-scoped, `Kind3Follows.authors`.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/model/DesktopIAccount.kt`
  — attach point for `wotService`.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/components/UserAvatar.kt`
  — add optional `badge` slot.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/tor/TorStatusIndicator.kt:75-95`
  — prior tooltip pattern (upgrading to Material3 `TooltipBox` in
  `WoTBadge`).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/Main.kt:979-982,1424-1427`
  — CompositionLocalProvider wiring sites.
- `cli/src/main/kotlin/com/vitorpamplona/amethyst/cli/Context.kt`
  — `FsEventStore` at `~/.amy/shared/events-store/` for amy hydration.
- `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip02FollowList/ContactListEvent.kt:56`
  — `verifiedFollowKeySet()` — not cached, bounded at parse time.

### External references

- [Zach Klippenstein — Compose Snapshot system](https://blog.zachklipp.com/introduction-to-the-compose-snapshot-system/)
  — per-key read tracking guarantees for `SnapshotStateMap`.
- [Android Developers — SnapshotStateMap reference](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/SnapshotStateMap)
  — `toMap()` is O(1), `.size` / iteration are structural reads.
- [Kotlin docs — Compose Multiplatform Material3 TooltipBox](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-tooltip-box.html)
  — cross-platform tooltip primitive; deprecation path for
  `TooltipArea`.
- [JetBrains issue #4275 — Deprecate TooltipArea](https://github.com/JetBrains/compose-multiplatform/issues/4275)
  — direction of travel.

### Skill references

- `account-state` — `IAccount` follow-set access, `Kind3FollowListState`
  scoping.
- `relay-client` — `FeedMetadataCoordinator` batch REQ pattern.
- `compose-recomposition-performance` — `SnapshotStateMap` per-key
  subscriber isolation, `Snapshot.withMutableSnapshot` for atomic
  frames.
- `compose-stability-diagnostics` — `@Stable` contract honesty on
  `WoTService`.
- `compose-slot-api-pattern` — badge slot on `UserAvatar` as visual
  extension point.
- `nostr-expert` — `ContactListEvent.verifiedFollowKeySet()`.
- `kotlin-flow-state-event-modeling` — `SharedFlow<ContactListEvent>`
  with buffer + `DROP_OLDEST` overflow; `StateFlow<Boolean>` for
  readiness.
- `amy-expert` — CLI verb structure, `FsEventStore` hydration, JSON
  output contract.

### Related work

- Hashtag-spam PR: https://github.com/vitorpamplona/amethyst/pull/3431
  — same CompositionLocal pattern; same "Content Filters" area for v2
  threshold UI when filtering ships.
- Feature backlog: `desktopApp/plans/_desktop-feature-backlog.md` item
  #2 (this plan).
