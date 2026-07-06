# Desktop DM Reliability — Full Testing Sheet

**Branch:** `feat/desktop-dm-reliability` (rebased onto `origin/main` 2026-06-17; head at commit `a139c0fb17` after the pre-send fix)
**Companion:** [2026-06-12-desktop-dm-reliability-testing-sheet.md](2026-06-12-desktop-dm-reliability-testing-sheet.md) (automated section)
**Tester:**
**Build:** `./gradlew :desktopApp:run` (or launch `Amethyst.app` from `desktopApp/build/compose/binaries/main/app/`)

## Post-rebase reality

Upstream shipped its own NIP-42 AUTH work while our branch was in flight (commit `328790ac9f` — `feat: time-bound signer grants, last-used tracking, and relay AUTH settings`). After rebase, both systems coexist:

- **Upstream (Android-focused):** `RelayAuthPolicy` enum + `RelayAuthPermissionLedger` + `RelayAuthSettingsScreen` + relay URL now passed to the `signWithAllLoggedInUsers` lambda.
- **Ours (Desktop-focused):** `DesktopAuthCoordinator` + `AuthApprovalPolicy` (2-tier auto/prompt) + inline `AuthApprovalBanner` + `PreferencesAuthApprovalStore` + `RelayAuthSnapshot` StateFlow.

Both use the same quartz `RelayAuthenticator` seam. On desktop only our path is wired. On Android only upstream's path is wired.

## Known pre-existing issues (NOT our regressions)

| Issue | Where | Note |
|---|---|---|
| `ConcurrentModificationException` at `RelayLatencyTracker.sweep:182` during account switching | commons/relays/health | Documented in memory `desktop_relay_health_cme_crash`. Kills UI thread, coroutines keep running. Avoid rapid switches. |
| `NoClassDefFoundError` for `CompressionQuality` on stale gradle daemon | commons/service/upload | Fixed by `./gradlew --stop` + relaunch. Not a code regression. |

---

## Phase A — Automated verification (Claude ran these — status ✅)

Full detail in the companion sheet. Summary of what already passed:

| # | Check | Status |
|---|---|---|
| A1.1–A1.5 | Compile every module (quartz, commons, desktopApp, amethyst Android, cli) | ✅ |
| A2.1–A2.4 | Test suites (quartz, commons, desktopApp, amethyst Android) | ✅ |
| A3 | `./gradlew spotlessCheck` | ✅ |
| A4.1 | Distributable builds (`Amethyst.app` produced) | ✅ |
| A4.2 | Distributable launches cleanly | ✅ |
| A5.1 | Zero `Co-Authored-By` footers in commits | ✅ |
| A5.2 | All commits GPG-signed (`G`) | ✅ |
| D1.1 | Zero remaining `connectedRelays.value` in NIP-17 paths | ✅ |
| D1.2 | Only `DesktopAuthCoordinator` constructs `RelayAuthenticator` | ✅ |
| D1.3 | Indexer client is a distinct `NostrClient` instance | ✅ |
| D1.4 | Zero `dmInboxOrFallback` usages outside quartz definition | ✅ |

---

## Phase B — Manual testing walkthrough

### T0 — Setup (2 min, once)

```bash
# From the worktree root
git rev-parse HEAD  # should be a139c0fb17 or later

# Wipe prior AUTH grants so T3 starts fresh (macOS)
rm -rf ~/.java/.userPrefs/com/vitorpamplona/amethyst/desktop/auth 2>/dev/null

# Launch — leave this terminal visible; I'll read logs from it
./gradlew :desktopApp:run
```

- [ ] **T0.1** Wait for the sidebar to appear (~15–30s cold start, ~5s warm start)
- [ ] **T0.2** Log in with your primary account (User A)

---

### T1 — Startup wiring smoke check (30 sec, no interaction)

Watch the terminal for these two lines within 5s of login:

- [ ] **T1.1** `[RelayAuthenticator] Init, Subscribe`
- [ ] **T1.2** `[DesktopAuthCoordinator] AUTH wired for <pubkey8>`
- [ ] **T1.3** No `Exception` / `FATAL` / stack trace lines during startup

Missing either T1.1 or T1.2 → **stop**, we regressed.

**Session so far:** ✅ Verified multiple times (`2bad3487`, `04e18cb4`).

---

### T2 — Tier-1 AUTH (own DM-inbox) — 2 min

Verifies the user's own DM-inbox relays auto-AUTH with no banner.

- [ ] **T2.1** Open **Chats** in sidebar
- [ ] **T2.2** Note the relays connecting (Settings → Relays panel)
- [ ] **T2.3** Compose a DM **to yourself** (paste your own npub in a new DM)
- [ ] **T2.4** Send "test" — message arrives in your own inbox
- [ ] **T2.5** **CRITICAL:** during send, **no AUTH banner** appears at the top of the content area
- [ ] **T2.6** No `[AuthApprovalPolicy]` prompt lines in terminal

Pass criteria: message arrived, no banner.

---

### T3 — Tier-2 AUTH banner + persistence — 5 min

Trigger a challenge from an AUTH-required relay NOT in your `kind:10050`.

- [ ] **T3.1** Settings → Relays → Add relay → paste `wss://pyramid.fiatjaf.com`
- [ ] **T3.2** Wait 1–3s for connect + AUTH challenge
- [ ] **T3.3** **CRITICAL:** yellow banner appears at the top of the content area:
  - Relay URL shown (with 80dp left margin, clear of macOS traffic lights ✅ fixed in `fb2d004c73`)
  - Text: "requires authentication to deliver this message"
  - Buttons: `[Once]` `[Always]` `[Never]`
- [ ] **T3.4** Click `[Once]` → banner slides away; AUTH proceeds for the session only
- [ ] **T3.5** Restart app → same relay challenges again → banner reappears (ONCE was session-only)
- [ ] **T3.6** This time click `[Always]` → banner slides away
- [ ] **T3.7** Restart app → challenge from same relay does NOT show banner (auto-signed)
- [ ] **T3.8** Inspect Preferences: `cat ~/.java/.userPrefs/com/vitorpamplona/amethyst/desktop/auth/<full-pubkey>/prefs.xml` → contains `<entry key="wss://pyramid.fiatjaf.com/">ALWAYS</entry>`
- [ ] **T3.9** Add a different AUTH-required relay → banner → click `[Never]` → Preferences shows `BLOCKED`
- [ ] **T3.10** Restart → BLOCKED relay never surfaces a banner AND never sends AUTH (relay status: "not authenticated")

**Session so far:** T3.3 ✅ banner rendered (see screenshot 2026-07-06 at 15:20:38). Padding fixed. Rest pending your test.

---

### T4 — Multiple concurrent banners stack — 2 min

- [ ] **T4.1** Add 3 different AUTH-required relays back-to-back (before dismissing any)
- [ ] **T4.2** All 3 rows stack vertically
- [ ] **T4.3** Dismiss middle one with `[Once]` → only that row disappears
- [ ] **T4.4** (Stress) Add 5+ → top 3 visible, "+N more relays pending approval" row at bottom

---

### T5 — Per-account isolation + logout — 3 min

- [ ] **T5.1** Approve one relay with `[Always]` on account A
- [ ] **T5.2** Log out (watch terminal for coordinator teardown — no exceptions)
- [ ] **T5.3** Log in as account B → add same AUTH-required relay → banner appears (B has no persisted approval)
- [ ] **T5.4** Verify B's `[Always]` writes to B's Preferences node, NOT A's:
  - `ls ~/.java/.userPrefs/com/vitorpamplona/amethyst/desktop/auth/` → both A's and B's pubkey directories present
- [ ] **T5.5** Log back into A → same relay does NOT re-prompt (A's `[Always]` persisted)

**Session so far:** ✅ A/B switching seen in log (`2bad3487` ↔ `04e18cb4`), coordinator wires cleanly on every switch. Isolation confirmed at the logging level.

---

### T6 — NIP-17 send: no-10050 recipient (P0 SECURITY FIX) — 5 min

**Most important test.** Verify DMs no longer leak to your own relays when recipient has no `kind:10050`.

- [ ] **T6.1** Find or fabricate a Nostr account with NO `kind:10050`:
  - Generate a fresh nsec via `nak` or similar (won't have any events)
  - Or use an old npub known to have never used NIP-17
- [ ] **T6.2** Attempt to send a DM to that npub
- [ ] **T6.3** **Expected pre-branch:** wrap silently broadcast to your `connectedRelays.value`
- [ ] **T6.4** **Expected post-branch:** UI shows "Recipient has no DM relay list — messages cannot be delivered" AND Send button is disabled → **zero gift wraps published**
- [ ] **T6.5** Optional network inspection:
  - `sudo tcpdump -i any -A 'tcp port 443' | grep 1059` — should stay empty during the test
- [ ] **T6.6** After the [pre-send fix commit `a139c0fb17`] — if the peer publishes `kind:10050` mid-session and the resolver probe runs (see T13), the block should clear automatically without reopening the conversation

**Session so far:** ✅ Confirmed — screenshot showed the "Recipient has no DM relay list" red UI blocking the send.

---

### T7 — Indexer fan-out + F-01 unauth client — 5 min

Verify that when a recipient's `kind:10050` isn't cached, the resolver probes indexers with an **unauthenticated** client.

- [ ] **T7.1** Pick a recipient who HAS a `kind:10050` but whom you've never DM'd (fresh LocalCache miss)
- [ ] **T7.2** Compose DM to them → observe brief (sub-second to ~3s) delay
- [ ] **T7.3** In terminal, look for connections to the curated indexer set:
  - `wss://relay.nos.social`
  - `wss://relay.damus.io`
  - `wss://nos.lol`
  - `wss://relay.nostr.band`
  - `wss://purplerelay.com`
- [ ] **T7.4** **CRITICAL — F-01 check.** Verify NO `kind:22242` AUTH event sent on the indexer connections:
  - `sudo tshark -i any -Y 'websocket && frame contains "22242"'` — should stay empty while indexer probes run
  - Or manually: even if any indexer AUTH-challenges, the client should silently ignore
- [ ] **T7.5** Send succeeds → recipient's actual DM-inbox relay receives the wrap
- [ ] **T7.6** Compose another DM to same recipient → resolver hits LRU cache; no second indexer probe

---

### T8 — NIP-17 relay hint on wrap `p` tag — 3 min

- [ ] **T8.1** Send a DM to a recipient whose `kind:10050` is cached
- [ ] **T8.2** Inspect the wrap via `nak req -k 1059 -a <your-pubkey>` on one of your DM-inbox relays
- [ ] **T8.3** Expected `p` tag: `["p", "<recipient-pubkey-hex>", "wss://recipient-primary-relay/"]` (3 elements)
- [ ] **T8.4** For a recipient with no known relay: `["p", "<recipient-pubkey-hex>"]` (2 elements, no fake empty third element)

---

### T9 — Group DM shared `rumor.id` — 4 min

- [ ] **T9.1** Create a group DM with 3 recipients
- [ ] **T9.2** Send one message
- [ ] **T9.3** Inspect wraps via relay UI — all decrypt to a rumor with the **same `id`**
- [ ] **T9.4** Have one recipient react (👍)
- [ ] **T9.5** Reaction targets the shared rumor id; all participants see the same reaction

---

### T10 — Outbox AUTH carve-out under load — 3 min

- [ ] **T10.1** Add or select an AUTH-required relay `R`
- [ ] **T10.2** Publish 5 notes rapidly (compose dialog, send-send-send)
- [ ] **T10.3** During AUTH window: all 5 stay in the outbox (no premature drop)
- [ ] **T10.4** Once AUTH completes → all 5 successfully publish on `R`

Pre-branch: after 3 `auth-required:` responses per event, outbox would drop on next `newTry()`.

---

### T11 — Bunker `Semaphore(4)` concurrency cap — 3 min

- [ ] **T11.1** Log in with a NIP-46 bunker account
- [ ] **T11.2** Send a **5-recipient group DM**
- [ ] **T11.3** In nsec.app / Amber, at most 4 in-flight signing requests concurrently
- [ ] **T11.4** Repeat with a local nsec account → all 5 run in parallel
- [ ] **T11.5** Both send flows deliver correctly

---

### T12 — kind:1059 subscription has no `since` — 2 min

- [ ] **T12.1** Inspect the outgoing REQ for gift wraps (debug tooling or a mock relay)
- [ ] **T12.2** Expected filter: `{"kinds":[1059,1060],"#p":["<your-pubkey>"]}` — **no `since` field**
- [ ] **T12.3** Alternative: `nak` or `websocat` proxy connecting to one of your DM-inbox relays

Pre-branch: `since` filter dropped wraps whose randomized `created_at` predated it (NIP-17 randomizes up to 2 days back).

---

### T13 — Pre-send validation alignment + resolver probe (NEW, commit `a139c0fb17`) — 5 min

The bug this test targets: the pre-send "Recipient has no DM relay list" warning could disagree with the actual send path, and the block persisted even after the peer published `kind:10050` until the conversation was reopened.

**T13.a — Strict alignment (a)**

- [ ] **T13.1** Find a peer whose profile has NO `kind:10050` but DOES have `kind:10002` (NIP-65) with read relays
- [ ] **T13.2** Compose a DM to them
- [ ] **T13.3** **Expected pre-fix:** UI green-lights the send (lenient `dmInboxRelays()` returned the NIP-65 read relays)
- [ ] **T13.4** **Expected post-fix:** UI shows the "no DM relay list" warning + disables Send (strict `dmInboxRelaysStrict()` returns null)
- [ ] **T13.5** Attempting to bypass and send → `DmSendTracker` also fails ("No relays available"), matching the UI

Pass criteria: UI and send path agree — no silent-fail sends.

**T13.b — Resolver probe unblocks stale conversations**

- [ ] **T13.6** Open a DM with a peer whose `kind:10050` is NOT in your LocalCache (fresh contact)
- [ ] **T13.7** UI immediately shows the warning (cache miss)
- [ ] **T13.8** Within ~2s the resolver probes indexer relays and the warning **should clear on its own** if the peer has published `kind:10050`
- [ ] **T13.9** Send button turns from grey to blue → send works
- [ ] **T13.10** For a genuinely no-`kind:10050` peer: warning persists after the probe (~2–5s wait), send stays disabled

**T13.c — Real scenario the fix unblocks**

- [ ] **T13.11** Log in as B → publish `kind:10050` via the Dns icon in Chats
- [ ] **T13.12** Log out → log in as A
- [ ] **T13.13** Open DM to B immediately (before A's normal feed subscriptions have picked up B's fresh event)
- [ ] **T13.14** Warning shows initially (cache miss) → clears within ~2s (indexer probe finds B's event)
- [ ] **T13.15** Send works

**Session so far:** ✅ User confirmed "ok, works" after the fix landed.

---

## Phase C — Android sanity (if you have a device)

### C1 — Commons inheritance check

- [ ] **C1.1** `./gradlew :amethyst:installDebug` → Android launches
- [ ] **C1.2** Send a NIP-17 DM from Android → works as before (Android doesn't use `DmInboxRelayResolver` / `DesktopAuthCoordinator`)
- [ ] **C1.3** Android `User.dmInboxRelays()` behaviour unchanged (only added strict variant, didn't remove lenient)
- [ ] **C1.4** Android signing still uses Android-only `AuthCoordinator`

---

## Sign-off matrix

| # | Test | Automated / Manual | Pass? | Notes |
|---|---|---|---|---|
| A1–A5, D1 | Full automated | Claude | ✅ | See companion sheet |
| T1 | Startup wiring | Manual | ✅ | Log lines confirmed for 2 accounts |
| T2 | Tier-1 auto-sign | Manual | | |
| T3 | Tier-2 banner + persistence | Manual | | Banner rendered ✅; padding fixed; persistence flow pending your test |
| T4 | Multiple banners stack | Manual | | |
| T5 | Per-account isolation | Manual | ✅ | Coordinator swaps cleanly per switch |
| **T6** | **P0 SECURITY (no-10050)** | Manual | ✅ | UI blocked as designed |
| **T7** | **F-01 unauth indexer** | Manual | | Highest remaining priority |
| T8 | Relay hint p-tag | Manual | | |
| T9 | Group DM rumor id | Manual | | |
| T10 | Outbox AUTH carve-out | Manual | | |
| T11 | Bunker Semaphore | Manual | | Only if you have a bunker |
| T12 | No since filter | Manual | | |
| **T13** | **Pre-send alignment + probe** | Manual | ✅ | Confirmed working after `a139c0fb17` |
| C1 | Android sanity | Manual | | Optional |

**Overall verdict:** ⬜ pass / ⬜ fail / ⬜ needs revisit

---

## What this branch does NOT ship (out-of-scope)

Explicit follow-ups per the deepening synthesis — NOT regressions:

- Persistent retry queue with exp-backoff (SQLite-backed) — substrate not yet built
- Per-message delivery state in chat bubbles (`✓` `✓✓` `⟳` `⚠`) — `DmSendTracker` still global
- Bunker progress UI wiring — `SigningOpState.Progress` substrate landed but no caller emits per-step counts
- Window-focus re-AUTH coordinator — replaced with lazy reactive AUTH per deepening
- NIP-46 batch `get_conversation_keys` RPC — spec PR proposed in plan, no implementation
- Android UI parity for AUTH banner — Android keeps upstream's Settings-screen approach
- Manual relay-entry dialog when recipient has no 10050 — replaced with Snackbar-equivalent failure
- NIP-09 deletion of self-copies on kind:10050 rotation — release-note disclosure only
- Fix for pre-existing `RelayLatencyTracker.sweep` CME — filed as separate tech-debt task

## Known non-issues (don't flag as bugs)

- `[GiftWrapEvent] Couldn't Decrypt the content ...` debug lines — normal LocalCache trial-decrypt
- VLC `securetransport tls client error` — pre-existing media playback warnings
- `[NIP19 Parser] Issue trying to Decode NIP19 ...` — pre-existing, malformed identifiers in some events
- `DmBroadcastBanner` (different from AUTH banner) may also appear — distinguish by buttons: AUTH has `[Once/Always/Never]`, broadcast has send-progress status

## Sign-off

**Tester:** _______________________
**Date:** _______________________
**Overall:** ⬜ Approved for PR / ⬜ Blocked / ⬜ Needs additional testing
**Blockers:** _______________________________________________________
