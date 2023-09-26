package com.vitorpamplona.quartz.encoders

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TlvBuilder() {
    val outputStream = ByteArrayOutputStream()

    private fun add(type: Byte, byteArray: ByteArray) {
        outputStream.write(byteArrayOf(type, byteArray.size.toByte()))
        outputStream.write(byteArray)
    }

    fun addString(type: Byte, string: String) = add(type, string.toByteArray(Charsets.UTF_8))
    fun addHex(type: Byte, key: HexKey) = add(type, key.hexToByteArray())
    fun addInt(type: Byte, data: Int) = add(type, data.to32BitByteArray())

    fun addStringIfNotNull(type: Byte, data: String?) = data?.let { addString(type, it) }
    fun addHexIfNotNull(type: Byte, data: HexKey?) = data?.let { addHex(type, it) }
    fun addIntIfNotNull(type: Byte, data: Int?) = data?.let { addInt(type, it) }

    fun build(): ByteArray {
        return outputStream.toByteArray()
    }
}

fun Int.to32BitByteArray(): ByteArray {
    val bytes = ByteArray(4)
    (0..3).forEach {
        bytes[3 - it] = ((this ushr (8 * it)) and 0xFFFF).toByte()
    }
    return bytes
}

fun ByteArray.toInt32(): Int? {
    if (size != 4) return null
    return ByteBuffer.wrap(this, 0, 4).order(ByteOrder.BIG_ENDIAN).int
}

class Tlv(val data: Map<Byte, List<ByteArray>>) {
    fun asInt(type: Byte) = data[type]?.mapNotNull { it.toInt32() }
    fun asHex(type: Byte) = data[type]?.map { it.toHexKey().intern() }
    fun asString(type: Byte) = data[type]?.map { it.toString(Charsets.UTF_8) }

    fun firstAsInt(type: Byte) = data[type]?.firstOrNull()?.toInt32()
    fun firstAsHex(type: Byte) = data[type]?.firstOrNull()?.toHexKey()?.intern()
    fun firstAsString(type: Byte) = data[type]?.firstOrNull()?.toString(Charsets.UTF_8)

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
