---
title: Desktop Profile Parity — Manual Testing Sheet
type: manual-testing
date: 2026-07-27
branch: feat/desktop-profile-parity
base: upstream/main (already contains feat/desktop-moderation-safety)
plan: desktopApp/plans/2026-07-27-feat-desktop-profile-parity-plan.md
---

# Desktop Profile Parity — Manual Testing

Run the desktop app: `./gradlew :desktopApp:run`. Log in with a real account that
follows a few people and has some zaps/bookmarks, and have a **second** pubkey handy
to view as "someone else's profile".

Legend: ☐ = to test. Note the OS + login type (local key / NIP-46 bunker / read-only npub).

## Phase 1 — Header polish

☐ **Banner** — open a profile whose kind-0 has a `banner` URL → banner image renders
  above the avatar (150px-ish, cropped, rounded). Profile with no banner → no gap/placeholder.
☐ **Rich-text bio** — a bio containing a `nostr:npub…`/`nostr:nprofile…` mention, a
  `#hashtag`, and an `https://` link renders them as styled/clickable (not raw text).
  Clicking a mention navigates to that profile; a link opens the browser.
☐ **Bio plain text** — a bio with just text still renders correctly.
☐ **CLINK offer (edit)** — own profile → Edit → the form shows a "CLINK offer (noffer…)"
  field. Enter a value, Save. Re-open Edit → value persists. Clear it, Save → removed.
  (Verify the kind-0 event carries/omits the `clink` tag accordingly.)

## Phase 2 — Tabs

The tab bar now scrolls horizontally (10 tabs). Scroll right to reach the new ones.

☐ **Followers** — tab shows a count; lists users (avatar + name) who follow this profile.
  Clicking a row opens that user's profile. Empty/not-yet-loaded shows "No followers found yet".
  ⚠️ Count is relay/cache-derived and may be partial for very-followed accounts — that's expected.
☐ **Following** — count + user list from the profile's contact list; row click navigates.
☐ **Relays** — lists the profile's kind-10002 relays with a read / write / read-write label.
  Profile without a relay list → "No relay list published".
☐ **Bookmarks** — the profile's public bookmarks (kind 10003) render as note cards once
  fetched. Empty → "No public bookmarks". (Private bookmarks are never shown.)
☐ **Mutual** — on **another** user's profile, shows notes **you** authored that tag them.
  On a fresh account → "You haven't posted about this user". Logged out → "Log in to see…".
☐ **Zaps** — tab header shows total sats; rows list zappers (avatar + name + sats), sorted
  by amount. On **your own** profile, private zaps resolve to the real sender; on **someone
  else's** profile private zaps fall back to the anon/zap-request pubkey (expected).
☐ **Hidden users** — mute or block someone, then check they do NOT appear in these tabs /
  their notes are hidden in Bookmarks/Mutual (shared mute∪block enforcement).
☐ **Existing tabs unaffected** — Notes, Replies, Reads, Gallery, Highlights still work.

## Phase 3 — Header actions

The "⋮" overflow menu appears only when logged in, writeable, and viewing **someone else**.

☐ **Message** — ⋮ → Message → the Messages column opens/focuses and the 1:1 room with
  this user is selected. Test in both layouts (wide multi-column deck AND single-pane/compact).
☐ **Add to list** — ⋮ → "Add to list…" → a submenu lists your follow packs. Pick one →
  snackbar "Added to list"; the user is appended to that pack (kind 39089 rebroadcast).
  With **no** packs → the submenu shows a disabled "No lists yet".
☐ **Share** — ⋮ → "Share (copy link)" → clipboard now contains `nostr:npub…`; snackbar
  "Profile link copied". Paste to confirm.
☐ **Mute / Report still work** — (from the moderation-safety base) Mute/Unmute + Report…
  behave as before.

## Actor / auth edge cases

☐ **Own profile** — the ⋮ menu (Message/Add-to-list/Share/Mute/Report) and the Follow
  button are **absent**; only Edit shows. (No way to self-DM/self-block.)
☐ **Read-only account** (watch-only npub) — write actions hidden; view tabs + navigation work.
☐ **Logged out** — profile viewing + tabs work; no write actions; Mutual prompts to log in.

## Regression / stability

☐ Rapidly switch between profiles → no crash, tab subscriptions don't leak (watch memory).
☐ Switch selected tab across all 11 tabs on a busy profile → smooth, no jank/ANR.
☐ Relay disconnect (kill wifi) → "Connecting to relays…" shown; reconnect repopulates.

## Notes / known limitations (v1)

- Followers/Following counts are cache/relay-derived, not a NIP-45 exact count.
- Zaps: private-zap decryption only on your own profile; NIP-46 bunker decrypts lazily per row.
- Share/DM/Add-to-list live in the writeable-other overflow menu (own-profile share can use
  the npub copy button in the profile card).
- Bookmarks resolves public event-bookmarks; address-bookmarks (naddr) are not yet rendered.

## Result

Record: OS, login type, and pass/fail per box. File bugs with the tab/action + repro.
