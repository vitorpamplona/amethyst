# NIP-46 Remote Signer (bunker:// + nostrconnect://) Login for Desktop — Deepened Plan

## Enhancement Summary

**Deepened on:** 2026-03-05 (2 rounds)
**Sections enhanced:** 10 (Steps 0-8 + security)
**Research agents used:** quartz NIP-46 audit, desktop account/main audit, NIP-46 spec research, coroutines heartbeat patterns, nostrconnect + QR + animation research, quartz nostrconnect gap audit, commons gap audit

### Key Improvements from Research
1. **`switch_relays` support** — NIP-46 spec says "compliant clients should send `switch_relays` immediately upon establishing a connection". Plan now includes this.
2. **Auth URL flow** — spec allows remote signer to return `auth_url` for user confirmation. Plan now handles this edge case.
3. **Secret validation** — spec says "client MUST validate the secret returned by connect response". Plan updated with explicit validation.
4. **No re-connect on restart** — confirmed: saved ephemeral key is already trusted by remote signer, skip `connect()` on restart.
5. **PoW relay concern** — some relays (nostr.mom, nos.lol) require Proof of Work for kind 24133. Plan notes this.
6. **`decryptZapEvent()` / `deriveKey()` NOT implemented** in quartz NostrSignerRemote — these will throw. Document as known limitation.
7. **Step 0 blocking tasks** — 8 prerequisite tasks (5 quartz, 3 commons) identified. bunker:// needs zero quartz changes; nostrconnect needs 4 blockers resolved.
8. **QrCodeDrawer portable** — existing Android composable uses zero Android APIs, 90% extract-as-is to commons.
9. **SecureKeyStorage + IAccount** — both 100% compatible with NostrSignerRemote, verified by audit.
10. **Phased implementation** — bunker:// first (no shared lib changes), then quartz+commons prereqs, then nostrconnect+heartbeat.

### New Considerations Discovered
- `NostrSigner.pubKey` = ephemeral key in NostrSignerRemote. DesktopIAccount uses `accountState.pubKeyHex` (user's real key) — **already correct**, no collision.
- `RemoteSignerManager.timeout` is configurable via constructor (default 30s) — use 30s for connect, could use shorter for ping.
- Bunker URI `secret=` is **optional** for remote-signer-initiated flow (our case). Plan should NOT require it.
- Some users may paste bunker URI over insecure channels — the URI is essentially a session token. Document this in security notes.

---

## Context

Desktop app only supports nsec/npub login. Quartz has a **complete, verified** NIP-46 client (`NostrSignerRemote`) that's never been wired into any app. Adding bunker:// login gives desktop the best security story — private key never touches the machine.

### Quartz NIP-46 Completeness Audit (Verified)

| Feature | Status | Location |
|---------|--------|----------|
| `connect()` handshake | ✅ | `NostrSignerRemote.connect()` |
| `sign_event` | ✅ | `NostrSignerRemote.sign()` |
| `ping` | ✅ | `NostrSignerRemote.ping()` |
| `get_public_key` | ✅ | `NostrSignerRemote.getPublicKey()` |
| NIP-04 encrypt/decrypt | ✅ | `nip04Encrypt()` / `nip04Decrypt()` |
| NIP-44 encrypt/decrypt | ✅ | `nip44Encrypt()` / `nip44Decrypt()` |
| `switch_relays` | ✅ | `BunkerRequestGetRelays` |
| Bunker URI parsing | ✅ | `fromBunkerUri()` — handles relay + secret params |
| Timeout handling | ✅ | 30s default, configurable in `RemoteSignerManager` |
| Subscription mgmt | ✅ | `openSubscription()` / `closeSubscription()` |
| `decryptZapEvent()` | ❌ TODO | Will throw — known limitation |
| `deriveKey()` | ❌ TODO | Will throw — known limitation |

### Desktop Account System Audit

| Component | Current State | Action |
|-----------|--------------|--------|
| `AccountState` sealed class | `LoggedOut` / `LoggedIn` | Add `ConnectingRelays` variant |
| `AccountManager.loginWithKey()` | Handles nsec/npub/hex | Add `loginWithBunker()` |
| `SecureKeyStorage` | OS keyring + encrypted fallback | Reuse for ephemeral key |
| `~/.amethyst/last_account.txt` | Stores npub | Reuse as-is |
| `Main.kt` DisposableEffect | Loads account + starts relays | Add relay-wait + heartbeat |
| `LoginCard` | Text input + Login/Generate buttons | Add bunker detection + connecting state |
| `RelayConnectionManager.client` | Exposes `INostrClient` | Pass to `loginWithBunker()` |

## Protocol Flow

```
User pastes bunker URI → validate format → generate ephemeral keypair
→ create NostrSignerRemote via fromBunkerUri() → call connect() over relays
→ remote signer (nsec.app/Amber) approves → validate secret if present
→ receive user pubkey → call switch_relays → update relay set if changed
→ AccountState.LoggedIn with NostrSignerRemote as signer
→ all sign/encrypt ops proxy transparently through remote signer
→ periodic ping heartbeat monitors connection health
```

### Research Insight: NIP-46 Spec Compliance
- **Secret validation**: If bunker URI contains `secret=`, client MUST validate the secret returned by `connect` response matches. If no secret in URI, skip validation.
- **`switch_relays`**: "Compliant clients should send a `switch_relays` request immediately upon establishing a connection." Remote signer replies with updated relay list or null.
- **Auth URL**: Remote signer may respond with `{"result": "auth_url", "error": "<URL>"}` — client should display URL to user for confirmation (e.g., nsec.app web confirmation).

## Step 0: Prerequisite Tasks (Blocking)

These must be completed before or alongside the desktop feature work. They strengthen quartz and commons as shared libraries.

### 0A. [QUARTZ/BLOCKER] Add `fromNostrConnectUri()` factory to NostrSignerRemote

**File:** `quartz/src/commonMain/.../nip46RemoteSigner/signer/NostrSignerRemote.kt`

**Problem:** `fromBunkerUri()` exists but no equivalent for nostrconnect://. Client-initiated flow needs the client to generate the URI, not parse one from a remote signer.

**Action:** Add companion factory that builds a `NostrSignerRemote` ready to *wait* for a connect response:
```kotlin
companion object {
    fun forNostrConnect(
        ephemeralSigner: NostrSignerInternal,
        relays: Set<NormalizedRelayUrl>,
        client: INostrClient,
        secret: String,
    ): Pair<NostrSignerRemote, String /* nostrconnect URI */> {
        // remotePubkey unknown until signer responds — see 0B
        // Build URI: nostrconnect://<ephemeral-pubkey>?relay=...&secret=...&name=Amethyst+Desktop
        // Return (signer, uriString)
    }
}
```

**Blocks:** Step 7 (nostrconnect login)

---

### 0B. [QUARTZ/BLOCKER] Make `remotePubkey` mutable or support deferred assignment

**File:** `quartz/src/commonMain/.../nip46RemoteSigner/signer/NostrSignerRemote.kt`

**Problem:** `val remotePubkey: HexKey` is immutable in constructor. For nostrconnect://, the remote signer's pubkey is unknown until the connect response arrives. Cannot instantiate `NostrSignerRemote` without it.

**Options:**
1. **`var remotePubkey`** — simplest, set after connect response. Risk: state mutation on a shared object.
2. **Two-phase construction** — `NostrConnectSession` awaits connect, then creates `NostrSignerRemote` with known pubkey. Cleaner but more code.
3. **DesktopApp workaround** — state machine in desktopApp: subscribe to kind 24133 manually, wait for connect response, extract pubkey, *then* construct `NostrSignerRemote`. Avoids quartz changes but duplicates logic.

**Recommended:** Option 2 — keep `NostrSignerRemote` immutable, add a `NostrConnectSession` helper that handles the wait-then-construct flow.

**Blocks:** Step 7 (nostrconnect login), 0A, 0C

---

### 0C. [QUARTZ/BLOCKER] NostrConnectEvent.create() requires remotePubkey

**File:** `quartz/src/commonMain/.../nip46RemoteSigner/NostrConnectEvent.kt`

**Problem:** `NostrConnectEvent.create()` calls `nip44Encrypt(content, remoteKey)`. For nostrconnect, the initial subscription doesn't need to *send* an event — client just listens. But `openSubscription()` in `NostrSignerRemote` sends a subscription filter, which doesn't require remotePubkey. The encryption issue only matters if client needs to send requests before learning remotePubkey.

**Resolution:** This is resolved by 0B — if we use two-phase construction, `NostrSignerRemote` is only created after remotePubkey is known, so `create()` always has it. No changes to `NostrConnectEvent.kt` needed.

**Blocks:** Step 7 (nostrconnect login). Resolved by 0B.

---

### 0D. [QUARTZ/SECURITY] Add secret validation to PubKeyResponse

**File:** `quartz/src/commonMain/.../nip46RemoteSigner/signer/PubKeyResponse.kt`

**Problem:** NIP-46 spec says "client MUST validate the secret returned by connect response matches the one sent". Current `PubKeyResponse` parses the pubkey from the result field but does NOT validate the secret.

**Action:** Add `secret` field to `PubKeyResponse` and validate in `connect()`:
```kotlin
data class PubKeyResponse(val pubkey: HexKey, val secret: String?) {
    companion object {
        fun parse(result: String): PubKeyResponse {
            // result may be just pubkey, or pubkey + secret separated by space
            // validate hex format
        }
    }
}
```

Then in `NostrSignerRemote.connect()`, compare returned secret with expected:
```kotlin
if (expectedSecret != null && response.secret != expectedSecret) {
    throw SignerExceptions.ManuallyUnauthorizedException("Secret mismatch — possible MITM")
}
```

**Priority:** Security hardening. Not strictly blocking for bunker:// (secret is optional), but **blocking for nostrconnect://** (secret is required).

**Blocks:** Step 2 (bunker login — optional validation), Step 7 (nostrconnect — required validation)

---

### 0E. [QUARTZ/NICE-TO-HAVE] Add `getRelays()` method to NostrSignerRemote

**File:** `quartz/src/commonMain/.../nip46RemoteSigner/signer/NostrSignerRemote.kt`

**Problem:** `BunkerRequestGetRelays` exists but `NostrSignerRemote` has no `getRelays()` or `switchRelays()` high-level method. The plan calls for `switch_relays` compliance per NIP-46 spec.

**Action:** Add method:
```kotlin
suspend fun switchRelays(): List<String>? {
    // Send BunkerRequestGetRelays, parse relay list response
    // Return updated relay list or null if signer doesn't support it
}
```

**Priority:** Nice-to-have. Can defer to a follow-up PR if needed. The `switch_relays` spec language is "compliant clients *should*" (not MUST).

**Does NOT block:** Any step. Can be added later.

---

### 0F. [COMMONS/BLOCKER] Extract QrCodeDrawer to commons

**Files:**
- **Source:** `amethyst/src/main/java/.../ui/screen/loggedIn/qrcode/QrCodeDrawer.kt`
- **Target:** `commons/src/commonMain/.../commons/ui/qrcode/QrCodeDrawer.kt`

**Problem:** QR code display needed for nostrconnect:// tab. Existing `QrCodeDrawer` in amethyst is pure Compose Canvas + ZXing core — **zero Android dependencies**. 90% extract-as-is.

**Gap:** References `QuoteBorder` from amethyst theme (just `RoundedCornerShape(15.dp)`). Replace with inline shape.

**Action:**
1. Copy `QrCodeDrawer.kt` to `commons/src/commonMain/.../commons/ui/qrcode/`
2. Replace `QuoteBorder` reference with `RoundedCornerShape(15.dp)`
3. Add typealias in amethyst to maintain backward compat
4. Add `libs.zxing` dependency to `commons/build.gradle.kts` (see 0G)

**Blocks:** Step 7 (nostrconnect QR display)

---

### 0G. [COMMONS/BLOCKER] Add ZXing core dependency to commons module

**File:** `commons/build.gradle.kts`

**Problem:** ZXing core 3.5.4 is in `gradle/libs.versions.toml` but NOT imported in `commons/build.gradle.kts`. QrCodeDrawer needs it.

**Action:** Add to `commonMain` dependencies (ZXing core is pure Java, works on JVM + Android):
```kotlin
commonMain.dependencies {
    implementation(libs.zxing)  // already in version catalog
}
```

**Note:** `zxing-embedded` (Android camera scanner wrapper) stays in amethyst only. Only `zxing:core` (encoder/decoder) goes to commons.

**Blocks:** 0F (QrCodeDrawer extraction), Step 7

---

### 0H. [COMMONS/NEW] Create HeartbeatIndicator composable

**File:** `commons/src/commonMain/.../commons/ui/components/HeartbeatIndicator.kt` (new)

**Problem:** No animated pulsing dot composable exists in commons. Needed for sidebar signer connection health indicator.

**Action:** Create shared composable:
- Input: `SignerConnectionState` sealed class (Connected/Unstable/Disconnected/NotRemote)
- Output: Pulsing dot (green/yellow/red) with `rememberInfiniteTransition` double-beat animation
- Desktop: wrapped in `TooltipArea` in DeckSidebar (desktop-only API)
- The composable itself is multiplatform; tooltip wrapping is platform-specific

**Dependencies:** None — pure Compose animation APIs.

**Blocks:** Step 8 (heartbeat UI)

---

### Step 0 Summary

| ID | Module | Type | Priority | Blocks |
|----|--------|------|----------|--------|
| 0A | quartz | `fromNostrConnectUri()` factory | 🔴 Blocker | Step 7 |
| 0B | quartz | Mutable/deferred `remotePubkey` | 🔴 Blocker | Step 7, 0A, 0C |
| 0C | quartz | NostrConnectEvent encryption | 🟢 Resolved by 0B | Step 7 |
| 0D | quartz | Secret validation in PubKeyResponse | 🟡 Security | Step 7 (required), Step 2 (optional) |
| 0E | quartz | `switchRelays()` method | ⚪ Nice-to-have | None |
| 0F | commons | Extract QrCodeDrawer | 🔴 Blocker | Step 7 |
| 0G | commons | ZXing dep in commons gradle | 🔴 Blocker | 0F, Step 7 |
| 0H | commons | HeartbeatIndicator composable | 🟡 Medium | Step 8 |

**Critical path for bunker:// (Steps 1-6):** 0D (optional hardening)
**Critical path for nostrconnect:// (Step 7):** 0B → 0A → 0D, 0G → 0F
**Critical path for heartbeat UI (Step 8):** 0H

### Implementation Order

```
Phase 1 (bunker:// — no quartz changes needed):
  Steps 1 → 2 → 3 → 4 → 5 → 6

Phase 2 (quartz + commons prereqs):
  0G → 0F (commons: ZXing + QrCodeDrawer)
  0B → 0A (quartz: deferred remotePubkey + nostrconnect factory)
  0D (quartz: secret validation)
  0H (commons: HeartbeatIndicator)

Phase 3 (nostrconnect + heartbeat):
  Step 7 (depends on 0A, 0B, 0D, 0F, 0G)
  Step 8 (depends on 0H)
```

**Key insight:** bunker:// login (Steps 1-6) requires ZERO quartz/commons changes — all quartz NIP-46 code works as-is. Ship bunker:// first, then layer nostrconnect + heartbeat.

---

## Implementation

### Step 1: AccountState + SignerType

**File:** `desktopApp/src/jvmMain/.../desktop/account/AccountManager.kt`

Add `SignerType` sealed class:
```kotlin
sealed class SignerType {
    data object Internal : SignerType()
    data class Remote(val bunkerUri: String) : SignerType()
}
```

Extend `AccountState`:
```kotlin
sealed class AccountState {
    data object LoggedOut : AccountState()
    data object ConnectingRelays : AccountState()
    data class LoggedIn(
        val signer: NostrSigner,
        val pubKeyHex: String,
        val npub: String,
        val nsec: String?,
        val isReadOnly: Boolean,
        val signerType: SignerType = SignerType.Internal,
    ) : AccountState()
}
```

#### Research Insights

**Existing pattern confirmed:** AccountState is observed via `accountManager.accountState.collectAsState()` in `App()` composable. Adding `ConnectingRelays` variant just needs a new `when` branch — zero refactoring needed.

**DesktopIAccount compatibility:** Uses `accountState.pubKeyHex` (not `signer.pubKey`), so `NostrSignerRemote` (where `signer.pubKey` = ephemeral key) works transparently. Verified in audit.

### Step 2: AccountManager — bunker login + persistence + heartbeat

**File:** `desktopApp/src/jvmMain/.../desktop/account/AccountManager.kt`

#### `loginWithBunker(bunkerUri: String, client: INostrClient): Result<LoggedIn>`

1. Validate URI format: starts with `bunker://`, hex pubkey (64 chars), `?relay=wss://` param
2. Generate ephemeral `KeyPair()` + wrap in `NostrSignerInternal`
3. `NostrSignerRemote.fromBunkerUri(bunkerUri, ephemeralSigner, client)`
   - Factory handles relay extraction + secret parsing
4. Call `remoteSigner.openSubscription()` — starts listening for responses
5. Call `remoteSigner.connect()` — sends BunkerRequestConnect, waits 30s
6. **Validate secret** if present: connect response must return matching secret value
7. On success: call `remoteSigner.switchRelays()` (new — NIP-46 compliance)
8. Set `AccountState.LoggedIn(signer=remoteSigner, pubKeyHex=remotePubkey, signerType=Remote(bunkerUri))`

#### Research Insights: Connect Flow Details

**From quartz audit — `connect()` internals:**
```
RemoteSignerManager.launchWaitAndParse():
  1. Build BunkerRequestConnect(remoteKey, secret?, permissions?)
  2. Create NostrConnectEvent (NIP-44 encrypted to remote signer)
  3. client.send(event, relayList)
  4. Store continuation[requestID] in LargeCache
  5. tryAndWait(30s) — suspendCancellableCoroutine
  6. On response: newResponse() → resume continuation
  7. Parse via PubKeyResponse → SignerResult
```

**Exception mapping in NostrSignerRemote.convertExceptions():**
- `Successful` → return result
- `Rejected` → throw `ManuallyUnauthorizedException`
- `TimedOut` → throw `TimedOutException`
- `ReceivedButCouldNotPerform` → throw `CouldNotPerformException`

**Auth URL edge case:** If remote signer needs web confirmation (nsec.app pattern), it returns `auth_url` response. Quartz doesn't handle this natively — **document as known limitation for v1**. Most bunker signers (Amber, local nsecBunker) don't use auth_url.

#### `saveBunkerAccount(ephemeralPrivKeyHex: String)`

Persistence files:
- `~/.amethyst/bunker_uri.txt` — full bunker URI (contains relay URLs + remote pubkey)
- `SecureKeyStorage("bunker_ephemeral")` — ephemeral private key (OS keyring)
- `~/.amethyst/last_account.txt` — user's npub (existing pattern)

#### Research Insight: Session Persistence

**Confirmed from NIP-46 community patterns:**
> "Once a connection has been successfully established and the BunkerPointer is stored, you do not need to call `connect()` on subsequent sessions."

Save: ephemeral privkey + bunker URI + user npub. On restart, recreate `NostrSignerRemote` with saved ephemeral key — remote signer already trusts this keypair.

**What NOT to save:** Don't store the secret separately — it's only needed for initial connect handshake. The ephemeral keypair IS the session credential.

#### `loadSavedAccount(client: INostrClient): Result<LoggedIn>`

Updated flow:
1. Check `bunker_uri.txt` — if exists, this is a bunker account
2. Load ephemeral privkey from `SecureKeyStorage("bunker_ephemeral")`
3. Load npub from `last_account.txt` → derive pubKeyHex
4. Create `NostrSignerInternal(KeyPair(ephemeralPrivKey))`
5. `NostrSignerRemote.fromBunkerUri(savedUri, ephemeralSigner, client)`
6. `remoteSigner.openSubscription()` — start listening (no connect() needed)
7. Return `LoggedIn(signer=remoteSigner, pubKeyHex=savedPubKeyHex, signerType=Remote)`
8. Fall back to existing internal key flow if no bunker file

#### `logout(deleteKey: Boolean)`

Bunker-specific cleanup:
1. `remoteSigner.closeSubscription()` — stop relay filter
2. Stop heartbeat job
3. Delete `bunker_uri.txt`
4. Delete `SecureKeyStorage("bunker_ephemeral")`
5. Clear `last_account.txt`
6. Set `AccountState.LoggedOut`

#### Ping Heartbeat

```kotlin
private var heartbeatJob: Job? = null
private var consecutiveFailures = 0

fun startHeartbeat(scope: CoroutineScope) {
    heartbeatJob = scope.launch {
        while (isActive) {
            delay(60_000)
            val current = currentAccount() ?: continue
            val remoteSigner = current.signer as? NostrSignerRemote ?: continue
            try {
                remoteSigner.ping()
                consecutiveFailures = 0  // reset on success
            } catch (e: SignerExceptions.TimedOutException) {
                consecutiveFailures++
                if (consecutiveFailures >= 3) {
                    forceLogoutWithReason("Lost connection to remote signer after 3 failed pings.")
                }
            } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
                forceLogoutWithReason("Remote signer revoked access.")
            }
        }
    }
}
```

#### Research Insights: Heartbeat Patterns

**Structured concurrency:** Heartbeat should be a child of the app's `CoroutineScope(SupervisorJob() + Dispatchers.Main)` already created in `App()`. SupervisorJob ensures heartbeat failure doesn't crash the app.

**Existing pattern in codebase:** `RelayStat.pingInMs` tracks relay ping latency — similar concept. RelayConnectionManager handles relay disconnection with reconnect logic, but no heartbeat loop exists yet.

**Simple counter vs AtomicInteger:** Since heartbeat runs in a single coroutine (sequential `while` loop), a plain `var consecutiveFailures: Int` is safe — no concurrent access.

**Window minimization:** Desktop heartbeat should continue even when minimized — the remote signer session is independent of window focus. The `CoroutineScope` created with `remember` persists across recompositions.

**Force logout state:** Use `MutableStateFlow<String?>` for `forceLogoutReason` — it's a stateful value that the UI observes, not a one-shot event. Dialog displays the reason, user dismisses, flow cleared.

### Step 3: LoginCard — validation + connecting state

**File:** `desktopApp/src/jvmMain/.../desktop/ui/auth/LoginCard.kt`

Add `LoginState` enum: `IDLE`, `CONNECTING`, `ERROR`

Add callback: `onLoginBunker: suspend (String) -> Result<Unit>`

**Bunker URI validation** (before attempting connect):
- Starts with `bunker://`
- Contains hex pubkey (64 hex chars after `bunker://`, before `?`)
- Contains at least one `relay=wss://...` param
- Show inline validation error if format wrong, don't attempt connection
- `secret=` is optional — do NOT require it

**UX when `bunker://` detected:**
- Button text: "Login" → "Connect to Signer"
- On click with valid URI: set `CONNECTING`, show `CircularProgressIndicator` + "Connecting to remote signer..."
- Disable input + all buttons while `CONNECTING`
- On success: caller handles transition
- On failure: show error message, return to `IDLE`, user can retry

#### Research Insights: UX Patterns

**From NIP-46 community feedback:**
> "Connection establishment takes a while to show to users" — always show clear progress indication.

**Pitfall discovered:** Users may not understand that bunker URI is a session credential:
> "Users might think the entire bunker URI is like a 2FA token and transmit it over untrusted messengers, and attackers can reuse the same URI"

Consider: add subtle help text below input when bunker:// detected: "This URI connects to your remote signer. Treat it like a password."

**Auto-detection approach:** Current `LoginCard` has a single `KeyInputField`. Detect `bunker://` prefix on input change — no new tabs/screens needed. Same pattern as NWC connection string input in settings.

### Step 4: LoginScreen + ConnectingRelays screen

**File:** `desktopApp/src/jvmMain/.../desktop/ui/LoginScreen.kt`

Add parameter: `relayClient: INostrClient`

Wire `onLoginBunker` callback → `accountManager.loginWithBunker(bunkerUri, relayClient)` → on success: save + `onLoginSuccess()`

**ConnectingRelaysScreen composable** (in same file):

Shown when `AccountState.ConnectingRelays`:
```
[Amethyst title text]
"Connecting to relays..."
[CircularProgressIndicator]
"Restoring remote signer session"
```

Used on app restart when a bunker account is saved — shows while relays connect before recreating `NostrSignerRemote`.

#### Research Insight: Startup Sequence

**From Main.kt audit — current startup:**
```kotlin
DisposableEffect(Unit) {
    scope.launch(Dispatchers.IO) { accountManager.loadSavedAccount() }
    relayManager.addDefaultRelays()
    relayManager.connect()
    subscriptionsCoordinator.start()
    onDispose { ... }
}
```

**Problem:** `loadSavedAccount()` for bunker accounts needs `relayManager.client` — but relay connections happen asynchronously. Current code loads account independently of relay state.

**Solution:** For bunker accounts, wait for relay connection before recreating signer. The pattern `relayManager.connectedRelays.first { it.isNotEmpty() }` already exists in `MainContent` LaunchedEffect for DM subscriptions.

### Step 5: Main.kt — relay state, pass client, heartbeat

**File:** `desktopApp/src/jvmMain/.../desktop/Main.kt`

**Updated startup flow:**
```kotlin
DisposableEffect(Unit) {
    relayManager.addDefaultRelays()
    relayManager.connect()
    subscriptionsCoordinator.start()

    scope.launch(Dispatchers.IO) {
        if (accountManager.hasBunkerAccount()) {
            accountManager.setConnectingRelays()
            // Wait for relay connections — pattern from MainContent DM setup
            relayManager.connectedRelays.first { it.isNotEmpty() }
            accountManager.loadSavedAccount(relayManager.client)
            accountManager.startHeartbeat(scope)
        } else {
            accountManager.loadSavedAccount(relayManager.client)
        }
    }

    onDispose {
        accountManager.stopHeartbeat()
        subscriptionsCoordinator.clear()
        relayManager.disconnect()
    }
}
```

**Account state routing — add ConnectingRelays + ForceLogout:**
```kotlin
when (accountState) {
    is AccountState.LoggedOut -> LoginScreen(accountManager, relayManager.client, onLoginSuccess)
    is AccountState.ConnectingRelays -> ConnectingRelaysScreen()
    is AccountState.LoggedIn -> MainContent(...)
}

// Force logout overlay
val forceLogoutReason by accountManager.forceLogoutReason.collectAsState()
forceLogoutReason?.let { reason ->
    ForceLogoutDialog(reason = reason, onDismiss = { accountManager.clearForceLogoutReason() })
}
```

#### Research Insight: Scope Lifecycle

**Current app scope:** `remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }` — created in `App()` composable, lives for the duration of the window. Heartbeat as a child of this scope is correct — it auto-cancels when app closes.

**onDispose cleanup:** Add `accountManager.stopHeartbeat()` to the existing `onDispose` block alongside `subscriptionsCoordinator.clear()` and `relayManager.disconnect()`.

### Step 6: ForceLogoutDialog

**File:** `desktopApp/src/jvmMain/.../desktop/ui/auth/ForceLogoutDialog.kt` (new)

Simple `AlertDialog`:
- Title: "Session Terminated"
- Body: reason string
- Single "OK" button → dismiss → login screen already showing (state is LoggedOut)

## Error Handling

| Exception | Source | User Message |
|-----------|--------|-------------|
| URI validation fail | LoginCard | "Invalid bunker URI. Expected: bunker://\<hex-pubkey\>?relay=wss://..." |
| `TimedOutException` | `connect()` | "Connection timed out. Ensure remote signer is online and has approved the connection." |
| `ManuallyUnauthorizedException` | `connect()` | "Connection rejected by remote signer." |
| `CouldNotPerformException` | `connect()` | "Remote signer error: {message}" |
| Generic exception | `connect()` | "Connection failed: {message}" |
| Heartbeat 3x timeout | `ping()` | Force logout: "Lost connection to remote signer after 3 failed pings." |
| Heartbeat revocation | `ping()` | Force logout: "Remote signer revoked access." |
| `decryptZapEvent` | Runtime | Known limitation — zap decryption not supported with remote signer |

### Research Insight: Relay-Specific Issues

**PoW requirement:** Some relays (nostr.mom, nos.lol, damus) rate-limit or require Proof of Work for kind 24133 events. If users specify these relays in their bunker URI, connections may fail. **Mitigation:** Document in error message. No code change needed — the relay list comes from the bunker URI (remote signer's choice).

## Files Modified

| File | Changes |
|------|---------|
| `AccountManager.kt` | SignerType, ConnectingRelays state, loginWithBunker(), saveBunkerAccount(), loadSavedAccount() updated, logout() updated, heartbeat, forceLogout |
| `LoginCard.kt` | LoginState enum, bunker URI validation, onLoginBunker callback, connecting UI, help text |
| `LoginScreen.kt` | Add relayClient param, wire bunker login, ConnectingRelaysScreen composable |
| `Main.kt` | Startup relay-wait flow, pass relayManager.client, heartbeat lifecycle, forceLogout dialog |
| `ForceLogoutDialog.kt` | **New** — AlertDialog for session termination |

## Reused As-Is (no changes needed — verified)

| File | What | Verified |
|------|------|----------|
| `NostrSignerRemote.kt` | Full NIP-46 client: connect, sign, encrypt, decrypt, ping | ✅ All methods present |
| `RemoteSignerManager.kt` | Request/response lifecycle with timeout | ✅ 30s configurable |
| `NostrConnectEvent.kt` | Kind 24133 wrapper with NIP-44 encryption | ✅ |
| `SignerExceptions.kt` | TimedOut, ManuallyUnauthorized, CouldNotPerform | ✅ |
| `DesktopIAccount.kt` | Uses `accountState.pubKeyHex` not `signer.pubKey` | ✅ Safe with remote signer |
| `RelayConnectionManager.kt` | Exposes `client: INostrClient` | ✅ |
| `SecureKeyStorage.kt` | OS keyring + encrypted fallback | ✅ Reuse for ephemeral key |

## Security Considerations

### Ephemeral Key = Session Credential
- Stored in OS keyring (macOS Keychain / Windows Credential Manager) via existing `SecureKeyStorage`
- If compromised: attacker can impersonate user's signing requests until remote signer revokes
- **Mitigation:** Remote signer can revoke anytime; heartbeat detects revocation

### Bunker URI as Session Token
- URI contains remote pubkey + relay URLs — NOT the user's private key
- But it enables initial connection establishment
- Once connected, the ephemeral keypair is the actual session credential
- **Secret parameter** (when present): only used once during initial connect. Not stored separately.

### Relay MITM
- NIP-46 messages are NIP-44 encrypted end-to-end between ephemeral key and remote signer
- Relay can see metadata (who's talking to whom) but NOT message content
- **No MITM on message content** — encryption is between known pubkeys

### Force Logout on Revocation
- `ManuallyUnauthorizedException` from ping → immediate logout + file cleanup
- 3 consecutive ping timeouts → logout (remote signer may be permanently offline)
- All bunker files deleted on force logout — clean slate

## Known Limitations (v1)

1. **Auth URL flow** — nsec.app may return `auth_url` response requiring web confirmation. Not handled in v1. Most signers (Amber, local bunkers) don't use this.
2. **`decryptZapEvent()`** — Not implemented in quartz's `NostrSignerRemote`. Zap decryption will fail for bunker accounts.
3. **`deriveKey()`** — Not implemented. NIP-06 key derivation won't work for bunker accounts.
4. **Single account** — Only one bunker OR one internal account at a time. Multi-account is a separate PR.

## Verification

1. `./gradlew :desktopApp:compileKotlin` — builds
2. `./gradlew :desktopApp:run` — test flows:
   - **Invalid URI** → paste `bunker://bad` → inline validation error, no network call
   - **Valid format, missing relay** → paste `bunker://<64hex>` → validation error about missing relay
   - **Login** → paste valid bunker URI → "Connecting..." → approve on phone → main screen
   - **Timeout** → paste URI, don't approve → 30s → timeout error, can retry
   - **Rejection** → paste URI, reject on phone → "rejected" error
   - **Restart** → close app → relaunch → "Connecting to relays..." → auto-login (no re-approval needed)
   - **Heartbeat** → revoke access on remote signer → force logout dialog within 60s
   - **Logout** → logout → bunker files deleted → login screen
   - **Post note** → compose + send → signed via remote signer transparently
   - **Send DM** → NIP-17 gift wrap → encrypted via remote signer
3. `./gradlew spotlessApply` before commit

## Answered Questions

1. **Both `bunker://` AND `nostrconnect://`** — see Step 7 below
2. **60s hardcoded** heartbeat interval
3. **Sidebar heartbeat icon** — tiny pulsing dot with tooltip, see Step 8 below
4. **Yes, persist relay updates** from `switch_relays` to `bunker_uri.txt`
5. **No auto-retry** — show error immediately, user clicks to retry

---

## Step 7: nostrconnect:// (Client-Initiated) Login

### Protocol Difference

| Aspect | `bunker://` (remote-signer-initiated) | `nostrconnect://` (client-initiated) |
|--------|---------------------------------------|--------------------------------------|
| Who generates URI | Remote signer (nsec.app, Amber) | Desktop client |
| Who scans/pastes | Desktop user pastes into app | User pastes into their signer app |
| Secret | Optional | **Required** (client generates) |
| Permissions | Not specified in URI | Client requests via `perms=` param |
| Flow | User already has URI → paste → connect | Client shows URI → user takes to signer → signer connects back |

### nostrconnect:// URI Format (from NIP-46 spec)

```
nostrconnect://<client-pubkey>?relay=wss://relay1.example.com&relay=wss://relay2.example.com&secret=<random-string>&perms=nip44_encrypt,nip44_decrypt,sign_event&name=Amethyst+Desktop&url=https://github.com/vitorpamplona/amethyst&image=<app-icon-url>
```

**Parameters:**
- `<client-pubkey>` — ephemeral pubkey generated by desktop (64 hex chars)
- `relay=` — relay URLs for communication (use app's connected relays)
- `secret=` — **REQUIRED** random string, client validates signer returns it in connect response
- `perms=` — comma-separated permissions to request
- `name=` — app display name ("Amethyst Desktop")
- `url=` — app URL (optional)
- `image=` — app icon URL (optional)

### UX Flow

1. User clicks **"Connect with Signer"** tab/button on login screen
2. Desktop generates ephemeral `KeyPair()` and random `secret`
3. Builds `nostrconnect://` URI with connected relay URLs + perms + secret
4. Displays:
   - **QR code** (primary — user scans with phone signer)
   - **Copyable text** (fallback — user pastes into web signer like nsec.app)
   - **"Waiting for signer to connect..."** status with spinner
5. Desktop subscribes to kind 24133 events on specified relays, filtered to ephemeral pubkey
6. User scans QR / pastes URI into their signer app
7. Signer sends `connect` response with the secret value
8. Desktop validates secret matches → `AccountState.LoggedIn`
9. If no response within **120s** (longer than bunker — user needs time to open phone): show timeout, allow retry

### Implementation Details

**LoginCard changes:**
- Add toggle/tab: "Paste Key" | "Connect with Signer"
- "Connect with Signer" tab shows QR + copyable URI + waiting state
- State machine: `IDLE → GENERATING → WAITING_FOR_SIGNER → CONNECTED / ERROR`

**AccountManager.loginWithNostrConnect(client: INostrClient): Pair<String, Job>**
1. Generate ephemeral `KeyPair()` + `NostrSignerInternal`
2. Generate random secret (16 hex chars)
3. Get connected relay URLs from relay manager (+ add `wss://relay.nsec.app` as fallback NIP-46 relay)
4. Build `nostrconnect://` URI string with `name=Amethyst+Desktop&perms=sign_event,nip44_encrypt,nip44_decrypt`
5. Subscribe to kind 24133 events on specified relays, filtered to ephemeral pubkey
6. Return (URI string, waiting Job)
7. When connect response received: validate secret, extract remote pubkey from response, create `NostrSignerRemote`
8. Set LoggedIn

**Key difference from bunker://:** With nostrconnect, client doesn't know user's pubkey upfront — learns it from signer's connect response. `fromBunkerUri()` cannot be reused directly — need to construct `NostrSignerRemote` manually after learning the remote pubkey.

**Quartz gap:** `RemoteSignerManager.launchWaitAndParse()` sends a request AND waits. For nostrconnect, client needs to just WAIT (signer initiates). Options:
- Build a `waitForConnect()` method in AccountManager that subscribes + waits
- Or call `openSubscription()` manually, then wait for the `newResponse()` callback

**Timeout:** 120s for nostrconnect (user needs to open phone, find app, scan QR). No auto-retry.

### QR Code Generation

**ZXing `core` 3.5.4 already in `gradle/libs.versions.toml`!** And there's an existing `QrCodeDrawer.kt` in the Android app (`amethyst/src/main/java/.../ui/screen/loggedIn/qrcode/QrCodeDrawer.kt`) that uses:
- `com.google.zxing.qrcode.encoder.Encoder` (pure Java, in `zxing:core`)
- Compose Canvas APIs (multiplatform)
- **Zero Android dependencies** — fully portable to desktop

**Action:** Extract `QrCodeDrawer.kt` to `commons/commonMain/` and add `libs.zxing` dependency to `commons` module. No new libraries needed.

**Gradle addition (commons module):**
```kotlin
// commons/build.gradle.kts - commonMain sourceSets
implementation(libs.zxing)  // already in version catalog
```

### Persistence

Same as bunker:// — save ephemeral key + URI + npub. On restart, reconnect without needing QR again (remote signer remembers ephemeral key).

### File Changes

| File | nostrconnect:// additions |
|------|--------------------------|
| `AccountManager.kt` | `loginWithNostrConnect()` method, generates URI + waits for connect |
| `LoginCard.kt` | Tab toggle "Paste Key" / "Connect with Signer", QR display, waiting state |
| `LoginScreen.kt` | Wire nostrconnect flow, pass relay client |
| `build.gradle.kts` | Add `qrcode-kotlin-jvm` dependency |

---

## Step 8: Heartbeat Status Indicator (Sidebar)

### Design

Tiny pulsing dot in the sidebar (below existing nav icons, above settings gear). Communicates remote signer connection health at a glance.

### States

| State | Visual | Tooltip |
|-------|--------|---------|
| **Connected** | Green dot, gentle pulse animation (1.5s cycle) | "Remote signer connected" |
| **Ping failed (1-2x)** | Yellow dot, faster pulse (0.8s cycle) | "Remote signer: connection unstable" |
| **Disconnected** | Red dot, no pulse (static) | "Remote signer: disconnected" |
| **Not remote** | Hidden | — (only show for bunker/nostrconnect accounts) |

### Animation Pattern — Double-Beat Heartbeat

Uses `keyframes` for organic lub-dub feel (two peaks per cycle):

```kotlin
val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
val scale by infiniteTransition.animateFloat(
    initialValue = 0.85f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
        animation = keyframes {
            durationMillis = 1200
            0.85f at 0                       // rest
            1.15f at 300 using EaseOut       // first beat (systole)
            0.95f at 500                     // contract
            1.05f at 700 using EaseInOut     // second beat (diastole)
            0.85f at 1200                    // rest
        },
        repeatMode = RepeatMode.Restart,
    ),
    label = "pulseScale",
)

// Apply via graphicsLayer on a small Box with CircleShape
Box(
    Modifier
        .size(8.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .background(color, CircleShape)
)
```

**Existing animation patterns in codebase:** `ProfileBroadcastBanner.kt` uses `animateFloatAsState` with `tween(300)` for progress bars. No `rememberInfiniteTransition` usage yet — this would be the first.

### Tooltip (Desktop-only API)

```kotlin
@OptIn(ExperimentalFoundationApi::class)
TooltipArea(
    tooltip = {
        Surface(
            modifier = Modifier.shadow(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(tooltipText, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        }
    },
    delayMillis = 400,
    tooltipPlacement = TooltipPlacement.CursorPoint(alignment = Alignment.BottomEnd),
) {
    HeartbeatDot(connectionState)
}
```

### Connection State Flow

```kotlin
// In AccountManager
sealed class SignerConnectionState {
    data object NotRemote : SignerConnectionState()  // internal signer, hide indicator
    data object Connected : SignerConnectionState()
    data class Unstable(val failCount: Int) : SignerConnectionState()
    data object Disconnected : SignerConnectionState()
}

val signerConnectionState: StateFlow<SignerConnectionState>
```

Heartbeat updates this state on each ping result. UI observes via `collectAsState()`.

### File Changes

| File | Heartbeat indicator additions |
|------|------------------------------|
| `AccountManager.kt` | `SignerConnectionState` sealed class + `signerConnectionState: StateFlow` |
| `DeckSidebar.kt` | Add `HeartbeatIndicator` composable at bottom of sidebar |
| `SinglePaneLayout.kt` | Add `HeartbeatIndicator` at bottom of nav rail |
| `HeartbeatIndicator.kt` | **New** — pulsing dot + tooltip composable |

---

## Updated Files Summary

| File | Changes |
|------|---------|
| `AccountManager.kt` | SignerType, ConnectingRelays, loginWithBunker(), loginWithNostrConnect(), saveBunkerAccount(), loadSavedAccount(), logout(), heartbeat, forceLogout, SignerConnectionState |
| `LoginCard.kt` | LoginState, tab toggle (paste/connect), bunker URI validation, nostrconnect QR + waiting, connecting UI |
| `LoginScreen.kt` | relayClient param, wire bunker + nostrconnect flows, ConnectingRelaysScreen |
| `Main.kt` | Startup relay-wait, heartbeat lifecycle, forceLogout dialog, pass relayManager.client |
| `ForceLogoutDialog.kt` | **New** — AlertDialog for session termination |
| `HeartbeatIndicator.kt` | **New** — pulsing dot + tooltip composable |
| `DeckSidebar.kt` | Add HeartbeatIndicator between spacer and Settings |
| `SinglePaneLayout.kt` | Add HeartbeatIndicator in nav rail |
| `QrCodeDrawer.kt` | **Extract** from `amethyst/.../qrcode/` → `commons/commonMain/` (zero Android deps) |
| `commons/build.gradle.kts` | Add `libs.zxing` (core) dependency |

## Answered Questions (Round 2)

1. **Full perms** for nostrconnect: `sign_event,nip04_encrypt,nip04_decrypt,nip44_encrypt,nip44_decrypt`
2. ~~Dark mode QR~~ — defer to implementation, follow existing theme
3. **Both layouts** — heartbeat in SINGLE_PANE nav rail AND DECK sidebar
