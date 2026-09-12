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
     * per-call [Keyring.create] would prompt the user twice on startup — the
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

    actual suspend fun savePrivateKey(
        npub: String,
        privKeyHex: String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                if (keyringAvailable) {
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
                if (keyringAvailable) {
                    getFromKeyring(npub)
                } else {
                    getFromFallback(npub)
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
                if (keyringAvailable) {
                    deleteFromKeyring(npub)
                } else {
                    deleteFromFallback(npub)
                }
            } catch (e: BackendNotSupportedException) {
                keyringAvailable = false
                deleteFromFallback(npub)
            } catch (e: Exception) {
                throw SecureStorageException("Failed to delete private key", e)
            }
        }

    actual suspend fun hasPrivateKey(npub: String): Boolean = getPrivateKey(npub) != null

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
