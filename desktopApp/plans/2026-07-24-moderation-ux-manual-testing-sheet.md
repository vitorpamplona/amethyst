# Manual Testing Sheet — Moderation UX & Reporting (discoverability + feedback)

Covers the follow-on UX fixes: right-click note menu, the **⋮** overflow, and the
new **moderation publish logging**. Companion to the full feature sheet
(`2026-07-23-desktop-moderation-safety-manual-testing-sheet.md`).

**Branch:** `worktree-feat-desktop-moderation-safety` (commit `65579ff` or later)
**Run:** `./gradlew :desktopApp:run`
**Watch the log** (this terminal, or `desktop-run*.log`) — moderation actions now
print a line tagged `[Moderation]`.

Legend: **P** pass · **F** fail (note it) · **B** blocked.

---

## 0. Preconditions

- Logged in with a **writeable** account (local nsec, or a connected bunker).
- **At least one relay connected** — check the relay indicator. This matters:
  a report/mute publishes to *connected relays only*; with 0 connected it is
  logged as **not delivered**.
- Have another user **X**'s notes visible in a feed.

---

## 1. Discoverability — how to reach the actions

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| D1 | Look at a note's action row (reply / ♥ / repost / zap / bookmark / **⋮**) | The last icon is now a **⋮ (three dots)**, not a share arrow | |
| D2 | **Left-click the ⋮** | Menu opens: Copy Text / Copy Note ID / Copy Event Link / Copy Raw JSON / Copy Web Link / Broadcast / **Mute user** / **Report…** | |
| D3 | **Right-click anywhere on a note** | Context menu opens: **Copy text / Mute user / Report…** | |
| D4 | Right-click **your own** note | Only **Copy text** (no Mute/Report on yourself) | |
| D5 | Open **another user's profile** → header shows a **⋮** next to Follow → click it | **Mute/Unmute user** + **Report…** | |
| D6 | On a **read-only (npub)** login, repeat D2/D3 | Only Copy items; no Mute/Report | |

---

## 2. Report (NIP-56) — with verifiable feedback

Do each, then **check the log line**.

| # | Steps | Expected + log signal | Result |
|---|-------|------------------------|:---:|
| R1 | ⋮ (or right-click) → **Report…** | Dialog: reason radios + comment + **Report** / **Block & report** / Cancel | |
| R2 | Pick a reason → **Report** | Log shows `[Moderation] report(<reason>) kind=1984 id=… → N relays` (N ≥ 1) | |
| R3 | Confirm on a relay (optional) | A kind-1984 event by you, tagging the note id + author, exists on relay | |
| R4 | **Report a user** from profile ⋮ → Report… | Log: `[Moderation] report-user(<reason>) kind=1984 id=… → N relays` | |
| R5 | **Block & report** | Two log lines: `report(...)` **and** `mute+ kind=10000 …`; author's notes vanish | |
| R6 | Disconnect all relays, then Report | Log **WARN**: `… → 0 connected relays (not delivered)` — proves the "silent failure" is now visible | |

> There is **no success snackbar yet** — confirmation is via the `[Moderation]`
> log line (and the author disappearing for mutes). A toast/snackbar is a noted
> follow-up; see "Known gaps" below.

---

## 3. Mute / unmute — live effect + log

| # | Steps | Expected + log | Result |
|---|-------|-----------------|:---:|
| M1 | ⋮ / right-click X's note → **Mute user** | X's notes disappear from the feed live; log `[Moderation] mute+ kind=10000 id=… → N relays` | |
| M2 | Settings → Content Filters → **Moderation & Safety** → Muted users | X is listed with a display name + **Unmute** | |
| M3 | Click **Unmute** | X reappears live; log `[Moderation] mute- kind=10000 … → N relays` | |
| M4 | **Hidden words** → type a word → **Add** | Notes with the word collapse; log `mute+ kind=10000` | |
| M5 | Profile ⋮ → **Mute user**, reopen menu | Label now reads **Unmute user** | |

---

## 4. Content warning (NIP-36) — sanity

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| C1 | Sensitive/CW-tagged note in feed | Collapsed "Sensitive content" + reason + **Show** | |
| C2 | Settings → Moderation & Safety → toggle **Always show sensitive content** ON | CW notes render unblurred; persists after app restart | |

---

## 5. Log reference

While testing, moderation lines look like:

```
INFO: [Moderation] mute+ kind=10000 id=3f9a1c02 → 4 relays
INFO: [Moderation] report(spam) kind=1984 id=9b7e4410 → 4 relays
WARNING: [Moderation] report(spam) kind=1984 id=9b7e4410 → 0 connected relays (not delivered)
WARNING: [Moderation] report failed: <signer error>
```

- **`→ N relays` (N≥1)** = published. **`→ 0 connected relays`** = you had no relay
  connected; reconnect and retry. **`report failed:`** = signing/publish threw
  (e.g. bunker rejected/timed out).

---

## Known gaps (not bugs — noted follow-ups)
- **No success snackbar/toast** — feedback is via the log line + the muted author
  disappearing. A user-facing confirmation is a small follow-up (needs a
  SnackbarHostState threaded to the note card / dialog).
- Reports are broadcast to **connected relays** (not a NIP-56 outbox split).

## Sign-off
- [ ] ⋮ overflow + right-click both reach Mute/Report (§1).
- [ ] Report logs a `→ N relays` line with N ≥ 1 (§2 R2/R4).
- [ ] Zero-relay report is logged as not-delivered (§2 R6).
- [ ] Mute hides live + shows in management screen + unmute restores (§3).

Overall: ☐ PASS ☐ FAIL — notes: __________________________________________
