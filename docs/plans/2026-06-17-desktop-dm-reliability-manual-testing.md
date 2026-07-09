# Desktop DM Reliability — Testing Playbook

**Branch:** `feat/desktop-dm-reliability` rebased onto `upstream/main`
**Tester:** _______________________
**Date:** _______________________

**Instructions:** Follow this top to bottom. Every step is an action or an observation. Don't skip ahead — later tests assume state from earlier ones. Total ≈ 40 min for full pass.

---

## Session results — 2026-07-09 (live run)

| Test | Result | Notes |
|------|--------|-------|
| T1 startup / AUTH wired | ✅ PASS | both `Init, Subscribe` + `AUTH wired` logged; no CME crash |
| T2 tier-1 self-DM (no banner) | ✅ PASS | published, no `PendingAuthApproval` prompt |
| T3.a tier-2 banner render | ✅ PASS | `relay.ditto.pub` banner, icon clear of traffic lights, 3 buttons |
| T3.c `Always` persists | ✅ PASS | `auth/<pubkey>/ wss://relay.ditto.pub/ = ALWAYS` in the plist |
| T6 no-10050 blocks send | ✅ PASS | "Recipient has no DM relay list", send disabled |
| T6b kind:10002-only blocks | ✅ PASS | same block; zero publish to the NIP-65 read relay |
| T8 wrap `p`-tag relay hint | ✅ PASS (after fix) | 3-element `["p", hex, wss://nos.lol/]` on the wrap |
| T12 kind:1059 sub has no `since` | ✅ PASS | `since` removed from `giftWrapsToMe` signature |
| T3.b `Once` / T3.d `Never` | ⏭️ not run | logic covered by `AuthApprovalEndToEndTest` |
| T9 group rumor.id | ⏭️ covered-by-construction | rumor signed once before the per-recipient loop |
| T10 AUTH-under-load / T11 bunker | ⏭️ not run | no challenging relay / bunker on hand |

**Bugs found & fixed this run:**
1. `DmInboxRelayResolver` LocalCache fast-path used lenient `dmInboxRelays()` → NIP-65 read-relay leak. Now `dmInboxRelaysStrict()`.
2. `NewDmDialog` rendered pasted npubs of metadata-less users as non-clickable → couldn't start a DM by npub. Now `getOrCreateUser`.
3. NIP-17 `p`-tag relay hint was plumbed in quartz but never passed by `DesktopIAccount` → every wrap shipped a 2-element `p` tag. Now wired.

---

## Setup (once, ~3 min)

**1.** In a terminal, cd to the worktree and confirm you're on the right commit:

```bash
cd /path/to/AmethystMultiplatform/.worktrees/feat/desktop-dm-reliability
git rev-parse HEAD
```

- Expect: `fcfc43eb44` (or later). If different: `git pull` and re-verify.

**2.** Wipe any prior AUTH grants so persistence tests start clean:

```bash
rm -rf ~/.java/.userPrefs/com/vitorpamplona/amethyst/desktop/auth
```

**3.** Launch the app (keep this terminal visible — we'll read logs from it):

```bash
./gradlew :desktopApp:run
```

- Expect: window appears in 15–30 s (cold) / 5 s (warm).

**4.** In the app, log in with your primary account. Call this **User A**.

- Expect: sidebar loads, feed populates.

**5.** In the terminal, look for these two lines (they appear within 5 s of login):

```
[RelayAuthenticator] Init, Subscribe
[DesktopAuthCoordinator] AUTH wired for <pubkey8>
```

- **If both appear:** ✅ setup complete. Proceed to T1.
- **If either is missing:** STOP. Tell me the terminal output.

---

## T1 — Startup smoke check (already ✅ during setup)

Nothing extra to do — the two log lines above ARE T1.

- [ ] **T1 PASS** — both `Init, Subscribe` and `AUTH wired for <pubkey8>` printed with no exceptions

---

## T2 — Tier-1 self-DM (no banner) — 2 min

**Goal:** verify your own DM-inbox relays auto-AUTH silently.

**Steps:**

**1.** In the sidebar, click **Chats** (chat bubble icon).

**2.** At the top of the conversation list, click the **`+` icon** (new conversation).

**3.** Paste your OWN npub into the recipient field. Confirm.

**4.** In the message box, type: `t1 self-dm test`

**5.** Watch the top of the content area (where the yellow banner would appear).

- **Expected:** send button enables blue → no yellow AUTH banner appears anywhere.

**6.** Click the **send arrow** (right side of the message box).

**7.** Wait 3 s. The message should appear in your inbox.

- **Expected:** message appears in the conversation. Terminal has NO `AuthApprovalPolicy` prompt lines.

**Record:**

- [ ] **T2.1** No AUTH banner appeared: **YES / NO**
- [ ] **T2.2** Message arrived: **YES / NO**
- [ ] **T2 PASS** — both YES

---

## T3 — Tier-2 banner + persistence — 8 min

**Goal:** trigger a challenge from an AUTH-required relay NOT in your `kind:10050`, verify the banner renders + all three buttons persist correctly.

### T3.a — Trigger the banner

**1.** Open Settings. (Look for a gear/cog icon in the sidebar. If absent, try the app menu → Settings.)

**2.** Go to the **Relays** tab.

**3.** Find the "Add relay" input. Paste: `wss://pyramid.fiatjaf.com`

**4.** Save/apply (button label varies — usually "Add" or "Save").

**5.** Wait 1–3 s. Watch the **top of the content area** (below the title bar, above the main content).

- **Expected:** a yellow-tinted horizontal row slides in showing:
  - Lock icon on the left (with proper margin from window edge — 80dp — not overlapping the traffic lights)
  - `pyramid.fiatjaf.com` in a semi-bold heading
  - Subtext: "requires authentication to deliver this message"
  - Three buttons on the right: **`Once`** **`Always`** **`Never`**

Record:

- [ ] **T3.a.1** Banner appeared within 3 s: **YES / NO**
- [ ] **T3.a.2** Icon + text NOT overlapping traffic lights: **YES / NO**
- [ ] **T3.a.3** All three buttons visible: **YES / NO**

### T3.b — `[Once]` behaviour (session-only, no persistence)

**6.** Click `Once`.

- **Expected:** banner slides away immediately, no visible change to relay state.

**7.** In a second terminal, check the Preferences store did NOT get written for this relay.

> **Prefs location (macOS).** This JVM uses the `MacOSXPreferences` backing
> store, NOT `~/.java/.userPrefs`. Java prefs land in
> `~/Library/Preferences/com.vitorpamplona.amethyst.plist` under an
> `auth/<full-pubkey>/` node. Read it with `plutil`:

```bash
plutil -convert xml1 -o - ~/Library/Preferences/com.vitorpamplona.amethyst.plist | grep -i "pyramid\|ditto"
```

- **Expected:** empty output (ONCE is not persisted).

**8.** Close the app (Cmd+Q). Wait 2 s. Relaunch via `./gradlew :desktopApp:run`. Log in as A again.

**9.** Wait ~5 s. The banner for `pyramid.fiatjaf.com` should reappear (session state was not saved).

Record:

- [ ] **T3.b.1** After `[Once]`: Preferences NOT written: **YES / NO**
- [ ] **T3.b.2** After restart: banner reappeared: **YES / NO**

### T3.c — `[Always]` behaviour (persisted grant)

**10.** In the banner that just reappeared, click `Always`.

- **Expected:** banner slides away, `pyramid.fiatjaf.com` now shows "Authenticated" in Settings → Relays.

**11.** Check Preferences was written:

```bash
plutil -convert xml1 -o - ~/Library/Preferences/com.vitorpamplona.amethyst.plist | grep -i "pyramid\|ditto\|ALWAYS"
```

- **Expected:** the relay URL (e.g. `wss://relay.ditto.pub/`) followed by `ALWAYS`, under the `auth/<full-pubkey>/` node.

**12.** Close app (Cmd+Q). Relaunch. Log in as A.

- **Expected:** relay auto-authenticates in the background. **No banner appears** for `pyramid.fiatjaf.com`.

Record:

- [ ] **T3.c.1** Preferences shows `ALWAYS`: **YES / NO**
- [ ] **T3.c.2** After restart: no banner, auto-authenticated: **YES / NO**

### T3.d — `[Never]` behaviour (persisted block)

**13.** Add a different AUTH-required relay. If you have another, use it. Otherwise, try `wss://nostr.wine` (they AUTH-challenge non-subscribers) or `wss://relay.snort.social`.

**14.** Wait for the new banner to appear.

**15.** Click `Never`.

- **Expected:** banner disappears. The relay shows as connected but "not authenticated".

**16.** Check Preferences:

```bash
plutil -convert xml1 -o - ~/Library/Preferences/com.vitorpamplona.amethyst.plist | grep -i "<relay-domain>\|BLOCKED"
```

- **Expected:** the relay URL followed by `BLOCKED`, under the `auth/<full-pubkey>/` node.

**17.** Restart the app. Log in as A.

- **Expected:** the BLOCKED relay never surfaces a banner. It stays "not authenticated". No `kind:22242` AUTH event ever sent to it.

Record:

- [ ] **T3.d.1** Preferences shows `BLOCKED`: **YES / NO**
- [ ] **T3.d.2** After restart: no banner, no AUTH sent: **YES / NO**

---

**T3 sign-off:** Complete `T3.a`, `T3.b`, `T3.c`, `T3.d`.

- [ ] **T3 PASS** — all four subs green

---

## T4 — Multiple concurrent banners — 3 min

**Goal:** verify multiple pending banners stack correctly and resolve independently.

**Steps:**

**1.** In Settings → Relays, quickly add 3 different AUTH-required relays back-to-back. Suggested set:
  - `wss://pyramid.fiatjaf.com` (if not already blocked/allowed)
  - `wss://relay.nostr.com.au`
  - `wss://nostr.wine`

**2.** Watch the banner area — all 3 rows should appear stacked vertically within ~3 s.

**3.** Click `Once` on the **middle** row.

- **Expected:** ONLY the middle row disappears. The other two remain visible.

**4.** (Optional stress test) Add 5+ more AUTH-required relays.

- **Expected:** first 3 shown inline; row at the bottom reads "+N more relays pending approval".

Record:

- [ ] **T4.1** 3 banners stack vertically: **YES / NO**
- [ ] **T4.2** Middle-row dismiss only affects itself: **YES / NO**
- [ ] **T4.3** "+N more" row shows when >3 pending: **YES / NO**
- [ ] **T4 PASS** — all three YES

---

## T5 — Per-account isolation — 4 min

**Goal:** verify AUTH grants are scoped per-account and cleaned on logout.

**Steps:**

**1.** Ensure A has at least one `ALWAYS` grant (from T3.c: `pyramid.fiatjaf.com`).

**2.** Log out of A (sidebar → profile → Logout, or app menu).

- **Terminal:** watch for `DesktopAuthCoordinator` teardown lines (no exceptions).

**3.** Log in as User B (different pubkey — nsec, npub, or bunker).

- **Terminal:** expect `[DesktopAuthCoordinator] AUTH wired for <B-pubkey8>` — different from A's.

**4.** Add `wss://pyramid.fiatjaf.com` in Settings → Relays for B.

- **Expected:** banner appears (B does NOT inherit A's `ALWAYS` grant).

**5.** Check that A's and B's Preferences are separate:

```bash
ls ~/.java/.userPrefs/com/vitorpamplona/amethyst/desktop/auth/
```

- **Expected:** two directories, one per full pubkey.

**6.** Click `Always` on B's banner.

**7.** Log out of B. Log back into A.

- **Terminal:** `AUTH wired for <A-pubkey8>` again.

**8.** Watch for banners.

- **Expected:** no banner for `pyramid.fiatjaf.com` (A's `ALWAYS` still persisted).

Record:

- [ ] **T5.1** Coordinator teardown clean on logout (no exceptions): **YES / NO**
- [ ] **T5.2** B sees banner for A-approved relay (isolation): **YES / NO**
- [ ] **T5.3** Two separate Preferences dirs exist: **YES / NO**
- [ ] **T5.4** Re-login to A: no re-prompt: **YES / NO**
- [ ] **T5 PASS** — all four YES

---

## T6 — P0 security fix (no-10050 recipient) — 5 min

**Goal:** verify DMs are NOT silently broadcast to your general relays when the recipient has no `kind:10050`.

### T6.a — Create a "no-inbox" test recipient

**1.** In a terminal, generate a fresh nsec/npub pair:

```bash
# Option: use nak
nak key generate
# Copy the printed nsec and npub
```

Or use any known npub of an account that never published `kind:10050`.

**2.** In Amethyst as A, click **`+`** in Chats. Paste the test npub. Confirm.

### T6.b — Verify the UI blocks send

**3.** Type any message.

**4.** Look at the row below the message input.

- **Expected:** red text "**Recipient has no DM relay list — messages cannot be delivered**"
- **Expected:** send button is grey/disabled.

**5.** Try clicking send anyway.

- **Expected:** nothing happens (button disabled). Or, if enabled by upstream UI quirk, `DmSendTracker` shows "No relays available" briefly.

### T6.c — Verify no wrap leaves the app (optional, for the security-conscious)

**6.** In a terminal, run:

```bash
sudo tcpdump -i any -A -s 0 'tcp port 443 or tcp port 80' 2>/dev/null | grep -i "kind\":1059"
```

**7.** In the app, try to send. Watch the tcpdump output for 30 s.

- **Expected:** zero output. No gift wrap (kind 1059) publishes anywhere.

**8.** Stop tcpdump with Ctrl+C.

Record:

- [ ] **T6.1** UI shows "no DM relay list" warning: **YES / NO**
- [ ] **T6.2** Send button disabled: **YES / NO**
- [ ] **T6.3** No `kind":1059` in outgoing traffic during send attempt: **YES / NO / SKIPPED**
- [ ] **T6 PASS** — T6.1 and T6.2 both YES (T6.3 optional but recommended)

---

## T6b — Strict kind:10050 (NIP-65 read-relay non-leak) — 4 min

**Goal:** verify the review fix — a recipient that DOES publish NIP-65 read
relays (kind:10002) but has NO `kind:10050` is still treated as unreachable.
The lenient fast-path bug would have published the wrap to those NIP-65 read
relays; the fix must NOT.

### T6b.a — Create a recipient with kind:10002 but no kind:10050

**1.** Generate a fresh key and publish ONLY a NIP-65 relay list (no 10050):

```bash
nak key generate            # copy nsec + npub
# publish a kind:10002 with a read relay, and NO kind:10050:
echo '{"kind":10002,"tags":[["r","wss://relay.damus.io","read"]],"content":""}' \
  | nak event --sec <nsec> wss://relay.damus.io wss://nos.lol
```

**2.** As User A, open a new chat to that npub so A's LocalCache ingests the
recipient's kind:10002 (send/hover the profile so the relay list loads).

### T6b.b — Verify send is blocked, not routed to the read relay

**3.** Type a message. Observe the row under the input.

- **Expected:** same "no DM relay list — messages cannot be delivered"
  warning as T6; send disabled.
- **Wrong (pre-fix bug):** send is ENABLED and the wrap goes to
  `wss://relay.damus.io` (the recipient's NIP-65 *read* relay).

**4.** (Optional, definitive) tcpdump as in T6.c while attempting send.

- **Expected:** zero `kind":1059` frames to the recipient's kind:10002 relays.

Record:

- [ ] **T6b.1** Send blocked despite recipient having kind:10002: **YES / NO**
- [ ] **T6b.2** No wrap sent to NIP-65 read relay (if tcpdump run): **YES / NO / SKIPPED**
- [ ] **T6b PASS** — T6b.1 YES

---

## T7 — Indexer fan-out + F-01 unauth check — 6 min

**Goal:** verify the resolver probes indexer relays with an UNAUTHENTICATED client (no `kind:22242` AUTH events leaked to indexers).

### T7.a — Prime the state

**1.** Restart the app (Cmd+Q, then `./gradlew :desktopApp:run`).

- Fresh LocalCache = maximum chance the resolver actually fires.

**2.** Log in as A.

### T7.b — Set up traffic capture (optional but revealing)

**3.** In a second terminal, start capturing all WebSocket traffic:

```bash
sudo tshark -i any -Y 'websocket' -T fields -e ws.payload 2>/dev/null | head -c 100000
```

Or (simpler):

```bash
sudo tcpdump -i any -A -s 0 'tcp port 443' 2>/dev/null > /tmp/dm-traffic.log &
```

### T7.c — Trigger the resolver

**4.** Pick a recipient who HAS a `kind:10050` published (a NIP-17-active account) but whom you have NEVER DM'd from account A.

**5.** In Amethyst, click **`+`** in Chats. Paste the recipient's npub. Confirm.

**6.** Watch the pre-send row.

- **Expected sequence:**
  - Initial: red "no DM relay list" warning (LocalCache miss).
  - Within 2–5 s: warning disappears (resolver probe found the recipient's `kind:10050` on an indexer).
  - Send button turns blue.

### T7.d — Verify F-01 (no AUTH to indexer)

**7.** Search the captured traffic for `kind:22242` AUTH events:

```bash
grep -i "\"kind\":22242" /tmp/dm-traffic.log | head -20
```

- **Expected:** any `kind:22242` events found should only be to relays in your existing DM-inbox set — NOT to the indexer set (`relay.nos.social`, `relay.damus.io`, `nos.lol`, `relay.nostr.band`, `purplerelay.com`).

**8.** In the app, type a message and send.

- **Expected:** send succeeds. Recipient's actual DM-inbox relay receives the wrap.

**9.** Stop tcpdump: `sudo pkill tcpdump`

### T7.e — Verify LRU cache hit on second send

**10.** Immediately compose a second DM to the same recipient. Send.

- **Expected:** send is immediate, no delay. Resolver hits its LRU cache, no new indexer probe.

Record:

- [ ] **T7.1** Warning cleared within 5 s (resolver probe worked): **YES / NO**
- [ ] **T7.2** Send button became enabled after probe: **YES / NO**
- [ ] **T7.3** No `kind:22242` AUTH sent to indexer relays: **YES / NO / SKIPPED**
- [ ] **T7.4** DM delivered to recipient: **YES / NO**
- [ ] **T7.5** Second DM to same recipient: no probe delay: **YES / NO**
- [ ] **T7 PASS** — T7.1, T7.2, T7.4, T7.5 all YES

---

## T8 — Wrap `p`-tag relay hint — 4 min

**Goal:** verify the outgoing gift wrap includes the recipient's primary DM relay as the third element of the `p` tag.

**Steps:**

**1.** Send a DM to any recipient with a known `kind:10050` (e.g. the one from T7).

**2.** In a terminal, use `nak` (or `websocat`) to query one of the recipient's DM-inbox relays for their gift wraps:

```bash
RECIPIENT_HEX=<paste-recipient-hex-pubkey>
DM_RELAY=<paste-one-of-their-10050-relays>

nak req -k 1059 --tag "p=$RECIPIENT_HEX" "$DM_RELAY" | head -5
```

**3.** Find the wrap you just sent (highest `created_at`). Look at its `p` tag.

- **Expected:** `["p", "<recipient-hex>", "wss://recipient-primary-relay/"]` — 3 elements, third is a valid relay URL.

**4.** For contrast, send a DM to a recipient whose `kind:10050` you have NO indexer/cache hit for (e.g. the one from T6 if you have their nsec to simulate — otherwise skip).

- **Expected:** wrap's `p` tag has only 2 elements: `["p", "<hex>"]` — no fake empty third element.

Record:

- [ ] **T8.1** With known relay: 3-element `p` tag: **YES / NO**
- [ ] **T8.2** Without known relay: 2-element `p` tag (no empty third): **YES / NO / SKIPPED**
- [ ] **T8 PASS** — T8.1 YES

---

## T9 — Group DM shared `rumor.id` — 5 min

**Goal:** verify all recipient wraps in a group DM decrypt to a rumor with the SAME `id`.

**Steps:**

**1.** In Amethyst as A, click **`+`** in Chats. Add 3 recipient npubs (you can include yourself as one, plus 2 others whose `kind:10050` is known).

**2.** Type a distinctive message: `t9 group rumor coherence test`. Send.

**3.** For each recipient, use `nak` to fetch the gift wrap from their DM-inbox relay:

```bash
for RECIPIENT in $RECIPIENT_A $RECIPIENT_B $RECIPIENT_C; do
  nak req -k 1059 --tag "p=$RECIPIENT" wss://relay.example/ | head -3
done
```

**4.** Ideally decrypt each wrap (requires each recipient's nsec). But since all 3 seals encode the same rumor, the rumor `id` should be identical across the 3 wraps.

**5.** If you have at least 2 recipient nsecs, decrypt via `nak`:

```bash
nak decrypt --sec $NSEC "<encrypted-wrap-content>"
# Look at the inner rumor's "id" field
```

**6.** Compare the rumor `id` across the wraps.

- **Expected:** all 3 rumor `id`s are IDENTICAL.

**7.** (Bonus) Have one of the recipients (in another Amethyst instance or via nak) react to the message with `+`.

**8.** Confirm A and other recipients see the reaction.

- **Expected:** reaction targets the shared `rumor.id` and appears cross-recipient.

Record:

- [ ] **T9.1** All wraps decrypt to same rumor.id: **YES / NO / SKIPPED (needs multi-account decrypt)**
- [ ] **T9.2** Cross-recipient reaction visible: **YES / NO / SKIPPED**
- [ ] **T9 PASS** — T9.1 YES (or explicitly skipped)

---

## T10 — Outbox AUTH carve-out under load — 4 min

**Goal:** verify multiple queued events are NOT silently dropped during AUTH negotiation.

**Steps:**

**1.** In Settings → Relays, ensure you have `wss://pyramid.fiatjaf.com` connected. If you `[Always]`-approved it in T3.c, first log out and back in so the relay reconnects and re-challenges.

**2.** In the compose dialog, publish 5 notes rapidly (10 seconds apart is fine):

```
t10 note 1
t10 note 2
t10 note 3
t10 note 4
t10 note 5
```

**3.** In the terminal, watch for AUTH activity on `pyramid.fiatjaf.com`:

```
[RelayAuthenticator] ... auth-required: ...
[RelayAuthenticator] ... AUTH accepted ...
```

**4.** Once AUTH completes, all 5 notes should publish to `pyramid.fiatjaf.com`.

**5.** Verify by querying `pyramid.fiatjaf.com` for your recent notes:

```bash
nak req -a $A_HEX -k 1 wss://pyramid.fiatjaf.com | head -10
```

- **Expected:** all 5 `t10 note N` events present on `pyramid.fiatjaf.com`.

Record:

- [ ] **T10.1** All 5 notes visible on `pyramid.fiatjaf.com`: **YES / NO**
- [ ] **T10.2** Terminal shows AUTH succeeded before drops: **YES / NO**
- [ ] **T10 PASS** — T10.1 YES

---

## T11 — Bunker `Semaphore(4)` (bunker users only) — 3 min

**Skip if you don't have a NIP-46 bunker (nsec.app / Amber).**

**Goal:** verify NIP-17 group DMs are rate-limited to ≤4 concurrent bunker RPCs.

**Steps:**

**1.** Log out. Log in with a bunker (`bunker://` URI).

**2.** Compose a group DM to **5 recipients** (5 different npubs with known `kind:10050`).

**3.** In nsec.app / Amber, watch the request feed as you click Send.

- **Expected:** at most **4** requests in-flight at any moment. Requests process in batches of 4.

**4.** For comparison: log out, log back in with a local nsec. Repeat the 5-recipient send.

- **Expected:** local signer runs all 5 requests in parallel (no Semaphore cap).

Record:

- [ ] **T11.1** Bunker: ≤4 concurrent RPCs: **YES / NO / SKIPPED (no bunker)**
- [ ] **T11.2** Local: fully parallel: **YES / NO / SKIPPED**
- [ ] **T11 PASS** — either both YES or both SKIPPED

---

## T12 — kind:1059 subscription has no `since` — 3 min

**Goal:** verify the outgoing REQ for gift wraps has NO `since` filter (would silently drop old-timestamped wraps).

**Steps:**

**1.** In one terminal, run a WebSocket relay proxy that echoes traffic (or use `nak` to inspect):

```bash
# Simplest: read the desktop subscription code directly
grep -n "FilterDMs.giftWrapsToMe\|since" desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/subscriptions/FilterDMs.kt
```

- **Expected:** signature `fun giftWrapsToMe(userPubKeyHex: HexKey)` — **NO `since` parameter**.

**2.** Live check (harder but definitive): use `mitmproxy` or `websocat` in proxy mode to intercept WebSocket traffic from the app.

**3.** Alternatively: rely on the unit tests. Confirm they pass:

```bash
./gradlew :quartz:jvmTest --tests "com.vitorpamplona.quartz.nip59Giftwrap.wraps.*"
```

- **Expected:** BUILD SUCCESSFUL.

Record:

- [ ] **T12.1** `giftWrapsToMe` signature has no `since`: **YES / NO**
- [ ] **T12.2** Unit tests pass: **YES / NO**
- [ ] **T12 PASS** — both YES

---

## T13 — Pre-send alignment + resolver probe (verified working) — sanity re-check, 3 min

**Already confirmed** during the pre-launch fix. Quick re-check:

**Steps:**

**1.** In Amethyst as A, open a fresh DM with a recipient whose `kind:10050` is NOT in your LocalCache (e.g. a fresh contact — click a new profile, then compose DM).

**2.** Watch the pre-send row.

- **Expected:** red "no DM relay list" warning appears initially.

**3.** Wait 2–5 s.

- **Expected:** warning clears on its own (resolver probe found the recipient via indexer). Send button turns blue.

Record:

- [ ] **T13.1** Warning appears initially: **YES / NO**
- [ ] **T13.2** Warning clears within 5 s (resolver worked): **YES / NO**
- [ ] **T13 PASS** — both YES

---

## Sign-off

| Test | Pass? | Notes |
|---|---|---|
| Setup | ⬜ | |
| T1 startup wiring | ⬜ | |
| T2 tier-1 self-DM | ⬜ | |
| T3 tier-2 banner (T3.a–T3.d) | ⬜ | |
| T4 multiple banners | ⬜ | |
| T5 per-account isolation | ⬜ | |
| **T6 P0 SECURITY** | ⬜ | Highest priority |
| **T7 F-01 unauth indexer** | ⬜ | Highest priority |
| T8 wrap p-tag relay hint | ⬜ | |
| T9 group DM rumor id | ⬜ | |
| T10 outbox AUTH carve-out | ⬜ | |
| T11 bunker Semaphore | ⬜ | Skip if no bunker |
| T12 no since filter | ⬜ | |
| T13 pre-send alignment | ⬜ | |

**Overall:** ⬜ PASS — ready for PR / ⬜ FAIL — see blockers / ⬜ NEEDS REVISIT

**Blockers:** _______________________________________________________

**Tester signature:** _______________________  **Date:** _______________________

---

## Known pre-existing issues (NOT branch regressions)

- **`ConcurrentModificationException` at `RelayLatencyTracker.sweep:182`** during rapid account switching. Kills UI thread; coroutines keep running. Documented in memory `desktop_relay_health_cme_crash`.
- **`NoClassDefFoundError` for `CompressionQuality`** on stale gradle daemon. Fix: `./gradlew --stop && ./gradlew :desktopApp:run`.

## Known non-issues (don't file as bugs)

- `[GiftWrapEvent] Couldn't Decrypt the content …` debug lines — normal LocalCache trial-decrypt for wraps not addressed to you.
- VLC `securetransport tls client error` — pre-existing media playback warnings.
- `[NIP19 Parser] Issue trying to Decode NIP19 …` — pre-existing, malformed identifiers in some events.
- `DmBroadcastBanner` (send-progress) may render simultaneously with `AuthApprovalBanner` — distinguish by buttons: AUTH banner has `Once/Always/Never`; broadcast has send-count status.

## Out of scope for this branch

Explicit follow-ups per the deepening synthesis:
- Persistent retry queue with exp-backoff (SQLite-backed)
- Per-message delivery state in bubbles (`✓` `✓✓` `⟳` `⚠`)
- Bunker progress UI wiring
- Window-focus re-AUTH
- NIP-46 batch `get_conversation_keys` RPC
- Android UI parity for AUTH banner
- Manual relay-entry dialog when recipient has no 10050
- NIP-09 deletion of self-copies on kind:10050 rotation
- Fix for pre-existing `RelayLatencyTracker.sweep` CME
