# Amethyst Desktop — Progress Report (March 2026)

**Grantee:** Róbert Nagy
**Period:** March 2026
**Project:** Amethyst Desktop (Kotlin Multiplatform)
**Repository:** [vitorpamplona/amethyst](https://github.com/vitorpamplona/amethyst)

---

## Summary

March focused on remote signing, desktop-native layout features, media, and feed architecture improvements. By month's end the desktop app handles feeds, DMs, search, media, multi-column deck, remote signing, chess, highlights, and drafts — covering the core daily-use surface area outlined in the grant. The deck layout and advanced search are the first desktop-specific power features. Relay health UI is planned next.

**Prior months (Dec 2025 – Feb 2026):** Established the desktop module, core feed/profile/thread UI, encrypted DMs (NIP-04/NIP-17), live chess over Nostr (NIP-64), broadcast feedback UI, and initial commons extraction. PRs [#1625](https://github.com/vitorpamplona/amethyst/pull/1625)–[#1710](https://github.com/vitorpamplona/amethyst/pull/1710).

---

## Work Completed

### NIP-46 Remote Signing (Bunker Login)

PRs: [#1789](https://github.com/vitorpamplona/amethyst/pull/1789), [#1791](https://github.com/vitorpamplona/amethyst/pull/1791)

Fixed two bugs in the Quartz NIP-46/NIP-44 implementation, then built the bunker login flow for desktop.

**NIP-44 HMAC key mutation fix** — a shared key reference was being mutated by Java's `Mac.destroy()`, silently corrupting all subsequent NIP-44 encryption. Fixed with a defensive copy.

**NIP-46 connect response fix** — `connect()` parsed responses as hex pubkeys but the spec returns `"ack"`. Added proper response type handling.

**Bunker login flow** — dedicated NIP-46 relay client with heartbeat monitoring. A pulsating status icon in the sidebar shows signer connection state with hover tooltip. Supports both `bunker://` and `nostr+connect://` URIs.

### Multi-Column Deck Layout

PR: [#1760](https://github.com/vitorpamplona/amethyst/pull/1760)

Desktop-specific power feature that uses screen space to show multiple feeds side by side. Users can add resizable, reorderable columns for any screen type (feeds, messages, search, profiles, etc.). Toggle between single-pane and deck mode via `Cmd+Shift+D` or the View menu. Column configuration persists across restarts.

### Advanced Search

PRs: [#1802](https://github.com/vitorpamplona/amethyst/pull/1802) (superseded), [#1840](https://github.com/vitorpamplona/amethyst/pull/1840)

Query engine with operator syntax and NIP-50 relay search. Supports operators including `from:`, `kind:`, date ranges, hashtags, exclusions, and boolean `OR`. An advanced filter panel syncs bidirectionally with the text bar. Results are grouped into collapsible sections (users, notes, hashtags). Search state survives screen transitions and history is persisted locally.

The shared search engine lives in `commons/search/` for reuse across platforms.

### Media

PR: [#1873](https://github.com/vitorpamplona/amethyst/pull/1873)

Built the media pipeline from loading to upload to playback. Desktop now covers images (Coil3 with blurhash placeholders), video (VLCJ), audio, file upload (NIP-96 with drag-and-drop), encrypted media in DMs (NIP-17), a lightbox viewer with zoom/pan/keyboard navigation, profile galleries, and media server management.

### Cache-Centric Feed Architecture

PR: [#1905](https://github.com/vitorpamplona/amethyst/pull/1905)

Architectural rewrite to fix feed reliability issues found during daily testing.

**Problem:** Rendering events directly from relay subscriptions caused events to disappear on back-navigation, duplicates in feeds, and a deadlock on launch showing 0 relays/notes/follows.

**Solution:** Events now flow through `DesktopLocalCache` → `DesktopFeedViewModel` → UI, with the cache as single source of truth. Replaced fixed-size eviction with WeakReference-based caching so notes are GC-driven. Extracted shared stores (bookmarks, mutes, badges) to commons. Result: instant back-navigation, proper deduplication, and reliable subscription lifecycle.

### Desktop UX Features

PR: [#1942](https://github.com/vitorpamplona/amethyst/pull/1942)

**Right-click highlight creation** — "Highlight" and "Highlight with Note" in the context menu. Since Compose Desktop has no API to read selected text, the implementation piggybacks on the clipboard.

**Shared stores** — `DesktopHighlightStore` and `DesktopDraftStore` as app-level singletons, fixing cross-deck reactivity and draft persistence.

**Profile tabs** — notes, replies, media, highlights, bookmarks, and zaps on user profiles.

**Editor toolbar** — formatting buttons (bold, italic, heading, link, image, list) for note composition.

### Chess Cross-Client Compatibility

PR: [#1903](https://github.com/vitorpamplona/amethyst/pull/1903)

Fixed interop with other Jester clients. Different clients use `0-0` vs `O-O` for castling — added a SAN normalization layer. Fixed `JesterContent` JSON serialization where missing fields broke game detection. Added a second shared relay for better discovery and fixed game listing dropping spectator-classified games. Covered with 25 interop tests.

### Stability & Bugfixes

PR: [#2027](https://github.com/vitorpamplona/amethyst/pull/2027)

- **Flaky thread test** — reply linking failed when replies arrived before root notes. Fixed with `getOrCreateNote`.
- **Repost rendering** — kind 6/16 reposts now render with overlapping avatars and "Boosted" label. Quoted notes render as embedded cards.
- **Reads feed** — following-mode events now route through cache correctly.

---

## Obstacles & Resolutions

| Challenge | Resolution |
|-----------|------------|
| NIP-44 key corruption after first use | Defensive byte array copy ([#1789](https://github.com/vitorpamplona/amethyst/pull/1789)) |
| No text selection API in Compose Desktop | Clipboard piggyback in custom context menu ([#1942](https://github.com/vitorpamplona/amethyst/pull/1942)) |
| Feed deadlock on launch | Cache-centric architecture rewrite ([#1905](https://github.com/vitorpamplona/amethyst/pull/1905)) |
| Chess castling notation incompatibility | SAN normalization + 25 interop tests ([#1903](https://github.com/vitorpamplona/amethyst/pull/1903)) |
| Search panel ↔ text bar infinite loops | `ChangeSource` guard pattern ([#1840](https://github.com/vitorpamplona/amethyst/pull/1840)) |

---

## What's Next

- Embedded Tor support with fail-closed privacy routing (work started)
- Customizable navigation modes — let users tailor the sidebar and deck to their workflow (reading/writing vs chess vs browsing/DMs)
- Relay management UI with connection health dashboard — directly addresses the grant goal of protocol-level transparency
- Polish and stability toward public release

---

## Personal Progress Assessment & Decision Making

Two decision drivers guided this month's work:

1. **Pareto rule (80/20)** — write and test the essential use-cases first, defer edge cases until feedback reveals which ones matter.
2. **Building what I want to use** — I'm relying on my own sense of what a desktop Nostr client should feel like to make design and implementation choices.

This approach inherently introduces bias. The features I prioritize and the UX I gravitate toward reflect my usage patterns, not necessarily the broader user base. Right now I consider this an effective way to get something usable into people's hands — it keeps momentum high and decisions fast.

As Desktop matures and feedback increases, this will need to shift. Stability and working with a target audience will become more important than personal intuition. I'm playing this by ear since Desktop is still early, though I'm proud of what's working already.
