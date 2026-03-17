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
package com.vitorpamplona.quartz.nip05.namecoin.wallet

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Import cosigner wallets from filesystem for multisig operations.
 *
 * Supports reading:
 * - **Electrum-NMC wallet files** (JSON): extracts public keys and
 *   optionally private keys (if wallet is not encrypted)
 * - **Plain WIF files**: one WIF key per line
 * - **Public key files**: hex-encoded compressed pubkeys, one per line
 * - **Amethyst multisig config files**: JSON with MultisigConfig format
 *
 * Typical usage:
 * 1. User points to a wallet file on disk (e.g., from Electrum-NMC)
 * 2. We extract the public key(s) for multisig setup
 * 3. When signing is needed, we load the private key if available
 */
object NmcFilesystemWallet {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Import cosigners from a file. Auto-detects format.
     *
     * @param path Absolute path to the wallet file
     * @return List of cosigners found in the file
     */
    fun importCosigners(path: String): List<FilesystemCosigner> {
        val file = File(path)
        require(file.exists()) { "File not found: $path" }
        require(file.canRead()) { "Cannot read file: $path" }

        val content = file.readText().trim()
        return when {
            content.startsWith("{") -> importFromJson(content, path)
            content.lines().all { it.isBlank() || isWifLike(it.trim()) } -> importFromWifFile(content, path)
            content.lines().all { it.isBlank() || isHexPubKey(it.trim()) } -> importFromPubKeyFile(content, path)
            else -> throw IllegalArgumentException("Unrecognized wallet file format")
        }
    }

    /**
     * Import a multisig config from a JSON file.
     */
    fun importMultisigConfig(path: String): MultisigConfig {
        val file = File(path)
        require(file.exists()) { "File not found: $path" }
        val content = file.readText().trim()
        return json.decodeFromString(MultisigConfig.serializer(), content)
    }

    /**
     * Export a multisig config to a JSON file.
     */
    fun exportMultisigConfig(
        config: MultisigConfig,
        path: String,
    ) {
        val content = json.encodeToString(MultisigConfig.serializer(), config)
        File(path).writeText(content)
    }

    /**
     * Export a partially signed transaction for cosigner pickup.
     */
    fun exportMultisigTransaction(
        tx: MultisigTransaction,
        path: String,
    ) {
        val content = json.encodeToString(MultisigTransaction.serializer(), tx)
        File(path).writeText(content)
    }

    /**
     * Import a partially signed transaction from a cosigner.
     */
    fun importMultisigTransaction(path: String): MultisigTransaction {
        val file = File(path)
        require(file.exists()) { "File not found: $path" }
        return json.decodeFromString(MultisigTransaction.serializer(), file.readText().trim())
    }

    /**
     * Load a private key from a filesystem wallet for signing.
     * Returns null if the wallet is encrypted or doesn't contain a private key.
     */
    fun loadPrivateKey(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null

        val content = file.readText().trim()
        return when {
            content.startsWith("{") -> {
                extractPrivKeyFromJson(content)
            }

            isWifLike(content.lines().firstOrNull()?.trim() ?: "") -> {
                val wif = content.lines().first().trim()
                NmcKeyManager.wifToPrivateKey(wif)
            }

            else -> {
                null
            }
        }
    }

    // ── JSON wallet import ─────────────────────────────────────────────

    private fun importFromJson(
        content: String,
        path: String,
    ): List<FilesystemCosigner> {
        val root = json.parseToJsonElement(content).jsonObject
        val cosigners = mutableListOf<FilesystemCosigner>()

        // Electrum-NMC format: look for "keystore" or "keystores"
        val keystore = root["keystore"]?.jsonObject
        if (keystore != null) {
            extractFromKeystore(keystore, path)?.let { cosigners.add(it) }
        }

        val keystores = root["keystores"]?.jsonObject
        if (keystores != null) {
            for ((label, ks) in keystores) {
                extractFromKeystore(ks.jsonObject, path, label)?.let { cosigners.add(it) }
            }
        }

        // Amethyst MultisigConfig format
        if (root.containsKey("requiredSigs") && root.containsKey("pubKeys")) {
            val config = json.decodeFromString(MultisigConfig.serializer(), content)
            config.pubKeys.forEachIndexed { i, pk ->
                cosigners.add(
                    FilesystemCosigner(
                        label = config.label.ifEmpty { "Key ${i + 1}" },
                        pubKeyHex = pk,
                        sourcePath = path,
                        hasPrivateKey = false,
                    ),
                )
            }
        }

        // Simple JSON with "pubkey" or "public_key" field
        val simplePubKey =
            root["pubkey"]?.jsonPrimitive?.content
                ?: root["public_key"]?.jsonPrimitive?.content
        if (simplePubKey != null && isHexPubKey(simplePubKey)) {
            val hasPriv = root.containsKey("privkey") || root.containsKey("private_key") || root.containsKey("wif")
            cosigners.add(
                FilesystemCosigner(
                    label = root["label"]?.jsonPrimitive?.content ?: File(path).nameWithoutExtension,
                    pubKeyHex = simplePubKey,
                    sourcePath = path,
                    hasPrivateKey = hasPriv,
                ),
            )
        }

        return cosigners
    }

    private fun extractFromKeystore(
        ks: JsonObject,
        path: String,
        label: String = "",
    ): FilesystemCosigner? {
        // Electrum-NMC keystore has "xpub" or "pubkey" field
        val type = ks["type"]?.jsonPrimitive?.content ?: ""

        // For hardware/watch-only: extract xpub-derived pubkey
        val pubKey =
            when {
                ks.containsKey("pubkey") -> ks["pubkey"]?.jsonPrimitive?.content

                ks.containsKey("xpub") -> null

                // Would need xpub derivation — skip for now
                else -> null
            }

        if (pubKey == null || !isHexPubKey(pubKey)) return null

        val hasPriv = ks.containsKey("xprv") || ks.containsKey("seed")

        return FilesystemCosigner(
            label = label.ifEmpty { type.ifEmpty { File(path).nameWithoutExtension } },
            pubKeyHex = pubKey,
            sourcePath = path,
            hasPrivateKey = hasPriv,
        )
    }

    private fun extractPrivKeyFromJson(content: String): ByteArray? {
        val root = json.parseToJsonElement(content).jsonObject

        // Direct WIF
        val wif =
            root["wif"]?.jsonPrimitive?.content
                ?: root["privkey"]?.jsonPrimitive?.content
                ?: root["private_key"]?.jsonPrimitive?.content
        if (wif != null) return NmcKeyManager.wifToPrivateKey(wif)

        // Direct hex key
        val hexKey = root["privkey_hex"]?.jsonPrimitive?.content
        if (hexKey != null && hexKey.length == 64) {
            return try {
                Hex.decode(hexKey)
            } catch (_: Exception) {
                null
            }
        }

        return null
    }

    // ── WIF file import ────────────────────────────────────────────────

    private fun importFromWifFile(
        content: String,
        path: String,
    ): List<FilesystemCosigner> {
        return content
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapIndexedNotNull { i, wif ->
                val privKey = NmcKeyManager.wifToPrivateKey(wif) ?: return@mapIndexedNotNull null
                val pubKey = NmcKeyManager.compressedPubKey(privKey)
                FilesystemCosigner(
                    label = "Key ${i + 1}",
                    pubKeyHex = pubKey.toHexKey(),
                    sourcePath = path,
                    hasPrivateKey = true,
                )
            }
    }

    // ── Public key file import ─────────────────────────────────────────

    private fun importFromPubKeyFile(
        content: String,
        path: String,
    ): List<FilesystemCosigner> =
        content
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapIndexed { i, hex ->
                FilesystemCosigner(
                    label = "Key ${i + 1}",
                    pubKeyHex = hex.lowercase(),
                    sourcePath = path,
                    hasPrivateKey = false,
                )
            }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun isWifLike(s: String): Boolean = s.length in 51..52 && (s.startsWith("T") || s.startsWith("K") || s.startsWith("L"))

    private fun isHexPubKey(s: String): Boolean = s.length == 66 && s.matches(Regex("^[0-9a-fA-F]+$"))
}
