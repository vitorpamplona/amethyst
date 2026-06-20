# Amy notifications — `amy notifications`

Status: implemented.
Owner extraction: `commons/.../ui/feeds/notifications/NotificationFeed.kt`.

## Goal

Bring Amethyst's **Notifications** feed to Amy: the events that notify you —
reactions, reposts, zaps, replies, mentions, comments — with the same
two-phase "drawing" model already used by `notes home` (snapshot by
default, opt-in live JSONL tail under `--watch`). See
`cli/plans/2026-06-19-home-feed.md` for the drawing-problem rationale; it
is identical here.

## Inclusion: a notification is an event that p-tags you

Amethyst's `NotificationFeedFilter.acceptableEvent` gates every event on
`isTaggedUser(me)` — a `#p`-tag for the logged-in user. So the entire feed
is a single relay query: `{"#p":[me], "kinds":[NOTIFICATION_KINDS]}`. The
p-tag gate already captures the dominant cases — reactions, reposts and
zaps all tag the note's author (you), and replies/mentions tag you
directly.

On top of the p-tag gate, Amethyst's **Global** notification mode applies
only Event-level checks, all reproducible against a raw Event store:

- kind ∈ `NOTIFICATION_KINDS`
- not your own event — **except zaps** (a zap can be self-directed through
  a provider, so `is LnZapEvent || pubKey != me`)
- author not muted/blocked
- not stamped in the future

Its **Selected** mode additionally runs `tagsAnEventByUser`, the per-kind
relevance heuristic (reaction/repost target authorship, reply parents,
citations, community moderation). That walks the `Note` reply graph and
does `LocalCache` author lookups, so it is **not** reproducible against
Amy's raw Event store. Amy therefore reproduces **Global** mode — a real,
first-class Amethyst mode — exactly, and defers Selected-mode narrowing.

### Extraction (Rule 5)

The notification kind taxonomy moves to
`commons/.../ui/feeds/notifications/NotificationFeed.kt` as the single
source of truth:

- `NotificationFeedKinds: Set<Int>` (base + addressable) — Android's
  `NotificationFeedFilter.NOTIFICATION_KINDS` now references it.
- `NotificationFeedAddressableKinds: List<Int>` — the addressable subset
  Android still scans separately.
- `Event?.isNotificationRenderableKind()` (with the `returns(true)→non-null`
  contract).
- `NotificationFeedParams(myPubkey, hidden)` with `match(event)` =
  the Global-mode predicate above.
- `Collection<Event>.sortedByNotificationFeedOrder()` — newest-first, dedup.

Android keeps `ADDRESSABLE_KINDS` (its addressables-cache scan needs it)
and the whole `tagsAnEventByUser` Selected-mode path untouched.

### Inputs Amy feeds the filter

- `myPubkey`: the active identity.
- `hidden`: NIP-51 kind:10000 mute list (public + private via signer),
  loaded by the shared `Context.hiddenUsers()` (also used by `notes home`).
- relays: **inbox** (NIP-65 read) relays — where events tagging you land.

### Known parity gaps (documented, deferred)

- **Selected-mode `tagsAnEventByUser`.** Reaction/repost target authorship,
  reply-parent and citation relevance, and community-moderation checks need
  the Note reply graph + `LocalCache`. Global mode (Amy's default) does not
  use them.
- **Zap author muting.** Android mutes on the *zapper* (decrypted from the
  zap request); Amy mutes on the receipt's `pubKey` (the provider). A muted
  user's zap may slip through.
- **Muted-thread resolution** for reaction/zap/repost *targets* (needs the
  target's thread root from the graph) and **decrypted-DM-content hiding**
  are not applied.

## CLI surface

```
amy notifications [--limit N] [--since TS] [--until TS] [--timeout SECS]
                  [--watch [--duration SECS]]
```

Alias: `notifs`. Snapshot `--limit` (50) / `--timeout` (8); watch
`--duration` (60, SIGINT also stops). Reuses `Context.stream` /
`Output.emitLine` from the home-feed work.

## Files

- `commons/.../ui/feeds/notifications/NotificationFeed.kt` (new shared).
- `amethyst/.../notifications/dal/NotificationFeedFilter.kt`
  (`NOTIFICATION_KINDS` → references commons; prune now-unused imports).
- `cli/.../commands/NotificationsCommand.kt` (new).
- `cli/.../Context.kt` (`hiddenUsers()` lifted out of `HomeFeedCommand`).
- `cli/.../commands/Commands.kt`, `Main.kt` (dispatch + usage),
  `README.md`, `ROADMAP.md`.
- `commons/.../NotificationFeedTest.kt` (predicate unit test).
