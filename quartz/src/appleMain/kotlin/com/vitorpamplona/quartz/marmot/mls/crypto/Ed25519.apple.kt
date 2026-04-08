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
package com.vitorpamplona.quartz.marmot.mls.crypto

import com.vitorpamplona.quartz.utils.RandomInstance
import io.github.andreypfau.kotlinx.crypto.Sha512

/**
 * Apple/Native Ed25519 implementation using pure Kotlin field arithmetic.
 *
 * Implements RFC 8032 Ed25519 digital signatures.
 * Private key format: 32-byte seed + 32-byte public key (64 bytes total).
 * Public key format: 32-byte compressed Edwards point.
 *
 * Based on the TweetNaCl algorithm by Bernstein et al.
 */
actual object Ed25519 {
    private const val SEED_LENGTH = 32
    private const val PUBLIC_KEY_LENGTH = 32

    actual fun generateKeyPair(): Ed25519KeyPair {
        val seed = RandomInstance.bytes(SEED_LENGTH)
        val publicKey = derivePublicKey(seed)
        val privateKey = seed + publicKey
        return Ed25519KeyPair(privateKey, publicKey)
    }

    actual fun sign(
        message: ByteArray,
        privateKey: ByteArray,
    ): ByteArray {
        require(privateKey.size == SEED_LENGTH * 2) { "Private key must be 64 bytes (seed + public)" }

        val seed = privateKey.copyOfRange(0, SEED_LENGTH)
        val publicKey = privateKey.copyOfRange(SEED_LENGTH, SEED_LENGTH * 2)

        // SHA-512(seed) -> expanded key
        val d = sha512(seed)
        // Clamp the scalar
        d[0] = (d[0].toInt() and 248).toByte()
        d[31] = ((d[31].toInt() and 63) or 64).toByte()

        // r = SHA-512(d[32..63] || message) mod L
        val rHash = sha512(d.copyOfRange(32, 64) + message)
        val r = reduce(rHash)

        // R = [r]B
        val rPoint = scalarMultBase(r)
        val rBytes = packPoint(rPoint)

        // S = (r + SHA-512(R || publicKey || message) * s) mod L
        val hramHash = sha512(rBytes + publicKey + message)
        val hram = reduce(hramHash)

        val signature = ByteArray(64)
        rBytes.copyInto(signature, 0)

        // Compute s = r + hram * d (mod L) in 64-byte arithmetic
        val x = LongArray(64)
        for (i in 0 until 32) x[i] = r[i].toLong() and 0xFF
        for (i in 0 until 32) {
            for (j in 0 until 32) {
                x[i + j] += (hram[i].toLong() and 0xFF) * (d[j].toLong() and 0xFF)
            }
        }

        val sBytes = modL(x)
        sBytes.copyInto(signature, 32)

        return signature
    }

    actual fun verify(
        message: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray,
    ): Boolean {
        require(publicKey.size == PUBLIC_KEY_LENGTH) { "Public key must be 32 bytes" }
        if (signature.size != 64) return false

        val aPoint = unpackPoint(publicKey) ?: return false

        val rBytes = signature.copyOfRange(0, 32)
        val sBytes = signature.copyOfRange(32, 64)

        // Check s < L
        if (!isCanonicalScalar(sBytes)) return false

        val hramHash = sha512(rBytes + publicKey + message)
        val hram = reduce(hramHash)

        // Verify: [s]B = R + [hram]A
        // Equivalent: [s]B - [hram]A = R
        // We compute: [s]B and [hram]A separately, then check
        val sPoint = scalarMultBase(sBytes)

        // Negate A for subtraction: compute [-hram]A
        val hramA = scalarMult(aPoint, hram)

        // R_check = [s]B - [hram]A = [s]B + [-hram]A
        val negHramA = negatePoint(hramA)
        val rCheck = addPoints(sPoint, negHramA)
        val rCheckBytes = packPoint(rCheck)

        return rCheckBytes.contentEquals(rBytes)
    }

    actual fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == SEED_LENGTH * 2) { "Private key must be 64 bytes (seed + public)" }
        return privateKey.copyOfRange(SEED_LENGTH, SEED_LENGTH * 2)
    }

    // --- Internal operations ---

    /** Derive Ed25519 public key from 32-byte seed. */
    private fun derivePublicKey(seed: ByteArray): ByteArray {
        val d = sha512(seed)
        d[0] = (d[0].toInt() and 248).toByte()
        d[31] = ((d[31].toInt() and 63) or 64).toByte()

        val p = scalarMultBase(d.copyOfRange(0, 32))
        return packPoint(p)
    }

    /** SHA-512 hash using the platform library. */
    private fun sha512(data: ByteArray): ByteArray {
        val digest = Sha512()
        digest.update(data)
        return digest.digest()
    }

    // --- Extended Edwards point operations ---
    // Point = Array of 4 LongArray(16), representing (X, Y, Z, T)
    // where x = X/Z, y = Y/Z, x*y = T/Z

    private fun newPoint(): Array<LongArray> =
        arrayOf(
            LongArray(16),
            LongArray(16),
            LongArray(16),
            LongArray(16),
        )

    /** Set point to the identity (0, 1, 1, 0). */
    private fun identityPoint(): Array<LongArray> {
        val p = newPoint()
        p[1][0] = 1
        p[2][0] = 1
        return p
    }

    /** Point addition on extended twisted Edwards curve. */
    private fun addPoints(
        p: Array<LongArray>,
        q: Array<LongArray>,
    ): Array<LongArray> {
        val result =
            arrayOf(
                p[0].copyOf(),
                p[1].copyOf(),
                p[2].copyOf(),
                p[3].copyOf(),
            )
        addPointInPlace(result, q)
        return result
    }

    /** In-place point addition: p += q. */
    private fun addPointInPlace(
        p: Array<LongArray>,
        q: Array<LongArray>,
    ) {
        val a = Curve25519Field.sub(p[1], p[0])
        val t = Curve25519Field.sub(q[1], q[0])
        val aMul = Curve25519Field.mul(a, t)
        val b = Curve25519Field.add(p[0], p[1])
        val t2 = Curve25519Field.add(q[0], q[1])
        val bMul = Curve25519Field.mul(b, t2)
        val c = Curve25519Field.mul(p[3], q[3])
        val cMul = Curve25519Field.mul(c, Curve25519Field.D2)
        val d = Curve25519Field.mul(p[2], q[2])
        val dAdd = Curve25519Field.add(d, d)
        val e = Curve25519Field.sub(bMul, aMul)
        val f = Curve25519Field.sub(dAdd, cMul)
        val g = Curve25519Field.add(dAdd, cMul)
        val h = Curve25519Field.add(bMul, aMul)

        Curve25519Field.mul(e, f).copyInto(p[0])
        Curve25519Field.mul(h, g).copyInto(p[1])
        Curve25519Field.mul(g, f).copyInto(p[2])
        Curve25519Field.mul(e, h).copyInto(p[3])
    }

    /** Point doubling (self-addition). */
    private fun doublePoint(p: Array<LongArray>): Array<LongArray> = addPoints(p, p)

    /** Negate a point: (X, Y, Z, T) -> (-X, Y, Z, -T). */
    private fun negatePoint(p: Array<LongArray>): Array<LongArray> {
        val result = newPoint()
        Curve25519Field.sub(Curve25519Field.GF0, p[0]).copyInto(result[0])
        p[1].copyInto(result[1])
        p[2].copyInto(result[2])
        Curve25519Field.sub(Curve25519Field.GF0, p[3]).copyInto(result[3])
        return result
    }

    /** Scalar multiplication: [s]P using double-and-add. */
    private fun scalarMult(
        p: Array<LongArray>,
        s: ByteArray,
    ): Array<LongArray> {
        val result = identityPoint()
        val q =
            arrayOf(
                p[0].copyOf(),
                p[1].copyOf(),
                p[2].copyOf(),
                p[3].copyOf(),
            )
        for (i in 255 downTo 0) {
            val b = ((s[i shr 3].toInt() shr (i and 7)) and 1).toLong()
            cswap(result, q, b)
            addPointInPlace(q, result)
            addPointInPlace(result, result)
            cswap(result, q, b)
        }
        return result
    }

    /** Scalar multiplication with the base point: [s]B. */
    private fun scalarMultBase(s: ByteArray): Array<LongArray> {
        val basePoint = newPoint()
        Curve25519Field.BX.copyInto(basePoint[0])
        Curve25519Field.BY.copyInto(basePoint[1])
        Curve25519Field.GF1.copyInto(basePoint[2])
        Curve25519Field.mul(Curve25519Field.BX, Curve25519Field.BY).copyInto(basePoint[3])
        return scalarMult(basePoint, s)
    }

    /** Conditional swap of two points. */
    private fun cswap(
        p: Array<LongArray>,
        q: Array<LongArray>,
        b: Long,
    ) {
        for (i in 0 until 4) {
            Curve25519Field.sel25519(p[i], q[i], b)
        }
    }

    /** Pack an extended Edwards point to 32-byte compressed encoding. */
    private fun packPoint(p: Array<LongArray>): ByteArray {
        val zi = Curve25519Field.inv25519(p[2])
        val tx = Curve25519Field.mul(p[0], zi)
        val ty = Curve25519Field.mul(p[1], zi)
        val r = Curve25519Field.pack25519(ty)
        r[31] = (r[31].toInt() xor (Curve25519Field.par25519(tx) shl 7)).toByte()
        return r
    }

    /**
     * Unpack a 32-byte compressed Edwards point.
     * Returns null if the point is not on the curve.
     */
    private fun unpackPoint(s: ByteArray): Array<LongArray>? {
        val p = newPoint()
        val r = Curve25519Field.unpack25519(s)
        r.copyInto(p[1])
        Curve25519Field.GF1.copyInto(p[2])

        // Recover x from y: x^2 = (y^2 - 1) / (d * y^2 + 1)
        val y2 = Curve25519Field.sqr(r)
        val d =
            Curve25519Field.gf(
                0x78A3,
                0x1359,
                0x4DCA,
                0x75EB,
                0xD8AB,
                0x4141,
                0x0A4D,
                0x0070,
                0xE898,
                0x7779,
                0x4079,
                0x8CC7,
                0xFE73,
                0x2B6F,
                0x6CEE,
                0x5203,
            )
        val num = Curve25519Field.sub(y2, Curve25519Field.GF1)
        val den = Curve25519Field.add(Curve25519Field.mul(d, y2), Curve25519Field.GF1)
        val denInv = Curve25519Field.inv25519(den)
        var x2 = Curve25519Field.mul(num, denInv)

        // Try sqrt(x2)
        var x = Curve25519Field.pow2523(x2)
        x = Curve25519Field.mul(x, x2)

        // Check: x^2 == x2?
        val check = Curve25519Field.sub(Curve25519Field.sqr(x), x2)
        val checkPacked = Curve25519Field.pack25519(check)
        if (!checkPacked.all { it == 0.toByte() }) {
            // Try x * sqrt(-1)
            x = Curve25519Field.mul(x, Curve25519Field.I)
            val check2 = Curve25519Field.sub(Curve25519Field.sqr(x), x2)
            val check2Packed = Curve25519Field.pack25519(check2)
            if (!check2Packed.all { it == 0.toByte() }) {
                return null
            }
        }

        // Adjust sign
        if (Curve25519Field.par25519(x) != ((s[31].toInt() shr 7) and 1)) {
            x = Curve25519Field.sub(Curve25519Field.GF0, x)
        }

        x.copyInto(p[0])
        Curve25519Field.mul(p[0], p[1]).copyInto(p[3])
        return p
    }

    // --- Scalar reduction modulo L ---
    // L = 2^252 + 27742317777372353535851937790883648493

    private val L =
        longArrayOf(
            0xED,
            0xD3,
            0xF5,
            0x5C,
            0x1A,
            0x63,
            0x12,
            0x58,
            0xD6,
            0x9C,
            0xF7,
            0xA2,
            0xDE,
            0xF9,
            0xDE,
            0x14,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0x10,
        )

    /**
     * Reduce a 64-byte hash to a 32-byte scalar modulo L.
     * This is used for Ed25519 nonce and challenge computation.
     */
    private fun reduce(input: ByteArray): ByteArray {
        val x = LongArray(64)
        for (i in 0 until 64) x[i] = input[i].toLong() and 0xFF
        return modL(x)
    }

    /**
     * Reduce a 64-element long array modulo L, returning a 32-byte result.
     */
    private fun modL(x: LongArray): ByteArray {
        for (i in 63 downTo 32) {
            var carry: Long = 0
            var j = i - 32
            val k = i - 12
            while (j < k) {
                x[j] += carry - 16 * x[i] * L[j - (i - 32)]
                carry = (x[j] + 128) shr 8
                x[j] -= carry shl 8
                j++
            }
            x[j] += carry
            x[i] = 0
        }

        var carry: Long = 0
        for (j in 0 until 32) {
            x[j] += carry - (x[31] shr 4) * L[j]
            carry = x[j] shr 8
            x[j] = x[j] and 0xFF
        }
        for (j in 0 until 32) {
            x[j] -= carry * L[j]
        }

        val r = ByteArray(32)
        for (i in 0 until 32) {
            x[i + 1] += x[i] shr 8
            r[i] = (x[i] and 0xFF).toByte()
        }
        return r
    }

    /** Check if a scalar s < L (canonical). */
    private fun isCanonicalScalar(s: ByteArray): Boolean {
        // Check s < L by comparing from high byte to low
        var borrow: Long = 0
        for (i in 31 downTo 0) {
            val si = s[i].toLong() and 0xFF
            val li = L[i]
            if (si < li + borrow) return true
            if (si > li + borrow) return false
            borrow = 0
        }
        return false // s == L is not canonical
    }
}
