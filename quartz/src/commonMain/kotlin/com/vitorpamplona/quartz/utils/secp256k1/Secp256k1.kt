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
 * Provides the same functionality as fr.acinq.secp256k1.Secp256k1 but
 * without requiring native bindings.
 *
 * Implements only the operations used by Secp256k1Instance:
 * - pubkeyCreate / pubKeyCompress
 * - secKeyVerify
 * - signSchnorr / verifySchnorr (BIP-340)
 * - privKeyTweakAdd
 * - pubKeyTweakMul
 */
object Secp256k1 {
    /**
     * Create a public key from a secret key.
     * @param seckey 32-byte secret key
     * @return 65-byte uncompressed public key (04 || x || y)
     */
    fun pubkeyCreate(seckey: ByteArray): ByteArray {
        require(seckey.size == 32) { "Secret key must be 32 bytes" }
        val scalar = U256.fromBytes(seckey)
        require(ScalarN.isValid(scalar)) { "Invalid secret key" }

        val point = ECPoint.mul(ECPoint.G, scalar)
        val (x, y) = ECPoint.toAffine(point) ?: error("Unexpected infinity")
        return ECPoint.serializeUncompressed(x, y)
    }

    /**
     * Compress a public key.
     * @param pubkey 65-byte uncompressed public key or 33-byte compressed
     * @return 33-byte compressed public key (02/03 || x)
     */
    fun pubKeyCompress(pubkey: ByteArray): ByteArray {
        val (x, y) = ECPoint.parsePublicKey(pubkey) ?: error("Invalid public key")
        return ECPoint.serializeCompressed(x, y)
    }

    /**
     * Verify that a secret key is valid (0 < key < n).
     * @param seckey secret key bytes
     * @return true if valid
     */
    fun secKeyVerify(seckey: ByteArray): Boolean {
        if (seckey.size != 32) return false
        val scalar = U256.fromBytes(seckey)
        return ScalarN.isValid(scalar)
    }

    /**
     * Create a Schnorr signature per BIP-340.
     * @param data 32-byte message hash
     * @param seckey 32-byte secret key
     * @param auxrand optional 32-byte auxiliary randomness (null for deterministic)
     * @return 64-byte signature
     */
    fun signSchnorr(
        data: ByteArray,
        seckey: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray {
        require(seckey.size == 32) { "Secret key must be 32 bytes" }

        // Step 1-3: Compute keypair, negate secret key if needed
        val d0 = U256.fromBytes(seckey)
        require(ScalarN.isValid(d0)) { "Invalid secret key" }

        val pubPoint = ECPoint.mul(ECPoint.G, d0)
        val (px, py) = ECPoint.toAffine(pubPoint) ?: error("Unexpected infinity")

        val d = if (ECPoint.hasEvenY(py)) d0 else ScalarN.neg(d0)
        val dBytes = U256.toBytes(d)
        val pBytes = U256.toBytes(px) // x-only public key

        // Step 4: Compute t = xor(d, tagged_hash("BIP0340/aux", auxrand))
        val t =
            if (auxrand != null) {
                require(auxrand.size == 32) { "Aux randomness must be 32 bytes" }
                val auxHash = taggedHash("BIP0340/aux", auxrand)
                val tArray = U256.fromBytes(dBytes)
                val auxArray = U256.fromBytes(auxHash)
                U256.toBytes(U256.xor(tArray, auxArray))
            } else {
                dBytes
            }

        // Step 5: rand = tagged_hash("BIP0340/nonce", t || pBytes || data)
        val nonceInput = t + pBytes + data
        val rand = taggedHash("BIP0340/nonce", nonceInput)

        // Step 6: k' = int(rand) mod n
        val k0 = ScalarN.reduce(U256.fromBytes(rand))
        require(!U256.isZero(k0)) { "Nonce is zero" }

        // Step 7: R = k'·G
        val rPoint = ECPoint.mul(ECPoint.G, k0)
        val (rx, ry) = ECPoint.toAffine(rPoint) ?: error("Unexpected infinity")

        // Step 8: k = k' if has_even_y(R), else n - k'
        val k = if (ECPoint.hasEvenY(ry)) k0 else ScalarN.neg(k0)

        // Step 9: e = int(tagged_hash("BIP0340/challenge", bytes(R) || bytes(P) || msg)) mod n
        val rBytes = U256.toBytes(rx)
        val challengeInput = rBytes + pBytes + data
        val eHash = taggedHash("BIP0340/challenge", challengeInput)
        val e = ScalarN.reduce(U256.fromBytes(eHash))

        // Step 10: sig = bytes(R) || bytes((k + e*d) mod n)
        val s = ScalarN.add(k, ScalarN.mul(e, d))
        val sig = rBytes + U256.toBytes(s)

        // Step 11: Verify (optional safety check - can be removed for performance)
        require(verifySchnorr(sig, data, pBytes)) { "Signature verification failed" }

        return sig
    }

    /**
     * Verify a Schnorr signature per BIP-340.
     * @param signature 64-byte signature
     * @param data 32-byte message hash
     * @param pub 32-byte x-only public key
     * @return true if the signature is valid
     */
    fun verifySchnorr(
        signature: ByteArray,
        data: ByteArray,
        pub: ByteArray,
    ): Boolean {
        if (signature.size != 64) return false
        if (pub.size != 32) return false

        // Step 1: P = lift_x(int(pk))
        val px = U256.fromBytes(pub)
        val (pxCoord, pyCoord) = ECPoint.liftX(px) ?: return false

        // Step 2: r = int(sig[0:32])
        val rBytes = signature.copyOfRange(0, 32)
        val r = U256.fromBytes(rBytes)
        if (U256.cmp(r, FieldP.P) >= 0) return false

        // Step 3: s = int(sig[32:64])
        val s = U256.fromBytes(signature.copyOfRange(32, 64))
        if (U256.cmp(s, ScalarN.N) >= 0) return false

        // Step 4: e = int(tagged_hash("BIP0340/challenge", bytes(r) || bytes(P) || msg)) mod n
        val challengeInput = rBytes + pub + data
        val eHash = taggedHash("BIP0340/challenge", challengeInput)
        val e = ScalarN.reduce(U256.fromBytes(eHash))

        // Step 5: R = s·G - e·P
        val sG = ECPoint.mul(ECPoint.G, s)
        val negE = ScalarN.neg(e)
        val pJac = JPoint(pxCoord, pyCoord, intArrayOf(1, 0, 0, 0, 0, 0, 0, 0))
        val ePneg = ECPoint.mul(pJac, negE)
        val rPoint = ECPoint.add(sG, ePneg)

        // Step 6: Fail if R is infinity, or if R has odd y, or if x(R) != r
        if (rPoint.isInfinity()) return false
        val (rx, ry) = ECPoint.toAffine(rPoint) ?: return false
        if (!ECPoint.hasEvenY(ry)) return false
        if (U256.cmp(rx, r) != 0) return false

        return true
    }

    /**
     * Add a tweak to a private key: (seckey + tweak) mod n.
     * @param seckey 32-byte secret key
     * @param tweak 32-byte tweak
     * @return 32-byte tweaked secret key
     */
    fun privKeyTweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        require(seckey.size == 32) { "Secret key must be 32 bytes" }
        require(tweak.size == 32) { "Tweak must be 32 bytes" }
        val a = U256.fromBytes(seckey)
        val b = U256.fromBytes(tweak)
        val result = ScalarN.add(a, b)
        require(!U256.isZero(result)) { "Result is zero" }
        require(U256.cmp(result, ScalarN.N) < 0) { "Result >= n" }
        return U256.toBytes(result)
    }

    /**
     * Multiply a public key by a tweak (scalar multiplication).
     * @param pubkey 33-byte compressed or 65-byte uncompressed public key
     * @param tweak 32-byte tweak/scalar
     * @return public key in the same format as input
     */
    fun pubKeyTweakMul(
        pubkey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        require(tweak.size == 32) { "Tweak must be 32 bytes" }
        val (x, y) = ECPoint.parsePublicKey(pubkey) ?: error("Invalid public key")
        val scalar = U256.fromBytes(tweak)
        require(ScalarN.isValid(scalar)) { "Invalid tweak" }

        val point = JPoint(x, y, intArrayOf(1, 0, 0, 0, 0, 0, 0, 0))
        val result = ECPoint.mul(point, scalar)
        val (rx, ry) = ECPoint.toAffine(result) ?: error("Result is infinity")

        return if (pubkey.size == 33) {
            ECPoint.serializeCompressed(rx, ry)
        } else {
            ECPoint.serializeUncompressed(rx, ry)
        }
    }

    // ============ Tagged Hash (BIP-340) ============

    /** BIP-340 tagged hash: SHA256(SHA256(tag) || SHA256(tag) || msg) */
    internal fun taggedHash(
        tag: String,
        msg: ByteArray,
    ): ByteArray {
        val tagHash = sha256(tag.encodeToByteArray())
        return sha256(tagHash + tagHash + msg)
    }
}
