# Fix: nsec Session Not Persisting Across Desktop Restarts

## Current Persistence Mechanism

Two-layer storage architecture:

| Layer | What | Where | Format |
|-------|------|-------|--------|
| Account metadata | npub, signerType, activeNpub | `~/.amethyst/accounts.json.enc` | AES-256-GCM encrypted JSON |
| Private keys | nsec hex, ephemeral keys, NWC URIs | OS keychain (macOS Keychain via `java-keyring` 1.0.4) | Plaintext in keychain entry |
| Encryption key | AES key for accounts.json.enc | OS keychain, alias `account-metadata-key` | Base64 in keychain entry |

### Login Flow (nsec)

1. User pastes nsec in `LoginCard` (Paste Key tab)
2. `LoginCard.onLogin(keyInput)` called synchronously
3. `LoginScreen.onLogin` calls `accountManager.loginWithKey(keyInput)` -- **synchronous**, sets `_accountState` immediately
4. On success, `.map {}` fires `scope.launch` (fire-and-forget coroutine):
   - `accountManager.saveCurrentAccount()` on `Dispatchers.IO`
   - `onLoginSuccess()` on Main
5. `onLoginSuccess` fires another `scope.launch(Dispatchers.IO)`:
   - `accountManager.ensureCurrentAccountInStorage()`
   - `accountManager.refreshAccountList()`

### Save Path (`saveCurrentAccount`)

1. Reads `currentAccount()` from in-memory `_accountState`
2. Calls `secureStorage.savePrivateKey(npub, privKeyHex)` -- writes nsec to OS keychain
3. Calls `accountStorage.saveAccount(info)` -- updates in-memory cache + writes `accounts.json.enc`
4. Calls `accountStorage.setCurrentAccount(npub)` -- updates activeNpub in cache + writes file again

### Restore Path (`loadSavedAccount`)

1. Reads `accountStorage.currentAccount()` -- decrypts `accounts.json.enc`, gets `activeNpub`
2. Finds matching `AccountInfo` in account list
3. For `SignerType.Internal`: calls `secureStorage.getPrivateKey(npub)` -- reads from OS keychain
4. If privkey found: creates `KeyPair` + `NostrSignerInternal`, sets `_accountState`
5. If privkey null: falls back to read-only (view-only mode)

## Root Cause Hypotheses (Ranked by Likelihood)

### H1: Fire-and-Forget Save Never Completes (HIGH)

**The save runs in a fire-and-forget coroutine that can be lost.**

In `LoginScreen.kt` lines 97-103:
```kotlin
accountManager.loginWithKey(keyInput).map {
    scope.launch {   // <-- fire-and-forget
        withContext(Dispatchers.IO) { accountManager.saveCurrentAccount() }
        onLoginSuccess()
    }
}
```

`loginWithKey` sets `_accountState = LoggedIn` synchronously. The UI immediately recomposes to show `MainContent`. The `scope` here is `rememberCoroutineScope()` from `LoginScreen` -- but `LoginScreen` is no longer composed (it was inside the `AccountState.LoggedOut` branch). When the composable leaves composition, `rememberCoroutineScope` cancels all launched coroutines.

**Race condition:** `loginWithKey()` updates `_accountState` -> recomposition -> `LoginScreen` exits composition -> its `scope` is cancelled -> the save coroutine is cancelled before `saveCurrentAccount()` completes.

The `onLoginSuccess` callback in `Main.kt` also tries `ensureCurrentAccountInStorage()`, but it uses a **different** `scope.launch(Dispatchers.IO)` from the `App` composable's scope. This should survive. But if `saveCurrentAccount()` was the only path that writes the private key to the keychain, and it got cancelled, then `ensureCurrentAccountInStorage()` only writes the metadata (npub + signerType) without the private key. On next startup, `loadInternalAccount` finds no privkey and falls back to read-only -- which looks like "session gone."

**Verdict:** The nsec is likely written to metadata but the keychain save is cancelled. On restart, the account loads as read-only (no nsec) which may appear as "not persisted."

### H2: Keychain Write Fails Silently (MEDIUM)

`java-keyring` 1.0.4 uses macOS Security framework. Known issues:
- First-time keychain access prompts the user for permission -- if dismissed, the write silently fails (caught as `BackendNotSupportedException` or `PasswordAccessException`)
- Sandboxed apps may not have keychain entitlements
- `./gradlew :desktopApp:run` runs via Gradle daemon which may lack keychain access

If `saveToKeyring` throws, `SecureKeyStorage.savePrivateKey` catches it and falls to `saveToFallback`, which requires a console password. In a GUI app launched via `./gradlew :desktopApp:run`, there IS no console. `getFallbackPassword()` calls `System.console()` which returns null, then calls `readLine()` which blocks on stdin. This could hang or throw.

### H3: AES Key Regenerated Between Sessions (MEDIUM-LOW)

The `accounts.json.enc` encryption key is stored in keychain under `account-metadata-key`. If H2 applies (keychain write fails), a NEW AES key is generated on each app launch. The file written with the old key becomes unreadable on next launch -- `readMetadataFromDisk` catches `AEADBadTagException`, backs up the corrupt file, and returns empty `AccountMetadata`. This means `currentAccount()` returns null -> "No saved account".

### H4: `cachedMetadata` Race Between Concurrent Coroutines (LOW)

`DesktopAccountStorage.cachedMetadata` is a plain `var` with no synchronization. The two save paths (`saveCurrentAccount` from LoginScreen's scope, `ensureCurrentAccountInStorage` from App's scope) could interleave reads/writes. One could overwrite the other's changes. But this would cause partial data loss, not complete failure.

### H5: `accounts.json.enc` Written But AES Key Lost (LOW)

If the app is killed between writing the file and the keychain entry being flushed, the AES key may not persist. On next startup, a new key is generated, old file is unreadable.

## Key Files Involved

| File | Role |
|------|------|
| `desktopApp/.../Main.kt` | Startup flow, `loadSavedAccount()` call, `onLoginSuccess` callback |
| `desktopApp/.../ui/LoginScreen.kt` | Login UI, calls `loginWithKey` + fire-and-forget save |
| `desktopApp/.../account/AccountManager.kt` | `loginWithKey`, `saveCurrentAccount`, `loadSavedAccount`, `ensureCurrentAccountInStorage` |
| `desktopApp/.../account/DesktopAccountStorage.kt` | Encrypted metadata file I/O, `accounts.json.enc` |
| `commons/.../keystorage/SecureKeyStorage.kt` (jvmMain) | OS keychain via `java-keyring`, fallback encrypted file |
| `desktopApp/.../ui/auth/LoginCard.kt` | UI form, calls `onLogin` synchronously |

## Proposed Fixes

### Fix 1: Make `loginWithKey` Save Inline (Addresses H1)

Change `loginWithKey` to a `suspend fun` that saves the account before returning. This eliminates the fire-and-forget race entirely.

```kotlin
// Before: loginWithKey is sync, save is fire-and-forget
fun loginWithKey(keyInput: String): Result<AccountState.LoggedIn>

// After: loginWithKey saves inline
suspend fun loginWithKey(keyInput: String): Result<AccountState.LoggedIn> {
    // ... create keyPair, signer, state ...
    _accountState.value = state
    saveCurrentAccount()  // save inline, not fire-and-forget
    return Result.success(state)
}
```

LoginScreen would call it from a coroutine:
```kotlin
onLogin = { keyInput ->
    scope.launch(Dispatchers.IO) {
        accountManager.loginWithKey(keyInput).fold(
            onSuccess = { withContext(Dispatchers.Main) { onLoginSuccess() } },
            onFailure = { /* show error */ }
        )
    }
    Result.success(Unit) // immediate return to LoginCard
}
```

### Fix 2: Use App-Level Scope for Save (Addresses H1, simpler)

Move the save coroutine to the `onLoginSuccess` callback (which uses App-level scope that survives recomposition) and ensure it saves the private key too:

```kotlin
onLoginSuccess = {
    scope.launch(Dispatchers.IO) {
        accountManager.saveCurrentAccount()  // <-- add this, saves privkey to keychain
        accountManager.ensureCurrentAccountInStorage()
        accountManager.refreshAccountList()
    }
}
```

### Fix 3: Validate Keychain Access on Startup (Addresses H2/H3)

Add a keychain health check at startup. Write a test value, read it back, delete it. If it fails, show a warning dialog instead of silently falling to broken fallback.

### Fix 4: Add Mutex to DesktopAccountStorage (Addresses H4)

Wrap `cachedMetadata` access in a `Mutex` to prevent concurrent read/write races.

### Fix 5: Log Save Result (Diagnostic)

In `LoginScreen.kt`, log the result of `saveCurrentAccount()` so failures are visible:
```kotlin
val result = accountManager.saveCurrentAccount()
if (result.isFailure) {
    Log.e("LoginScreen", "Failed to save account", result.exceptionOrNull())
}
```

## Unanswered Questions

- Is the keychain prompt appearing on first login? User may be dismissing it.
- Is `./gradlew :desktopApp:run` the launch method? Gradle daemon may lack keychain entitlements.
- Does `~/.amethyst/accounts.json.enc` exist after login? If yes, metadata saved but privkey lost (H1/H2). If no, metadata never written (H1 complete cancellation).
- Is the app packaged (`.dmg`/`.deb`) or run from source? Packaging affects keychain access.
- Does the user see read-only mode on restart, or the login screen? Read-only = privkey lost. Login screen = metadata lost.
- Is there a `accounts.json.enc.corrupt.*` backup file in `~/.amethyst/`? If yes, H3 confirmed.
