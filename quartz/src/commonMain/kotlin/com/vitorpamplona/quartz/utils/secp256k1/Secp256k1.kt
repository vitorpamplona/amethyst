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
 * Pure Kotlin implementation of secp256k1 elliptic curve operations.
 *
 * Performance optimizations:
 * - Mutable field/point operations to minimize IntArray allocations
 * - Precomputed 4-bit window table for generator G multiplication
 * - Shamir's trick for verify: s*G + (-e)*P in a single scalar-mul pass
 * - Cached BIP-340 tagged hash prefixes
 * - Thread-local scratch buffers in field arithmetic
 */
object Secp256k1 {
    // ============ Cached tag hash prefixes for BIP-340 ============
    // SHA256(tag) || SHA256(tag) — precomputed once
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

    fun pubKeyCompress(pubkey: ByteArray): ByteArray {
        val x = IntArray(8)
        val y = IntArray(8)
        check(ECPoint.parsePublicKey(pubkey, x, y))
        return ECPoint.serializeCompressed(x, y)
    }

    fun secKeyVerify(seckey: ByteArray): Boolean {
        if (seckey.size != 32) return false
        return ScalarN.isValid(U256.fromBytes(seckey))
    }

    fun signSchnorr(
        data: ByteArray,
        seckey: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray {
        require(seckey.size == 32)
        val d0 = U256.fromBytes(seckey)
        require(ScalarN.isValid(d0))

        // Compute public key
        val pubPoint = MutablePoint()
        ECPoint.mulG(pubPoint, d0)
        val px = IntArray(8)
        val py = IntArray(8)
        check(ECPoint.toAffine(pubPoint, px, py))

        val d = if (ECPoint.hasEvenY(py)) d0 else ScalarN.neg(d0)
        val dBytes = U256.toBytes(d)
        val pBytes = U256.toBytes(px)

        // t = xor(d, tagged_hash("BIP0340/aux", auxrand))
        val t =
            if (auxrand != null) {
                require(auxrand.size == 32)
                val auxHash = sha256(AUX_PREFIX + auxrand)
                val tArr = IntArray(8)
                val auxArr = U256.fromBytes(auxHash)
                U256.xorTo(tArr, U256.fromBytes(dBytes), auxArr)
                U256.toBytes(tArr)
            } else {
                dBytes
            }

        // rand = tagged_hash("BIP0340/nonce", t || P || msg)
        val rand = sha256(NONCE_PREFIX + t + pBytes + data)
        val k0 = ScalarN.reduce(U256.fromBytes(rand))
        require(!U256.isZero(k0))

        // R = k'·G
        val rPoint = MutablePoint()
        ECPoint.mulG(rPoint, k0)
        val rx = IntArray(8)
        val ry = IntArray(8)
        check(ECPoint.toAffine(rPoint, rx, ry))

        val k = if (ECPoint.hasEvenY(ry)) k0 else ScalarN.neg(k0)

        // e = tagged_hash("BIP0340/challenge", R || P || msg) mod n
        val rBytes = U256.toBytes(rx)
        val eHash = sha256(CHALLENGE_PREFIX + rBytes + pBytes + data)
        val e = ScalarN.reduce(U256.fromBytes(eHash))

        // sig = R || (k + e*d) mod n
        val s = ScalarN.add(k, ScalarN.mul(e, d))
        val sig = rBytes + U256.toBytes(s)

        // Safety verify
        require(verifySchnorr(sig, data, pBytes))
        return sig
    }

    fun verifySchnorr(
        signature: ByteArray,
        data: ByteArray,
        pub: ByteArray,
    ): Boolean {
        if (signature.size != 64 || pub.size != 32) return false

        // P = lift_x(pub)
        val px = IntArray(8)
        val py = IntArray(8)
        if (!ECPoint.liftX(px, py, U256.fromBytes(pub))) return false

        // r, s from signature
        val r = U256.fromBytes(signature.copyOfRange(0, 32))
        if (U256.cmp(r, FieldP.P) >= 0) return false
        val s = U256.fromBytes(signature.copyOfRange(32, 64))
        if (U256.cmp(s, ScalarN.N) >= 0) return false

        // e = tagged_hash("BIP0340/challenge", sig[0:32] || pub || msg) mod n
        val eHash = sha256(CHALLENGE_PREFIX + signature.copyOfRange(0, 32) + pub + data)
        val e = ScalarN.reduce(U256.fromBytes(eHash))

        // R = s*G - e*P using Shamir's trick (combined as s*G + (-e)*P)
        val negE = ScalarN.neg(e)
        val pPoint = MutablePoint()
        pPoint.setAffine(px, py)
        val result = MutablePoint()
        ECPoint.mulDoubleG(result, s, pPoint, negE)

        // Check: R is not infinity, has even y, and x(R) == r
        if (result.isInfinity()) return false
        val rx = IntArray(8)
        val ry = IntArray(8)
        if (!ECPoint.toAffine(result, rx, ry)) return false
        if (!ECPoint.hasEvenY(ry)) return false
        return U256.cmp(rx, r) == 0
    }

    fun privKeyTweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        require(seckey.size == 32 && tweak.size == 32)
        val result = ScalarN.add(U256.fromBytes(seckey), U256.fromBytes(tweak))
        require(!U256.isZero(result) && U256.cmp(result, ScalarN.N) < 0)
        return U256.toBytes(result)
    }

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

    /** BIP-340 tagged hash (for non-cached tags) */
    internal fun taggedHash(
        tag: String,
        msg: ByteArray,
    ): ByteArray {
        val tagHash = sha256(tag.encodeToByteArray())
        return sha256(tagHash + tagHash + msg)
    }
}
