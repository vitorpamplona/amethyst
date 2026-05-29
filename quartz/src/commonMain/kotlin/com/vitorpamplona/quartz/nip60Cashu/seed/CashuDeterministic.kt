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
package com.vitorpamplona.quartz.nip60Cashu.seed

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip32SeedDerivation
import com.vitorpamplona.quartz.nip06KeyDerivation.Hardener
import com.vitorpamplona.quartz.nip06KeyDerivation.KeyPath
import com.vitorpamplona.quartz.utils.mac.MacInstance

/**
 * NUT-13 deterministic secret and blinding-factor derivation.
 *
 * Without NUT-13, every secret and blinding factor is freshly random, which
 * means losing the kind:7375 token events (rogue relay, NIP-09 from a
 * compromised key) is permanent loss of funds: the mint has the proofs but
 * we can't ask for them back because we don't know what we asked for. With
 * NUT-13, every (secret, r) pair is derived deterministically from a single
 * seed plus a per-keyset counter, so the wallet can re-derive past secrets
 * and ask the mint to return any blind signatures it still holds via
 * NUT-09 /v1/restore.
 *
 * The seed input is the standard BIP-39 root seed (64 bytes from
 * [Bip39Mnemonics.toSeed]).
 *
 * Two derivation paths, branching on keyset-id version (first byte):
 *
 *  - **v00 keysets** (8-byte id, hex starts with `00`): BIP-32 hardened
 *    derivation at
 *    `m/129372'/0'/{keyset_id_int}'/{counter}'/{leaf}`
 *    where leaf is 0 for the secret, 1 for the blinding factor.
 *    `keyset_id_int` is the keyset's hex parsed as a big-endian integer
 *    mod 2^31 - 1 (fits a 31-bit BIP-32 hardened child index).
 *
 *  - **v01 keysets** (33-byte id, hex starts with `01`): HMAC-SHA256
 *    derivation directly off the seed — BIP-32 hardened indices are 32
 *    bits, which can't faithfully encode a 33-byte id, so the protocol
 *    swaps to HMAC for v01. Spec:
 *    ```
 *    base   = "Cashu_KDF_HMAC_SHA256" || keyset_id || counter_be64
 *    secret = HMAC_SHA256(seed, base || 0x00)
 *    r      = HMAC_SHA256(seed, base || 0x01)
 *    ```
 *
 * 129372' is the UTF-8 codepoint for 🥜 ("peanuts"); coin-type 0' is fixed
 * by the spec.
 *
 * Output: the 32-byte private key at the derived path is the secret /
 * blinding factor directly. Per NUT-13 §1, the SECRET — which goes into
 * the kind:7375 token JSON — is the lowercase hex string of those 32 bytes
 * (utf-8 encoded), NOT the raw bytes. [secretBytes] returns the raw bytes;
 * [secretAsAscii] wraps the hex encoding the wallet ships on the wire.
 */
object CashuDeterministic {
    private const val CASHU_PURPOSE: Long = 129372L
    private const val CASHU_COIN_TYPE: Long = 0L
    private val V01_HMAC_PREFIX = "Cashu_KDF_HMAC_SHA256".encodeToByteArray()
    private val WALLET_SEED_KEY = "Cashu-Wallet-Seed-v1".encodeToByteArray()

    /**
     * Derive a 64-byte NUT-13 master seed from a wallet's P2PK private
     * key. Useful for wallets that don't (yet) carry a BIP-39 mnemonic
     * but do persist a long-lived private key — the kind:17375 wallet
     * event is the canonical example.
     *
     * Derivation: `HMAC-SHA512("Cashu-Wallet-Seed-v1", p2pk_priv)`.
     *  - Output shape (64 bytes) matches BIP-39's PBKDF2-HMAC-SHA512
     *    output so existing NUT-13 derivation paths accept it unchanged.
     *  - Using HMAC ensures any leakage of secrets derived from the seed
     *    doesn't reveal the P2PK key itself — distinct key-derivation
     *    domains.
     *  - The "v1" tag isolates this derivation from any future scheme
     *    we want to roll out without breaking existing wallets.
     *
     * Idempotent and pure; safe to recompute on every wallet load.
     */
    fun deriveWalletSeed(p2pkPrivkey: ByteArray): ByteArray {
        require(p2pkPrivkey.size == 32) { "P2PK private key must be 32 bytes" }
        val mac = MacInstance("HmacSHA512", WALLET_SEED_KEY)
        mac.update(p2pkPrivkey)
        return mac.doFinal()
    }

    /**
     * 32 raw bytes of the derived secret. The Cashu protocol uses these
     * bytes as the ASCII-hex string they encode to — see [secretAsAscii].
     */
    fun secretBytes(
        seed: ByteArray,
        keysetId: String,
        counter: Long,
    ): ByteArray = derive(seed, keysetId, counter, leaf = 0L)

    /**
     * NUT-13 §1: "The secret is the UTF-8 encoded hex-string". This is
     * what goes into the kind:7375 proof `secret` field and what the
     * mint hashes in hash-to-curve.
     */
    fun secretAsAscii(
        seed: ByteArray,
        keysetId: String,
        counter: Long,
    ): ByteArray = bytesToLowercaseHex(secretBytes(seed, keysetId, counter)).encodeToByteArray()

    /**
     * 32 raw bytes of the BDHKE blinding factor `r`. Used as the second
     * argument to [com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke.blind].
     */
    fun blindingFactor(
        seed: ByteArray,
        keysetId: String,
        counter: Long,
    ): ByteArray = derive(seed, keysetId, counter, leaf = 1L)

    /**
     * Spec: `int.from_bytes(bytes.fromhex(keyset_id_hex), "big") % (2^31 - 1)`.
     * Modulo by Mersenne-31 keeps the result inside a 32-bit BIP-32 hardened
     * index (top bit is the hardened flag, so only 31 bits are usable).
     */
    fun keysetIdToInt(keysetId: String): Long {
        // BigInteger isn't available in commonMain; the keyset id is at most
        // 33 bytes today (1 version byte + 32 hex chars for v01). Compute the
        // mod-(2^31-1) incrementally — `acc = (acc * 256 + byte) % M` keeps
        // every intermediate in Long range.
        val bytes = keysetId.hexToByteArray()
        val mersenne31 = 0x7FFFFFFFL
        var acc = 0L
        for (b in bytes) {
            acc = (acc * 256L + (b.toLong() and 0xFFL)) % mersenne31
        }
        return acc
    }

    private fun derive(
        seed: ByteArray,
        keysetId: String,
        counter: Long,
        leaf: Long,
    ): ByteArray {
        require(seed.isNotEmpty()) { "Seed cannot be empty" }
        require(counter >= 0L) { "Counter must be non-negative" }
        require(leaf == 0L || leaf == 1L) { "Leaf must be 0 (secret) or 1 (blinding factor)" }

        val keysetIdBytes = keysetId.hexToByteArray()
        // First byte is the version flag — v00 keysets use BIP-32 hardened
        // derivation; v01 uses HMAC-SHA256 because a 33-byte keyset id
        // doesn't fit a 31-bit BIP-32 child index without lossy modular
        // collapse. Treat unknown / missing version as v00 for forwards-
        // compat with any short legacy ids.
        val isV01 = keysetIdBytes.isNotEmpty() && keysetIdBytes[0].toInt() == 0x01
        return if (isV01) {
            deriveV01(seed, keysetIdBytes, counter, leaf)
        } else {
            deriveV00(seed, keysetId, counter, leaf)
        }
    }

    private fun deriveV00(
        seed: ByteArray,
        keysetId: String,
        counter: Long,
        leaf: Long,
    ): ByteArray {
        val keysetInt = keysetIdToInt(keysetId)
        val path =
            KeyPath(
                listOf(
                    Hardener.hardened(CASHU_PURPOSE),
                    Hardener.hardened(CASHU_COIN_TYPE),
                    Hardener.hardened(keysetInt),
                    Hardener.hardened(counter),
                    leaf,
                ),
            )
        val derivation = Bip32SeedDerivation()
        val master = derivation.generate(seed)
        return derivation.derivePrivateKey(master, path)
    }

    private fun deriveV01(
        seed: ByteArray,
        keysetIdBytes: ByteArray,
        counter: Long,
        leaf: Long,
    ): ByteArray {
        // base = "Cashu_KDF_HMAC_SHA256" || keyset_id || counter_be64
        val counterBytes = ByteArray(8)
        for (i in 0 until 8) {
            counterBytes[7 - i] = ((counter ushr (i * 8)) and 0xFFL).toByte()
        }
        val baseLen = V01_HMAC_PREFIX.size + keysetIdBytes.size + counterBytes.size
        val message = ByteArray(baseLen + 1)
        V01_HMAC_PREFIX.copyInto(message, 0)
        keysetIdBytes.copyInto(message, V01_HMAC_PREFIX.size)
        counterBytes.copyInto(message, V01_HMAC_PREFIX.size + keysetIdBytes.size)
        message[baseLen] = leaf.toByte() // 0x00 for secret, 0x01 for blinding factor

        val mac = MacInstance("HmacSHA256", seed)
        mac.update(message)
        return mac.doFinal()
    }

    /**
     * Lowercase 0-9a-f without StringBuilder allocs in inner loops —
     * matches what the spec demands (`bytes.hex()` in the Python ref impl).
     */
    private fun bytesToLowercaseHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0xF]
        }
        return chars.concatToString()
    }

    private val HEX_CHARS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
}
