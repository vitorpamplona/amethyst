# Multi-Account Testing Sheet

**Branch:** `feat/desktop-multi-account`
**Date:** 2026-04-28
**Tester:**

## Pre-test Setup
- [ ] Clean build: `./gradlew :desktopApp:clean :desktopApp:run`
- [ ] Delete `~/.amethyst/accounts.json.enc` if exists (fresh state)

---

## T1: Fresh Launch (No Accounts)
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 1.1 | Launch app | Login screen shown | | |
| 1.2 | Check sidebar | Person icon visible at top of sidebar (replaces "A" logo) | | |
| 1.3 | Click person icon | Dropdown shows "No accounts" + "Add Account" | | |

## T2: Login with nsec (First Account)
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 2.1 | Paste nsec on login screen | Login succeeds, feed loads | | |
| 2.2 | Click "Save" (if prompted) | Account saved to encrypted storage | | |
| 2.3 | Check `~/.amethyst/accounts.json.enc` exists | File exists, not readable as plaintext | | |
| 2.4 | Click person icon in sidebar | Dropdown shows 1 account with checkmark + "Add Account" | | |
| 2.5 | Verify account npub shown | First 16 chars of npub visible | | |

## T3: Login with Bunker (Second Account)
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 3.1 | Log out current account | Login screen shown | | |
| 3.2 | Login with bunker:// URI | Bunker connection succeeds, feed loads | | |
| 3.3 | Click person icon | Dropdown shows 2 accounts (nsec + bunker) | | |
| 3.4 | Bunker account shows "Bunker" label | Signer type label visible under npub | | |
| 3.5 | Active account has checkmark | Only current account checked | | |

## T4: Login with npub (View-Only, Third Account)
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 4.1 | Log out, paste npub on login | Login succeeds (read-only mode) | | |
| 4.2 | Click person icon | Dropdown shows 3 accounts | | |
| 4.3 | View-only shows label | "View only" label visible | | |

## T5: Account Switching
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 5.1 | Click different account in dropdown | App switches, feed reloads | | |
| 5.2 | Check relay reconnection | Relay indicators show reconnection activity | | |
| 5.3 | Switch back to first account | Feed loads with first account's data | | |
| 5.4 | Verify active checkmark moves | Checkmark on newly active account | | |
| 5.5 | Switch to bunker account | Bunker heartbeat indicator appears | | |
| 5.6 | Switch away from bunker | Heartbeat stops, NIP-46 cleaned up | | |

## T6: Persistence Across Restarts
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 6.1 | Close app (Cmd+Q) | Clean exit | | |
| 6.2 | Relaunch app | Auto-login to last active account | | |
| 6.3 | Click person icon | All previously added accounts still listed | | |
| 6.4 | Switch to different account, restart | Restarts into the account you switched to | | |

## T7: Account Removal
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 7.1 | Remove a non-active account | Account disappears from list, active stays | | |
| 7.2 | Remove the active account | App switches to next account or shows login | | |
| 7.3 | Remove all accounts | Login screen shown | | |

## T8: Encrypted Storage Security
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 8.1 | `cat ~/.amethyst/accounts.json.enc` | Binary/encrypted data, no plaintext npubs | | |
| 8.2 | `hexdump -C ~/.amethyst/accounts.json.enc | head` | No readable strings | | |
| 8.3 | File permissions: `ls -la ~/.amethyst/accounts.json.enc` | `-rw-------` (owner only) | | |

## T9: Edge Cases
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 9.1 | Login with invalid nsec | Error message, stays on login screen | | |
| 9.2 | Login with same account twice | Updates existing entry, no duplicate | | |
| 9.3 | Rapid account switching (click fast) | No crash, settles on last clicked | | |
| 9.4 | Switch while relays still connecting | Handles gracefully, no stuck state | | |

## T10: UI/UX
| # | Step | Expected | Pass? | Notes |
|---|------|----------|-------|-------|
| 10.1 | Dropdown positioning | Opens to the right of 48dp sidebar | | |
| 10.2 | Click outside dropdown | Dropdown dismisses | | |
| 10.3 | "Add Account" button | Visible at bottom with divider | | |
| 10.4 | Sidebar icon size | Person icon fits within 48dp width | | |

---

## Known Limitations (Not Bugs)
- "Add Account" button is wired but has no dialog yet (TODO)
- No keyboard shortcut (Cmd+Shift+A) yet
- No background notification counts yet
- No system tray integration yet
- Account removal not yet exposed in UI (only programmatic)
- NWC connection is global, not per-account

## Test Environment
- OS:
- JDK version:
- Amethyst version: 1.08.0 (feat/desktop-multi-account)
