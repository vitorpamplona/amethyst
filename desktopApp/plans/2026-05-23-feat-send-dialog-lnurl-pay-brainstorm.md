# Brainstorm: Send Dialog LNURL-Pay Support

**Date:** 2026-05-23
**Status:** Ready for planning

## What We're Building

Extend the desktop wallet Send dialog to accept LNURL-pay strings and lightning addresses in addition to BOLT11 invoices. The dialog auto-detects input type, resolves LNURL endpoints, and presents an amount/comment form before fetching the final BOLT11 invoice and paying via NWC.

## Why

Users commonly receive payment requests as lightning addresses (`user@domain`) or LNURL bech32 strings, not just raw BOLT11 invoices. The current send dialog rejects these with a confusing "Unknown chain url" error from the NWC wallet.

## Input Types

| Format | Example | Detection |
|--------|---------|-----------|
| BOLT11 | `lnbc210n1p4pr...` | Starts with `lnbc` (regex in `LnInvoiceUtil`) |
| LNURL | `lnurl1dp68gurn...` | Starts with `lnurl` (decode via `Lud06.toLnUrlp`) |
| Lightning address | `user@domain.com` | Contains `@`, split on `@` |

## Dialog Flow

### State Machine

```
Input -> detecting type...
  |
  |- BOLT11 detected -> [Pay Invoice] (current flow, no change)
  |
  |- LNURL/address detected -> resolving endpoint (spinner)...
      |
      |- Fixed amount -> show amount (read-only), optional comment -> [Pay]
      |- Variable amount -> show amount field with min/max hint, optional comment -> [Pay]
      |- Resolution error -> inline error (copiable)
      |
      [Pay] -> fetching invoice (spinner)...
        |
        |- Got BOLT11 -> paying via NWC (spinner)...
        |     |- Success -> close dialog, snackbar
        |     |- Error -> inline error, button resets to [Pay]
        |- Fetch error -> inline error, button resets to [Pay]
```

### UI States

1. **Input** — text field + paste button (current)
2. **Resolving** — spinner below input, input disabled
3. **Amount** — shows endpoint info + amount field (with min/max label) + optional comment field
4. **Paying** — button shows "Paying...", fields disabled
5. **Error** — inline red text (copiable via SelectionContainer), button resets

### Amount Input

- Label: `"Amount (1 - 500,000 sats)"` — populated from `minSendable`/`maxSendable` (converted from msats)
- If fixed amount (`minSendable == maxSendable`): show read-only, prepopulated
- Digits only, validate against range on submit
- Comment field: only visible if `commentAllowed > 0` from endpoint response

## Key Decisions

- **Auto-detect on paste/type** — no explicit "Resolve" button; detect as user types/pastes
- **Single dialog, multi-step** — no separate dialogs for LNURL vs BOLT11
- **Reuse `LightningAddressResolver`** — already handles LNURL endpoint fetch + invoice callback
- **Comment support** — show optional comment field when endpoint allows it
- **Amount in sats** — convert msats from LNURL spec to sats for display
- **Error display** — inline, copiable, same pattern as current SendDialog

## Reusable Code

| Component | Location | Notes |
|-----------|----------|-------|
| `Lud06.toLnUrlp()` | quartz | Decodes LNURL bech32 to URL |
| `LnInvoiceUtil.findInvoice()` | quartz | Detects BOLT11 pattern |
| `LightningAddressResolver.assembleUrl()` | commons | `user@domain` -> endpoint URL |
| `LightningAddressResolver.fetchInvoice()` | commons | Full LNURL-pay flow (fetch endpoint, get invoice) |
| `NwcPaymentHandler.payInvoice()` | desktopApp | Pay BOLT11 via NWC |

## Resolved Questions

- **Input types**: BOLT11 + LNURL + lightning address (all three)
- **Amount UX**: Free text field with min/max hint from endpoint
- **Comments**: Yes, show comment field if `commentAllowed > 0`
- **Flow**: Auto-detect + resolve inline (single dialog, multi-step)

## Open Questions

None — all questions resolved during brainstorm.
