package com.vitorpamplona.amethyst.commons.util

/** Converts a Unicode code point to a CharArray (handling surrogates). */
fun codePointToChars(codePoint: Int): CharArray =
    if (codePoint < 0x10000) {
        charArrayOf(codePoint.toChar())
    } else {
        val cp = codePoint - 0x10000
        charArrayOf(
            ((cp ushr 10) + 0xD800).toChar(),
            ((cp and 0x3FF) + 0xDC00).toChar(),
        )
    }

/** Returns the number of chars used by the code point (1 or 2). */
fun charCountForCodePoint(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

/** Returns the Unicode code point at the given char index. */
fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
        }
    }
    return high.code
}
