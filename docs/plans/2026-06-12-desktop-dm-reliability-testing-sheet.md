# Desktop DM Reliability — Testing Sheet

**Branch:** `feat/desktop-dm-reliability`
**Date:** 2026-06-12
**Plan:** [docs/plans/2026-06-10-feat-desktop-dm-reliability-plan.md](2026-06-10-feat-desktop-dm-reliability-plan.md)
**Tester:**

## Scope

17 commits across `quartz` / `commons` / `desktopApp`. Net ~1,797 LOC (incl. ~600 LOC tests).
The branch ships:

1. NIP-42 AUTH end-to-end on desktop (today: nothing) — tier-1 auto-sign, tier-2 banner
2. P0 security fix: NIP-17 sends no longer fall back to user's connected relays
3. Dedicated unauthenticated NostrClient for kind:10050 indexer probes (no identity-key leak)
4. NIP-17 relay hint on gift-wrap p-tag (correct per spec)
5. Bunker concurrency cap (Semaphore(4)) in NIP17Factory
6. `auth-required:` carved out of outbox try cap
7. Drop `since` from kind:1059 subscription
8. Compose-observable AUTH state, `SigningOpState.Progress` variant
9. Per-account AUTH approval persistence via `java.util.prefs.Preferences`

---

## Pre-test setup

- [ ] `git -C .worktrees/feat/desktop-dm-reliability log --oneline ^origin/main` shows 17 commits
- [ ] Note current `~/.amethyst/accounts/<pubkey8>/` paths so they can be inspected after the run
- [ ] Run `defaults read /Library/Preferences/com.apple.security ...` baseline — irrelevant; `Preferences.userRoot()` lives in `~/Library/Preferences/com.apple.java.util.prefs.plist` on macOS, `~/.java/.userPrefs` on Linux. Note path for later verification.

---

## A. Automated verification (Claude can run these)

**Run on 2026-06-12 against worktree at HEAD `4ed0ff241`.**

### A1: Compile every module
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| A1.1 | `./gradlew :quartz:compileKotlinJvm` | BUILD SUCCESSFUL, no errors | ✅ | Only pre-existing `BirthdayTolerantSerializer` opt-in warning |
| A1.2 | `./gradlew :commons:compileKotlinJvm` | BUILD SUCCESSFUL | ✅ | |
| A1.3 | `./gradlew :desktopApp:compileKotlin` | BUILD SUCCESSFUL | ✅ | |
| A1.4 | `./gradlew :amethyst:compileFdroidDebugKotlin` (Android) | BUILD SUCCESSFUL — commons changes don't break Android | ✅ | Note: task is `:amethyst:compileFdroidDebugKotlin`, not the non-flavour `compileDebugKotlin` originally listed |
| A1.5 | `./gradlew :cli:compileKotlin` | BUILD SUCCESSFUL — quartz API changes don't break amy | ✅ | |

### A2: Unit + integration tests
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| A2.1 | `./gradlew :quartz:jvmTest` | All pass; `PoolEventOutboxStateTest` (4) + `GiftWrapRelayHintTest` (3) included | ✅ | |
| A2.2 | `./gradlew :commons:jvmTest` | All pass; `AuthApprovalPolicyTest` (8) + `AuthApprovalEndToEndTest` (4) + `DmInboxRelayResolverTest` (8) included | ✅ | |
| A2.3 | `./gradlew :desktopApp:test` | All pass | ✅ | |
| A2.4 | `./gradlew :amethyst:testFdroidDebugUnitTest` | All pass — no Android regression from commons changes | ✅ | |

### A3: Static analysis
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| A3.1 | `./gradlew spotlessCheck` | All formatted | ✅ | |
| A3.2 | Pre-commit hook runs on every commit (already exercised 17 times this branch) | Hook runs spotlessCheck + tests, passes | ✅ | |

### A4: Package build
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| A4.1 | `./gradlew :desktopApp:createDistributable -Pcompose.desktop.packaging.checkJdkVendor=false` | Produces `Amethyst.app`; new code (`DesktopAuthCoordinator`, `AuthApprovalPolicy`, `AuthApprovalBanner`, `RelayAuthSnapshot`, `DmInboxRelayResolver`) inside the bundled JARs | ✅ | `packageDistributionForCurrentOS` blocked on local Homebrew JDK vendor check — pre-existing env issue, not a branch regression. `createDistributable` with the flag works fine. |
| A4.2 | Launch the built `Amethyst.app` for 10s | No crash, no exceptions in log | ✅ | Confirmed: `DesktopAuthCoordinator` + `indexerClient` + `DmInboxRelayResolver` + banner mount all initialize cleanly. Only pre-existing VLC plugin warnings in stderr. |

### A5: Branch integrity
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| A5.1 | `git log --grep "Co-Authored-By" feat/desktop-dm-reliability ^origin/main \| wc -l` | 0 — no Claude footer leaked into commits | ✅ | 0 matches |
| A5.2 | All commits GPG-signed: `git log --pretty="%G?" feat/desktop-dm-reliability ^origin/main \| sort -u` | Only `G` (good signature) | ✅ | Only `G` |
| A5.3 | No `--no-verify` or `--no-gpg-sign` flags in reflog | clean | ✅ | Hook passed on every commit; one GPG retry mid-session resolved by re-unlock, no flags used |

---

## B. Manual desktop verification (human required)

**Pre-step:** `./gradlew :desktopApp:run` on the worktree. Use at least two test accounts: one nsec, one bunker (`bunker://...` from nsec.app or Amber).

### B1: AUTH end-to-end — tier 1 (own DM-inbox relay)
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B1.1 | Log in with nsec account A. Verify the user has a `kind:10050` published with at least one relay (e.g. `wss://relay.nos.social`). | Account loads; feed shows | | |
| B1.2 | Open Settings → Relays. Confirm one of A's DM-inbox relays is in the connected set. | DM relay listed, status connected | | |
| B1.3 | Trigger an AUTH-walled action: ideally send a DM TO yourself (NIP-17). Watch DevTools / log output (or `~/.amethyst/logs/` if Tor is on). | Mock relay should send `AUTH ...`; client signs + replies; outbox publish OK | | |
| B1.4 | Confirm NO banner appears for tier-1 relays | Banner stays empty | | |
| B1.5 | Inspect Preferences node: `defaults read com.vitorpamplona.amethyst.desktop.auth.<full-pubkey>` (macOS) OR `cat ~/.java/.userPrefs/com/vitorpamplona/amethyst/desktop/auth/<full-pubkey>/prefs.xml` (Linux) | Empty / does not exist yet — tier-1 doesn't write | | |

### B2: AUTH end-to-end — tier 2 (unknown relay, banner)
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B2.1 | Add an AUTH-required relay to the user's settings that is NOT in their `kind:10050` (e.g. `wss://pyramid.fiatjaf.com` if accessible, or any test relay with `auth-required` policy). | Relay appears in list | | |
| B2.2 | Send a DM that would route through that relay (e.g. user it's listed for). Or just connect and let the relay challenge. | Yellow/inline AUTH banner appears at top of content area with relay URL + `[Once] [Always] [Never]` | | |
| B2.3 | Click `[Once]` | Banner dismisses; AUTH proceeds for this session only | | |
| B2.4 | Restart app, repeat connection — banner appears again | banner returns (ONCE was session-only) | | |
| B2.5 | This time click `[Always]` | Banner dismisses; AUTH proceeds | | |
| B2.6 | Restart app. Banner does NOT appear for this relay. | tier-2 → tier-1 once persisted | | |
| B2.7 | Inspect Preferences node: should contain `relay.example.url=ALWAYS` | persistence verified | | |
| B2.8 | Block a different relay via `[Never]` | Future AUTH challenges from it are silently dropped (no banner, no AUTH event sent) | | |
| B2.9 | Confirm clicking `[Never]` writes `relay.url=BLOCKED` | persistence verified | | |

### B3: AUTH banner — multiple concurrent challenges
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B3.1 | Connect to 3 different AUTH-required relays back-to-back, none auto-approved | 3 banner rows stack vertically | | |
| B3.2 | Resolve middle one with `[Once]` | Only that row dismisses; other 2 remain | | |
| B3.3 | Trigger 5 simultaneous AUTH challenges from different relays | First 3 visible inline; "+2 more relays pending approval" row at bottom | | |

### B4: Banner lifecycle — logout / account switch
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B4.1 | With at least 2 pending banner rows visible, log out | Banners disappear; coordinator's `onLogout` completes all pending deferreds with BLOCKED | | |
| B4.2 | Log in to account B (different pubkey); trigger same AUTH challenges | Banners reappear because B has no persisted approvals from A | | |
| B4.3 | Verify B's `[Always]` writes to B's Preferences node, NOT A's | per-account isolation | | |
| B4.4 | Account A's persisted approvals still intact: log back into A → no banner for previously-approved relays | persistence stable across switches | | |

### B5: NIP-17 send — security fix (no-10050 case)
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B5.1 | Pick a recipient who has NEVER published a kind:10050 (rare in practice; can fabricate a npub) | account known, no DM inbox advertised | | |
| B5.2 | Try to send a DM | `DmSendTracker` shows "No relays available" failure briefly | | |
| B5.3 | Inspect outgoing socket activity (e.g. Wireshark filtered to `wss://`) | NO gift wrap is published anywhere — neither to user's outbox nor general relays | | |
| B5.4 | DesktopRelayConnectionManager metrics: no spike for this send | confirmed | | |

### B6: NIP-17 send — DmInboxRelayResolver indexer fan-out
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B6.1 | Pick a recipient who HAS a kind:10050 but whose 10050 is NOT in your LocalCache (fresh, never-DM'd contact) | empty LocalCache for that user | | |
| B6.2 | Click compose DM to them | Resolver consults indexer relays; brief delay (sub-second to ~3s) | | |
| B6.3 | Inspect network: indexer client (port-share with primary client?) makes one-shot queries to `relay.nos.social`, `relay.damus.io`, `nos.lol`, `relay.nostr.band`, `purplerelay.com` | indexer fan-out confirmed | | |
| B6.4 | **CRITICAL — F-01**: verify NO AUTH event was sent on the indexer client even if any indexer challenged | confirms unauth client. If wrong, that's a security regression. Easy check: filter pcap for kind:22242 on the indexer connections. | | |
| B6.5 | Send succeeds; recipient's actual DM-inbox relay receives the wrap | normal NIP-17 delivery | | |
| B6.6 | Immediately compose another DM to the same recipient | Resolver hits LRU cache; no second indexer call | | |
| B6.7 | Wait > 1 hour (or set system clock forward); compose again | Resolver fans out again (cache expired) | | |

### B7: NIP-17 send — group DM rumor coherence
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B7.1 | Create a 3-recipient group DM | NIP17Factory builds 3 wraps + 1 self-copy | | |
| B7.2 | Inspect the rumor inside each seal (use a Nostr event inspector or relay log) — `rumor.id` matches across all 3 wraps | shared rumor_created_at confirmed | | |
| B7.3 | One recipient sends a reaction (`+`) on their device | reaction targets shared `rumor.id`; all participants see it | | |
| B7.4 | Verify `wrap.created_at` is randomized per-wrap (within 2 days past) | seal/wrap timestamps stay independent | | |

### B8: NIP-17 relay hint on wrap p-tag
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B8.1 | Send a DM. Capture the published kind:1059 event via your DM-inbox relay UI or a tool like `nostr-tool`. | event captured | | |
| B8.2 | Inspect the `p` tag on the wrap | shape is `["p", recipient_pubkey, relay_url]` — relay_url is recipient's primary DM relay if known, else 2-element shape | | |
| B8.3 | Verify the SEAL (kind 13) does NOT carry the hint | NIP-17 spec compliance | | |

### B9: Outbox AUTH carve-out
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B9.1 | Pre-condition: relay R demanding AUTH. User has account that needs to AUTH. | configured | | |
| B9.2 | Publish a note to R while NOT yet authenticated | Relay replies `auth-required: ...` | | |
| B9.3 | Inspect outbox state: event remains queued for relay R | NOT discarded after 1 try | | |
| B9.4 | Watch for AUTH event sign and submission (tier-1 auto-sign or banner approval) | AUTH OK | | |
| B9.5 | Original note re-publishes successfully on R after AUTH | E.g. via syncFilters() | | |
| B9.6 | Repeat: send 5 notes in rapid succession during AUTH window | All 5 re-publish after AUTH, none silently dropped | | |

### B10: Bunker concurrency cap
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B10.1 | Log in with a NIP-46 bunker account | bunker connected | | |
| B10.2 | Send a 5-recipient group DM | NIP17Factory caps at 4 concurrent bunker RPCs | | |
| B10.3 | Inspect bunker request timing (nsec.app / Amber log) | At most 4 in-flight at any moment | | |
| B10.4 | Compare to a 5-recipient group DM with a local nsec account | local-nsec runs all 5 in parallel; no semaphore overhead | | |
| B10.5 | Verify DM still delivers correctly to all recipients | functional parity | | |

### B11: kind:1059 subscription — no `since` filter
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B11.1 | Inspect the actual REQ message sent for kind:1059 subscription (e.g. via relay debug or `nostr-tool` proxy) | filter has `kinds:[1059]`, `#p:[user_pubkey]`, NO `since` field | | |
| B11.2 | Have someone send you a DM with `created_at = now() - 1.5 days` (use a custom client) | wrap arrives, unread badge increments | | |
| B11.3 | Send self a DM, restart app, verify still loaded | persistent dedupe still working | | |

### B12: SigningOpState.Progress
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| B12.1 | Existing zap / sign flows: trigger a sign, inspect status bar | "Waiting for signer approval... (Ns)" — unchanged | | |
| B12.2 | (Manual / future) Set `SigningState.updateProgress(2, 5)` from somewhere | Status bar shows "Signing (2 of 5)" | | |

---

## C. Cross-platform sanity (manual, Android only if you have a device)

### C1: Android — commons inheritance check
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| C1.1 | Build and install `./gradlew :amethyst:installDebug` | Android app launches | | |
| C1.2 | Send a NIP-17 DM from Android | works as before (Android doesn't yet use DmInboxRelayResolver / DesktopAuthCoordinator) | | |
| C1.3 | `User.dmInboxRelays()` behaviour: unchanged on Android | no regression | | |
| C1.4 | Confirm Android signing still goes through Android-only `AuthCoordinator` (not the new desktop one) | platform separation intact | | |

---

## D. Security audit (mostly Claude-verifiable)

### D1: Code/git inspection
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| D1.1 | `grep -rn "connectedRelays.value" desktopApp/.../DesktopIAccount.kt` | Zero remaining matches in NIP-17 paths (NIP-04 path may keep its broadcast-to-connected behaviour by design) | ✅ | 3 hits: lines 113-114 (`DesktopAccountRelays` defaults — unrelated), line 173 (NIP-04 broadcast — intentional). All three NIP-17 send paths use `resolveDmInboxRelaysStrict`. |
| D1.2 | `grep -rn "RelayAuthenticator" desktopApp/` | Only `DesktopAuthCoordinator` references; no other coordinator | ✅ | Single construction site: `DesktopAuthCoordinator.kt:106`. Doc-strings reference it elsewhere; no other coordinator class. |
| D1.3 | DmInboxRelayResolver uses a NostrClient distinct from `relayManager.client` | verified in Main.kt | ✅ | Main.kt:848-853 constructs `indexerClient = NostrClient(BasicOkHttpWebSocket.Builder(...))` separately; never passed `RelayAuthenticator`. |
| D1.4 | `grep -rn "dmInboxOrFallback" commons/ desktopApp/` | Zero matches — resolver uses `lists.dmInbox` strict | ✅ | Zero matches outside the Quartz definition site. |

### D2: Threat checks
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| D2.1 | Inspect a single wrap's `p` tag: confirm only ONE pubkey listed (the recipient), no leakage of group members | wrap p-tag is single-recipient | | |
| D2.2 | Logout: verify `PreferencesAuthApprovalStore.clear()` is called for each account | (currently called via `DesktopAuthCoordinator.onLogout`'s `tearDownLocked`; but does it call `store.clear()`? Looking at code: it does NOT call clear — see follow-up below.) | | This is a known gap. See note. |

### Known gap surfaced during sheet writing

**D2.2**: `DesktopAuthCoordinator.onLogout` tears down the authenticator and completes pending deferreds, but does **not** call `store.clear()`. This is by design (`ALWAYS`/`BLOCKED` decisions persist across login sessions for the same account, scoped by `account_pubkey` in the Preferences node). Account deletion (separate from logout) is the trigger that should call `clear()`. **Follow-up:** verify Amethyst Desktop account-delete path calls `PreferencesAuthApprovalStore(pubKey).clear()`. Not in scope of this branch.

---

## E. Sign-off

| Section | Pass? | Notes |
|---|---|---|
| A — Automated | | |
| B — Desktop manual | | |
| C — Android sanity | | |
| D — Security audit | | |
| **Overall** | | |

---

## What this branch does NOT ship (out-of-scope for testing)

These were captured in the deepening synthesis and are explicit follow-ups, NOT regressions:

- Persistent retry queue with exp-backoff (SQLite-backed) — substrate not yet built
- Per-message delivery state in chat bubbles (`✓` `✓✓` `⟳` `⚠`) — `DmSendTracker` is still global
- Bunker progress UI wiring — `SigningOpState.Progress` substrate landed but no caller yet emits per-step counts
- Window-focus re-AUTH coordinator — replaced with lazy reactive AUTH per deepening
- NIP-46 batch `get_conversation_keys` RPC — spec PR proposed in plan, no implementation
- Android UI parity for AUTH banner — Android stays on its existing unconditional `AuthCoordinator`
- Manual relay-entry dialog when recipient has no 10050 — replaced with Snackbar-equivalent "no relays" failure today
- NIP-09 deletion of self-copies on kind:10050 rotation — release-note disclosure only
