package com.vitorpamplona.amethyst.service

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

import kotlin.jvm.JvmStatic

/**
 * Bech32 works with 5 bits values, we use this type to make it explicit: whenever you see Int5 it means 5 bits values,
 * and whenever you see Byte it means 8 bits values.
 */
private typealias Int5 = Byte

/**
 * Bech32 and Bech32m address formats.
 * See https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki and https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki.
 */
object Bech32 {
    const val alphabet: String = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    enum class Encoding(public val constant: Int) {
        Bech32(1),
        Bech32m(0x2bc830a3),
        Beck32WithoutChecksum(0)
    }

    // char -> 5 bits value
    private val map = Array<Int5>(255) { -1 }

    init {
        for (i in 0..alphabet.lastIndex) {
            map[alphabet[i].code] = i.toByte()
        }
    }

    private fun expand(hrp: String): Array<Int5> {
        val result = Array<Int5>(hrp.length + 1 + hrp.length) { 0 }
        for (i in hrp.indices) {
            result[i] = hrp[i].code.shr(5).toByte()
            result[hrp.length + 1 + i] = (hrp[i].code and 31).toByte()
        }
        result[hrp.length] = 0
        return result
    }

    private fun polymod(values: Array<Int5>, values1: Array<Int5>): Int {
        val GEN = arrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        values.forEach { v ->
            val b = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v.toInt()
            for (i in 0..5) {
                if (((b shr i) and 1) != 0) chk = chk xor GEN[i]
            }
        }
        values1.forEach { v ->
            val b = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v.toInt()
            for (i in 0..5) {
                if (((b shr i) and 1) != 0) chk = chk xor GEN[i]
            }
        }
        return chk
    }

    /**
     * @param hrp human readable prefix
     * @param int5s 5-bit data
     * @param encoding encoding to use (bech32 or bech32m)
     * @return hrp + data encoded as a Bech32 string
     */
    @JvmStatic
    public fun encode(hrp: String, int5s: Array<Int5>, encoding: Encoding): String {
        require(hrp.lowercase() == hrp || hrp.uppercase() == hrp) { "mixed case strings are not valid bech32 prefixes" }
        val data = int5s.toByteArray().toTypedArray()
        val checksum = when (encoding) {
            Encoding.Beck32WithoutChecksum -> arrayOf()
            else -> checksum(hrp, data, encoding)
        }
        return hrp + "1" + (data + checksum).map { i -> alphabet[i.toInt()] }.toCharArray().concatToString()
    }

    /**
     * @param hrp human readable prefix
     * @param data data to encode
     * @param encoding encoding to use (bech32 or bech32m)
     * @return hrp + data encoded as a Bech32 string
     */
    @JvmStatic
    public fun encodeBytes(hrp: String, data: ByteArray, encoding: Encoding): String = encode(hrp, eight2five(data), encoding)

    /**
     * decodes a bech32 string
     * @param bech32 bech32 string
     * @param noChecksum if true, the bech32 string doesn't have a checksum
     * @return a (hrp, data, encoding) tuple
     */
    @JvmStatic
    public fun decode(bech32: String, noChecksum: Boolean = false): Triple<String, Array<Int5>, Encoding> {
        require(bech32.lowercase() == bech32 || bech32.uppercase() == bech32) { "mixed case strings are not valid bech32" }
        bech32.forEach { require(it.code in 33..126) { "invalid character " } }
        val input = bech32.lowercase()
        val pos = input.lastIndexOf('1')
        val hrp = input.take(pos)
        require(hrp.length in 1..83) { "hrp must contain 1 to 83 characters" }
        val data = Array<Int5>(input.length - pos - 1) { 0 }
        for (i in 0..data.lastIndex) data[i] = map[input[pos + 1 + i].code]
        return if (noChecksum) {
            Triple(hrp, data, Encoding.Beck32WithoutChecksum)
        } else {
            val encoding = when (polymod(expand(hrp), data)) {
                Encoding.Bech32.constant -> Encoding.Bech32
                Encoding.Bech32m.constant -> Encoding.Bech32m
                else -> throw IllegalArgumentException("invalid checksum for $bech32")
            }
            Triple(hrp, data.dropLast(6).toTypedArray(), encoding)
        }
    }

    /**
     * decodes a bech32 string
     * @param bech32 bech32 string
     * @param noChecksum if true, the bech32 string doesn't have a checksum
     * @return a (hrp, data, encoding) tuple
     */
    @JvmStatic
    public fun decodeBytes(bech32: String, noChecksum: Boolean = false): Triple<String, ByteArray, Encoding> {
        val (hrp, int5s, encoding) = decode(bech32, noChecksum)
        return Triple(hrp, five2eight(int5s, 0), encoding)
    }

    /**
     * @param hrp Human Readable Part
     * @param data data (a sequence of 5 bits integers)
     * @param encoding encoding to use (bech32 or bech32m)
     * @return a checksum computed over hrp and data
     */
    private fun checksum(hrp: String, data: Array<Int5>, encoding: Encoding): Array<Int5> {
        val values = expand(hrp) + data
        val poly = polymod(values, arrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())) xor encoding.constant
        return Array(6) { i -> (poly.shr(5 * (5 - i)) and 31).toByte() }
    }

    /**
     * @param input a sequence of 8 bits integers
     * @return a sequence of 5 bits integers
     */
    @JvmStatic
    public fun eight2five(input: ByteArray): Array<Int5> {
        var buffer = 0L
        val output = ArrayList<Int5>()
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
        return output.toTypedArray()
    }

    /**
     * @param input a sequence of 5 bits integers
     * @return a sequence of 8 bits integers
     */
    @JvmStatic
    public fun five2eight(input: Array<Int5>, offset: Int): ByteArray {
        var buffer = 0L
        val output = ArrayList<Byte>()
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

fun ByteArray.toNsec() = Bech32.encodeBytes(hrp = "nsec", this, Bech32.Encoding.Bech32)
fun ByteArray.toNpub() = Bech32.encodeBytes(hrp = "npub", this, Bech32.Encoding.Bech32)

fun String.bechToBytes(hrp: String? = null): ByteArray {
    val decodedForm = Bech32.decodeBytes(this)
    hrp?.also {
        if (it != decodedForm.first) {
            throw IllegalArgumentException("Expected $it but obtained ${decodedForm.first}")
        }
    }
    return decodedForm.second
}
