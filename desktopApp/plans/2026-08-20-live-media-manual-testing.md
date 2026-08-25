# Manual Testing Sheet — Desktop Live Media (NIP-53) v1

**Plan:** `docs/plans/2026-08-20-feat-desktop-live-media-plan.md`
**Branch / worktree:** `feat/desktop-live-media` · `.claude/worktrees/feat-desktop-live-media`
**Run:** `./gradlew :desktopApp:run`

Acceptance test for the whole v1 feature. Each section is tagged with its current build state so you
know what will actually work today:

- **🟢 LIVE** — built & compiling; test it now.
- **🟡 PARTIAL** — built but with a known gap (called out inline).
- **⚪ PENDING** — not built yet; skip until its commit lands.

**Legend for results:** ✅ pass · ❌ fail (write what happened) · ⏭️ skipped/blocked

---

## Setup (do once)
- [ ] Open **<https://zap.stream>** in a browser — this shows who is live on Nostr right now with real
      kind-30311 streams + kind-1311 chat. Keep it handy as your source of truth.
- [ ] Log into Amethyst Desktop with an account that **follows ≥1 host who streams** (follow a couple of
      the zap.stream front-page hosts if not).
- [ ] Confirm relays are connected (no persistent "offline" banner).
- [ ] (For later zap tests) connect an NWC wallet under **Wallet**.

> **Tip:** live streams come and go. If a section says "nobody is live," check zap.stream — if the front
> page is also empty, wait for a stream to start rather than recording a failure.

---

## 1. Discover → "Live now"  🟢 LIVE

Open the **Discover** destination. Scroll to the **LIVE NOW** section (below the featured pack hero).

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1.1 | Open Discover with ≥1 stream live on the network | A **LIVE NOW** section appears with a red dot header + a search box + a grid of cards | |
| 1.2 | Look at card order | **Live** streams first, then **planned** ("starts in…"), then ended; within live, streams where **more of your follows** participate rank higher, then higher **viewer count** | |
| 1.3 | Inspect a live card | Shows thumbnail image, a red **LIVE** badge, the **title**, the **host** name, and **"N watching"** when the host reports a viewer count | |
| 1.4 | Inspect a planned card | Shows a **"in 2h / in 30m / SCHEDULED"** badge instead of LIVE | |
| 1.5 | Type a host's name in the search box | Grid filters live to matching streams as you type | |
| 1.6 | Type part of a stream **title** | Filters to matching titles | |
| 1.7 | Type a **hashtag** that a live stream uses (e.g. `bitcoin`) | Filters to streams tagged with it | |
| 1.8 | Type gibberish (`zzzzz`) | Shows **"No live streams match "zzzzz.""** (grid empty, section still visible) | |
| 1.9 | Clear the search | Full ranked grid returns | |
| 1.10 | Open Discover when **nothing** is live network-wide | The LIVE NOW section is **absent** (not an empty box) | |
| 1.11 | **Known gap:** click a card | Nothing happens yet — the watch screen isn't wired (see §3). Not a bug today. | |

---

## 2. Per-column "live now" bar  🟢 LIVE

| # | Step | Expected | Result |
|---|------|----------|--------|
| 2.1 | Open a **Following** feed column while a **followed** host is live | A compact pinned bar sits at the **top of that column**, above the feed: red dot + **"<host> is live"** | |
| 2.2 | Have **2+** followed hosts live at once | The bar shows the **most-watched** one, plus a **"+N live ›"** link | |
| 2.3 | Click **"+N live ›"** | A dropdown lists all currently-live streams in scope; each entry is clickable | |
| 2.4 | Open a **Global** feed column | The bar reflects the **global** live set (typically different/larger than Following) | |
| 2.5 | Scroll the feed | The bar stays **pinned** at the top (doesn't scroll away) and the feed scrolls smoothly beneath it | |
| 2.6 | Open a column where **nobody in scope** is live | **No bar** is shown (Following bar hidden when no followed host is live) | |
| 2.7 | Open a **hashtag / list / search / notifications** column | **No bar** (by design — v1 scopes the bar to Following + Global only) | |
| 2.8 | **Known gap:** click the bar / a dropdown entry | No-op today (watch screen pending, §3) | |

---

## 3. Watch screen — player + chat + zap  ⚪ PENDING

*Not built yet. These are the target checks for when the watch commit lands; skip for now.*

### Open / layout
- [ ] Clicking a live card or the bar opens a **full-window** watch screen: video **left**, chat **right**, host/metadata below.
- [ ] Header shows title, host (avatar + name), participant roles, viewer count, and a **LIVE** pill.
- [ ] Closing returns to the prior view and **stops playback**.

### Video (live)
- [ ] The live stream plays (audio + video).
- [ ] **No seek bar** in live mode (a "● LIVE" pill instead).
- [ ] Play/pause, volume, fullscreen work.
- [ ] Kill the source briefly → **"Reconnecting…"** (not a frozen frame) → recovers, or an **offline + Retry** state if it stays down.
- [ ] Opening an already-offline stream → offline placeholder, never an endless spinner.

### Video (VOD / recording)
- [ ] An **ended** stream with a recording plays the recording with a **normal seekable** bar.
- [ ] An ended stream with **no recording** shows "Stream ended — no recording" (non-playable).

### Chat (read)
- [ ] Kind-1311 messages for this stream appear, newest at bottom.
- [ ] Auto-scrolls to newest **only while at the bottom**; scrolling up shows a **"↓ N new"** pill.
- [ ] Hovering chat pauses auto-scroll; leaving resumes.
- [ ] **Muted/blocked** users' messages don't appear; muting mid-stream hides existing ones immediately.
- [ ] A busy stream stays smooth (messages may batch slightly).

### Chat (post)
- [ ] Sending posts a kind-1311 with the correct stream `a` tag (verify it shows on zap.stream).
- [ ] Composer is **disabled with a reason** when logged out / no relays / stream ended (draft preserved).

### Zap
- [ ] **Zap** the stream → amount dialog → sends via NWC; receipt attributed to the 30311.
- [ ] Zap **disabled with reason** when the host has no lightning address or no NWC wallet is connected.
- [ ] *(Per-message chat zap + top-zappers leaderboard are v1.5 — not expected.)*

### Lifecycle / re-entry
- [ ] Host edits title/participants mid-stream → header updates live.
- [ ] Stream ends mid-watch → "ended" banner, composer disabled, "Play recording" offered; window does **not** auto-close.
- [ ] Opening a **second** stream tears down the first cleanly (no double audio, no leaked chat).
- [ ] Opening via a direct **naddr/nevent** link resolves + loads; unknown → clear message, no hang.

---

## 4. Profile "Streams" tab  ⚪ PENDING
- [ ] A streaming host's **profile** has a **Streams** tab listing their live + past streams.
- [ ] A live entry opens the watch screen; a past one with a recording plays VOD.

## 5. Planned streams  🟡 PARTIAL
- [ ] Planned streams show a **"starts in…"** badge in the Discover grid.  🟢 (works now)
- [ ] A planned stream whose start passed long ago and never went live is relabeled/downranked.  ⚪ (pending online/overdue wiring)
- [ ] *(No "Remind me" in v1 — badge only, by design.)*

## 6. Online-probe / dead streams  ⚪ PENDING
- [ ] A `status=live` stream whose `.m3u8` is actually dead is **not** shown as live (sinks/hidden).
      *Today all `status=live` are treated as online — this downgrade is not wired yet.*

---

## 7. Cross-cutting / security & regression  🟡 PARTIAL (mostly pending)
- [ ] ⚪ App only plays `https` stream URLs; `file://`/`smb://`/custom-scheme URLs are refused (incl. the "open in default player" fallback).
- [ ] ⚪ No drive-by HEAD probing of every cached stream URL (probe only what's needed for visible cards).
- [ ] ⚪ Chat messages with bidirectional/control unicode render without reordering surrounding UI.
- [ ] 🟢 Existing feed video (NowPlayingBar) still works; existing feeds/Discover unaffected by the new section.
- [ ] 🟢 `./gradlew :commons:jvmTest --tests "*.LiveActivitySortingTest"` passes.
- [ ] ⚪ Full build green: `./gradlew :desktopApp:compileKotlin :amethyst:compileDebugKotlin` + `spotlessApply` clean.

---

## Platform notes
- **macOS** (AVFoundation): primary target for the video tests.
- **Linux** (GStreamer): must be installed at runtime; live-HLS reconnection differs — spot-check when watch lands.
- **Windows** (Media Foundation): weakest native live HLS — the stall watchdog/reconnect must carry it.

## Known v1 limitations (by design — not bugs)
No broadcasting; no audio rooms / raids / clips; no per-message chat zap or leaderboard (v1.5); no OS
notifications / "Remind me" (badge only); live bar only on Following + Global columns.

---

### Quick "test right now" path (today's build)
1. `./gradlew :desktopApp:run`, log in, ensure ≥1 stream is live (check zap.stream).
2. **Discover → LIVE NOW**: see the grid, confirm ranking (§1.2), try search (§1.5–1.9).
3. **Home → Following** and **Global**: confirm the pinned live bar + "+N live ›" (§2).
4. Everything in §3–§7 marked ⚪/🟡-pending is expected **not** to work yet — that's the next build.
