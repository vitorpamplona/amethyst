---
title: "feat: Support LNURL-pay and lightning addresses in send dialog"
type: feat
status: active
date: 2026-05-23
origin: desktopApp/plans/2026-05-23-feat-send-dialog-lnurl-pay-brainstorm.md
deepened: 2026-05-23
---

# feat: Support LNURL-pay and lightning addresses in send dialog

## Enhancement Summary

**Deepened on:** 2026-05-23
**Research agents:** LNURL edge cases, Compose state machine patterns

### Key Improvements from Research
1. `LightningAddressResolver` doesn't parse `minSendable`/`maxSendable`/`commentAllowed` — must fetch and parse endpoint JSON directly in dialog
2. Use `ImportFollowListDialog` sealed class state machine pattern (proven in codebase)
3. Strip `lightning:` URI prefix from pasted input
4. `fetchInvoice()` can be called with just amount (no zap request) — reuse for final invoice fetch

## Overview

Extend the desktop SendDialog to accept LNURL bech32 strings and lightning addresses (`user@domain`) in addition to BOLT11 invoices. Auto-detect input type, resolve LNURL endpoints, and present an amount/comment form before fetching the final BOLT11 invoice and paying via NWC.

## Problem

User pastes an LNURL or lightning address into the send dialog and gets "Unknown chain url: invalid token" error because the dialog only supports BOLT11 invoices.

## Proposed Solution

Single dialog, multi-step flow with auto-detection (see brainstorm).

### Input Detection

```kotlin
fun classifyInput(input: String): PaymentInput {
    val trimmed = input.trim()
        .removePrefix("lightning:")  // Strip lightning: URI prefix
        .trim()
    // 1. BOLT11: starts with lnbc
    LnInvoiceUtil.findInvoice(trimmed)?.let { return PaymentInput.Bolt11(it) }
    // 2. LNURL bech32: starts with lnurl
    if (trimmed.lowercase().startsWith("lnurl")) {
        Lud06().toLnUrlp(trimmed)?.let { return PaymentInput.LnurlPay(it) }
    }
    // 3. Lightning address: user@domain
    if (trimmed.contains("@") && trimmed.contains(".")) {
        val parts = trimmed.split("@")
        if (parts.size == 2) return PaymentInput.LnurlPay("https://${parts[1]}/.well-known/lnurlp/${parts[0]}")
    }
    return PaymentInput.Unknown
}
```

### Dialog State Machine

Follow `ImportFollowListDialog` pattern — sealed class with `LaunchedEffect` auto-transitions.

```kotlin
sealed class SendState {
    data object Idle : SendState()
    data class Resolving(val url: String) : SendState()
    data class NeedsAmount(
        val lnAddress: String,      // original input for fetchInvoice
        val callback: String,
        val minSats: Long,
        val maxSats: Long,
        val commentAllowed: Int,
    ) : SendState()
    data class ReadyToPay(val bolt11: String) : SendState()
    data class FetchingInvoice(val lnAddress: String, val amountSats: Long, val comment: String) : SendState()
    data object Paying : SendState()
    data class Error(val message: String) : SendState()
}
```

### State Transitions via LaunchedEffect

```kotlin
// Auto-resolve LNURL endpoint when entering Resolving state
LaunchedEffect(sendState) {
    val state = sendState
    if (state is SendState.Resolving) {
        // Fetch LNURL-pay JSON using OkHttp directly
        val json = fetchLnurlPayEndpoint(state.url)
        // Parse: callback, minSendable, maxSendable, commentAllowed
        // Transition to NeedsAmount or Error
    }
}

// Auto-fetch invoice when entering FetchingInvoice state
LaunchedEffect(sendState) {
    val state = sendState
    if (state is SendState.FetchingInvoice) {
        val resolver = LightningAddressResolver(DesktopHttpClient.currentClient())
        val result = resolver.fetchInvoice(
            lnAddress = state.lnAddress,
            milliSats = state.amountSats * 1000,
            message = state.comment,
        )
        // Transition to ReadyToPay or Error
    }
}
```

### LNURL Endpoint Parsing (new logic in dialog)

```kotlin
// Fetch and parse LNURL-pay endpoint JSON
// Fields needed: callback, minSendable, maxSendable, commentAllowed
val lnurlp = mapper.readTree(responseBody)
val callback = lnurlp.get("callback")?.asText()
val minSendable = lnurlp.get("minSendable")?.asLong() ?: 1000  // msats
val maxSendable = lnurlp.get("maxSendable")?.asLong() ?: 100_000_000  // msats
val commentAllowed = lnurlp.get("commentAllowed")?.asInt() ?: 0
val isFixed = minSendable == maxSendable
```

### UI Layout per State

| State | Shows |
|-------|-------|
| Idle | Input field ("Payment request or lightning address"), paste button |
| Resolving | Input (disabled), spinner below |
| NeedsAmount | Input (disabled, shows address), amount field w/ "Amount (min - max sats)", comment (if allowed), Pay |
| ReadyToPay | Input (disabled), Pay button |
| FetchingInvoice | Fields disabled, spinner |
| Paying | All disabled, "Paying..." button |
| Error | Inline red copiable text, Retry button resets to appropriate prior state |

### Edge Cases

- **`lightning:lnbc...`** prefix: strip before classification
- **Fixed amount** (`minSendable == maxSendable`): prepopulate, make read-only
- **Amount out of range**: validate client-side before fetching invoice, show inline error
- **Endpoint timeout**: 15s timeout on LNURL fetch, show error
- **Invoice amount mismatch**: `fetchInvoice()` already validates this (line 172)
- **`commentAllowed = 0`**: hide comment field entirely
- **Invalid LNURL bech32**: `Lud06().toLnUrlp()` returns null → stays Unknown

## Files to Modify

| File | Change |
|------|--------|
| `WalletColumnScreen.kt` (SendDialog) | Rewrite to multi-step flow with sealed state machine |

No new files needed. All LNURL utilities already exist in quartz/commons.

## Reusable Code

| Component | Location | Usage |
|-----------|----------|-------|
| `LnInvoiceUtil.findInvoice()` | quartz | Detect BOLT11 |
| `Lud06().toLnUrlp()` | quartz | Decode LNURL bech32 |
| `LightningAddressResolver.fetchInvoice()` | commons | Fetch invoice with amount (no zap request) |
| `DesktopHttpClient.currentClient()` | desktopApp | OkHttpClient |
| `NwcPaymentHandler.payInvoice()` | desktopApp | Pay BOLT11 via NWC |
| `jacksonObjectMapper()` | already imported | Parse LNURL endpoint JSON |

## Acceptance Criteria

- [ ] Pasting a BOLT11 invoice works as before (no regression)
- [ ] Pasting `lightning:lnbc...` works (prefix stripped)
- [ ] Pasting an LNURL bech32 string resolves and shows amount form
- [ ] Pasting a lightning address (user@domain) resolves and shows amount form
- [ ] Fixed-amount LNURL prepopulates amount (read-only)
- [ ] Variable-amount LNURL shows input with min/max hint
- [ ] Comment field appears when endpoint `commentAllowed > 0`
- [ ] Amount validated against min/max before fetching invoice
- [ ] Errors shown inline (copiable), button resets for retry
- [ ] "Paste from Clipboard" works for all input types
- [ ] Label changed from "BOLT11 Invoice" to "Payment request or lightning address"

## Sources

- **Origin brainstorm:** desktopApp/plans/2026-05-23-feat-send-dialog-lnurl-pay-brainstorm.md
- **State machine pattern:** desktopApp/.../ui/ImportFollowListDialog.kt (sealed class + LaunchedEffect)
- `LightningAddressResolver`: commons/src/jvmAndroid/.../LightningAddressResolver.kt:47-234
- `Lud06.toLnUrlp()`: quartz/src/commonMain/.../lightning/Lud06.kt:51-58
- `LnInvoiceUtil.findInvoice()`: quartz/src/commonMain/.../lightning/LnInvoiceUtil.kt:302-307
- Current `SendDialog`: desktopApp/.../ui/wallet/WalletColumnScreen.kt:457-584
