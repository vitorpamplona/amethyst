---
title: Desktop Profile — Replies Tab
type: feat
status: active
date: 2026-06-05
---

# Desktop Profile — Replies Tab

## Enhancement Summary (deepen-plan, 2026-06-05)

Source-verified facts (carried into the final design):

- **`isNewThread()` semantics** (`commons/.../model/Note.kt:775-783`): returns
  true when `(event is RepostEvent || event is GenericRepostEvent || replyTo == null || replyTo.size == 0)`
  AND `event !is ChannelMessageEvent` AND `event !is LiveActivitiesChatMessageEvent`.
  Important edge case: `!isNewThread()` would include channel/live messages,
  so the replies-filter predicate must also constrain by event type.
- **Replies predicate (final):** `author == pubkey && event is TextNoteEvent && !isNewThread()`.
  Using `event is TextNoteEvent` rather than `isFeedNote()` excludes reposts
  AND channel messages in one shot. NIP-22 kind 1111 deferred.
- **Notes predicate (final):** add `note.isNewThread()` to existing
  `author == pubkey && isFeedNote(event)`. Safe because `isFeedNote()`
  restricts to kind 1/6/16 — none of which are channel/live messages, so
  the `isNewThread()` channel-message exclusion clause never fires here.
- **`DesktopProfileFeedFilter` shape** (`DesktopFeedFilters.kt:140-163`):
  `AdditiveFeedFilter<Note>` with `feed()`, `applyFilter(newItems)`,
  `sort(items)`, `limit()` overrides. The replies filter must override all
  four to match the framework contract — covered in Phase 1.
- **`DesktopFeedViewModel` lifecycle** (`UserProfileScreen.kt:153-163`):
  remembered keyed on `pubKeyHex`; disposed via `DisposableEffect`. The
  replies VM follows identical pattern.
- **Tab structure** (`UserProfileScreen.kt:714-735`): `PrimaryTabRow` with
  `selectedTab` state at line 198. Tab body branches at line 738 via
  `when (selectedTab)`. Inserting at index 1 requires shifting Reads (1→2),
  Gallery (2→3), Highlights (3→4) — touched in two places (tabs + body).
- **Relay subscription** (`UserProfileScreen.kt:174-195`): already pulls
  kind 1/6/16 via `FilterBuilders.textNotesFromAuthors`. Covers NIP-10
  replies. No subscription change needed.

## Overview

Add a "Replies" tab to the desktop profile screen alongside the existing
Notes tab, so the reply-context rendering from the prior PR can be tested
on profile feeds. Scope is intentionally narrow: **purely additive**, the
existing Notes tab is unchanged.

## Problem Statement

Reply-context rendering (just shipped) is wired into every screen that
funnels through `FeedNoteCard` — profile included. But the only profile
feed currently is "Notes" which mixes everything, so a user testing the
feature has to scroll to find an organic reply. A dedicated Replies tab
is the straightforward QA affordance.

## Proposed Solution

1. **Extend `DesktopProfileFeedFilter`** (existing) with a `repliesOnly: Boolean = false`
   constructor parameter. When `false` (default): keeps existing predicate
   exactly — Notes tab behavior unchanged. When `true`: predicate becomes
   `author == pubkey && event is TextNoteEvent && !note.isNewThread()`.
2. **Wire a second `DesktopFeedViewModel`** in `UserProfileScreen.kt` with
   `repliesOnly = true`.
3. **Insert "Replies" tab at index 1** between Notes and Reads; shift Reads
   to 2, Gallery to 3, Highlights to 4.
4. **Render** the replies feed using the same `FeedNoteCard` pipeline — reply
   context engages automatically.

No relay-subscription change: existing `textNotesFromAuthors` already pulls
kinds 1/6/16 from the user, which covers all NIP-10 replies. NIP-22 kind
1111 deferred — most replies today are kind 1.

**Out of scope (deliberately):**
- Splitting Notes/Replies in the existing Notes tab — separate concern,
  potential UX regression for users who like the mixed feed, not
  load-bearing for this QA goal.
- Count badge on the Replies tab ("Replies (N)") — premature; Reads/Highlights
  count their statically-loaded events, replies are dynamic via FeedViewModel.
- Behavior changes to global / following / bookmark feeds.

## Survey Matrix

| Component | Status | Location | Action |
|---|---|---|---|
| `Note.isNewThread()` | ✅ Reuse | `commons/.../model/Note.kt:775` | Canonical reply-vs-root check |
| `DesktopProfileFeedFilter` | 📦 Extend | `desktopApp/.../feeds/DesktopFeedFilters.kt:140-163` | Add `repliesOnly: Boolean = false` ctor param + branched predicate |
| `DesktopFeedViewModel` | ✅ Reuse | existing | Instantiate a second VM with `repliesOnly = true` |
| Profile relay subscription | ✅ Reuse | `UserProfileScreen.kt:174-195` | Already pulls kind 1; covers NIP-10 replies |
| `PrimaryTabRow` + tab indices | 📦 Extend | `UserProfileScreen.kt:714-735` | Insert "Replies" tab; shift Reads/Gallery/Highlights indices |
| Tab body render | 📦 Extend | `UserProfileScreen.kt:738+` | Add `when (selectedTab) { 1 -> ... }` branch mirroring index 0 |
| Android equivalent | 📖 Reference | `amethyst/.../profile/conversations/dal/UserProfileConversationsFeedFilter.kt` | Pattern only (`acceptableEvent` with `!isNewThread()`) |

## Phases

### Phase 1 — Filter + Profile screen wiring

Files:
- `desktopApp/.../feeds/DesktopFeedFilters.kt`:
  - Add `repliesOnly: Boolean = false` constructor parameter to
    `DesktopProfileFeedFilter`.
  - Update `feedKey()` to include the mode so the two filter instances
    don't collide cache-wise: `"profile-$pubkey${if (repliesOnly) "-replies" else ""}"`.
  - Branch the predicate inside `isProfileNote`:
    - `repliesOnly = false` (default): existing predicate, unchanged.
    - `repliesOnly = true`: `author == pubkey && event is TextNoteEvent && !note.isNewThread()`.
  - The `event is TextNoteEvent` check (rather than `isFeedNote()`)
    excludes reposts AND `ChannelMessageEvent` / `LiveActivitiesChatMessageEvent`
    (which `!isNewThread()` would otherwise let through).
- `desktopApp/.../ui/UserProfileScreen.kt`:
  - Add a `repliesViewModel = remember(pubKeyHex) { DesktopFeedViewModel(DesktopProfileFeedFilter(pubKeyHex, localCache, repliesOnly = true), localCache) }`
    next to the existing `profileViewModel` (line 154). Add a matching
    `DisposableEffect` to destroy it.
  - Collect its feed state into `repliesFeedState` + derive
    `repliesLoadedNotes` mirroring the existing Notes setup (lines 164-171).
  - Insert a "Replies" tab at index 1 in the `PrimaryTabRow` (lines 714-735).
    Shift Reads → 2, Gallery → 3, Highlights → 4. (Updates needed in tab
    declarations AND in the `when (selectedTab)` body branches.)
  - Add a `1 -> { ... }` body branch that mirrors the Notes branch
    (`Loading` / `Empty` / `FeedError` / `Loaded`) but uses `repliesFeedState`
    and `repliesLoadedNotes`. Empty-state copy: "No replies yet".

Verify (single command — Phases 1 and 2 must build together since they're
cross-file):
- `./gradlew :desktopApp:compileKotlin`
- `./gradlew spotlessApply`

Manual sanity:
- Open a profile with mixed posts. Notes tab = unchanged (still shows
  everything as before). Replies tab = only the user's reply posts, each
  rendered with the embedded parent card + "Replying to @X" label.

### Phase 2 — (none)

Folded into Phase 1 per the review pass — separate phases for a single
cross-file edit + format was ceremonial.

## Acceptance Criteria

- [ ] Profile screen shows a "Replies" tab between Notes and Reads.
- [ ] Notes tab behavior is UNCHANGED — still shows whatever it did before.
- [ ] Replies tab shows ONLY the user's reply posts (kind 1 with parent tags).
- [ ] Each reply in the Replies tab renders with the embedded parent + "Replying to @X" label (validates the prior PR end-to-end on profiles).
- [ ] Tab switching is responsive (no scroll-position weirdness, no flash).
- [ ] Cross-module compiles green; spotless clean.

## Testing

Manual UI:
1. `./gradlew :desktopApp:run` from the worktree.
2. Open the profile of an account that posts a mix of root notes and replies (your own account works).
3. Notes tab → only top-level posts visible.
4. Replies tab → only replies visible, each with parent embed + label.
5. Confirm reposts appear in Notes, not Replies.
6. Confirm switching tabs is smooth.

## Dependencies & Risks

- **NIP-22 (kind 1111) replies skipped in v1.** Most replies are kind 1.
  Adding kind 1111 needs `isFeedNote()` extension or new helper, plus possibly
  extending the relay subscription. Defer.
- **Existing Notes tab will lose replies** — intended behavior change.
  Anyone relying on seeing replies in the Notes feed will find them in the new
  Replies tab. Document in PR body.

## Unanswered Questions

- Manual scan needed during testing: is the second `DesktopFeedViewModel`'s
  cache-scan overhead noticeable on profile open? Existing pattern already
  does one full scan per profile; this doubles it. If it shows up in latency,
  fallback is derive-from-loaded-list at render time.
- NIP-22 kind 1111 inclusion — deferred. Add in a follow-up if QA confirms
  enough kind 1111 traffic on the relays used.
