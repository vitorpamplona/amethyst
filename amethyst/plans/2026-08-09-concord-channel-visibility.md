# Per-channel visibility for Concord communities

**Status:** study / design options — no code written yet.
**Problem:** joining a Concord community is all-or-nothing. Every channel the
Control Plane folds becomes an inbox row, a live plane subscription, a preview
drain and an unread contributor. A user who only wants *one* discussion out of a
20-channel community has exactly two levers today: leave the community, or turn
**every** Concord community off (`ChatFeedType.CONCORD`). The ask is a middle
setting — "follow `#dev-chat` in this community, ignore the rest, and keep my
DMs clean."

---

## 1. What "all channels" is baked into today

Membership is per **community** (kind-13302 `ConcordCommunityListEntry`); the
channel set is *derived* by folding that community's Control Plane
(`ConcordCommunityState.channels`). Nothing between the fold and the UI is
per-channel selectable. Every consumer walks `state.channels.keys` unfiltered:

| Surface | Where | What it does with `channels.keys` |
|---|---|---|
| Messages inbox (INLINE) | `ChatroomListKnownFeedFilter.kt:187-212` | one row per channel of every joined community |
| Messages inbox (GROUPED) | same, `:201-211` | one row per community, newest across *all* channels |
| Inbox incremental update | `ChatroomListKnownFeedFilter.kt:402-441` | keys a new message to its channel/community row |
| Community server view | `ConcordChannelListScreen.kt:330-390` | one row per channel, no per-user filter |
| Concord hub | `ConcordHomeScreen.kt:~180-230` | expand community → all channels (or the UNREAD peek) |
| Live plane REQ | `ConcordSubscriptionPlanner.channelPlaneSubs` (`:117-141`), reached via `controlIsolatedFilters` (`:241-257`) ← `ConcordChannelFilterAssembler.kt:71-96` | subscribes every channel plane at every held epoch |
| Preview warm | `ConcordSubscriptionPlanner.channelPreviewFilters` (`:166-210`) ← `AccountConcordActions.warmConcordChannelPreviews` (`:1309-1329`) | one catch-up/preview filter **per channel**, for every joined community, always-on from `ConcordChannelPreviewAccountPreload` |
| Unread rollup | `ConcordUnread.kt` — `concordChannelUnreadCountFlow`, `concordCommunityHasUnreadFlow` | community dot = OR over every channel |
| Decrypt routing | `ConcordPlaneRegistry.registerChannels` (`:109-120`), `ConcordSessionRegistry.subscribeAddresses` (`:98-109`) | registers every channel plane address |
| Relay AUTH | `ConcordSessionManager.streamAuthSecretsFor` → `AuthCoordinator.kt:161` | signs kind-22242 with every plane key on that relay |

Partial affordances that already exist (and why none of them is the answer):

- **`ChatFeedType.CONCORD` on/off** (`MessagesSettingsScreen.kt:189`) — kills
  Concord globally, including the community you *do* want.
- **`ConcordViewMode.INLINE` / `GROUPED`** (`ConcordViewMode.kt`) — collapses the
  rows but still downloads and still counts unread for everything.
- **Hub `ChannelExpand.UNREAD` peek** (`ConcordHomeScreen.kt:206-223`) — a
  transient view state, not a preference; nothing persists and nothing stops
  downloading.
- **Bottom-bar pin of one channel** (`BottomBarEntry.ConcordChannel`,
  `BottomBarEntry.kt:116`) — promotes the channel you care about but does not
  demote the other nineteen.
- **`dismissedChannelInvites`** (`AccountSettings.kt:332`) — the closest existing
  *pattern*: a local-only set of channel ids the user chose not to show on
  Messages, explicitly "a display preference, not membership." Concord wants the
  same shape, one level deeper.

Note `ConcordSessionRegistry.subscribeAddresses()` is referenced only by tests —
production filter assembly goes through `ConcordSubscriptionPlanner`. That is
where a subscription-level filter has to land.

---

## 2. Semantics: pick the level before picking the storage

"Hide a channel" can mean three materially different things. They differ in
bandwidth, in how fast the channel opens when you do want it, and in how badly
they can strand you.

**Level A — cosmetic (inbox-only).** The channel keeps downloading and keeps its
unread count; it just stops rendering an inbox row. Cheapest to build (one
predicate in `ChatroomListKnownFeedFilter`), zero risk, and *does not* solve the
part of the ask that is really about noise-plus-battery in a 20-channel
community.

**Level B — muted.** Hidden from Messages, contributes no unread to the
community dot, but stays subscribed. Opening it is instant, no backlog gap. Cost
is unchanged (still one live plane + one preview filter per channel per relay).

**Level C — off / not-followed.** No live plane sub, no preview warm, no unread,
no inbox row. The channel still exists in the server view, marked off, one tap to
turn back on. This is the only level that actually shrinks the REQ set — the
preview warm alone is *one filter per channel per relay* on a debounce, so a user
in three 20-channel communities is paying ~60 filters for channels they never
read.

**Recommendation: build B and C as one tri-state, skip A.** A is a strict subset
of B and shipping it alone invites a second migration. Model it as:

```kotlin
enum class ConcordChannelVisibility { FOLLOWED, MUTED, OFF }
```

`FOLLOWED` is the default and matches today's behavior exactly.

### The default-for-new-channels question

This is the decision that actually shapes the feature. A community's channel set
is not static — staff create channels, and a fold can reveal one months later.
Two policies:

- **Opt-out (hide list):** store the exceptions; a newly folded channel is
  visible. Right for "this one channel is noisy."
- **Opt-in (follow list):** store the inclusions; a newly folded channel is off.
  Right for the literal ask — "I just want to follow a single discussion."

Neither alone is enough, and picking one globally makes the other case
miserable. Store **both**: a per-community default plus per-channel overrides.

```kotlin
class ConcordCommunityChannelPrefs(
    val default: ConcordChannelVisibility,          // FOLLOWED (normal) or OFF ("only what I pick")
    val overrides: Map<String, ConcordChannelVisibility>, // channelIdHex -> visibility
)
```

The UI for this is one switch per community — *"Only channels I pick"* — plus a
per-channel toggle. Flipping the switch on a community you already curated must
not silently drop the overrides.

---

## 3. Where the preference lives — four options

A hard constraint first: **Concord community ids and channel ids are private.**
Membership is invite-gated and E2E-encrypted; the plane addresses are derived
secrets. Anything that publishes a channel id in cleartext (a plain NIP-51 mute
list, a public `d`-tagged list) leaks that this npub is in that community. Every
viable option below is either device-local or self-encrypted.

### (a) Local-only `AccountSettings` — precedent: `dismissedChannelInvites`

Add `concordChannelPrefs: MutableStateFlow<Map<String, ConcordCommunityChannelPrefs>>`,
persist through `LocalPreferences` (`PrefKeys` + `putStringSet`/JSON at
`LocalPreferences.kt:646`/`:755`), key channels as `"$communityId/$channelId"` —
the same flattening `concordChannelLastReadRoute` already uses.

- **Pro:** smallest diff; no wire format, no merge, no privacy question; ships in
  one PR; trivially reversible.
- **Con:** does not sync. Curate on the phone, and the desktop still shows all 20
  channels. Given Concord's multi-device story is a headline feature (the 13302
  list exists precisely so *every* device re-derives the same planes), this is a
  real gap — but an acceptable v1 one, because last-read markers already don't
  sync either, so per-device divergence is a familiar behavior here.

### (b) Inside the kind-13302 entry (client extension)

The list document is NIP-44 self-encrypted and its codec preserves unknown keys
at **every** level (`ConcordCommunityList.ExtrasPreserving`, `:244-263`) — an
Armada/Vector peer would round-trip a new `channel_prefs` key untouched, which is
exactly what that machinery was built for. `WireChannel` already carries per-
channel entries in `current.channels` (with a `name` and an `extras` bag), so a
per-channel `visibility` could even ride *inside* the existing channel objects…
except those exist only for **private** channels the member holds a key for
(`PrivateChannelKey`), so a public channel has no entry to hang it on. It would
have to be a new sibling key on the join material.

- **Pro:** syncs across devices for free; private by construction; conceptually
  lives next to membership.
- **Con:** the 13302 write path is a read-modify-write with a lot of writers
  (`follow`/`unfollow` in `ConcordChannelListState.kt:112-127`, rekey adoption,
  `withControlRoot`, stranded recovery). Every one must carry the new field or it
  gets clobbered — the exact class of bug the residue machinery exists to prevent,
  now reintroduced on a *typed* field. Worse, `ConcordCommunityList.merge`
  (`:534-563`) resolves two devices by `rootEpoch`, which says nothing about
  whose *display preferences* are newer: two devices editing different channels
  in the same epoch would have one side's edits vanish. Converging needs a
  per-entry `prefs_updated_at`, i.e. real CRDT-ish design work.
- **Also:** it puts a pure UI preference into the security-critical join
  material. Every future reviewer of that file now has to reason about it.

### (c) NIP-78 synced settings (`AccountSyncedSettings`) — **recommended sync home**

`AccountSyncedSettings` is serialized to `AccountSyncedSettingsInternal`, NIP-44
self-encrypted, and published as kind-30078 app data
(`AppSpecificState.saveNewAppSpecificData:55-63`). It already carries a chats
section — `AccountChatPreferencesInternal.pinnedRooms`
(`AccountSyncedSettingsInternal.kt:240-245`) — which is the same kind of thing:
a private, per-account, cross-device chat display preference.

- **Pro:** syncs; private; the save/load/`updateFrom` plumbing exists and is
  well-trodden; adding a field is a mechanical change to three places
  (`AccountChatPreferences`, `…Internal`, `toInternal`/`updateFrom`); **zero**
  contact with the Concord wire format or the join material.
- **Con:** last-writer-wins on the whole 30078 event (same as every other synced
  setting today — pinned rooms, zap amounts). Two devices editing preferences
  simultaneously: one loses. Acceptable; it's the established bar.

### (d) A dedicated encrypted NIP-51 list

A new list kind for hidden Concord channels. No advantage over (c) — same
privacy, same sync, more code, one more replaceable event to fetch and reconcile.
Rejected.

### Recommendation

**(a) then (c), same domain type.** Define the preference model in `commons`
(`commons/…/model/concord/ConcordChannelVisibility.kt`) with one read predicate,
back it with local storage in v1, and move the backing store to
`AccountSyncedSettings.chats` once the semantics have settled with real use. The
predicate is the whole API surface, so the storage swap is invisible to the eight
call sites. Explicitly **not** (b): keep display preferences out of the join
material.

---

## 4. Implementation sketch

### 4.1 Domain (commons — shared with desktop/CLI)

```
commons/src/commonMain/kotlin/…/model/concord/ConcordChannelVisibility.kt
```

- `enum ConcordChannelVisibility { FOLLOWED, MUTED, OFF }`
- `ConcordCommunityChannelPrefs(default, overrides)` + `visibilityOf(channelIdHex)`
- `ConcordChannelPrefsState` — a `StateFlow<Map<communityId, prefs>>` holder with
  `visibility(communityId, channelIdHex)`, `isVisible(...)` (== not `OFF` for
  subscriptions; != `FOLLOWED` hides the row), and the setters. Backed by a
  repository interface (`ConcordChannelPrefsRepository`) exactly like
  `ConcordListRepository` — that interface is what lets v2 swap local prefs for
  synced settings without touching a consumer.

Everything downstream reads **one** helper so no two surfaces can disagree (the
same discipline `isConcordTimelineMessage` already enforces across the badge,
preview and feed).

### 4.2 Subscription layer (the actual win)

`ConcordSubscriptionPlanner` is pure and unit-tested — thread the filter in as a
parameter, never a global read:

- `channelPlaneSubs(entry, state, include: (channelIdHex) -> Boolean = { true })`
  — filter both the current-epoch and the historical planes (`:123-140`).
- `channelPreviewFilters(...)` — same predicate (`:174-181`), so an `OFF` channel
  costs no preview filter.
- `controlIsolatedFilters(...)` — take the predicate and pass it down (`:251-253`).

**Do not touch the Control Plane, the Guestbook, or the next-epoch rekey plane.**
They are what fold the channel list, the roster and an inbound Refounding; gating
them on channel visibility would silently break membership. Likewise leave
`ConcordPlaneRegistry.registerChannels` and `streamAuthSecretsFor` alone —
registering an address you don't currently subscribe to costs nothing and keeps
an `OFF` channel instantly re-subscribable (and keeps AUTH working if a wrap
arrives from a shared REQ).

Call sites to thread it through: `ConcordChannelFilterAssembler.kt:85-94` and
`AccountConcordActions.warmConcordChannelPreviews:1317-1324`.

### 4.3 UI

- **Inbox** — `ChatroomListKnownFeedFilter`: filter `state.channels.keys` in both
  view modes (`:195`, `:206`) and drop non-`FOLLOWED` notes in
  `filterRelevantConcordMessages` (`:421-441`). GROUPED mode must roll up only
  followed channels, or a muted channel keeps bumping the community row.
- **Server view** — `ConcordChannelListRow` already has an overflow menu, but it
  is gated on `canManageChannels` (`ConcordChannelListScreen.kt:465-470`). A
  visibility toggle belongs to *every* member, so the menu needs to render
  unconditionally with the manage-only items gated inside it. Render `OFF`
  channels dimmed at the bottom (or behind a "Show N hidden channels" footer) —
  never remove them, or there is no way back.
- **Hub** — `ConcordHomeScreen` expansion lists followed channels; a hidden count
  in the community header.
- **Community switch** — "Only channels I pick" in the server view overflow, next
  to Leave.
- **Channel screen** — a mute/unfollow item in the channel's own overflow, so you
  can act where the noise is.
- **Bottom bar** — a pinned `BottomBarEntry.ConcordChannel` that is `OFF` is a
  contradiction. Cheapest correct rule: pinning forces `FOLLOWED`; turning a
  pinned channel off also unpins it.

### 4.4 Unread and last-read

- `concordCommunityHasUnreadFlow` must fan in over followed channels only.
- Turning a channel back on after weeks: we never downloaded the backlog, so the
  badge would be whatever the catch-up filter pulls — noisy and wrong-looking.
  Stamp last-read to "now" when a channel goes `OFF` (`markAsRead` on
  `concordChannelLastReadRoute`), so re-enabling starts clean and the catch-up
  filter's `since = lastRead - 1` window stays small. Muting (B) should *not*
  stamp — muted channels stay accurate, they just don't shout.

### 4.5 Tests

- `ConcordSubscriptionPlanner` — `OFF` channel produces no plane sub and no
  preview filter, at the current epoch *and* every held epoch; Control/Guestbook/
  rekey planes survive with every channel off.
- Prefs model — per-community default flip preserves overrides; unknown channel
  → default; new channel under `default = OFF` stays off.
- `ChatroomListKnownFeedFilter` — INLINE drops the row; GROUPED excludes the
  hidden channel from the community's newest-message rollup.
- Unread — community dot ignores hidden channels.
- Round-trip persistence (v1 local; v2 the 30078 encode/decode).

---

## 5. Risks / open questions

1. **Voice channels** (`ChannelEntity.voice`) — presence/`nestsClient` joins are
   separate from message planes. Does `OFF` also suppress voice presence? Assume
   yes for consistency, but it needs a look at the CORD-07 presence path.
2. **Private channels** — an `OFF` private channel still keeps its
   `PrivateChannelKey` in the 13302 entry (it must: the key is delivered, not
   re-derivable). Visibility must never touch key material.
3. **Refounding (CORD-06)** — channel ids are stable across a Refounding, so
   overrides keyed by `channelIdHex` survive. Worth an explicit test; a rebuilt
   session must not resurrect hidden channels.
4. **Mentions in a hidden channel** — muting a channel where someone @s you is
   how people miss things. Concord messages currently produce no push
   notifications (the tray path in `NotificationDispatcher` never sees plane
   rumors), so there is nothing to leak *today* — but an `OFF` channel is not
   downloaded at all, so a future mention-notification feature can never see it.
   Document that as the deliberate cost of `OFF`, and keep `MUTED` as the
   "quiet but still watching" answer.
5. **Discoverability** — a user who hides everything and forgets will read it as
   a bug. The community row should always show "N channels hidden."

---

## 6. Smallest useful slice

If this needs to land incrementally, the first PR that is genuinely useful on its
own:

1. `ConcordChannelVisibility` + local prefs + repository interface (commons).
2. Predicate threaded into `ConcordSubscriptionPlanner` (both plane subs and
   preview filters) and the two call sites.
3. Inbox filtering in `ChatroomListKnownFeedFilter` (both view modes).
4. Per-channel toggle in the server-view row menu, ungated from
   `canManageChannels`, plus the hidden-channels footer.
5. Unread rollup fix.

The per-community "only channels I pick" switch and the sync migration to
NIP-78 follow as PR 2 and PR 3.
