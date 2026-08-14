# Manual Testing — Desktop Settings Searchable Accordion

Feature branch: `feat/desktop-settings-search-accordion`
Run: `./gradlew :desktopApp:run` → open **Settings** (sidebar, File → Settings, or Cmd/Ctrl+,).

Legend: ☐ untested · ✅ pass · ❌ fail (note issue)

## Accordion basics
- ✅ Settings opens as a list of **collapsed** cards (icon + bold title + subtitle + chevron), matching the mockups.
- ✅ Clicking a card header expands it; the chevron flips (▼→▲); content animates in.
- ✅ Multiple cards can be open at once.
- ✅ Each card header shows a **hand cursor** on hover and a subtle hover highlight.
- ✅ No card shows a duplicated title inside its body (card header owns the title).
- ✅ Leaving Settings and returning **resets** all cards to collapsed.

## Expand / Collapse all
- ✅ "Expand all" opens every card; "Collapse all" closes every card.

## Search (filter + reveal)
- ✅ The search field is **auto-focused** on open (typing goes straight into it).
- ✅ Typing filters cards case-insensitively by title (e.g. `wallet` → Wallet Connect).
- ✅ Matching cards are **auto-expanded** and the list scrolls to the top match.
- ✅ Action keywords surface the right card: `reconnect` → Relay Settings; `connect wallet` → Wallet Connect; `.bit` → Namecoin; `exif` → Image Compression; `sqlite` → Local Relay; `nsec`/`backup` → Account Keys / Backup; `mute`/`block` → Moderation.
- ✅ A no-match query (e.g. `zzzzz`) shows the "No settings match …" placeholder and no cards.
- ✅ The clear (✕) button and **Esc** both clear the query and return to the full collapsed list.
- ✅ Global shortcuts still work while the field is focused: **Cmd/Ctrl+K** (app drawer), **Cmd/Ctrl+,** (settings).

## Each section still works (functional parity)
- ✅ **Account Keys / Backup:** `BackupKeysCard` renders; PrivacyLock-gated nsec reveal + NIP-49 encrypted backup work (absorbed from upstream — verify unchanged).
- ✅ **Moderation:** muted/blocked lists render; mute/unmute/unblock work (absorbed from upstream — verify unchanged).
- ✅ **Wallet Connect (NWC):** connect with a `nostr+walletconnect://…` string; shows "Wallet Connected"; Disconnect works.
- ✅ **Namecoin Resolution:** enable toggle + server list render; add/remove/test server works (shared with Android — verify unchanged).
- ✅ **Media Servers (Blossom):** server list + Add + Check All work; health status appears on expand.
- ✅ **Image Compression:** quality presets + EXIF toggle work.
- ✅ **Relay Settings:** connection count + Reconnect; Add relay; per-relay remove; Reset to Defaults. Relay list scrolls with the page (no nested-scroll crash).
- ✅ **Local Relay:** stats/storage/export sub-sections render and function.
- ✅ **Tor:** status indicator + Advanced… dialog + mode selector work.
- ✅ **Privacy Lock:** lock/inactivity/redaction cards work.
- ✅ **Content Filters:** hashtag-spam switch + threshold slider work.
- ✅ **Developer Settings:** present only in debug builds.
- ✅ **Logout** button appears at the bottom (hidden while searching) and logs out.

## Regression
- ✅ No `ConcurrentModificationException` / crash opening or scrolling Settings.
- ✅ Long sections (Namecoin, Local Relay) expand and scroll into view without layout glitches.

## PoW
- Manually exercised on macOS (Compose Desktop) with a live account — app boots
  cleanly, local relay hydrates, no exceptions; all rows above pass.
- Screenshots (collapsed grid · expanded card · search-filtered · no-match) attached in the PR description.
