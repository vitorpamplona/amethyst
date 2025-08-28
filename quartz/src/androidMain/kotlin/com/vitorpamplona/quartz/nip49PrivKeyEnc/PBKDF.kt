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

import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlin.math.pow

/**
 * An implementation of the Password-Based Key Derivation Function as specified
 * in RFC 2898.
 *
 * @author Will Glozer
 */
object PBKDF {
    /**
     * Implementation of PBKDF2 (RFC2898).
     *
     * @param alg     HMAC algorithm to use.
     * @param password       Password.
     * @param salt       Salt.
     * @param iterationCount       Iteration count.
     * @param dkLen   Intended length, in octets, of the derived key.
     *
     * @return The derived key.
     *
     * @throws GeneralSecurityException
     */
    @Throws(GeneralSecurityException::class)
    fun pbkdf2(
        alg: String,
        password: ByteArray,
        salt: ByteArray,
        iterationCount: Int,
        dkLen: Int,
    ): ByteArray {
        val mac = Mac.getInstance(alg)
        mac.init(SecretKeySpec(password, alg))
        val destKey = ByteArray(dkLen)
        pbkdf2(mac, salt, iterationCount, destKey, dkLen)
        return destKey
    }

    /**
     * Implementation of PBKDF2 (RFC2898).
     *
     * @param mac     Pre-initialized [Mac] instance to use.
     * @param salt       Salt.
     * @param c       Iteration count.
     * @param destKey      Byte array that derived key will be placed in.
     * @param dkLen   Intended length, in octets, of the derived key.
     *
     * @throws GeneralSecurityException
     */
    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun pbkdf2(
        mac: Mac,
        salt: ByteArray,
        c: Int,
        destKey: ByteArray,
        dkLen: Int,
    ) {
        val hLen = mac.getMacLength()

        if (dkLen > (2.0.pow(32.0) - 1) * hLen) {
            throw GeneralSecurityException("Requested key length too long")
        }

        val uArray = ByteArray(hLen)
        val rArray = ByteArray(hLen)
        val block1 = ByteArray(salt.size + 4)

        val l = ceil(dkLen.toDouble() / hLen).toInt()
        val r = dkLen - (l - 1) * hLen

        System.arraycopy(salt, 0, block1, 0, salt.size)

        for (i in 1..l) {
            block1[salt.size + 0] = (i shr 24 and 0xff).toByte()
            block1[salt.size + 1] = (i shr 16 and 0xff).toByte()
            block1[salt.size + 2] = (i shr 8 and 0xff).toByte()
            block1[salt.size + 3] = (i shr 0 and 0xff).toByte()

            mac.update(block1)
            mac.doFinal(uArray, 0)
            System.arraycopy(uArray, 0, rArray, 0, hLen)

            for (j in 1..<c) {
                mac.update(uArray)
                mac.doFinal(uArray, 0)

                for (k in 0..<hLen) {
                    rArray[k] = (rArray[k].toInt() xor uArray[k].toInt()).toByte()
                }
            }

            System.arraycopy(rArray, 0, destKey, (i - 1) * hLen, (if (i == l) r else hLen))
        }
    }
}
