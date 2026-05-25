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
package com.vitorpamplona.quartz.nip60Cashu.bdhke

import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.secp256k1.ECPoint
import com.vitorpamplona.quartz.utils.secp256k1.Fe4
import com.vitorpamplona.quartz.utils.secp256k1.FieldP
import com.vitorpamplona.quartz.utils.secp256k1.KeyCodec
import com.vitorpamplona.quartz.utils.secp256k1.MutablePoint
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import com.vitorpamplona.quartz.utils.secp256k1.U256
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Blind Diffie-Hellman Key Exchange (BDHKE) — the cryptographic primitive
 * that powers Cashu ecash. See https://github.com/cashubtc/nuts/blob/main/00.md.
 *
 * The protocol — three participants implied (Alice = user, Bob = mint):
 *   1. Alice picks a random `secret` and blinding factor `r`. Computes
 *      `Y = hash_to_curve(secret)` and the blinded message `B_ = Y + r·G`.
 *   2. Bob signs the blinded point with private key `a`: `C_ = a·B_`.
 *      Bob's public key for this amount is `K = a·G`.
 *   3. Alice unblinds: `C = C_ - r·K`. The triple `(secret, C, id)` is the
 *      proof — Bob can verify it by checking `C == a·Y`.
 *
 * Bob cannot link `B_` to `(secret, C)` because the blinding factor `r` is
 * private to Alice — hence "blind" signatures and the privacy property of
 * ecash. See NUT-00 for the full security argument.
 */
object Bdhke {
    /** Cashu hash-to-curve domain separator (NUT-00). */
    private val DOMAIN_SEPARATOR = "Secp256k1_HashToCurve_Cashu_".encodeToByteArray()

    /**
     * Map an arbitrary byte string to a secp256k1 point using the NUT-00
     * try-and-increment algorithm.
     *
     *   msg_to_hash = SHA256(domain_separator || x)
     *   for counter in 0..2^32:
     *     y_candidate = SHA256(msg_to_hash || counter as little-endian uint32)
     *     try lift_x(y_candidate); if it's on the curve, return that point.
     *
     * Always returns the point with even Y (lift_x convention).
     *
     * Internal because [MutablePoint] is internal to the Quartz crypto package.
     * External callers should use [hashToCurveCompressed] to get a 33-byte point.
     */
    internal fun hashToCurve(x: ByteArray): MutablePoint {
        val msgToHash = sha256(DOMAIN_SEPARATOR + x)
        val buf = ByteArray(36)
        msgToHash.copyInto(buf, 0)

        var counter = 0
        while (counter < MAX_HASH_TO_CURVE_ITERATIONS) {
            buf[32] = (counter and 0xFF).toByte()
            buf[33] = ((counter ushr 8) and 0xFF).toByte()
            buf[34] = ((counter ushr 16) and 0xFF).toByte()
            buf[35] = ((counter ushr 24) and 0xFF).toByte()

            val candidate = sha256(buf)
            val x4 = U256.fromBytes(candidate)
            val outX = Fe4()
            val outY = Fe4()
            if (KeyCodec.liftX(outX, outY, x4)) {
                val point = MutablePoint()
                point.setAffine(outX, outY)
                return point
            }
            counter++
        }
        error("hash_to_curve failed to find a valid point in $MAX_HASH_TO_CURVE_ITERATIONS iterations")
    }

    /**
     * Public form of [hashToCurve] that returns a 33-byte compressed point.
     */
    fun hashToCurveCompressed(x: ByteArray): ByteArray = toCompressed(hashToCurve(x))

    /**
     * Step 1 of BDHKE — Alice creates a blinded message.
     *
     * @param secret 32-byte (or arbitrary length) message to be later unblinded.
     * @param r 32-byte blinding factor (must be a valid scalar < n).
     * @return 33-byte compressed point `B_ = hash_to_curve(secret) + r·G`.
     */
    fun blind(
        secret: ByteArray,
        r: ByteArray,
    ): ByteArray {
        require(r.size == 32) { "Blinding factor must be 32 bytes" }
        require(Secp256k1.secKeyVerify(r)) { "Invalid blinding factor" }

        val y = hashToCurve(secret)
        val rg = MutablePoint()
        val rScalar = Fe4()
        U256.fromBytesInto(rScalar, r, 0)
        ECPoint.mulG(rg, rScalar)

        val out = MutablePoint()
        ECPoint.addPoints(out, y, rg)

        return toCompressed(out)
    }

    /**
     * Step 3 of BDHKE — Alice unblinds the mint's signature.
     *
     *   C = C_ - r·K
     *
     * Implemented as `C_ + (-r·K)` since we don't expose point subtraction
     * directly. `-r·K` is computed by negating the scalar: `(n - r)·K`.
     *
     * @param blindSignature 33-byte compressed `C_` returned by the mint.
     * @param r 32-byte blinding factor used in [blind].
     * @param mintPubKey 33-byte compressed `K = a·G` (the mint's public key
     *                   for this amount and keyset).
     * @return 33-byte compressed `C`, the unblinded signature on `secret`.
     */
    fun unblind(
        blindSignature: ByteArray,
        r: ByteArray,
        mintPubKey: ByteArray,
    ): ByteArray {
        require(r.size == 32) { "Blinding factor must be 32 bytes" }

        val cTickX = Fe4()
        val cTickY = Fe4()
        require(KeyCodec.parsePublicKey(blindSignature, cTickX, cTickY)) { "Invalid blind signature" }
        val cTick = MutablePoint().also { it.setAffine(cTickX, cTickY) }

        val kx = Fe4()
        val ky = Fe4()
        require(KeyCodec.parsePublicKey(mintPubKey, kx, ky)) { "Invalid mint public key" }
        val k = MutablePoint().also { it.setAffine(kx, ky) }

        // r·K, then negate Y to get -r·K
        val rScalar = Fe4()
        U256.fromBytesInto(rScalar, r, 0)
        val rk = MutablePoint()
        ECPoint.mul(rk, k, rScalar)

        // Convert to affine first, then negate Y.
        val rkX = Fe4()
        val rkY = Fe4()
        require(ECPoint.toAffine(rk, rkX, rkY)) { "rK is point at infinity" }
        FieldP.neg(rkY, rkY)
        val negRk = MutablePoint().also { it.setAffine(rkX, rkY) }

        val out = MutablePoint()
        ECPoint.addPoints(out, cTick, negRk)
        return toCompressed(out)
    }

    /**
     * Mint-side BDHKE — Bob signs the blinded message with his private key.
     * Used only by tests and the optional mint-emulation path.
     *
     *   C_ = a·B_
     *
     * @param blindedMessage 33-byte compressed `B_`.
     * @param mintPrivKey 32-byte mint private key `a` for this amount.
     * @return 33-byte compressed `C_`.
     */
    fun sign(
        blindedMessage: ByteArray,
        mintPrivKey: ByteArray,
    ): ByteArray = Secp256k1.pubKeyTweakMul(blindedMessage, mintPrivKey)

    /**
     * Mint-side proof verification — Bob checks `C == a·Y`.
     * Used by tests and (optionally) by clients that want to verify the mint's
     * signature locally before publishing a kind:7375 token event.
     *
     * @param secret The proof's secret.
     * @param unblindedSignature 33-byte compressed `C`.
     * @param mintPrivKey 32-byte mint private key `a`.
     */
    fun verify(
        secret: ByteArray,
        unblindedSignature: ByteArray,
        mintPrivKey: ByteArray,
    ): Boolean {
        val y = hashToCurve(secret)
        val yCompressed = toCompressed(y)
        val expected = Secp256k1.pubKeyTweakMul(yCompressed, mintPrivKey)
        return expected.contentEquals(unblindedSignature)
    }

    /**
     * Generate a uniformly random 32-byte scalar suitable as a BDHKE blinding
     * factor or wallet P2PK private key. Rejects values >= n; in practice this
     * succeeds on the first sample with overwhelming probability.
     */
    fun randomScalar(): ByteArray {
        while (true) {
            val candidate = RandomInstance.bytes(32)
            if (Secp256k1.secKeyVerify(candidate)) return candidate
        }
    }

    /**
     * Generate a uniformly random 32-byte "secret" for a Cashu proof.
     * Currently the same as [randomScalar] but kept as a separate symbol so
     * the caller's intent is explicit: this is not a private key.
     */
    fun randomSecret(): ByteArray = randomScalar()

    private fun toCompressed(p: MutablePoint): ByteArray {
        val x = Fe4()
        val y = Fe4()
        require(ECPoint.toAffine(p, x, y)) { "Point is at infinity" }
        return KeyCodec.serializeCompressed(x, y)
    }

    // Spec doesn't bound this; in practice the first iteration succeeds with
    // probability ~1/2. We cap at 2^16 — astronomically unlikely to hit.
    private const val MAX_HASH_TO_CURVE_ITERATIONS = 65536
}
