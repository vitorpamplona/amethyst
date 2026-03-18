# Manual Testing Plan: Desktop Media Full Parity (`feat/desktop-media`)

## Context
Branch has 13 commits implementing Phases 0-9 of desktop media: image display, upload, video/audio playback, lightbox, encrypted DM files, profile gallery, server management, and imeta tags. 49 unit tests cover service logic. This plan covers **integration & UI manual testing** that unit tests can't reach.

## Prerequisites
- VLC installed on system (required for VLCJ video/audio)
- Logged into a Nostr account with signing capability
- At least one reachable Blossom server (e.g. `https://blossom.primal.net`)
- Test files ready: JPEG (with EXIF), PNG, GIF, SVG, MP4, MP3, large file (>10MB)

## Run Command
```bash
./gradlew :desktopApp:run
```

---

## Phase 1: Image Display & Caching

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 1.1 | Images load in feed | Open feed with image notes | Blurhash placeholder shows first, then full image fades in | ⬜ TODO |
| 1.2 | User avatars render | Browse feed/profile | All profile pictures display correctly | ⬜ TODO |
| 1.3 | Animated GIF | Open note with GIF URL | GIF animates with all frames | ✅ PASS (was static-only, fixed with AnimatedGifImage composable) |
| 1.4 | Base64 inline images | Open note with base64 data URI | Image decodes and displays | ⬜ TODO |
| 1.5 | Cache persistence | Close app, reopen, revisit same feed | Previously loaded images appear instantly (disk cache) | ⬜ TODO |
| 1.6 | Cache location | Check OS cache dir | macOS: `~/Library/Caches/amethyst/`, Linux: `~/.cache/amethyst/` | ⬜ TODO |
| 1.7 | Broken image URL | Note with 404 image URL | Graceful fallback (no crash, placeholder or blank) | ⬜ TODO |

---

## Phase 2: File Upload (Blossom Protocol) — ⏭️ SKIPPED (come back later)

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 2.1 | Upload JPEG | Compose note → attach JPEG → publish | Upload succeeds, URL in note | ⬜ TODO |
| 2.2 | Upload PNG | Compose note → attach PNG → publish | Upload succeeds, URL in note | ⬜ TODO |
| 2.3 | EXIF stripped | Upload JPEG with GPS EXIF → download result from Blossom URL | No EXIF metadata in downloaded file | ⬜ TODO |
| 2.4 | Auth header | Upload with Nostr signer | Server accepts upload (BUD-11 auth works) | ⬜ TODO |
| 2.5 | Upload progress | Attach large file → upload | Progress indicator updates during upload | ⬜ TODO |
| 2.6 | Upload error | Disconnect network mid-upload | Error state shown, no crash | ⬜ TODO |
| 2.7 | Server selector | Open compose → check server dropdown | Shows configured Blossom servers | ⬜ TODO |

---

## Phase 3: Upload UX (File Picker, Paste, Drag-Drop) — ⏭️ SKIPPED (come back later)

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 3.1 | File picker | Compose → click Attach → select image | Native dialog opens, file appears in attachment row | ⬜ TODO |
| 3.2 | Multi-select | File picker → select 3 images | All 3 appear in attachment row with thumbnails | ⬜ TODO |
| 3.3 | File type filter | File picker dialog | Only media files shown (images, video, audio) | ⬜ TODO |
| 3.4 | Clipboard paste | Copy image → Compose → Cmd+V/Ctrl+V | Pasted image appears in attachment row | ⬜ TODO |
| 3.5 | Drag & drop | Drag image file onto compose dialog | File appears in attachment row; visual drop indicator | ⬜ TODO |
| 3.6 | Remove attachment | Click X on attached file | File removed from row | ⬜ TODO |
| 3.7 | Thumbnail preview | Attach image | Thumbnail visible in attachment row | ⬜ TODO |
| 3.8 | Alt text | Attach image → enter alt text → publish | Alt text included in imeta tag | ⬜ TODO |
| 3.9 | Imeta tags | Publish note with image | Published event has imeta tag (url, m, x, dim, blurhash) | ⬜ TODO |

---

## Phase 4: Video Playback (VLCJ)

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 4.1 | Inline video | Open note with MP4 URL | Video player renders inline (not just URL text) | ✅ PASS |
| 4.2 | Play/pause | Click play button | Video plays; click again pauses | ✅ PASS |
| 4.3 | Seek bar | Drag seek bar | Video jumps to position | ✅ PASS |
| 4.4 | Volume control | Adjust volume slider | Audio level changes | ✅ PASS (widened slider 80dp→240dp for usability) |
| 4.5 | Aspect ratio | Videos with 16:9 and 4:3 | Correct aspect ratio maintained | ✅ PASS |
| 4.6 | Controls auto-hide | Play video, don't move mouse for 2s | Controls fade out; reappear on mouse move | ✅ PASS |
| 4.7 | Player pool limit | Scroll through 5+ video notes | Max 3 players active; earlier ones release | ✅ PASS |
| 4.8 | WebM format | Note with WebM URL | Plays correctly (if VLC supports it) | ✅ PASS |

---

## Phase 5: Lightbox & Gallery Navigation

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 5.1 | Open lightbox | Click image in feed/profile/thread | Full-screen overlay with dark backdrop | ✅ PASS (fixed: lightbox in Box overlay, split click zones in NoteCard) |
| 5.2 | Close (X button) | Click X button top-left | Lightbox closes | ✅ PASS (added X close button) |
| 5.3 | Close (Esc) | Press Escape | Lightbox closes | ✅ PASS |
| 5.4 | Zoom (scroll) | Mouse wheel up/down | Image zooms in/out (up to 10x) | ✅ PASS |
| 5.5 | Pan (drag) | Zoom in → click-drag | Image pans with cursor | ✅ PASS |
| 5.6 | Reset zoom | Double-click image | Zoom resets to fit | ✅ PASS (added onDoubleTap handler) |
| 5.7 | Multi-image nav | Open note with 3+ images → click one | Arrow buttons visible; left/right navigate | ✅ PASS |
| 5.8 | Arrow key nav | Left/Right arrow keys | Navigate between images | ✅ PASS |
| 5.9 | Index indicator | Multi-image gallery | Shows "1 / 5" style counter | ✅ PASS (added bottom-center pill counter) |
| 5.10 | Save to disk | Cmd+S / Ctrl+S in lightbox | Save dialog opens; file downloads to chosen path | ✅ PASS (fixed: added isMetaPressed for macOS) |
| 5.11 | Video in lightbox | Click video thumbnail | Video plays in lightbox with controls | ✅ PASS |

---

## Phase 6: Encrypted Media (NIP-17 DMs) — ⏭️ SKIPPED (DM file attach not implemented yet — MessageInput is text-only)

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 6.1 | DM file attach | Open DM → attach file | Encryption indicator visible | ⬜ BLOCKED — no attach button in DM input |
| 6.2 | Send encrypted | Attach file in DM → send | File uploads encrypted to Blossom | ⬜ BLOCKED |
| 6.3 | Receive encrypted | Receive DM with encrypted file | File downloads and decrypts; displays correctly | ⬜ TODO |
| 6.4 | Wrong key | (If testable) Attempt to view another user's encrypted media | Decryption fails gracefully | ⬜ TODO |

---

## Phase 7: Profile Gallery (NIP-68 / Kind 20)

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 7.1 | Gallery tab visible | Navigate to profile | "Gallery" tab appears | ✅ PASS |
| 7.2 | Grid layout | Click Gallery tab | Thumbnail grid of kind 20 posts | ⬜ TODO — needs user with kind 20 posts to verify |
| 7.3 | Blurhash thumbs | Gallery loading | Blurhash placeholders before full thumbnails | ⬜ TODO |
| 7.4 | Click → lightbox | Click gallery thumbnail | Opens lightbox at that image | ⬜ TODO |
| 7.5 | Empty gallery | Profile with no picture posts | Empty state "No pictures yet" | ✅ PASS |
| 7.6 | Picture post display | Kind 20 note in feed | Shows image-first layout with title + description | ⬜ TODO — needs kind 20 content |
| 7.7 | Create picture post | Compose kind 20 with multiple images | Multi-image post publishes with imeta tags | ⬜ BLOCKED — no kind 20 compose UI |

---

## Phase 8: Blossom Server Management

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 8.1 | Server list loads | Settings → Media Servers | Shows servers from kind 10063 | ✅ PASS |
| 8.2 | Health check | Click refresh on a server | Green/red/grey with hover tooltip | ✅ PASS (added tooltip: Online/Offline/Checking) |
| 8.3 | Check all | Click "Check All" | All servers checked in parallel | ✅ PASS |
| 8.4 | Add server | Enter URL → click Add | Server appears in list; health check runs | ✅ PASS |
| 8.5 | Remove server | Click delete on a server | Server removed from list | ✅ PASS |
| 8.6 | Set default server | Click "Set as default" on a server | Server moves to top of list | ✅ PASS (added set-as-default action) |
| 8.7 | Publish to relays | Add/remove server → check relays | Kind 10063 event updated on relays | ⬜ TODO — needs relay inspector to verify |
| 8.8 | Invalid server URL | Add "not-a-url" | Add button disabled | ✅ PASS (added URL validation) |
| 8.9 | Settings scroll | Scroll settings page | Content scrolls | ✅ PASS (fixed: added verticalScroll) |

---

## Phase 9: Audio Playback

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 9.1 | Inline audio | Note with MP3 URL | Audio player renders inline | ✅ PASS |
| 9.2 | Play/pause | Click play | Audio plays; click again pauses | ✅ PASS |
| 9.3 | Seek | Drag seek bar | Playback jumps to position | ✅ PASS |
| 9.4 | Time display | Play audio file | Shows current/total time | ✅ PASS |
| 9.5 | Multiple formats | Notes with OGG, WAV, FLAC, AAC, OPUS, M4A | All play (where VLC supports) | ⬜ TODO |
| 9.6 | Audio pool | Scroll past 6+ audio notes | Max 5 audio players; earlier ones release | N/A — GlobalMediaPlayer uses single shared player now |
| 9.7 | Initial volume | Play audio without touching volume slider | Audio audible at 100% on first play | 🐛 BUG — VLC starts silent; moving volume slider fixes it. Tried `:start-volume`, `setVolume` in playing callback, delayed retries — none work. Needs investigation into VLCJ audio output init timing on macOS. |

---

## Phase 10: Cross-Cutting / Edge Cases

| # | Test | Steps | Expected | Status |
|---|------|-------|----------|--------|
| 10.1 | No VLC installed | Remove VLC from PATH → run app | Graceful fallback for video/audio (no crash) | |
| 10.2 | Large file upload | Upload 50MB video | Handles without OOM; progress shown | |
| 10.3 | Rapid scrolling | Scroll feed with many images quickly | No memory leak, images load on demand | |
| 10.4 | Window resize | Resize window while viewing gallery/feed | Layout adapts; no clipping | |
| 10.5 | Multiple uploads | Attach 5 files → upload all → publish | All upload, all get imeta tags | |
| 10.6 | App restart | Restart app after uploads/config | Cache, server prefs, all persisted | |

---

## Unanswered Questions
- Is VLCJ arm64 macOS working? (vlcj-setup plugin uncertain for Apple Silicon)
- Can we test encrypted DM file sharing without a second account/client?
- Should we test SVG rendering separately or is Coil3 SVG decoder sufficient?
- How to verify kind 10063 publish without a relay inspector tool?
