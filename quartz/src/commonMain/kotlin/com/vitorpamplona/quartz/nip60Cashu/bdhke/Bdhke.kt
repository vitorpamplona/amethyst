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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.secp256k1.ECPoint
import com.vitorpamplona.quartz.utils.secp256k1.Fe4
import com.vitorpamplona.quartz.utils.secp256k1.FieldP
import com.vitorpamplona.quartz.utils.secp256k1.KeyCodec
import com.vitorpamplona.quartz.utils.secp256k1.MutablePoint
import com.vitorpamplona.quartz.utils.secp256k1.ScalarN
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

        // The body was previously inlined here as one ~30-line method
        // with ~10 short-lived Fe4 / MutablePoint allocations. Android
        // 15+ ART crashes (SIGSEGV at 0x48 in "Jit thread pool") when
        // it tries to escape-analyze that exact allocation density.
        // Splitting into three smaller methods gives the JIT three
        // small bodies it can compile cleanly instead of one big one
        // that triggers the optimizer bug.
        val cTick = parseAffinePoint(blindSignature, "blind signature")
        val k = parseAffinePoint(mintPubKey, "mint public key")
        val negRk = computeNegRk(k, r)
        val out = MutablePoint()
        ECPoint.addPoints(out, cTick, negRk)
        return toCompressed(out)
    }

    /** Parse a 33-byte compressed pubkey into an affine [MutablePoint]. */
    private fun parseAffinePoint(
        compressed: ByteArray,
        label: String,
    ): MutablePoint {
        val px = Fe4()
        val py = Fe4()
        require(KeyCodec.parsePublicKey(compressed, px, py)) { "Invalid $label" }
        return MutablePoint().also { it.setAffine(px, py) }
    }

    /** Compute `-r·K` as an affine [MutablePoint]. */
    private fun computeNegRk(
        k: MutablePoint,
        r: ByteArray,
    ): MutablePoint {
        val rScalar = Fe4()
        U256.fromBytesInto(rScalar, r, 0)
        val rk = MutablePoint()
        ECPoint.mul(rk, k, rScalar)
        val rkX = Fe4()
        val rkY = Fe4()
        require(ECPoint.toAffine(rk, rkX, rkY)) { "rK is point at infinity" }
        FieldP.neg(rkY, rkY)
        return MutablePoint().also { it.setAffine(rkX, rkY) }
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
     * NUT-12 Alice-side DLEQ verification.
     *
     * Without this, the mint can return a junk `C'` and we won't notice
     * until we try to spend the proof — by which point any sender has
     * already considered the payment done. DLEQ proves the mint used the
     * private key whose pubkey is the [mintPubKey] we read from
     * `/v1/keys` — i.e. the same key for both [blindSignature] and
     * [mintPubKey].
     *
     * Math (from NUT-12):
     * ```
     *   R1 = sG - eA          where A = mintPubKey, e and s are the
     *   R2 = sB' - eC'        scalars the mint returns alongside C'.
     *   e' = SHA256(R1 || R2 || A || C')   (compressed, 33-byte each)
     *   return e' == e
     * ```
     *
     * Returns false on any malformed input (wrong byte length, scalars
     * outside [1, n), points not on curve) or DLEQ mismatch. Callers
     * should treat false as a protocol violation and abort the swap/mint.
     *
     * @param e 32-byte challenge scalar from the mint.
     * @param s 32-byte response scalar from the mint.
     * @param blindedMessage 33-byte compressed `B_` we sent.
     * @param blindSignature 33-byte compressed `C_` the mint returned.
     * @param mintPubKey 33-byte compressed `A = k·G` for this amount.
     */
    fun verifyDleq(
        e: ByteArray,
        s: ByteArray,
        blindedMessage: ByteArray,
        blindSignature: ByteArray,
        mintPubKey: ByteArray,
    ): Boolean {
        if (e.size != 32 || s.size != 32) return false
        if (blindedMessage.size != 33 || blindSignature.size != 33 || mintPubKey.size != 33) return false

        val eScalar = Fe4()
        U256.fromBytesInto(eScalar, e, 0)
        val sScalar = Fe4()
        U256.fromBytesInto(sScalar, s, 0)
        // `s` is `r' + e*k mod n` by construction (NUT-12 §2), so it must
        // be a canonical scalar < n; reject if not.
        if (!ScalarN.isValid(sScalar)) return false
        // `e` is a SHA-256 hash output that NUT-12 treats as raw bytes,
        // NOT a scalar mod n — there's no requirement that `e < n`. With
        // probability ~2^-128 a valid proof has `e >= n`; rejecting on
        // `isValid` here would spuriously fail those. We only check that
        // `e` isn't zero (a zero `e` would let any junk signature pass
        // because R1_check / R2_check would collapse to `sG` / `sB'`,
        // independent of the mint's keyset key). Downstream point
        // multiplications (`ECPoint.mul(_, A, eScalar)`) handle e >= n
        // correctly via internal GLV reduction.
        if (eScalar.isZero()) return false

        val aX = Fe4()
        val aY = Fe4()
        if (!KeyCodec.parsePublicKey(mintPubKey, aX, aY)) return false
        val aPoint = MutablePoint().also { it.setAffine(aX, aY) }

        val bX = Fe4()
        val bY = Fe4()
        if (!KeyCodec.parsePublicKey(blindedMessage, bX, bY)) return false
        val bPoint = MutablePoint().also { it.setAffine(bX, bY) }

        val cX = Fe4()
        val cY = Fe4()
        if (!KeyCodec.parsePublicKey(blindSignature, cX, cY)) return false
        val cPoint = MutablePoint().also { it.setAffine(cX, cY) }

        // R1 = sG - eA, R2 = sB' - eC'. We use the unblind() pattern for
        // point negation: compute the positive multiple, take affine, flip
        // the Y coordinate. Cheaper than computing (n-e) and re-multiplying.
        val sG = MutablePoint().also { ECPoint.mulG(it, sScalar) }
        val eA = MutablePoint().also { ECPoint.mul(it, aPoint, eScalar) }
        val negEa = negate(eA) ?: return false
        val r1 = MutablePoint().also { ECPoint.addPoints(it, sG, negEa) }

        val sB = MutablePoint().also { ECPoint.mul(it, bPoint, sScalar) }
        val eC = MutablePoint().also { ECPoint.mul(it, cPoint, eScalar) }
        val negEc = negate(eC) ?: return false
        val r2 = MutablePoint().also { ECPoint.addPoints(it, sB, negEc) }

        val r1Uncompressed = toUncompressedOrNull(r1) ?: return false
        val r2Uncompressed = toUncompressedOrNull(r2) ?: return false
        val aUncompressed = compressedToUncompressed(mintPubKey) ?: return false
        val cUncompressed = compressedToUncompressed(blindSignature) ?: return false

        // NUT-12 §2 hash input. The spec text says `sha256(R1 || R2 || A || C')`
        // but the normative behaviour established by every reference impl
        // (cashu-ts, nutshell, CDK) is:
        //   1. Serialize each point as 65-byte UNCOMPRESSED form (04 || X || Y)
        //   2. Hex-encode each (130 chars per point)
        //   3. Concatenate the four hex strings (520 chars)
        //   4. Hash the UTF-8 bytes of that concatenation
        // Real mints emit `e` computed this way. Earlier versions of this
        // code used raw 33-byte compressed bytes — round-tripping our own
        // [signFull] hid the mismatch but every real mint rejected the
        // proof. CDK's `hash_e` in crates/cashu/src/dhke.rs is the
        // authoritative reference.
        val hashInput =
            (r1Uncompressed.toHexKey() + r2Uncompressed.toHexKey() + aUncompressed.toHexKey() + cUncompressed.toHexKey())
                .encodeToByteArray()

        val computed = sha256(hashInput)
        return computed.contentEquals(e)
    }

    /**
     * NUT-12 §3 Carol-side DLEQ verification.
     *
     * Carol is the recipient of a proof that was minted by some other
     * wallet ("Alice"). The proof carries the UNBLINDED `C` and `(e, s, r)`,
     * where `r` is Alice's original blinding factor. To verify, Carol
     * has to reconstruct BOTH points that the DLEQ was proven over:
     *
     *   `B' = hashToCurve(secret) + r·G`   (the blinded message Alice sent)
     *   `C' = C + r·A`                     (the blinded signature the mint returned)
     *
     * Then she runs the same checks Alice did at mint-receive time.
     * Without `r` she couldn't compute either, which is exactly why the
     * NUT-12 dleq tuple carries `r` between wallets but never between
     * mint and wallet.
     *
     * Common pitfall (and the source of a real DLEQ-rejected-by-mint
     * production report): an earlier version of this code passed the
     * UNBLINDED `C` straight to [verifyDleq] as if it were `C'`. That
     * always failed because the math is over the blinded form. The
     * function name's `blindSignature` parameter is misleading — it's
     * actually the unblinded `C` from the proof; [verifyDleq] gets the
     * reconstructed `C'`.
     *
     * Returns false on any malformed input (wrong sizes, off-curve
     * points, infinity arithmetic) or mismatch.
     *
     * @param secret The proof's secret bytes (UTF-8 of the hex string
     *               the mint hashed in hash-to-curve, NOT raw 32 bytes).
     *               Same form as [CashuProof.secret].
     * @param r 32-byte blinding factor Alice used; carried in the proof's
     *          dleq tuple by spec.
     * @param e 32-byte challenge scalar from the dleq tuple.
     * @param s 32-byte response scalar from the dleq tuple.
     * @param unblindedC 33-byte `C` from the proof — the unblinded
     *                   signature the wallet stores after Alice's
     *                   [unblind].
     * @param mintPubKey 33-byte compressed `A = k·G` for this amount,
     *                   looked up from the mint's keyset.
     */
    fun verifyDleqCarol(
        secret: ByteArray,
        r: ByteArray,
        e: ByteArray,
        s: ByteArray,
        unblindedC: ByteArray,
        mintPubKey: ByteArray,
    ): Boolean {
        if (r.size != 32) return false
        if (unblindedC.size != 33) return false
        if (mintPubKey.size != 33) return false
        // Reconstruct B' the way Alice did at mint time.
        val bTick = runCatching { blind(secret, r) }.getOrNull() ?: return false
        // Reconstruct C' = C + r·A. Without this, verifyDleq is computing
        // the DLEQ check against the wrong point and always returns false
        // — this was a real production bug.
        val cTick = runCatching { addRTimesA(unblindedC, r, mintPubKey) }.getOrNull() ?: return false
        return verifyDleq(
            e = e,
            s = s,
            blindedMessage = bTick,
            blindSignature = cTick,
            mintPubKey = mintPubKey,
        )
    }

    /**
     * Returns `C + r·A` as a 33-byte compressed point. Inverse of the
     * `C' - r·A` step inside [unblind]; used by [verifyDleqCarol] to
     * reconstruct the blinded signature from its unblinded form plus
     * the blinding factor.
     */
    private fun addRTimesA(
        c: ByteArray,
        r: ByteArray,
        mintPubKey: ByteArray,
    ): ByteArray {
        val cX = Fe4()
        val cY = Fe4()
        require(KeyCodec.parsePublicKey(c, cX, cY)) { "Invalid C" }
        val cPoint = MutablePoint().also { it.setAffine(cX, cY) }

        val kx = Fe4()
        val ky = Fe4()
        require(KeyCodec.parsePublicKey(mintPubKey, kx, ky)) { "Invalid mint public key" }
        val k = MutablePoint().also { it.setAffine(kx, ky) }

        val rScalar = Fe4()
        U256.fromBytesInto(rScalar, r, 0)
        val rk = MutablePoint()
        ECPoint.mul(rk, k, rScalar)

        val out = MutablePoint()
        ECPoint.addPoints(out, cPoint, rk)
        return toCompressed(out)
    }

    /**
     * Mint-side NUT-12 DLEQ-signing — produces `(C', e, s)` for the given
     * `B'`. Test-only; real mints have their own signers. Useful for
     * round-tripping verification in unit tests without standing up a mint.
     *
     * Returns `Triple(cTick, e, s)` where each is 33 / 32 / 32 bytes.
     */
    fun signFull(
        blindedMessage: ByteArray,
        mintPrivKey: ByteArray,
        rPrime: ByteArray = randomScalar(),
    ): Triple<ByteArray, ByteArray, ByteArray> {
        require(rPrime.size == 32) { "DLEQ nonce must be 32 bytes" }
        require(Secp256k1.secKeyVerify(rPrime)) { "Invalid DLEQ nonce" }

        val cTick = sign(blindedMessage, mintPrivKey)

        val rPrimeScalar = Fe4()
        U256.fromBytesInto(rPrimeScalar, rPrime, 0)

        val r1 = MutablePoint().also { ECPoint.mulG(it, rPrimeScalar) }
        val r1Uncompressed = toUncompressedOrNull(r1) ?: error("R1 is at infinity")

        val bX = Fe4()
        val bY = Fe4()
        require(KeyCodec.parsePublicKey(blindedMessage, bX, bY)) { "Invalid B'" }
        val bPoint = MutablePoint().also { it.setAffine(bX, bY) }
        val r2 = MutablePoint().also { ECPoint.mul(it, bPoint, rPrimeScalar) }
        val r2Uncompressed = toUncompressedOrNull(r2) ?: error("R2 is at infinity")

        // pubkeyCreate returns 65-byte uncompressed — exactly what
        // NUT-12 wants in the hash input. Don't compress.
        val aUncompressed = Secp256k1.pubkeyCreate(mintPrivKey)
        val cUncompressed = compressedToUncompressed(cTick) ?: error("Invalid C' from sign()")

        // See [verifyDleq] for the hash format. Uncompressed (65-byte)
        // points serialized as UTF-8 hex strings concatenated, NOT raw
        // bytes. Real mints emit `e` this way; CDK / cashu-ts / nutshell
        // all agree.
        val hashInput =
            (r1Uncompressed.toHexKey() + r2Uncompressed.toHexKey() + aUncompressed.toHexKey() + cUncompressed.toHexKey())
                .encodeToByteArray()
        val e = sha256(hashInput)

        // s = r' + e*k mod n
        val eScalar = Fe4()
        U256.fromBytesInto(eScalar, e, 0)
        val kScalar = Fe4()
        U256.fromBytesInto(kScalar, mintPrivKey, 0)
        val ek = ScalarN.mul(eScalar, kScalar)
        val sScalar = ScalarN.add(rPrimeScalar, ek)
        val s = U256.toBytes(sScalar)

        return Triple(cTick, e, s)
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

    /**
     * 65-byte uncompressed form `04 || X || Y`. Used only by the
     * NUT-12 hash input — the on-wire form for everything else is
     * compressed. Null on infinity.
     */
    private fun toUncompressedOrNull(p: MutablePoint): ByteArray? {
        val x = Fe4()
        val y = Fe4()
        return if (ECPoint.toAffine(p, x, y)) KeyCodec.serializeUncompressed(x, y) else null
    }

    /**
     * Convert a 33-byte compressed point to its 65-byte uncompressed
     * form. Used for the NUT-12 hash input where the spec requires
     * uncompressed bytes (`04 || X || Y`) rather than the wire form
     * (`02/03 || X`). Returns null on parse failure.
     */
    private fun compressedToUncompressed(compressed: ByteArray): ByteArray? {
        val x = Fe4()
        val y = Fe4()
        if (!KeyCodec.parsePublicKey(compressed, x, y)) return null
        return KeyCodec.serializeUncompressed(x, y)
    }

    /** Non-throwing variant for verification paths — infinity → null instead of crash. */
    private fun toCompressedOrNull(p: MutablePoint): ByteArray? {
        val x = Fe4()
        val y = Fe4()
        return if (ECPoint.toAffine(p, x, y)) KeyCodec.serializeCompressed(x, y) else null
    }

    /** Negate a point via the affine-Y flip pattern used in [unblind]. Returns null on infinity. */
    private fun negate(p: MutablePoint): MutablePoint? {
        val x = Fe4()
        val y = Fe4()
        if (!ECPoint.toAffine(p, x, y)) return null
        FieldP.neg(y, y)
        return MutablePoint().also { it.setAffine(x, y) }
    }

    // Spec doesn't bound this; in practice the first iteration succeeds with
    // probability ~1/2. We cap at 2^16 — astronomically unlikely to hit.
    private const val MAX_HASH_TO_CURVE_ITERATIONS = 65536
}
