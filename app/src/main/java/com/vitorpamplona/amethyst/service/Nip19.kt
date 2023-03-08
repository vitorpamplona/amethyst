package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.nip19.TlvTypes
import nostr.postr.bechToBytes
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Nip19 {

    enum class Type {
        USER, NOTE, RELAY, ADDRESS
    }

    data class Return(val type: Type, val hex: String, val relay: String? = null)

    fun uriToRoute(uri: String?): Return? {
        try {
            val key = uri?.removePrefix("nostr:") ?: return null

            val bytes = key.bechToBytes()
            if (key.startsWith("npub")) {
                return npub(bytes)
            } else if (key.startsWith("note")) {
                return note(bytes)
            } else if (key.startsWith("nprofile")) {
                return nprofile(bytes)
            } else if (key.startsWith("nevent")) {
                return nevent(bytes)
            } else if (key.startsWith("nrelay")) {
                return nrelay(bytes)
            } else if (key.startsWith("naddr")) {
                return naddr(bytes)
            }
        } catch (e: Throwable) {
            println("Issue trying to Decode NIP19 $uri: ${e.message}")
        }

        return null
    }

    private fun npub(bytes: ByteArray): Return {
        return Return(Type.USER, bytes.toHexKey())
    }

    private fun note(bytes: ByteArray): Return {
        return Return(Type.NOTE, bytes.toHexKey())
    }

    private fun nprofile(bytes: ByteArray): Return? {
        val tlv = parseTLV(bytes)

        val hex = tlv.get(TlvTypes.SPECIAL.id)
            ?.get(0)
            ?.toHexKey() ?: return null

        val relay = tlv.get(TlvTypes.RELAY.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8)

        return Return(Type.USER, hex, relay)
    }

    private fun nevent(bytes: ByteArray): Return? {
        val tlv = parseTLV(bytes)

        val hex = tlv.get(TlvTypes.SPECIAL.id)
            ?.get(0)
            ?.toHexKey() ?: return null

        val relay = tlv.get(TlvTypes.RELAY.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8)

        return Return(Type.USER, hex, relay)
    }

    private fun nrelay(bytes: ByteArray): Return? {
        val relayUrl = parseTLV(bytes)
            .get(TlvTypes.SPECIAL.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8) ?: return null

        return Return(Type.RELAY, relayUrl)
    }

    private fun naddr(bytes: ByteArray): Return? {
        val tlv = parseTLV(bytes)

        val d = tlv.get(TlvTypes.SPECIAL.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8) ?: return null

        val relay = tlv.get(TlvTypes.RELAY.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8)

        val author = tlv.get(TlvTypes.AUTHOR.id)
            ?.get(0)
            ?.toHexKey()

        val kind = tlv.get(TlvTypes.KIND.id)
            ?.get(0)
            ?.let { toInt32(it) }

        return Return(Type.ADDRESS, "$kind:$author:$d", relay)
    }
}

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
