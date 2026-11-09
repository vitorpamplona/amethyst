# Account feed lifecycle: switch accounts at will

Status: proposed 2026-11-09. Step 1 is implemented on `claude/determined-meitner-wf8m3i`
(`FeedOwnerTracker`); the rest is design.

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
ViewModel and screen layers. The cursors live in the process layer. Three things drop the notes
and keep the cursor:

1. An account switch, or the Android activity finishing, clears the `AccountViewModel`.
2. Leaving a screen clears its ViewModel.
3. `CachePruner` tier 2 (`MemoryTrimmingService`, desktop heap above 70%):
   `pruneRepliesAndReactions` and `pruneOldMessages` remove notes a feed still holds; the next
   full rebuild scans the cache and loses them. Its doc says this "may cause feeds to re-fetch",
   but no cursor is reset, so nothing does. (Read from the code, not reproduced.)

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

## Steps

Each step ships on its own and is checked with the desktop switch test (A → B → wait for GC
→ A) plus `jcmd GC.run`.

1. **Done:** reset cursors when a different feed object shows up (`FeedOwnerTracker`), for the
   top-nav feeds and the notification managers. Fixes the reported bug.
2. Lazy feeds. No behaviour change; less work on every switch.
3. `RelayCursors` in the feeds, keys name their holders, managers read the minimum and fan
   EOSEs out. Remove `FeedOwnerTracker`, `MergedAuthorTracker` and the per-list cursor maps.
   Covers the screen feeds (section 3) too.
4. `AccountFeeds` with Active / Warm (15 min) / Cold, notification feeds account-lifetime;
   with tabs-and-windows Phase 2b.
5. Every account notified on desktop, labelled on both platforms.
6. Pruner coordination.

## Open questions

- How many accounts may be warm at once on Android? Measure what one account's feed set retains
  (class histogram before and after a switch) before choosing a cap.
- Read-only accounts: notify them too?
- Desktop publishes scheduled posts only for the account on screen (`LoggedInEffects`), while
  Android does it for every account. Same "background account" question; same answer?
