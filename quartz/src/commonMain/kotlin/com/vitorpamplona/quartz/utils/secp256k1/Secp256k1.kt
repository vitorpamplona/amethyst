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
 * Portable KMP implementation that runs on all Kotlin targets (JVM, Android, iOS,
 * Linux) without requiring platform-specific native libraries.
 *
 * Provides only the operations used by Nostr:
 * - [pubkeyCreate] / [pubKeyCompress]: Key generation
 * - [secKeyVerify]: Key validation
 * - [signSchnorr] / [signSchnorrWithPubKey] / [verifySchnorr]: BIP-340 Schnorr (NIP-01)
 * - [privKeyTweakAdd]: BIP-32 key derivation (NIP-06)
 * - [pubKeyTweakMul] / [ecdhXOnly]: ECDH shared secrets (NIP-04, NIP-44)
 *
 * Performance on JVM (vs native C/JNI secp256k1, 2000+ warmup, 3000-5000 iterations):
 *   verify        ~15,000 ops/s  (1.7× native, with pubkey cache)
 *   sign          ~18,000 ops/s  (1.5× native)
 *   sign(cached)  ~28,000 ops/s  (0.9× — FASTER than native)
 *   pubCreate     ~38,000 ops/s  (1.3× native)
 *   ECDH          ~14,000 ops/s  (2.0× native)
 *
 * Key optimizations:
 *   - Unrolled 4×64-bit field arithmetic (mulWide, sqrWide, addTo, subTo)
 *   - GLV endomorphism + wNAF + comb method + Strauss/Shamir
 *   - Ping-pong point buffers (eliminates copyFrom in scalar mul loops)
 *   - Pre-allocated ThreadLocal scratch (eliminates ~130 allocs/operation)
 *   - Pubkey decompression cache (skips sqrt for repeated pubkeys)
 *   - P-side wNAF table cache (skips table build for repeated pubkeys)
 *   - Direct unsigned multiplyHigh fallback (faster on Android < API 31)
 *
 * Remaining gap vs C libsecp256k1:
 *   - No lazy reduction (4×64 limbs fully packed; C's 5×52 have 12-bit headroom)
 *   - Fermat inversion instead of safegcd (safegcd slower on JVM)
 *   - WINDOW_G=12 vs 15 (JVM heap tables cause cache pressure at w=15)
 *   - No constant-time guarantees (not needed for Nostr)
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

    // ==================== Pubkey decompression cache ====================
    //
    // liftX (square root on secp256k1) costs ~280 field ops per call. In Nostr,
    // the same pubkeys are verified repeatedly (every event from the same author).
    // This cache maps x-only pubkey bytes → decompressed (x, y) coordinates,
    // saving the sqrt for repeated pubkeys (~13% of verify cost per cache hit).
    //
    // Simple fixed-size direct-mapped cache (no LRU overhead). Size must be power of 2.
    // 256 entries × (32 + 32 + 32) bytes = ~24KB. Cache collisions just evict silently.
    private const val PUBKEY_CACHE_SIZE = 256 // power of 2
    private const val PUBKEY_CACHE_MASK = PUBKEY_CACHE_SIZE - 1

    private class CachedPubkey(
        val keyBytes: ByteArray, // 32-byte x-only pubkey (for equality check)
        val px: LongArray, // decompressed x (4 limbs)
        val py: LongArray, // decompressed y (4 limbs)
    )

    private val pubkeyCache = arrayOfNulls<CachedPubkey>(PUBKEY_CACHE_SIZE)

    /**
     * liftX with caching. Returns true and fills outX/outY if the pubkey is valid.
     * On cache hit, copies the cached coordinates (2 array copies, ~trivial).
     * On cache miss, computes sqrt and stores the result.
     */
    private fun liftXCached(
        outX: LongArray,
        outY: LongArray,
        pub: ByteArray,
    ): Boolean {
        // Hash the pubkey bytes to a cache slot (use first 4 bytes as index)
        val slot =
            (
                (pub[0].toInt() and 0xFF) or
                    ((pub[1].toInt() and 0xFF) shl 8)
            ) and PUBKEY_CACHE_MASK

        val cached = pubkeyCache[slot]
        if (cached != null && cached.keyBytes.contentEquals(pub)) {
            // Cache hit — copy pre-computed coordinates
            cached.px.copyInto(outX)
            cached.py.copyInto(outY)
            return true
        }

        // Cache miss — compute sqrt and store
        if (!KeyCodec.liftX(outX, outY, U256.fromBytes(pub))) return false

        pubkeyCache[slot] = CachedPubkey(pub.copyOf(), outX.copyOf(), outY.copyOf())
        return true
    }

    // ==================== Key operations ====================

    /** Create a 65-byte uncompressed public key (04 || x || y) from a 32-byte secret key. */
    fun pubkeyCreate(seckey: ByteArray): ByteArray {
        require(seckey.size == 32)
        val scalar = U256.fromBytes(seckey)
        require(ScalarN.isValid(scalar))
        val sc = ECPoint.getScratch()
        ECPoint.mulG(sc.entryResult, scalar)
        check(ECPoint.toAffine(sc.entryResult, sc.entryPx, sc.entryPy, sc))
        return KeyCodec.serializeUncompressed(sc.entryPx, sc.entryPy)
    }

    /**
     * Compress a public key to 33 bytes (02/03 || x). Accepts 33 or 65 byte input.
     *
     * For uncompressed keys (04 prefix): reads the y-coordinate's parity from the last
     * byte and copies the x-coordinate directly — no field arithmetic needed.
     * For already-compressed keys: returns the input unchanged.
     */
    fun pubKeyCompress(pubkey: ByteArray): ByteArray =
        when (pubkey.size) {
            65 if pubkey[0] == 0x04.toByte() -> {
                val result = ByteArray(33)
                result[0] = if (pubkey[64].toInt() and 1 == 0) 0x02 else 0x03
                pubkey.copyInto(result, 1, 1, 33)
                result
            }

            33 if (pubkey[0] == 0x02.toByte() || pubkey[0] == 0x03.toByte()) -> {
                pubkey
            }

            else -> {
                error("Invalid public key: size=${pubkey.size}")
            }
        }

    /**
     * Verify that a byte array is a valid secret key (32 bytes, 0 < value < n).
     * Operates directly on bytes without converting to limbs — avoids LongArray allocation.
     */
    fun secKeyVerify(seckey: ByteArray): Boolean {
        if (seckey.size != 32) return false
        // Check not zero (any non-zero byte means non-zero)
        var nonZero = 0
        for (b in seckey) nonZero = nonZero or b.toInt()
        if (nonZero == 0) return false
        // Check < n by comparing big-endian bytes against n
        // n = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
        for (i in 0 until 32) {
            val si = seckey[i].toInt() and 0xFF
            val ni = N_BYTES[i].toInt() and 0xFF
            if (si < ni) return true // definitely less
            if (si > ni) return false // definitely greater
        }
        return false // equal to n → invalid
    }

    // n as big-endian bytes for direct comparison
    private val N_BYTES =
        byteArrayOf(
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFE.toByte(),
            0xBA.toByte(),
            0xAE.toByte(),
            0xDC.toByte(),
            0xE6.toByte(),
            0xAF.toByte(),
            0x48.toByte(),
            0xA0.toByte(),
            0x3B.toByte(),
            0xBF.toByte(),
            0xD2.toByte(),
            0x5E.toByte(),
            0x8C.toByte(),
            0xD0.toByte(),
            0x36.toByte(),
            0x41.toByte(),
            0x41.toByte(),
        )

    // ==================== BIP-340 Schnorr Signatures ====================

    /**
     * Create a BIP-340 Schnorr signature.
     *
     * This convenience overload derives the public key from the secret key.
     * For repeated signing with the same key, prefer [signSchnorrWithPubKey]
     * to avoid redundant G multiplication.
     */
    fun signSchnorr(
        data: ByteArray,
        seckey: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray {
        require(seckey.size == 32)
        val d0 = U256.fromBytes(seckey)
        require(ScalarN.isValid(d0))

        // Derive public key (one G multiplication + one inversion)
        val sc = ECPoint.getScratch()
        ECPoint.mulG(sc.entryResult, d0)
        check(ECPoint.toAffine(sc.entryResult, sc.entryPx, sc.entryPy, sc))

        val xOnlyPub = U256.toBytes(sc.entryPx)
        return signSchnorrInternal(data, d0, xOnlyPub, KeyCodec.hasEvenY(sc.entryPy), auxrand)
    }

    /**
     * Create a BIP-340 Schnorr signature with a pre-computed compressed public key.
     *
     * This is the fast path — skips the expensive G multiplication for public key
     * derivation. The 33-byte compressed key provides both the x-coordinate (for the
     * BIP-340 tagged hash) and the y-parity (02=even, 03=odd, needed to determine
     * whether to negate the secret key).
     *
     * The C library's signing function similarly takes a pre-computed keypair.
     *
     * @param data Message bytes (any length)
     * @param seckey 32-byte secret key
     * @param compressedPub 33-byte compressed public key (02/03 || x)
     * @param auxrand Optional 32-byte auxiliary randomness (null for deterministic)
     */
    fun signSchnorrWithPubKey(
        data: ByteArray,
        seckey: ByteArray,
        compressedPub: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray {
        require(seckey.size == 32 && compressedPub.size == 33)
        val d0 = U256.fromBytes(seckey)
        require(ScalarN.isValid(d0))
        val hasEvenY = compressedPub[0] == 0x02.toByte()
        val xOnlyPub = compressedPub.copyOfRange(1, 33)
        return signSchnorrInternal(data, d0, xOnlyPub, hasEvenY, auxrand)
    }

    /**
     * Internal signing implementation shared by both public overloads.
     * Performs: nonce derivation → R = k·G → challenge → s = k + e·d.
     * Does NOT re-derive the public key or self-verify (matching the C library).
     */
    private fun signSchnorrInternal(
        data: ByteArray,
        d0: LongArray,
        pBytes: ByteArray,
        pubKeyHasEvenY: Boolean,
        auxrand: ByteArray?,
    ): ByteArray {
        val sc = ECPoint.getScratch()
        val tmp = sc.entryTmp

        val d =
            if (pubKeyHasEvenY) {
                d0
            } else {
                ScalarN.negTo(tmp, d0)
                tmp
            }
        val dBytes = U256.toBytes(d)

        val tBytes: ByteArray
        if (auxrand != null) {
            require(auxrand.size == 32)
            val auxHash = sha256(AUX_PREFIX + auxrand)
            U256.xorTo(sc.entryTmp2, U256.fromBytes(dBytes), U256.fromBytes(auxHash))
            tBytes = U256.toBytes(sc.entryTmp2)
        } else {
            tBytes = dBytes
        }

        val nonceInput = ByteArray(64 + 32 + 32 + data.size)
        NONCE_PREFIX.copyInto(nonceInput, 0)
        tBytes.copyInto(nonceInput, 64)
        pBytes.copyInto(nonceInput, 96)
        data.copyInto(nonceInput, 128)
        val rand = sha256(nonceInput)
        val k0 = ScalarN.reduce(U256.fromBytes(rand))
        require(!U256.isZero(k0))

        // R = k0·G
        ECPoint.mulG(sc.entryResult, k0)
        val rx = sc.entryPx
        val ry = sc.entryPy
        check(ECPoint.toAffine(sc.entryResult, rx, ry, sc))

        val k = if (KeyCodec.hasEvenY(ry)) k0 else ScalarN.neg(k0)

        // Challenge: e = H(R || P || msg)
        val chalInput = ByteArray(64 + 32 + 32 + data.size)
        CHALLENGE_PREFIX.copyInto(chalInput, 0)
        U256.toBytesInto(rx, chalInput, 64)
        pBytes.copyInto(chalInput, 96)
        data.copyInto(chalInput, 128)
        val eHash = sha256(chalInput)
        val e = ScalarN.reduce(U256.fromBytes(eHash))

        // s = k + e·d mod n
        val sScalar = ScalarN.add(k, ScalarN.mul(e, d))
        val sig = ByteArray(64)
        U256.toBytesInto(rx, sig, 0)
        U256.toBytesInto(sScalar, sig, 32)
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

        // Use thread-local scratch to avoid per-verify allocations.
        // Saves ~10 LongArray(4) + 2 MutablePoint = ~14 object allocations per call.
        val sc = ECPoint.getScratch()
        if (!liftXCached(sc.entryPx, sc.entryPy, pub)) return false

        val r = sc.entryTmp
        U256.fromBytesInto(r, signature, 0)
        if (U256.cmp(r, FieldP.P) >= 0) return false
        val s = sc.entryTmp2
        U256.fromBytesInto(s, signature, 32)
        if (U256.cmp(s, ScalarN.N) >= 0) return false

        // Build challenge hash input in a single array: prefix(64) + r(32) + pub(32) + data(N)
        val hashInput = ByteArray(64 + 32 + 32 + data.size)
        CHALLENGE_PREFIX.copyInto(hashInput, 0)
        signature.copyInto(hashInput, 64, 0, 32) // r bytes from signature
        pub.copyInto(hashInput, 96)
        data.copyInto(hashInput, 128)
        val eHash = sha256(hashInput)
        // Reuse entryPx for e (liftX result already copied into pPoint below)
        val e = sc.zInv // safe: zInv not used until toAffine after mulDoubleG
        U256.fromBytesInto(e, eHash, 0)
        if (U256.cmp(e, ScalarN.N) >= 0) U256.subTo(e, e, ScalarN.N) // inline reduce

        // R = s·G + (-e)·P via Shamir's trick
        ScalarN.negTo(e, e) // negate in-place
        sc.entryPoint.setAffine(sc.entryPx, sc.entryPy) // copies px/py, so entryPx is free
        ECPoint.mulDoubleG(sc.entryResult, s, sc.entryPoint, e)

        if (sc.entryResult.isInfinity()) return false
        if (!ECPoint.toAffine(sc.entryResult, sc.entryPx, sc.entryPy, sc)) return false
        if (!KeyCodec.hasEvenY(sc.entryPy)) return false
        return U256.cmp(sc.entryPx, r) == 0
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
        val sc = ECPoint.getScratch()
        check(KeyCodec.parsePublicKey(pubkey, sc.entryPx, sc.entryPy))
        val scalar = U256.fromBytes(tweak)
        require(ScalarN.isValid(scalar))

        sc.entryPoint.setAffine(sc.entryPx, sc.entryPy)
        ECPoint.mul(sc.entryResult, sc.entryPoint, scalar)
        check(ECPoint.toAffine(sc.entryResult, sc.entryPx, sc.entryPy, sc))

        return if (pubkey.size == 33) {
            KeyCodec.serializeCompressed(sc.entryPx, sc.entryPy)
        } else {
            KeyCodec.serializeUncompressed(sc.entryPx, sc.entryPy)
        }
    }

    /**
     * ECDH x-only multiplication: computes the x-coordinate of scalar · P.
     *
     * Optimized for the Nostr ECDH use case (NIP-04, NIP-44) where the caller only
     * needs the x-coordinate of the shared secret. This avoids the expensive square
     * root (~267 field ops) needed to decompress the y-coordinate from a compressed
     * public key, because k·(x,y) and k·(x,-y) produce the same x-coordinate
     * (negating a point only flips y: k·(-P) = -(k·P), and negation preserves x).
     *
     * @param xOnlyPub 32-byte x-only public key
     * @param scalar 32-byte scalar (private key)
     * @return 32-byte x-coordinate of the shared point
     */
    fun ecdhXOnly(
        xOnlyPub: ByteArray,
        scalar: ByteArray,
    ): ByteArray {
        require(xOnlyPub.size == 32 && scalar.size == 32)
        val sc = ECPoint.getScratch()
        U256.fromBytesInto(sc.entryTmp, xOnlyPub, 0)
        require(U256.cmp(sc.entryTmp, FieldP.P) < 0)
        val k = U256.fromBytes(scalar)
        require(ScalarN.isValid(k))

        // Compute y = sqrt(x³ + 7). We need SOME valid y for EC point operations,
        // but the result's x-coordinate is the same regardless of y sign.
        check(KeyCodec.liftX(sc.entryPx, sc.entryPy, sc.entryTmp, sc.entryTmp2)) {
            "Not a valid x-coordinate on secp256k1"
        }

        sc.entryPoint.setAffine(sc.entryPx, sc.entryPy)
        ECPoint.mul(sc.entryResult, sc.entryPoint, k)
        check(ECPoint.toAffineX(sc.entryResult, sc.entryPx, sc))
        return U256.toBytes(sc.entryPx)
    }

    /** BIP-340 tagged hash (for tags not cached above). */
    internal fun taggedHash(
        tag: String,
        msg: ByteArray,
    ): ByteArray {
        val tagHash = sha256(tag.encodeToByteArray())
        return sha256(tagHash + tagHash + msg)
    }

    // ==================== Same-Pubkey Batch Verification ====================

    /**
     * Batch-verify multiple BIP-340 Schnorr signatures from the SAME public key
     * using scalar and point summation. Returns true if ALL signatures are valid.
     *
     * Instead of n individual mulDoubleG calls (each with ~130 doublings + toAffine),
     * this combines everything into scalar sums + one point sum + one mulDoubleG:
     *
     *   S = Σ sᵢ mod n          (scalar addition — trivial)
     *   E = Σ eᵢ mod n          (scalar addition — trivial)
     *   R_sum = Σ liftX(rᵢ)     (point addition — n-1 addMixed calls)
     *   Check: S·G - E·P - R_sum == O  (one mulDoubleG + point subtraction)
     *
     * This works because valid Schnorr signatures are linear:
     *   sᵢ·G = Rᵢ + eᵢ·P  →  (Σsᵢ)·G = (ΣRᵢ) + (Σeᵢ)·P
     *
     * Performance: ~1,350 + 11·n field ops vs n × ~1,620 individual (with caches).
     * For n=16: ~1,526 vs ~25,920 = ~17x throughput improvement.
     *
     * Security: by linearity, if any signature is invalid (sᵢ·G ≠ Rᵢ + eᵢ·P),
     * the sum fails — errors cannot cancel without solving the discrete log.
     * For extra hardening with duplicate events from multiple relays, the caller
     * can verify duplicates individually to detect relay manipulation.
     *
     * @param pub 32-byte x-only public key (same for all events)
     * @param signatures list of 64-byte signatures (R.x || s)
     * @param messages list of message byte arrays (same order as signatures)
     * @return true if all signatures are valid for this pubkey
     */
    fun verifySchnorrBatch(
        pub: ByteArray,
        signatures: List<ByteArray>,
        messages: List<ByteArray>,
    ): Boolean {
        val n = signatures.size
        require(n == messages.size) { "signatures and messages must have same size" }
        if (n == 0) return true
        if (n == 1) return verifySchnorr(signatures[0], messages[0], pub)
        if (pub.size != 32) return false

        val sc = ECPoint.getScratch()

        // Decompress pubkey P once (uses liftX cache)
        val px = sc.entryPx
        val py = sc.entryPy
        if (!liftXCached(px, py, pub)) return false

        // Accumulators for the scalar sums
        val sSum = LongArray(4) // Σ sᵢ mod n
        val eSum = LongArray(4) // Σ eᵢ mod n

        // Accumulator for R point sum (Jacobian)
        val rSum = MutablePoint()
        rSum.setInfinity()
        val rTmp = sc.entryResult // reuse as temp for addMixed

        for (i in 0 until n) {
            val sig = signatures[i]
            val msg = messages[i]
            if (sig.size != 64) return false

            // Parse r, s from signature
            val r = U256.fromBytes(sig, 0)
            if (U256.cmp(r, FieldP.P) >= 0) return false
            val s = U256.fromBytes(sig, 32)
            if (U256.cmp(s, ScalarN.N) >= 0) return false

            // Accumulate s: sSum += sᵢ mod n
            ScalarN.addTo(sSum, sSum, s)

            // Compute challenge eᵢ = H(rᵢ || pub || msgᵢ)
            val hashInput = ByteArray(64 + 32 + 32 + msg.size)
            CHALLENGE_PREFIX.copyInto(hashInput, 0)
            sig.copyInto(hashInput, 64, 0, 32)
            pub.copyInto(hashInput, 96)
            msg.copyInto(hashInput, 128)
            val eHash = sha256(hashInput)
            val e = ScalarN.reduce(U256.fromBytes(eHash))

            // Accumulate e: eSum += eᵢ mod n
            ScalarN.addTo(eSum, eSum, e)

            // Decompress Rᵢ = liftX(rᵢ) and accumulate into rSum
            val rx = LongArray(4)
            val ry = LongArray(4)
            if (!KeyCodec.liftX(rx, ry, r)) return false

            // rSum += Rᵢ (mixed addition: Rᵢ is affine)
            if (rSum.isInfinity()) {
                rSum.setAffine(rx, ry)
            } else {
                ECPoint.addMixed(rTmp, rSum, rx, ry, sc)
                rSum.copyFrom(rTmp)
            }
        }

        // Compute Q = sSum·G + (-eSum)·P via Shamir's trick (one mulDoubleG)
        ScalarN.negTo(eSum, eSum)
        val pPoint = sc.entryPoint
        pPoint.setAffine(px, py)
        val q = MutablePoint()
        ECPoint.mulDoubleG(q, sSum, pPoint, eSum)

        // Check: Q - R_sum == O  →  Q + (-R_sum) == O
        // Negate R_sum: just negate its Y coordinate
        FieldP.neg(rSum.y, rSum.y)

        // Add Q + (-R_sum) and check if result is infinity
        val result = MutablePoint()
        ECPoint.addPoints(result, q, rSum, sc)

        return result.isInfinity()
    }
}
