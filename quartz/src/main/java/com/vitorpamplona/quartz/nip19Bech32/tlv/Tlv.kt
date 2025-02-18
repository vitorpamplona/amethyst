/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip19Bech32.tlv

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Tlv(
    val data: Map<Byte, List<ByteArray>>,
) {
    fun asInt(type: Byte) = data[type]?.mapNotNull { it.toInt32() }

    fun asHex(type: Byte) = data[type]?.map { it.toHexKey().intern() }

    fun asString(type: Byte) = data[type]?.map { it.toString(Charsets.UTF_8) }

    fun firstAsInt(type: Byte) = data[type]?.firstOrNull()?.toInt32()

    fun firstAsHex(type: Byte) = data[type]?.firstOrNull()?.toHexKey()?.intern()

    fun firstAsString(type: Byte) = data[type]?.firstOrNull()?.toString(Charsets.UTF_8)

    fun asStringList(type: Byte) = data[type]?.map { it.toString(Charsets.UTF_8) }

    companion object {
        fun parse(data: ByteArray): Tlv {
            val result = mutableMapOf<Byte, MutableList<ByteArray>>()
            var rest = data
            while (rest.isNotEmpty()) {
                val t = rest[0]
                val l = rest[1].toUByte().toInt()
                val v = rest.sliceArray(IntRange(2, (2 + l) - 1))
                rest = rest.sliceArray(IntRange(2 + l, rest.size - 1))
                if (v.size < l) continue

                if (!result.containsKey(t)) {
                    result[t] = mutableListOf()
                }
                result[t]?.add(v)
            }
            return Tlv(result)
        }
    }
}

fun ByteArray.toInt32(): Int? {
    if (size != 4) return null
    return ByteBuffer.wrap(this, 0, 4).order(ByteOrder.BIG_ENDIAN).int
}
