---
title: Desktop Moderation & Safety — deferred follow-ups (management screens, sensitive-content toggle, thread/profile enforcement)
type: feat
status: active
date: 2026-07-23
origin: desktopApp/plans/2026-07-23-feat-desktop-moderation-safety-plan.md
module: desktopApp (+ commons/jvmMain)
---

# ✨ Desktop Moderation & Safety — follow-ups

Continuation of the shipped core (branch `worktree-feat-desktop-moderation-safety`,
commits `9090dfe8`→`f4435bfe`). The account-layer write API
(`hideUser/showUser/hideWord/showWord/hideThread/showThread`, `report`) and the
`LocalDesktopIAccount` CompositionLocal already exist — these follow-ups are the
remaining UI + persistence.

## Scope (the 3 deferred items)

1. **Thread + Profile enforcement** — `ThreadScreen`/`UserProfileScreen` build
   their `DesktopThreadFilter`/`DesktopProfileFeedFilter` with the default
   `hidden = { EMPTY }`, so mutes don't apply there. Wire the real lambda.
2. **Sensitive-content toggle + profile moderation actions** — persist an
   "Always show sensitive content" setting and back
   `DesktopIAccount.showSensitiveContentSetting` with it; add Mute/Report to the
   profile screen.
3. **Management screens** — list + remove Blocked users / Hidden words / Muted
   threads, reachable from the Content Filters settings section.

## Key findings (grounded 2026-07-23)

- `ThreadScreen` (`ui/ThreadScreen.kt:98`) + `UserProfileScreen`
  (`ui/UserProfileScreen.kt:120`) take `account: AccountState.LoggedIn?` — **not**
  `DesktopIAccount`. But `LocalDesktopIAccount.current` is now in scope inside
  them, so **no param/caller threading needed** (callers: `DeckColumnContainer.kt`
  :566,:720). Just read the local and pass
  `{ LocalDesktopIAccount.current?.hiddenUsers?.value ?: LiveHiddenUsers.EMPTY }`
  to the filter. (Filters already accept the lambda.)
- Persistence pattern: `commons/jvmMain/.../moderation/PreferencesHashtagSpamSettings.kt`
  — `Preferences.userRoot().node(NODE)` with `MutableStateFlow` fields. Mirror for
  a sensitive-content boolean.
- Content Filters UI: `desktopApp/.../ui/settings/HashtagSpamSettingsSection.kt`
  is the section composable; add the toggle + management entries alongside it.
- Read side for management screens: `DesktopIAccount.hiddenUsersState.flow`
  (`LiveHiddenUsers`) already exposes `hiddenUsers` / `hiddenWords` / `mutedThreads`
  sets. Remove = `account.showUser/showWord/showThread`. Resolve a blocked pubkey
  to a name via `localCache.getUserIfExists(hex)?.toBestDisplayName()`.

## Technical Approach — phases

### Phase A — Thread + Profile enforcement (S)
- In `ThreadScreen` and `UserProfileScreen`, capture
  `val iAccount = LocalDesktopIAccount.current` and pass
  `hidden = { iAccount?.hiddenUsers?.value ?: LiveHiddenUsers.EMPTY }` to
  `DesktopThreadFilter` / both `DesktopProfileFeedFilter` constructions
  (`ThreadScreen.kt:125`, `UserProfileScreen.kt:192,212`).
- Thread root intentionally stays visible (filter already only hides replies).
- Success: muting a reply author hides them in an open thread + on the profile
  Notes/Replies tabs, live.

### Phase B — Sensitive-content toggle + profile moderation (M)
- New `commons/jvmMain/.../moderation/PreferencesSensitiveContentSettings.kt`:
  persisted `alwaysShowSensitive: MutableStateFlow<Boolean>` (default false) under
  a stable prefs node; expose `showSensitiveContent: StateFlow<Boolean?>` mapping
  `true → true`, `false → null` (null = respect warnings, matching
  `Note.isHiddenFor`).
- `DesktopIAccount`: replace the placeholder `showSensitiveContentSetting =
  MutableStateFlow(null)` with this store's flow; add
  `setAlwaysShowSensitive(Boolean)`.
- Content Filters section: a "Show sensitive content" switch (reads/writes via
  `LocalDesktopIAccount.current`).
- `UserProfileScreen`: add **Mute user** + **Report…** actions (reuse
  `ReportNoteDialog`; call `account.hideUser` / `account.report(userHex, …)`),
  shown only for writeable accounts and not for self.
- Success: toggling the switch flips CW blur app-wide and persists across restart;
  profile has working Mute/Report.

### Phase C — Management screens (M)
- New `desktopApp/.../ui/settings/BlockedContentSettings.kt` (or 3 small
  composables): three expandable lists driven by
  `LocalDesktopIAccount.current?.hiddenUsers` (collectAsState):
  - **Blocked/muted users** → rows with resolved display name + "Unmute"
    (`showUser`).
  - **Hidden words** → text rows + "Remove" (`showWord`) + an add-word field
    (`hideWord`).
  - **Muted threads** → note-id rows + "Unmute" (`showThread`).
- Surfaced from the Content Filters section (expander or sub-screen).
- Success: lists reflect the live mute set and removing an entry publishes the
  updated kind-10000 and un-hides immediately.

## System-Wide Impact
- **Interaction graph:** removing a mute → `account.showUser/Word/Thread` →
  `MuteListEvent.remove` signed + `justConsumeMyOwnEvent` + broadcast → mute flow
  re-emits → feeds `invalidateData` → entry disappears from feed AND from the
  management list (same flow). No separate refresh path.
- **State lifecycle:** management-list edits and the sensitive toggle are
  independent stores (NIP-51 event vs local prefs); no shared partial-failure.
- **API parity:** the CW toggle now feeds three readers —
  `Note.isHiddenFor` (feed filters) and `SpamCheckedNoteRender` (blur). Both read
  the same `showSensitiveContent` value; verify one source of truth.
- **Read-only accounts:** management screens are read-only (show lists, disable
  remove/add); the sensitive toggle still works (it's local prefs, not signed).

### Integration test scenarios
1. Mute a reply author → open the thread → reply hidden; root still shown.
2. Mute a user → open their profile → their Notes/Replies tabs empty.
3. Toggle "show sensitive content" on → CW notes render unblurred everywhere;
   restart app → still on.
4. Unmute from the Blocked-users screen → their notes reappear in feed live.
5. Add a hidden word in the management screen → matching notes collapse.
6. Read-only account → management screen lists render, remove/add disabled.

## Acceptance Criteria
- [x] Muting hides in open threads (replies) + on profile tabs, live.
- [x] "Always show sensitive content" toggle persists and flips CW blur app-wide.
- [x] Profile screen has working Mute + Report actions (writeable, non-self) —
      MoreVert overflow.
- [x] Blocked-users / Hidden-words / Muted-threads lists in
      `ModerationSettingsSection`; remove publishes the updated mute list and
      un-hides live; hidden-words has an add field.
- [x] All reachable from the Content Filters settings section.
- [x] Read-only + bunker accounts behave (write actions gated on `isWriteable()`;
      management remove/add hidden for read-only).
- [x] Unit test: sensitive-content mapping (`true→true`, `false→null`, persists)
      in `PreferencesSensitiveContentSettingsTest`.
- [x] `spotlessApply` clean; commons + desktopApp compile; tests green.

**Status: COMPLETE** — commit `49d0518d`. Manual run (`./gradlew :desktopApp:run`)
+ PR remain (nostr-git repo → `ngit-pr` flow).

## Dependencies & Risks
- Low risk — all reuse the shipped write API + `LocalDesktopIAccount`. The one
  new persisted store mirrors an existing pattern. No new third-party deps.
- Watch: two readers of `showSensitiveContent` (feed filter via `LiveHiddenUsers`
  vs blur composable) must agree — thread the same setting into
  `DesktopHiddenUsersState` (already takes a `showSensitiveContent` flow param).

## Sources & References
- Origin core plan: `desktopApp/plans/2026-07-23-feat-desktop-moderation-safety-plan.md`
- `desktopApp/.../ui/settings/HashtagSpamSettingsSection.kt`,
  `commons/jvmMain/.../moderation/PreferencesHashtagSpamSettings.kt`
- `desktopApp/.../model/{DesktopIAccount,DesktopHiddenUsersState,LocalDesktopIAccount}.kt`
- `desktopApp/.../ui/{ThreadScreen,UserProfileScreen}.kt`,
  `desktopApp/.../ui/note/{ShareMenu,ReportNoteDialog}.kt`
