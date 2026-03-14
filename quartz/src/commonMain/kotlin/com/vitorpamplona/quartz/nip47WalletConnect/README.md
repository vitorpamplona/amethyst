# NIP-47 Wallet Connect (Quartz)

Quartz implementation of [NIP-47](https://github.com/nostr-protocol/nips/blob/master/47.md) — Nostr
Wallet Connect (NWC). This module provides everything needed to build both **wallet client apps**
(like Amethyst) and **wallet service backends** (like Alby Hub).

## Architecture

```
nip47WalletConnect/
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

| Method               | Request Class             | Success Response Class            |
|----------------------|---------------------------|-----------------------------------|
| `pay_invoice`        | `PayInvoiceMethod`        | `PayInvoiceSuccessResponse`       |
| `pay_keysend`        | `PayKeysendMethod`        | `PayKeysendSuccessResponse`       |
| `make_invoice`       | `MakeInvoiceMethod`       | `MakeInvoiceSuccessResponse`      |
| `lookup_invoice`     | `LookupInvoiceMethod`     | `LookupInvoiceSuccessResponse`    |
| `list_transactions`  | `ListTransactionsMethod`  | `ListTransactionsSuccessResponse` |
| `get_balance`        | `GetBalanceMethod`        | `GetBalanceSuccessResponse`       |
| `get_info`           | `GetInfoMethod`           | `GetInfoSuccessResponse`          |
| `get_budget`         | `GetBudgetMethod`         | `GetBudgetSuccessResponse`        |
| `sign_message`       | `SignMessageMethod`       | `SignMessageSuccessResponse`      |
| `create_connection`  | `CreateConnectionMethod`  | `CreateConnectionSuccessResponse` |
| `make_hold_invoice`  | `MakeHoldInvoiceMethod`   | `MakeHoldInvoiceSuccessResponse`  |
| `cancel_hold_invoice`| `CancelHoldInvoiceMethod` | `CancelHoldInvoiceSuccessResponse`|
| `settle_hold_invoice`| `SettleHoldInvoiceMethod`  | `SettleHoldInvoiceSuccessResponse`|

Any method can also return `NwcErrorResponse` or (for `pay_invoice`) `PayInvoiceErrorResponse`.

## Implementing a Wallet Client

A wallet client connects to a user's lightning wallet to send payments, check
balances, create invoices, and list transactions.

### 1. Parse the NWC Connection URI

Users provide an NWC connection string from their wallet provider:

```kotlin
val uri = "nostr+walletconnect://b889ff5b...?relay=wss%3A%2F%2Frelay.damus.io&secret=71a8c14c..."
val nwcConfig = Nip47WalletConnect.parse(uri)

// nwcConfig.pubKeyHex  — wallet service pubkey
// nwcConfig.relayUri   — relay to communicate through (NormalizedRelayUrl)
// nwcConfig.secret     — hex secret for the client signer
// nwcConfig.lud16      — optional lightning address
```

Supported URI schemes: `nostr+walletconnect://`, `nostrwalletconnect://`,
`amethyst+walletconnect://`

### 2. Create the Client Signer

The `secret` from the URI becomes the client's signing key:

```kotlin
val clientSigner = NostrSignerInternal(
    KeyPair(nwcConfig.secret!!.hexToByteArray())
)
```

### 3. Build and Send Requests

Use `LnZapPaymentRequestEvent.createRequest()` to create encrypted request events:

```kotlin
// Get balance
val balanceRequest = GetBalanceMethod.create()
val event = LnZapPaymentRequestEvent.createRequest(
    request = balanceRequest,
    walletServicePubkey = nwcConfig.pubKeyHex,
    signer = clientSigner,
)
// Send `event` to `nwcConfig.relayUri`

// Pay an invoice
val payRequest = PayInvoiceMethod.create("lnbc50n1...")
val payEvent = LnZapPaymentRequestEvent.createRequest(
    request = payRequest,
    walletServicePubkey = nwcConfig.pubKeyHex,
    signer = clientSigner,
)

// Create an invoice (amount in millisats)
val invoiceRequest = MakeInvoiceMethod.create(
    amount = 50000L,           // 50 sats in millisats
    description = "Coffee",
)
val invoiceEvent = LnZapPaymentRequestEvent.createRequest(
    request = invoiceRequest,
    walletServicePubkey = nwcConfig.pubKeyHex,
    signer = clientSigner,
)

// List transactions with pagination
val listRequest = ListTransactionsMethod.create(
    limit = 20,
    offset = 0,
)
val listEvent = LnZapPaymentRequestEvent.createRequest(
    request = listRequest,
    walletServicePubkey = nwcConfig.pubKeyHex,
    signer = clientSigner,
)
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

### 4. Receive and Parse Responses

Subscribe to kind `23195` events on the NWC relay, filtered by the wallet
service pubkey and the request event ID. When a response arrives:

```kotlin
// responseEvent is a LnZapPaymentResponseEvent (kind 23195)
val response: Response = responseEvent.decrypt(clientSigner)

when (response) {
    is GetBalanceSuccessResponse -> {
        val balanceMillisats = response.result?.balance ?: 0L
        val balanceSats = balanceMillisats / 1000L
    }
    is PayInvoiceSuccessResponse -> {
        val preimage = response.result?.preimage
        val feesPaid = response.result?.fees_paid
    }
    is MakeInvoiceSuccessResponse -> {
        val bolt11 = response.result?.invoice
        val paymentHash = response.result?.payment_hash
    }
    is ListTransactionsSuccessResponse -> {
        val transactions: List<NwcTransaction> = response.result?.transactions ?: emptyList()
        transactions.forEach { tx ->
            // tx.type — "incoming" or "outgoing"
            // tx.amount — in millisats
            // tx.description, tx.created_at, tx.state, etc.
        }
    }
    is GetInfoSuccessResponse -> {
        val alias = response.result?.alias
        val methods = response.result?.methods  // supported methods
        val lud16 = response.result?.lud16
    }
    is PayInvoiceErrorResponse -> {
        val errorCode = response.error?.code    // NwcErrorCode enum
        val errorMessage = response.error?.message
    }
    is NwcErrorResponse -> {
        val errorCode = response.error?.code
        val errorMessage = response.error?.message
    }
}
```

### 5. Listen for Notifications (Optional)

Subscribe to kind `23196`/`23197` events from the wallet:

```kotlin
// notificationEvent is an NwcNotificationEvent
val notification: Notification = notificationEvent.decryptNotification(clientSigner)

when (notification) {
    is PaymentReceivedNotification -> {
        val tx: NwcTransaction? = notification.notification
        // tx?.amount, tx?.description, tx?.payment_hash, etc.
    }
    is PaymentSentNotification -> {
        val tx: NwcTransaction? = notification.notification
    }
    is HoldInvoiceAcceptedNotification -> {
        val data = notification.notification
        // data?.payment_hash, data?.amount, data?.settle_deadline
    }
}
```

### 6. Transaction State Helpers

Transaction states from different wallet implementations may use different
casing. Use the case-insensitive helpers:

```kotlin
val tx: NwcTransaction = ...

NwcTransactionState.isSettled(tx.state)   // true for "SETTLED" or "settled"
NwcTransactionState.isPending(tx.state)   // true for "PENDING" or "pending"
NwcTransactionState.isFailed(tx.state)    // true for "FAILED" or "failed"
NwcTransactionState.isAccepted(tx.state)  // true for "ACCEPTED" or "accepted"
```

### 7. URI Persistence

To save/restore the NWC connection:

```kotlin
// Save
val nip47URI: Nip47WalletConnect.Nip47URI = nwcConfig.denormalize()!!
val json = Nip47WalletConnect.Nip47URI.serializer(nip47URI)

// Restore
val restored = Nip47WalletConnect.Nip47URI.parser(json)
val normalized = restored.normalize()!!
```

## Implementing a Wallet Service

A wallet service receives NWC requests from clients, processes them (e.g.,
pays invoices via a Lightning node), and sends back responses.

### 1. Publish Capabilities

Advertise which methods your wallet supports by publishing a kind `13194`
event:

```kotlin
val capabilities = listOf(
    NwcMethod.PAY_INVOICE,
    NwcMethod.GET_BALANCE,
    NwcMethod.GET_INFO,
    NwcMethod.MAKE_INVOICE,
    NwcMethod.LOOKUP_INVOICE,
    NwcMethod.LIST_TRANSACTIONS,
)

val infoTemplate = NwcInfoEvent.build(
    capabilities = capabilities,
    encryptionSchemes = listOf("nip04", "nip44_v2"),
    notificationTypes = listOf(
        NwcNotificationType.PAYMENT_RECEIVED,
        NwcNotificationType.PAYMENT_SENT,
    ),
)
// Sign with wallet signer: walletSigner.sign(infoTemplate)
```

### 2. Receive and Parse Requests

Subscribe to kind `23194` events on your relay, filtered by your wallet
service pubkey in the `p` tag. When a request arrives:

```kotlin
// requestEvent is a LnZapPaymentRequestEvent (kind 23194)
val request: Request = requestEvent.decryptRequest(walletSigner)

when (request) {
    is PayInvoiceMethod -> {
        val bolt11 = request.params?.invoice
        val amount = request.params?.amount  // optional override in millisats
        // Process payment via your Lightning node...
    }
    is GetBalanceMethod -> {
        // Query your Lightning node for balance...
    }
    is MakeInvoiceMethod -> {
        val amount = request.params?.amount  // in millisats
        val description = request.params?.description
        // Create invoice via your Lightning node...
    }
    is ListTransactionsMethod -> {
        val limit = request.params?.limit
        val offset = request.params?.offset
        val type = request.params?.type  // "incoming" or "outgoing"
        // Query transaction history...
    }
    is GetInfoMethod -> {
        // Return node info...
    }
    is GetBudgetMethod -> {
        // Return budget info...
    }
    // ... handle other methods
}
```

### 3. Build and Send Responses

Use `LnZapPaymentResponseEvent.createResponse()` to create encrypted
response events:

```kotlin
// Success response for get_balance
val balanceResponse = GetBalanceSuccessResponse(
    GetBalanceSuccessResponse.GetBalanceResult(balance = 2100000L)  // in millisats
)
val responseEvent = LnZapPaymentResponseEvent.createResponse(
    response = balanceResponse,
    requestEvent = requestEvent,
    signer = walletSigner,
)
// Send responseEvent to the relay

// Success response for pay_invoice
val payResponse = PayInvoiceSuccessResponse(
    PayInvoiceSuccessResponse.PayInvoiceResultParams(
        preimage = "0123456789abcdef",
        fees_paid = 100L,
    )
)
val payResponseEvent = LnZapPaymentResponseEvent.createResponse(
    response = payResponse,
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

To use NIP-44 encryption for responses:

```kotlin
val responseEvent = LnZapPaymentResponseEvent.createResponse(
    response = balanceResponse,
    requestEvent = requestEvent,
    signer = walletSigner,
    useNip44 = true,
)
```

### 4. Send Notifications

Push notifications to clients for payment events:

```kotlin
// Payment received notification
val notification = PaymentReceivedNotification(
    notification = NwcTransaction(
        type = NwcTransactionType.INCOMING,
        state = NwcTransactionState.SETTLED,
        invoice = "lnbc...",
        amount = 50000L,  // in millisats
        payment_hash = "abc123",
        settled_at = TimeUtils.now(),
        created_at = TimeUtils.now(),
    ),
)
val notifEvent = NwcNotificationEvent.createNotification(
    notification = notification,
    clientPubkey = clientPubkeyHex,
    signer = walletSigner,
)
// Send notifEvent to the relay
```

### 5. Build Transaction Objects

Transactions are used across multiple response types:

```kotlin
val transaction = NwcTransaction(
    type = NwcTransactionType.INCOMING,      // or OUTGOING
    state = NwcTransactionState.SETTLED,     // PENDING, SETTLED, FAILED, ACCEPTED
    invoice = "lnbc50n1...",
    description = "Coffee payment",
    payment_hash = "abc123def456",
    preimage = "fedcba654321",
    amount = 50000L,                         // in millisats
    fees_paid = 100L,                        // in millisats
    created_at = 1693876497L,                // unix timestamp
    settled_at = 1693876500L,
    expires_at = 1694876497L,
)
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

- **NIP-04** (default): Set `useNip44 = false` in event builders
- **NIP-44 v2**: Set `useNip44 = true` in event builders

When building requests with NIP-44, the event includes an `encryption` tag:
```
["encryption", "nip44_v2"]
```

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

// These cache up to 50 decrypted results and handle
// async retry logic for permission dialogs and timeouts.
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
