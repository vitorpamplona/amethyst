package com.vitorpamplona.quartz.encoders

/** Makes the distinction between String and Hex **/
typealias HexKey = String

fun ByteArray.toHexKey(): HexKey {
    return Hex.encode(this)
}

fun HexKey.hexToByteArray(): ByteArray {
    return Hex.decode(this)
}

object HexValidator {
    private fun isHex2(c: Char): Boolean {
        return when (c) {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F', ' ' -> true
            else -> false
        }
    }

    fun isHex(hex: String?): Boolean {
        if (hex == null) return false
        var isHex = true
        for (c in hex.toCharArray()) {
            if (!isHex2(c)) {
                isHex = false
                break
            }
        }
        return isHex
    }
}

object Hex {
    private val hexCode = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    // Faster if no calculations are needed.
    private fun hexToBin(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        in 'A'..'F' -> ch - 'A' + 10
        else -> throw IllegalArgumentException("illegal hex character: $ch")
    }

    @JvmStatic
    fun decode(hex: String): ByteArray {
        // faster version of hex decoder
        require(hex.length % 2 == 0)
        val outSize = hex.length / 2
        val out = ByteArray(outSize)

        for (i in 0 until outSize) {
            out[i] = (hexToBin(hex[2 * i]) * 16 + hexToBin(hex[2 * i + 1])).toByte()
        }

        return out
    }

    @JvmStatic
    fun encode(input: ByteArray): String {
        val len = input.size
        val out = CharArray(len * 2)
        for (i in 0 until len) {
            out[i*2] = hexCode[(input[i].toInt() shr 4) and 0xF]
            out[i*2+1] = hexCode[input[i].toInt() and 0xF]
        }
        return String(out)
    }
}