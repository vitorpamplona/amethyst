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
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.account.AccountStorage
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import com.vitorpamplona.quartz.utils.Log
import java.io.File
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
class DesktopAccountStorage(
    private val secureStorage: SecureKeyStorage,
    private val homeDir: File = File(System.getProperty("user.home")),
) : AccountStorage {
    companion object {
        private const val METADATA_KEY_ALIAS = "account-metadata-key"
        private const val ACCOUNTS_FILE = "accounts.json.enc"
        private const val AES_KEY_SIZE = 32 // 256 bits
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
    }

    private val mapper = jacksonObjectMapper()
    private val amethystDir by lazy { File(homeDir, ".amethyst") }

    // --- AccountStorage interface ---

    override suspend fun loadAccounts(): List<AccountInfo> = readMetadata().accounts.map { it.toAccountInfo() }

    override suspend fun saveAccount(info: AccountInfo) {
        val metadata = readMetadata()
        val dto = AccountInfoDto.from(info)
        val updated = metadata.accounts.filter { it.npub != info.npub } + dto
        writeMetadata(metadata.copy(accounts = updated))
    }

    override suspend fun deleteAccount(npub: String) {
        val metadata = readMetadata()
        val updated = metadata.accounts.filter { it.npub != npub }
        val newActive =
            if (metadata.activeNpub == npub) {
                updated.firstOrNull()?.npub
            } else {
                metadata.activeNpub
            }
        writeMetadata(metadata.copy(accounts = updated, activeNpub = newActive))
    }

    override suspend fun currentAccount(): String? = readMetadata().activeNpub

    override suspend fun setCurrentAccount(npub: String) {
        val metadata = readMetadata()
        writeMetadata(metadata.copy(activeNpub = npub))
    }

    // --- Migration from single-account files ---

    suspend fun migrateFromLegacyFiles(accountManager: AccountManager): Boolean {
        val prefsFile = File(amethystDir, "last_account.txt")
        val bunkerFile = File(amethystDir, "bunker_uri.txt")

        if (!prefsFile.exists() && !bunkerFile.exists()) return false
        if (getAccountsFile().exists()) return false // already migrated

        val npub =
            prefsFile
                .takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return false

        val bunkerUri =
            bunkerFile
                .takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        val signerType =
            if (bunkerUri != null) {
                SignerType.Remote(bunkerUri)
            } else {
                val hasPrivKey = secureStorage.hasPrivateKey(npub)
                if (hasPrivKey) SignerType.Internal else SignerType.ViewOnly
            }

        val info = AccountInfo(npub = npub, signerType = signerType)
        val metadata = AccountMetadata(accounts = listOf(AccountInfoDto.from(info)), activeNpub = npub)
        writeMetadata(metadata)

        // Verify migration by reading back
        val verified = readMetadata()
        if (verified.accounts.any { it.npub == npub }) {
            // Migration verified — rename old files
            prefsFile.renameTo(File(amethystDir, "last_account.txt.bak"))
            bunkerFile.takeIf { it.exists() }?.renameTo(File(amethystDir, "bunker_uri.txt.bak"))
            Log.d("DesktopAccountStorage", "Migrated legacy account: $npub")
            return true
        }

        return false
    }

    // --- Encrypted file I/O ---

    private suspend fun readMetadata(): AccountMetadata {
        val file = getAccountsFile()
        if (!file.exists()) return AccountMetadata()

        return try {
            val encrypted = file.readBytes()
            val decrypted = decrypt(encrypted)
            mapper.readValue<AccountMetadata>(decrypted)
        } catch (e: Exception) {
            Log.e("DesktopAccountStorage", "Failed to read accounts metadata", e)
            AccountMetadata()
        }
    }

    private suspend fun writeMetadata(metadata: AccountMetadata) {
        ensureDir()
        val json = mapper.writeValueAsBytes(metadata)
        val encrypted = encrypt(json)

        // Atomic write via temp file
        val file = getAccountsFile()
        val temp = File(amethystDir, "${ACCOUNTS_FILE}.tmp")
        temp.writeBytes(encrypted)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)

        setFilePermissions(file)
    }

    private fun getAccountsFile() = File(amethystDir, ACCOUNTS_FILE)

    // --- AES-256-GCM encryption ---

    private suspend fun getOrCreateKey(): ByteArray {
        val existing = secureStorage.getPrivateKey(METADATA_KEY_ALIAS)
        if (existing != null) return Base64.getDecoder().decode(existing)

        val key = ByteArray(AES_KEY_SIZE).also { SecureRandom().nextBytes(it) }
        secureStorage.savePrivateKey(METADATA_KEY_ALIAS, Base64.getEncoder().encodeToString(key))
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
    val isTransient: Boolean = false,
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
                isTransient = info.isTransient,
            )
    }
}
