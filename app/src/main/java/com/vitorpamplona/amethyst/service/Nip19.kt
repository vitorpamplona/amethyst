package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.bechToBytes

class Nip19 {

  enum class Type {
    USER, NOTE
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
          val hex = tlv.get(0)?.get(0)?.toHexKey()
          if (hex != null)
            return Return(Type.USER, hex)
        }
        if (key.startsWith("nevent")) {
          val tlv = parseTLV(bytes)
          val hex = tlv.get(0)?.get(0)?.toHexKey()
          if (hex != null)
            return Return(Type.USER, hex)
        }
      }
    } catch (e: Throwable) {
      println("Issue trying to Decode NIP19 ${uri}: ${e.message}")
      //e.printStackTrace()
    }

    return null
  }

  fun parseTLV(data: ByteArray): Map<Byte, List<ByteArray>> {
    var result = mutableMapOf<Byte, MutableList<ByteArray>>()
    var rest = data
    while (rest.isNotEmpty()) {
      val t = rest[0]
      val l = rest[1]
      val v = rest.sliceArray(IntRange(2, (2 + l) - 1))
      rest = rest.sliceArray(IntRange(2 + l, rest.size-1))
      if (v.size < l) continue

      if (!result.containsKey(t)) {
        result.put(t, mutableListOf())
      }
      result.get(t)?.add(v)
    }
    return result
  }
}