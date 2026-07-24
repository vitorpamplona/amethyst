# Manual Testing Sheet — Desktop Moderation & Safety

**Feature branch:** `worktree-feat-desktop-moderation-safety`
**Covers commits:** `9090dfe8` → `274f8c01` (enforcement, report, CW blur, follow-ups)
**Date executed:** ____________  **Tester:** ____________  **Build:** ____________

Run: `./gradlew :desktopApp:run`

---

## 0. Setup & Prerequisites

You need up to **three accounts** to cover every path. Note their npubs below.

| Role | Requirement | npub / note |
|------|-------------|-------------|
| **A — main (writeable)** | Local `nsec` login. Ideally already has a kind-10000 mute list set from the Amethyst mobile app (to test hydration). | |
| **B — bunker (writeable, NIP-46)** | NIP-46 remote signer login. Used only for the async-decrypt path. | |
| **C — read-only (npub)** | Login with an `npub` only (no signer). | |
| **Target user X** | Any account you can mute/report/block. Should be posting notes visible in your feeds. | |
| **Followed user Y** | A user account **A follows** — used to prove mute wins over follow. | |

Legend for results: **P** = pass, **F** = fail (add note), **B** = blocked/couldn't test, **N/A**.

> Tip: to observe published events (mutes = kind 10000, reports = kind 1984), tail a
> relay you publish to, or use `amy` to fetch them by author/kind.

---

## 1. Enforcement — the core bug fix (feeds + DMs)

Logged in as **A**.

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| E1 | A already has a mobile-set mute on user X → open **Following** and **Global** | X's notes do **not** appear in either feed | |
| E2 | Right-click one of X's notes → **Mute user** | X's notes disappear from the feed **without restarting** the app (allow a moment for publish + flow update) | |
| E3 | Mute followed user **Y** (E2 flow) → open **Following** | Y's notes are hidden even though you follow Y (mute wins over follow) | |
| E4 | Open a **thread** whose root is by someone else but has a reply from a muted user | The muted user's **reply is hidden**; the thread **root is still shown** | |
| E5 | Open the **Notifications** tab; have a muted user react to / reply to your note | The muted user's notification does **not** appear | |
| E6 | Open the **DM / Messages** conversation list while a muted user has messaged you | The muted user's conversation is filtered out of the list | |
| E7 | A's mute list (from mobile) contains a **hidden word** → find a note containing that word | The note is hidden/collapsed | |
| E8 | Open the **Reads** (long-form) tab and **Search** with a muted author present | Muted author's articles/results are hidden | |
| E9 | **Unmute** X (see §4) | X's notes **reappear** across feeds, live | |

---

## 2. Thread + Profile enforcement (follow-up A)

Logged in as **A**.

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| T1 | Mute user X → open a thread containing X's reply | X's reply hidden; root shown; sibling replies still there | |
| T2 | Open **X's profile** → Notes tab | X's own notes are hidden (empty / filtered) | |
| T3 | X's profile → **Replies** tab | X's replies hidden too | |
| T4 | While X's profile is open, **Unmute** X from the header overflow (§5) | Their notes **repopulate** the tabs live | |

---

## 3. Report — NIP-56 (note + user)

Logged in as **A**.

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| R1 | Right-click a note → **Report…** | Dialog opens with reason radios (Spam / Profanity / Impersonation / Nudity / Illegal / Malware) + a comment field | |
| R2 | Pick a reason, add a comment → **Report** | A **kind-1984** event is published (verify on a relay / via `amy`) tagging the note id + author; the note is **not** hidden | |
| R3 | Report a note → **Block & report** | Report published **and** the author is muted (their notes vanish) | |
| R4 | Open X's profile → header **⋮** → **Report…** → pick reason → **Report** | kind-1984 published tagging **the user** (no `e` tag required) | |
| R5 | Profile **⋮** → **Block & report** | Report published + user muted | |

---

## 4. Content warning — NIP-36 blur (follow-up B render)

Logged in as **A**. Ensure "Always show sensitive content" is **OFF** (§6) for C1–C3.

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| C1 | Find a note tagged `content-warning` / NSFW in a feed | Renders **collapsed**: "Sensitive content" + the reason text + a **Show** button; media not visible | |
| C2 | Click **Show** | The note body/media reveals; stays revealed while scrolling away and back (per-note) | |
| C3 | Open a CW-tagged note as a **thread root** | Auto-revealed (you navigated into it — `forceReveal`) | |
| C4 | Turn **ON** "Always show sensitive content" (§6) → revisit a CW note in the feed | Renders **unblurred** immediately, no Show gate | |
| C5 | Turn it **OFF** again | CW notes blur again | |
| C6 | Confirm spam-collapse still works and doesn't conflict on a note that is both spammy and CW | One collapse shown; **Show** reveals the note | |

---

## 5. Profile moderation actions (follow-up B)

Logged in as **A**, viewing **X's** profile (not your own).

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| P1 | Profile header shows a **⋮ (MoreVert)** button next to Follow | Present for other users on a writeable account | |
| P2 | ⋮ → menu | Shows **Mute user** (or **Unmute user** if already muted) + **Report…** | |
| P3 | ⋮ → **Mute user** | X muted; label flips to **Unmute user** on reopen; notes hide | |
| P4 | ⋮ → **Unmute user** | X unmuted; notes return | |
| P5 | Open **your own** profile | **No** ⋮ moderation button (can't mute yourself) | |

---

## 6. Settings — sensitive toggle + management screens (follow-ups B & C)

Open **Settings → Content Filters** as **A**.

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| S1 | Locate the **Moderation & Safety** section (below Hashtag-spam filter) | Section renders with an "Always show sensitive content" switch | |
| S2 | Toggle the switch on → **restart the app** → reopen settings | Switch is **still on** (persisted via prefs) | |
| S3 | **Muted users** list | Lists every muted/blocked user with a resolved display name (or short pubkey) + **Unmute** | |
| S4 | Click **Unmute** on a user | Row disappears; that user's notes reappear in feed live; kind-10000 re-published | |
| S5 | **Hidden words**: type a word → **Add** | Word appears in the list; notes containing it collapse in feeds | |
| S6 | **Hidden words**: **Remove** a word | Word gone; matching notes reappear | |
| S7 | **Muted threads** list (mute a thread first, if none) | Lists muted thread ids (truncated) + **Unmute**; removing restores the thread | |
| S8 | Mute a user via note menu (§1 E2) → reopen settings | The new user shows up in the **Muted users** list immediately | |

---

## 7. Account edge cases

| # | Account | Steps | Expected | Result |
|---|---------|-------|----------|:---:|
| A1 | **C (read-only)** | Right-click a note; open a profile ⋮; open settings Moderation section | **No** Mute/Report items in menus; management lists render **read-only** (no Unmute/Add/Remove); sensitive toggle still works (local pref). No crash. | |
| A2 | **C (read-only)** | A public (unencrypted) mute entry exists on this npub | Public mute portion is still **enforced** in feeds | |
| A3 | **B (bunker)** | Log in fresh; the private mute list must be decrypted via the remote signer | Enforcement engages **once decryption resolves** (may lag a beat); no crash while waiting; approve the decrypt on the signer if prompted | |
| A4 | **A** | Mute someone, then kill the relay / go offline mid-publish | App doesn't crash; local hide still applied; re-syncs when back online | |

---

## 8. Regression

| # | Steps | Expected | Result |
|---|-------|----------|:---:|
| X1 | Browse feeds with **no** mutes set | Everything renders as before; no scroll jank / perf regression | |
| X2 | Existing hashtag-spam collapse | Still works independently of moderation | |
| X3 | Reactions, reposts, zaps, bookmarks on a note | Unchanged (moderation menu items are additive to ShareMenu) | |
| X4 | Open/refresh a thread and a profile repeatedly | No leaks / duplicate VMs (filters recreated on account change only) | |

---

## Automated coverage (already green)

- `./gradlew :desktopApp:test --tests "com.vitorpamplona.amethyst.desktop.filters.DesktopMuteEnforcementTest"`
  — muted author hidden (even if followed), hidden-word drop, clean notes pass (§1 E1/E3/E7).
- `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.moderation.PreferencesSensitiveContentSettingsTest"`
  — sensitive toggle mapping `on→true`, `off→null` (never false), persistence (§6 S2).

## Sign-off

- [ ] All §1 enforcement scenarios pass (the core bug fix).
- [ ] All follow-ups (§2, §5, §6) pass.
- [ ] Edge cases (§7) behave with no crash.
- [ ] No regressions (§8).

Overall: ☐ PASS  ☐ FAIL — notes: ______________________________________________
