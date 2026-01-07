/**
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
package com.vitorpamplona.quartz.nip01Core.crypto

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
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
    private fun saveToKeyring(
        npub: String,
        privKeyHex: String,
    ) {
        val keyring = Keyring.create()
        keyring.setPassword(SERVICE_NAME, npub, privKeyHex)
    }

    private fun getFromKeyring(npub: String): String? =
        try {
            val keyring = Keyring.create()
            keyring.getPassword(SERVICE_NAME, npub)
        } catch (e: PasswordAccessException) {
            null
        }

    private fun deleteFromKeyring(npub: String): Boolean =
        try {
            val keyring = Keyring.create()
            keyring.deletePassword(SERVICE_NAME, npub)
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
                        fallbackFile.delete()
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
            // Clean up temp file if it still exists
            if (tempFile.exists()) {
                tempFile.delete()
            }
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
                    readLine() ?: throw SecureStorageException("Password required for fallback storage")
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
