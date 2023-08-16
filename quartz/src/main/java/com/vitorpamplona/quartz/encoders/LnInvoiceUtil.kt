package com.vitorpamplona.quartz.encoders

import java.math.BigDecimal
import java.util.Locale
import java.util.regex.Pattern

/** based on litecoinj */
object LnInvoiceUtil {
    private val invoicePattern = Pattern.compile("lnbc((?<amount>\\d+)(?<multiplier>[munp])?)?1[^1\\s]+", Pattern.CASE_INSENSITIVE)

    /** The Bech32 character set for encoding.  */
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    /** The Bech32 character set for decoding.  */
    private val CHARSET_REV = byteArrayOf(
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        15, -1, 10, 17, 21, 20, 26, 30, 7, 5, -1, -1, -1, -1, -1, -1,
        -1, 29, -1, 24, 13, 25, 9, 8, 23, -1, 18, 22, 31, 27, 19, -1,
        1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1,
        -1, 29, -1, 24, 13, 25, 9, 8, 23, -1, 18, 22, 31, 27, 19, -1,
        1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1
    )

    /** Find the polynomial with value coefficients mod the generator as 30-bit.  */
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

    /** Expand a HRP for use in checksum computation.  */
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

    /** Verify a checksum.  */
    private fun verifyChecksum(hrp: String, values: ByteArray): Boolean {
        val hrpExpanded: ByteArray = expandHrp(hrp)
        val combined = ByteArray(hrpExpanded.size + values.size)
        System.arraycopy(hrpExpanded, 0, combined, 0, hrpExpanded.size)
        System.arraycopy(values, 0, combined, hrpExpanded.size, values.size)
        return polymod(combined) == 1
    }

    class AddressFormatException(message: String) : Exception(message)

    fun decodeUnlimitedLength(invoice: String): Boolean {
        var lower = false
        var upper = false
        for (i in 0 until invoice.length) {
            val c = invoice[i]
            if (c.code < 33 || c.code > 126) throw AddressFormatException("Invalid character: $c, pos: $i")
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
            if (CHARSET_REV.get(c.code).toInt() == -1) throw AddressFormatException("Invalid character: " + c + ", pos: " + (i + pos + 1))
            values[i] = CHARSET_REV.get(c.code)
        }
        val hrp = invoice.substring(0, pos).lowercase(Locale.ROOT)
        if (!verifyChecksum(hrp, values)) throw AddressFormatException("Invalid Checksum")
        return true
    }

    /**
     * Parses invoice amount according to
     * https://github.com/lightningnetwork/lightning-rfc/blob/master/11-payment-encoding.md#human-readable-part
     * @return invoice amount in bitcoins, zero if the invoice has no amount
     * @throws RuntimeException if invoice format is incorrect
     */
    private fun getAmount(invoice: String): BigDecimal {
        try {
            decodeUnlimitedLength(invoice) // checksum must match
        } catch (e: AddressFormatException) {
            throw IllegalArgumentException("Cannot decode invoice: $invoice", e)
        }

        val matcher = invoicePattern.matcher(invoice)
        require(matcher.matches()) { "Failed to match HRP pattern" }
        val amountGroup = matcher.group("amount")
        val multiplierGroup = matcher.group("multiplier")
        if (amountGroup == null) {
            return BigDecimal.ZERO
        }
        val amount = BigDecimal(amountGroup)
        if (multiplierGroup == null) {
            return amount
        }
        require(!(multiplierGroup == "p" && amountGroup[amountGroup.length - 1] != '0')) { "sub-millisatoshi amount" }
        return amount.multiply(multiplier(multiplierGroup))
    }

    fun getAmountInSats(invoice: String): BigDecimal {
        return getAmount(invoice).multiply(BigDecimal(100000000))
    }

    private fun multiplier(multiplier: String): BigDecimal {
        return when (multiplier.lowercase()) {
            "m" -> BigDecimal("0.001")
            "u" -> BigDecimal("0.000001")
            "n" -> BigDecimal("0.000000001")
            "p" -> BigDecimal("0.000000000001")
            else -> throw IllegalArgumentException("Invalid multiplier: $multiplier")
        }
    }

    /**
     * Finds LN invoice in the provided input string and returns it.
     * For example for input = "aaa bbb lnbc1xxx ccc" it will return "lnbc1xxx"
     * It will only return the first invoice found in the input.
     *
     * @return the invoice if it was found. null for null input or if no invoice is found
     */
    fun findInvoice(input: String?): String? {
        if (input == null) {
            return null
        }
        val matcher = invoicePattern.matcher(input)
        return if (matcher.find()) {
            matcher.group()
        } else {
            null
        }
    }

    /**
     * If the string contains an LN invoice, returns a Pair of the start and end
     * positions of the invoice in the string. Otherwise, returns (0, 0). This is
     * used to ensure we don't accidentally cut an invoice in the middle when taking
     * only a portion of the available text.
     */
    fun locateInvoice(input: String?): Pair<Int, Int> {
        if (input == null) {
            return Pair(0, 0)
        }
        val matcher = invoicePattern.matcher(input)
        return if (matcher.find()) {
            Pair(matcher.start(), matcher.end())
        } else {
            Pair(0, 0)
        }
    }
}
