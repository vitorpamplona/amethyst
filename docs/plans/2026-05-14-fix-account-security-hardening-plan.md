---
title: "fix: Account Security Hardening"
type: fix
status: active
date: 2026-05-14
origin: docs/brainstorms/2026-05-14-fix-account-security-hardening-brainstorm.md
---

# Account Security Hardening

## Deepening Insights (2026-05-14)

1. **NWC URI reconstruction**: `Nip47URINorm` has a direct constructor `(pubKeyHex, relayUri, secret, lud16)` — no URI string parsing needed. Store pubKeyHex+relayUri in metadata, secret in keychain, reconstruct via constructor.
2. **SecureKeyStorage alias**: Accepts any arbitrary string — `"nwc_npub1..."` confirmed to work. No format validation on alias.
3. **Corruption detection**: Use `StateFlow<StorageCorruption?>` on `AccountManager` (matches existing `forceLogoutReason` pattern). Distinguish key-lost (`getPrivateKey("account-metadata-key")` returns null) vs file-corrupted (`AEADBadTagException`) vs JSON-malformed (`JacksonException`).
4. **NWC save failure**: Don't fall back to plaintext or in-memory — fail the connection entirely. NWC secrets control real money.
5. **Cold boot ConnectingRelays**: Must read `accounts.json.enc` first to know account type, then show ConnectingRelays UI if Remote. Can't show it before reading.
6. **ensureCurrentAccountInStorage**: No longer needed — `loadSavedAccount()` only loads from `accounts.json.enc`, so account is guaranteed to be there.
7. **Backup pattern**: Use timestamped backups (`accounts.json.enc.corrupt.<millis>`) to avoid overwriting previous backups.

## Overview

Single PR fixing 6 security and correctness issues in desktop account management.
Makes `accounts.json.enc` the sole source of truth, moves NWC secrets to OS keychain,
makes NWC per-account, and eliminates stale file bugs. (see brainstorm for full rationale)

## Problem Statement

1. **CRITICAL:** NWC wallet secret in plaintext `nwc_connection.txt` — funds can be stolen
2. **HIGH:** NWC is global — switching accounts doesn't switch wallets
3. **HIGH:** Cold boot reads stale `bunker_uri.txt` before `accounts.json.enc` — nsec misidentified as bunker
4. **MEDIUM:** `logout(deleteKey=true)` doesn't remove from `accounts.json.enc` — ghost accounts
5. **MEDIUM:** `accounts.json.enc` corruption silently returns empty — data loss
6. **LOW:** `bunker_uri.txt` is singleton — can't support multi-bunker

## Implementation Phases

### Phase 1: NWC Secret to Keychain + Per-Account (Issues 1, 2)

#### 1a: Add NWC fields to AccountInfoDto

**File:** `DesktopAccountStorage.kt:223-258`

```kotlin
internal data class AccountInfoDto(
    val npub: String,
    val signerKind: String,
    val bunkerUri: String? = null,
    val displayName: String? = null,
    val isTransient: Boolean = false,
    val nwcPubKey: String? = null,   // NEW: wallet service pubkey (non-secret)
    val nwcRelay: String? = null,    // NEW: wallet relay URL (non-secret)
)
```

Jackson handles new nullable fields transparently — old `accounts.json.enc` files
deserialize without them (null defaults). `AccountInfo` in commons also needs
matching fields (or `AccountInfoDto.toAccountInfo()` maps them).

#### 1b: Rewrite NWC methods in AccountManager

**File:** `AccountManager.kt:788-844`

Replace `setNwcConnection()`, `clearNwcConnection()`, `loadNwcConnection()`.

**Key insight from deepening:** `Nip47URINorm` has a direct constructor — no URI
string reconstruction needed. Store components, rebuild via constructor.

```kotlin
// --- NWC (per-account, secret in keychain) ---

private fun nwcKeyAlias(npub: String) = "nwc_$npub"

fun setNwcConnection(npub: String, uri: String): Result<Nip47WalletConnect.Nip47URINorm> =
    try {
        val parsed = Nip47WalletConnect.parse(uri)
        val secret = parsed.secret ?: throw IllegalArgumentException("NWC URI has no secret")

        // Secret → keychain (fail entirely if keychain unavailable — don't fall back to plaintext)
        secureStorage.savePrivateKey(nwcKeyAlias(npub), secret)

        // Non-secret parts → accounts.json.enc
        scope.launch {
            val info = accountStorage.loadAccounts().find { it.npub == npub }
            if (info != null) {
                accountStorage.saveAccount(
                    info.copy(
                        nwcPubKey = parsed.pubKeyHex,
                        nwcRelay = parsed.relayUri.toString(),
                    ),
                )
            }
        }

        _nwcConnection.value = parsed
        Result.success(parsed)
    } catch (e: Exception) {
        Result.failure(e)
    }

fun clearNwcConnection(npub: String) {
    try { secureStorage.deletePrivateKey(nwcKeyAlias(npub)) } catch (_: SecureStorageException) {}
    scope.launch {
        val info = accountStorage.loadAccounts().find { it.npub == npub }
        if (info != null) {
            accountStorage.saveAccount(info.copy(nwcPubKey = null, nwcRelay = null))
        }
    }
    _nwcConnection.value = null
}

fun loadNwcConnection(npub: String) {
    val secret = try {
        secureStorage.getPrivateKey(nwcKeyAlias(npub))
    } catch (_: SecureStorageException) { null }

    if (secret != null) {
        scope.launch {
            val info = accountStorage.loadAccounts().find { it.npub == npub }
            if (info?.nwcPubKey != null && info.nwcRelay != null) {
                // Reconstruct directly — no URI parsing needed
                _nwcConnection.value = Nip47WalletConnect.Nip47URINorm(
                    pubKeyHex = info.nwcPubKey,
                    relayUri = NormalizedRelayUrl(info.nwcRelay),
                    secret = secret,
                )
            }
        }
    } else {
        _nwcConnection.value = null
    }
}
```

#### 1c: Delete legacy NWC file helpers

**Remove from AccountManager.kt:**
- `saveNwcUri()` (line 839-841)
- `getNwcFile()` (line 844)

**Delete on startup:** Add to `loadSavedAccount()`:
```kotlin
// Clean up legacy plaintext NWC file
File(amethystDir, "nwc_connection.txt").delete()
```

#### 1d: Update callers

- `switchAccount()` (line 761): `loadNwcConnection()` → `loadNwcConnection(targetNpub)`
- `Main.kt`: any `loadNwcConnection()` calls → pass npub
- Wallet column: `setNwcConnection(uri)` → `setNwcConnection(npub, uri)`

---

### Phase 2: Cold Boot from accounts.json.enc (Issue 3)

#### 2a: Rewrite loadSavedAccount()

**File:** `AccountManager.kt:233-247`

Replace entirely:

```kotlin
suspend fun loadSavedAccount(): Result<AccountState.LoggedIn> =
    try {
        val activeNpub = accountStorage.currentAccount()
            ?: return Result.failure(Exception("No saved account"))

        val accounts = accountStorage.loadAccounts()
        val info = accounts.find { it.npub == activeNpub }
            ?: return Result.failure(Exception("Account not found in storage"))

        // Clean up legacy files (one-time)
        File(amethystDir, "last_account.txt").delete()
        File(amethystDir, "bunker_uri.txt").delete()
        File(amethystDir, "nwc_connection.txt").delete()

        when (info.signerType) {
            is SignerType.Internal -> loadInternalAccount(activeNpub)
            is SignerType.Remote -> loadBunkerAccount(
                (info.signerType as SignerType.Remote).bunkerUri,
                activeNpub,
            )
            is SignerType.ViewOnly -> loadReadOnlyAccount(activeNpub)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
```

#### 2b: Rewrite saveCurrentAccount()

**File:** `AccountManager.kt:460-492`

Remove `saveLastNpub()` call (line 484). The `accountStorage.setCurrentAccount()`
call is sufficient.

#### 2c: Remove legacy file helpers

**Remove from AccountManager.kt:**
- `getLastNpub()` (line 846-848)
- `saveLastNpub()` (line 851-853)
- `clearLastNpub()` (line 856-857)
- `getPrefsFile()` (line 860)
- `getBunkerUri()` (line 862-864)
- `saveBunkerUri()` (line 867-869)
- `getBunkerFile()` (line 872)
- `hasBunkerAccount()` — uses `getBunkerUri()`, no longer needed

#### 2d: Update saveBunkerAccount()

Stop writing to `bunker_uri.txt`. The bunker URI is already saved in
`accounts.json.enc` via `AccountInfoDto.bunkerUri`.

#### 2e: Update Main.kt startup

**File:** `Main.kt:831-853`

**Deepening insight:** `ensureCurrentAccountInStorage()` is no longer needed
since `loadSavedAccount()` only loads from `accounts.json.enc` (account is
guaranteed to be there). `ConnectingRelays` UI must come AFTER reading
metadata to know the account type.

```kotlin
scope.launch(Dispatchers.IO) {
    accountManager.refreshAccountListOnStartup()

    val result = accountManager.loadSavedAccount()
    if (result.isSuccess) {
        accountManager.refreshAccountList()

        val current = accountManager.currentAccount()
        if (current?.signerType is SignerType.Remote) {
            accountManager.startHeartbeat(scope)
        }
        // Load per-account NWC
        accountManager.loadNwcConnection(current!!.npub)
    }
    // If failure: state remains LoggedOut → login screen shows automatically
}
```

Removed:
- `hasBunkerAccount()` — was based on `bunker_uri.txt` existence
- `ensureCurrentAccountInStorage()` — no longer needed
- `logout(deleteKey=true)` fallback for corrupt bunker state — corruption handling in Phase 4 covers this

---

### Phase 3: Logout Cleanup (Issue 4)

#### 3a: Fix logout(deleteKey=true)

**File:** `AccountManager.kt:561-589`

```kotlin
suspend fun logout(deleteKey: Boolean = false) {
    val current = currentAccount()
    if (current != null) {
        if (current.signerType is SignerType.Remote) {
            (current.signer as? NostrSignerRemote)?.closeSubscription()
        }
        if (deleteKey) {
            try { secureStorage.deletePrivateKey(current.npub) } catch (_: SecureStorageException) {}
            try { secureStorage.deletePrivateKey(bunkerEphemeralKeyAlias(current.npub)) } catch (_: SecureStorageException) {}
            try { secureStorage.deletePrivateKey(nwcKeyAlias(current.npub)) } catch (_: SecureStorageException) {}
            // Remove from metadata — fixes ghost accounts
            accountStorage.deleteAccount(current.npub)
        }
    }
    disconnectNip46Client()
    _nwcConnection.value = null
    _signerConnectionState.value = SignerConnectionState.NotRemote
    _lastPingTimeSec.value = null
    _accountState.value = AccountState.LoggedOut
    stopHeartbeat()
}
```

Key changes:
- Add `secureStorage.deletePrivateKey(nwcKeyAlias(current.npub))`
- Add `accountStorage.deleteAccount(current.npub)` when `deleteKey=true`
- Remove `getBunkerFile().delete()` and `clearLastNpub()` (files eliminated)

---

### Phase 4: Corruption Handling (Issue 5)

**Deepening insight:** Use `StateFlow` pattern (matches existing `forceLogoutReason`).
Distinguish key-lost vs file-corrupted. Use timestamped backups.

#### 4a: Add StorageCorruption sealed class to AccountManager

```kotlin
sealed class StorageCorruption {
    data class KeyLost(val backupPath: String?) : StorageCorruption()
    data class FileCorrupted(val backupPath: String?) : StorageCorruption()
    data class JsonMalformed(val backupPath: String?) : StorageCorruption()
}

private val _storageCorruption = MutableStateFlow<StorageCorruption?>(null)
val storageCorruption: StateFlow<StorageCorruption?> = _storageCorruption.asStateFlow()
fun clearStorageCorruption() { _storageCorruption.value = null }
```

#### 4b: Rewrite readMetadataFromDisk in DesktopAccountStorage

**File:** `DesktopAccountStorage.kt:114-126`

Accept a corruption callback (constructor param), catch specific exceptions:

```kotlin
class DesktopAccountStorage(
    homeDir: File,
    private val secureStorage: SecureKeyStorage,
    private val onCorruption: (StorageCorruption) -> Unit = {},  // NEW
) : AccountStorage {
    // ...

    private suspend fun readMetadataFromDisk(): AccountMetadata {
        val file = getAccountsFile()
        if (!file.exists()) return AccountMetadata()

        val encrypted = file.readBytes()
        if (encrypted.size < GCM_IV_SIZE) {
            val backup = backupCorruptFile(file)
            onCorruption(StorageCorruption.FileCorrupted(backup))
            return AccountMetadata()
        }

        return try {
            val decrypted = decrypt(encrypted)
            mapper.readValue<AccountMetadata>(decrypted)
        } catch (e: javax.crypto.AEADBadTagException) {
            val backup = backupCorruptFile(file)
            onCorruption(StorageCorruption.FileCorrupted(backup))
            AccountMetadata()
        } catch (e: javax.crypto.BadPaddingException) {
            val backup = backupCorruptFile(file)
            onCorruption(StorageCorruption.FileCorrupted(backup))
            AccountMetadata()
        } catch (e: com.fasterxml.jackson.core.JacksonException) {
            val backup = backupCorruptFile(file)
            onCorruption(StorageCorruption.JsonMalformed(backup))
            AccountMetadata()
        } catch (e: Exception) {
            Log.e("DesktopAccountStorage", "Failed to read accounts metadata", e)
            val backup = backupCorruptFile(file)
            onCorruption(StorageCorruption.FileCorrupted(backup))
            AccountMetadata()
        }
    }

    private fun backupCorruptFile(file: File): String? =
        try {
            val backup = File(file.parent, "accounts.json.enc.corrupt.${System.currentTimeMillis()}")
            Files.copy(file.toPath(), backup.toPath())
            file.delete()
            backup.absolutePath
        } catch (_: Exception) { null }
}
```

#### 4c: Wire in AccountManager

Pass corruption callback when constructing `DesktopAccountStorage`:

```kotlin
val accountStorage = DesktopAccountStorage(
    homeDir = homeDir,
    secureStorage = secureStorage,
    onCorruption = { _storageCorruption.value = it },
)
```

#### 4d: Show warning dialog in Main.kt

Observe `storageCorruption` StateFlow (same pattern as `forceLogoutReason`):

```kotlin
val corruption by accountManager.storageCorruption.collectAsState()
corruption?.let { c ->
    AlertDialog(
        onDismissRequest = { accountManager.clearStorageCorruption() },
        title = { Text("Account Data Issue") },
        text = {
            Text(when (c) {
                is StorageCorruption.KeyLost -> "Encryption key not found. Account list cannot be recovered."
                is StorageCorruption.FileCorrupted -> "Account data was corrupted."
                is StorageCorruption.JsonMalformed -> "Account data was partially written."
            } + if (c.backupPath != null) "\n\nA backup was saved." else "")
        },
        confirmButton = {
            Button(onClick = { accountManager.clearStorageCorruption() }) { Text("OK") }
        },
    )
}
```

---

## Files Modified

| File | Lines | Changes |
|------|-------|---------|
| `AccountManager.kt` | 233-247, 460-492, 561-589, 788-872 | Rewrite loadSavedAccount, saveCurrentAccount, logout, NWC methods; remove 8 legacy helpers |
| `DesktopAccountStorage.kt` | 114-126, 223-258 | Corruption backup; add nwcPubKey/nwcRelay to AccountInfoDto |
| `Main.kt` | 835-852 | Remove hasBunkerAccount check; add corruption warning dialog |

## Files Deleted (Legacy, stop reading/writing)

| File | Content | Risk |
|------|---------|------|
| `~/.amethyst/nwc_connection.txt` | NWC URI with spending secret | CRITICAL — deleted on startup |
| `~/.amethyst/last_account.txt` | Last active npub | Low — deleted on startup |
| `~/.amethyst/bunker_uri.txt` | Bunker URI | Medium — deleted on startup |

## Test Cases

| # | Test | File | Type |
|---|------|------|------|
| 1 | nsec login → save → cold boot loads Internal | `AccountManagerColdBootTest.kt` | Unit |
| 2 | bunker login → save → cold boot loads Remote with URI | `AccountManagerColdBootTest.kt` | Unit |
| 3 | bunker login → switch to nsec → cold boot loads Internal | `AccountManagerColdBootTest.kt` | Unit |
| 4 | Two bunker accounts → switch → each uses own URI | `AccountManagerSwitchTest.kt` | Unit |
| 5 | NWC set for account A → switch to B → back to A → NWC restored | `AccountManagerNwcTest.kt` | Unit |
| 6 | NWC secret stored in keychain, NOT in any file | `AccountManagerNwcTest.kt` | Unit |
| 7 | logout(deleteKey=true) removes from metadata + keychain | `AccountManagerLogoutTest.kt` | Unit |
| 8 | Corrupted accounts.json.enc → .bak created, empty returned | `DesktopAccountStorageCorruptionTest.kt` | Unit |
| 9 | Legacy files deleted on first cold boot | `AccountManagerMigrationTest.kt` | Unit |
| 10 | Fresh install → "No saved account" | `AccountManagerColdBootTest.kt` | Unit |

## Acceptance Criteria

- [ ] `nwc_connection.txt` never written; deleted on startup if exists
- [ ] `last_account.txt` never written; deleted on startup if exists
- [ ] `bunker_uri.txt` never written; deleted on startup if exists
- [ ] NWC secret stored via `secureStorage.savePrivateKey("nwc_<npub>", secret)`
- [ ] `loadSavedAccount()` routes by `SignerType` from `accounts.json.enc`
- [ ] `logout(deleteKey=true)` removes account from `accounts.json.enc`
- [ ] Corrupted `accounts.json.enc` → `.bak` backup + warning dialog
- [ ] `switchAccount()` loads correct NWC per-account
- [ ] All 10 test cases pass
- [ ] `./gradlew spotlessCheck` passes
- [ ] No plaintext secrets in `~/.amethyst/` directory

## Risk Analysis

| Risk | Severity | Mitigation |
|------|----------|------------|
| Existing users lose NWC connection | Low | Acceptable — reconnect wallet once (brainstorm decision) |
| Existing users lose account on cold boot | Medium | accounts.json.enc already stores everything; only users without it are affected (new installs) |
| SecureKeyStorage fallback password prompt for NWC | Low | Same pattern as nsec storage — well-tested |
| Jackson backward compat for new AccountInfoDto fields | Low | Nullable fields with defaults — Jackson handles gracefully |

## Sources

- **Origin brainstorm:** docs/brainstorms/2026-05-14-fix-account-security-hardening-brainstorm.md
- `AccountManager.kt` — cold boot (233-247), save (460-492), logout (561-589), NWC (788-844), legacy files (846-872)
- `DesktopAccountStorage.kt` — corruption (114-126), AccountInfoDto (223-258)
- `Main.kt` — startup (831-853)
- `SecureKeyStorage.kt` — savePrivateKey/getPrivateKey/deletePrivateKey (commons/jvmMain)
