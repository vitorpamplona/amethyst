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
package com.vitorpamplona.quartz.lightning

import com.vitorpamplona.quartz.utils.BigDecimal

/** based on litecoinj */
object LnInvoiceUtil {
    private val invoicePattern =
        Regex("lnbc((\\d+)([munp])?)?1[^1\\s]+", RegexOption.IGNORE_CASE)

    /** The Bech32 character set for encoding. */
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    /** The Bech32 character set for decoding. */
    private val CHARSET_REV =
        byteArrayOf(
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            15,
            -1,
            10,
            17,
            21,
            20,
            26,
            30,
            7,
            5,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            29,
            -1,
            24,
            13,
            25,
            9,
            8,
            23,
            -1,
            18,
            22,
            31,
            27,
            19,
            -1,
            1,
            0,
            3,
            16,
            11,
            28,
            12,
            14,
            6,
            4,
            2,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            29,
            -1,
            24,
            13,
            25,
            9,
            8,
            23,
            -1,
            18,
            22,
            31,
            27,
            19,
            -1,
            1,
            0,
            3,
            16,
            11,
            28,
            12,
            14,
            6,
            4,
            2,
            -1,
            -1,
            -1,
            -1,
            -1,
        )

    /** Find the polynomial with value coefficients mod the generator as 30-bit. */
    private fun polymod(values: ByteArray): Int {
        var c = 1
        for (v_i in values) {
            val c0 = c ushr 25 and 0xff
            c = c and 0x1ffffff shl 5 xor (v_i.toInt() and 0xff)
            if (c0 and 1 != 0) c = c xor 0x3b6a57b2
            if (c0 and 2 != 0) c = c xor 0x26508e6d
            if (c0 and 4 != 0) c = c xor 0x1ea119fa
            if (c0 and 8 != 0) c = c xor 0x3d4233dd
            if (c0 and 16 != 0) c = c xor 0x2a1462b3
        }
        return c
    }

    /** Expand a HRP for use in checksum computation. */
    private fun expandHrp(hrp: String): ByteArray {
        val hrpLength = hrp.length
        val ret = ByteArray(hrpLength * 2 + 1)
        for (i in 0 until hrpLength) {
            val c = hrp[i].code and 0x7f // Limit to standard 7-bit ASCII
            ret[i] = (c ushr 5 and 0x07).toByte()
            ret[i + hrpLength + 1] = (c and 0x1f).toByte()
        }
        ret[hrpLength] = 0
        return ret
    }

    /** Verify a checksum. */
    private fun verifyChecksum(
        hrp: String,
        values: ByteArray,
    ): Boolean {
        val hrpExpanded: ByteArray = expandHrp(hrp)
        val combined = ByteArray(hrpExpanded.size + values.size)
        // System.arraycopy(hrpExpanded, 0, combined, 0, hrpExpanded.size)
        hrpExpanded.copyInto(combined, 0, 0, hrpExpanded.size)
        // System.arraycopy(values, 0, combined, hrpExpanded.size, values.size)
        values.copyInto(combined, hrpExpanded.size, 0, values.size)
        return polymod(combined) == 1
    }

    class AddressFormatException(
        message: String,
    ) : Exception(message)

    fun decodeUnlimitedLength(invoice: String): Boolean {
        var lower = false
        var upper = false
        for (i in 0 until invoice.length) {
            val c = invoice[i]
            if (c.code < 33 || c.code > 126) {
                throw AddressFormatException("Invalid character: $c, pos: $i")
            }
            if (c in 'a'..'z') {
                if (upper) throw AddressFormatException("Invalid character: $c, pos: $i")
                lower = true
            }
            if (c in 'A'..'Z') {
                if (lower) throw AddressFormatException("Invalid character: $c, pos: $i")
                upper = true
            }
        }
        val pos = invoice.lastIndexOf('1')
        if (pos < 1) throw AddressFormatException("Missing human-readable part")
        val dataPartLength = invoice.length - 1 - pos
        if (dataPartLength < 6) throw AddressFormatException("Data part too short: $dataPartLength")
        val values = ByteArray(dataPartLength)
        for (i in 0 until dataPartLength) {
            val c = invoice[i + pos + 1]
            if (CHARSET_REV.get(c.code).toInt() == -1) {
                throw AddressFormatException("Invalid character: " + c + ", pos: " + (i + pos + 1))
            }
            values[i] = CHARSET_REV.get(c.code)
        }
        val hrp = invoice.substring(0, pos).lowercase()
        if (!verifyChecksum(hrp, values)) throw AddressFormatException("Invalid Checksum")
        return true
    }

    /**
     * Parses invoice amount according to
     * https://github.com/lightningnetwork/lightning-rfc/blob/master/11-payment-encoding.md#human-readable-part
     *
     * @return invoice amount in bitcoins, zero if the invoice has no amount
     * @throws RuntimeException if invoice format is incorrect
     */
    private fun getAmount(invoice: String): BigDecimal {
        try {
            decodeUnlimitedLength(invoice) // checksum must match
        } catch (e: AddressFormatException) {
            throw IllegalArgumentException("Cannot decode invoice: $invoice", e)
        }

        val matcher = invoicePattern.find(invoice)
        require(matcher != null) { "Failed to match HRP pattern" }
        val amountGroup = matcher.groups[2]?.value
        val multiplierGroup = matcher.groups[3]?.value
        if (amountGroup == null) {
            return Zero
        }
        val amount = BigDecimal(amountGroup)
        if (multiplierGroup == null) {
            return amount
        }
        require(!(multiplierGroup == "p" && amountGroup[amountGroup.length - 1] != '0')) {
            "sub-millisatoshi amount"
        }
        return amount.multiply(multiplier(multiplierGroup))
    }

    val Zero = BigDecimal("0")
    val OneHundredK = BigDecimal(100_000_000)
    val OneMili = BigDecimal("0.001")
    val OneMicro = BigDecimal("0.000001")
    val OneNano = BigDecimal("0.000000001")
    val OnePico = BigDecimal("0.000000000001")

    fun getAmountInSats(invoice: String): BigDecimal = getAmount(invoice).multiply(OneHundredK)

    /** Tagged-field types from BOLT-11 (the bech32 value of the tag character). */
    private const val TAG_DESCRIPTION = 13 // 'd'
    private const val TAG_EXPIRY = 6 // 'x'

    /** Default expiry per BOLT-11 when the `x` field is absent. */
    private const val DEFAULT_EXPIRY_SECONDS = 3600L

    /**
     * The human-relevant tagged fields of a BOLT-11 invoice: when it was created,
     * the free-text description (absent when the payee used a description hash),
     * and how long it stays valid.
     */
    class Bolt11Details(
        val timestamp: Long,
        val description: String?,
        val expirySeconds: Long?,
    ) {
        fun expiresAt(): Long = timestamp + (expirySeconds ?: DEFAULT_EXPIRY_SECONDS)
    }

    /**
     * Parses the data part of a BOLT-11 invoice and returns its timestamp, description
     * and expiry. Returns null if the string is not a checksum-valid invoice.
     */
    fun parseDetails(invoice: String): Bolt11Details? {
        try {
            decodeUnlimitedLength(invoice) // checksum must match
        } catch (e: AddressFormatException) {
            return null
        }

        val pos = invoice.lastIndexOf('1')
        val dataPart = invoice.substring(pos + 1).lowercase()
        // 7 chars of timestamp + 104 chars of signature + 6 chars of checksum is the minimum.
        if (dataPart.length < 7 + 104 + 6) return null

        val values = ByteArray(dataPart.length)
        for (i in dataPart.indices) {
            val code = dataPart[i].code
            if (code >= CHARSET_REV.size || CHARSET_REV[code].toInt() == -1) return null
            values[i] = CHARSET_REV[code]
        }

        var timestamp = 0L
        for (i in 0 until 7) {
            timestamp = (timestamp shl 5) or values[i].toLong()
        }

        // Tagged fields run from after the timestamp to before the 520-bit signature
        // + recovery id (104 values) and the checksum (6 values).
        val fieldsEnd = values.size - 104 - 6
        var description: String? = null
        var expiry: Long? = null

        var i = 7
        while (i + 3 <= fieldsEnd) {
            val type = values[i].toInt()
            val dataLength = values[i + 1].toInt() * 32 + values[i + 2].toInt()
            val dataStart = i + 3
            val dataEnd = dataStart + dataLength
            if (dataEnd > fieldsEnd) break

            when (type) {
                TAG_DESCRIPTION -> description = fiveBitsToBytes(values, dataStart, dataEnd).decodeToString()
                TAG_EXPIRY -> {
                    var seconds = 0L
                    for (j in dataStart until dataEnd) {
                        seconds = (seconds shl 5) or values[j].toLong()
                    }
                    expiry = seconds
                }
            }

            i = dataEnd
        }

        return Bolt11Details(timestamp, description?.takeIf { it.isNotBlank() }, expiry)
    }

    /**
     * Returns the free-text description (`d` tagged field) of a BOLT-11 invoice, or null
     * when the invoice is invalid, has no description, or carries only a description hash.
     */
    fun getDescription(invoice: String): String? = parseDetails(invoice)?.description

    /** Regroups 5-bit bech32 values into 8-bit bytes, discarding incomplete trailing bits. */
    private fun fiveBitsToBytes(
        values: ByteArray,
        from: Int,
        to: Int,
    ): ByteArray {
        val result = ByteArray((to - from) * 5 / 8)
        var acc = 0
        var bits = 0
        var index = 0
        for (i in from until to) {
            acc = (acc shl 5) or values[i].toInt()
            bits += 5
            if (bits >= 8) {
                bits -= 8
                result[index++] = ((acc ushr bits) and 0xFF).toByte()
            }
        }
        return result
    }

    private fun multiplier(multiplier: String): BigDecimal =
        when (multiplier.lowercase()) {
            "m" -> OneMili
            "u" -> OneMicro
            "n" -> OneNano
            "p" -> OnePico
            else -> throw IllegalArgumentException("Invalid multiplier: $multiplier")
        }

    /**
     * Finds LN invoice in the provided input string and returns it. For example for input = "aaa bbb
     * lnbc1xxx ccc" it will return "lnbc1xxx" It will only return the first invoice found in the
     * input.
     *
     * @return the invoice if it was found. null for null input or if no invoice is found
     */
    fun findInvoice(input: String?): String? {
        if (input == null) {
            return null
        }
        return invoicePattern.find(input)?.value
    }

    /**
     * If the string contains an LN invoice, returns a Pair of the start and end positions of the
     * invoice in the string. Otherwise, returns (0, 0). This is used to ensure we don't accidentally
     * cut an invoice in the middle when taking only a portion of the available text.
     */
    fun locateInvoice(input: String?): Pair<Int, Int> {
        if (input == null) {
            return Pair(0, 0)
        }
        val matcher = invoicePattern.find(input)
        return if (matcher != null) {
            Pair(matcher.range.start, matcher.range.endInclusive + 1)
        } else {
            Pair(0, 0)
        }
    }
}
