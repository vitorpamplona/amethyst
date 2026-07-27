---
title: Desktop Profile Feature Parity
type: feat
status: active
date: 2026-07-27
origin: docs/brainstorms/2026-07-27-feat-desktop-profile-parity-brainstorm.md
---

# ✨ Desktop Profile Feature Parity

Bring the Desktop (`desktopApp/`) user-profile experience to parity with Amethyst
Android (`amethyst/`), for both viewing and editing.

> **Origin:** grounded in `docs/brainstorms/2026-07-27-feat-desktop-profile-parity-brainstorm.md`.
> Carried-forward decisions: **core parity first / niche deferred**, **single feature
> branch**, **new action builders → `commons`**, Mutual tab + Add-to-list in core.

## Enhancement Summary (deepened 2026-07-27)

Six parallel research/review agents (codebase-grounding, architecture, security, simplicity,
flow-gaps, past-learnings) verified this plan against source. Key changes folded in:

1. **CORRECTION — Block list kind was wrong.** Block is **`PeopleListEvent` kind 30000,
   d-tag `"mute"`** — *not* 30382. Kind 30382 is already **NIP-85 `ContactCardEvent`
   (WoT/GrapeRank)** in this codebase; using it would collide with the WoT feature.
2. **Don't hand-roll Block.** `PeopleListEvent.addUser(earlierVersion, isPrivate=true)` +
   `BlockPeopleListState` + `PeopleListDecryptionCache` already exist and do a correct,
   fail-closed decrypt→merge→encrypt round-trip. The new commons builder is a **thin adapter**;
   **never call `create()` on an already-existing (replaceable) list** — that silently
   un-blocks everyone.
3. **Prerequisite gap.** `DesktopLocalCache` has **no `observeEvents(filter)` accessor**;
   Followers + Zaps tabs need it. Add it first. (Relays/Following/Bookmarks/Mutual use
   accessors that already exist.)
4. **Private zaps** decrypt **only on your own profile**, and Desktop's
   `privateZapsDecryptionCache` is a **null stub** — must wire `PrivateZapCache(signer)`,
   gate on `isWriteable()`, and decrypt lazily (a NIP-46 bunker does one round-trip per zap).
5. **Enforcement must be unified now:** `isUserHidden = mute ∪ block` through one combined
   hidden-set across all new tabs (route Block through `DesktopIAccount` symmetrically with
   moderation-safety's `updateMuteList`). Shared `HiddenUsersState` extraction = follow-up.
6. **New flow rules:** actor×subject matrix (own/other/logged-out), self-action guards
   (no self-block footgun), per-tab loading/empty/partial states, AlertDialog-subscription
   gotcha for the pop-up modals.

## Sub-plans (per-phase, deepened 2026-07-27)

Phases 2 and 3 are unpacked in dedicated grounded sub-plans (Phase 1/4 need no further detail):
- **Phase 2 — Tabs:** `desktopApp/plans/2026-07-27-feat-desktop-profile-tabs-plan.md`
- **Phase 3 — Actions:** `desktopApp/plans/2026-07-27-feat-desktop-profile-actions-plan.md`

### Corrections fed back from per-phase research (supersede the sections below)
1. **Enforcement is ALREADY DONE on the base.** `DesktopHiddenUsersState` (moderation-safety)
   already loads the kind-30000 block list and `LiveHiddenUsers.isUserHidden` already returns
   **mute ∪ block**. The new tab filters just read the existing `hiddenUsersHashCodes` — **no new
   enforcement code, no commons extraction** (retires Open Q #3 & #6, and the "unify enforcement now" task).
2. **Block is inline, not a commons builder.** moderation-safety keeps *mute* inline on
   `DesktopIAccount.updateMuteList`; Block mirrors it (`blockUser/unblockUser/updateBlockList`) using
   quartz `PeopleListEvent.addUser/removeUser`. This overrides the "extract Block to `commons/actions/`"
   line below and resolves the architecture reviewer's two-parallel-patterns smell.
   (`FollowPackActions` may still be a thin commons builder.)
3. **`observeEvents` is a fallback, not a requirement.** Existing Desktop tabs use a cache-scan
   `AdditiveFeedFilter` + `rememberSubscription`; Followers/Zaps follow that. Only add
   `DesktopLocalCache.observeEvents` (needs a new filter index) if the scan proves insufficient.
4. **DM ctor** is `ChatroomKey(setOf(pubkey))`, not `build1on1`.

## Reality Check — brainstorm assumptions vs codebase

| Brainstorm assumption | Codebase reality | Revised decision |
|---|---|---|
| mute/block/report missing on Desktop | mute + report built on **unmerged** `feat/desktop-moderation-safety` (`DesktopIAccount.hideUser/showUser/report`, desktop `ReportNoteDialog`, header overflow menu, `DesktopHiddenUsersState`, feed enforcement, NIP-36 blur). **Not in this worktree** (currently on `main`). | Build on top; do NOT re-implement mute/report. **Resolve the base first** (see Base Strategy). |
| Extract mute/block/report to `commons` | moderation-safety put mute/report **desktop-local** on `DesktopIAccount` | New builders (Block, Add-to-pack) → `commons/actions/` (matches `FollowActions`/`ReportAction`). Route Block's **write** through `DesktopIAccount` symmetric with `updateMuteList`. Unify **enforcement** now; extract shared `HiddenUsersState` as follow-up. |
| Rich-text bio needs extraction (open Q) | `DesktopRichTextViewer` + commons `RichTextParser` already render feed cards | **Reuse as-is** (~5-line wrap). Resolved. |
| Block = NIP-51 kind **30382** | 30382 = NIP-85 `ContactCardEvent` (WoT). Block = `PeopleListEvent` **30000**, d=`mute` | **Corrected throughout.** |

## Base & Branch Strategy

The mute/report code this plan extends lives only on **unmerged** `feat/desktop-moderation-safety`;
this worktree is currently on `origin/main`, so that code is **not present here**. Pick one
**before Phase 3**:

- **Recommended — wait & rebase (default).** Let moderation-safety merge to `main`, then rebase
  profile-parity onto `main`. Avoids stacking under the repo's nostr-proposal flow (three-mains
  alignment gate; a base-API change during review silently rots the child). See `ngit-pr` skill.
- **If you can't wait — stack.** Recreate this worktree off `feat/desktop-moderation-safety`
  (so the code the plan extends is in-tree), set PR `--base feat/desktop-moderation-safety`, and
  name the exact contract depended on: `DesktopIAccount.hideUser/showUser`, the header
  overflow-menu composable, `publishModeration`, the zero-relay guard — so a base change is a
  compile break, not silent drift.

Phases 1–2 (banner, bio, CLINK, tabs) **don't depend on moderation-safety** and can proceed on
`main` immediately. Only Phase 3's Block menu item touches the moderation surface.

- **Single feature branch** (brainstorm), focused commits per phase.
- ⚠️ **Maintainer header overlap.** `upstream/claude/{redesign-profile-header, add-last-seen-profile,
  add-profile-settings-page, add-topbar-profile-screen, add-profile-upload-button}` touch this
  header. Diff before touching it; keep header changes **additive and minimal**.

## Scope

### In scope (core parity)

**Viewing**
- Banner image display (upload works; render it) — mirror `DrawBanner.kt`.
- Rich-text bio: wrap `about` with `DesktopRichTextViewer`.
- Six tabs: **Followers, Following, Zaps received, Relays, Bookmarks, Mutual** (see per-tab table).

**Actions** (mute/report already present via moderation-safety)
- **DM** → open Desktop Messages for this pubkey.
- **Share** (copy `npub` / `nostr:` link).
- **Add to list / follow-pack** (reuse Desktop Follow Packs).
- **Block** — `PeopleListEvent` **kind 30000, d=`mute`** (encrypted), distinct from the mute list;
  + Unblock. (Reuses existing quartz builder — see Technical Approach.)
- **Edit Profile:** add the missing **CLINK offer** field (`noffer1…`).

### Deferred to v2 (niche)
NIP-58 badges · NIP-85 petname/nickname card (kind 30382 — the WoT card) · on-chain BTC / Cashu /
NIP-A3 chips (LN only) · Apps tab · Followed-tags tab · Reports-about-user tab · QR nprofile ·
last-seen (maintainer `add-last-seen-profile` likely covers) · pronunciation play.

### Sequencing recommendation (from simplicity review — confirm as open Q)
Followers + **Zaps** carry the two heavy dependencies (`observeEvents` accessor; private-zap
decryption + bunker cost + bespoke `ProfileZapRow`). Consider a **core PR** = Following, Followers,
Bookmarks, Mutual, Relays + DM/Share/Add-to-list/Block, with **Zaps as a fast-follow**. Keeps the
riskiest path off the critical PR. (Scope decision for the user — see Open Q #5.)

## Actor × Subject Matrix (flow-gap review — highest-value addition)

Every action/tab needs defined behavior across **whose profile** × **viewer auth state**.
Derive header visibility from this (self-guards prevent real footguns, e.g. self-block hiding
your own feed):

| Action / tab | Own profile | Other's profile | Read-only / logged-out |
|---|---|---|---|
| DM | hide | show | hide (or disabled+reason) |
| Block / Unblock | hide (**never allow self-block**) | show | hide |
| Report | hide | show | hide |
| Add to list | hide | show | hide |
| Share | show | show | show (no auth) |
| Edit (CLINK) | show | hide | hide |
| Followers / Following / Bookmarks / Relays | show | show | show |
| Zaps | show (+ private zaps you can decrypt) | show (**public only**) | show (public only) |
| Mutual | hide/empty (me tagging me) | show | hide (no "me") |

Write-path template must guard `pubKeyHex == account.signer.pubKey` (self) and `isWriteable()`
(read-only) **before** any publish.

## Technical Approach

### Key files (verified)

| Purpose | File |
|---|---|
| Desktop profile screen (tabs ~L847–1125, header ~L672–845) | `desktopApp/.../desktop/ui/UserProfileScreen.kt` |
| Desktop profile feed filters (exemplar `DesktopProfileFeedFilter`) | `desktopApp/.../desktop/feeds/DesktopFeedFilters.kt` |
| Desktop write path (`FollowAction.follow` → `broadcastToAll`) | `UserProfileScreen.kt` L1202–1247 |
| Desktop account bridge (mute/report on moderation-safety; `privateZapsDecryptionCache` = null stub; `isHidden` = false today) | `desktopApp/.../desktop/model/DesktopIAccount.kt` |
| Relay publish (fire-and-forget; NIP-42 AUTH partial) | `desktopApp/.../desktop/network/RelayConnectionManager.kt` (`broadcastToAll`) |
| Confirmed publish (reuse for Block) | quartz `accessories/` `publishAndConfirm` / desktop `DmSendTracker.publishAndConfirmDetailed` |
| Rich-text render (reuse for bio) | `desktopApp/.../desktop/ui/note/DesktopRichTextViewer.kt` + commons `richtext/RichTextParser.kt` |
| Edit dialog (add CLINK) | `desktopApp/.../desktop/ui/profile/EditProfileScreen.kt` + commons `profile/EditProfileFields.kt` |
| Follow Packs (add-to-list target) | `desktopApp/.../desktop/followpacks/` |
| Desktop Messages (DM target) | `desktopApp/.../desktop/ui/chats/` (`ChatroomListState.selectRoom(build1on1(pubkey))`) |
| **Block — reuse, don't rebuild** | quartz `nip51Lists/peopleList/PeopleListEvent.kt` (`KIND=30000`, `BLOCK_LIST_D_TAG="mute"`, `addUser(earlierVersion, isPrivate=true)`); Android `blockPeopleList/BlockPeopleListState.kt`; commons `PeopleListDecryptionCache.kt` |
| Zap decryption | quartz `PrivateZapCache(signer)` / `PrivateZapRequestBuilder.decryptZapEvent`; Android `UserProfileZapsViewModel` |
| Commons actions (pattern) | `commons/.../actions/FollowActions.kt`, `commons/.../model/nip56Reports/ReportAction.kt` |

### Prerequisite: add `observeEvents(filter)` to `DesktopLocalCache`
Followers + Zaps subscribe to a `Filter` stream; **this accessor is missing on Desktop**
(Android uses `account.cache.observeEvents<Event>(filter)`). Add it (or an equivalent
compose-scoped subscription) as the **first task in Phase 2**. Following/Bookmarks/Relays/Mutual
do **not** need it.

### Per-tab data logic (verified Android sources; tab filters stay desktop-local over the shared `AdditiveFeedFilter` base)

| Tab | Android class (verified) | Query / accessor | Desktop accessor status | Renders |
|---|---|---|---|---|
| Followers | `UserProfileFollowersUserFeedViewModel` | `observeEvents(Filter(kind 3, p=user))` → unique authors; filter `!isHidden` | ⚠️ needs `observeEvents`; `getOrCreateUser` ✅ | user rows |
| Following | `UserProfileFollowsUserFeedViewModel` | `getOrCreateAddressableNote(ContactListEvent.createAddress(user))` → `verifiedFollowKeySet()` → load users | ✅ all present | user rows |
| Zaps | `UserProfileZapsViewModel` | `observeEvents(Filter(kinds 9735+onchain, p=user))` → `mapRequest`; **decrypt only if `user==self`**; `sumAmountsByUser` | ⚠️ needs `observeEvents` **and** `PrivateZapCache(signer)` (null stub today) | zapper rows + total |
| Relays | `RelayFeedViewModel` | `user.nip65RelayListNote.flow()` (write/read) + `user.dmRelayListNote.flow()` (kind 10050) + `user.relayState().flow()` counters | ✅ all present | relay rows (reuse `RelaySettingsScreen` row) |
| Bookmarks | `UserProfileBookmarksFeedFilter` | `getOrCreateAddressableNote(BookmarkListEvent.createBookmarkAddress(user))` → `publicBookmarks()` (+ legacy `OldBookmarkListEvent` 30001) → resolve notes | ✅ (confirm `checkGetOrCreateNote`) | `FeedNoteCard` |
| Mutual | `UserProfileMutualFeedFilter` | iterate `notes` + `addressableNotes`; author==`userProfile()` AND `event.isTaggedUser(user)`; limit 200 | ✅ all present | `FeedNoteCard` |

Followers/Following/Zaps/Relays need row composables (**reuse** `UserSearchCard`-style user row
and `RelaySettingsScreen` relay row where possible — avoid net-new). Bookmarks/Mutual reuse
`FeedNoteCard`.

### Write-path template (all actions — with the guards the reviews demand)

```kotlin
// 1. actor/subject guards FIRST
require(pubKeyHex != account.signer.pubKey)   // never self-DM/block/report
if (!account.isWriteable()) return             // read-only/watch-only: no-op, UI already hides

// 2. NIP-51 lists (Block): reuse existing decrypt→merge→encrypt; NEVER create() an existing list
val current = blockListState.getBlockList()    // must be LOADED from relays first (see guard)
val event = PeopleListEvent.addUser(earlierVersion = current, pubKeyHex, relayHint, isPrivate = true, signer)
// fail-closed: addUser throws UnauthorizedDecryptionException rather than dropping private entries

// 3. publish. Block = security-relevant → confirm, don't fire-and-forget
if (relayManager.connectedRelays.value.isEmpty()) throw IllegalStateException("No relays")
relayManager.publishAndConfirm(event)   // Block; plain broadcastToAll acceptable for non-critical
account.justConsumeMyOwnEvent(event)    // optimistic local apply, symmetric with updateMuteList
```

- **Stale-list clobber guard:** Block/Add-to-list must be **disabled until the account's own
  current list (30000 / 39089) has loaded** — mutating a stale/absent `earlierVersion` publishes a
  replacement that drops prior entries (silent un-block / lost pack members).
- **Optimistic rollback:** on publish failure, revert the optimistic local apply + snackbar.
- **Modals:** the pack-picker and any block-confirm dialog **must not** use `rememberSubscription`
  (broken inside AlertDialog) — hoist the subscription or use direct `relayManager.subscribe()`.

## System-Wide Impact

- **Unified enforcement (do now):** `isUserHidden` must union **mute ∪ block** through one combined
  hidden-set (mirror Android `HiddenUsersState` composing `muteList`+`blockList` into
  `LiveHiddenUsers`; keys include `hiddenUsersHashCodes: Set<Int>` — feed the filters the same
  representation). Every new tab (Followers/Following/Zaps/Mutual/Bookmarks) must respect it — new
  list surfaces are where enforcement is forgotten.
- **Block publish reliability:** `broadcastToAll` is fire-and-forget and Desktop NIP-42 AUTH is
  partial → a Block can be accepted-but-not-persisted on an AUTH relay. Use confirmed publish for
  Block; snackbar covers zero-relay, confirmation covers not-persisted.
- **Privacy caveat:** an encrypted 30000 list still leaks *that you keep a block list* (kind +
  pubkey + cadence) to every connected relay; members stay NIP-44 encrypted. Don't advertise Block
  as fully private.
- **Bunker cost:** private-zap decryption via a NIP-46 remote signer is one round-trip per zap
  (`PrivateZapCache` LRU 1000). Decrypt lazily (visible rows / on tab open), not eagerly.

## Implementation Phases

### Phase 1 — Header polish (small, additive; no moderation-safety dep)
- Render banner (mirror `DrawBanner.kt`).
- Wrap bio with `DesktopRichTextViewer`.
- Add **CLINK offer** to `EditProfileScreen` + `EditProfileFields` + kind-0 write.
- Diff `upstream/claude/redesign-profile-header` first.

### Phase 2 — Six tabs (no moderation-safety dep)
- **First:** add `observeEvents(filter)` to `DesktopLocalCache` (+ compose-scoped subscription).
- **Wire `PrivateZapCache(signer)`** into `DesktopIAccount` (replace null stub); gate on `isWriteable()`.
- New desktop filters mirroring the verified Android classes above.
- Row composables: reuse user-row + relay-row; new `ProfileZapRow` only if Zaps stays in core.
- Tab headers with counts/totals; per-tab **loading / empty / partial** states (see below).

### Phase 3 — Header actions (Block depends on base strategy)
- **DM:** `onStartDmWith(pubKeyHex)` → `Main.kt` pushes Messages + `selectRoom(build1on1)`; handle
  target-has-no-inbox-relays / read-only gracefully (no crash).
- **Share:** copy `nostr:`/`npub` (reuse copy-npub util).
- **Add to list:** `commons/actions/FollowPackActions.kt` (build/publish kind 39089) + pack-picker
  modal; **zero-packs → offer "Create new pack"** (no dead-end).
- **Block:** thin `commons/actions/BlockActions.kt` adapter over
  `PeopleListEvent.addUser(earlierVersion, isPrivate=true)`; route write through `DesktopIAccount`
  symmetric with `updateMuteList`; add to header overflow menu; enforce via unified hidden-set.

### Phase 4 — Tests & manual sheet
- Unit: each new filter; **Block round-trip preserves prior private entries** (mirror
  `MuteListEventTest.add_eventTagPreservesPriorUserAndWordTags`); **Block decrypt-fail is
  fail-closed** (no silent drop); `isUserHidden = mute ∪ block`; CLINK round-trip; zaps own-profile
  decryption vs other-profile public fallback.
- `spotlessApply` + `:commons:compileKotlinJvm` + `:desktopApp:compileKotlin` + relevant `jvmTest`.
- Manual sheet `desktopApp/plans/2026-07-27-desktop-profile-parity-manual-testing.md`.

## Per-tab UI states (flow-gap review)

Every tab defines **loading / empty / populated**; Followers + Zaps add a fourth **partial**:
- **Followers/Following count is cache-limited** — you only see followers whose kind-3 is cached.
  Either label "N found" (not a false exact total) or use a NIP-45 `count` query. Don't show a
  wrong exact count.
- **10k lists:** `LazyColumn` windowing; no full materialization.
- **Bookmarks/Mutual notes not yet in cache:** loading placeholder, not blank.
- **Zaps:** on a third-party profile, label "public zaps only" (private undecryptable).

## Acceptance Criteria

### Functional
- [ ] Banner renders; bio renders mentions/hashtags/links/emoji.
- [ ] Six tabs populated with correct headers; Followers count is honest (labeled partial or NIP-45).
- [ ] Zaps: own-profile sums private+public; other-profile sums public and is labeled "public only".
- [ ] DM opens the correct 1:1 room; graceful state when target has no inbox relays.
- [ ] Share yields a valid `nostr:`/`npub` link.
- [ ] Add-to-list adds to a chosen pack (39089) and persists; zero-packs offers create-new.
- [ ] Block (kind 30000, d=`mute`) hides the user across feeds/threads/replies/profile; Unblock reverses; distinct from mute.
- [ ] Edit Profile CLINK field round-trips through kind 0.
- [ ] Mute + report (moderation-safety) still work unchanged.

### Actor/auth & states
- [ ] Own profile hides DM/Block/Report/Add-to-list; shows Share+Edit. **Self-block impossible.**
- [ ] Read-only/logged-out: write actions hidden; view tabs + Share work.
- [ ] Each tab has distinct loading vs empty states; large lists scroll windowed.

### Correctness / safety gates
- [ ] `isUserHidden = mute ∪ block`, enforced across all six new tabs (one chokepoint).
- [ ] Block round-trip preserves prior private entries; Block disabled until own list loaded; decrypt-fail is fail-closed.
- [ ] Block publish is confirmed (not fire-and-forget); optimistic UI rolls back on failure.
- [ ] `PrivateZapCache(signer)` wired + `isWriteable()` gate; decryption lazy (no bunker storm).

### Quality
- [ ] New builders in `commons/actions/` as pure builders (Block = thin adapter over existing quartz API — **never `create()` an existing list**).
- [ ] Tab subscriptions lifecycle-scoped; modals don't use `rememberSubscription`.
- [ ] `spotlessApply` clean; commons + desktopApp compile; unit tests green.
- [ ] Header changes reconciled against `upstream/claude/redesign-profile-header`.

## Dependencies & Risks

- **Base:** moderation-safety must merge (rebase) or be stacked; currently not in-tree. Only Phase 3
  depends on it.
- **`observeEvents` accessor** is a hard prerequisite for Followers + Zaps.
- **Private-zap decryption** stubbed null on Desktop; own-profile-only; bunker round-trip cost.
- **NIP-51 replaceable clobber** (stale `earlierVersion`) = data-loss bug → load-latest guard + test.
- **Maintainer header redesign** conflict → additive-only changes.

## Open Questions

1. **Block vs mute UX:** expose separate Block (30000) in addition to mute, or fold into a combined
   "block & mute" like Android's dialog? (Simplicity review argues fold/defer since the *user-facing*
   effect overlaps; security/arch confirm they're genuinely distinct events. User call.)
2. **Follow-pack picker:** modal vs route into `FollowPackDetailScreen`; zero-packs create-new flow.
3. **Reconcile moderation-safety mute → commons:** enforcement unified now; is the shared
   `HiddenUsersState` extraction in-scope or a follow-up?
4. **Block confirm strategy:** `publishAndConfirmDetailed` (like DMs) vs optimistic+retry?
5. **Sequencing:** ship Zaps (+ maybe Relays) as a fast-follow to keep `observeEvents`/decryption
   off the core PR? (Simplicity review recommends yes.)
6. **Own 30000 block-list StateFlow:** does moderation-safety already load it, or is that new wiring here?

## Sources & References

### Origin
- Brainstorm: `docs/brainstorms/2026-07-27-feat-desktop-profile-parity-brainstorm.md`.

### Verified Android tab sources
- `amethyst/.../profile/followers/dal/UserProfileFollowersUserFeedViewModel.kt`
- `amethyst/.../profile/follows/dal/UserProfileFollowsUserFeedViewModel.kt`
- `amethyst/.../profile/zaps/dal/UserProfileZapsViewModel.kt`
- `amethyst/.../profile/relays/RelayFeedViewModel.kt`
- `amethyst/.../profile/bookmarks/dal/UserProfileBookmarksFeedFilter.kt`
- `amethyst/.../profile/mutual/dal/UserProfileMutualFeedFilter.kt`

### Verified shared / quartz
- `quartz/.../nip51Lists/peopleList/PeopleListEvent.kt` (KIND 30000, d=`mute`, `addUser`)
- `amethyst/.../model/nip51Lists/blockPeopleList/BlockPeopleListState.kt`
- `commons/.../model/nip51Lists/peopleList/PeopleListDecryptionCache.kt`
- quartz `PrivateZapCache` / `PrivateZapRequestBuilder.decryptZapEvent`
- `commons/.../actions/FollowActions.kt`, `commons/.../model/nip56Reports/ReportAction.kt`
- `commons/.../richtext/RichTextParser.kt`, `desktopApp/.../ui/note/DesktopRichTextViewer.kt`
- Android `HiddenUsersState` / `LiveHiddenUsers` (mute ∪ block enforcement model)

### Desktop targets / gaps
- `desktopApp/.../ui/UserProfileScreen.kt`, `feeds/DesktopFeedFilters.kt`,
  `model/DesktopIAccount.kt` (privateZapsDecryptionCache null stub; isHidden false),
  `network/RelayConnectionManager.kt` (`broadcastToAll` fire-and-forget), `followpacks/`, `ui/chats/`.
- **Missing:** `DesktopLocalCache.observeEvents(filter)`; confirm `checkGetOrCreateNote`.

### Related branches
- `origin/feat/desktop-profile-editing` (MERGED), `origin/feat/desktop-rich-text-and-profile` (MERGED),
  `origin/feat/desktop-moderation-safety` (OPEN — base), `upstream/claude/{redesign-profile-header,
  add-last-seen-profile, add-profile-settings-page, add-topbar-profile-screen, add-profile-upload-button}`.
