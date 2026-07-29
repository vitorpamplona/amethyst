---
title: Desktop Profile — Phase 3 (Header Actions)
type: feat
status: drafting
date: 2026-07-27
parent: docs/plans/2026-07-27-feat-desktop-profile-parity-plan.md
---

# Desktop Profile — Phase 3: Header Actions

Sub-plan expanding Phase 3 of the [parent parity plan](../../docs/plans/2026-07-27-feat-desktop-profile-parity-plan.md).
Adds DM, Share, Add-to-list, and Block to the profile header.

> **Depends on `feat/desktop-moderation-safety`** (Block extends its header overflow menu +
> `DesktopIAccount` write path + hidden-set). Resolve the base first — see parent "Base Strategy"
> (recommended: wait for merge + rebase onto `main`). DM/Share/Add-to-list do NOT need it.

## Actor/subject guards (apply to every write action)
From the parent's actor×subject matrix. Before any publish:
- `require(pubKeyHex != account.signer.pubKey)` — no self-DM/block/report.
- `if (!account.isWriteable()) return` — read-only/watch-only; UI already hides the button.
- Buttons hidden (not just disabled) per the matrix when not applicable.

## Action 1 — DM (all REUSE; only button + callback are new)
Open Desktop Messages for this pubkey via the existing deck-column pattern.
- Expose `onStartDm: (pubKey: String) -> Unit` from `UserProfileScreen`; wire in `Main.kt`:
  ```kotlin
  onStartDm = { pubkey -> scope.launch {
      chatListState.fetchMetadataIfNeeded(listOf(pubkey))   // ChatroomListState.kt:158 — handles no-relay
      chatListState.selectRoom(ChatroomKey(setOf(pubkey)))  // ChatroomListState.kt:121  (ctor, NOT build1on1)
      /* focus existing Messages column or addColumn(DeckColumnType.Messages) — reuse onOpenMessages() Main.kt:2145 */
  } }
  ```
- `ChatroomKey(users: Set<HexKey>)` (quartz `nip17Dm/base/ChatroomKey.kt:27`); 1:1 = `setOf(pubkey)`.
- `fetchMetadataIfNeeded` already handles the no-inbox-relay case; guard read-only per the matrix. No crash.

## Action 2 — Share (all REUSE)
- `copyToClipboard(text)` (AWT `Toolkit…systemClipboard`, `ShareMenu.kt:113`; also inline in
  `UserProfileScreen.kt:721`).
- npub: `pubKeyHex.hexToByteArrayOrNull()?.toNpub()` (quartz `nip19Bech32/ByteArrayExt.kt:27`).
- nprofile (with relay hints): `UserTag(pubKey, relayHint).toNProfile()` (`UserTag.kt:39`).
- `UserProfileScreen.kt:699-738` already shows the copy-npub + "Copied" feedback pattern — extend it
  to a Share menu item (npub / nostr: toggle). Optionally reuse the `ShareMenu` composable
  (`ShareMenu.kt:56`) shape. No auth required; own + others.

## Action 3 — Add to follow-pack (kind 39089) — mostly REUSE
- **Picker source:** `FollowPacksState.allPacks: StateFlow<List<FollowListEvent>>`
  (`followpacks/FollowPacksState.kt:84`) — zero-packs returns `emptyList()`.
- **Add member:** `FollowListEvent.add(earlierVersion, person = UserTag(pubKey, relayHint), signer)`
  (`quartz nip51Lists/followList/FollowListEvent.kt:116`; `UserTag` from `muteList/tags/UserTag.kt:35`).
  Publish via `relayManager.broadcastToAll(event)` + `cache.consume(event, relay, wasVerified=false)`
  (pattern in `FollowPackDetailScreen.kt:178-192`).
- **Thin `commons/actions/FollowPackActions.kt`** = pure builder wrapping `FollowListEvent.add`
  (returns the signed event; desktop layer publishes). No hand-rolled tag assembly.
- **Zero-packs → offer "Create new pack"** (reuse `FollowPackEditor`); no dead-end modal.
- **Stale-list guard:** disable until the target pack's current 39089 has loaded (`add` onto a stale
  `earlierVersion` drops members).
- **Modal caveat:** the picker must NOT use `rememberSubscription` (broken in AlertDialog); the
  `allPacks` StateFlow is already hoisted, so read it directly.

## Action 4 — Block (`PeopleListEvent` kind 30000, d=`mute`)
**Much of this already exists on moderation-safety — Block is mostly wiring, mirroring mute.**

> **Revision of parent's "extract to commons" default:** moderation-safety keeps the *mute* write
> **inline** on `DesktopIAccount.updateMuteList` (not a commons builder). To avoid the architecture
> reviewer's "two parallel patterns" smell, Block should be **inline and symmetric with mute** — NOT
> a `commons/actions/BlockActions.kt`. The quartz `PeopleListEvent.addUser` IS the shared builder; a
> commons wrapper adds nothing. (If a commons home is later wanted, extract mute + block *together*.)

**Already present (REUSE, no changes):**
- `PeopleListEvent` (quartz `nip51Lists/peopleList/PeopleListEvent.kt`): `KIND=30000`,
  `BLOCK_LIST_D_TAG="mute"`, `createBlockAddress(pubKey)`;
  `addUser(earlierVersion, pubKeyHex, relayHint, isPrivate, signer)`,
  `removeUser(earlierVersion, pubKeyHex, isUserPrivate, signer)`, `remove/removeAll`.
- `PeopleListDecryptionCache(signer)` (commons) — `userIdSet(event)` merges public+private,
  **fail-closed** (`privateTags` → null / `UnauthorizedDecryptionException` on read-only).
- `DesktopHiddenUsersState` **already loads the own kind-30000 block list**
  (`blockListNote = cache.getOrCreateAddressableNote(PeopleListEvent.createBlockAddress(signer.pubKey))`).
- `publishAndConfirmDetailed(event, relays, timeout): Map<relay,Boolean>` (quartz
  `NostrClientPublishExt.kt`); `DmSendTracker.sendBatch` shows the confirmed-publish + status pattern.

**WIRE (new, small):**
- `DesktopHiddenUsersState.currentBlockList(): PeopleListEvent?` — mirror the existing
  `currentMuteList()` (`blockListNote.event as? PeopleListEvent`).
- `DesktopIAccount.blockUser(pubkeyHex)` / `unblockUser(pubkeyHex)` + private
  `updateBlockList(tag, isPrivate, add)` — **mirror `updateMuteList` exactly**: load
  `currentBlockList()`, `PeopleListEvent.addUser/removeUser` (or `create` **only if null**),
  `localCache.justConsumeMyOwnEvent(event)` (optimistic), then publish.
- Publish Block via **`publishAndConfirmDetailed`** (Block is security-relevant; Desktop NIP-42 AUTH
  is partial so fire-and-forget `broadcastToAll` — what `publishModeration` uses for mute — risks
  accepted-but-not-persisted). Snackbar on result; rollback optimistic apply on total failure.
- **Never `create()` an existing list** (replaceable → silently un-blocks everyone); **disable Block
  until `currentBlockList()` has loaded** (stale-`earlierVersion` clobber guard).
- Header menu: add "Block user"/"Unblock user" to the moderation overflow dropdown built at
  `UserProfileScreen.kt:~350-390` (shown when `iAccount.isWriteable() && pubKeyHex != iAccount.pubKey`
  — the self/read-only guards already live there).

## Unified enforcement — ALREADY DONE on the base branch ✅
moderation-safety already merges mute ∪ block: `DesktopHiddenUsersState.assemble()` folds
`blockCache.userIdSet(blockEvent)` into `LiveHiddenUsers`, and
`LiveHiddenUsers.isUserHidden(hex) = hiddenUsers.contains(hex) || spammers.contains(hex)` where
`hiddenUsers = mute ∪ block`. The six new Phase-2 tab filters simply read the existing
`iAccount.hiddenUsers.value.hiddenUsersHashCodes` / `.hiddenUsers` — **no new enforcement code, no
`commons` extraction needed.** (This retires parent Open Q #3 and the "do it now" enforcement task.)

## Acceptance criteria
- [ ] DM opens the correct 1:1 room (`ChatroomKey(setOf(pubkey))` + `selectRoom`); graceful when target has no inbox relays / read-only.
- [ ] Share yields a valid `npub` / `nostr:` link via `toNpub()`/`toNProfile()` + `copyToClipboard`.
- [ ] Add-to-list adds to a chosen pack (39089, `FollowListEvent.add`) and persists; zero-packs offers create-new; disabled until pack loaded.
- [ ] Block (30000, d=`mute`) hides user across feeds/threads/replies/profile (via existing `LiveHiddenUsers`); Unblock reverses.
- [ ] Block round-trip preserves prior private entries (test mirrors `MuteListEventTest.add_eventTagPreservesPriorUserAndWordTags`); disabled until `currentBlockList()` loaded; decrypt-fail is fail-closed.
- [ ] Block publish confirmed via `publishAndConfirmDetailed` (not fire-and-forget); optimistic UI rolls back on total failure.
- [ ] Self-actions impossible; read-only hides write actions (existing header guard covers this).
- [ ] Block write is inline on `DesktopIAccount`, symmetric with `updateMuteList` (not a commons builder); `FollowPackActions` may be a thin commons builder; modals don't use `rememberSubscription`.
- [ ] spotless clean; commons + desktopApp compile; unit tests green.

## Open questions
1. **Block write location:** inline on `DesktopIAccount` symmetric with mute (recommended, overrides parent's "extract to commons") — confirm, or still want a `commons/actions` home (then extract mute+block together)?
2. Fold Block into a combined "block & mute" (Android-style dialog) or keep standalone? (parent Open Q #1) — note enforcement effect is identical since `LiveHiddenUsers` already unions both.
3. Add-member to a 39089 pack: `FollowListEvent.add` inline in `FollowPackActions` builder — any existing desktop add-member path to prefer? (agent found none.)

### Resolved by research
- ✅ moderation-safety already loads the own kind-30000 list and unions mute ∪ block in `LiveHiddenUsers` — **enforcement needs no new code** (retires parent Open Q #3 / #6).
- ✅ Confirmed publish = `publishAndConfirmDetailed` (reuse `DmSendTracker` pattern).

## Sources (verified)
- Parent plan. Branch `origin/feat/desktop-moderation-safety`: `DesktopIAccount.kt` (`updateMuteList`,
  `hideUser/showUser`, `publishModeration`, header menu ~L350-390), `DesktopHiddenUsersState.kt`
  (`currentMuteList`, loads block via `createBlockAddress`, `assemble()`).
- quartz `nip51Lists/peopleList/PeopleListEvent.kt` (KIND 30000, d=`mute`, `addUser/removeUser`),
  commons `PeopleListDecryptionCache.kt`, commons `IAccount.kt` (`LiveHiddenUsers.isUserHidden`),
  quartz `NostrClientPublishExt.kt` (`publishAndConfirm`/`Detailed`), `DmSendTracker.kt`.
- Packs/DM/share: `followpacks/FollowPacksState.kt` (`allPacks`), quartz `FollowListEvent.kt`
  (`add`), `ChatroomKey.kt`, `ui/chats/ChatroomListState.kt` (`selectRoom`, `fetchMetadataIfNeeded`),
  `ShareMenu.kt` (`copyToClipboard`), `nip19Bech32/ByteArrayExt.kt` (`toNpub`/`toNProfile`).
