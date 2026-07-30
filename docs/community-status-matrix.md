# Community status matrix — Buzz and Concord

Every status a user can hold in a Buzz workspace or a Concord community, split by
**who decides it**: the community/relay side (authoritative, published as events) or our
own device (local bookkeeping — "Add to Messages", stars, dismissals).

The two protocols answer "what is this user's standing?" from opposite ends:

- **Buzz** is a member of the **NIP-29 relay-group family**. The host relay is the single
  source of truth: it signs the roster (39001 admins / 39002 members per channel, NIP-43
  13534 per tenant), enforces reads and writes server-side, and keeps content plaintext. A
  client can observe status but never grant itself any.
- **Concord** has **no server authority at all**. Standing is (a) *key possession* — anyone
  holding the community root can decrypt, which IS membership — and (b) the **owner-rooted
  folded Control Plane** (kind-3308 editions: roles, grants, banlist). Relays store opaque
  kind-1059 wraps and cannot tell members from strangers.

Consequence: a Buzz status is *revocable by the relay*; a Concord status is only revocable
by **rotating the key** (CORD-06 Refounding). Everything else in Concord is advisory.

---

## 1. Buzz / NIP-29 — per-channel status

The relay's truth, from its own signed lists. Modeled by
`RelayGroupMembership` (`commons/.../model/nip29RelayGroups/RelayGroupMembership.kt`),
resolved in `RelayGroupChannel.membershipOf()`.

| Status | Where it comes from | Read | Post | Moderate | Notes |
|---|---|---|---|---|---|
| `ADMIN` | kind **39001** entry whose role is `admin` **or** `owner` | ✅ | ✅ | ✅ | Buzz's hierarchy is `owner` > `admin` > `member` (no moderator), so the channel creator carries `owner` — both strings map here via `ADMIN_ROLES`. Gates Edit, Delete (9008), Archive. |
| `MODERATOR` | kind **39001** entry with any *other* / unrecognized / absent role label | ✅ | ✅ | ✅ | Presence in the admins list **is** the moderation signal; an unknown role string is never demoted to plain member. Gates Invite, not Edit/Delete. |
| `MEMBER` | kind **39002** members list | ✅ | ✅ | ❌ | |
| `MEMBER` (community fallback) | NIP-43 tenant roster (13534 / 8000 / 8001) via `BuzzCommunityMembership`, **Buzz relays only** | ✅ | ✅ | ❌ | A Buzz community member may participate in every channel without a per-channel 39002 row. Deliberately mapped to `MEMBER` only — never channel `ADMIN` — to avoid over-granting per-channel moderation. |
| `PENDING` | **client-only**, never read from a relay | — | ❌ | ❌ | Optimistic state set when we publish a kind-**9021** join; cleared once the roster shows us. Held in a `remember{}` in `RelayGroupTopBar`, so a fresh visit reads relay truth again. |
| `NONE` | in neither list (or the lists haven't loaded) | depends on channel flags | depends on flags | ❌ | On an **open Buzz channel**, `NONE` can still post — see the flags table. |

**Agent (NIP-OA) virtual membership** is a fourth way to be a member: an un-enrolled agent
key that presents an owner-signed `auth` tag on its NIP-42 AUTH is granted membership **for
the life of that connection**. It never appears in 39001/39002, so `membershipOf()` reports
`NONE` for it even while the relay accepts its writes.

### 1a. Channel flags that change what a status can *do*

From the relay-signed kind-39000 metadata (`GroupMetadataEvent`). These are orthogonal to
the membership status above — the same `MEMBER` behaves differently per flag.

| Flag / field | Meaning | Effect on the user |
|---|---|---|
| `private` | only members can read | The real write ACL on Buzz: `requiresMembershipToPost()` is `!isBuzz \|\| isPrivate()`. |
| `restricted` | only members can write | Standard NIP-29 write gate. |
| `closed` | join requests ignored (invite-only) | Join needs an invite code. **Buzz stamps `closed` on every channel**, so there it says nothing about who may post. |
| `hidden` | metadata hidden from non-members | |
| `public` / `open` | the inverse status tags | |
| `livekit` | live audio/video capable | |
| `archived` (Buzz-only) | relay hid it from the sidebar; history intact | Reversible via 9002; admin-gated, non-destructive. |
| `t` = `stream` / `forum` / `dm` / `workflow` | Buzz channel type | A `dm` gets a different title, no forum/share, and its own hide mechanism. |
| `parent` / `child` | subgroup tree position | |
| `supported_kinds` | kinds this group accepts | |

On a **vanilla NIP-29 relay** membership is required to post to *every* group (`open` only
means a 9021 is auto-approved). On a **Buzz relay** an open (non-`private`) channel accepts
kind-9 from any authenticated tenant member with no per-channel join at all — which is why
`canPost()` branches on the dialect.

---

## 2. Buzz — per-relay / workspace (tenant) status

A Buzz workspace **is a relay** (a tenant, keyed by the connection host). These statuses are
relay-wide and sit *above* every channel on it.

| Status | Set by | Cleared by | Effect |
|---|---|---|---|
| **Owner** | relay config only | — | Not transferable by event: 9032 explicitly refuses `owner`. |
| **Admin** | kind **9032** `role=admin` (owner-signed only) | 9032 `role=member` | Relay-admin commands. |
| **Member** | kind **9030** add-member, or NIP-43 **8000**; snapshot republished as **13534** | **9031** / **8001** | Read + post across the tenant's open channels; the `MEMBER` fallback in §1. |
| **Not a member (authenticated)** | — | — | Relay answers `restricted: not a workspace member`. |
| **Unauthenticated** | — | — | Relay answers `auth-required` so the client runs NIP-42 and retries. |
| **Banned** | kind **9040** | **9041** unban | Write-blocked tenant-wide. (9041 collides with NIP-75 `GoalEvent`, which keeps the `EventFactory` slot — the Buzz class must be built/parsed explicitly.) |
| **Timed out** | kind **9042** + mandatory `expiration` | **9043**, or expiry | Temporary write block. Validated and executed by the relay, never stored. |
| **Archived identity** | **9035** request → relay-signed **8002**, listed in **13535** | **9036** → **8003** | NIP-IA identity retirement, with an optional `replaced_by` rotation pointer. |
| **Virtual member (agent)** | NIP-OA `auth` tag on the NIP-42 AUTH, owner must be a member | dropping the socket, or revoking the owner | Connection-scoped only. |
| **Presence** `online` / `away` / `offline` | ephemeral kind **20001** | — | Not rendered: 20001 is registered as `GeohashPresenceEvent` in `EventFactory` and needs disambiguation first. |
| **Typing** | ephemeral kind **20002** | 6s heartbeat staleness | Wired (`BuzzTypingState` → `BuzzTypingIndicator`). |

---

## 3. Concord — per-community status

Derived locally by folding the Control Plane; `ConcordMembership.of(authority, pubkey, holdsKey)`.
Nothing here is enforced by a relay — every client folds the same editions and reaches the
same verdict, or the edition is dropped.

| Status | Derivation | Read | Post | Moderate |
|---|---|---|---|---|
| `OWNER` | pubkey == the community's owner key | ✅ | ✅ | ✅ |
| `ADMIN` | holds **any** of `MANAGE_ROLES`(0), `MANAGE_CHANNELS`(1), `MANAGE_METADATA`(2), `KICK`(3), `BAN`(4), `MANAGE_MESSAGES`(5) | ✅ | ✅ | ✅ |
| `MEMBER` | holds the community key, no moderation bit | ✅ | ✅ | ❌ |
| `BANNED` | in the healed banlist union | keys still decrypt | ❌ | ❌ |
| `NONE` | not placeable (no key, no role) | ❌ | ❌ | ❌ |

A role-holder whose only bits are `CREATE_INVITE`(6), `VIEW_AUDIT_LOG`(8) or
`MENTION_EVERYONE`(9) classifies as **`MEMBER`** — those aren't in `MOD_BITS`.

`rank` is a second, independent axis: the owner is rank **0** (supreme, unremovable, and no
role may claim position 0); lower position = higher authority; `canActOn` requires the actor
to hold the bit **and strictly outrank** the target ("equal cannot act on equal").

### 3a. Community-level states layered on top

| State | Source | Effect |
|---|---|---|
| **Dissolved** | owner-only `DISSOLVED` tombstone (CORD-02 §9) | Terminal, one-way. The community seals **read-only for everyone including the owner**: held keys still open history, nothing new is honored. `ConcordChannel.canPost() = membership.isMember() && !dissolved`. The one carve-out — deleting your own past message — runs through the note menu, not the composer, so this gate never blocks it. |
| **Current epoch** | `rootEpoch` + `heldRoots` on the kind-13302 entry | Which epochs' history you can still derive. Prior-epoch roots are kept so pre-Refounding messages stay readable. |
| **Stranded / excluded** | `excludedAtEpoch`, detected by `ConcordStrandedRecovery` | A Refounding carries **no recipient list**, so a member simply left out hears nothing and sits on a dead epoch forever. The only way back is re-resolving the membership's own `inviteRef` and finding a higher epoch — then `mergeForward` catches up while preserving the anchor and prior roots. |
| **Refounded out** | not receiving the new root | The only *real* eviction Concord has: cryptographic, not a flag. |
| **Kicked** | kind **3309** Guestbook kick, honored only from a `KICK` holder who outranks the target | Off-consensus and advisory. **Modeled in Quartz, not wired in the client** — nothing calls `Guestbook.kick`/`kickTarget`. |
| **Guestbook joined / left** | self-signed kind **3306** `join`/`leave` | Best-effort presence for member discovery, explicitly *not* authority. |

CORD-04's "Three Removals" are therefore distinct: **Kick** (advisory), **Ban** (denies
standing but keys still decrypt), **Refounding** (actually evicts).

### 3b. Invite-link status (how a user *gets* standing)

`InviteBundleStatus` (protocol) and `ConcordInviteResult` (app-facing, tells the UI whether
retrying can ever help):

| Bundle status | App result | Retry helps? |
|---|---|---|
| `Live` (`vsk=6`, opens with the token) | `Joined(communityId)` | — |
| `Revoked` (newest event at the coordinate is a `vsk=9` tombstone) | `Revoked` | ❌ owner retired it |
| `Expired` (`expires_at` passed; invite still carried for preview) | `Expired` | ❌ ask for a fresh link |
| `Unreadable` (wrong token, wrong sub-kind, newer format) | `Incompatible` | ❌ |
| `Absent` (nothing found on any relay) | `NotReachable` | ✅ transient |
| malformed link / read-only key | `InvalidLink` | ❌ |

---

## 4. Concord — per-channel status

| State | Source | Notes |
|---|---|---|
| **Exists** | a non-tombstoned `CHANNEL` edition in the fold | Gated by `MANAGE_CHANNELS`. |
| **Deleted** | `deleted: true` | Terminal — the channel id is never reused. |
| **Voice** | `voice: true` | Audio channel (`Mic` icon); CORD-07 broker token + kind-23313 voice presence. |
| **Private** | `private: true` | Derived-key visibility: a separate plane key delivered per member in the kind-13302 entry's `privateChannels`, at that channel's own epoch. Resolved by `ConcordChannelPlanner`; a member holding no key for it sees no channel at all. |
| **Typing** | ephemeral kind **23311** | Wired, with a staleness window. |
| **Posting allowed** | `membership.isMember() && !dissolved` | |

There is **no per-channel membership** in Concord: standing is community-wide, so any status
from §3 applies identically to every channel you can decrypt.

---

## 5. Local / device-side status — "our own perspective"

None of these are the community's opinion; several are deliberately *independent* of it. The
recurring trap they exist to prevent: on a Buzz relay you can be a full member (relay roster,
composer enabled, messages streaming) while your Messages list shows no row for the channel.

| Local status | Stored in | Scope | Effect | Syncs across devices? |
|---|---|---|---|---|
| **On my Messages list** | kind **10009** `SimpleGroupListEvent`, public `["group", id, relay]` tags | per NIP-29/Buzz channel | The "Add to / Remove from Messages" toggle. Inbox row + always-on subscription. Public on purpose — group membership is already public in 39002, and Flotilla/nostrord read only the public tags. | ✅ |
| **Joined Concord community** | kind **13302**, NIP-44 self-encrypted, **carries the secrets** | per **community** (not per channel) | Every channel of the community streams and gets inbox rows. Leaving is a local list edit — best-effort published to *our* outbox, which is what makes leaving a community whose relays are dead work at all. | ✅ |
| **Pending channel invite** | `BuzzChannelInvites` (in-memory, fed by kind **44100** where `actor != me`) | per channel | Card in Notifications + the Messages "New Requests" tab, with Accept / Ignore / Leave. Drops out the moment it stops being a question. | ❌ session |
| **Dismissed channel invite** | `AccountSettings.dismissedChannelInvites` | per channel | Suppresses that card. A **display** choice — the relay roster still lists you. | ❌ device |
| **Hidden Buzz DM** | per-viewer kind **30622** snapshot; toggled with **41012** hide / **41010** re-open | per DM channel | The DM's own Add/Remove-from-Messages. Relay-side, so it is *not* the 10009 list. | ✅ |
| **Starred / pinned channel** | `BuzzChannelStars` → `BuzzChannelStarPreferences` | per channel | Floats + badges it at the top of the workspace view. No Nostr event exists for a personal star. | ❌ device |
| **Joined workspace** | `BuzzWorkspaces` → `BuzzWorkspacePreferences` | per relay | Connect + NIP-42 AUTH + run member-channel discovery on a cold start. Needed because Buzz membership is granted by an HTTP invite claim — there is no join event to key off. | ❌ device |
| **Relay speaks Buzz** | `BuzzRelayDialect`, event-shape detection via `markBuzzIfVerified` | per relay | Unlocks the Buzz kinds, composer behavior and UI. Only a **verified** event may flip it, so a hostile relay can't change what the composer sends. | ❌ re-detected |
| **Held owner attestation** | `BuzzHeldAttestations` → `BuzzAttestationPreferences` | per agent pubkey | Appends the owner-signed `auth` tag to that account's NIP-42 AUTH (never to Concord stream-key AUTHs sharing the template). Re-verified on load, so a tampered on-disk credential is dropped. | ❌ device |
| **Optimistic "Pending"** | `remember{}` in `RelayGroupTopBar` | per channel, per visit | Shows "Pending" instead of Join after a 9021. | ❌ |
| **Protocol enabled** | `AccountSettings.enabledChatFeeds` (`ChatFeedType.NIP29` / `CONCORD` / …) | per protocol | Turns the type off the inbox **and** off the always-on download routes. | ❌ device |
| **Inbox view mode** | `relayGroupViewMode` / `concordViewMode` (`INLINE` / `GROUPED`) | per protocol | One row per channel, vs one row per relay/community with a drill-down. | ❌ device |
| **Unread / last read** | `Account.markAsRead` route keys, e.g. `Concord/<communityId>/<channelId>` | per channel | Unread badge + previews. NIP-RS cross-device read state (kind 30078, `read-state:<slot>`) **is modeled in Quartz but not wired**. | ❌ device |
| **Dialect overlay state** | `BuzzWorkspaceStates` keyed by channel UUID, held *outside* the channel object | per channel | Kind-40003 edit overlays and the newest 40100 canvas. Kept outside the object so the dialect can flip mid-session without swapping an instance screens already captured. | ❌ session |

### 5a. The three "leave"-shaped actions, which are not the same thing

| Action | Mechanism | What actually changes |
|---|---|---|
| **Remove from Messages** | kind 10009 edit (Buzz DM: kind 41012) | Display only — you remain a relay member, still reading. Reversible, and it deliberately does **not** navigate away, so the toggle is visibly undoable. |
| **Ignore invite** | `dismissedChannelInvites` | Display only, device-local. |
| **Leave** | kind **9022** `LeaveRequestEvent` (Concord: drop the 13302 entry) | Real: asks the relay to remove you. Pops the screen. |

---

## 6. How the axes compose (the combinations that read as bugs)

- **Member but invisible.** Buzz grants membership server-side; the Messages list reads kind
  10009. Before `BuzzChannelInvites` existed, a channel could be simultaneously joined,
  streaming, and absent from the inbox. Anything not `t=dm` now waits for consent.
- **`NONE` but can post.** An open (non-`private`) Buzz channel accepts writes from any
  authenticated tenant member, so the composer must stay enabled for `NONE` —
  `requiresMembershipToPost()`/`canPost()` mirror the relay's `check_channel_membership`
  rather than the roster.
- **Every Buzz channel is `closed`.** The relay stamps the flag unconditionally, so `closed`
  is not a write signal there; only `private` is.
- **`BANNED` still decrypts.** In Concord a ban removes *standing*, not *keys*. History stays
  readable until a Refounding rotates the root.
- **Dissolved outranks `OWNER`.** After the tombstone nobody may post, the owner included.
- **Stranded looks like an empty community.** Same entry, same id, but every plane address is
  derived at a dead epoch — so it silently returns nothing rather than erroring.

---

## 7. Is each status reflected in the UI?

Three tiers: **shown** (an explicit label, badge or notice names the state), **implied**
(only the presence/absence of an affordance signals it — correct for some, silent for
others), and **invisible** (nothing renders it at all).

### Buzz / NIP-29

| Status | UI | Tier |
|---|---|---|
| `ADMIN` / `MODERATOR` | `RoleBadge` chip in `RelayGroupTopBar`, primary-container colored; unlocks Edit, Delete, Archive, Invite | **shown** |
| `PENDING` | "Requested" — both as the badge and as the text replacing the Join button | **shown** |
| `MEMBER` | no badge, deliberately: "the lack of a Join button already says it" | implied (fine) |
| `NONE` (gated channel) | Join button; a `closed` channel opens the invite-code sheet instead | implied (fine) |
| `NONE` (open Buzz channel) | nothing — composer simply stays enabled, which is correct | implied (fine) |
| `private` | Lock icon next to the relay url in the top bar | **shown** |
| `archived` | its own "Archived" section in the channel list + Archive/Unarchive menu label + a 40099 system row ("X archived this channel") | **shown** |
| channel type (`stream`/`forum`/`dm`/`workflow`) | drives the workspace list's sections, icons and DM titling | **shown** |
| `closed` / `restricted` / `hidden` / `livekit` | editable toggles in `RelayGroupMetadataScreen`; not surfaced as reader-facing state | implied |
| Agent (NIP-OA) virtual membership | `AgentAttestationScreen` shows held/issued attestations; the channel still reports `NONE` | partial |
| Tenant **banned** (9040) / **timed out** (9042) | The message's own bubble. The relay enforces both at publish time and answers `OK false`; `ChatDeliveryTracker` records that refusal, the bubble turns to an error tick instead of the pending clock, and the delivery dialog quotes the relay's reason verbatim. Still **no state object** folds 9040/9042, so nothing warns you *before* you type — inherent, since the relay only says so on publish. | **shown** (on send) |
| Tenant role (owner/admin/member) | `BuzzCommunityMembership` drops the role by design and there is no community roster view. "Add people" exists (9030); `removeCommunityMember` (9031) has **no caller**; change-role (9032), ban/unban, timeout/untimeout have **no send path at all** | **invisible** |
| **Presence** (online/away/offline) | nothing — 20001 is still the geohash-presence slot in `EventFactory` | **invisible** |
| **Archived identity** (8002 / 13535) | nothing | **invisible** |
| **Typing** (20002) | `BuzzTypingIndicator` row above the composer | **shown** |

### Concord

| Status | UI | Tier |
|---|---|---|
| `OWNER` / `ADMIN` / `BANNED` / custom role name | `MemberBadge` in `ConcordMembersScreen` — banned in `errorContainer` — for **other** members | **shown** |
| My own `OWNER`/`ADMIN` | only via affordances: the create-channel FAB, the Edit icon, the roster's promote/ban items | implied |
| My own `BANNED` | `PostingGateNotice` takes the composer's place and names the ban ("Past messages stay readable, but you can no longer post"). Was invisible: the only explanatory branch tested `dissolved`, so a banned member got an empty space and no reason. | **shown** |
| `dissolved` | The same `PostingGateNotice` replaces the composer, inside an opened channel only — not on the community row, the channel list, or the Messages inbox. (There is also **no write path** to dissolve: an owner cannot do it from Amethyst.) | partial |
| **stranded / excluded epoch** | nothing. `recoverStrandedConcordCommunities()` runs at startup, `Log.w` on failure. A stranded community renders as an ordinary *empty* one. | **invisible** |
| current epoch / `heldRoots` | nothing | **invisible** |
| `private` channel | `Lock` icon in the channel list, shown only to members who hold its key; others get no row (and no inbox row). Reaching one anyway explains itself via `PostingGate.NoKey`. | **shown** |
| `voice` channel | `Mic` icon + preview-line suppression in the list; the opened channel has no voice affordance | partial |
| **kicked** (3309) | nothing — unwired | **invisible** |
| Guestbook join/leave | feeds the members roster; the motion itself isn't shown | implied |
| **Typing** (23311) | `ConcordTypingIndicator` | **shown** |

The Concord channel top bar is also markedly barer than its NIP-29 sibling: **name +
community name only** — no role badge, no lock, no member count, no overflow menu.

### Local / device-side

Essentially all of it is reflected, because each local status *is* a control:

| Status | UI | Tier |
|---|---|---|
| On my Messages list | the menu item's label flips Add ↔ Remove off the live kind-10009 | **shown** |
| Hidden Buzz DM | same flip, off the 30622 snapshot | **shown** |
| Starred channel | Pin/Unpin label + primary-tinted icon | **shown** |
| Pending channel invite | card in Notifications + Messages "New Requests", with Accept / Ignore / Leave | **shown** |
| Dismissed invite | implied — the card is gone | implied |
| Protocol enabled, view mode | switches + selection cards in Messages settings | **shown** |
| Unread / last read | badges + previews per channel | **shown** |
| Joined workspace | the Workspaces tab groups by relay | **shown** |
| Relay speaks Buzz | implied via the Buzz-only affordances (canvas, agent work, forum FAB) | implied |
| Held attestation | listed in `AgentAttestationScreen` | **shown** |

## 8. Gaps found while mapping this

Statuses that exist in the protocol/Quartz layer with no client behavior behind them.
§8.1–8.2 are the two that look like defects rather than unfinished features; §8.7–8.8 are
UI-only (the state is tracked correctly, nothing tells the user):

1. ~~**Concord private channels are listed but never opened.**~~ **Fixed.** The read *and* write
   paths derived every channel — `private: true` included — from the `community_root`, at six
   independent call sites. So a private channel was addressed on a plane every member of the
   community could derive: readable **and postable** by people never granted its key, and
   simultaneously invisible to any conformant client (which addresses it by the delivered key). Now
   one resolver, `ConcordChannelPlanner`, decides which secret addresses a channel — the root for a
   public one, the bundle's per-member `channel_key` at the channel's own epoch for a private one —
   and every consumer (plane registry, subscription planner, session re-fold, five send paths,
   channel list, Messages inbox) reads it. A private channel we hold no key for is **omitted
   entirely**, matching Armada's `channelsView` ("omit rather than tease"); arriving at one anyway
   yields `PostingGate.NoKey`. Note the epoch now travels with the plane: a private channel's wraps
   bind to its channel epoch, so the old code would have rejected them even with the right key.
   **Migration:** "private" channels created by earlier Amethyst builds have their history on the
   root-derived plane, which is no longer read for a private channel. That history is orphaned by
   design — keeping it would mean continuing to read (and legitimise) traffic on a plane the whole
   community can write to.
2. **`RoleScope` is decode-only.** `RoleEntity.scope` distinguishes `{"kind":"server"}` from
   `{"kind":"channel","channel_id":…}`, but `AuthorityResolver.effectivePermissions` unions
   every held role's bits regardless of scope — and nothing else reads it. A channel-scoped
   moderator currently resolves to `ADMIN` community-wide.
3. **Concord Kick (3309) is unwired.** Modeled with the correct authority rule; no caller.
4. **Buzz presence (20001) can't render** — the `EventFactory` slot belongs to
   `GeohashPresenceEvent`, so it needs the same tag-shape disambiguation 39005 got.
5. **NIP-RS cross-device read state (30078 `read-state:<slot>`) is unwired** — unread is
   device-local route keys only.
6. **Buzz per-tenant roles are flattened.** `BuzzCommunityMembership` deliberately drops the
   NIP-43 role, so a tenant *admin* with no per-channel 39001 row reads as channel `MEMBER`.
   Correct as a safety default (it avoids over-granting), but it means the community-admin
   status has no channel-level expression.
7. ~~**A ban is never explained to the person banned.**~~ **Fixed, from both ends.** *Concord:*
   every reason posting is blocked is a `PostingGate` (`commons/.../model/chats/PostingGate.kt`)
   rendered by `PostingGateNotice`, with `canPost()` defined as `postingGate() == Allowed` so the
   two can't drift. *Buzz:* the refusal is the only signal that exists — the relay enforces a ban
   or timeout at publish time and answers `OK false` with no event to fold — so
   `ChatDeliveryTracker` now records rejections alongside acceptances
   (`RelayInsertConfirmationCollector` gained an `onRelayRejected` callback; it previously dropped
   every `OK false` on the floor). The bubble shows an error tick rather than a permanent pending
   clock, and the delivery dialog quotes the reason. `duplicate:` is normalized to acceptance
   (relays disagree on which OK flag carries it) and a later acceptance clears an earlier refusal
   (the auth-required → AUTH → republish round trip). Retry is offered only when the NIP-01 prefix
   says a resend could work — `rate-limited`, `error`, `auth-required` — not for `blocked` /
   `restricted`, where the relay would repeat itself. This is a general fix: every relay refusal
   on a chat message now reaches the sender, with a ban as one case.
8. **Stranding is silent.** Recovery is automatic and log-only, so a member left out of a
   Refounding sees an ordinary empty community rather than "you were left behind on epoch N".
9. **Buzz tenant moderation has no write path.** 9031 remove-member has no caller; 9032
   change-role, 9040/9041 ban/unban and 9042/9043 timeout/untimeout have no builder call at
   all. Only 9030 add-member is reachable (the "Add people" dialog).
10. **Concord dissolution is read-only.** `dissolved` is detected and honored, but no code
    authors a `DISSOLVED` edition — an owner cannot dissolve a community from Amethyst.
