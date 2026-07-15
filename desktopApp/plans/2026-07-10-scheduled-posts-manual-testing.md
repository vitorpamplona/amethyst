# Desktop Note Scheduling & Draft Sync — Manual Test Sheet

Covers the whole feature (composer scheduling, in-app publishing, the
Drafts & Scheduled screen, app-closed OS firing, and NIP-37 draft sync) across
**macOS / Windows / Linux**. The maintainers test **macOS**; the Windows and
Linux sections are for community testers.

Most of the feature is testable from a normal `./gradlew :desktopApp:run` — but
**app-closed firing (Section D) requires a packaged build**, because the OS job
is only registered when the app is launched from a jpackage bundle (it reads the
`jpackage.app-path` system property; in a dev run it is absent and OS
registration is a deliberate no-op).

---

## How to record results

Copy this block into the PR (or a comment) and fill it in. One row per platform.

| Platform | Build type | Tester | Date | A | B | C | D | E | F | G | Notes |
|----------|-----------|--------|------|---|---|---|---|---|---|---|-------|
| macOS 14 (arm64) | app-image | | | | | | | | | | |
| macOS 13 (x64) | dmg | | | | | | | | | | |
| Windows 11 | msi | | | | | | | | | | |
| Ubuntu 24.04 (systemd) | deb | | | | | | | | | | |
| Fedora 40 (systemd) | rpm | | | | | | | | | | |

Mark each section **✅ pass / ❌ fail / ⚠️ partial / — skipped**. Put failure
detail + logs in Notes.

---

## Build

**Fastest testable bundle (recommended for local testing):**

```bash
./gradlew :desktopApp:createDistributable
```

Output (native app-image, embeds a JRE, sets `jpackage.app-path`):

- **macOS:** `desktopApp/build/compose/binaries/main/app/Amethyst.app`
  → run `open desktopApp/build/compose/binaries/main/app/Amethyst.app`
- **Windows:** `desktopApp\build\compose\binaries\main\app\Amethyst\Amethyst.exe`
- **Linux:** `desktopApp/build/compose/binaries/main/app/Amethyst/bin/Amethyst`

**Full installers** (what end users get — use for a final pass):
`./gradlew :desktopApp:packageDmg` · `:packageMsi` · `:packageDeb` · `:packageRpm`.

**Dev run** (Sections A–C, E–F only; **D will not register a job by design**):
`./gradlew :desktopApp:run` — look for the log line
`scheduled-post OS job not registered: no packaged app binary in dev mode`.

**Preconditions for all sections:** logged in with an account that has working
**write relays** (NIP-65 outbox, or at least connected relays). For sync tests
(F) you need the account's signer available (local nsec, or bunker online).

Data locations used by the feature:
`~/.amethyst/scheduled/scheduled_posts.json` (store),
`~/.amethyst/scheduled/scheduled.lock` (single-writer lock),
`~/.amethyst/scheduled/scheduler.log` (headless run log).

---

## Section A — Scheduling from the composer

- [ ] **A1** Open the composer (New Note). A **clock/schedule icon** is present in
      the action row.
- [ ] **A2** Click it → a **date/time picker** opens in a dialog (not a broken
      modal). Preset chips show: *in 1 hour*, *tomorrow 9am*, *next Monday 9am*.
- [ ] **A3** Pick a preset → the composer shows the chosen time; the Publish
      action becomes **Schedule**.
- [ ] **A4** Pick a custom date **then** time → confirm. Time is **rounded up to
      the next 15-minute** boundary (expected — matches the ~5-min tick coarseness).
- [ ] **A5** Type note text, hit **Schedule** → dialog closes; a row appears in
      **Drafts & Scheduled → Scheduled** with status **PENDING** and the chosen time.
- [ ] **A6** Verify the note did **not** post immediately (check your feed / a relay).
- [ ] **A7** (Picture posts) With an image attached, the schedule button is hidden
      (pictures are not schedulable in v1) — expected, note if otherwise.
- [ ] **A8 — bunker offline block** (only if you use a NIP-46 bunker signer):
      take the bunker offline, try to Schedule → it is **refused with a message**
      ("Signer must be online to schedule…") and **no** row is stored. (Local-nsec
      users can skip A8.)

## Section B — In-app publishing (app open + catch-up)

- [ ] **B1** Schedule a note for **~2 min** out. Keep the app **open**. Within one
      45-second tick after the time passes, the row flips **PENDING → SENT** and the
      note appears on a relay / in your feed.
- [ ] **B2 — created_at** The published note's timestamp is the **scheduled** time
      (it sorts into the timeline at the intended moment), not the compose time.
- [ ] **B3 — launch catch-up** Schedule a note ~2 min out, then **quit the app
      before** the time. Reopen the app **after** the time → it publishes on launch
      (overdue catch-up).
- [ ] **B4 — far-future guard** Schedule a note for e.g. next week. When it
      eventually fires (or via Publish-now), if a relay rejects a too-far-future
      `created_at`, the row shows **FAILED** with a clear error (not a silent drop).

## Section C — Drafts & Scheduled management screen

- [ ] **C1** Sidebar → **Drafts & Scheduled** opens a tabbed screen
      (**Drafts / Articles / Scheduled**).
- [ ] **C2** Scheduled tab lists your account's rows: content preview, absolute +
      relative time ("… (in 3h)"), and a **status chip** (PENDING/PUBLISHING/SENT/
      FAILED/CANCELLED).
- [ ] **C3 — Cancel** On a PENDING row, overflow menu → **Cancel** → status becomes
      CANCELLED and it stops firing.
- [ ] **C4 — Publish now** On a PENDING/FAILED row → **Publish now** → it publishes
      within a tick (or immediately) and flips to SENT.
- [ ] **C5 — live updates** Actions update the list **without** re-navigating
      (reactive off the store).
- [ ] **C6 — Edit** *(known limitation)* Edit currently **cancels** the row; there
      is no composer-prefill reopen yet. Confirm it cancels; log if it does more.
- [ ] **C7** Empty state ("No scheduled posts") shows when the list is empty.

## Section D — App-closed firing (OS scheduler) — **packaged build required**

> If you ran a dev build, D is expected to be skipped (no OS job registered).

**Common flow:**

1. **D1 — register on schedule:** with a packaged app, schedule a note ~10 min out.
   The PENDING row triggers `osScheduler.ensureRegistered()`.
2. **D2 — job exists:** verify per-OS (below).
3. **D3 — close the app entirely** (Quit, not just close the window).
4. **D4 — wait** past the publish time **and** the next ~5-min OS tick.
5. **D5 — confirm publish:** `~/.amethyst/scheduled/scheduler.log` shows a drain
   with `published=1`; the note is on a relay; the row is SENT.
6. **D6 — unregister on drain:** reopen the app (or let the headless run finish)
   with no PENDING/PUBLISHING rows left → `osScheduler.unregister()`; verify the
   OS job is **gone**.

### macOS (launchd)
- [ ] Registered: `launchctl print gui/$UID/com.vitorpamplona.amethyst.scheduler` exits 0.
- [ ] Plist at `~/Library/LaunchAgents/com.vitorpamplona.amethyst.scheduler.plist`,
      perms `-rw-------`; `ProgramArguments[0]` = absolute app path,
      `StartInterval` 300, `RunAtLoad` false.
- [ ] Force a run: `launchctl kickstart -k gui/$UID/com.vitorpamplona.amethyst.scheduler` → publishes.
- [ ] Unregistered: `launchctl print …` non-zero **and** plist deleted.

### Windows (schtasks + hidden VBS)
- [ ] Registered: `schtasks /query /tn "Amethyst\Scheduler"` exits 0.
- [ ] VBS at `%LOCALAPPDATA%\Amethyst\amethyst-scheduler-launch.vbs`.
- [ ] **No console flash:** when the task fires, **no black window** pops up.
- [ ] Force a run: `schtasks /run /tn "Amethyst\Scheduler"` → publishes.
- [ ] Unregistered: query says "task does not exist"; VBS deleted.

### Linux (systemd --user; cron fallback)
- [ ] systemd present: `systemctl --user is-enabled amethyst-scheduler.timer` → `enabled`;
      `systemctl --user list-timers amethyst-scheduler.timer` lists it.
- [ ] Units at `~/.config/systemd/user/amethyst-scheduler.{service,timer}`, perms `-rw-------`.
- [ ] **Lingering (required for app-closed firing while logged out):**
      `loginctl show-user $USER -p Linger` → `Linger=yes`. If registration warned it
      couldn't enable it, run `sudo loginctl enable-linger $USER` and retest.
- [ ] Force a run: `systemctl --user start amethyst-scheduler.service` → publishes.
- [ ] Unregistered: `is-enabled` non-zero; unit files deleted.
- [ ] **cron fallback** (no systemd): `crontab -l` shows a
      `# BEGIN amethyst-scheduler … # END amethyst-scheduler` block with a
      `*/5 * * * *` line invoking the **absolute** app path + `--publish-scheduled`;
      after drain the block is removed and other cron entries are untouched.

## Section E — Single-writer lock (no double-publish)

- [ ] **E1** With the app **open** (45s timer running), manually trigger the OS job
      (the per-OS "force a run" command). The headless process should log
      `Drain skipped: another process holds the lock …` and exit 0 — the note is
      published **exactly once**, never twice. (Exercises
      `~/.amethyst/scheduled/scheduled.lock`.)
- [ ] **E2 — crash recovery** *(optional/advanced)* If a headless run is killed
      mid-publish (leaving a row PUBLISHING), the row auto-reverts to PENDING after
      the claim TTL (~10 min) and publishes on a later tick — it is never stranded.

## Section F — NIP-37 draft sync (opt-in)

- [ ] **F1** Composer → **Save as draft** (without the sync toggle) → the draft
      appears in **Drafts & Scheduled → Drafts** (local, **no** cloud badge).
- [ ] **F2** The **"Sync across devices (encrypted)"** toggle is **OFF by default**.
- [ ] **F3** Enable the toggle → Save as draft → the draft shows a **cloud/synced
      badge**. A kind **31234** event was published (verify on a relay if you can).
- [ ] **F4 — cross-device** On a **second** device/profile logged into the **same**
      account, the synced draft appears in the Drafts tab (subscribed + decrypted).
- [ ] **F5 — dedup** The local copy and the synced copy of the same draft show as
      **one** row (deduped by dTag), not two.
- [ ] **F6 — sync failure is soft** With relays unreachable, enabling sync + Save
      still **saves locally** and shows "Draft saved locally, but sync failed: …".
- [ ] **F7 — privacy** Draft **content** is never sent in plaintext (NIP-44
      encrypted to your own pubkey). Note: kind 31234 *does* publicly reveal that a
      draft of some kind exists at a time for your pubkey — expected, documented.

## Section G — Security spot-checks (packaged, Section D context)

- [ ] **G1** `plist` / `.vbs` / systemd unit / `scheduled_posts.json` are **`0600`**
      (owner-only).
- [ ] **G2** The registered OS command uses an **absolute** app path (no bare
      `java`/`gradle`/`PATH` lookup).
- [ ] **G3** A headless drain **never** prompts for a signer/keychain unlock (it only
      pushes pre-signed bytes). Watch for any macOS keychain dialog during D5 — there
      should be none.

---

## Multi-account note

The store keys rows by account pubkey and claims are account-scoped. If you test
with multiple accounts, confirm each account only publishes **its own** scheduled
rows, and switching accounts restarts the in-app timer for the new account.

## Known limitations (v1 — not test failures)

- Opening a saved/synced **draft back into the composer** is not wired yet (no
  prefill entry point) — Drafts list shows them but clicking doesn't reopen.
- **Edit scheduled post** = Cancel (same prefill gap).
- OS integration is **compile-verified**; this sheet is its first real-world run.
- Windows/Linux are **untested by the authors** — results from Section D there are
  especially valuable.

## Cleanup after testing

Remove any leftover OS jobs and test data:

- macOS: `launchctl bootout gui/$UID/com.vitorpamplona.amethyst.scheduler; rm -f ~/Library/LaunchAgents/com.vitorpamplona.amethyst.scheduler.plist`
- Windows: `schtasks /delete /tn "Amethyst\Scheduler" /f` and delete the VBS.
- Linux: `systemctl --user disable --now amethyst-scheduler.timer; rm -f ~/.config/systemd/user/amethyst-scheduler.*; systemctl --user daemon-reload` (or remove the crontab marker block).
- `rm -rf ~/.amethyst/scheduled/` to clear the store/lock/log.
