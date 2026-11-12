# Account feed lifecycle: switch accounts at will

Status (2026-11-12), on `claude/determined-meitner-wf8m3i`: every step is implemented, and no
EOSE manager keeps cursors of its own any more (step 7); step 4's UI half (warm view-model stores, `AccountSession` at application scope) waits for tabs-and-windows
Phase 2b.

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

### 1. The cursor lives with the notes it vouches for

An EOSE cursor claims "this holder has everything relay R sent, up to T, for this query". That
is only true while the holder keeps those notes, and `LocalCache` keeps nothing on its own (weak
references), so the cursor lives in the holder and dies with it:

| Holder (keeps the notes) | Cursor on | Replaces |
|---|---|---|
| A feed (`FeedContentState`, `CardFeedContentState`) | the feed | the managers' per-user, per-list and per-id maps; `FeedOwnerTracker` |
| A note's replies, reactions and zaps (`Note.replies` and friends are strong lists) | the `Note` object | `EventWatcherSubAssembler`'s cursors by note id (the stuck-counter bug) |
| An account's own lists, wallets, drafts, joined groups; its DMs in the chatroom index | the `Account` | per-user maps |
| A channel's messages and state (`Channel.notes`, held strongly by `LocalCache`) | the `Channel` | per-channel maps |
| A user's metadata and relay lists (the pinned notes on `User`) | the `User` | `UserWatcherSubAssembler`'s LRU of 200 users, the profile's per-relay cursor |
| Only the screen that made the key (a composable or view model collecting the notes) | the key (`KeyHeldCursors`) | per-id and per-user maps that outlived the screen |

**A cursor records what it covered, not only when.** Per relay, `RelayCursors` keeps the time T
and the filter shape (kinds, authors, tags) that T vouches for. `plan(relay, requested)` returns
the parts of a request: what the stored shape covers is asked `since T`, the rest from scratch.
Narrowing keeps the cursor; widening by authors (a new follow, another account joining a merged
REQ) refetches only the new authors; any other widening (a kind turned back on, different tags)
asks the whole request from scratch. After an EOSE or a live event, the cursor covers exactly the
request that was sent, up to that time. This replaces `MergedAuthorTracker`, `gainedAuthors` and
the per-list reset flags.

**Every way a holder loses notes adjusts its own cursor:**

| Notes lost because | Cursor |
|---|---|
| The holder is released (screen closed, account switched, logout) | gone with it; the next holder asks from scratch, with nothing to detect |
| The feed's key changes (another list) | `clear()`, for every feed |
| `LocalCache` drops a `Note` and rebuilds one for the same id | was on the old object; the new one asks from scratch |
| The pruner removes notes a feed holds | the pruner tells the feed, which drops them and `rewindTo(oldest removed)` |
| A local filter widens (unmute; notes fetched, rejected and collected) | `clear()`, in the branch of `FeedsKeptCurrent` that rebuilds |
| `trimFeedsToSize` drops the oldest | nothing: below the paging floor, which refetches on scroll |
| A deletion (kind 5) | nothing: meant to be gone |

**The managers keep the wire, not the cursors.** A query key names its holders (`HomeQueryState`
the two Home feeds, `AccountUiQueryState` the notification feeds, a background account its
`Account`). Reading: per relay, the manager asks each holder the subscription serves for its plan
and asks each author from the oldest cursor any holder has for it (from scratch if one has none),
so a holder that joins does not make the others refetch beyond what it is missing. A holder keeps
one time per relay, so a filter splits into two parts at most (caught up, from scratch), well
within what relays allow per REQ. Older pages (filters with `until`) are not governed by cursors. Writing: an EOSE or live event goes to the
holders the subscription serves at that moment, through a `subId → keys` map kept by
`updateSubscriptions` (today's listeners capture the key from `newSub(key)`, which goes stale).
Cursors never reference their holder; a heap check after each step confirms it.

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

### 4. The pruner leaves feeds alone

The reply/reaction pass dates from before `LocalCache` held notes weakly. Now a note lives while a
feed, a channel or a screen holds it, and its children live with it, so dropping the holder frees
them; pruning them earlier only broke what still held them. The pruner keeps to what is held
strongly and grows without bound (channels, DMs), and rewinds the history cursors of what it
drops (see step 6).

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
3. Cursors with their holders (section 1), one family at a time, the managers' maps kept as the
   fallback for keys that name no holder yet:
   1. `RelayCursors` (time + shape per relay) and `RelayCursorHolder`, on both feed classes;
      cleared on a feed key change (a card feed's `clear()` is a rebuild, not a loss: a widened
      local filter clears the cursors in step 8). Unit tests.
   2. **Done:** manager plumbing. A key implements `CursorHoldingQuery` to name its holders; the
      base managers then build its filters without their own map and let `CursorPlanner.window`
      set the windows, and every subscription's listener is wrapped (`CursorRoutingListener`) so
      EOSEs and live events reach the holders it serves at that moment (`serve`, per subscription,
      from every key sharing it). Keys that name none keep the old maps; none name any yet.
   3. **Done:** notifications. A key names its holders through `notificationCursorHolders`
      (the managers' `cursorHoldersOf`): the notification feeds being built when a screen is
      attached (a split tab joins when it wakes), or the background key's own tail cursor, which
      lives as long as the background registry keeps the account mounted. The inbox tail plans
      each account against its own holders and merges the accounts whose windows agree
      (`CursorPlanner.windowPerMember`): an account back with a new feed set is asked from
      scratch alone, one joining a relay inherits nothing. The straggler probe reads the same
      holders. The history pager's progress moved from the `Account` to the feed set
      (`AccountFeedContentStates.notificationHistory`), so a new feed set pages from the newest.
      `FeedOwnerTracker`, `MergedAuthorTracker` and the history reset are gone from these three
      managers. Desktop, jack + a signing account: the tail asked jack from scratch on return and
      the background account resumed from its own cursor; 6 round trips left one feed set, one
      `AccountViewModel` and three card feeds in the heap; the signing account's history
      bootstrapped a fresh page on each return.
   4. **Done:** top-nav feeds. `TopNavFeedSubAssembler` names each key's feeds as holders
      (`cursorFeeds`; each Discover manager only the tabs it fills, whose floors also re-issue
      it), so every top-nav screen with a feed (Home, Video, Discover, the media and content
      tabs) reads and writes its feeds' cursors; a list switch clears them with the feed's key.
      Two rules came with it. Each cursor entry belongs to the manager that wrote it
      (`CursorSource`): a feed filled by several managers would otherwise have each overwrite
      the others' shapes and refetch everything on every pass. And a holder only accepts an
      answer to a filter planned for it, asked from scratch, or continuing its own cursor
      (`RelayCursors.accepts`): on desktop, an EOSE for the REQ planned from the old feeds'
      cursors arrived after a switch back and was recorded on the new, empty feeds as if they
      had everything up to now. Keys without a feed (nsites, napplets, DRSS, the DVM
      heartbeat) keep the managers' per-list maps. `MergedAuthorTracker` is not used by
      top-nav managers; it stays with the account metadata, d-list and single-sub screen
      managers (step 5). Desktop: Home asked from scratch after a switch back and from its
      floor after a list switch, Pictures and Discover filled, and 6 round trips left one feed
      set and one `AccountViewModel`.
   5. **Done:** screen feeds. The hashtag, geohash, URL, relay, community and follow-pack keys
      carry the screen's feeds (`feeds`, replacing the view-model `owner`), and the profile key
      its tabs that hold posts and media; only the managers that fill those feeds name them
      (`cursorHoldersOf`), so profile metadata, followers and zaps and the community rules keep
      their own maps. A subscription shared by several entities (`SingleSubEoseManager`:
      communities, follow packs) windows each entity by its own keys' holders, so one opened
      later is asked from scratch without the others refetching and closing one takes its
      cursors along; with no holders it keeps the shared map as before. Desktop: profile,
      hashtag and community screens asked from scratch on each visit and filled; going back
      released their feeds. Found, not changed: a profile whose relay list has not loaded yet
      asks no relays and nothing re-plans it until the screen is opened again (same before this
      step). Fixed after step 5: the posts and media managers re-plan when that user's relay
      list arrives (`ProfileRelayListWatchers`); same steps, the notes show on the first visit.
   6. **Done:** note counters. Reproduced first on desktop: jack's "built on nostr" note showed
      119 replies / 125 boosts / 424 likes / 42k sats; after a switch to another account, forced
      GCs and a switch back, 5 / 8 / 36 / 189, and it stayed there (its reaction card also left
      the notifications). The cursors of `EventWatcherSubAssembler` moved from an LRU by note id
      to the `Note` (`childCursors()`, created on first watch), which holds the replies,
      reactions and zaps they vouch for: a note rebuilt for the same id is asked from scratch.
      An answer reaches a note only through filters that name it (`#e`/`#E`/`#q`/`#a`/`#A`) and
      only when asked from scratch or from no later than its own cursor. After: 119 / 124 / 424
      / 42k and the card back, same steps.
   7. **Done:** account lists; gift wraps needed nothing. `AccountMetadataEoseManager` (profile,
      follows, mutes, bookmarks, lists, DRSS, the last month of posts, the switcher avatars)
      keeps its cursors on the `Account` (`ownEventsCursors`) and plans each account against its
      own, merging the accounts whose windows agree (`windowPerMember`), so a newcomer on a relay
      no longer clears the cursor for everyone there (`MergedAuthorTracker` is gone from it). A
      holder's planned windows now add up across passes (the manager plans one relay twice).
      The gift-wrap live tail asks the last week on every REQ and reads no cursor, and its history
      pager's progress already lives on the account's `ChatroomList`, which holds the messages
      (`pruneOldMessages` rewinds it). Drafts, wallet (NWC, Cashu), Marmot and Buzz keep the
      managers' maps. Desktop: the signing account joining after unlock was asked from scratch
      while jack's lists stayed caught up, then they shared filters; logging it out and back in (a
      new `Account`) asked it from scratch again, jack untouched.
   8. **Done:** local-filter widening. A feed rejects what the account hides (mutes, spammers,
      hidden words and hashtags, muted threads, sensitive content); a rejected note is held by
      nothing and may be collected while the feed's cursors say it was loaded. When the hidden
      set changes in any way but hiding more (`onlyHidesMoreThan`), `AccountFeedContentStates`
      clears the cursors of every account feed. A `RelayCursors` now knows which managers serve
      it (`serve` registers them), and clearing or rewinding it re-plans them: a live REQ keeps
      its window otherwise, and relays never resend what is older. One rule had to loosen: a
      holder accepts answers to filters asking from no later than a window planned for it, not
      only exactly one, since the client does not resend a REQ whose `since` only moved forward
      and the filter on the wire asks for more. Desktop: hiding a word or blocking an author kept
      Home's cursors; removing the word, unblocking the author or switching sensitive content
      cleared them and Home was asked again (0 → 4-5 of 7 relays covered within 10 s; the rest
      never answer here). Not covered: screen feeds (a reopened screen asks from scratch anyway;
      one left open in the back stack across an unmute keeps its cursors), Web of Trust verdicts
      and the report settings, which also widen what feeds accept.
   Each ends with the desktop switch test and a heap check.
4. **Done (2026-11-12), the data half:** `AccountFeeds` owns an account's feed set on a child of
   the account's scope; `BaseAccountViewModel` leases it instead of building one, and releases it
   in `onCleared`. Active while leased (the event-bundle collectors moved here from the view
   model, so two view models of one account do not apply each bundle twice); warm for 15 minutes
   after the last lease, keeping notes and cursors and taking no events; cold after that, at once
   on memory pressure while warm, or when the account's scope ends (logout), when the set is
   destroyed and forgotten. A lease on a warm set rebuilds its built feeds from the cache, and the
   cursors ask the relays for what came since. Notification feeds go cold with the rest: the
   background tail keeps its own cursor (step 3.3), so they need no live holder. Desktop, warm
   period shortened to 1 minute for the check: switching away and back reused jack's feeds and
   Home's first REQ already had 167 of 559 relays caught up (it started from 0 before); the other
   account's set went cold one minute after its last lease and the heap went from two feed sets
   to one. Not done: the per-account `ViewModelStore` (scroll positions, open screens) still
   clears on a switch, and `AccountSession` at application scope is still tabs-and-windows Phase
   2b. Open: how many warm sets Android should allow at once (memory pressure releases them
   now).
5. **Done (2026-11-11):** every account notified on desktop, labelled on both platforms.
   `DesktopAccountsNotified` keeps each saved account that signs without asking (a key the vault
   keeps, or a remote signer) loaded, mounts its loaders through `AccountSubscriptionRegistry`,
   and runs `DesktopNotifications.watch` for it; the account on screen holds its toasts back while
   the main window has the focus, the others never do. They load only once keys read silently
   (the OS keyring answered, or the password file is unlocked): with a read-only account on
   screen the key store stays closed, and loading the others would raise the unlock prompt at
   launch by itself, so they wait until the user opens a signing account. Toasts read "For
   <account>: …" and Android notifications carry the account as sub-text, both only when more
   than one account is loaded (`NotificationAccountLabel`). Read-only accounts are not loaded in
   the background (as on Android). A click on a desktop toast opens what Android's tap does, in the
   notification's account (2026-11-11): each `NotificationSpec` carries `openUri`, the same
   `NotificationRoutes` link Android uses (`?account=`), and `NucleusNotificationDispatcher` maps
   each OS's click back to it (macOS delegate by request id, Linux default action by notification
   id, Windows toast launch arguments, the tray balloon's action). The click goes through
   `DesktopDeepLinks.openFromNotification`, and the deep-link routing now switches to the link's
   account first, as `AppNavigation` does on Android. Checked on desktop through the instance bus
   (read-only jack → a Notifications link for the signing account switched and opened it, then a
   link for jack switched back); the OS click callbacks themselves were not clicked: the container
   has no notification daemon.
6. **Done (2026-11-11):** the pruner. Measured first on desktop, with a hook that ran tier 2 on
   demand: a pass removed ~3,000 replies, reactions, zaps and reports (only ~650 of their parents
   were loaded: most removed notes were themselves Replies/Conversations feed items, and removal is
   a deletion to feeds), and a later pass freed 0.84 MB of 179.6 MB (0.5%). fiatjaf's profile, in
   the back stack, lost every reply and reaction on its notes (23 and 21 children to 0), and they
   stayed at 0 after returning to it: their cursors still said covered. Android runs that tier on
   most trips to the background. Changes: `pruneRepliesAndReactions` and the cache-wide
   `pruneHiddenEvents` are gone (the notes they removed go with their holders, and a pruned muted
   note never came back after an unmute); `prunePastVersionsOfReplaceables` moved to tier 2;
   channel prunes (`pruneOldMessages`, `pruneHiddenMessages`) now rewind the history cursors of
   what they drop, as DMs did: a relay group's chat and Threads windows and a Concord channel's
   (scrolling back after a prune used to end at a hole). Tier 2 went from ~5 s to ~50 ms on
   desktop. Decryption caches (`PrivateDMCache`, `PrivateZapCache`, the shared-key cache, the list
   caches) are LRU-bounded or per list and never needed the pruner; gift wraps and seals are weak
   notes, re-decrypted if collected and re-delivered.

7. **Done (2026-11-12):** every remaining manager names its holders, and the managers keep no
   cursors at all. `PerUserEoseManager`, `PerUniqueIdEoseManager`, `PerUserAndFollowListEoseManager`
   and `SingleSubEoseManager` lost their maps, and a key that names no holder is asked from scratch
   with nothing recorded (nothing vouches for what is in memory). Gone with them: `FeedOwnerTracker`,
   `MergedAuthorTracker`, `sinceDroppingGrownRelays`, `clearEoseFor`, the unused `PerKeyEoseManager`,
   `EOSEByKey`, `EOSEAccountKey` and `EOSECache`, and the dead `ReactionsFilterAssembler` and
   `MetadataFilterAssembler`. Holders, by what keeps the notes (a survey of every manager's kinds
   and where they land):
   - `Channel` (strong in `LocalCache`): the public, ephemeral, geohash and live chat tails, channel
     metadata and live-activity watchers, my messages in a channel, relay-group tails, threads and
     card warm-ups, nests rooms and their liveness probe (with the room note). The pruner rewinds a
     channel's cursors to the oldest message it drops.
   - `User`: profile metadata, and `UserWatcherSubAssembler` (each user's own cursor; no LRU keeping
     Users alive).
   - `Note` (its children): a thread's root, a poll's responses, a chess game's moves, each
     repository item's statuses and replies (per item, as `EventWatcherSubAssembler` does per note).
   - `Account`: own events, drafts, Cashu wallet, NWC notifications (handled, not kept), Buzz
     memberships, Concord planes, joined-group state, group discovery, a relay's group directory,
     followed public, ephemeral and geohash chats.
   - The key (`KeyHeldCursors`, remembered per screen): connected apps, the git dashboard, chess
     lobby games, software releases, NIP-66 relay info, search, d-list items, repository content,
     on-chain zaps, podcasts, DRSS episodes; community rules and profile followers and zaps (on
     `CommunityQueryState` / `UserProfileQueryState`); a top-nav key with no feed (nSites, nApplets,
     the DRSS river, the DVM heartbeat).
   - `CashuMintDirectoryState`: the mint directory.
   `SingleSubEoseManager` now builds entities whose cursors agree on every relay together, so a
   watcher of many channels or profiles still sends one batched filter per relay. Left without a
   cursor, as before: history pagers (`until`-bounded) and the DM and gift-wrap tails, which floor at
   `recentBoundary()`.

## Feed conversion (2026-11-10)

Done on the branch, each step its own commit:

1. **Contract.** `UniverseFeedFilter` (`universe`, `candidates`) and `rebuildFeed`, which logs
   every rebuild at debug level under `FeedRebuild` (feed, source, notes kept, ms): run a long
   session with `-Damethyst.debug=true` and read the costs. Notifications use candidates; Home
   walks the cache (its universe is most of it) and lost its candidate set.
2. **Screen feeds.** Profile threads/replies/gallery, hashtag and geohash are
   `OwnedUniverseFeedFilter`s: their `FeedViewModel` keeps the universe as a weak candidate set
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

4. **Video and Discover.** `FeedsKeptCurrent` is the reusable form of Home's triggers: a group of
   feeds that skip the resume rebuild, rebuilt (settled 2 s) when their inputs change and once
   when a future-dated note becomes visible. Mutes and spam flags are their own input: a change
   that only hides more re-checks the shown notes (`FeedContentState.refilterShown`, no walk), and
   anything else rebuilds. Groups:
   - Home: its list; mutes.
   - Video (a full cache walk, like Home): its list, follows and the report settings (it uses
     `Account.isAcceptable`); mutes; and a batch carrying reports re-checks the shown videos.
   - Discover Long-form, Follow packs, Marketplace (sorted by date): the Discover list; mutes.
   - Discover Chat, Communities, DVMs, Live keep the resume rebuild: they sort by activity that
     never reaches them as feed items (a channel's last message, boosts, interactions, stream
     status), and their rebuild scans one kind or the channel maps (0-6 ms on desktop).
   Over 12 returns on desktop: no rebuild of a kept-current feed on return; one burst of one
   rebuild per group, between returns, from an input change.

5. **The other account feeds.** Every account feed sorted by date that reads nothing but its
   events, its list and the mutes is kept current the same way, one `FeedsKeptCurrent` each:
   Polls (all), Pictures, Workouts, Highlights, Badges, Emoji packs, Git repositories (its
   co-maintainer merge already rebuilds on announcements), Calendar lists, Marketplace, Geocaches,
   Cache hunts, my geocaches and finds (no list), Articles and Follow packs. Shorts, Longs,
   Podcast episodes, Podcasts, Music tracks and playlists and App store also read
   `Account.isAcceptable`, so their inputs add follows and the report settings and a batch with a
   report re-checks what they show, as on Video. Still rebuilt on resume:
   - against the clock: Polls open/closed, Calendar appointments, Nests, Relay groups;
   - by activity that never arrives as one of their items: Chess, Public chats, Live streams,
     Messages, Communities list;
   - read off the account, not the cache (cheap): Drafts, Web bookmarks.
   On desktop: Highlights, Badges and Follow packs rebuilt twice when first opened and never on
   return; Live streams still rebuilt on each return.
6. **Community and URL screens** are `OwnedUniverseFeedFilter`s like profile and hashtag. A
   community's universe is its posts and approvals, whoever wrote them (`communityUniverse`);
   who may post or approve, the rules and the mod queue's "already approved" are checked at each
   rebuild, which maps an approval to the posts it approves (`rebuildFeedMapping`). URL's is the
   comments scoped to the page and its highlights, and its key carries the screen as `owner`.
   Gamestr on desktop: the feed and mod queue rebuilt from 215 candidates in 0-2 ms on return
   (92 and 2 rows). Not run: the URL screen. The community subscription shares one cursor per
   relay across communities (`SingleSubEoseManager`), so it has no per-screen owner yet.

7. **Rare-kind account feeds from candidates.** Video, Pictures, Shorts, Longs, Podcast episodes,
   Workouts, Highlights, Geocache finds and the three Polls tabs (one shared set) declare their
   universe and rebuild from a weak `CandidateSet` instead of walking the cache
   (`OwnedUniverseFeedFilter`, renamed from `ScreenUniverseFeedFilter` now that account feeds own
   sets too). A set costs nothing until its feed's first rebuild, which is its one cache walk.
   Desktop A/B (each candidate rebuild compared with a walk): identical for all ten (Video 487,
   Polls 52, open polls 129 of 252, Pictures 30, Highlights 10, …), except Podcast episodes, where
   the walk also counted two superseded versions of addressable episodes. Warm rebuilds 0-5 ms
   (open polls, which still rebuild on every return, 1-10 ms) against 7-13 ms walks on that small
   cache. Home keeps walking: its universe is most of the cache.

8. **The last screen feeds.** Single relay, mutual ("Yours" on a profile), one podcast's episodes
   and a follow pack's New threads and Conversations are `OwnedUniverseFeedFilter`s too. A follow
   pack's filter reads the pack's people once, when the screen opens, so its universe is fixed for
   the screen's life (as its walk already was). A relay's universe reads `Note.relays`, which grows
   without a new event when a cached note is seen on another relay, so the cache now routes every
   relay it records through one place (`EventCache.seenOn`) and tells the candidate sets that ask
   (`watchesRelays`, from `UniverseFeedFilter.universeReadsRelays`); the list of those is empty
   unless a relay screen is open. Like the walk, it leaves out addressable notes: without that the
   set took in ~415 relay lists, profiles and contact lists (kind 10002, 0, 3) as feed items.
   Desktop A/B: follow pack 79 and 96 notes, identical; relay.nos.social 443, 790 and 794 notes
   across rebuilds while it streamed, identical except two superseded relay-list versions the walk
   picked up from the notes map. One podcast and mutual compared empty to empty on that account.

9. **Audit fixes (2026-11-11).** A review of the branch found, and this fixed:
   - A relay picked as a kept-current group's list: a cached note seen on that relay later is no
     new event, and with no resume rebuild it never joined. The cache's relay hook
     (`RelaySeenObserver`, `EventCache.seenOn`) now also reaches the account, which hands such
     notes to the groups reading that relay (`FeedsKeptCurrent.seenOnSelectedRelay`, batched).
     Not run in the app: a read-only account cannot add a relay feed.
   - Future-dated notes: only the earliest was rescheduled, so a later one stayed hidden. Each
     group now keeps every pending time (up to 64) and only those of notes in its universe, so a
     future-dated reaction no longer rebuilds Home.
   - The account's event-bundle collector queued without bound; bundles that arrive while the
     feeds are busy now merge into one set (`coalesced()`).
   - The Home badge and the Home bar item built hidden tabs (Everything walks the whole cache);
     they now only build the tabs Settings shows.
   - A public-chat message loading after the replies below it now re-checks them for
     notifications (`NotificationCandidates.chatRepliesBelow`).
   - The per-user cursor-owner tracker held every visited `User`; it keys by pubkey now.
   - Dead candidate plumbing on the Home filters is gone, and the walk-only feeds log through a
     `rebuildFeed(name) { … }` overload without copying their set.

10. **Memory on switching and logging out (2026-11-11).** Desktop, class histograms after forced
    GCs. Switching (41 switches between a read-only and a signing account): account view models,
    feed sets, candidate sets, kept-current groups and background subscriptions stayed constant,
    and so did coroutine scopes. Logging out and back in with OS notifications on (10 cycles)
    found a leak older than this work: `ForwardKind0ToLocalRelayState`'s `EventCollector` is a
    listener on the app-wide client and was never destroyed, so every logged-out account's
    settings, signer, relay lists and ~100 state flows stayed reachable. It now leaves the client
    when the account's scope ends; after that, nothing per account grows across cycles. A
    previous screen's `AccountFeedContentStates` also stayed reachable (two live with one view
    model). A heap dump traced it: the account-level relay managers keep one subscription per user
    for as long as the account stays subscribed (always, now that background accounts are
    notified), and the jobs they start in `newSub` / `updateSubscriptions` captured the key that
    opened it, `AccountUiQueryState`, which held the screen's feed set, or captured the feed set
    itself. The key now holds it weakly, and the two notification managers' jobs hold only the
    notifications feed's depth flow. After: one feed set per view model across logout cycles and
    switches. Not measured on Android.
    The inbox notifications manager also started its "feed loaded deeper, re-plan" watcher only
    once per user, so after leaving an account and coming back it watched the old screen's feed
    and never fired again. It now restarts on a new screen's feed and stops when the account has
    no screen; with a screen key and a background key for one user, the screen's wins. Checked on
    desktop: the watcher followed the screen across switches, and Active Subscriptions showed the
    background account's DM inbox (gift wraps) and notifications queries still open on its relays.

11. **Signing flows checked (2026-11-11).** Two throwaway accounts: A signed up in the desktop
    app, B made with `amy` and posting a note and a kind-20 picture. In A, with no screen
    revisit: follow B put both posts on Home (one rebuild, 2 kept); Block & Hide took them off
    (`rechecked 2 shown: 0 kept`, no cache walk); unblock brought them back (full rebuild,
    2 kept); a report on B's picture kept it off Media while it stayed on Home (Home does not
    filter on reports, as before); unfollow emptied Home (rebuild, 0 kept). Single relay as a
    feed's list: a local geode (`amy serve`) added to A's Relay Feeds and picked on Home. B posted
    to damus only; A held the note open in a thread (so the cache kept it), then the same event
    was published to the local relay. It joined Home through `seenOnSelectedRelay` with no new
    event bundle and no rebuild. A note not held by anything is dropped by the cache and comes
    back as a new event, which the ordinary path handles.

No screen feed walks the whole cache any more. Threads, bookmarks, lists and pins read their notes
directly; Home walks it on purpose (its universe is most of the cache).

## Open questions

- How many accounts may be warm at once on Android? Measure what one account's feed set retains
  (class histogram before and after a switch) before choosing a cap.
- Read-only accounts: notify them too?
- Desktop publishes scheduled posts only for the account on screen (`LoggedInEffects`), while
  Android does it for every account. Same "background account" question; same answer?
