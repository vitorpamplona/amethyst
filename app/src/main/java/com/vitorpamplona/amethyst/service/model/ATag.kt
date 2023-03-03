package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.NIP19TLVTypes
import com.vitorpamplona.amethyst.service.parseTLV
import com.vitorpamplona.amethyst.service.toInt32
import fr.acinq.secp256k1.Hex
import nostr.postr.Bech32
import nostr.postr.bechToBytes
import nostr.postr.toByteArray

data class ATag(val kind: Int, val pubKeyHex: String, val dTag: String) {
    fun toTag() = "$kind:$pubKeyHex:$dTag"

    fun toNAddr(): String {
        val kind = kind.toByteArray()
        val addr = pubKeyHex.toByteArray()
        val dTag = dTag.toByteArray(Charsets.UTF_8)

        val fullArray =
            byteArrayOf(NIP19TLVTypes.SPECIAL.id, dTag.size.toByte()) + dTag +
              byteArrayOf(NIP19TLVTypes.AUTHOR.id, addr.size.toByte()) + addr +
              byteArrayOf(NIP19TLVTypes.KIND.id, kind.size.toByte()) + kind

        return Bech32.encodeBytes(hrp = "naddr", fullArray, Bech32.Encoding.Bech32)
    }

    companion object {
        fun parse(address: String): ATag? {
            return if (address.startsWith("naddr") || address.startsWith("nostr:naddr"))
                parseNAddr(address)
            else
                parseAtag(address)
        }

        fun parseAtag(atag: String): ATag? {
            return try {
                val parts = atag.split(":")
              Hex.decode(parts[1])
              ATag(parts[0].toInt(), parts[1], parts[2])
            } catch (t: Throwable) {
              Log.w("Address", "Error parsing A Tag: ${atag}: ${t.message}")
                null
            }
        }

        fun parseNAddr(naddr: String): ATag? {
            try {
                val key = naddr.removePrefix("nostr:")

                if (key.startsWith("naddr")) {
                    val tlv = parseTLV(key.bechToBytes())
                    val d = tlv.get(NIP19TLVTypes.SPECIAL.id)?.get(0)?.toString(Charsets.UTF_8) ?: ""
                    val relay = tlv.get(NIP19TLVTypes.RELAY.id)?.get(0)?.toString(Charsets.UTF_8)
                    val author = tlv.get(NIP19TLVTypes.AUTHOR.id)?.get(0)?.toHexKey()
                    val kind = tlv.get(NIP19TLVTypes.KIND.id)?.get(0)?.let { toInt32(it) }

                    if (kind != null && author != null)
                        return ATag(kind, author, d)
                }

            } catch (e: Throwable) {
                println("Issue trying to Decode NIP19 ${this}: ${e.message}")
                //e.printStackTrace()
            }

            return null
        }
    }
}