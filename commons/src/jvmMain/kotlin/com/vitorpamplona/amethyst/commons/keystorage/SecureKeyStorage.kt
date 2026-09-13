/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.keystorage

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop implementation of SecureKeyStorage using OS-native credential managers
 * (macOS Keychain, Windows Credential Manager, Linux Secret Service/KWallet).
 *
 * Falls back to encrypted file storage with user-provided password if OS keyring
 * is unavailable.
 *
 * ## Fallback Storage Security
 *
 * When OS keyring is unavailable, the implementation uses:
 * - **Encryption:** AES-256-GCM with PBKDF2 (100k iterations)
 * - **File Permissions:** Owner-only read/write (600 for files, 700 for directories) on Unix systems
 * - **Atomic Writes:** Temp file + atomic move to prevent corruption
 * - **File Locking:** Prevents concurrent access race conditions
 *
 * **Password Memory Limitation:** The fallback password is stored as a String and cannot be
 * securely zeroed from memory. It remains cached for the application lifetime to avoid repeated
 * password prompts. This is acceptable for desktop applications where the user's session is
 * already trusted, but may not be suitable for shared/multi-user systems.
 */
actual class SecureKeyStorage private actual constructor() {
    actual companion object {
        /**
         * Creates a SecureKeyStorage instance for Desktop.
         *
         * @param context Ignored on Desktop (no context needed)
         * @return SecureKeyStorage instance
         */
        actual fun create(context: Any?): SecureKeyStorage = SecureKeyStorage()

        private const val SERVICE_NAME = "amethyst-desktop"
        private const val FALLBACK_DIR = ".amethyst"
        private const val FALLBACK_FILE = "keys.enc"

        // Encryption constants for fallback
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_LENGTH = 256
        private const val ITERATION_COUNT = 100000
        private const val IV_LENGTH = 12 // GCM standard
    }

    private var keyringAvailable: Boolean = true
    private var fallbackPassword: String? = null
    private val fallbackMutex = Mutex() // Protects concurrent access to fallback file

    /**
     * Cached Keyring instance. Opening a Keyring session is expensive and, on
     * some OSes (notably macOS and locked GNOME/KWallet sessions), triggers a
     * user-visible unlock prompt every time. Callers hit the storage at least
     * twice on cold start (metadata AES key, then active account nsec), so a
     * per-call [Keyring.create] would prompt the user twice on startup, the
     * exact bug this cache fixes.
     *
     * Guarded by [keyringLock] so probing/opening the backend happens exactly
     * once per process; the [Keyring] itself is thread-safe once obtained.
     */
    @Volatile
    private var cachedKeyring: KeyringHandle? = null
    private val keyringLock = Any()

    /**
     * Package-private factory used by tests to inject a stub Keyring backend
     * and count backend-open invocations. Production code always defers to
     * [Keyring.create] through [RealKeyringHandle].
     */
    internal var keyringFactory: () -> KeyringHandle = { RealKeyringHandle(Keyring.create()) }

    /**
     * Consolidated single-item vault name. When [enableConsolidatedVault] has
     * run, every alias Amethyst owns is packed as one JSON blob under this
     * account name, so the OS keychain sees exactly one item to gate.
     *
     * macOS Keychain gates access per item, not per session, so caching the
     * [Keyring] handle alone cannot collapse the cold-boot double prompt
     * (metadata AES key plus the active account's nsec). One item, one ACL,
     * one prompt is the durable fix.
     */
    private val vaultAlias: String = "vault-v1"
    private val vaultMutex = Mutex()

    @Volatile
    private var vaultActive: Boolean = false

    /**
     * In-memory copy of the vault contents. Non-null iff [vaultActive] is
     * true. Guarded by [vaultMutex] for mutations; reads are lock-free via
     * the volatile reference plus a defensive copy inside [vaultGet].
     */
    @Volatile
    private var vaultContents: MutableMap<String, String>? = null

    actual suspend fun savePrivateKey(
        npub: String,
        privKeyHex: String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                if (vaultActive) {
                    vaultPut(npub, privKeyHex)
                } else if (keyringAvailable) {
                    saveToKeyring(npub, privKeyHex)
                } else {
                    saveToFallback(npub, privKeyHex)
                }
            } catch (e: BackendNotSupportedException) {
                keyringAvailable = false
                println("OS keyring not available, using fallback encrypted storage")
                saveToFallback(npub, privKeyHex)
            } catch (e: Exception) {
                throw SecureStorageException("Failed to save private key", e)
            }
        }
    }

    actual suspend fun getPrivateKey(npub: String): String? =
        withContext(Dispatchers.IO) {
            try {
                when {
                    // A vault miss is NOT proof of absence: the vault only covers the
                    // aliases a migration pass was given. Phase 1 activates it with just
                    // the metadata key, and a failed phase 2 leaves every nsec outside
                    // it. Fall back to the legacy per-alias item before reporting null.
                    vaultActive -> vaultGet(npub) ?: getFromKeyring(npub)
                    keyringAvailable -> getFromKeyring(npub)
                    else -> getFromFallback(npub)
                }
            } catch (e: BackendNotSupportedException) {
                keyringAvailable = false
                println("OS keyring not available, using fallback encrypted storage")
                getFromFallback(npub)
            } catch (e: PasswordAccessException) {
                null // Key doesn't exist
            } catch (e: Exception) {
                throw SecureStorageException("Failed to retrieve private key", e)
            }
        }

    /**
     * Strict variant that distinguishes "backend confirms item not found" from every
     * other outcome. This matters on macOS: `javakeyring` collapses `errSecItemNotFound`
     * (-25300), `errSecAuthFailed` (-25293), `errSecUserCanceled` (-128), and
     * `errSecInteractionNotAllowed` (-25308) into the same `PasswordAccessException`.
     * A caller that mistook "user clicked Deny" for "first launch, generate a fresh
     * key" would silently rotate the metadata AES key and permanently destroy the
     * accounts.json.enc it was supposed to unlock.
     *
     * On macOS this shells out to `/usr/bin/security find-generic-password`, whose
     * exit codes are documented and unambiguous (44 = not found, 128 = user cancel /
     * dialog dismissed, others = backend failure). On Windows / Linux, javakeyring
     * has no such ambiguity for the equivalent flows in practice, but we still treat
     * any `PasswordAccessException` here as ambiguous (throw) to keep the contract
     * strict on the getOrCreate path.
     */
    actual suspend fun getPrivateKeyOrThrow(npub: String): String? =
        withContext(Dispatchers.IO) {
            try {
                // The vault is authoritative for every alias it covers. Without this the
                // strict path probes the OS for a per-alias item the migration already
                // deleted, reads exit 44 / NotFound as "definitively absent", and lets
                // DesktopAccountStorage.getOrCreateKey mint a fresh AES key over the one
                // that decrypts accounts.json.enc -- the exact silent wipe this method
                // exists to prevent. A vault miss still falls through to the strict
                // per-alias probe, so uncovered aliases keep the strict contract.
                if (vaultActive) {
                    vaultGet(npub)?.let { return@withContext it }
                }
                if (!keyringAvailable) {
                    return@withContext getFromFallback(npub)
                }
                if (isMacOs()) {
                    return@withContext getFromMacSecurityCli(SERVICE_NAME, npub)
                }
                try {
                    keyring().getPassword(SERVICE_NAME, npub)
                } catch (e: PasswordAccessException) {
                    // Non-mac backends: keep the strict contract by refusing to
                    // treat this as "definitively absent". A caller that needs a
                    // permissive lookup should use getPrivateKey() instead.
                    throw SecureStorageException(
                        "Keyring backend refused access or returned ambiguous not-found",
                        e,
                    )
                }
            } catch (e: SecureStorageException) {
                throw e
            } catch (e: BackendNotSupportedException) {
                keyringAvailable = false
                println("OS keyring not available, using fallback encrypted storage")
                getFromFallback(npub)
            } catch (e: Exception) {
                throw SecureStorageException("Failed to retrieve private key (strict)", e)
            }
        }

    /**
     * Test seam: overridable strategy for the strict macOS lookup. Production wires
     * to [defaultMacSecurityLookup] which spawns `/usr/bin/security`. Tests replace
     * this with a stub so unit tests run hermetically on any OS.
     */
    internal var macSecurityLookup: (String, String) -> MacSecurityResult =
        ::defaultMacSecurityLookup

    private fun getFromMacSecurityCli(
        service: String,
        account: String,
    ): String? {
        val result = macSecurityLookup(service, account)
        return when (result) {
            is MacSecurityResult.Found -> result.password
            is MacSecurityResult.NotFound -> null
            is MacSecurityResult.Ambiguous -> throw SecureStorageException(
                "macOS Keychain access failed (${result.reason}, exit=${result.exitCode})",
            )
        }
    }

    actual suspend fun deletePrivateKey(npub: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                when {
                    vaultActive -> vaultDelete(npub)
                    keyringAvailable -> deleteFromKeyring(npub)
                    else -> deleteFromFallback(npub)
                }
            } catch (e: BackendNotSupportedException) {
                keyringAvailable = false
                deleteFromFallback(npub)
            } catch (e: Exception) {
                throw SecureStorageException("Failed to delete private key", e)
            }
        }

    actual suspend fun hasPrivateKey(npub: String): Boolean = getPrivateKey(npub) != null

    // --- Consolidated vault (see [vaultAlias] docs) ---

    /**
     * Consolidates [candidateAliases] into a single OS keychain item named
     * [vaultAlias]. On the next cold boot only that one item is read, so the
     * OS surfaces at most one Keychain Access prompt regardless of how many
     * secrets Amethyst manages.
     *
     * Semantics:
     *
     * 1. If [vaultAlias] already exists, load it into memory and mark
     *    [vaultActive]. Legacy per-alias items are not touched, zero extra
     *    prompts on that path.
     * 2. If [vaultAlias] is absent, batch-read each candidate alias in the
     *    legacy per-item layout (paying the migration prompt once), pack the
     *    recovered entries into the vault item, and delete the legacy items.
     * 3. If neither the vault nor any legacy alias exists (fresh install),
     *    the vault becomes an empty active map; subsequent writes go
     *    straight into it.
     *
     * Migration is idempotent and cheap when the vault already exists (one
     * keychain read plus a JSON parse). Safe to call on every cold boot and
     * safe to call multiple times per process: additional calls extend the
     * vault with any newly-discovered legacy aliases and never rewrite the
     * item if the delta is empty.
     *
     * Legacy items are deleted only after the vault write succeeds, so a
     * crash mid-migration leaves the legacy items in place and the next run
     * retries cleanly. No data loss window.
     *
     * The fallback (no-keyring) storage path is not migrated: it already
     * uses a single encrypted file, so it does not have the per-item ACL
     * problem the vault exists to solve.
     */
    suspend fun enableConsolidatedVault(candidateAliases: List<String>) {
        withContext(Dispatchers.IO) {
            vaultMutex.withLock {
                if (!keyringAvailable) return@withLock // fallback path doesn't need the vault
                try {
                    val handle = keyring()
                    if (!vaultActive) {
                        val existing =
                            try {
                                handle.getPassword(SERVICE_NAME, vaultAlias)
                            } catch (_: PasswordAccessException) {
                                null
                            }
                        if (existing != null) {
                            vaultContents = decodeVault(existing).toMutableMap()
                            vaultActive = true
                            // Fall through to the fold-in pass so any legacy per-alias
                            // items left behind by a partial earlier migration get
                            // absorbed on this cold boot.
                        } else {
                            // Fresh migration path: batch-read every candidate alias.
                            val collected = LinkedHashMap<String, String>()
                            for (alias in candidateAliases) {
                                try {
                                    collected[alias] = handle.getPassword(SERVICE_NAME, alias)
                                } catch (_: PasswordAccessException) {
                                    // absent, skip
                                }
                            }
                            // Write the vault first, delete legacy items only after the write
                            // succeeded. Empty vaults are still written so a fresh install ends
                            // up in vault mode (subsequent savePrivateKey calls populate it).
                            handle.setPassword(SERVICE_NAME, vaultAlias, encodeVault(collected))
                            for (alias in collected.keys) {
                                try {
                                    handle.deletePassword(SERVICE_NAME, alias)
                                } catch (_: PasswordAccessException) {
                                    // already gone, fine
                                }
                            }
                            vaultContents = collected
                            vaultActive = true
                            return@withLock
                        }
                    }

                    // Fold-in pass. Runs on:
                    //   - a second `enableConsolidatedVault(fullList)` call after the
                    //     phase-1 metadata-key-only bootstrap, and
                    //   - a cold boot that finds `vault-v1` alongside legacy per-alias
                    //     items from an interrupted earlier migration.
                    val current = vaultContents ?: LinkedHashMap()
                    val additions = LinkedHashMap<String, String>()
                    for (alias in candidateAliases) {
                        if (alias in current) continue
                        val legacy =
                            try {
                                handle.getPassword(SERVICE_NAME, alias)
                            } catch (_: PasswordAccessException) {
                                null
                            } ?: continue
                        additions[alias] = legacy
                    }
                    if (additions.isNotEmpty()) {
                        val merged = LinkedHashMap(current).also { it.putAll(additions) }
                        handle.setPassword(SERVICE_NAME, vaultAlias, encodeVault(merged))
                        for (alias in additions.keys) {
                            try {
                                handle.deletePassword(SERVICE_NAME, alias)
                            } catch (_: PasswordAccessException) {
                                // already gone, fine
                            }
                        }
                        vaultContents = merged
                    }
                } catch (e: BackendNotSupportedException) {
                    keyringAvailable = false
                    println("OS keyring not available during vault migration, keeping fallback storage")
                } catch (e: Exception) {
                    // Migration is best-effort: if the OS keychain is misbehaving we leave
                    // the legacy per-alias items in place and continue in legacy mode.
                    println("enableConsolidatedVault: aborting migration: ${e.message}")
                }
            }
        }
    }

    /** Returns true iff the consolidated vault has been loaded or migrated. */
    fun isVaultActive(): Boolean = vaultActive

    /**
     * Test-only accessor for the current in-memory alias set. Kept internal
     * so tests in the same module can assert vault contents without exposing
     * secrets to app code.
     */
    internal fun snapshotCacheKeys(): Set<String> = vaultContents?.keys?.toSet() ?: emptySet()

    private fun vaultGet(alias: String): String? = vaultContents?.get(alias)

    private fun vaultPut(
        alias: String,
        value: String,
    ) {
        val contents = vaultContents ?: LinkedHashMap<String, String>().also { vaultContents = it }
        contents[alias] = value
        keyring().setPassword(SERVICE_NAME, vaultAlias, encodeVault(contents))
    }

    /**
     * Removes [alias] from the vault *and* unlinks any legacy per-alias item still
     * holding it. An alias the vault does not cover (a partial migration, or a
     * phase 2 that failed) would otherwise survive a logout as an orphaned secret
     * in the OS keychain, since [getPrivateKey] can still read it.
     */
    private fun vaultDelete(alias: String): Boolean {
        val contents = vaultContents
        val removedFromVault = contents != null && contents.remove(alias) != null

        val removedLegacy =
            try {
                keyring().deletePassword(SERVICE_NAME, alias)
                true
            } catch (_: PasswordAccessException) {
                false // no legacy item, fine
            }

        if (removedFromVault) {
            if (contents!!.isEmpty()) {
                try {
                    keyring().deletePassword(SERVICE_NAME, vaultAlias)
                } catch (_: PasswordAccessException) {
                    // already gone, still removed from our POV
                }
            } else {
                keyring().setPassword(SERVICE_NAME, vaultAlias, encodeVault(contents))
            }
        }

        return removedFromVault || removedLegacy
    }

    /**
     * Envelope: `{"schemaVersion":1,"entries":{alias: base64(secret), …}}`.
     *
     * The [schemaVersion] field reserves room for future migrations. Values
     * are base64-encoded so alias/secret contents that contain quotes,
     * backslashes, control chars, or non-ASCII round-trip cleanly through the
     * hand-rolled JSON codec (kept hand-rolled to avoid pulling Jackson into
     * the keystorage module; Jackson lives in desktopApp / quartz).
     */
    private fun encodeVault(map: Map<String, String>): String {
        val sb = StringBuilder("{\"schemaVersion\":1,\"entries\":{")
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            sb
                .append('"')
                .append(jsonEscape(k))
                .append('"')
                .append(':')
                .append('"')
                .append(Base64.getEncoder().encodeToString(v.toByteArray(Charsets.UTF_8)))
                .append('"')
        }
        sb.append("}}")
        return sb.toString()
    }

    private fun decodeVault(raw: String): Map<String, String> {
        val trimmed = raw.trim()
        if (trimmed.length < 2 || trimmed.first() != '{' || trimmed.last() != '}') return emptyMap()
        val body = trimmed.substring(1, trimmed.length - 1)
        if (body.isBlank()) return emptyMap()

        // Minimal object parser: expect a mix of "key":number and "key":"string"
        // pairs at the top level, plus a nested "entries":{ ... } object holding
        // base64-encoded alias values. Bare-map layouts written by a hypothetical
        // earlier vault format are still accepted (fallback path).
        var i = 0
        var entriesRaw: String? = null
        while (i < body.length) {
            if (body[i] != '"') return decodeBareEntries(body)
            val keyEnd = findUnescapedQuote(body, i + 1)
            if (keyEnd < 0) return emptyMap()
            val key = jsonUnescape(body.substring(i + 1, keyEnd))
            i = keyEnd + 1
            if (i >= body.length || body[i] != ':') return emptyMap()
            i += 1
            if (i >= body.length) return emptyMap()
            when (body[i]) {
                '"' -> {
                    val valEnd = findUnescapedQuote(body, i + 1)
                    if (valEnd < 0) return emptyMap()
                    // String-valued top-level fields are ignored except for legacy
                    // format detection handled by decodeBareEntries.
                    i = valEnd + 1
                }
                '{' -> {
                    val objEnd = findMatchingBrace(body, i)
                    if (objEnd < 0) return emptyMap()
                    if (key == "entries") {
                        entriesRaw = body.substring(i + 1, objEnd)
                    }
                    i = objEnd + 1
                }
                else -> {
                    // Skip a bare token (schemaVersion number, boolean, null).
                    while (i < body.length && body[i] != ',' && body[i] != '}') i += 1
                }
            }
            if (i < body.length) {
                if (body[i] != ',') return emptyMap()
                i += 1
            }
        }
        return entriesRaw?.let { decodeBareEntries(it) } ?: emptyMap()
    }

    /** Parses a `"k":"b64","k2":"b64"` body into `{k: decoded, k2: decoded}`. */
    private fun decodeBareEntries(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return out
        var i = 0
        while (i < trimmed.length) {
            if (trimmed[i] != '"') return emptyMap()
            val keyEnd = findUnescapedQuote(trimmed, i + 1)
            if (keyEnd < 0) return emptyMap()
            val key = jsonUnescape(trimmed.substring(i + 1, keyEnd))
            i = keyEnd + 1
            if (i >= trimmed.length || trimmed[i] != ':') return emptyMap()
            i += 1
            if (i >= trimmed.length || trimmed[i] != '"') return emptyMap()
            val valEnd = findUnescapedQuote(trimmed, i + 1)
            if (valEnd < 0) return emptyMap()
            val b64 = trimmed.substring(i + 1, valEnd)
            val value =
                try {
                    String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
                } catch (_: IllegalArgumentException) {
                    return emptyMap()
                }
            out[key] = value
            i = valEnd + 1
            if (i < trimmed.length) {
                if (trimmed[i] != ',') return emptyMap()
                i += 1
            }
        }
        return out
    }

    private fun findUnescapedQuote(
        s: String,
        from: Int,
    ): Int {
        var i = from
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                '"' -> return i
                else -> i += 1
            }
        }
        return -1
    }

    private fun findMatchingBrace(
        s: String,
        openAt: Int,
    ): Int {
        var depth = 0
        var i = openAt
        while (i < s.length) {
            when (s[i]) {
                '"' -> {
                    val end = findUnescapedQuote(s, i + 1)
                    if (end < 0) return -1
                    i = end + 1
                }
                '{' -> {
                    depth += 1
                    i += 1
                }
                '}' -> {
                    depth -= 1
                    if (depth == 0) return i
                    i += 1
                }
                else -> i += 1
            }
        }
        return -1
    }

    private fun jsonEscape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun jsonUnescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i += 1
            }
        }
        return sb.toString()
    }

    // Keyring-based storage

    /**
     * Returns the process-wide [Keyring] instance, opening the OS-native
     * backend on first call. Subsequent calls reuse the same handle so the
     * user is only prompted (macOS Keychain Access, Secret Service unlock,
     * KWallet unlock) once per app run.
     *
     * Callers must handle [BackendNotSupportedException] — it can escape on
     * the very first call if no backend is available at all.
     */
    private fun keyring(): KeyringHandle {
        cachedKeyring?.let { return it }
        return synchronized(keyringLock) {
            cachedKeyring ?: keyringFactory().also { cachedKeyring = it }
        }
    }

    private fun saveToKeyring(
        npub: String,
        privKeyHex: String,
    ) {
        keyring().setPassword(SERVICE_NAME, npub, privKeyHex)
    }

    private fun getFromKeyring(npub: String): String? =
        try {
            keyring().getPassword(SERVICE_NAME, npub)
        } catch (e: PasswordAccessException) {
            null
        }

    private fun deleteFromKeyring(npub: String): Boolean =
        try {
            keyring().deletePassword(SERVICE_NAME, npub)
            true
        } catch (e: PasswordAccessException) {
            false
        }

    // Fallback encrypted file storage
    private suspend fun saveToFallback(
        npub: String,
        privKeyHex: String,
    ) {
        fallbackMutex.withLock {
            val password = getFallbackPassword()
            val encrypted = encryptData(privKeyHex, password)

            val fallbackFile = getFallbackFile()

            // Create directory with restrictive permissions
            fallbackFile.parentFile?.let { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                    setRestrictivePermissions(dir)
                }
            }

            withFileLock(fallbackFile) {
                val data = loadFallbackDataUnsafe().toMutableMap()
                data[npub] = encrypted
                atomicWriteFallbackData(fallbackFile, data)
            }
        }
    }

    private suspend fun getFromFallback(npub: String): String? {
        val password = fallbackPassword ?: return null // No password set yet

        return fallbackMutex.withLock {
            val fallbackFile = getFallbackFile()
            if (!fallbackFile.exists()) return@withLock null

            withFileLock(fallbackFile) {
                val data = loadFallbackDataUnsafe()
                val encrypted = data[npub] ?: return@withFileLock null

                try {
                    decryptData(encrypted, password)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private suspend fun deleteFromFallback(npub: String): Boolean {
        return fallbackMutex.withLock {
            val fallbackFile = getFallbackFile()
            if (!fallbackFile.exists()) return@withLock false

            withFileLock(fallbackFile) {
                val data = loadFallbackDataUnsafe().toMutableMap()
                val existed = data.remove(npub) != null

                if (existed) {
                    if (data.isEmpty()) {
                        fallbackFile.deleteOrWarn("SecureKeyStorage", "fallback key file")
                    } else {
                        atomicWriteFallbackData(fallbackFile, data)
                    }
                }

                existed
            }
        }
    }

    /**
     * Loads fallback data without locking. Caller must hold mutex and file lock.
     */
    private fun loadFallbackDataUnsafe(): Map<String, String> {
        val fallbackFile = getFallbackFile()
        if (!fallbackFile.exists()) return emptyMap()

        return fallbackFile
            .readLines()
            .mapNotNull { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
    }

    /**
     * Atomically writes fallback data using temp file + rename.
     */
    private fun atomicWriteFallbackData(
        fallbackFile: File,
        data: Map<String, String>,
    ) {
        val tempFile = File(fallbackFile.parentFile, "${fallbackFile.name}.tmp")
        try {
            // Write to temp file
            tempFile.writeText(data.entries.joinToString("\n") { "${it.key}:${it.value}" })
            setRestrictivePermissions(tempFile)

            // Atomic rename
            Files.move(
                tempFile.toPath(),
                fallbackFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            // Clean up any leftover temp file
            tempFile.deleteOrWarn("SecureKeyStorage", "temp key file")
        }
    }

    /**
     * Executes block with file lock held.
     */
    private fun <T> withFileLock(
        file: File,
        block: () -> T,
    ): T {
        // Ensure lock file exists
        val lockFile = File(file.parentFile, "${file.name}.lock")
        lockFile.parentFile?.mkdirs()
        if (!lockFile.exists()) {
            lockFile.createNewFile()
            setRestrictivePermissions(lockFile)
        }

        return RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use { lock ->
                block()
            }
        }
    }

    private fun getFallbackFile(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, "$FALLBACK_DIR/$FALLBACK_FILE")
    }

    private fun getFallbackPassword(): String {
        if (fallbackPassword == null) {
            println("OS keyring not available. Fallback encrypted storage requires a password.")
            val console = System.console()
            fallbackPassword =
                if (console != null) {
                    // Use Console.readPassword() for masked input
                    val password = console.readPassword("Enter master password: ")
                    password?.let {
                        val str = String(it)
                        it.fill('\u0000') // Clear the char array from memory
                        str
                    } ?: throw SecureStorageException("Password required for fallback storage")
                } else {
                    // Fallback for non-interactive environments (testing, etc.)
                    print("Enter master password: ")
                    readlnOrNull() ?: throw SecureStorageException("Password required for fallback storage")
                }
        }
        return fallbackPassword!!
    }

    private fun setRestrictivePermissions(file: File) {
        try {
            val path = file.toPath()
            // Set owner-only read/write permissions (600 for files, 700 for directories)
            val permissions =
                if (file.isDirectory) {
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    )
                } else {
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                    )
                }
            Files.setPosixFilePermissions(path, permissions)
        } catch (e: UnsupportedOperationException) {
            // Windows doesn't support POSIX permissions - file system security handles this
            // No action needed
        } catch (e: Exception) {
            // Log but don't fail - permissions are a security enhancement, not critical
            System.err.println("Warning: Could not set restrictive file permissions: ${e.message}")
        }
    }

    private fun encryptData(
        plaintext: String,
        password: String,
    ): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec)
        val key = SecretKeySpec(secretKey.encoded, ALGORITHM)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray())

        val combined = salt + iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decryptData(
        ciphertext: String,
        password: String,
    ): String {
        val combined = Base64.getDecoder().decode(ciphertext)

        val salt = combined.copyOfRange(0, 16)
        val iv = combined.copyOfRange(16, 16 + IV_LENGTH)
        val encrypted = combined.copyOfRange(16 + IV_LENGTH, combined.size)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec)
        val key = SecretKeySpec(secretKey.encoded, ALGORITHM)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val decrypted = cipher.doFinal(encrypted)

        return String(decrypted)
    }
}

/**
 * Small package-private abstraction over `com.github.javakeyring.Keyring`,
 * mirroring the three operations `SecureKeyStorage` actually uses. The real
 * implementation is a thin delegator; tests substitute an in-memory version
 * so the desktop unit test suite doesn't touch the OS Keychain (which would
 * be non-hermetic and slow, and on macOS would surface a user-visible prompt
 * during test runs).
 *
 * Not part of the public API — kept in this file so it stays private to the
 * keystorage package.
 */
internal interface KeyringHandle {
    @Throws(PasswordAccessException::class)
    fun getPassword(
        service: String,
        account: String,
    ): String

    @Throws(PasswordAccessException::class)
    fun setPassword(
        service: String,
        account: String,
        password: String,
    )

    @Throws(PasswordAccessException::class)
    fun deletePassword(
        service: String,
        account: String,
    )
}

/**
 * Outcome of a strict macOS `/usr/bin/security find-generic-password` lookup.
 * Kept as a sealed hierarchy so [SecureKeyStorage.getPrivateKeyOrThrow] can
 * cleanly translate to `null` versus `SecureStorageException`.
 */
internal sealed class MacSecurityResult {
    data class Found(
        val password: String,
    ) : MacSecurityResult()

    object NotFound : MacSecurityResult()

    /**
     * Any exit code other than 0 (found) or 44 (item not found). Reason is a short
     * human string derived from stderr / documented codes:
     *   128 = user cancelled or dismissed the Keychain Access dialog
     *   -25293 (errSecAuthFailed) surfaces as exit 51 in practice
     *   -25308 (errSecInteractionNotAllowed) surfaces when Keychain is locked
     */
    data class Ambiguous(
        val exitCode: Int,
        val reason: String,
    ) : MacSecurityResult()
}

/**
 * Pure parser split out for testability on non-macOS CI runners. Maps the
 * documented exit code contract of `/usr/bin/security find-generic-password`
 * to a [MacSecurityResult]. `stdout` is the raw password body (`-w` prints it
 * followed by a newline; strip the trailing newline only). `stderr` is used
 * as a hint for the ambiguous [MacSecurityResult.Ambiguous.reason] string.
 */
internal fun parseMacSecurityFindResult(
    exitCode: Int,
    stdout: String,
    stderr: String,
): MacSecurityResult =
    when (exitCode) {
        0 -> MacSecurityResult.Found(stdout.trimEnd('\n', '\r'))
        44 -> MacSecurityResult.NotFound
        else -> {
            val reason =
                when {
                    exitCode == 128 -> "user cancelled Keychain dialog"
                    stderr.contains("-25293") -> "errSecAuthFailed"
                    stderr.contains("-25308") -> "errSecInteractionNotAllowed"
                    stderr.contains("-128") -> "user cancelled Keychain dialog"
                    stderr.isNotBlank() ->
                        stderr
                            .lineSequence()
                            .first()
                            .trim()
                            .take(120)
                    else -> "unknown"
                }
            MacSecurityResult.Ambiguous(exitCode, reason)
        }
    }

private fun isMacOs(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

/**
 * Production implementation: spawn `/usr/bin/security` and read exit code + streams.
 * Kept package-private so tests can also reach it if they want to run the real path
 * on a mac host, but production always goes through the [SecureKeyStorage.macSecurityLookup]
 * indirection.
 */
internal fun defaultMacSecurityLookup(
    service: String,
    account: String,
): MacSecurityResult {
    val process =
        try {
            ProcessBuilder(
                "/usr/bin/security",
                "find-generic-password",
                "-s",
                service,
                "-a",
                account,
                "-w",
            ).redirectErrorStream(false).start()
        } catch (e: Exception) {
            return MacSecurityResult.Ambiguous(-1, "failed to spawn /usr/bin/security: ${e.message ?: e::class.simpleName ?: "unknown"}")
        }
    process.outputStream.close()
    val stdout = process.inputStream.bufferedReader().use { it.readText() }
    val stderr = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    return parseMacSecurityFindResult(exitCode, stdout, stderr)
}

internal class RealKeyringHandle(
    private val keyring: Keyring,
) : KeyringHandle {
    override fun getPassword(
        service: String,
        account: String,
    ): String = keyring.getPassword(service, account)

    override fun setPassword(
        service: String,
        account: String,
        password: String,
    ) {
        keyring.setPassword(service, account, password)
    }

    override fun deletePassword(
        service: String,
        account: String,
    ) {
        keyring.deletePassword(service, account)
    }
}
