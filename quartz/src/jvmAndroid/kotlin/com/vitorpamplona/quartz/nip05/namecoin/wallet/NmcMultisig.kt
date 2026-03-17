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
import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

/**
 * M-of-N multisig configuration and script building for Namecoin.
 *
 * Supports:
 * - P2SH multisig (standard m-of-n up to 15 keys)
 * - Multisig address generation
 * - Partial signing workflow (collect signatures from multiple parties)
 * - Filesystem wallet cosigner import
 *
 * Typical flow:
 * 1. Create [MultisigConfig] with m, n, and all public keys
 * 2. Build unsigned transaction referencing the multisig output
 * 3. Each cosigner calls [partialSign] with their private key
 * 4. Combine partial signatures with [combineSignatures]
 * 5. Build the final scriptSig and broadcast
 */
object NmcMultisig {
    /** Maximum number of keys in a standard multisig. */
    const val MAX_MULTISIG_KEYS = 15

    // ── Multisig Script Building ───────────────────────────────────────

    /**
     * Build a bare multisig redeem script.
     *
     * Script: OP_<m> <pubkey1> <pubkey2> ... <pubkeyN> OP_<n> OP_CHECKMULTISIG
     *
     * @param requiredSigs Number of required signatures (m)
     * @param pubKeys Ordered list of compressed public keys
     * @return The redeem script bytes
     */
    fun buildRedeemScript(
        requiredSigs: Int,
        pubKeys: List<ByteArray>,
    ): ByteArray {
        require(requiredSigs in 1..pubKeys.size) { "Required sigs must be between 1 and ${pubKeys.size}" }
        require(pubKeys.size <= MAX_MULTISIG_KEYS) { "Maximum $MAX_MULTISIG_KEYS keys allowed" }
        pubKeys.forEach { require(it.size == 33) { "All public keys must be 33-byte compressed keys" } }

        val stream = ByteArrayOutputStream()
        // OP_m (OP_1 = 0x51, OP_2 = 0x52, etc.)
        stream.write(0x50 + requiredSigs)
        // Push each public key
        for (pk in pubKeys) {
            stream.write(NmcTransactionBuilder.pushData(pk))
        }
        // OP_n
        stream.write(0x50 + pubKeys.size)
        // OP_CHECKMULTISIG
        stream.write(0xae)
        return stream.toByteArray()
    }

    /**
     * Build a P2SH output script for a multisig redeem script.
     *
     * Script: OP_HASH160 <Hash160(redeemScript)> OP_EQUAL
     *
     * @return The P2SH scriptPubKey
     */
    fun buildP2SHScript(redeemScript: ByteArray): ByteArray {
        val scriptHash = NmcKeyManager.hash160(redeemScript)
        return byteArrayOf(
            0xa9.toByte(), // OP_HASH160
            0x14, // push 20 bytes
        ) + scriptHash +
            byteArrayOf(
                0x87.toByte(), // OP_EQUAL
            )
    }

    /**
     * Generate the P2SH address for a multisig setup.
     * Starts with '6' on Namecoin (script version byte 13).
     */
    fun multisigAddress(redeemScript: ByteArray): String {
        val scriptHash = NmcKeyManager.hash160(redeemScript)
        return NmcKeyManager.base58CheckEncode(byteArrayOf(NmcKeyManager.NMC_SCRIPT_VERSION) + scriptHash)
    }

    /**
     * Generate a multisig address from config.
     */
    fun multisigAddress(config: MultisigConfig): String {
        val redeemScript = buildRedeemScript(config.requiredSigs, config.pubKeys.map { Hex.decode(it) })
        return multisigAddress(redeemScript)
    }

    // ── Partial Signing ────────────────────────────────────────────────

    /**
     * Create a partial signature for one input of a multisig transaction.
     *
     * @param sighash The 32-byte SIGHASH_ALL digest for this input
     * @param privKey The signer's 32-byte private key
     * @return DER-encoded signature with SIGHASH_ALL byte appended
     */
    fun partialSign(
        sighash: ByteArray,
        privKey: ByteArray,
    ): ByteArray {
        val secp = Secp256k1.get()
        val compactSig = secp.sign(sighash, privKey)
        val derSig = secp.compact2der(compactSig)
        return derSig + byteArrayOf(0x01) // SIGHASH_ALL
    }

    /**
     * Build the final P2SH multisig scriptSig from collected signatures.
     *
     * ScriptSig: OP_0 <sig1> <sig2> ... <sigM> <redeemScript>
     *
     * Signatures must be in the same order as their corresponding
     * public keys appear in the redeem script.
     *
     * @param signatures Ordered DER+hashtype signatures (one per required signer)
     * @param redeemScript The full redeem script
     * @return The complete scriptSig for spending the P2SH output
     */
    fun buildMultisigScriptSig(
        signatures: List<ByteArray>,
        redeemScript: ByteArray,
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        // OP_0 (dummy for CHECKMULTISIG off-by-one bug)
        stream.write(0x00)
        // Push each signature
        for (sig in signatures) {
            stream.write(NmcTransactionBuilder.pushData(sig))
        }
        // Push the redeem script
        stream.write(NmcTransactionBuilder.pushData(redeemScript))
        return stream.toByteArray()
    }

    /**
     * Compute the Electrum-style scripthash for a multisig P2SH output.
     * Used to query balance/UTXOs from ElectrumX.
     */
    fun p2shScripthash(redeemScript: ByteArray): String {
        val p2shScript = buildP2SHScript(redeemScript)
        val sha256 = NmcKeyManager.sha256(p2shScript)
        return sha256.reversedArray().toHexKey()
    }
}

// ── Data Types ─────────────────────────────────────────────────────

/**
 * Configuration for an m-of-n multisig wallet.
 *
 * @param requiredSigs Number of signatures needed to spend (m)
 * @param pubKeys Hex-encoded compressed public keys in canonical order
 * @param label Optional human-readable label
 */
@Serializable
data class MultisigConfig(
    val requiredSigs: Int,
    val pubKeys: List<String>,
    val label: String = "",
) {
    val totalKeys: Int get() = pubKeys.size
    val description: String get() = "$requiredSigs-of-$totalKeys multisig"

    /** The redeem script for this config. */
    val redeemScript: ByteArray get() =
        NmcMultisig.buildRedeemScript(requiredSigs, pubKeys.map { Hex.decode(it) })

    /** The P2SH address for this multisig. */
    val address: String get() = NmcMultisig.multisigAddress(redeemScript)

    /** Scripthash for ElectrumX queries. */
    val scripthash: String get() = NmcMultisig.p2shScripthash(redeemScript)

    init {
        require(requiredSigs in 1..pubKeys.size) { "m must be 1..n" }
        require(pubKeys.size <= NmcMultisig.MAX_MULTISIG_KEYS) { "Max ${NmcMultisig.MAX_MULTISIG_KEYS} keys" }
    }
}

/**
 * A partially signed multisig transaction.
 * Tracks which cosigners have signed and collects their signatures.
 */
@Serializable
data class MultisigTransaction(
    val unsignedTxHex: String,
    val redeemScriptHex: String,
    val inputSighashes: List<String>,
    val config: MultisigConfig,
    val signatures: MutableMap<String, List<String>> = mutableMapOf(),
) {
    /** How many unique signers have contributed so far. */
    val signatureCount: Int get() = signatures.size

    /** Whether we have enough signatures to broadcast. */
    val isComplete: Boolean get() = signatureCount >= config.requiredSigs

    /** Which pubkeys have signed. */
    val signedBy: Set<String> get() = signatures.keys

    /** Which pubkeys still need to sign. */
    val pendingSigners: List<String> get() = config.pubKeys.filter { it !in signedBy }

    /**
     * Add a cosigner's signatures (one per input).
     *
     * @param pubKeyHex The signer's compressed public key (hex)
     * @param sigs DER+hashtype signatures, one per input (hex-encoded)
     */
    fun addSignatures(
        pubKeyHex: String,
        sigs: List<String>,
    ) {
        require(pubKeyHex in config.pubKeys) { "Public key not in multisig config" }
        require(sigs.size == inputSighashes.size) { "Need one signature per input" }
        signatures[pubKeyHex] = sigs
    }
}

/**
 * A cosigner wallet loaded from the filesystem.
 * Contains the public key and optionally the private key for signing.
 */
@Serializable
data class FilesystemCosigner(
    val label: String,
    val pubKeyHex: String,
    val sourcePath: String,
    val hasPrivateKey: Boolean = false,
) {
    /** Whether this cosigner can actively sign transactions. */
    val canSign: Boolean get() = hasPrivateKey
}
