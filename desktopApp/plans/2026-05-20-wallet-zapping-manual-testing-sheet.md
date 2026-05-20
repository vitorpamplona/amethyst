# Desktop Wallet & Zapping — Manual Testing Sheet

**Date:** 2026-05-20
**Branch:** `feat/desktop-wallet-zapping`
**Prerequisites:** NWC-compatible wallet (Alby Hub, Coinos, Phoenix, or LNbits)

---

## Setup

Before testing, get your NWC URI ready:
- **Alby Hub:** Settings > Wallet Connections > + New > copy `nostr+walletconnect://...`
- **Coinos:** Wallet > NWC > Create connection > copy URI
- **Phoenix:** Settings > Wallet Connect > copy URI

Run the desktop app: `./gradlew :desktopApp:run`

---

## A. Wallet Column — No Wallet State

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| A1 | Column appears | Add Wallet column via AppDrawer or sidebar | "No Wallet Connected" screen with wallet icon | |
| A2 | CTA text | Read the empty state | Shows NWC explanation + "Connect Wallet" button | |
| A3 | Connect button opens dialog | Click "Connect Wallet" | ConnectWalletDialog appears | |
| A4 | Cancel dialog | Open connect dialog > Cancel | Dialog closes, still on empty state | |

## B. NWC Connection

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| B1 | Paste valid NWC URI | Paste `nostr+walletconnect://...` into field > Connect | Dialog closes, snackbar "Wallet connected!" | |
| B2 | Paste from clipboard | Copy NWC URI > click "Paste from Clipboard" | URI appears in text field | |
| B3 | Invalid URI rejected | Type `garbage` > Connect | Error: "Invalid NWC URI. Expected: nostr+walletconnect://..." | |
| B4 | Empty URI rejected | Leave field empty | Connect button is disabled (greyed out) | |
| B5 | URI with spaces/newlines | Paste URI with leading/trailing whitespace | Should still connect (or show clear error) | |
| B6 | Wallet info shown | After connecting | Shows relay URL + truncated wallet pubkey | |
| B7 | Persistence | Connect wallet > restart app | Wallet should still be connected on restart | |

## C. Balance

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| C1 | Auto-fetch on connect | Connect wallet | Balance card shows spinner, then sats amount | |
| C2 | Balance formatting | Have >1000 sats | Shows comma-separated (e.g. "12,345 sats") | |
| C3 | Refresh button | Click "Refresh" | Spinner appears, balance updates | |
| C4 | Balance error | Disconnect internet > Refresh | Snackbar: "Balance request timed out" (after 30s) | |
| C5 | Zero balance | Use wallet with 0 sats | Shows "0 sats" (not "--" or error) | |
| C6 | Balance after send | Send payment > observe balance | Balance should NOT auto-update (need manual refresh) | |

## D. Send Payment

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| D1 | Open send dialog | Click "Send" button | SendDialog appears with invoice field | |
| D2 | Paste invoice | Copy BOLT11 > "Paste from Clipboard" | Invoice appears in field | |
| D3 | Pay valid invoice | Paste real invoice > "Pay Invoice" | Spinner appears, then snackbar "Payment successful!", dialog closes | |
| D4 | Pay expired invoice | Paste expired BOLT11 > Pay | Snackbar with error message from wallet | |
| D5 | Pay invalid string | Type `not-an-invoice` > Pay | Error from wallet (check it doesn't crash) | |
| D6 | Cancel during send | Start paying > close dialog | Payment may still complete in background — no crash | |
| D7 | Empty invoice | Leave field empty | "Pay Invoice" button is disabled | |
| D8 | Timeout | Pay invoice while wallet is offline | Snackbar "Payment timed out" after ~60s | |
| D9 | Double-click prevention | Click "Pay Invoice" twice quickly | Button disables after first click, shows "Sending..." | |

## E. Receive Payment

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| E1 | Open receive dialog | Click "Receive" button (outlined) | ReceiveDialog with amount + description fields | |
| E2 | Generate invoice | Enter 100 sats > "Create Invoice" | Dialog title changes to "Invoice Created", shows BOLT11 string | |
| E3 | Copy invoice | Generate invoice > "Copy Invoice" | Invoice copied to clipboard, snackbar "Invoice copied!" | |
| E4 | Amount validation | Type letters in amount field | Only digits accepted | |
| E5 | Zero amount | Enter 0 > Create Invoice | Nothing happens (button should be disabled for blank, but 0 may pass — note behavior) | |
| E6 | Large amount | Enter 1000000 sats | Invoice generated successfully (or wallet-specific limit error) | |
| E7 | Description | Enter amount + description > Create | Invoice created (verify description doesn't break anything) | |
| E8 | Timeout | Generate while wallet offline | Snackbar "Invoice request timed out" | |
| E9 | Close after generate | Generate > "Close" | Dialog closes cleanly | |
| E10 | Pay the invoice | Copy generated invoice > pay from external wallet | Payment should succeed (verify with balance refresh) | |

## F. Disconnect Wallet

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| F1 | Disconnect | Click red "Disconnect" text | Snackbar "Wallet disconnected", returns to empty state | |
| F2 | Balance clears | Disconnect | Balance resets to null (shows empty state, not stale balance) | |
| F3 | Reconnect | Disconnect > Connect again | Full flow works, balance fetches again | |
| F4 | Persistence after disconnect | Disconnect > restart app | Should remain disconnected | |

## G. Zapping Notes (NoteActionsRow)

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| G1 | Quick zap (left-click) | With wallet connected, left-click zap icon on a note | Spinner on icon, then ZapFeedback.Success, icon turns primary color | |
| G2 | Quick zap amount | Left-click zap | Should zap 21 sats (first preset) | |
| G3 | Custom zap (right-click) | Right-click zap icon | ZapAmountDialog opens | |
| G4 | Preset amounts | Open zap dialog | Shows chips: 21, 100, 500, 1k, 5k, 10k | |
| G5 | Select preset | Click "500" chip > Zap | Zaps 500 sats | |
| G6 | Custom amount | Click "Custom" > type 42 > Zap | Zaps 42 sats | |
| G7 | Zap types | Open dialog | Shows Public/Private/Anonymous filter chips | |
| G8 | Zap type selection | Select "Private" > note label change | Label changes to "Private message (only recipient sees)" | |
| G9 | Zap message | Type message > Zap | Zap includes message (verify in receipts) | |
| G10 | No wallet — external | Without wallet connected, left-click zap | Opens ZapAmountDialog (not quick zap) | |
| G11 | No lightning address | Zap a user with no LN address | ZapFeedback.NoLightningAddress feedback | |
| G12 | Zap counter updates | After successful zap | Zap amount on note should reflect new total | |

**NOTE:** ZapType PRIVATE/ANONYMOUS is wired to the dialog but the TODO at line 899 says it's not yet passed to ZapAction. Verify Public works; Private/Anonymous may silently fall back to Public.

## H. Zap Receipts Dialog

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| H1 | Open receipts | Click the zap amount text on a note that has zaps | ZapReceiptsDialog opens | |
| H2 | Receipt content | View receipts | Shows sender name, amount, message | |
| H3 | Metadata loading | Receipts from unknown users | Shows spinner while loading, then names appear | |
| H4 | Sorting | Multiple zaps | Sorted by amount descending | |
| H5 | Overflow | Note with >10 zaps | Shows top 10 + "and N more..." | |
| H6 | Empty state | Note with 0 zaps | "No zaps yet" message | |
| H7 | Close | Click "Close" | Dialog dismisses | |

## I. Edge Cases & Error Handling

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| I1 | Network loss mid-operation | Start a payment > disconnect wifi | Timeout after configured period, no crash | |
| I2 | Wallet column + no relays | Disconnect all relays > try balance | Graceful error or timeout | |
| I3 | Multiple rapid zaps | Quick-click zap on 3 different notes fast | Each processes independently, no double-spend crash | |
| I4 | Re-zap same note | Zap a note, then zap it again | Second zap should work (stacking zaps is normal) | |
| I5 | Very long NWC URI | Paste extremely long URI | TextField handles it, no UI overflow | |
| I6 | Column resize | Resize wallet column narrower/wider | UI adapts (max 360dp content width) | |
| I7 | Snackbar stacking | Trigger multiple snackbars quickly | No crash, messages queue properly | |

## J. Cross-Feature

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| J1 | Wallet + other columns | Have Home + Wallet columns side by side | Both function, zaps from Home use connected wallet | |
| J2 | React/Repost still work | Like, repost, bookmark a note | All work independently of wallet state | |
| J3 | Copy note/event links | Use overflow menu on a note | Copies correct nostr: links to clipboard | |
| J4 | Bookmark dialog | Click bookmark icon | Public/Private dialog appears, bookmarking works | |

---

## Known Limitations / TODOs

- Private/Anonymous zap types — dialog exists but not wired to ZapAction (line 899 TODO)
- No transaction history screen yet (Phase 2)
- No keyboard shortcut for zapping (deferred to Phase 2b)
- Balance doesn't auto-update after send/receive (manual refresh required)
- Metadata fetch uses GlobalScope (line 504, 1142) — works but not ideal for structured concurrency

## Test Wallets

| Wallet | Best For | Notes |
|--------|----------|-------|
| **Alby Hub** | Full NWC testing | Self-hosted, full RPC support |
| **Coinos** | Quick setup | Custodial, easy NWC URI |
| **Phoenix** | Real mobile wallet | Good for realistic testing |
| **Mutiny (RIP)** | N/A | Shut down — don't use |

## Results Summary

| Section | Total | Pass | Fail | Skip | Notes |
|---------|-------|------|------|------|-------|
| A. No Wallet | 4 | | | | |
| B. Connection | 7 | | | | |
| C. Balance | 6 | | | | |
| D. Send | 9 | | | | |
| E. Receive | 10 | | | | |
| F. Disconnect | 4 | | | | |
| G. Zapping | 12 | | | | |
| H. Receipts | 7 | | | | |
| I. Edge Cases | 7 | | | | |
| J. Cross-Feature | 4 | | | | |
| **TOTAL** | **70** | | | | |
