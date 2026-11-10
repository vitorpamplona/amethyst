# Account feed lifecycle: switch accounts at will

Status: proposed 2026-11-09. Steps 1 (`FeedOwnerTracker`) and 2 (lazy feeds) are implemented on
`claude/determined-meitner-wf8m3i`; the rest is design.

## Problem

On desktop, load account A, switch to B, switch back to A: Notifications keep only the NIP-17
DMs and Home shows older posts, until a different list is picked at the top. Reproduced in the
desktop app with two read-only accounts: A's "Today" went from 37 replies / 8 likes (newest 2h
ago) to 5 replies / 0 likes (newest 16h ago).

The same mechanism, without any account switch, loses notes when a screen is left and opened
again after a GC: `#fotografie` went from 534 notes to 191, a profile from 92 to 36, each
measured with `jcmd GC.run` and a log of the `since` each request carried.

## Lifetimes today

| Layer | Lives | Holds |
|---|---|---|
| Process | whole run | `LocalCache` (notes, users and addressables by `WeakReference`; channels and each account's DM index strongly), `INostrClient`, `RelaySubscriptionsCoordinator` and every EOSE manager with its cursors (per user, per (user, list), per id, per relay) |
| Account | load until logout (`AccountCacheState`) | `Account`, its scope and lists, `notificationHistory` paging cursors |
| `AccountViewModel` | while the account is on screen; cleared at once on a switch (`AccountScopedViewModelStore`), and on Android when the activity finishes | `AccountFeedContentStates`: ~60 `FeedContentState` + 3 `CardFeedContentState`, and the `newEventBundles` collector that updates them |
| Screen ViewModel | one navigation entry | hashtag, profile, thread, community, follow pack, … feeds |
| Composition | while composed | relay subscriptions: account loaders while the logged-in page is up; feed REQs until 30 s after ON_STOP (`LifecycleAwareKeyDataSourceSubscription`) |

**The invariant that breaks.** An EOSE cursor means "this store already has everything up to T".
It is only true while the notes are held, and the only strong holders of feed notes are the
ViewModel and screen layers. The cursors live in the process layer. Four things drop the notes
and keep the cursor:

1. An account switch, or the Android activity finishing, clears the `AccountViewModel`.
2. Leaving a screen clears its ViewModel.
3. `CachePruner` tier 2 (`MemoryTrimmingService`, desktop heap above 70%):
   `pruneRepliesAndReactions` and `pruneOldMessages` remove notes a feed still holds; the next
   full rebuild scans the cache and loses them. Its doc says this "may cause feeds to re-fetch",
   but no cursor is reset, so nothing does. (Read from the code, not reproduced.)
4. The reply, reaction and zap counters under each note (`EventWatcherSubAssembler`) keep their
   cursors by note id, on purpose, so a note `LocalCache` rebuilt for the same id reuses them. A
   rebuilt note has lost its children, so it then only learns of new ones. Seen on desktop: after
   a switch away and back, jack's top notification showed 29 replies / 110 likes instead of
   119 / 416, and stayed there.

`trimFeedsToSize(200)` under memory pressure is safe: it drops the oldest notes, and those come
back through the floor paging, which the cursor does not govern.

**What makes switching expensive.** `FeedContentState.updateFeedWith` on a feed still `Loading`
falls back to a full `invalidateData()`, so the first event bundle builds all ~60 feeds by
scanning `LocalCache`, including screens never opened, and every bundle after updates all of
them. A switch throws that away and builds it again for the other account.

**Notifications by platform.**
- Android: `AlwaysOnNotificationServiceManager` mounts every account that can sign through
  `AccountSubscriptionRegistry` (read-only accounts are skipped). Notifications carry the
  account's npub in their action intents, but nothing visible says which account one is for.
- Desktop: `DesktopNotifications.watch` runs only for the account on screen
  (`DesktopAppRoot.LoggedInEffects`), and no other account has subscriptions. A switched-away
  account receives nothing. Toasts do not name the account.

## Decisions (2026-11-09)

- Every account receives notifications at all times, on desktop as on Android, whichever one is
  on screen. Each notification says which account it is for.
- An account switched away from stays **warm for 15 minutes**: its feeds and their notes are
  kept, so switching back is instant.
- Cursors must be associated with the feeds they describe.

## Design

### 1. The feed owns the cursor, the manager owns the wire

A cursor is a claim about one holder of notes, so it lives in that holder:

```kotlin
/** Per-relay EOSE times for one holder of notes. Dies with the holder; nothing resets it. */
class RelayCursors {
    fun since(relay: NormalizedRelayUrl): Long?
    fun newEose(relay: NormalizedRelayUrl, time: Long)
    fun clear()
}
```

- `FeedContentState` and `CardFeedContentState` each own a `RelayCursors`.
- A feed clears its own cursors when its `feedKey()` changes (a different list or filter) and on
  `clear()`. That makes `SingleTopNavFeedSubAssembler.resetEoseOnListChange` the rule for every
  feed instead of an opt-in, and replaces the per-list cursor maps (`EOSEAccountKey`).
- A query key lists the cursors its REQ fills: `HomeQueryState` → new threads + replies;
  `AccountUiQueryState` → the notification feeds; `HashtagQueryState` → the screen's feed. Keys
  with no feed name the holder that keeps their data: the account's notification feed for the
  background tails, `Account` for its own lists, the chatroom index for gift wraps.

The EOSE managers keep everything about subscriptions (grouping keys, one REQ per relay, relay
limits) and lose their cursor maps:

- **Reading:** `since(relay)` for a subscription is the **minimum** over every holder it serves,
  and null if any holder has none. This is the rule the merged notification REQ already uses for
  its floors ("the oldest wins"), and it replaces `MergedAuthorTracker`: an account or screen
  that joins has no cursor, so the relay is asked from scratch.
- **Writing:** an EOSE (or a live event) is written to the holders the subscription serves
  *at that moment*, resolved through a `subId → keys` map kept by `updateSubscriptions`. Today's
  listeners capture the key from `newSub(key)`, which goes stale when a new key takes over the
  same subscription.
- **Lifetime:** nothing to detect. A new feed has empty cursors; a released feed takes its
  cursors with it. `FeedOwnerTracker` (step 1) goes away.

### 2. Account feeds outlive the ViewModel: Active, Warm, Cold

`AccountFeedContentStates` moves out of `BaseAccountViewModel` into an `AccountFeeds` created
with the `Account` in `AccountCacheState`. ViewModels and screens attach to it.

| State | When | Feeds | Updates |
|---|---|---|---|
| Active | on screen in some window | kept | applied |
| Warm | switched away, up to 15 minutes | kept, with their notes and cursors | paused; one full refresh from `LocalCache` on return, and the cursors fetch exactly the gap |
| Cold | 15 minutes passed, or memory pressure | released, cursors with them | none |

- **Notification feeds are never cold** while the account is loaded: every account is notified
  at all times (decision above), and the always-on tail's cursor must have a live holder.
  They are bounded by the feed limit, so the cost is one card feed per account.
- **Feeds are lazy:** a feed is built the first time a screen or a pinned bottom-bar preloader
  asks for it, and only built feeds receive event bundles. Without this, warm accounts multiply
  the per-bundle work.
- The per-account `ViewModelStore` can follow the same warm window, so scroll positions and
  open screens come back too. That is a UI choice on top; correctness does not depend on it.
- This is the data half of the tabs-and-windows plan's Phase 2b (`AccountSession` at application
  scope, `2026-11-04-tabs-and-windows-layouts.md`); build them together.

### 3. Screen feeds

Hashtag, geohash, relay, URL, community and follow pack feeds and profile tabs get the same
treatment: their query key names the screen feed's cursors, so a new visit asks from scratch.
Optionally keep recently closed screens warm the same way as accounts.

### 4. The pruner coordinates with feeds

After tier-2 pruning, clear the cursors of every feed that held a removed note, or skip notes a
live feed holds. `pruneOldMessages` already rewinds the chatroom history cursors when it trims
DMs; this extends that rule to feeds.

### 5. Every account notified, and labelled

- **Desktop:** mount `AccountSubscriptionRegistry` (already in `commonsUI/jvmAndroid`) for every
  saved account, and run `DesktopNotifications.watch` for each, not only the one on screen.
  Toasts name the account; clicking one switches to it (`checkAndSwitchUserSync` with a route)
  and opens the note.
- **Android:** when more than one account is saved, show the account on each notification
  (`setSubText` with its display name), and check that tapping one switches to that account.
- **Read-only accounts:** Android skips accounts that cannot sign. "All accounts" would include
  them for public notifications (mentions, replies, zaps); they cannot decrypt DMs.

### 6. Feeds rebuild from candidates, not from the cache (prototype: notifications)

A full rebuild walked every note in `LocalCache` (50-500 ms reported on long sessions), and every
mute, Web of Trust answer, spam flag, invite or list switch triggers one. The walk is not needed:

- `IFilter` (`model/observables`) is a note-level rule: `matches(note)`, with the whole `Note`, so
  it can say what a Nostr `Filter` cannot.
- `NoteSetMatchingFilter` keeps every cached note that passes one current as notes arrive and are
  deleted (an unindexed observer: one `matches` per new note), after one walk when it starts
  (`EventCache.registerNoteSet`). `dependents` re-checks notes whose answer changes when another
  note arrives.
- Keep the rule stable and apply viewer policy over the set: a rule that read mutes would go
  stale on each change and need the walk again.

`NotificationCandidates` is the first: "could this reach me" (`mightBeANotification`) as the
rule, and the children of my own notes as dependents (a Buzz like or a chat reply that arrived
before the note it answers). The three notification feeds share it and run
`acceptableEvent` (mutes, WoT, the list, the toggles) over it. Measured on desktop over 9 rebuilds:
identical results every time, the candidate scan 2-3x faster than the walk with ~6,000 notes in
the cache and 2,318 candidates (jack has unusually many notifications). The walk grows with the
cache, the scan with the notifications, so the gap widens with a long session; not measured here,
because this environment's relays could not grow the cache past ~6,000 notes.

Home uses the same mechanism (`CandidateSet`, shared by the three Home feeds): the rule is "a type
Home shows" (`HomeNewThreadFeedFilter.isThreadKind`, `HomeConversationsFeedFilter.isConversationKind`,
RSVPs), and the list, mutes, the kind toggles and the latest-RSVP check stay per feed. It holds
its notes weakly (`weak = true`), since Home types are most of the cache, and starts with the first
Home rebuild. Over 18 rebuilds: identical results, but only ~30% less time in total (144 ms walk,
101 ms scan) and noisy, because Home types were ~60% of the cache. The levers that matter more
for Home: every return to Home rebuilds all three feeds (`WatchLifecycleAndUpdateModel` on resume;
four cache walks, as Everything walks twice), and the list check, which an author index for
follow-based lists would narrow.

Not yet: the per-bundle path still runs `applyFilter` on each new note (cheap since the gate), the
set lives with the `AccountViewModel` (one walk again after a switch back, until notification feeds
live as long as the account), and the other feeds still walk the cache.

## Steps

Each step ships on its own and is checked with the desktop switch test (A → B → wait for GC
→ A) plus `jcmd GC.run`.

1. **Done:** reset cursors when a different feed object shows up (`FeedOwnerTracker`), for the
   top-nav feeds and the notification managers. Fixes the reported bug.
2. **Done:** lazy feeds. `FeedContentState` / `CardFeedContentState` take `lazy = true`; all
   account feeds but the main notifications feed use it. A dormant feed ignores events,
   deletions and invalidations, and builds once from the cache when something collects
   `feedContent` or calls `activate()`. Whatever fills a feed wakes it: the top-nav managers wake
   their keys' feeds (`activateFeeds`), the notification manager wakes the split tabs while the
   split is on, and `BottomBarFeedPreloaders` wakes every feed a pinned tab shows (several tabs
   ride a REQ whose key names only one feed). Measured on desktop, jack read-only, Home open,
   60 s after start: 3 feeds built instead of 14, and only the pinned tabs' feeds (5 by default)
   plus notifications take event bundles instead of all 46. Notes in memory barely change
   (4,476 vs 4,372): the gain is work, not memory.
3. `RelayCursors` in the feeds, keys name their holders, managers read the minimum and fan
   EOSEs out. Remove `FeedOwnerTracker`, `MergedAuthorTracker` and the per-list cursor maps.
   Covers the screen feeds (section 3) too, and the note counters: their holder is the `Note`
   object, not its id.
4. `AccountFeeds` with Active / Warm (15 min) / Cold, notification feeds account-lifetime;
   with tabs-and-windows Phase 2b.
5. Every account notified on desktop, labelled on both platforms.
6. Pruner coordination.

## Feed conversion (2026-11-10)

Done on the branch, each step its own commit:

1. **Contract.** `UniverseFeedFilter` (`universe`, `candidates`) and `rebuildFeed`, which logs
   every rebuild at debug level under `FeedRebuild` (feed, source, notes kept, ms): run a long
   session with `-Damethyst.debug=true` and read the costs. Notifications use candidates; Home
   walks the cache (its universe is most of it) and lost its candidate set.
2. **Screen feeds.** Profile threads/replies/gallery, hashtag and geohash are
   `ScreenUniverseFeedFilter`s: their `FeedViewModel` keeps the universe as a weak candidate set
   for the screen's life, and their keys carry the screen as `owner`, so a screen opened again
   asks the relays from scratch (`feedOwner` on the per-id and per-user managers). `#foto` reopened
   after a forced GC: 115 survivors, refilled by the relays to 338. Hashtag/geohash now count an
   addressable event once, at its latest version, instead of once per cached version.
3. **Home rebuilds.** Home no longer rebuilds on every return (`refreshOnResume = false`, via
   `InvalidatableContent.onResume`): 0 rebuilds across returns instead of 4 cache walks each, and
   posts that arrived while away were on top when it came back. What the resume rebuild used to
   cover is explicit now: the account feeds' event collector buffers without limit (the shared
   flow drops a slow collector's oldest bundles), mutes/spam flags and the resolved Home list
   rebuild Home (debounced 2 s), and a future-dated note schedules one rebuild for when it becomes
   visible. Everything walks the cache once instead of twice (same notes as before, except which
   of a note and its repost is kept, which was already arbitrary). Not checked in the app: the
   mute, follow and future-dated triggers (a read-only account cannot mute or follow).

Other screens still rebuild on resume; Discover, Video and the other top-nav feeds can follow
Home once their own triggers are listed the same way.

## Open questions

- How many accounts may be warm at once on Android? Measure what one account's feed set retains
  (class histogram before and after a switch) before choosing a cap.
- Read-only accounts: notify them too?
- Desktop publishes scheduled posts only for the account on screen (`LoggedInEffects`), while
  Android does it for every account. Same "background account" question; same answer?
