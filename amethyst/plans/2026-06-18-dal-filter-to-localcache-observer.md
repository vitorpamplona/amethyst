# Migrating feeds from the `dal` filter scan to `LocalCache` observers

Date: 2026-06-18
Branch: `claude/dal-filter-localcache-migration-65r8aj`
Status: in progress — infrastructure + Home pilot

## Problem

Every scrollable list in Amethyst (home, profile, hashtag, discover, …) is a
`FeedFilter`/`AdditiveFeedFilter` subclass (≈70 of them, mostly under
`amethyst/.../ui/screen/loggedIn/*/dal/`) driven by `FeedContentState` /
`FeedViewModel`.

The data-sourcing model is a **global fan-out**:

- `AccountViewModel` collects one global event stream
  (`LocalCache.getEventStream().newEventBundles`) and calls
  `AccountFeedContentStates.updateFeedsWith(newNotes)`.
- `updateFeedsWith` pushes the *same* new-note bundle into **every** live
  `FeedContentState`, each of which runs its filter's `applyFilter` over the
  bundle. Cost is O(open feeds × events), even for feeds the event can't
  possibly belong to.
- A cold `feed()` is a full linear scan of `LocalCache.notes` /
  `LocalCache.addressables`.

`LocalCache` already grew the replacement: an inverted index
(`observables: FilterIndex<Observable>`). On insertion, an event fans out only
to observers whose Nostr `Filter` narrows on a field the event actually carries
(`candidatesFor(event)`), then each candidate runs `filter.match` to enforce
negative constraints. Cost is O(matching observers). The public surface is
`LocalCache.observeNotes(filter)` / `observeEvents(filter)` /
`observeNewEvents(...)` returning cold `Flow`s, and several newer call sites
already use it (`UserProfileFollowersUserFeedViewModel`,
`UserProfileZapsViewModel`, the `Room*State` holders in commons, notifications
dispatcher, …).

**Goal:** move the `dal` feeds off the global fan-out onto the indexed observer
registry, then retire the global `updateFeedsWith`/`deleteNotes` plumbing and
(eventually) the `AdditiveComplexFeedFilter`/`FilterByListParams` scan helpers
once nothing depends on them.

## The gap that makes this non-trivial

`observeNotes(filter)` applies **only** the Nostr `Filter`. Most feeds layer on
predicates the Filter grammar can't express:

- `FilterByListParams.match()` — top-nav follow/relay/global/community
  membership, hidden users, muted threads, future-dating, excessive-hashtag
  guard. Driven by **reactive** state (`account.liveHomeFollowLists`,
  `account.hiddenUsers.flow`).
- `Note.isNewThread()` / `!isNewThread()`, NIP-32 follow-label cross-refs
  (hashtag feed), repost de-duplication, etc.

Two consequences:

1. We can't just hand a `Filter` to `observeNotes` and be done — the observer
   must still run the filter's own `applyFilter`/`feed` predicates. So we need a
   richer observer that **reuses the existing `FeedFilter` verbatim** and is
   merely *triggered* by the index instead of by the global bundle.
2. When the reactive inputs change (follow list switched, user muted), the
   membership predicate changes for events already in the list. The index only
   helps with *new* events; membership-change re-evaluation stays an explicit
   `invalidateData()` (full `feed()` re-scan), exactly as today. We keep the
   existing Account-flow → invalidate wiring for that.

## Design: stateless delta forwarding into the existing additive path

The lowest-risk shape — and the one implemented — leaves `FeedContentState`
**byte-for-byte unchanged** and changes only the *trigger*. Because
`FeedContentState` already has a robust additive engine
(`updateFeedWith` → `invalidateInsertData` → `refreshFromOldState` →
`AdditiveFeedFilter.updateListWith` → `applyFilter`) that reconciles against its
own displayed list, dedups, handles `feedKey` mismatch (full re-scan) and
deletions, the migrated feed just needs its candidate notes delivered there from
the index instead of from the global bundle. Making the observer **stateless**
(no list of its own) avoids a second source of truth that could desync from the
`FeedContentState` list.

Two pieces:

1. **`IndexableFeedFilter` (commons)** — opt-in marker giving the coarse index
   `Filter`(s): the narrowest Nostr filter that is a *superset* of what `feed()`
   returns, so the index never drops a real match.

   ```
   interface IndexableFeedFilter { fun indexFilters(): List<Filter> }
   ```

   For the Home filters that is `Filter(kinds = <every kind acceptableEvent
   admits>)` — the full superset, co-located as `INDEX_KINDS` next to
   `acceptableEvent`. The index buckets by kind; `FilterByListParams.match` +
   `isNewThread()` still run inside `applyFilter`, unchanged. (Authors are
   intentionally *not* in the coarse filter even when the top-nav is an author
   list — the author set is large and reactive; kind-bucketing is selective
   enough and keeps re-subscription off the follow-list-change path.)

2. **`LocalCache.observeFeedDeltas(filters): Flow<FeedNoteDelta>` (amethyst)** —
   modelled on `observeNotes`: registers a stateless `Observable` whose `new` /
   `remove` emit `FeedNoteDelta.Added` / `Removed`; `awaitClose { unregister }`;
   `UNLIMITED` buffer (deltas must not be conflated — a dropped `Added` is a
   missing post).

### Wiring (`AccountFeedContentStates`)

- **Today:** `updateFeedsWith(bundle)` → `feedState.updateFeedWith(bundle)` for
  every feed (global O(feeds × events) fan-out).
- **After:** `connectObservedFeed(state, filter)` collects
  `observeFeedDeltas(filter.indexFilters())` and routes
  `Added → state.updateFeedWith(setOf(note))`,
  `Removed → state.deleteFromFeed(setOf(note))`. The three Home feeds are
  removed from `updateFeedsWith` / `deleteNotes` (only `homeLive`, a
  `ChannelFeedContentState`, stays on the fan-out — not migrated in step 1).

Per-event `updateFeedWith(setOf(note))` re-bundles inside
`BasicBundledInsert.invalidateList` (it queues items and drains them as a batch
every 250 ms), so this is functionally equivalent to today's per-bundle call.
`lastNoteCreatedAtWhenFullyLoaded` is still computed by `updateFeed` from
`size >= limit()`, so relay pagination via `HomeOutboxEventsEoseManager` is
unaffected. Initial load and membership-change refreshes keep flowing through
the existing `FeedContentState` scan path (`checkKeysInvalidateDataAndSendToTop`
in `WatchAccountForHomeScreen`, pull-refresh `invalidateData`) — untouched.

## Pilot: the Home feed

Home is the hardest feed and the one chosen to prove the pattern end-to-end. It
has the deepest integration surface, so if the bridge holds here it holds
everywhere:

- Filters: `HomeNewThreadFeedFilter`, `HomeConversationsFeedFilter`
  (`HomeEverythingFeedFilter` composes the two; `HomeLiveFilter` is a separate
  `ChannelFeedContentState` — out of scope for step 1).
- State holders: `AccountFeedContentStates.homeNewThreads` / `homeReplies` /
  `homeEverything`.
- Consumers that must keep working unchanged:
  - `HomeScreen` (renders the three `FeedContentState`s).
  - `AccountViewModel` tab badges (`tabHasNewItems(..., homeNewThreads.feedContent)`).
  - `HomeOutboxEventsEoseManager` (relay pagination via
    `homeNewThreads.lastNoteCreatedAtWhenFullyLoaded` / `lastNoteCreatedAtIfFilled`).

### Steps

1. **commons:** add `IndexableFeedFilter`. ✅
2. **amethyst:** add `LocalCache.observeFeedDeltas(...)` + `FeedNoteDelta`. ✅
3. **amethyst:** implement `indexFilters()` / `INDEX_KINDS` on the three home
   filters (`HomeNewThreadFeedFilter`, `HomeConversationsFeedFilter`,
   `HomeEverythingFeedFilter`). ✅
4. **amethyst:** `connectObservedFeed` in `AccountFeedContentStates`; drop the
   three home feeds from `updateFeedsWith` / `deleteNotes`; keep their
   membership-change invalidation. ✅
5. **amethyst test:** `HomeFeedIndexKindsTest` asserts `INDEX_KINDS` covers
   every rendered relay-subscription kind. ✅
6. Build `:amethyst`; run unit tests. **On-device smoke test of the Home screen
   (new posts appear live, mute/follow-switch refresh, scroll-to-top, relay
   pagination) is the remaining manual gate before merge** — it can't be
   verified headless.

## Rollout order after the pilot

Migrate in increasing order of predicate complexity, one PR per cluster, each
behind the same `IndexableFeedFilter` opt-in so un-migrated feeds keep using the
global fan-out until converted:

1. Single-author / single-tag feeds (profile sub-feeds, hashtag, geohash,
   git-repo, community) — small coarse filters, easy verification.
2. Kind-scoped discover/list feeds (articles, longs, video, pictures, polls,
   marketplace, …).
3. Chat/DM list feeds (`ChatroomList*`) — already have bespoke invalidation.
4. Notifications (`CardFeedContentState`) — special mute/delete handling.
5. Home everything + live, then retire `updateFeedsWith`/`deleteNotes` and the
   global event-stream collection in `AccountViewModel`.

Once every feed is migrated, retire `FilterByListParams`'s use inside scan-based
`feed()` only where it's been fully replaced, and the `AdditiveComplexFeedFilter`
scan helpers if unused. (`FeedFilter`/`AdditiveFeedFilter` stay — the observer
reuses them.)

## Risks / open questions

- **Coarse-filter completeness.** `indexFilters()` must be a superset of
  `feed()`; a missing kind silently drops posts. Mitigation: derive the kind
  list from the same `acceptableEvent` switch and assert in tests.
- **Re-subscription churn** if `indexFilters()` ever depends on reactive author
  sets. Avoided in the pilot by indexing on kind only.
- **Double counting** while a feed is half-migrated: a feed must be either on
  the observer *or* in `updateFeedsWith`, never both. The opt-in check
  (`is IndexableFeedFilter`) enforces this per feed.
- **Headless verification limit.** Unit tests cover the observer/bridge logic;
  the Home screen behaviour itself needs a device/emulator pass.
</invoke>
