package com.vitorpamplona.amethyst.service.nip19

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun toInt32(bytes: ByteArray): Int {
    require(bytes.size == 4) { "length must be 4, got: ${bytes.size}" }
    return ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
}

fun parseTLV(data: ByteArray): Map<Byte, List<ByteArray>> {
    val result = mutableMapOf<Byte, MutableList<ByteArray>>()
    var rest = data
    while (rest.isNotEmpty()) {
        val t = rest[0]
        val l = rest[1]
        val v = rest.sliceArray(IntRange(2, (2 + l) - 1))
        rest = rest.sliceArray(IntRange(2 + l, rest.size - 1))
        if (v.size < l) continue

        if (!result.containsKey(t)) {
            result[t] = mutableListOf()
        }
        result[t]?.add(v)
    }
    return result
}
