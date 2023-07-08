package com.vitorpamplona.amethyst.service

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
