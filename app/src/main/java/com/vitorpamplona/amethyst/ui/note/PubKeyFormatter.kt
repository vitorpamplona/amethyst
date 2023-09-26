package com.vitorpamplona.amethyst.ui.note

import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toHexKey

fun ByteArray.toShortenHex(): String {
    return toHexKey().toShortenHex()
}

fun String.toShortenHex(): String {
    if (length <= 16) return this
    return replaceRange(8, length - 8, ":")
}

fun HexKey.toDisplayHexKey(): String {
    return this.toShortenHex()
}
