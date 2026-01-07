# SecureKeyStorage Migration Guide

## Overview

SecureKeyStorage is now in `quartz/nip01Core/crypto/` as a KMP module for secure nsec (private key) storage.

**Location:** `com.vitorpamplona.quartz.nip01Core.crypto.SecureKeyStorage`

## Platform Implementations

| Platform | Backend | Encryption | Hardware-Backed |
|----------|---------|------------|-----------------|
| **Android** | EncryptedSharedPreferences + Android Keystore | AES-256-GCM | ✅ (StrongBox when available) |
| **Desktop macOS** | macOS Keychain (via java-keyring) | OS-managed | ✅ (T2/M1+ chips) |
| **Desktop Windows** | Credential Manager (via java-keyring) | DPAPI | ✅ (TPM when available) |
| **Desktop Linux** | Secret Service/KWallet (via java-keyring) | OS-managed | ⚠️ (depends on setup) |
| **Fallback** | Encrypted file (~/.amethyst/keys.enc) | AES-256-GCM (PBKDF2) | ❌ |

## API

```kotlin
// Create instance (Android requires Context)
val storage = SecureKeyStorage(context) // Android
val storage = SecureKeyStorage() // Desktop

// Save private key
suspend fun savePrivateKey(npub: String, privKeyHex: String)

// Retrieve private key
suspend fun getPrivateKey(npub: String): String?

// Delete private key
suspend fun deletePrivateKey(npub: String): Boolean

// Check if exists
suspend fun hasPrivateKey(npub: String): Boolean
```

## Migrating Amethyst

### Current Implementation

**File:** `amethyst/src/main/java/com/vitorpamplona/amethyst/LocalPreferences.kt`

- Uses `EncryptedSharedPreferences` directly
- Private keys stored in per-account encrypted prefs: `secret_keeper_$npub`
- Key: `NOSTR_PRIVKEY`

**Current flow:**
```kotlin
// Save
val prefs = EncryptedStorage.preferences(context, npub)
prefs.edit().putString(PrefKeys.NOSTR_PRIVKEY, privKeyHex).apply()

// Load
val privKey = prefs.getString(PrefKeys.NOSTR_PRIVKEY, null)
```

### New Implementation

**Import:**
```kotlin
import com.vitorpamplona.quartz.nip01Core.crypto.SecureKeyStorage
import com.vitorpamplona.quartz.nip01Core.crypto.SecureStorageException
```

**New flow:**
```kotlin
// Initialize once (in Application or DI module)
val secureStorage = SecureKeyStorage(applicationContext)

// Save
suspend fun saveAccount(npub: String, privKeyHex: String) {
    try {
        secureStorage.savePrivateKey(npub, privKeyHex)
    } catch (e: SecureStorageException) {
        Log.e("Account", "Failed to save key", e)
    }
}

// Load
suspend fun loadAccount(npub: String): String? {
    return try {
        secureStorage.getPrivateKey(npub)
    } catch (e: SecureStorageException) {
        Log.e("Account", "Failed to load key", e)
        null
    }
}

// Delete
suspend fun deleteAccount(npub: String) {
    secureStorage.deletePrivateKey(npub)
}
```

### Migration Steps

1. **Update LocalPreferences.kt:**
   - Add `SecureKeyStorage` instance
   - Replace `EncryptedStorage.preferences()` calls with `SecureKeyStorage` methods
   - Wrap in coroutines (`withContext(Dispatchers.IO)`)

2. **Update AccountSecretsEncryptedStores.kt:**
   - Already uses DataStore - can be replaced or kept for NWC secrets
   - Consider using `SecureKeyStorage` for consistency

3. **Handle Existing Keys:**
   - **Option A (Automatic Migration):** On first launch, read from old storage → save to new storage → delete old
   - **Option B (Keep Dual Storage):** Keep old code for read, use new for writes, phase out old storage later
   - **Option C (Clean Break):** Require users to re-enter nsec on first launch after update

4. **Desktop Integration:**
   - Create `SecureKeyStorage()` instance (no context needed)
   - Use same API calls as Android
   - Fallback password prompt if OS keyring unavailable (auto-triggered)

### Example Migration (Option A)

```kotlin
// In LocalPreferences
private val secureStorage = SecureKeyStorage(applicationContext)

suspend fun migrateToNewStorage(npub: String) {
    // Check if already migrated
    if (secureStorage.hasPrivateKey(npub)) return

    // Read from old storage
    val oldPrefs = EncryptedStorage.preferences(applicationContext, npub)
    val privKey = oldPrefs.getString(PrefKeys.NOSTR_PRIVKEY, null) ?: return

    // Save to new storage
    secureStorage.savePrivateKey(npub, privKey)

    // Clean up old storage (optional)
    oldPrefs.edit().remove(PrefKeys.NOSTR_PRIVKEY).apply()
}

suspend fun loadCurrentAccountFromEncryptedStorage(npub: String?): AccountSettings? {
    npub ?: return null

    // Auto-migrate if needed
    migrateToNewStorage(npub)

    // Load from new storage
    val privKey = secureStorage.getPrivateKey(npub)

    // ... rest of loading logic
}
```

## Desktop Fallback Behavior

If OS keyring unavailable (headless server, permission denied):

1. User prompted for master password on first `savePrivateKey()` call
2. Password cached in memory for session
3. Keys stored encrypted in `~/.amethyst/keys.enc`
4. Format: `<npub>:<base64-encrypted-key>` (one per line)
5. Encryption: AES-256-GCM with PBKDF2-derived key (100k iterations)

**Security Note:** Fallback is less secure than OS keyring. Warn users to use OS-integrated keychain when possible.

## Testing

### Android
```kotlin
@Test
fun testSecureStorage() = runTest {
    val storage = SecureKeyStorage(context)
    val npub = "npub1test..."
    val privKey = "nsec1test..."

    storage.savePrivateKey(npub, privKey)
    assertEquals(privKey, storage.getPrivateKey(npub))
    assertTrue(storage.hasPrivateKey(npub))

    storage.deletePrivateKey(npub)
    assertFalse(storage.hasPrivateKey(npub))
}
```

### Desktop
```kotlin
@Test
fun testDesktopStorage() = runTest {
    val storage = SecureKeyStorage()

    // Test with OS keyring (if available)
    storage.savePrivateKey("npub1test...", "nsec1test...")
    assertNotNull(storage.getPrivateKey("npub1test..."))
}
```

## Security Considerations

- **Android:** Keys protected by hardware when available (TEE/StrongBox)
- **Desktop:** Keys stored in OS-native credential managers (encrypted at rest)
- **Fallback:** User-password-protected file (PBKDF2 + AES-256-GCM)
- **Never log private keys** - use `SecureStorageException` for errors
- **Clear from memory** - keys returned as strings (consider SecureString for future enhancement)

## References

- Android Implementation: `quartz/src/androidMain/kotlin/.../SecureKeyStorage.kt`
- Desktop Implementation: `quartz/src/jvmMain/kotlin/.../SecureKeyStorage.kt`
- java-keyring: https://github.com/javakeyring/java-keyring
- Android EncryptedSharedPreferences: https://developer.android.com/topic/security/data
