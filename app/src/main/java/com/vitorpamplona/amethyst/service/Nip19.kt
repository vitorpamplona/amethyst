package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.model.ATag
import java.nio.ByteBuffer
import java.nio.ByteOrder
import nostr.postr.Bech32
import nostr.postr.bechToBytes
import nostr.postr.toByteArray

class Nip19 {

  enum class Type {
    USER, NOTE, RELAY, ADDRESS
  }
  data class Return(val type: Type, val hex: String)

  fun uriToRoute(uri: String?): Return? {
    try {
      val key = uri?.removePrefix("nostr:")

      if (key != null) {
        val bytes = key.bechToBytes()
        if (key.startsWith("npub")) {
          return Return(Type.USER, bytes.toHexKey())
        }
        if (key.startsWith("note")) {
          return Return(Type.NOTE, bytes.toHexKey())
        }
        if (key.startsWith("nprofile")) {
          val tlv = parseTLV(bytes)
          val hex = tlv.get(NIP19TLVTypes.SPECIAL.id)?.get(0)?.toHexKey()
          if (hex != null)
            return Return(Type.USER, hex)
        }
        if (key.startsWith("nevent")) {
          val tlv = parseTLV(bytes)
          val hex = tlv.get(NIP19TLVTypes.SPECIAL.id)?.get(0)?.toHexKey()
          if (hex != null)
            return Return(Type.USER, hex)
        }
        if (key.startsWith("nrelay")) {
          val tlv = parseTLV(bytes)
          val relayUrl = tlv.get(NIP19TLVTypes.SPECIAL.id)?.get(0)?.toString(Charsets.UTF_8)
          if (relayUrl != null)
            return Return(Type.RELAY, relayUrl)
        }
        if (key.startsWith("naddr")) {
          val tlv = parseTLV(bytes)
          val d = tlv.get(NIP19TLVTypes.SPECIAL.id)?.get(0)?.toString(Charsets.UTF_8)
          val relay = tlv.get(NIP19TLVTypes.RELAY.id)?.get(0)?.toString(Charsets.UTF_8)
          val author = tlv.get(NIP19TLVTypes.AUTHOR.id)?.get(0)?.toHexKey()
          val kind = tlv.get(NIP19TLVTypes.KIND.id)?.get(0)?.let { toInt32(it) }
          if (d != null)
            return Return(Type.ADDRESS, "$kind:$author:$d")
        }
      }
    } catch (e: Throwable) {
      println("Issue trying to Decode NIP19 ${uri}: ${e.message}")
      //e.printStackTrace()
    }

    return null
  }
}

enum class NIP19TLVTypes(val id: Byte) { //classes should start with an uppercase letter in kotlin
  SPECIAL(0),
  RELAY(1),
  AUTHOR(2),
  KIND(3);
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
    rest = rest.sliceArray(IntRange(2 + l, rest.size-1))
    if (v.size < l) continue

    if (!result.containsKey(t)) {
      result[t] = mutableListOf()
    }
    result[t]?.add(v)
  }
  return result
}
