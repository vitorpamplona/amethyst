---
title: "feat: Desktop Wallet & Zapping Test Coverage"
type: feat
status: active
date: 2026-05-12
origin: desktopApp/plans/2026-05-12-wallet-zapping-phase1-2-plan.md
---

# Desktop Wallet & Zapping Test Coverage

## Enhancement Summary (Deepened 2026-05-12)

**Key implementation insights from deepening research:**

1. **mockk callback capture**: Use `slot<(Event, NormalizedRelayUrl) -> Unit>()` + `capture(slot)` to intercept relay subscription callbacks and feed simulated wallet responses
2. **GlobalScope.launch problem**: NwcPaymentHandler uses `GlobalScope.launch(Dispatchers.IO)` inside callbacks — tests need real time via `withTimeout(5.seconds)` to await (or refactor to inject scope)
3. **LnZapPaymentResponseEvent.createResponse()** exists — build real encrypted test responses with deterministic keys, no need to mock encryption
4. **runTest auto-advances virtual time** — timeout tests resolve instantly without real delays
5. **Codebase prefers anonymous objects** over mockk for complex interfaces (see RelayConnectionManagerTest)

## Overview

Add comprehensive test coverage for the desktop wallet column and enhanced zapping UX.
Quartz already has 160+ NIP-47 protocol tests (request/response serialization, URI parsing,
Alby interop). This plan covers the **desktop-specific** layer: NwcPaymentHandler RPC
methods, wallet column state management, and zap dialog logic.

## Existing Coverage (Already Done)

| Layer | Module | Tests | Status |
|-------|--------|-------|--------|
| NIP-47 URI parsing | quartz | `Nip47WalletConnectTest.kt` | Complete |
| NWC request serialization | quartz | `RequestTest.kt` (50+ cases) | Complete |
| NWC response parsing | quartz | `ResponseTest.kt` (50+ cases) | Complete |
| NWC event encryption | quartz | `LnZapPaymentRequestEventTest.kt` | Complete |
| Alby interop | quartz | `AlbyInteropTest.kt` (60+ cases) | Complete |
| Account management | desktopApp | `AccountManager*Test.kt` (5 files) | Complete |
| Cache pipeline | desktopApp | `DesktopCachePipelineTest.kt` | Complete |

## New Coverage Needed

| Component | Location | Test Type | Priority |
|-----------|----------|-----------|----------|
| `NwcPaymentHandler` | desktopApp | Unit (mockk) | P0 |
| Wallet column state | desktopApp | State management | P1 |
| Zap dialog logic | desktopApp | Unit | P1 |
| NWC RPC round-trip | desktopApp | Integration | P2 |

## Test Framework & Patterns

From codebase research:

```kotlin
// Framework: kotlin.test + mockk + kotlinx-coroutines-test
import kotlin.test.*
import io.mockk.*
import kotlinx.coroutines.test.runTest

// Pattern: relaxed mocks for infrastructure
val relayManager = mockk<DesktopRelayConnectionManager>(relaxed = true)

// Pattern: deterministic keys for crypto
val keyPair = KeyPair()
val signer = NostrSignerInternal(keyPair)

// Pattern: StateFlow assertions via .value
assertEquals(expected, stateFlow.value)

// Pattern: BeforeTest/AfterTest lifecycle
@BeforeTest fun setup() { ... }
@AfterTest fun teardown() { ... }
```

## Phase 1: NwcPaymentHandler Unit Tests (P0)

**File:** `desktopApp/src/jvmTest/kotlin/.../nwc/NwcPaymentHandlerTest.kt`

Tests the 3 NWC RPC methods + error/timeout handling. Uses mockk to
mock `DesktopRelayConnectionManager` and `DesktopLocalCache`, then
simulates wallet responses via the `onEvent` callback.

### Test Cases

#### payInvoice

| # | Test | Setup | Expected |
|---|------|-------|----------|
| 1 | `payInvoice returns Success on valid response` | Mock relay, simulate PayInvoiceSuccessResponse | `PaymentResult.Success(preimage)` |
| 2 | `payInvoice returns Error on wallet error` | Simulate PayInvoiceErrorResponse | `PaymentResult.Error(message)` |
| 3 | `payInvoice returns Timeout when no response` | No simulated response, short timeout | `PaymentResult.Timeout` |
| 4 | `payInvoice returns Error when no secret` | nwcConnection with null secret | `PaymentResult.Error("no secret")` |
| 5 | `payInvoice publishes to correct relay` | Capture publishToRelay args | Correct relay URI + valid event |
| 6 | `payInvoice subscribes with correct filter` | Capture subscribeOnRelay args | Filter has kind 23195, author = wallet pubkey |
| 7 | `payInvoice tracks payment in zapped note` | Provide zappedNote mock | `zappedNote.addZapPayment()` called |
| 8 | `payInvoice cleans up subscription on cancel` | Cancel coroutine mid-flight | `closeSubscription()` called |

#### getBalance

| # | Test | Setup | Expected |
|---|------|-------|----------|
| 9 | `getBalance returns Success with msats` | Simulate GetBalanceSuccessResponse(balance=50000) | `BalanceResult.Success(50000)` |
| 10 | `getBalance returns Error on wallet error` | Simulate NwcErrorResponse | `BalanceResult.Error(message)` |
| 11 | `getBalance returns Timeout` | No response, short timeout | `BalanceResult.Timeout` |
| 12 | `getBalance returns Error when no secret` | null secret | `BalanceResult.Error` |
| 13 | `getBalance uses Nip47Client.fromNip47URI` | Verify request event is kind 23194 | Correct NWC request structure |

#### makeInvoice

| # | Test | Setup | Expected |
|---|------|-------|----------|
| 14 | `makeInvoice returns invoice on success` | Simulate MakeInvoiceSuccessResponse | `InvoiceResult.Success(invoice, hash)` |
| 15 | `makeInvoice returns Error when no invoice in response` | Simulate success with null invoice | `InvoiceResult.Error("no invoice")` |
| 16 | `makeInvoice returns Error on wallet error` | Simulate NwcErrorResponse | `InvoiceResult.Error(message)` |
| 17 | `makeInvoice returns Timeout` | No response | `InvoiceResult.Timeout` |
| 18 | `makeInvoice sends correct amount in msats` | Capture request, verify amount field | Amount matches input |

### Implementation Pattern

```kotlin
class NwcPaymentHandlerTest {
    private lateinit var relayManager: DesktopRelayConnectionManager
    private lateinit var localCache: DesktopLocalCache
    private lateinit var handler: NwcPaymentHandler
    private lateinit var nwcConnection: Nip47WalletConnect.Nip47URINorm

    // Capture the onEvent callback from subscribeOnRelay so we can
    // feed simulated wallet responses back into the handler.
    private var capturedOnEvent: ((Event, NormalizedRelayUrl) -> Unit)? = null

    @BeforeTest
    fun setup() {
        relayManager = mockk(relaxed = true)
        localCache = mockk(relaxed = true)
        handler = NwcPaymentHandler(relayManager, localCache)

        // Build a deterministic NWC connection
        val walletKeyPair = KeyPair()
        val clientKeyPair = KeyPair()
        nwcConnection = Nip47WalletConnect.Nip47URINorm(
            pubKeyHex = walletKeyPair.pubKey.toHexKey(),
            relayUri = NormalizedRelayUrl("wss://relay.test/"),
            secret = clientKeyPair.privKey!!.toHexKey(),
        )

        // Capture the onEvent callback
        every {
            relayManager.subscribeOnRelay(
                relay = any(),
                subId = any(),
                filters = any(),
                onEvent = capture(capturedOnEvent),
            )
        } answers { /* store callback */ }

        // Cache verification always passes
        coEvery { localCache.justVerify(any()) } returns true
    }

    @Test
    fun `payInvoice returns Success on valid response`() = runTest {
        // Simulate wallet response in a launched coroutine
        // after handler subscribes
        launch {
            delay(50) // Let handler subscribe first
            val responseEvent = buildPayInvoiceResponse(
                requestId = /* captured from subscribe filter */,
                preimage = "abc123",
                walletSigner = walletSigner,
                clientPubKey = clientKeyPair.pubKey.toHexKey(),
            )
            capturedOnEvent?.invoke(responseEvent, nwcConnection.relayUri)
        }

        val result = handler.payInvoice(
            bolt11 = "lnbc50n1...",
            nwcConnection = nwcConnection,
        )

        assertIs<NwcPaymentHandler.PaymentResult.Success>(result)
        assertEquals("abc123", result.preimage)
    }
}
```

**Challenge:** The `subscribeOnRelay` callback runs asynchronously. Tests
must capture the `onEvent` lambda and invoke it with a simulated
`LnZapPaymentResponseEvent`. This requires building encrypted response
events using the wallet's signer — similar to
`quartz/nip47WalletConnect/LnZapPaymentRequestEventTest.kt`.

**Alternative (simpler):** If capturing the relay callback is too complex,
extract the response processing logic into a testable pure function and
test that directly, then integration-test the full flow separately.

---

## Phase 2: Wallet Column State Tests (P1)

**File:** `desktopApp/src/jvmTest/kotlin/.../ui/wallet/WalletColumnStateTest.kt`

Tests the state management logic of WalletColumnScreen without Compose UI.
Extract state logic into a testable class if needed.

### Test Cases

| # | Test | Expected |
|---|------|----------|
| 19 | `initial state is HOME with no balance` | currentScreen=HOME, balanceSats=null |
| 20 | `navigating to CONNECT and back preserves state` | Screen transitions correctly |
| 21 | `connecting with valid URI transitions to HOME` | After setNwcConnection, screen=HOME |
| 22 | `connecting with invalid URI shows error` | connectionError is set |
| 23 | `disconnect clears connection` | nwcConnection becomes null |
| 24 | `balance updates after refresh` | balanceSats reflects RPC result |
| 25 | `send success clears invoice field` | sendInvoice="" after success |
| 26 | `send error shows error message` | sendResult contains error |
| 27 | `receive generates invoice` | generatedInvoice is set |
| 28 | `screen navigation: HOME->SEND->HOME` | Back button returns to HOME |

### Implementation Approach

Currently the wallet column state is all `var` inside the composable.
To test without Compose, either:

**Option A: Extract state holder class**
```kotlin
class WalletColumnState {
    var currentScreen by mutableStateOf(WalletScreen.HOME)
    var balanceSats by mutableStateOf<Long?>(null)
    var isLoadingBalance by mutableStateOf(false)
    // ... etc

    fun navigateToConnect() { currentScreen = WalletScreen.CONNECT }
    fun navigateBack() { currentScreen = WalletScreen.HOME }
}
```

**Option B: Test via AccountManager integration**

Test that `AccountManager.setNwcConnection()` and `clearNwcConnection()`
work correctly (these are already partially covered by existing
AccountManager tests — verify and extend).

**Recommendation:** Option A (extract state holder) gives the cleanest
test surface. Minimal refactor — move state vars into a class, test
the class directly.

---

## Phase 3: Zap Dialog Logic Tests (P1)

**File:** `desktopApp/src/jvmTest/kotlin/.../ui/ZapDialogLogicTest.kt`

Tests the zap amount/type selection logic without Compose rendering.

### Test Cases

| # | Test | Expected |
|---|------|----------|
| 29 | `default amount is first preset` | selectedAmount = 21 |
| 30 | `selecting preset updates amount` | selectedAmount = 500 after click |
| 31 | `custom amount mode enables text input` | useCustom = true |
| 32 | `custom amount parses to long` | effectiveAmount = 1337 |
| 33 | `invalid custom amount results in 0` | effectiveAmount = 0 for "abc" |
| 34 | `zap type defaults to PUBLIC` | selectedType = ZapType.PUBLIC |
| 35 | `zap type changes to PRIVATE` | selectedType = ZapType.PRIVATE |
| 36 | `zap type changes to ANONYMOUS` | selectedType = ZapType.ANONYMOUS |
| 37 | `empty message is valid` | message = "" is accepted |
| 38 | `confirm disabled when amount is 0` | enabled = false when custom="" |
| 39 | `DEFAULT_ZAP_AMOUNTS has expected values` | [21, 100, 500, 1000, 5000, 10000] |
| 40 | `formatSats formats thousands` | formatSats(5000) = "5k" |
| 41 | `formatSats keeps small numbers` | formatSats(100) = "100" |

### Implementation

```kotlin
class ZapDialogLogicTest {
    @Test
    fun `formatSats formats thousands with k suffix`() {
        assertEquals("5k", formatSats(5000))
        assertEquals("10k", formatSats(10000))
        assertEquals("1k", formatSats(1000))
    }

    @Test
    fun `formatSats preserves small amounts`() {
        assertEquals("21", formatSats(21))
        assertEquals("100", formatSats(100))
        assertEquals("500", formatSats(500))
    }

    @Test
    fun `DEFAULT_ZAP_AMOUNTS contains expected presets`() {
        assertEquals(listOf(21L, 100L, 500L, 1000L, 5000L, 10000L), DEFAULT_ZAP_AMOUNTS)
    }

    @Test
    fun `ZapType has correct labels`() {
        assertEquals("Public", ZapType.PUBLIC.label)
        assertEquals("Private", ZapType.PRIVATE.label)
        assertEquals("Anonymous", ZapType.ANONYMOUS.label)
    }
}
```

**Note:** `formatSats` and `DEFAULT_ZAP_AMOUNTS` are currently `private` in
`NoteActions.kt`. Either make them `internal` (for test access) or extract
to a utility object.

---

## Phase 4: NWC RPC Integration Tests (P2)

**File:** `desktopApp/src/jvmTest/kotlin/.../nwc/NwcRpcIntegrationTest.kt`

Full round-trip test: create NWC request event -> encrypt -> decrypt ->
verify request content -> build response -> encrypt -> decrypt -> verify.
Uses real crypto (NostrSignerInternal) with no mocking.

### Test Cases

| # | Test | Description |
|---|------|-------------|
| 42 | `pay_invoice round-trip` | Build request, verify BOLT11 in decrypted content |
| 43 | `get_balance round-trip` | Build request, create balance response, verify amount |
| 44 | `make_invoice round-trip` | Build request with amount, verify in decrypted content |
| 45 | `error response round-trip` | Build error response, verify error code and message |
| 46 | `NWC URI -> Nip47Client -> request event` | Full client pipeline test |

### Implementation Pattern

```kotlin
class NwcRpcIntegrationTest {
    private val clientKeyPair = KeyPair()
    private val walletKeyPair = KeyPair()
    private val clientSigner = NostrSignerInternal(clientKeyPair)
    private val walletSigner = NostrSignerInternal(walletKeyPair)

    @Test
    fun `get_balance full round-trip`() = runTest {
        // 1. Client builds request
        val client = Nip47Client(
            walletPubKeyHex = walletKeyPair.pubKey.toHexKey(),
            relayUrl = NormalizedRelayUrl("wss://relay.test/"),
            signer = clientSigner,
        )
        val requestEvent = client.getBalance()

        // 2. Verify request is properly encrypted
        assertEquals(LnZapPaymentRequestEvent.KIND, requestEvent.kind)
        assertTrue(requestEvent.content.isNotBlank())

        // 3. Wallet decrypts and verifies method
        val decryptedRequest = requestEvent.decryptRequest(walletSigner)
        assertIs<GetBalanceMethod>(decryptedRequest)

        // 4. Wallet builds encrypted response
        val responseEvent = LnZapPaymentResponseEvent.create(
            request = requestEvent,
            response = GetBalanceSuccessResponse(
                result = GetBalanceSuccessResponse.GetBalanceResult(balance = 125000),
            ),
            signer = walletSigner,
        )

        // 5. Client decrypts response
        val decryptedResponse = client.parseResponse(responseEvent)
        assertIs<GetBalanceSuccessResponse>(decryptedResponse)
        assertEquals(125000, decryptedResponse.result?.balance)
    }
}
```

---

## File Summary

### New Test Files (4)

```
desktopApp/src/jvmTest/kotlin/com/vitorpamplona/amethyst/desktop/
  nwc/
    NwcPaymentHandlerTest.kt          (~300 lines, 18 test cases)
    NwcRpcIntegrationTest.kt          (~150 lines, 5 test cases)
  ui/
    wallet/WalletColumnStateTest.kt   (~200 lines, 10 test cases)
    ZapDialogLogicTest.kt             (~100 lines, 13 test cases)
```

### Modified Files (1-2)

```
desktopApp/.../ui/NoteActions.kt      (make formatSats + DEFAULT_ZAP_AMOUNTS internal)
desktopApp/.../ui/wallet/WalletColumnScreen.kt  (optionally extract state holder)
```

### Total: ~46 test cases across 4 files

## Implementation Order

```
Phase 1: NwcPaymentHandlerTest (P0)     -- 18 tests, ~3h
Phase 3: ZapDialogLogicTest (P1)        -- 13 tests, ~1h (quick, no deps)
Phase 2: WalletColumnStateTest (P1)     -- 10 tests, ~2h (may need refactor)
Phase 4: NwcRpcIntegrationTest (P2)     -- 5 tests, ~2h
                                        ──────────
                                        ~8h total, 46 tests
```

Phase 3 before Phase 2 because it's simpler (pure functions, no state
extraction needed).

## Acceptance Criteria

- [ ] All 46 tests pass: `./gradlew :desktopApp:test`
- [ ] NwcPaymentHandler: all 3 RPC methods tested (success + error + timeout)
- [ ] Zap dialog: amount selection, custom amounts, type selection, formatSats tested
- [ ] Wallet state: navigation, connect/disconnect, balance refresh tested
- [ ] NWC round-trip: request->encrypt->decrypt->response cycle tested
- [ ] No flaky tests (no real network, no timing-dependent assertions)
- [ ] spotlessCheck passes

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Mocking relay callback is complex | Medium | Extract response processing into pure function, test that directly |
| Compose state testing without Compose | Medium | Extract state holder class (Option A) |
| `formatSats` is private | Low | Change to `internal` visibility |
| Encrypted response building may need quartz test utils | Low | Use same pattern as `LnZapPaymentRequestEventTest.kt` |

## Sources

- Existing desktop tests: `desktopApp/src/jvmTest/kotlin/.../` (25+ files)
- NIP-47 protocol tests: `quartz/src/commonTest/.../nip47WalletConnect/` (13 files)
- Test framework: kotlin.test + mockk 1.14.9 + kotlinx-coroutines-test 1.10.2
- Mock pattern: `AccountManagerBunkerLoginTest.kt` (relaxed mockk + coEvery)
- Crypto test pattern: `LnZapPaymentRequestEventTest.kt` (deterministic KeyPair)
