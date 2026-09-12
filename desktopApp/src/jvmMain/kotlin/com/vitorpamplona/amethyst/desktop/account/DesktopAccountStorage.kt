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
package com.vitorpamplona.amethyst.desktop.account

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.keystorage.SecureStorageException
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.account.AccountStorage
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted account metadata storage for Desktop.
 *
 * Architecture:
 * - Account metadata (npub, signerType, active account) stored in encrypted JSON file
 * - Private keys (nsecs) stored separately in SecureKeyStorage (OS keychain)
 * - AES-256-GCM encryption key stored in OS keychain via SecureKeyStorage
 *
 * File: ~/.amethyst/accounts.json.enc
 */
sealed class StorageCorruption(
    val backupPath: String?,
) {
    class FileCorrupted(
        backupPath: String?,
    ) : StorageCorruption(backupPath)

    class JsonMalformed(
        backupPath: String?,
    ) : StorageCorruption(backupPath)

    /**
     * A transient failure surfaced from the read path (I/O error, keychain refused
     * or otherwise ambiguous access, OOM, etc). No backup was written and the
     * on-disk file is untouched. Callers should retry or surface an error UI rather
     * than treating this as data loss. See [DesktopAccountStorage.readMetadataFromDisk].
     */
    class TransientError(
        val cause: Throwable,
    ) : StorageCorruption(backupPath = null)
}

class DesktopAccountStorage(
    private val secureStorage: SecureKeyStorage,
    private val homeDir: File = File(System.getProperty("user.home")),
    private val onCorruption: (StorageCorruption) -> Unit = {},
) : AccountStorage {
    companion object {
        private const val METADATA_KEY_ALIAS = "account-metadata-key"
        private const val ACCOUNTS_FILE = "accounts.json.enc"
        private const val ACCOUNTS_LOCK_FILE = "accounts.json.enc.lock"
        private const val AES_KEY_SIZE = 32 // 256 bits
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
    }

    private val mapper = jacksonObjectMapper()
    private val amethystDir by lazy { File(homeDir, ".amethyst") }

    // In-memory cache: read from disk once, then serve from memory
    private var cachedMetadata: AccountMetadata? = null

    // In-process mutex around the cross-process file lock. Two callers inside
    // the same JVM would otherwise fail with OverlappingFileLockException from
    // FileChannel.lock(), since JVM file locks are per-JVM not per-thread.
    private val fileLockMutex = Mutex()

    // Guards read-modify-write cycles on [cachedMetadata]. Distinct from
    // [fileLockMutex] so we can hold it across a full read + mutate + write
    // sequence (the file lock is taken and released inside each disk op).
    private val stateMutex = Mutex()

    // --- AccountStorage interface ---

    override suspend fun loadAccounts(): List<AccountInfo> = getCachedMetadata().accounts.map { it.toAccountInfo() }

    override suspend fun saveAccount(info: AccountInfo) =
        stateMutex.withLock {
            val metadata = getCachedMetadata()
            val dto = AccountInfoDto.from(info)
            val updated = metadata.accounts.filter { it.npub != info.npub } + dto
            writeCachedMetadata(metadata.copy(accounts = updated))
        }

    override suspend fun deleteAccount(npub: String) =
        stateMutex.withLock {
            val metadata = getCachedMetadata()
            val updated = metadata.accounts.filter { it.npub != npub }
            val newActive =
                if (metadata.activeNpub == npub) {
                    updated.firstOrNull()?.npub
                } else {
                    metadata.activeNpub
                }
            writeCachedMetadata(metadata.copy(accounts = updated, activeNpub = newActive))
        }

    override suspend fun currentAccount(): String? = getCachedMetadata().activeNpub

    override suspend fun setCurrentAccount(npub: String) =
        stateMutex.withLock {
            val metadata = getCachedMetadata()
            writeCachedMetadata(metadata.copy(activeNpub = npub))
        }

    // --- Cached I/O ---

    private suspend fun getCachedMetadata(): AccountMetadata {
        cachedMetadata?.let { return it }
        val loaded = readMetadataFromDisk()
        cachedMetadata = loaded
        return loaded
    }

    /**
     * Persists first, caches second.
     *
     * If the disk write fails (keychain refused, I/O error, disk full) the in-memory
     * cache must NOT be left claiming a state that was never written: the rest of the
     * session would serve accounts that vanish on the next launch, and the user would
     * see a successful save that silently did nothing.
     */
    private suspend fun writeCachedMetadata(metadata: AccountMetadata) {
        writeMetadataToDisk(metadata)
        cachedMetadata = metadata
    }

    // --- Encrypted file I/O ---

    private suspend fun readMetadataFromDisk(): AccountMetadata {
        val file = getAccountsFile()
        if (!file.exists()) return AccountMetadata()

        ensureDir()
        return withAccountsFileLock {
            readMetadataFromDiskLocked(file)
        }
    }

    private suspend fun readMetadataFromDiskLocked(file: File): AccountMetadata {
        val encrypted = file.readBytes()
        if (encrypted.size < GCM_IV_SIZE) {
            // Genuinely unusable: not enough bytes for the IV. Back up and reset.
            val backup = backupCorruptFile(file, ".corrupt")
            onCorruption(StorageCorruption.FileCorrupted(backup))
            return AccountMetadata()
        }

        return try {
            val decrypted = decrypt(encrypted)
            mapper.readValue<AccountMetadata>(decrypted)
        } catch (e: javax.crypto.AEADBadTagException) {
            // Genuine ciphertext corruption or lost/rotated AES key.
            Log.e("DesktopAccountStorage", "GCM auth tag mismatch, file corrupted or key lost", e)
            val backup = backupCorruptFile(file, ".corrupt")
            onCorruption(StorageCorruption.FileCorrupted(backup))
            AccountMetadata()
        } catch (e: javax.crypto.BadPaddingException) {
            // Genuine ciphertext corruption.
            Log.e("DesktopAccountStorage", "Decryption failed, file corrupted", e)
            val backup = backupCorruptFile(file, ".corrupt")
            onCorruption(StorageCorruption.FileCorrupted(backup))
            AccountMetadata()
        } catch (e: com.fasterxml.jackson.core.JacksonException) {
            // Schema mismatch: decrypted cleanly but the JSON does not fit our shape.
            // Distinct suffix so operators can tell it apart from ciphertext corruption.
            Log.e("DesktopAccountStorage", "JSON malformed after decryption", e)
            val backup = backupCorruptFile(file, ".jsonerror")
            onCorruption(StorageCorruption.JsonMalformed(backup))
            AccountMetadata()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Transient failure: I/O error, keychain refused / ambiguous, OOM, etc.
            // DO NOT rename the on-disk file; the ciphertext is intact and the next
            // launch may succeed (for example after the user re-approves the
            // Keychain Access prompt). Surface up for the caller to decide.
            Log.e("DesktopAccountStorage", "Transient error reading accounts metadata; file preserved", e)
            onCorruption(StorageCorruption.TransientError(e))
            throw e
        }
    }

    private fun backupCorruptFile(
        file: File,
        suffix: String,
    ): String? =
        try {
            val backup = File(file.parent, "${file.name}$suffix.${System.currentTimeMillis()}")
            java.nio.file.Files
                .copy(file.toPath(), backup.toPath())
            file.deleteOrWarn("DesktopAccountStorage", "corrupt accounts file")
            backup.absolutePath
        } catch (_: Exception) {
            null
        }

    private suspend fun writeMetadataToDisk(metadata: AccountMetadata) {
        ensureDir()
        val json = mapper.writeValueAsBytes(metadata)
        val encrypted = encrypt(json)

        val file = getAccountsFile()
        withAccountsFileLock {
            // Atomic write via temp file, under the cross-process lock so two
            // Amethyst instances (Homebrew upgrade race, accidental double-launch)
            // cannot interleave writes and truncate the file.
            val temp = File(amethystDir, "$ACCOUNTS_FILE.tmp")
            temp.writeBytes(encrypted)
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            setFilePermissions(file)
        }
    }

    /**
     * Cross-process advisory lock + in-process mutex around the accounts.json.enc
     * read/write critical section. The mutex is required because JVM
     * `FileChannel.lock()` is a per-JVM lock and would throw
     * `OverlappingFileLockException` on the second acquire from the same JVM.
     * The channel lock is required to keep two Amethyst processes serial (upgrade
     * race, accidental double-launch, cron-style relaunch).
     *
     * Mirrors the pattern used in SecureKeyStorage.withFileLock; kept private
     * to this class so the two lock lifecycles stay independent.
     */
    private suspend inline fun <T> withAccountsFileLock(crossinline block: suspend () -> T): T =
        fileLockMutex.withLock {
            val lockFile = File(amethystDir, ACCOUNTS_LOCK_FILE)
            if (!lockFile.exists()) {
                lockFile.createNewFile()
                setFilePermissions(lockFile)
            }
            RandomAccessFile(lockFile, "rw").use { raf ->
                raf.channel.lock().use { _ ->
                    block()
                }
            }
        }

    private fun getAccountsFile() = File(amethystDir, ACCOUNTS_FILE)

    // --- AES-256-GCM encryption ---

    private var cachedKey: ByteArray? = null

    /**
     * Reads (or creates on first launch) the metadata AES key.
     *
     * Distinguishes:
     *   - key exists in keychain: use it
     *   - keychain confirms definitively absent: generate + persist a fresh key
     *   - any other outcome (user cancelled/denied prompt, keychain locked,
     *     backend transient error): propagate the exception, do NOT rotate --
     *     unless there is no accounts.json.enc yet, in which case there is no
     *     ciphertext to orphan and we bootstrap a fresh key (see below).
     *
     * Rotating the AES key on an ambiguous miss silently destroys the ability
     * to decrypt the existing accounts.json.enc, wiping the logged-in accounts
     * on next launch. That is the bug this method exists to prevent.
     */
    private suspend fun getOrCreateKey(): ByteArray {
        cachedKey?.let { return it }

        val existing =
            try {
                secureStorage.getPrivateKeyOrThrow(METADATA_KEY_ALIAS)
            } catch (e: SecureStorageException) {
                // Bootstrap escape. Every non-macOS backend java-keyring ships
                // (Windows Credential Store, Freedesktop Secret Service, KWallet)
                // throws PasswordAccessException for a *genuinely absent* credential,
                // so the strict lookup structurally cannot report "definitively
                // absent" there. Without this branch a fresh Linux/Windows install
                // could never mint the key and could never persist an account.
                //
                // Minting is only safe while there is no accounts.json.enc: with no
                // ciphertext on disk there is nothing a new key can orphan. Once the
                // file exists the strict contract applies and we propagate.
                if (getAccountsFile().exists()) throw e
                Log.w(
                    "DesktopAccountStorage",
                    "Keychain lookup failed and no accounts file exists; bootstrapping a fresh metadata key",
                    e,
                )
                null
            }

        if (existing != null) {
            val key = Base64.getDecoder().decode(existing)
            cachedKey = key
            return key
        }

        // Definitively absent (or bootstrapping with nothing on disk): safe to
        // create and persist a fresh key.
        val key = ByteArray(AES_KEY_SIZE).also { SecureRandom().nextBytes(it) }
        secureStorage.savePrivateKey(METADATA_KEY_ALIAS, Base64.getEncoder().encodeToString(key))
        cachedKey = key
        return key
    }

    private suspend fun encrypt(data: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(data)
        // Prepend IV to ciphertext
        return iv + ciphertext
    }

    private suspend fun decrypt(data: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val iv = data.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = data.copyOfRange(GCM_IV_SIZE, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    // --- File system helpers ---

    private fun ensureDir() {
        if (!amethystDir.exists()) amethystDir.mkdirs()
        try {
            Files.setPosixFilePermissions(
                amethystDir.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows
        } catch (_: Exception) {
        }
    }

    private fun setFilePermissions(file: File) {
        try {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
        } catch (_: Exception) {
        }
    }
}

/**
 * Internal DTO for JSON serialization.
 * Uses flat fields instead of polymorphic SignerType to keep serialization simple.
 */
internal data class AccountMetadata(
    val accounts: List<AccountInfoDto> = emptyList(),
    val activeNpub: String? = null,
)

internal data class AccountInfoDto(
    val npub: String,
    val signerKind: String, // "internal", "remote", "viewonly"
    val bunkerUri: String? = null,
    val displayName: String? = null,
    val isTransient: Boolean = false,
    val nwcPubKey: String? = null, // NWC wallet service pubkey (non-secret)
    val nwcRelay: String? = null, // NWC wallet relay URL (non-secret)
) {
    fun toAccountInfo(): AccountInfo =
        AccountInfo(
            npub = npub,
            signerType =
                when (signerKind) {
                    "remote" -> if (bunkerUri.isNullOrEmpty()) SignerType.Internal else SignerType.Remote(bunkerUri)
                    "viewonly" -> SignerType.ViewOnly
                    else -> SignerType.Internal
                },
            displayName = displayName,
            isTransient = isTransient,
        )

    companion object {
        fun from(info: AccountInfo): AccountInfoDto =
            AccountInfoDto(
                npub = info.npub,
                signerKind =
                    when (info.signerType) {
                        is SignerType.Internal -> "internal"
                        is SignerType.Remote -> "remote"
                        is SignerType.ViewOnly -> "viewonly"
                    },
                bunkerUri = (info.signerType as? SignerType.Remote)?.bunkerUri,
                displayName = info.displayName,
                isTransient = info.isTransient,
            )
    }
}
