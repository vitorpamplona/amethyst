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
 * Linux/Native X25519 implementation using pure Kotlin field arithmetic.
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
     */
    private fun scalarmult(
        n: ByteArray,
        p: ByteArray,
    ): ByteArray {
        val z = n.copyOf()
        z[0] = (z[0].toInt() and 248).toByte()
        z[31] = ((z[31].toInt() and 127) or 64).toByte()

        val x = Curve25519Field.unpack25519(p)
        val a = Curve25519Field.GF1.copyOf()
        val b = x.copyOf()
        val c = Curve25519Field.GF0.copyOf()
        val d = Curve25519Field.GF1.copyOf()

        for (i in 254 downTo 0) {
            val r = ((z[i shr 3].toLong() shr (i and 7)) and 1)
            Curve25519Field.sel25519(a, b, r)
            Curve25519Field.sel25519(c, d, r)

            val e = Curve25519Field.add(a, c)
            val aMc = Curve25519Field.sub(a, c)
            val f = Curve25519Field.add(b, d)
            val bMd = Curve25519Field.sub(b, d)

            val dd = Curve25519Field.sqr(e)
            val ff = Curve25519Field.sqr(aMc)
            val da = Curve25519Field.mul(bMd, e)
            val cb = Curve25519Field.mul(f, aMc)

            val ePrime = Curve25519Field.add(da, cb)
            val aPrime = Curve25519Field.sub(da, cb)

            val bNew = Curve25519Field.sqr(ePrime)
            val aSqr = Curve25519Field.sqr(aPrime)
            val dNew = Curve25519Field.mul(aSqr, x)

            val aNew = Curve25519Field.mul(dd, ff)
            val cc = Curve25519Field.sub(dd, ff)
            val tmp = Curve25519Field.mul(cc, Curve25519Field.A24)
            val ddPlusTmp = Curve25519Field.add(dd, tmp)
            val cNew = Curve25519Field.mul(cc, ddPlusTmp)

            aNew.copyInto(a)
            bNew.copyInto(b)
            cNew.copyInto(c)
            dNew.copyInto(d)

            Curve25519Field.sel25519(a, b, r)
            Curve25519Field.sel25519(c, d, r)
        }

        val invC = Curve25519Field.inv25519(c)
        val result = Curve25519Field.mul(a, invC)
        return Curve25519Field.pack25519(result)
    }
}
