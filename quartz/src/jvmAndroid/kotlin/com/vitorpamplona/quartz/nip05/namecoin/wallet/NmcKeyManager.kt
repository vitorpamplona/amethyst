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

import com.vitorpamplona.quartz.nip03Timestamp.ots.crypto.RIPEMD160Digest
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip32SeedDerivation
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics
import com.vitorpamplona.quartz.nip06KeyDerivation.Hardener
import com.vitorpamplona.quartz.nip06KeyDerivation.KeyPath
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Namecoin key management: derivation, address encoding, WIF export.
 *
 * Uses BIP44 derivation path m/44'/7'/0'/0/0 (coin type 7 = Namecoin).
 * Reuses Amethyst's existing [Bip32SeedDerivation] and [Secp256k1Instance].
 *
 * Both Nostr and Namecoin use secp256k1, so the same mnemonic seed can
 * produce keys for both networks at different derivation paths.
 */
object NmcKeyManager {
    /** Namecoin BIP44 coin type. */
    private const val NMC_COIN_TYPE = 7L

    /** Address version bytes. */
    const val NMC_PUBKEY_VERSION: Byte = 52 // 'N' prefix
    const val NMC_SCRIPT_VERSION: Byte = 13 // '6' prefix
    const val NMC_WIF_VERSION: Byte = 0xB4.toByte() // 180

    private val bip32 = Bip32SeedDerivation()

    // ── Key Derivation ─────────────────────────────────────────────────

    /** BIP44 path: m/44'/7'/<account>'/0/<index> */
    private fun nmcPath(
        account: Long = 0,
        index: Long = 0,
    ): KeyPath =
        KeyPath("")
            .derive(Hardener.hardened(44L))
            .derive(Hardener.hardened(NMC_COIN_TYPE))
            .derive(Hardener.hardened(account))
            .derive(0L)
            .derive(index)

    /**
     * Derive a Namecoin private key from a BIP39 mnemonic.
     *
     * @param mnemonic BIP39 mnemonic phrase (same one used for Nostr via NIP-06)
     * @param account BIP44 account index (default 0)
     * @param index Address index within the account (default 0)
     * @return 32-byte private key
     */
    fun privateKeyFromMnemonic(
        mnemonic: String,
        account: Long = 0,
        index: Long = 0,
    ): ByteArray {
        val seed = Bip39Mnemonics.toSeed(mnemonic, "")
        return privateKeyFromSeed(seed, account, index)
    }

    /**
     * Derive a Namecoin private key from a raw BIP39 seed.
     */
    fun privateKeyFromSeed(
        seed: ByteArray,
        account: Long = 0,
        index: Long = 0,
    ): ByteArray = bip32.derivePrivateKey(bip32.generate(seed), nmcPath(account, index))

    /**
     * Derive a Namecoin private key directly from a Nostr private key
     * using BIP32 hardened derivation. This allows users who don't have
     * a mnemonic to still get a deterministic NMC key.
     *
     * Derivation: HMAC-SHA512("Namecoin-from-Nostr", nostr_privkey)
     * then take first 32 bytes as the NMC private key.
     */
    fun privateKeyFromNostrKey(nostrPrivKey: ByteArray): ByteArray {
        val hmac = bip32.hmac512("Namecoin-from-Nostr".toByteArray(), nostrPrivKey)
        val candidate = hmac.copyOfRange(0, 32)
        require(Secp256k1Instance.isPrivateKeyValid(candidate)) {
            "Derived NMC key is invalid (astronomically unlikely)"
        }
        return candidate
    }

    /**
     * Get the 33-byte compressed public key for a private key.
     */
    fun compressedPubKey(privKey: ByteArray): ByteArray = Secp256k1Instance.compressedPubKeyFor(privKey)

    // ── Address Generation ─────────────────────────────────────────────

    /**
     * Generate a Namecoin P2PKH address from a private key.
     * Address format: Base58Check(version_byte + RIPEMD160(SHA256(compressed_pubkey)))
     *
     * @return Namecoin address starting with 'N' or 'M'
     */
    fun addressFromPrivKey(privKey: ByteArray): String {
        val pubKey = compressedPubKey(privKey)
        return addressFromPubKey(pubKey)
    }

    /**
     * Generate a Namecoin P2PKH address from a compressed public key.
     */
    fun addressFromPubKey(compressedPubKey: ByteArray): String {
        val hash160 = hash160(compressedPubKey)
        return base58CheckEncode(byteArrayOf(NMC_PUBKEY_VERSION) + hash160)
    }

    /**
     * Compute Hash160: RIPEMD160(SHA256(data))
     */
    fun hash160(data: ByteArray): ByteArray {
        val sha256 = sha256(data)
        return ripemd160(sha256)
    }

    // ── WIF Private Key Export ─────────────────────────────────────────

    /**
     * Encode a private key in Wallet Import Format (WIF).
     * WIF = Base58Check(version + privkey + 0x01)
     * The 0x01 suffix indicates compressed public key.
     *
     * This allows importing the key into Electrum-NMC or Namecoin Core.
     */
    fun privateKeyToWif(privKey: ByteArray): String {
        require(privKey.size == 32) { "Private key must be 32 bytes" }
        val payload = byteArrayOf(NMC_WIF_VERSION) + privKey + byteArrayOf(0x01)
        return base58CheckEncode(payload)
    }

    /**
     * Decode a WIF-encoded private key.
     *
     * @return 32-byte private key, or null if invalid
     */
    fun wifToPrivateKey(wif: String): ByteArray? {
        val decoded = base58CheckDecode(wif) ?: return null
        if (decoded.isEmpty()) return null
        if (decoded[0] != NMC_WIF_VERSION) return null
        // With compression flag: version(1) + key(32) + flag(1) = 34
        // Without: version(1) + key(32) = 33
        return when (decoded.size) {
            34 -> decoded.copyOfRange(1, 33)

            // compressed
            33 -> decoded.copyOfRange(1, 33)

            // uncompressed
            else -> null
        }
    }

    // ── Base58Check ────────────────────────────────────────────────────

    private const val BASE58_CHARS = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun base58CheckEncode(payload: ByteArray): String {
        val checksum = doubleSha256(payload).copyOfRange(0, 4)
        return base58Encode(payload + checksum)
    }

    fun base58CheckDecode(encoded: String): ByteArray? {
        val raw = base58Decode(encoded) ?: return null
        if (raw.size < 5) return null
        val payload = raw.copyOfRange(0, raw.size - 4)
        val checksum = raw.copyOfRange(raw.size - 4, raw.size)
        val expected = doubleSha256(payload).copyOfRange(0, 4)
        if (!checksum.contentEquals(expected)) return null
        return payload
    }

    /**
     * Validate a Namecoin address (P2PKH with version byte 52).
     */
    fun isValidAddress(address: String): Boolean {
        val decoded = base58CheckDecode(address) ?: return false
        if (decoded.size != 21) return false
        return decoded[0] == NMC_PUBKEY_VERSION || decoded[0] == NMC_SCRIPT_VERSION
    }

    /**
     * Extract the 20-byte hash160 from a Namecoin address.
     */
    fun addressToHash160(address: String): ByteArray? {
        val decoded = base58CheckDecode(address) ?: return null
        if (decoded.size != 21) return null
        return decoded.copyOfRange(1, 21)
    }

    // ── Hashing ────────────────────────────────────────────────────────

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun doubleSha256(data: ByteArray): ByteArray = sha256(sha256(data))

    fun ripemd160(data: ByteArray): ByteArray {
        val digest = RIPEMD160Digest()
        digest.update(data, 0, data.size)
        val result = ByteArray(20)
        digest.doFinal(result, 0)
        return result
    }

    // ── Base58 encoding/decoding ───────────────────────────────────────

    private fun base58Encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var bi = BigInteger(1, input)
        val sb = StringBuilder()
        val fifty8 = BigInteger.valueOf(58)
        while (bi > BigInteger.ZERO) {
            val (q, r) = bi.divideAndRemainder(fifty8)
            sb.append(BASE58_CHARS[r.toInt()])
            bi = q
        }
        // Leading zero bytes → leading '1's
        for (b in input) {
            if (b == 0.toByte()) sb.append('1') else break
        }
        return sb.reverse().toString()
    }

    private fun base58Decode(input: String): ByteArray? {
        if (input.isEmpty()) return ByteArray(0)
        var bi = BigInteger.ZERO
        val fifty8 = BigInteger.valueOf(58)
        for (c in input) {
            val idx = BASE58_CHARS.indexOf(c)
            if (idx < 0) return null
            bi = bi.multiply(fifty8).add(BigInteger.valueOf(idx.toLong()))
        }
        val bytes = bi.toByteArray()
        // BigInteger may prepend a zero byte for sign
        val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        val leadingZeros = input.takeWhile { it == '1' }.length
        return ByteArray(leadingZeros) + stripped
    }
}
