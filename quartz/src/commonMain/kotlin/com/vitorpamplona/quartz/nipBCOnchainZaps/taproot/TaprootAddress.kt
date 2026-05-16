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
package com.vitorpamplona.quartz.nipBCOnchainZaps.taproot

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * BIP-341 key-path-only taproot address derivation from a Nostr public key.
 *
 * NIP-BC uses the Nostr pubkey directly as the BIP-341 internal key with no
 * script tree. The output key is the tweaked internal key, encoded as a
 * bech32m P2TR address on Bitcoin mainnet.
 *
 * `Q = lift_x(P) + int(hashTapTweak(bytes(P)))·G`
 *
 * where `bytes(P)` is the 32-byte x-only Nostr pubkey and `hashTapTweak` is
 * the BIP-340 tagged hash with tag `"TapTweak"`.
 *
 * This `taproot/` package is intentionally hand-rolled rather than delegated to
 * a Bitcoin library — a recorded architecture decision, see
 * `amethyst/plans/2026-05-14-onchain-zaps.md` ("Architecture decision:
 * hand-rolled Bitcoin consensus code").
 */
object TaprootAddress {
    private const val TAP_TWEAK_TAG = "TapTweak"

    private val tapTweakTagHash: ByteArray by lazy {
        sha256(TAP_TWEAK_TAG.encodeToByteArray())
    }

    /**
     * Compute the BIP-341 tagged hash `tagged_hash("TapTweak", data)`.
     *
     * Tagged hash is defined as
     * `SHA256(SHA256(tag) || SHA256(tag) || data)`.
     */
    fun tapTweakHash(internalKey: ByteArray): ByteArray {
        require(internalKey.size == 32) {
            "internal key must be 32 bytes (got ${internalKey.size})"
        }
        val buf = ByteArray(64 + internalKey.size)
        tapTweakTagHash.copyInto(buf, 0)
        tapTweakTagHash.copyInto(buf, 32)
        internalKey.copyInto(buf, 64)
        return sha256(buf)
    }

    /**
     * Apply the BIP-341 key-path-only tweak to an internal key.
     *
     * @return the 32-byte x-only output key (Q.x).
     */
    fun tweakOutputKey(internalKey: ByteArray): ByteArray {
        require(internalKey.size == 32) {
            "internal key must be 32 bytes (got ${internalKey.size})"
        }
        val tweak = tapTweakHash(internalKey)
        // Returns compressed (33-byte) point; drop the parity byte for the x-only output.
        val tweaked = Secp256k1Instance.pubKeyTweakAdd(internalKey, tweak)
        require(tweaked.size == 33) { "expected compressed tweaked point" }
        return tweaked.copyOfRange(1, 33)
    }

    /**
     * BIP-341 `taproot_tweak_seckey` for a key-path-only spend (no script tree).
     *
     * Produces the private key that controls the P2TR output derived from
     * [internalSecKey]'s public key. The internal key is first normalized to
     * the even-y representation BIP-341 hashes over, then the TapTweak scalar
     * is added.
     *
     * @param internalSecKey 32-byte internal private key.
     * @return 32-byte tweaked private key suitable for a BIP-340 Schnorr
     *         signature over a [TaprootSigHash]-style key-path sighash.
     */
    fun tweakSecretKey(internalSecKey: ByteArray): ByteArray {
        require(internalSecKey.size == 32) {
            "internal secret key must be 32 bytes (got ${internalSecKey.size})"
        }
        // P = internalSecKey · G, as a 33-byte compressed point.
        val compressedPubKey = Secp256k1Instance.compressedPubKeyFor(internalSecKey)
        val xOnly = compressedPubKey.copyOfRange(1, 33)

        // If P has odd y, BIP-341 negates the secret key so it corresponds to
        // the even-y lift used when computing the TapTweak hash.
        val evenYParity = compressedPubKey[0].toInt() == 0x02
        val normalizedSecKey =
            if (evenYParity) internalSecKey else Secp256k1Instance.privKeyNegate(internalSecKey)

        val tweak = tapTweakHash(xOnly)
        return Secp256k1Instance.privateKeyAdd(normalizedSecKey, tweak)
    }

    /**
     * Derive the Bitcoin mainnet taproot address (`bc1p...`) for a Nostr
     * public key. The pubkey is used directly as the BIP-341 internal key.
     */
    fun fromPubKey(pubKey: HexKey): String {
        val bytes = pubKey.hexToByteArray()
        require(bytes.size == 32) { "Nostr pubkey must be 32 bytes" }
        return SegwitAddress.encodeP2TR(tweakOutputKey(bytes))
    }

    /** Derive the Bitcoin mainnet taproot address from a 32-byte x-only key. */
    fun fromPubKey(pubKey: ByteArray): String {
        require(pubKey.size == 32) { "x-only pubkey must be 32 bytes" }
        return SegwitAddress.encodeP2TR(tweakOutputKey(pubKey))
    }

    /** Produce the BIP-341 P2TR scriptPubKey for the given output key: `OP_1 <32-byte-x-only>`. */
    fun outputKeyToScriptPubKey(outputKey: ByteArray): ByteArray {
        require(outputKey.size == 32) {
            "taproot output key must be 32 bytes (got ${outputKey.size})"
        }
        val out = ByteArray(34)
        out[0] = 0x51 // OP_1
        out[1] = 0x20 // push 32 bytes
        outputKey.copyInto(out, 2)
        return out
    }

    /** Produce the BIP-341 scriptPubKey for the recipient of a Nostr pubkey. */
    fun scriptPubKeyForRecipient(pubKey: HexKey): ByteArray = outputKeyToScriptPubKey(tweakOutputKey(pubKey.hexToByteArray()))

    /** Hex of the BIP-341 scriptPubKey for the recipient — convenient for tx-output matching. */
    fun scriptPubKeyHexForRecipient(pubKey: HexKey): String = scriptPubKeyForRecipient(pubKey).toHexKey()
}
