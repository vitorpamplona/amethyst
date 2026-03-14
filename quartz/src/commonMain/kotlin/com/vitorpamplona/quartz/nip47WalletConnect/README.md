# NIP-47 Wallet Connect (Quartz)

Quartz implementation of [NIP-47](https://github.com/nostr-protocol/nips/blob/master/47.md) — Nostr
Wallet Connect (NWC). This module provides everything needed to build both **wallet client apps**
(like Amethyst) and **wallet service backends** (like Alby Hub).

## Quick Start — Wallet Client

Use `Nip47Client` for a high-level API that handles URI parsing, signer creation,
event building, filter construction, and response decryption:

```kotlin
// 1. Create client from NWC URI
val client = Nip47Client.fromUri("nostr+walletconnect://pubkey?relay=...&secret=...")

// 2. Build request events — one method per NWC command
val payEvent = client.payInvoice("lnbc50n1...")
val balanceEvent = client.getBalance()
val infoEvent = client.getInfo()
val invoiceEvent = client.makeInvoice(amount = 50000L, description = "Coffee")
val txEvent = client.listTransactions(limit = 20)

// 3. Send event to client.relayUrl via your relay connection
// 4. Subscribe using client.responseFilter(payEvent.id) for the response

// 5. When response arrives, parse it
val response = client.parseResponse(responseEvent)
when (response) {
    is PayInvoiceSuccessResponse -> println("Paid! Preimage: ${response.result?.preimage}")
    is GetBalanceSuccessResponse -> println("Balance: ${response.result?.balance} msats")
    is NwcErrorResponse -> println("Error: ${response.error?.message}")
}

// Filter helpers for relay subscriptions
val filter = client.responseFilter(payEvent.id)       // Filter for a specific response
val allFilter = client.allResponsesFilter()            // Filter for all responses
val notifFilter = client.notificationsFilter()         // Filter for notifications
val walletInfo = client.infoFilter()                   // Filter for wallet info event
```

## Quick Start — Wallet Service

Use `Nip47Server` to build a wallet service that receives requests and sends responses:

```kotlin
// 1. Create server
val server = Nip47Server(
    signer = walletSigner,
    capabilities = listOf(NwcMethod.PAY_INVOICE, NwcMethod.GET_BALANCE, NwcMethod.GET_INFO),
)

// 2. Publish capabilities (kind 13194)
val infoTemplate = server.buildInfoEvent()
// Sign and send: walletSigner.sign(infoTemplate)

// 3. Subscribe using server.requestsFilter() on your relay

// 4. When a request arrives, parse and respond
val request = server.parseRequest(requestEvent)
when (request) {
    is GetBalanceMethod -> {
        val response = server.respondGetBalance(requestEvent, balance = 2100000L)
        // Send response to relay
    }
    is PayInvoiceMethod -> {
        // Process payment, then:
        val response = server.respondPayInvoice(requestEvent, preimage = "abc123")
        // Or on error:
        val error = server.respondError(requestEvent, NwcErrorCode.PAYMENT_FAILED, "Route not found")
    }
    is MakeInvoiceMethod -> {
        val tx = NwcTransaction(type = NwcTransactionType.INCOMING, invoice = "lnbc...")
        val response = server.respondMakeInvoice(requestEvent, tx)
    }
}

// 5. Send notifications
val notifEvent = server.notifyPaymentReceived(clientPubkey, transaction)
```

## Architecture

```
nip47WalletConnect/
├── Nip47Client.kt                 # High-level client API (URI → requests → responses)
├── Nip47Server.kt                 # High-level server API (requests → responses → notifications)
├── Nip47WalletConnect.kt          # URI parsing (nostr+walletconnect://)
├── Request.kt                     # All 13 NWC request methods + params
├── Response.kt                    # All response types (success + error)
├── Notification.kt                # Wallet notification types
├── NwcMethod.kt                   # Method name constants
├── NwcErrorCode.kt                # Error codes enum + NwcError
├── NwcTransaction.kt              # Transaction, state, budget, TLV models
├── NwcInfoEvent.kt                # Kind 13194 — wallet capabilities
├── LnZapPaymentRequestEvent.kt    # Kind 23194 — client → wallet request
├── LnZapPaymentResponseEvent.kt   # Kind 23195 — wallet → client response
├── NwcNotificationEvent.kt        # Kind 23197 — wallet → client notification
├── NostrWalletConnectRequestCache.kt   # Request decryption cache
├── NostrWalletConnectResponseCache.kt  # Response decryption cache
└── tags/
    ├── EncryptionTag.kt            # "encryption" tag parsing
    └── NotificationsTag.kt         # "notifications" tag parsing
```

## Event Kinds

| Kind  | Class                        | Direction       | Purpose              |
|-------|------------------------------|-----------------|----------------------|
| 13194 | `NwcInfoEvent`               | Wallet → Relay  | Service capabilities |
| 23194 | `LnZapPaymentRequestEvent`   | Client → Wallet | NWC request          |
| 23195 | `LnZapPaymentResponseEvent`  | Wallet → Client | NWC response         |
| 23196 | `NwcNotificationEvent`       | Wallet → Client | Notification (NIP-04, legacy) |
| 23197 | `NwcNotificationEvent`       | Wallet → Client | Notification (NIP-44) |

## Supported Methods

| Method               | `Nip47Client` method        | Request Class             | Success Response Class            |
|----------------------|-----------------------------|---------------------------|-----------------------------------|
| `pay_invoice`        | `payInvoice()`              | `PayInvoiceMethod`        | `PayInvoiceSuccessResponse`       |
| `pay_keysend`        | `payKeysend()`              | `PayKeysendMethod`        | `PayKeysendSuccessResponse`       |
| `make_invoice`       | `makeInvoice()`             | `MakeInvoiceMethod`       | `MakeInvoiceSuccessResponse`      |
| `lookup_invoice`     | `lookupInvoiceByHash/ByInvoice()` | `LookupInvoiceMethod`| `LookupInvoiceSuccessResponse`    |
| `list_transactions`  | `listTransactions()`        | `ListTransactionsMethod`  | `ListTransactionsSuccessResponse` |
| `get_balance`        | `getBalance()`              | `GetBalanceMethod`        | `GetBalanceSuccessResponse`       |
| `get_info`           | `getInfo()`                 | `GetInfoMethod`           | `GetInfoSuccessResponse`          |
| `get_budget`         | `getBudget()`               | `GetBudgetMethod`         | `GetBudgetSuccessResponse`        |
| `sign_message`       | `signMessage()`             | `SignMessageMethod`       | `SignMessageSuccessResponse`      |
| `create_connection`  | `buildRequest()`            | `CreateConnectionMethod`  | `CreateConnectionSuccessResponse` |
| `make_hold_invoice`  | `makeHoldInvoice()`         | `MakeHoldInvoiceMethod`   | `MakeHoldInvoiceSuccessResponse`  |
| `cancel_hold_invoice`| `cancelHoldInvoice()`       | `CancelHoldInvoiceMethod` | `CancelHoldInvoiceSuccessResponse`|
| `settle_hold_invoice`| `settleHoldInvoice()`       | `SettleHoldInvoiceMethod` | `SettleHoldInvoiceSuccessResponse`|

Any method can also return `NwcErrorResponse` or (for `pay_invoice`) `PayInvoiceErrorResponse`.

## Low-Level API

The high-level `Nip47Client` and `Nip47Server` classes wrap the lower-level event
builders. You can use these directly if you need more control.

### Wallet Client (Low-Level)

#### 1. Parse the NWC Connection URI

```kotlin
val uri = "nostr+walletconnect://b889ff5b...?relay=wss%3A%2F%2Frelay.damus.io&secret=71a8c14c..."
val nwcConfig = Nip47WalletConnect.parse(uri)
```

Supported URI schemes: `nostr+walletconnect://`, `nostrwalletconnect://`,
`amethyst+walletconnect://`

#### 2. Create the Client Signer

```kotlin
val clientSigner = NostrSignerInternal(
    KeyPair(nwcConfig.secret!!.hexToByteArray())
)
```

#### 3. Build and Send Requests

```kotlin
val balanceRequest = GetBalanceMethod.create()
val event = LnZapPaymentRequestEvent.createRequest(
    request = balanceRequest,
    walletServicePubkey = nwcConfig.pubKeyHex,
    signer = clientSigner,
)
// Send `event` to `nwcConfig.relayUri`
```

To use NIP-44 encryption instead of NIP-04:

```kotlin
val event = LnZapPaymentRequestEvent.createRequest(
    request = GetInfoMethod.create(),
    walletServicePubkey = nwcConfig.pubKeyHex,
    signer = clientSigner,
    useNip44 = true,
)
```

#### 4. Receive and Parse Responses

Subscribe to kind `23195` events on the NWC relay, filtered by the wallet
service pubkey and the request event ID:

```kotlin
val response: Response = responseEvent.decrypt(clientSigner)

when (response) {
    is GetBalanceSuccessResponse -> {
        val balanceSats = (response.result?.balance ?: 0L) / 1000L
    }
    is PayInvoiceSuccessResponse -> {
        val preimage = response.result?.preimage
    }
    is NwcErrorResponse -> {
        val errorMessage = response.error?.message
    }
}
```

#### 5. Listen for Notifications

```kotlin
val notification: Notification = notificationEvent.decryptNotification(clientSigner)

when (notification) {
    is PaymentReceivedNotification -> {
        val tx: NwcTransaction? = notification.notification
    }
    is PaymentSentNotification -> {
        val tx: NwcTransaction? = notification.notification
    }
}
```

### Wallet Service (Low-Level)

#### 1. Publish Capabilities

```kotlin
val infoTemplate = NwcInfoEvent.build(
    capabilities = listOf(NwcMethod.PAY_INVOICE, NwcMethod.GET_BALANCE),
    encryptionSchemes = listOf("nip04", "nip44_v2"),
    notificationTypes = listOf(NwcNotificationType.PAYMENT_RECEIVED),
)
// Sign with wallet signer: walletSigner.sign(infoTemplate)
```

#### 2. Parse Requests and Build Responses

```kotlin
val request: Request = requestEvent.decryptRequest(walletSigner)

// Build response
val balanceResponse = GetBalanceSuccessResponse(
    GetBalanceSuccessResponse.GetBalanceResult(balance = 2100000L)
)
val responseEvent = LnZapPaymentResponseEvent.createResponse(
    response = balanceResponse,
    requestEvent = requestEvent,
    signer = walletSigner,
)

// Error response
val errorResponse = NwcErrorResponse(
    resultType = NwcMethod.PAY_INVOICE,
    error = NwcError(NwcErrorCode.INSUFFICIENT_BALANCE, "Not enough funds"),
)
val errorEvent = LnZapPaymentResponseEvent.createResponse(
    response = errorResponse,
    requestEvent = requestEvent,
    signer = walletSigner,
)
```

#### 3. Send Notifications

```kotlin
val notifEvent = NwcNotificationEvent.createNotification(
    notification = PaymentReceivedNotification(
        notification = NwcTransaction(
            type = NwcTransactionType.INCOMING,
            state = NwcTransactionState.SETTLED,
            invoice = "lnbc...",
            amount = 50000L,
            payment_hash = "abc123",
            settled_at = TimeUtils.now(),
            created_at = TimeUtils.now(),
        ),
    ),
    clientPubkey = clientPubkeyHex,
    signer = walletSigner,
)
```

## Transaction State Helpers

Transaction states from different wallet implementations may use different
casing. Use the case-insensitive helpers:

```kotlin
NwcTransactionState.isSettled(tx.state)   // true for "SETTLED" or "settled"
NwcTransactionState.isPending(tx.state)   // true for "PENDING" or "pending"
NwcTransactionState.isFailed(tx.state)    // true for "FAILED" or "failed"
NwcTransactionState.isAccepted(tx.state)  // true for "ACCEPTED" or "accepted"
```

## URI Persistence

```kotlin
// Save
val json = Nip47WalletConnect.Nip47URI.serializer(nwcConfig.denormalize()!!)

// Restore
val restored = Nip47WalletConnect.Nip47URI.parser(json).normalize()!!
```

## Error Codes

| Code                    | When to Use                               |
|-------------------------|-------------------------------------------|
| `RATE_LIMITED`          | Too many requests                         |
| `NOT_IMPLEMENTED`       | Method not supported by wallet            |
| `INSUFFICIENT_BALANCE`  | Not enough funds for payment              |
| `PAYMENT_FAILED`        | Payment could not be completed            |
| `QUOTA_EXCEEDED`        | Budget/spending limit exceeded            |
| `RESTRICTED`            | Method not allowed for this connection    |
| `UNAUTHORIZED`          | Invalid or expired credentials            |
| `INTERNAL`              | Internal wallet error                     |
| `UNSUPPORTED_ENCRYPTION`| Requested encryption not supported        |
| `BAD_REQUEST`           | Malformed request parameters              |
| `NOT_FOUND`             | Invoice or resource not found             |
| `EXPIRED`               | Connection or invoice expired             |
| `OTHER`                 | Unspecified error                         |

## Encryption

NWC supports two encryption schemes:

- **NIP-04** (default): `Nip47Client(useNip44 = false)` or `useNip44 = false` in event builders
- **NIP-44 v2**: `Nip47Client(useNip44 = true)` or `useNip44 = true` in event builders

Clients can check a wallet's supported encryption via the info event:
```kotlin
val infoEvent: NwcInfoEvent = ...
val schemes: List<String> = infoEvent.encryptionSchemes()
// e.g., ["nip04", "nip44_v2"]
```

## Caching

For apps handling many concurrent NWC events, use the built-in LRU caches:

```kotlin
val requestCache = NostrWalletConnectRequestCache(signer)
val responseCache = NostrWalletConnectResponseCache(signer)

val request: Request? = requestCache.decryptRequest(requestEvent)
val response: Response? = responseCache.decryptResponse(responseEvent)
```

## Amounts

All amounts in NWC are in **millisatoshis** (1 sat = 1000 msats). Convert for
display:

```kotlin
val balanceMsats = response.result?.balance ?: 0L
val balanceSats = balanceMsats / 1000L
```

## Interoperability

This implementation is tested against:
- **Alby Hub** (server) — uppercase transaction states, all error codes
- **Alby JS SDK** (client) — lowercase transaction states, budget renewal
  periods, structured metadata

See `AlbyInteropTest.kt` for real-world test vectors.
