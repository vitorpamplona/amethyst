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

/**
 * Apple/Native X25519 implementation using pure Kotlin field arithmetic.
 *
 * Implements RFC 7748 X25519 Diffie-Hellman key agreement via Montgomery ladder.
 * Key format: raw 32-byte Curve25519 keys (little-endian per RFC 7748).
 */
actual object X25519 {
    private const val KEY_LENGTH = 32

    actual fun generateKeyPair(): X25519KeyPair {
        val privateKey = RandomInstance.bytes(KEY_LENGTH)
        val publicKey = publicFromPrivate(privateKey)
        return X25519KeyPair(privateKey, publicKey)
    }

    actual fun dh(
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): ByteArray {
        require(privateKey.size == KEY_LENGTH) { "Private key must be 32 bytes" }
        require(publicKey.size == KEY_LENGTH) { "Public key must be 32 bytes" }

        val result = scalarmult(privateKey, publicKey)

        require(!result.all { it == 0.toByte() }) {
            "DH produced all-zero shared secret (possible small-subgroup attack)"
        }

        return result
    }

    actual fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_LENGTH) { "Private key must be 32 bytes" }
        val basepoint = ByteArray(KEY_LENGTH)
        basepoint[0] = 9
        return scalarmult(privateKey, basepoint)
    }

    /**
     * X25519 scalar multiplication via Montgomery ladder (RFC 7748).
     *
     * Computes [n]P on Curve25519 in Montgomery form.
     * Based on the TweetNaCl algorithm by Bernstein et al.
     */
    private fun scalarmult(
        n: ByteArray,
        p: ByteArray,
    ): ByteArray {
        val z = n.copyOf()
        // Clamp scalar per RFC 7748 Section 5
        z[0] = (z[0].toInt() and 248).toByte()
        z[31] = ((z[31].toInt() and 127) or 64).toByte()

        val x = Curve25519Field.unpack25519(p)
        val a = Curve25519Field.GF1.copyOf()
        val b = x.copyOf()
        val c = Curve25519Field.GF0.copyOf()
        val d = Curve25519Field.GF1.copyOf()

        // The ladder's entire working set, allocated ONCE. Every field
        // operation in the loop writes into one of these, so 255 iterations
        // allocate nothing at all — where the allocating form produced a fresh
        // element per operation, about 1.3 MB of garbage per call.
        val e = LongArray(16)
        val f = LongArray(16)
        val g = LongArray(16)
        val h = LongArray(16)
        val dd = LongArray(16)
        val ff = LongArray(16)
        val da = LongArray(16)
        val cb = LongArray(16)
        val cc = LongArray(16)
        val tmp = LongArray(16)
        val t = LongArray(31)

        for (i in 254 downTo 0) {
            val r = ((z[i shr 3].toLong() shr (i and 7)) and 1)
            Curve25519Field.sel25519(a, b, r)
            Curve25519Field.sel25519(c, d, r)

            // a, b, c and d are read only by these four lines; from here on
            // they are dead and can be overwritten with the new values.
            Curve25519Field.addInto(e, a, c)
            Curve25519Field.subInto(g, a, c)
            Curve25519Field.addInto(f, b, d)
            Curve25519Field.subInto(h, b, d)

            Curve25519Field.sqrInto(dd, e, t)
            Curve25519Field.sqrInto(ff, g, t)
            Curve25519Field.mulInto(da, h, e, t)
            Curve25519Field.mulInto(cb, f, g, t)

            // e := da + cb and g := da - cb. Reusing e and g is safe: both
            // held inputs to the four products above, which are now computed.
            Curve25519Field.addInto(e, da, cb)
            Curve25519Field.subInto(g, da, cb)

            Curve25519Field.sqrInto(b, e, t)
            Curve25519Field.sqrInto(g, g, t)
            Curve25519Field.mulInto(d, g, x, t)

            Curve25519Field.mulInto(a, dd, ff, t)
            Curve25519Field.subInto(cc, dd, ff)
            Curve25519Field.mulInto(tmp, cc, Curve25519Field.A24, t)
            Curve25519Field.addInto(tmp, dd, tmp)
            Curve25519Field.mulInto(c, cc, tmp, t)

            Curve25519Field.sel25519(a, b, r)
            Curve25519Field.sel25519(c, d, r)
        }

        // c := 1/c, then a := a/c. `tmp` is free again and serves as the
        // inversion's scratch element.
        Curve25519Field.inv25519Into(c, c, tmp, t)
        Curve25519Field.mulInto(a, a, c, t)
        return Curve25519Field.pack25519(a)
    }
}
