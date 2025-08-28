/**
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
package com.vitorpamplona.quartz.nip49PrivKeyEnc

import com.vitorpamplona.quartz.nip49PrivKeyEnc.PBKDF.pbkdf2
import java.security.GeneralSecurityException
import javax.crypto.Mac

/**
 * An implementation of the [](http://www.tarsnap.com/scrypt/scrypt.pdf)scrypt
 * key derivation function. This class will attempt to load a native library
 * containing the optimized C implementation from
 * [http://www.tarsnap.com/scrypt.html<a> and
 * fall back to the pure Java version if that fails.
 *
 * @author  Will Glozer
</a>](http://www.tarsnap.com/scrypt.html) */
object SCrypt {
    /**
     * Implementation of the [](http://www.tarsnap.com/scrypt/scrypt.pdf)scrypt KDF.
     *
     * @param passwd    Password.
     * @param salt      Salt.
     * @param n         CPU cost parameter.
     * @param r         Memory cost parameter.
     * @param p         Parallelization parameter.
     * @param dkLen     Intended length of the derived key.
     *
     * @return The derived key.
     *
     * @throws GeneralSecurityException when HMAC_SHA256 is not available.
     */
    @Throws(GeneralSecurityException::class)
    fun scrypt(
        passwd: ByteArray,
        salt: ByteArray,
        n: Int,
        r: Int,
        p: Int,
        dkLen: Int,
    ): ByteArray = scryptJ(passwd, salt, n, r, p, dkLen)

    /**
     * Pure Java implementation of the [](http://www.tarsnap.com/scrypt/scrypt.pdf)scrypt KDF.
     *
     * @param passwd    Password.
     * @param salt      Salt.
     * @param n         CPU cost parameter.
     * @param r         Memory cost parameter.
     * @param p         Parallelization parameter.
     * @param dkLen     Intended length of the derived key.
     *
     * @return The derived key.
     *
     * @throws GeneralSecurityException when HMAC_SHA256 is not available.
     */
    @Throws(GeneralSecurityException::class)
    fun scryptJ(
        passwd: ByteArray,
        salt: ByteArray,
        n: Int,
        r: Int,
        p: Int,
        dkLen: Int,
    ): ByteArray {
        require(!(n < 2 || (n and (n - 1)) != 0)) { "N must be a power of 2 greater than 1" }

        require(n <= Int.Companion.MAX_VALUE / 128 / r) { "Parameter N is too large" }
        require(r <= Int.Companion.MAX_VALUE / 128 / p) { "Parameter r is too large" }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeyOrEmptySpec(passwd, "HmacSHA256"))

        val deskKey = ByteArray(dkLen)

        val bArray = ByteArray(128 * r * p)
        val xyArray = ByteArray(256 * r)
        val vArray = ByteArray(128 * r * n)
        var i: Int

        pbkdf2(mac, salt, 1, bArray, p * 128 * r)

        i = 0
        while (i < p) {
            smix(bArray, i * 128 * r, r, n, vArray, xyArray)
            i++
        }

        pbkdf2(mac, bArray, 1, deskKey, dkLen)

        return deskKey
    }

    fun smix(
        bArray: ByteArray,
        biArray: Int,
        r: Int,
        n: Int,
        vArray: ByteArray,
        xyArray: ByteArray,
    ) {
        val xIndex = 0
        val yIndex = 128 * r
        var i: Int

        System.arraycopy(bArray, biArray, xyArray, xIndex, 128 * r)

        i = 0
        while (i < n) {
            System.arraycopy(xyArray, xIndex, vArray, i * (128 * r), 128 * r)
            blockmixSalsa8(xyArray, xIndex, yIndex, r)
            i++
        }

        i = 0
        while (i < n) {
            val j = integerify(xyArray, xIndex, r) and (n - 1)
            blockxor(vArray, j * (128 * r), xyArray, xIndex, 128 * r)
            blockmixSalsa8(xyArray, xIndex, yIndex, r)
            i++
        }

        System.arraycopy(xyArray, xIndex, bArray, biArray, 128 * r)
    }

    fun blockmixSalsa8(
        byArray: ByteArray,
        biArray: Int,
        yi: Int,
        r: Int,
    ) {
        val xArray = ByteArray(64)
        var i: Int

        System.arraycopy(byArray, biArray + (2 * r - 1) * 64, xArray, 0, 64)

        i = 0
        while (i < 2 * r) {
            blockxor(byArray, i * 64, xArray, 0, 64)
            salsa20x8(xArray)
            System.arraycopy(xArray, 0, byArray, yi + (i * 64), 64)
            i++
        }

        i = 0
        while (i < r) {
            System.arraycopy(byArray, yi + (i * 2) * 64, byArray, biArray + (i * 64), 64)
            i++
        }

        i = 0
        while (i < r) {
            System.arraycopy(byArray, yi + (i * 2 + 1) * 64, byArray, biArray + (i + r) * 64, 64)
            i++
        }
    }

    // NOT SURE IF THIS IS ACTUALLY A ROTATE. Ignore the name
    fun rotate(
        a: Int,
        b: Int,
    ): Int = (a shl b) or (a ushr (32 - b))

    fun salsa20x8(bArray: ByteArray) {
        val b32 = IntArray(16)
        val x = IntArray(16)
        var i: Int

        i = 0
        while (i < 16) {
            b32[i] = (bArray[i * 4 + 0].toInt() and 0xff) shl 0
            b32[i] = b32[i] or ((bArray[i * 4 + 1].toInt() and 0xff) shl 8)
            b32[i] = b32[i] or ((bArray[i * 4 + 2].toInt() and 0xff) shl 16)
            b32[i] = b32[i] or ((bArray[i * 4 + 3].toInt() and 0xff) shl 24)
            i++
        }

        System.arraycopy(b32, 0, x, 0, 16)

        i = 8
        while (i > 0) {
            x[4] = x[4] xor rotate(x[0] + x[12], 7)
            x[8] = x[8] xor rotate(x[4] + x[0], 9)
            x[12] = x[12] xor rotate(x[8] + x[4], 13)
            x[0] = x[0] xor rotate(x[12] + x[8], 18)
            x[9] = x[9] xor rotate(x[5] + x[1], 7)
            x[13] = x[13] xor rotate(x[9] + x[5], 9)
            x[1] = x[1] xor rotate(x[13] + x[9], 13)
            x[5] = x[5] xor rotate(x[1] + x[13], 18)
            x[14] = x[14] xor rotate(x[10] + x[6], 7)
            x[2] = x[2] xor rotate(x[14] + x[10], 9)
            x[6] = x[6] xor rotate(x[2] + x[14], 13)
            x[10] = x[10] xor rotate(x[6] + x[2], 18)
            x[3] = x[3] xor rotate(x[15] + x[11], 7)
            x[7] = x[7] xor rotate(x[3] + x[15], 9)
            x[11] = x[11] xor rotate(x[7] + x[3], 13)
            x[15] = x[15] xor rotate(x[11] + x[7], 18)
            x[1] = x[1] xor rotate(x[0] + x[3], 7)
            x[2] = x[2] xor rotate(x[1] + x[0], 9)
            x[3] = x[3] xor rotate(x[2] + x[1], 13)
            x[0] = x[0] xor rotate(x[3] + x[2], 18)
            x[6] = x[6] xor rotate(x[5] + x[4], 7)
            x[7] = x[7] xor rotate(x[6] + x[5], 9)
            x[4] = x[4] xor rotate(x[7] + x[6], 13)
            x[5] = x[5] xor rotate(x[4] + x[7], 18)
            x[11] = x[11] xor rotate(x[10] + x[9], 7)
            x[8] = x[8] xor rotate(x[11] + x[10], 9)
            x[9] = x[9] xor rotate(x[8] + x[11], 13)
            x[10] = x[10] xor rotate(x[9] + x[8], 18)
            x[12] = x[12] xor rotate(x[15] + x[14], 7)
            x[13] = x[13] xor rotate(x[12] + x[15], 9)
            x[14] = x[14] xor rotate(x[13] + x[12], 13)
            x[15] = x[15] xor rotate(x[14] + x[13], 18)
            i -= 2
        }

        i = 0
        while (i < 16) {
            b32[i] = x[i] + b32[i]
            ++i
        }

        i = 0
        while (i < 16) {
            bArray[i * 4 + 0] = (b32[i] shr 0 and 0xff).toByte()
            bArray[i * 4 + 1] = (b32[i] shr 8 and 0xff).toByte()
            bArray[i * 4 + 2] = (b32[i] shr 16 and 0xff).toByte()
            bArray[i * 4 + 3] = (b32[i] shr 24 and 0xff).toByte()
            i++
        }
    }

    fun blockxor(
        sArray: ByteArray,
        si: Int,
        dArray: ByteArray,
        di: Int,
        len: Int,
    ) {
        for (i in 0..<len) {
            dArray[di + i] = (dArray[di + i].toInt() xor sArray[si + i].toInt()).toByte()
        }
    }

    fun integerify(
        bArray: ByteArray,
        bi: Int,
        r: Int,
    ): Int {
        var bIndex = bi
        var n: Int

        bIndex += (2 * r - 1) * 64

        n = (bArray[bIndex + 0].toInt() and 0xff) shl 0
        n = n or ((bArray[bIndex + 1].toInt() and 0xff) shl 8)
        n = n or ((bArray[bIndex + 2].toInt() and 0xff) shl 16)
        n = n or ((bArray[bIndex + 3].toInt() and 0xff) shl 24)

        return n
    }
}
