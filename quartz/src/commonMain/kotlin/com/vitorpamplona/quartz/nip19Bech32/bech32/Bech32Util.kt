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
package com.vitorpamplona.quartz.nip19Bech32.bech32

/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Bech32 works with 5 bits values, we use this type to make it explicit: whenever you see Int5 it
 * means 5 bits values, and whenever you see Byte it means 8 bits values.
 */
private typealias Int5 = Byte

/**
 * Bech32 and Bech32m address formats. See
 * https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki and
 * https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki.
 */
object Bech32 {
    const val ALPHABET: String = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    const val ALPHABET_UPPERCASE: String = "QPZRY9X8GF2TVDW0S3JN54KHCE6MUA7L"

    private const val GEN0 = 0x3b6a57b2
    private const val GEN1 = 0x26508e6d
    private const val GEN2 = 0x1ea119fa
    private const val GEN3 = 0x3d4233dd
    private const val GEN4 = 0x2a1462b3

    enum class Encoding(
        val constant: Int,
    ) {
        Bech32(1),
        Bech32m(0x2bc830a3),
        Beck32WithoutChecksum(0),
    }

    // char -> 5 bits value
    private val map = Array<Int5>(255) { -1 }

    init {
        for (i in 0..ALPHABET.lastIndex) {
            map[ALPHABET[i].code] = i.toByte()
        }
        for (i in 0..ALPHABET_UPPERCASE.lastIndex) {
            map[ALPHABET_UPPERCASE[i].code] = i.toByte()
        }
    }

    fun expand(hrp: String): Array<Int5> {
        val half = hrp.length + 1
        val size = half + hrp.length
        val firstPart = hrp.indices
        val secondPart = half until size
        return Array<Int5>(size) {
            when (it) {
                in firstPart -> hrp[it].code.shr(5).toByte()
                in secondPart -> (hrp[it - half].code and 31).toByte()
                else -> 0
            }
        }
    }

    fun polymod(
        values: Array<Int5>,
        values1: Array<Int5>,
    ): Int {
        var chk = 1
        values.forEach { v ->
            val b = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v.toInt()
            if (((b shr 0) and 1) != 0) chk = chk xor GEN0
            if (((b shr 1) and 1) != 0) chk = chk xor GEN1
            if (((b shr 2) and 1) != 0) chk = chk xor GEN2
            if (((b shr 3) and 1) != 0) chk = chk xor GEN3
            if (((b shr 4) and 1) != 0) chk = chk xor GEN4
        }
        values1.forEach { v ->
            val b = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v.toInt()
            if (((b shr 0) and 1) != 0) chk = chk xor GEN0
            if (((b shr 1) and 1) != 0) chk = chk xor GEN1
            if (((b shr 2) and 1) != 0) chk = chk xor GEN2
            if (((b shr 3) and 1) != 0) chk = chk xor GEN3
            if (((b shr 4) and 1) != 0) chk = chk xor GEN4
        }
        return chk
    }

    /**
     * @param hrp human readable prefix
     * @param int5s 5-bit data
     * @param encoding encoding to use (bech32 or bech32m)
     * @return hrp + data encoded as a Bech32 string
     */

    public fun encode(
        hrp: String,
        int5s: ArrayList<Int5>,
        encoding: Encoding,
    ): String {
        require(hrp.lowercase() == hrp || hrp.uppercase() == hrp) {
            "mixed case strings are not valid bech32 prefixes"
        }
        val dataWithChecksum =
            when (encoding) {
                Encoding.Beck32WithoutChecksum -> int5s
                else -> addChecksum(hrp, int5s, encoding)
            }

        val charArray = CharArray(dataWithChecksum.size) { ALPHABET[dataWithChecksum[it].toInt()] }.concatToString()

        return hrp + "1" + charArray
    }

    /**
     * @param hrp human readable prefix
     * @param data data to encode
     * @param encoding encoding to use (bech32 or bech32m)
     * @return hrp + data encoded as a Bech32 string
     */

    public fun encodeBytes(
        hrp: String,
        data: ByteArray,
        encoding: Encoding,
    ): String = encode(hrp, eight2five(data), encoding)

    /**
     * decodes a bech32 string
     *
     * @param bech32 bech32 string
     * @param noChecksum if true, the bech32 string doesn't have a checksum
     * @return a (hrp, data, encoding) tuple
     */

    public fun decode(
        bech32: String,
        noChecksum: Boolean = false,
    ): Triple<String, Array<Int5>, Encoding> {
        val filteredBech32 = bech32.filter { it.code in 33..126 }

        var pos = 0
        filteredBech32.forEachIndexed { index, char ->
            require(char.code in 33..126) { "invalid character $char" }
            if (char == '1') {
                pos = index
            }
        }

        val hrp = filteredBech32.take(pos).lowercase() // strings must be lower case
        require(hrp.length in 1..83) { "hrp must contain 1 to 83 characters" }

        val data = Array(filteredBech32.length - pos - 1) { map[filteredBech32[pos + 1 + it].code] }

        return if (noChecksum) {
            Triple(hrp, data, Encoding.Beck32WithoutChecksum)
        } else {
            val encoding =
                when (polymod(expand(hrp), data)) {
                    Encoding.Bech32.constant -> Encoding.Bech32
                    Encoding.Bech32m.constant -> Encoding.Bech32m
                    else -> throw IllegalArgumentException("invalid checksum for $bech32")
                }
            Triple(hrp, data.copyOfRange(0, data.size - 6), encoding)
        }
    }

    /**
     * decodes a bech32 string
     *
     * @param bech32 bech32 string
     * @param noChecksum if true, the bech32 string doesn't have a checksum
     * @return a (hrp, data, encoding) tuple
     */

    public fun decodeBytes(
        bech32: String,
        noChecksum: Boolean = false,
    ): Triple<String, ByteArray, Encoding> {
        val (hrp, int5s, encoding) = decode(bech32, noChecksum)
        return Triple(hrp, five2eight(int5s, 0), encoding)
    }

    val ZEROS = arrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())

    /**
     * @param hrp Human Readable Part
     * @param data data (a sequence of 5 bits integers)
     * @param encoding encoding to use (bech32 or bech32m)
     * @return a checksum computed over hrp and data
     */
    private fun addChecksum(
        hrp: String,
        data: ArrayList<Int5>,
        encoding: Encoding,
    ): ArrayList<Int5> {
        val values = expand(hrp) + data
        val poly = polymod(values, ZEROS) xor encoding.constant

        for (i in 0 until 6) {
            data.add((poly.shr(5 * (5 - i)) and 31).toByte())
        }

        return data
    }

    /**
     * @param input a sequence of 8 bits integers
     * @return a sequence of 5 bits integers
     */

    public fun eight2five(input: ByteArray): ArrayList<Int5> {
        var buffer = 0L
        // larger array on purpose. Checksum is added later.
        val output = ArrayList<Int5>(input.size * 2)
        var count = 0
        input.forEach { b ->
            buffer = (buffer shl 8) or (b.toLong() and 0xff)
            count += 8
            while (count >= 5) {
                output.add(((buffer shr (count - 5)) and 31).toByte())
                count -= 5
            }
        }
        if (count > 0) output.add(((buffer shl (5 - count)) and 31).toByte())
        return output
    }

    /**
     * @param input a sequence of 5 bits integers
     * @return a sequence of 8 bits integers
     */

    public fun five2eight(
        input: Array<Int5>,
        offset: Int,
    ): ByteArray {
        var buffer = 0L
        val output = ArrayList<Byte>(input.size)
        var count = 0
        for (i in offset..input.lastIndex) {
            val b = input[i]
            buffer = (buffer shl 5) or (b.toLong() and 31)
            count += 5
            while (count >= 8) {
                output.add(((buffer shr (count - 8)) and 0xff).toByte())
                count -= 8
            }
        }
        require(count <= 4) { "Zero-padding of more than 4 bits" }
        require((buffer and ((1L shl count) - 1L)) == 0L) { "Non-zero padding in 8-to-5 conversion" }
        return output.toByteArray()
    }
}

fun String.bechToBytes(hrp: String? = null): ByteArray {
    val decodedForm = Bech32.decodeBytes(this)
    hrp?.also {
        if (it != decodedForm.first) {
            throw IllegalArgumentException("Expected $it but obtained ${decodedForm.first}")
        }
    }
    return decodedForm.second
}
