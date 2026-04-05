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
package com.vitorpamplona.quartz.utils.secp256k1

import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Pure Kotlin implementation of secp256k1 elliptic curve operations for Nostr.
 *
 * This replaces the native fr.acinq.secp256k1 JNI bindings with a portable KMP
 * implementation that runs on all Kotlin targets (JVM, Android, iOS, Linux) without
 * requiring platform-specific native libraries.
 *
 * Provides only the operations used by Nostr:
 * - [pubkeyCreate] / [pubKeyCompress]: Key generation
 * - [secKeyVerify]: Key validation
 * - [signSchnorr] / [verifySchnorr]: BIP-340 Schnorr signatures (NIP-01)
 * - [privKeyTweakAdd]: BIP-32 key derivation (NIP-06)
 * - [pubKeyTweakMul]: ECDH shared secrets (NIP-04, NIP-44)
 *
 * Performance: ~2,100 verify/s on JVM (~13× slower than the native C library).
 * The gap is primarily due to JVM's lack of 128-bit integer types (forcing 8×32-bit
 * limbs instead of C's 5×52-bit) and the absence of the GLV endomorphism optimization
 * that halves the number of EC point doublings. See Point.kt for optimization notes.
 */
object Secp256k1 {
    // ==================== Cached BIP-340 tag hash prefixes ====================
    //
    // BIP-340 uses "tagged hashes": SHA256(SHA256(tag) || SHA256(tag) || message).
    // The tag prefixes SHA256(tag) || SHA256(tag) are constant per tag string.
    // We precompute them once to save 2 SHA256 calls per sign/verify operation.

    private val CHALLENGE_PREFIX: ByteArray by lazy {
        val h = sha256("BIP0340/challenge".encodeToByteArray())
        h + h
    }
    private val AUX_PREFIX: ByteArray by lazy {
        val h = sha256("BIP0340/aux".encodeToByteArray())
        h + h
    }
    private val NONCE_PREFIX: ByteArray by lazy {
        val h = sha256("BIP0340/nonce".encodeToByteArray())
        h + h
    }

    // ==================== Key operations ====================

    /** Create a 65-byte uncompressed public key (04 || x || y) from a 32-byte secret key. */
    fun pubkeyCreate(seckey: ByteArray): ByteArray {
        require(seckey.size == 32)
        val scalar = U256.fromBytes(seckey)
        require(ScalarN.isValid(scalar))
        val p = MutablePoint()
        ECPoint.mulG(p, scalar)
        val x = IntArray(8)
        val y = IntArray(8)
        check(ECPoint.toAffine(p, x, y))
        return ECPoint.serializeUncompressed(x, y)
    }

    /**
     * Compress a public key to 33 bytes (02/03 || x). Accepts 33 or 65 byte input.
     *
     * For uncompressed keys (04 prefix): reads the y-coordinate's parity from the last
     * byte and copies the x-coordinate directly — no field arithmetic needed.
     * For already-compressed keys: returns the input unchanged.
     */
    fun pubKeyCompress(pubkey: ByteArray): ByteArray =
        when {
            pubkey.size == 65 && pubkey[0] == 0x04.toByte() -> {
                val result = ByteArray(33)
                result[0] = if (pubkey[64].toInt() and 1 == 0) 0x02 else 0x03
                pubkey.copyInto(result, 1, 1, 33)
                result
            }

            pubkey.size == 33 && (pubkey[0] == 0x02.toByte() || pubkey[0] == 0x03.toByte()) -> {
                pubkey
            }

            else -> {
                error("Invalid public key: size=${pubkey.size}")
            }
        }

    /** Verify that a byte array is a valid secret key (32 bytes, 0 < value < n). */
    fun secKeyVerify(seckey: ByteArray): Boolean {
        if (seckey.size != 32) return false
        return ScalarN.isValid(U256.fromBytes(seckey))
    }

    // ==================== BIP-340 Schnorr Signatures ====================

    /**
     * Create a BIP-340 Schnorr signature.
     *
     * Implements the full BIP-340 signing algorithm:
     * 1. Derive keypair, negate secret key if public key has odd y
     * 2. Compute deterministic nonce via tagged hashes (with optional aux randomness)
     * 3. Compute R = k·G, ensure even y
     * 4. Compute challenge e = H(R || P || msg)
     * 5. Compute s = k + e·d (mod n)
     * 6. Verify the signature as a safety check
     *
     * @param data Message bytes (any length — hashed internally via tagged hash)
     * @param seckey 32-byte secret key
     * @param auxrand Optional 32-byte auxiliary randomness (null for deterministic)
     * @return 64-byte signature (R.x || s)
     */
    fun signSchnorr(
        data: ByteArray,
        seckey: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray {
        require(seckey.size == 32)
        val d0 = U256.fromBytes(seckey)
        require(ScalarN.isValid(d0))

        val pubPoint = MutablePoint()
        ECPoint.mulG(pubPoint, d0)
        val px = IntArray(8)
        val py = IntArray(8)
        check(ECPoint.toAffine(pubPoint, px, py))

        val d = if (ECPoint.hasEvenY(py)) d0 else ScalarN.neg(d0)
        val dBytes = U256.toBytes(d)
        val pBytes = U256.toBytes(px)

        val t =
            if (auxrand != null) {
                require(auxrand.size == 32)
                val auxHash = sha256(AUX_PREFIX + auxrand)
                val tArr = IntArray(8)
                U256.xorTo(tArr, U256.fromBytes(dBytes), U256.fromBytes(auxHash))
                U256.toBytes(tArr)
            } else {
                dBytes
            }

        val nonceInput = ByteArray(64 + 32 + 32 + data.size)
        NONCE_PREFIX.copyInto(nonceInput, 0)
        t.copyInto(nonceInput, 64)
        pBytes.copyInto(nonceInput, 96)
        data.copyInto(nonceInput, 128)
        val rand = sha256(nonceInput)
        val k0 = ScalarN.reduce(U256.fromBytes(rand))
        require(!U256.isZero(k0))

        val rPoint = MutablePoint()
        ECPoint.mulG(rPoint, k0)
        val rx = IntArray(8)
        val ry = IntArray(8)
        check(ECPoint.toAffine(rPoint, rx, ry))

        val k = if (ECPoint.hasEvenY(ry)) k0 else ScalarN.neg(k0)
        val chalInput = ByteArray(64 + 32 + 32 + data.size)
        CHALLENGE_PREFIX.copyInto(chalInput, 0)
        U256.toBytesInto(rx, chalInput, 64)
        pBytes.copyInto(chalInput, 96)
        data.copyInto(chalInput, 128)
        val eHash = sha256(chalInput)
        val e = ScalarN.reduce(U256.fromBytes(eHash))

        val sScalar = ScalarN.add(k, ScalarN.mul(e, d))
        val sig = ByteArray(64)
        U256.toBytesInto(rx, sig, 0)
        U256.toBytesInto(sScalar, sig, 32)

        require(verifySchnorr(sig, data, pBytes)) { "Signature self-verification failed" }
        return sig
    }

    /**
     * Verify a BIP-340 Schnorr signature.
     *
     * This is the performance-critical operation for a Nostr client — every received
     * event must be verified. The algorithm:
     * 1. Decompress public key P from x-only representation
     * 2. Parse r (x-coordinate of R) and s from signature
     * 3. Compute challenge e = H(r || P || msg)
     * 4. Compute R' = s·G - e·P using Shamir's trick (single combined scalar mul)
     * 5. Verify R' is not infinity, has even y, and x(R') = r
     *
     * @param signature 64-byte signature (R.x || s)
     * @param data Message bytes (any length)
     * @param pub 32-byte x-only public key
     */
    fun verifySchnorr(
        signature: ByteArray,
        data: ByteArray,
        pub: ByteArray,
    ): Boolean {
        if (signature.size != 64 || pub.size != 32) return false

        val px = IntArray(8)
        val py = IntArray(8)
        if (!ECPoint.liftX(px, py, U256.fromBytes(pub))) return false

        val r = U256.fromBytes(signature, 0)
        if (U256.cmp(r, FieldP.P) >= 0) return false
        val s = U256.fromBytes(signature, 32)
        if (U256.cmp(s, ScalarN.N) >= 0) return false

        // Build challenge hash input in a single array: prefix(64) + r(32) + pub(32) + data(N)
        val hashInput = ByteArray(64 + 32 + 32 + data.size)
        CHALLENGE_PREFIX.copyInto(hashInput, 0)
        signature.copyInto(hashInput, 64, 0, 32) // r bytes from signature
        pub.copyInto(hashInput, 96)
        data.copyInto(hashInput, 128)
        val eHash = sha256(hashInput)
        val e = ScalarN.reduce(U256.fromBytes(eHash))

        // R = s·G + (-e)·P via Shamir's trick
        val negE = ScalarN.neg(e)
        val pPoint = MutablePoint()
        pPoint.setAffine(px, py)
        val result = MutablePoint()
        ECPoint.mulDoubleG(result, s, pPoint, negE)

        if (result.isInfinity()) return false
        val rx = IntArray(8)
        val ry = IntArray(8)
        if (!ECPoint.toAffine(result, rx, ry)) return false
        if (!ECPoint.hasEvenY(ry)) return false
        return U256.cmp(rx, r) == 0
    }

    // ==================== Tweak operations ====================

    /** Add a tweak to a private key: result = (seckey + tweak) mod n. Used by BIP-32. */
    fun privKeyTweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        require(seckey.size == 32 && tweak.size == 32)
        val result = ScalarN.add(U256.fromBytes(seckey), U256.fromBytes(tweak))
        require(!U256.isZero(result) && U256.cmp(result, ScalarN.N) < 0)
        return U256.toBytes(result)
    }

    /** Multiply a public key by a scalar. Used for ECDH shared secret derivation. */
    fun pubKeyTweakMul(
        pubkey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        require(tweak.size == 32)
        val x = IntArray(8)
        val y = IntArray(8)
        check(ECPoint.parsePublicKey(pubkey, x, y))
        val scalar = U256.fromBytes(tweak)
        require(ScalarN.isValid(scalar))

        val p = MutablePoint()
        p.setAffine(x, y)
        val result = MutablePoint()
        ECPoint.mul(result, p, scalar)
        val rx = IntArray(8)
        val ry = IntArray(8)
        check(ECPoint.toAffine(result, rx, ry))

        return if (pubkey.size == 33) {
            ECPoint.serializeCompressed(rx, ry)
        } else {
            ECPoint.serializeUncompressed(rx, ry)
        }
    }

    /** BIP-340 tagged hash (for tags not cached above). */
    internal fun taggedHash(
        tag: String,
        msg: ByteArray,
    ): ByteArray {
        val tagHash = sha256(tag.encodeToByteArray())
        return sha256(tagHash + tagHash + msg)
    }
}
