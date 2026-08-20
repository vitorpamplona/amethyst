# Manual Testing Sheet — Desktop Live Media (NIP-53) v1

**Plan:** `docs/plans/2026-08-20-feat-desktop-live-media-plan.md`
**Branch:** `feat/desktop-live-media`
**Run:** `./gradlew :desktopApp:run`

This is the acceptance test for the whole v1 feature. Check items off as the corresponding phase
lands. Sections are ordered by implementation phase so you can start testing the earliest layers
before the watch screen exists.

**Legend:** ✅ pass · ❌ fail (note what happened) · ⏭️ blocked/not-yet-built

> **Good test streams:** open <https://zap.stream> in a browser to see who is currently live on
> Nostr, then find the same hosts in Amethyst. zap.stream streams are real kind-30311 events with
> live HLS `.m3u8` URLs and active kind-1311 chat — ideal for end-to-end testing. Have at least one
> **live**, one **planned/scheduled**, and one **ended-with-recording** stream in view.

---

## Pre-flight
- [ ] App launches, you are logged in with an account that **follows at least one host who streams**
      (follow a couple of zap.stream regulars if not).
- [ ] Relays connected (no persistent offline banner).
- [ ] (Optional, for zap tests) an NWC wallet is connected under Wallet.

---

## Phase 1 — Data layer (no UI yet; verify via logs / debugging)
These have no user-facing surface; they're validated indirectly once Phase 2 UI exists. If you build
with debug logging, confirm:
- [ ] Kind **30311** events arriving from relays are stored (one entry per stream address; a
      re-published 30311 **replaces** the old one, doesn't duplicate).
- [ ] Kind **1311** chat events attach to their stream's channel (grouped by the root `a` tag).
- [ ] Memory doesn't grow unbounded while a busy stream's chat streams in (channel chat is capped
      at ~500 messages).

*(Unit tests already cover the ordering/freshness logic: `./gradlew :commons:jvmTest --tests
"*.LiveActivitySortingTest"` — should be green.)*

---

## Phase 2 — Discovery & the "live now" bar

### Discover → Lives
- [ ] Open **Discover**. A **Lives** section appears when ≥1 stream is known.
- [ ] Live streams show first (LIVE badge), then planned ("starts in…"), then ended.
- [ ] Within LIVE, streams with more of **your follows** participating rank higher; among those,
      higher viewer count ranks higher.
- [ ] A `status=live` stream whose video is actually dead is **not** shown as live (sinks/hidden).
- [ ] Each card shows: thumbnail/image, title, host, LIVE/scheduled badge, viewer count when present.
- [ ] **Search box** filters the Lives grid live by **title**, **host name**, and **hashtag** as you type.
- [ ] Empty states read sensibly: no lives at all; search with no matches.

### Per-column "live now" bar
- [ ] Open a **Following** feed column. When a followed host is live, a compact pinned bar appears at
      the **top of that column**, above the feed.
- [ ] The bar shows **one** host (the one with the most viewers) with a red LIVE indicator + title,
      and a **"+N live ›"** affordance when more than one is live.
- [ ] Tapping **"+N live ›"** reveals/leads to the rest of the currently-live set.
- [ ] Open a **Global** feed column — the bar reflects the **global** live set (different from Following).
- [ ] When nobody in a column's audience is live, the bar is **hidden** (no empty bar).
- [ ] Scrolling the feed does not cause the bar to flicker/recompute visibly; scrolling is smooth.

---

## Phase 3 — Watch screen (player + chat + zap)

### Opening / layout
- [ ] Click a live card (from Discover or the bar). A **full-window watch screen** opens: video on the
      **left**, live chat on the **right**, host/metadata below/around.
- [ ] Header shows: title, host (avatar+name), participant roles (Host/Speaker/…), viewer count, and a
      **LIVE** status pill.
- [ ] Closing the watch screen returns to where you were (deck/single-pane) and **stops playback**.

### Video playback (live)
- [ ] The live stream plays (audio + video).
- [ ] **No seek bar** is shown in live mode (a "● LIVE" pill instead of a scrubber).
- [ ] Play/pause and volume work; fullscreen works.
- [ ] Pull your network briefly / stop the source: the player shows **"Reconnecting…"** (not a frozen
      frame), then recovers when the stream returns, or shows an **offline + Retry** state if it stays down.
- [ ] A stream that is offline when you open it shows an **offline placeholder**, never an endless spinner.

### Video playback (VOD / recording)
- [ ] Open an **ended** stream that has a recording. It plays the **recording** with a **normal seekable**
      seek bar (VOD mode), scrubbing works.
- [ ] An **ended stream with no recording** shows a "Stream ended — no recording" state (non-playable),
      both in the grid and if opened.

### Live chat (read)
- [ ] Chat messages (kind 1311) for **this** stream appear on the right, newest at the bottom.
- [ ] The list auto-scrolls to the newest message **only while you're at the bottom**.
- [ ] Scroll up: auto-scroll pauses and a **"↓ N new messages"** pill appears; clicking it snaps to bottom.
- [ ] Hovering the chat pauses auto-scroll (mouse-first); moving away resumes.
- [ ] Messages from **muted/blocked** users do **not** appear; muting someone mid-stream hides their
      existing messages immediately.
- [ ] A very busy stream stays smooth (no UI jank/freeze); messages may batch slightly (expected).

### Live chat (post)
- [ ] Type a message and send. It appears in the chat (as a kind-1311 with the correct stream `a` tag),
      visible to other clients (verify on zap.stream if possible).
- [ ] Sending with **no relays connected** / **logged out** shows a clear disabled reason, not a silent failure.
- [ ] When the stream **ends** while you're watching: an "ended" banner shows, the composer is **disabled**
      ("stream ended"), chat stays **readable**, and a **"Play recording"** action appears if a recording exists.
      The window does **not** auto-close.

### Zapping the stream
- [ ] A **Zap** action is available for the stream. Zapping opens the amount dialog and sends via NWC.
- [ ] Zap is **disabled with a reason** when the recipient has no lightning address, or when no NWC wallet
      is connected (routes you to connect).
- [ ] Zap receipt is attributed to the **stream** (30311), and the amount reflects in the stream's totals.
      *(Per-message chat zap + a top-zappers leaderboard are **deferred to v1.5** — not expected here.)*

### Mid-watch transitions & re-entry
- [ ] Host edits the title/adds participants mid-stream (30311 re-published): the header updates live.
- [ ] While watching one stream, open a **second** stream from the bar/Discover: the first tears down
      cleanly (video + chat stop) and the second starts — no double audio, no leaked chat.
- [ ] Open a stream via a **direct naddr/nevent link**: it resolves, shows a loading state, then the live/
      ended/offline branch. An unknown/not-found address shows a clear message (never hangs).

---

## Phase 4 — Profile Streams tab & planned streams
- [ ] Open a streaming host's **profile**. A **Streams** tab lists their streams (live + past).
- [ ] A live stream in the tab is marked LIVE and opens the watch screen; a past one with a recording
      plays VOD.
- [ ] A **planned** stream anywhere shows a **"starts in…" badge**. *(A "Remind me" action is **deferred**
      — not expected in v1.)*
- [ ] A planned stream whose start time passed long ago and never went live is relabeled/downranked
      (not stuck showing "starts in -3h").

---

## Cross-cutting / regression
- [ ] **Security:** the app only plays `https` stream URLs; a stream with a `file://`/`smb://`/custom-scheme
      URL is refused (no OS handler is invoked, incl. the "open in default player" fallback).
- [ ] **Privacy:** the app does not probe stream URLs you never chose to interact with beyond what's needed
      to show live/offline for visible cards (no drive-by HEAD storm to every stream in the cache).
- [ ] **Chat safety:** a chat message containing bidirectional/control unicode renders without reordering
      surrounding UI text.
- [ ] Existing feed video (NowPlayingBar) still works; opening a live takes over playback and closing it
      leaves the player in a clean state (no stale live position leaking into later feed/VOD video).
- [ ] No new crashes; `./gradlew :commons:jvmTest :desktopApp:test` green; `./gradlew spotlessApply` clean.

---

## Platform notes
- **macOS** (AVFoundation): primary test target.
- **Linux** (GStreamer): must be installed at runtime; live HLS reconnection behaviour differs — spot-check.
- **Windows** (Media Foundation): native live HLS is weakest; verify the stall watchdog/reconnect carries it.

---

## Known v1 limitations (by design — not bugs)
- No broadcasting / going live.
- No audio rooms / Nests, raids, or clips.
- No per-message chat zap or top-zappers leaderboard (v1.5).
- No OS-level notifications / "Remind me" for planned streams (badge only).
- Live bar only on **Following** and **Global** columns (not hashtag/list/search columns).
