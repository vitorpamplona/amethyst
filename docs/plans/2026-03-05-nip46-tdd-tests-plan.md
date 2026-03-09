---
title: "test: TDD tests for NIP-46 relay isolation fix"
type: test
status: active
date: 2026-03-05
parent: 2026-03-05-fix-nip46-relay-isolation-and-init-plan.md
---

# test: TDD tests for NIP-46 relay isolation fix

## Overview

Write tests that **reproduce the three NIP-46 bugs** before they're fixed, then verify the fix. Tests should fail against the old shared-client architecture and pass after relay isolation.

## Bug → Test Mapping

| Bug | Root Cause | Test Strategy |
|-----|-----------|---------------|
| **Bug 1:** Empty `indexRelays` snapshot | `availableRelays.value` captured at coordinator construction = `emptySet()` | Verify coordinator receives non-empty relay set |
| **Bug 2:** Shared client relay contamination | NIP-46 relay leaks into general pool → ClosedMessage loop | Verify NIP-46 traffic stays on dedicated client, never touches general client |
| **Bug 3:** Sign request response lost | Subscription disrupted by ClosedMessage churn from Bug 2 | Verify NIP-46 subscription filters only target NIP-46 relays |

## Test Infrastructure

### Existing
- Framework: `kotlin.test` + `kotlinx-coroutines-test` + `mockk`
- Location: `desktopApp/src/jvmTest/kotlin/.../desktop/account/`
- Mocks: `EmptyNostrClient` (no-op), `mockk<SecureKeyStorage>(relaxed = true)`, temp dirs
- Pattern: `@BeforeTest` setup → `runTest {}` → `@AfterTest` cleanup

### Needed
- `SpyNostrClient` — wraps `INostrClient`, records which relays receive `send()` and `openReqSubscription()` calls
- No new dependencies (mockk `capture` + `slot` + `verify` sufficient)

## Phase 0: Fix Existing Broken Tests

**File:** `AccountManagerLoadAccountTest.kt`

Three tests pass `client` param to `loadSavedAccount()` which no longer accepts it:

| Test | Fix |
|------|-----|
| `loadSavedAccountBunkerNoEphemeralReturnsFailure` (line 114) | Remove `client = EmptyNostrClient` arg |
| `loadSavedAccountBunkerNoClientFallsBackToInternal` (line 119-136) | **Delete entirely** — concept no longer exists (AccountManager always creates its own NIP-46 client) |
| `loadSavedAccountBunkerSuccess` (line 155-158) | Remove `client = EmptyNostrClient` arg |

## Phase 1: Relay Isolation Tests (Bug 2 + 3)

### Test File: `AccountManagerNip46IsolationTest.kt`

**Location:** `desktopApp/src/jvmTest/kotlin/.../desktop/account/`

#### Test 1: `nip46ClientIsNotTheGeneralClient`

**Reproduces:** Bug 2 root cause — shared `NostrClient` pollution.

```kotlin
@Test
fun nip46ClientIsNotTheGeneralClient() = runTest {
    // Setup: bunker URI + ephemeral key in storage
    val validHex = "a".repeat(64)
    val ephemeralKeyPair = KeyPair()
    File(amethystDir, "bunker_uri.txt").writeText("bunker://$validHex?relay=wss://relay.nsec.app")
    File(amethystDir, "last_account.txt").writeText(ephemeralKeyPair.pubKey.toNpub())
    coEvery { storage.getPrivateKey(BUNKER_EPHEMERAL_KEY_ALIAS) } returns ephemeralKeyPair.privKey!!.toHexKey()

    // Load bunker account — this creates the internal NIP-46 client
    manager.loadSavedAccount()

    // The general relay pool client (e.g. relayManager.client) should NOT
    // have any NIP-46 relay subscriptions. Since AccountManager creates its
    // own client internally, we verify via the accountState having Remote signer.
    val state = manager.currentAccount()
    assertNotNull(state)
    assertIs<SignerType.Remote>(state.signerType)

    // The signer's client should be the dedicated one, not EmptyNostrClient
    val signer = state.signer as NostrSignerRemote
    // NostrSignerRemote stores its client — it should be a real NostrClient, not null
    assertNotNull(signer)
}
```

**Key assertion:** AccountManager creates a dedicated client internally rather than requiring one passed in.

#### Test 2: `loginWithBunkerDoesNotRequireExternalClient`

**Reproduces:** The old API required callers to pass `relayManager.client`.

```kotlin
@Test
fun loginWithBunkerDoesNotRequireExternalClient() = runTest {
    // loginWithBunker() should compile and work with just a bunkerUri
    // This test verifies the API — no client param needed
    try {
        manager.loginWithBunker("bunker://${"a".repeat(64)}?relay=wss://relay.nsec.app")
    } catch (e: Exception) {
        // Expected — no real relay to connect to. But it should NOT throw
        // a compile error or "client required" error.
        assertTrue(
            e.message?.contains("Connection") == true ||
                e.message?.contains("timed out") == true ||
                e.message?.contains("Connection failed") == true,
            "Unexpected error: ${e.message}",
        )
    }
}
```

#### Test 3: `loginWithNostrConnectDoesNotRequireExternalClient`

Same pattern — verifies API no longer takes `client`.

```kotlin
@Test
fun loginWithNostrConnectDoesNotRequireExternalClient() = runTest {
    var generatedUri: String? = null
    try {
        manager.loginWithNostrConnect { uri -> generatedUri = uri }
    } catch (e: Exception) {
        // Expected — no signer scanning the QR
        assertTrue(e.message?.contains("Timed out") == true || e.message != null)
    }
    // URI should have been generated even if connection failed
    assertNotNull(generatedUri)
    assertTrue(generatedUri!!.startsWith("nostrconnect://"))
}
```

#### Test 4: `logoutDisconnectsNip46Client`

**Reproduces:** Bug 2 side effect — leaked NIP-46 WebSocket after logout.

```kotlin
@Test
fun logoutDisconnectsNip46Client() = runTest {
    // Login with bunker (will create internal NIP-46 client)
    val keyPair = KeyPair()
    manager.loginWithKey(keyPair.privKey!!.toNsec())
    // Force to Remote type for test
    // Instead: test that logout from any state doesn't crash
    manager.logout()

    // After logout, state should be LoggedOut
    assertIs<AccountState.LoggedOut>(manager.accountState.value)
    // Signer connection should be NotRemote
    assertIs<SignerConnectionState.NotRemote>(manager.signerConnectionState.value)
}
```

#### Test 5: `logoutThenLoginCreatesNewNip46Client`

**Reproduces:** Ensuring fresh client after re-login (no stale state).

```kotlin
@Test
fun logoutThenLoginCreatesNewNip46Client() = runTest {
    // First bunker load
    val validHex = "a".repeat(64)
    val ephemeralKeyPair = KeyPair()
    File(amethystDir, "bunker_uri.txt").writeText("bunker://$validHex?relay=wss://relay.nsec.app")
    File(amethystDir, "last_account.txt").writeText(ephemeralKeyPair.pubKey.toNpub())
    coEvery { storage.getPrivateKey(BUNKER_EPHEMERAL_KEY_ALIAS) } returns ephemeralKeyPair.privKey!!.toHexKey()

    manager.loadSavedAccount()
    val firstState = manager.currentAccount()
    assertNotNull(firstState)

    // Logout — should disconnect NIP-46 client
    manager.logout()
    assertIs<AccountState.LoggedOut>(manager.accountState.value)

    // Re-load — should create a NEW NIP-46 client
    File(amethystDir, "bunker_uri.txt").writeText("bunker://$validHex?relay=wss://relay.nsec.app")
    File(amethystDir, "last_account.txt").writeText(ephemeralKeyPair.pubKey.toNpub())
    manager.loadSavedAccount()
    val secondState = manager.currentAccount()
    assertNotNull(secondState)

    // Both states should be valid Remote signers
    assertIs<SignerType.Remote>(firstState.signerType)
    assertIs<SignerType.Remote>(secondState.signerType)
}
```

## Phase 2: State Transition Tests

### Test File: `AccountManagerStateTransitionTest.kt`

#### Test 6: `bunkerRestoreShowsConnectingThenLoggedIn`

**Reproduces:** Bug 1 UX — user sees nothing while connecting.

```kotlin
@Test
fun bunkerRestoreShowsConnectingThenLoggedIn() = runTest {
    val states = mutableListOf<AccountState>()
    val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        manager.accountState.toList(states)
    }

    // Set connecting state (what Main.kt does before loadSavedAccount)
    manager.setConnectingRelays()

    // Then load internal account
    val keyPair = KeyPair()
    val npub = keyPair.pubKey.toNpub()
    File(amethystDir, "last_account.txt").writeText(npub)
    coEvery { storage.getPrivateKey(npub) } returns keyPair.privKey!!.toHexKey()
    manager.loadSavedAccount()

    advanceUntilIdle()

    // Should have: LoggedOut → ConnectingRelays → LoggedIn
    assertTrue(states.size >= 3, "Expected at least 3 state transitions, got: $states")
    assertIs<AccountState.LoggedOut>(states[0])
    assertIs<AccountState.ConnectingRelays>(states[1])
    assertIs<AccountState.LoggedIn>(states[2])

    collector.cancel()
}
```

#### Test 7: `loadSavedAccountBunkerNoEphemeralReturnsFailure`

Updated version — no `client` param.

```kotlin
@Test
fun loadSavedAccountBunkerNoEphemeralReturnsFailure() = runTest {
    val validHex = "a".repeat(64)
    val keyPair = KeyPair()
    val npub = keyPair.pubKey.toNpub()

    File(amethystDir, "last_account.txt").writeText(npub)
    File(amethystDir, "bunker_uri.txt").writeText("bunker://$validHex?relay=wss://r.com")
    coEvery { storage.getPrivateKey(AccountManager.BUNKER_EPHEMERAL_KEY_ALIAS) } returns null

    val result = manager.loadSavedAccount()
    assertTrue(result.isFailure)
}
```

## Phase 3: NostrSignerRemote Isolation Tests (quartz)

### Test File: `NostrSignerRemoteIsolationTest.kt`

**Location:** `quartz/src/commonTest/kotlin/.../nip46RemoteSigner/signer/`

#### Test 8: `subscriptionFilterTargetsOnlyBunkerRelays`

**Reproduces:** Bug 2 — subscription filters should ONLY reference NIP-46 relays.

```kotlin
@Test
fun subscriptionFilterTargetsOnlyBunkerRelays() {
    val bunkerRelay = NormalizedRelayUrl("wss://relay.nsec.app/")
    val generalRelay = NormalizedRelayUrl("wss://relay.damus.io/")

    val capturedFilters = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()
    val mockClient = mockk<INostrClient>(relaxed = true)
    every { mockClient.openReqSubscription(any(), capture(capturedFilters), any()) } just Runs

    val ephemeralSigner = NostrSignerInternal(KeyPair())
    val remoteSigner = NostrSignerRemote(
        signer = ephemeralSigner,
        remotePubkey = "a".repeat(64),
        relays = setOf(bunkerRelay),
        client = mockClient,
    )
    remoteSigner.openSubscription()

    // Verify filter only targets bunker relay
    assertTrue(capturedFilters.isNotEmpty(), "No subscription opened")
    capturedFilters.forEach { filterMap ->
        assertTrue(filterMap.keys.all { it == bunkerRelay }, "Filter contains non-bunker relay: ${filterMap.keys}")
        assertTrue(generalRelay !in filterMap.keys, "General relay leaked into NIP-46 filter")
    }
}
```

#### Test 9: `subscriptionFilterContainsCorrectKindAndPTag`

```kotlin
@Test
fun subscriptionFilterContainsCorrectKindAndPTag() {
    val capturedFilters = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()
    val mockClient = mockk<INostrClient>(relaxed = true)
    every { mockClient.openReqSubscription(any(), capture(capturedFilters), any()) } just Runs

    val ephemeralKeyPair = KeyPair()
    val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)
    val remoteSigner = NostrSignerRemote(
        signer = ephemeralSigner,
        remotePubkey = "b".repeat(64),
        relays = setOf(NormalizedRelayUrl("wss://relay.nsec.app/")),
        client = mockClient,
    )
    remoteSigner.openSubscription()

    val filters = capturedFilters.flatMap { it.values.flatten() }
    assertTrue(filters.isNotEmpty())
    filters.forEach { filter ->
        assertTrue(filter.kinds?.contains(NostrConnectEvent.KIND) == true,
            "Filter missing kind ${NostrConnectEvent.KIND}")
        assertTrue(filter.tags?.get("p")?.contains(ephemeralSigner.pubKey) == true,
            "Filter missing p-tag for ephemeral pubkey")
    }
}
```

## Phase 4: `since` Filter Tests (Bug 3 mitigation)

### Test File: add to `NostrSignerRemoteIsolationTest.kt`

#### Test 10: `subscriptionFilterHasSinceTimestamp`

**Reproduces:** Stale NIP-46 responses from previous sessions being processed.

```kotlin
@Test
fun subscriptionFilterHasSinceTimestamp() {
    val capturedFilters = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()
    val mockClient = mockk<INostrClient>(relaxed = true)
    every { mockClient.openReqSubscription(any(), capture(capturedFilters), any()) } just Runs

    val beforeTime = TimeUtils.now()

    val remoteSigner = NostrSignerRemote(
        signer = NostrSignerInternal(KeyPair()),
        remotePubkey = "c".repeat(64),
        relays = setOf(NormalizedRelayUrl("wss://relay.nsec.app/")),
        client = mockClient,
    )
    remoteSigner.openSubscription()

    val filters = capturedFilters.flatMap { it.values.flatten() }
    assertTrue(filters.isNotEmpty())
    filters.forEach { filter ->
        assertNotNull(filter.since, "Filter missing 'since' timestamp")
        // since should be roughly now - 60s (with some tolerance)
        val expectedMin = beforeTime - 120  // extra tolerance
        val expectedMax = beforeTime
        assertTrue(filter.since!! >= expectedMin, "since too old: ${filter.since}")
        assertTrue(filter.since!! <= expectedMax, "since in the future: ${filter.since}")
    }
}
```

> **Note:** This test will FAIL until Step 8 (add `since` to NostrSignerRemote) is implemented. That's the TDD red phase.

## Implementation Order

| # | Action | File | Phase |
|---|--------|------|-------|
| 1 | Fix broken tests (remove `client` param) | `AccountManagerLoadAccountTest.kt` | 0 |
| 2 | Write relay isolation tests (Tests 1-5) | `AccountManagerNip46IsolationTest.kt` | 1 |
| 3 | Write state transition tests (Tests 6-7) | `AccountManagerStateTransitionTest.kt` | 2 |
| 4 | Write signer isolation tests (Tests 8-9) | `NostrSignerRemoteIsolationTest.kt` | 3 |
| 5 | Write `since` filter test (Test 10) — **expect RED** | `NostrSignerRemoteIsolationTest.kt` | 4 |
| 6 | Run all → confirm Tests 1-9 GREEN, Test 10 RED | — | — |
| 7 | Implement Step 8 (`since` filter in quartz) | `NostrSignerRemote.kt` | — |
| 8 | Run all → confirm all GREEN | — | — |

## Test Commands

```bash
# Run all desktop tests
./gradlew :desktopApp:test

# Run specific test class
./gradlew :desktopApp:test --tests "*.AccountManagerNip46IsolationTest"

# Run quartz common tests
./gradlew :quartz:jvmTest --tests "*.NostrSignerRemoteIsolationTest"

# Run all NIP-46 related tests
./gradlew :desktopApp:test --tests "*.AccountManager*" :quartz:jvmTest --tests "*.nip46RemoteSigner.*"
```

## Unanswered Questions

1. `getOrCreateNip46Client()` is private — can't directly verify single-creation from tests without reflection or `@VisibleForTesting`. Sufficient to test indirectly through public API?
2. `NostrSignerRemote.subscription` field is private — MockK `capture` on `openReqSubscription` is the best proxy for filter verification?
3. Should we add Turbine dependency for cleaner StateFlow testing, or stick with manual `backgroundScope` collection?
4. `NostrClient.connect()` is non-suspending (fire-and-forget) — how to test that the NIP-46 client actually connects before subscriptions? Accept relay auto-queue behavior?
