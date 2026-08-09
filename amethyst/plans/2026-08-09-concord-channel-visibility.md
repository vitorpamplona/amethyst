# Hiding individual Concord channels from the Messages screen

**Status:** study / design options — no code written yet.

**Scope, stated precisely:** this is a **Messages-screen display preference and
nothing else**. It is not membership, not a mute, not an unsubscribe. The user
stays fully joined to the community and to every one of its channels; the client
keeps downloading exactly what it downloads today; the community's own screens
(hub, server view, channel list) keep listing every channel with live previews
and unread badges. The only thing that changes is which rows the **Messages**
inbox is willing to render.

**Privacy requirement:** nobody else — not other members, not staff, not the
relay — may be able to tell that a user has hidden a channel from their inbox.

**Problem:** joining a Concord community turns every channel it folds into a
Messages row. A user who wants one discussion out of twenty has only two levers:
leave the community, or turn all of Concord off (`ChatFeedType.CONCORD`). There
is no "keep me in, just stop putting these in my inbox."

---

## 1. The single choke point

Everything the Messages screen shows for Concord comes out of **one** class:

| What | Where |
|---|---|
| Rows, INLINE mode (one per channel) | `ChatroomListKnownFeedFilter.kt:187-200` |
| Rows, GROUPED mode (one per community, newest across all its channels) | `ChatroomListKnownFeedFilter.kt:201-211` |
| Incremental row updates as messages arrive | `filterRelevantConcordMessages` (`:421-441`) |
| Row identity for replace-in-place | `Note.concordRowKey()` (`:402-412`) |

And the **Messages bottom-bar dot** reads that same filter's output —
`AccountViewModel.messagesHasNewItems` (`:454-472`) fans in over
`feedStates.dmKnown.feedContent`, i.e. the rows this filter produced. So a row
dropped here also stops lighting the nav dot, with no second code path to keep in
sync. That is the whole nagging surface.

Two things confirmed *not* in scope, by inspection:

- `ChatroomListNewFeedFilter` (the "New" tab) handles only DMs and Marmot groups
  — it has no Concord branch at all, so nothing to change.
- The community's own surfaces (`ConcordHomeScreen`, `ConcordChannelListScreen`,
  `ConcordUnread.concordChannelUnreadCountFlow`) are deliberately untouched. A
  hidden channel still shows its unread badge in the server view. That is the
  point: you didn't leave, you just don't want it in your inbox.

Everything I looked at in the subscription layer — `ConcordSubscriptionPlanner`,
`ConcordChannelFilterAssembler`, `warmConcordChannelPreviews`,
`ConcordPlaneRegistry`, `streamAuthSecretsFor` — stays **exactly as is**.

### Why not touching the subscription layer is the privacy answer

A relay sees which plane addresses you `REQ`. If hiding a channel also dropped
its plane subscription, a relay operator (often the community's own host) could
watch a member's REQ set shrink and infer which channels they stopped caring
about. Keeping downloads byte-identical makes the preference unobservable to
anyone but the user — the privacy requirement is met by *construction*, not by
policy. It also means the feature has no protocol surface at all: no new event,
no new field on the wire, nothing another client could parse.

(The trade-off is honest: this is a UI filter, so it saves no bandwidth. The
preview warm still emits its per-channel catch-up filter for hidden channels. If
bandwidth ever becomes the complaint, that's a *separate* feature with a
different threat model — and it should be labelled differently in the UI, because
it is observable.)

---

## 2. Model

Binary per channel, plus a per-community default:

```kotlin
class ConcordInboxPrefs(
    /** Show newly-folded channels in Messages? true = normal, false = "only the ones I pick". */
    val showNewChannels: Boolean = true,
    /** Explicit per-channel overrides, channelIdHex -> shown. */
    val overrides: Map<String, Boolean> = emptyMap(),
) {
    fun isShown(channelIdHex: String) = overrides[channelIdHex] ?: showNewChannels
}
```

Stored per community id. Default state (`showNewChannels = true`, no overrides)
is byte-for-byte today's behavior.

**Why the per-community default is not optional.** The stated use case is "I just
want to follow a single discussion in that community." With overrides alone that
user hides nineteen channels by hand — and then a twentieth appears next week and
lands back in their inbox. The default flag turns that into one switch: *"Only
show channels I pick"*, then tick the one. Flipping the switch must preserve
existing overrides, so a user can go back and forth without re-curating.

---

## 3. Where it lives

Both remaining candidates satisfy "other users cannot tell." The choice is only
about whether it follows the user to their other devices.

### (a) Local-only, `AccountSettings` — precedent: `dismissedChannelInvites`

`AccountSettings.kt:332` already carries exactly this kind of thing: a local-only
set of channel ids "the viewer chose NOT to show on Messages… a display
preference, not membership." Same sentence, one level deeper. Persist through
`LocalPreferences` (`PrefKeys` + the save/load pair at `:646`/`:755`), keyed
`"$communityId/$channelId"` — the flattening `concordChannelLastReadRoute`
already uses.

- **Pro:** smallest possible diff; nothing published anywhere, so the privacy
  requirement is trivially met; no merge semantics.
- **Con:** curate on the phone, the desktop still shows all twenty. Last-read
  markers already behave this way, so it is a familiar divergence — but it is a
  divergence.

### (b) NIP-78 synced settings (`AccountSyncedSettings`)

`AccountSyncedSettings` serializes to `AccountSyncedSettingsInternal`, is NIP-44
self-encrypted, and publishes as kind-30078 app data
(`AppSpecificState.saveNewAppSpecificData:55-63`). Only the user's own key
decrypts it. Its chats section already holds a private cross-device inbox
preference of the same family — `pinnedRooms`
(`AccountSyncedSettingsInternal.kt:240-245`).

- **Pro:** syncs; still invisible to everyone else; the save/load/`updateFrom`
  plumbing exists; adding a field is mechanical (three places).
- **Con:** last-writer-wins on the whole 30078 event — the established bar for
  every synced setting today (pinned rooms, zap amounts). Also: the ciphertext
  size grows with the number of curated channels; a relay can see *that* the blob
  changed, never what changed. Worth stating out loud since the requirement is
  explicit — an observer learns "this user edited some app setting," which is
  already true every time they pin a room.

### Rejected

- **Inside the kind-13302 entry.** It would sync and it is self-encrypted, but it
  is the wrong home for the same reason the user just made clear: that document
  is *membership and key material*, and this is not membership. Practically it is
  also the riskiest place to put it — the entry is read-modify-written by
  `follow`/`unfollow`, rekey adoption, `withControlRoot` and stranded recovery,
  every one of which would have to carry the field or clobber it, and
  `ConcordCommunityList.merge` resolves two devices by `rootEpoch`, which says
  nothing about whose display preferences are newer.
- **Any public list (NIP-51 mute list, a public `d`-tagged list).** Concord
  community and channel ids are private; publishing one in cleartext leaks
  membership itself, never mind the preference. Hard no.

### Recommendation

**(a) now, with the model and the read predicate defined so (b) is a
drop-in.** Put `ConcordInboxPrefs` in `commons` behind a small repository
interface — the same shape `ConcordListRepository` uses — so moving the backing
store from local prefs to `AccountSyncedSettings.chats` later touches one class
and no call sites.

---

## 4. Implementation

### 4.1 Model (commons)

`commons/src/commonMain/kotlin/…/model/concord/ConcordInboxPrefs.kt`

- `ConcordInboxPrefs` as above.
- `ConcordInboxPrefsState` — holds `StateFlow<Map<communityId, ConcordInboxPrefs>>`,
  exposes `isShownInMessages(communityId, channelIdHex)` plus setters
  (`setShown`, `setShowNewChannels`), backed by a
  `ConcordInboxPrefsRepository` interface.

One predicate, one name, read by every consumer — the same discipline
`isConcordTimelineMessage` already enforces so the badge, the preview and the
feed can't disagree.

### 4.2 The filter (`ChatroomListKnownFeedFilter`)

- **INLINE** (`:193-199`): filter `state.channels.keys` through the predicate.
- **GROUPED** (`:203-210`): roll up only shown channels; a community whose
  channels are *all* hidden must produce **no row at all** (return null), or the
  community keeps surfacing on the strength of a channel the user hid.
- **`filterRelevantConcordMessages`** (`:421-441`): drop notes whose channel is
  hidden, in both modes — otherwise a new message re-adds a row the `feed()` pass
  had excluded.
- **`concordRowKey`** (`:402-412`): unchanged.
- The prefs flow must be wired into `AccountFeedContentStates` alongside
  `concordViewMode` (`:234`) so editing a preference re-runs the filter
  immediately.

That is the entire behavioral change. The nav dot follows for free.

### 4.3 UI

- **Primary: from the row that's bugging you.** Long-press (or the existing chat
  row action sheet) on a Concord Messages row → *"Hide from Messages"*. Acting
  where the annoyance is beats hunting through settings, and it's the one gesture
  that makes the feature discoverable at all.
- **Manager: per community.** *"Channels shown in Messages"* — the community's
  channel list with a checkbox each, plus the *"Only show channels I pick"*
  switch at the top. Reachable from the server-view overflow (next to Leave) and
  from `MessagesSettingsScreen` under the existing Concord section (`:189-208`),
  which is where a user who hid something and forgot will go looking.
- **Server view row menu.** `ConcordChannelListRow`'s overflow currently renders
  only when `canManageChannels` (`ConcordChannelListScreen.kt:465-470`) — a
  per-user inbox preference belongs to *every* member, so the menu needs to
  render unconditionally with the manage-only items gated inside it.
- **Never make a hidden channel unreachable.** It stays in the hub and the server
  view, unchanged, with its unread badge — so "hidden" can never mean "lost."
- **Say so on the community row.** In GROUPED mode, the community row should note
  "N channels hidden" somewhere visible, and the server view likewise, so the
  state is never a mystery.
- **Bottom-bar pins are orthogonal.** A pinned `BottomBarEntry.ConcordChannel`
  stays pinned and functional while hidden from Messages — "give it a tab, keep it
  out of the inbox" is a coherent thing to want, not a conflict to resolve.

### 4.4 What this scope deliberately does *not* need

Worth recording, because the wider version of this feature needs all of it and
this one needs none:

- No last-read stamping games. The channel keeps downloading, so unhiding shows
  an accurate count with no backlog gap.
- No re-subscribe path, no epoch/Refounding interaction, no
  `streamAuthSecretsFor` change.
- No private-channel or voice-channel special cases (no key material involved).
- No mention/notification hazard — a hidden channel is still fully downloaded and
  still visible in the community's own screens.

### 4.5 Generalizing later (design the key for it)

`ChatroomListKnownFeedFilter` already computes a stable row key per row family —
`concordRowKey`, `relayGroupRowKey`, `geohashRowKey`, public-chat channel id. The
identical complaint exists for NIP-29 relay groups (`:150-179`, same
INLINE/GROUPED shape). If the stored key is a *prefixed row key*
(`"concord:$communityId/$channelId"`) rather than a bare Concord pair, the same
storage and the same predicate extend to hiding a NIP-29 group or a public chat
from Messages without a second migration. Cheap now, awkward to retrofit.

### 4.6 Tests

- INLINE: hidden channel produces no row; the channel still appears in the
  community's own channel list and still counts unread there.
- GROUPED: hidden channel excluded from the community's newest-message rollup;
  all-hidden community produces no row.
- `filterRelevantConcordMessages`: a new message in a hidden channel does not
  re-add the row (both modes).
- Nav dot: unread confined to hidden channels leaves `messagesHasNewItems` false.
- Defaults: `showNewChannels = false` keeps a newly folded channel out; flipping
  the switch preserves overrides; unknown channel falls back to the default.
- Persistence round-trip (local now; the 30078 encode/decode if/when synced).
- Regression: default-state prefs produce exactly today's feed.

---

## 5. Open questions

1. **Does hiding survive across communities with the same channel name?** Keys
   are `(communityId, channelIdHex)`, so yes — no cross-talk. Just don't key by
   name.
2. **Refounding (CORD-06)** rotates roots and epochs but keeps channel ids, so
   overrides survive. Cheap to assert in a test; worth doing since a rebuilt
   session must not resurrect hidden rows.
3. **Should a message that @s the user override the hide?** My read is no — an
   override makes the setting unpredictable, and the user's framing ("stop
   bugging me in Messages") is unconditional. Worth a decision though, since it's
   the one case where a user might later say "but I wanted that one."
4. **Sync now or later?** (a) ships in one small PR; (b) is maybe half a day more
   and spares a "my desktop still shows them" report. If the multi-device story
   matters more than the ship date, go straight to (b) — the model is identical
   either way.
