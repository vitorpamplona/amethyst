package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.service.bechToBytes
import com.vitorpamplona.amethyst.service.nip19.Tlv
import com.vitorpamplona.amethyst.service.nip19.TlvBuilder
import com.vitorpamplona.amethyst.service.toNAddress
import fr.acinq.secp256k1.Hex

@Immutable
data class ATag(val kind: Int, val pubKeyHex: String, val dTag: String, val relay: String?) {
    fun toTag() = "$kind:$pubKeyHex:$dTag"

    fun toNAddr(): String {
        return TlvBuilder().apply {
            addString(Tlv.Type.SPECIAL, dTag)
            addStringIfNotNull(Tlv.Type.RELAY, relay)
            addHex(Tlv.Type.AUTHOR, pubKeyHex)
            addInt(Tlv.Type.KIND, kind)
        }.build().toNAddress()
    }

    companion object {
        fun isATag(key: String): Boolean {
            return key.startsWith("naddr1") || key.contains(":")
        }

        fun parse(address: String, relay: String?): ATag? {
            return if (address.startsWith("naddr") || address.startsWith("nostr:naddr")) {
                parseNAddr(address)
            } else {
                parseAtag(address, relay)
            }
        }

        fun parseAtag(atag: String, relay: String?): ATag? {
            return try {
                val parts = atag.split(":")
                Hex.decode(parts[1])
                ATag(parts[0].toInt(), parts[1], parts[2], relay)
            } catch (t: Throwable) {
                Log.w("ATag", "Error parsing A Tag: $atag: ${t.message}")
                null
            }
        }

        fun parseNAddr(naddr: String): ATag? {
            try {
                val key = naddr.removePrefix("nostr:")

                if (key.startsWith("naddr")) {
                    val tlv = Tlv.parse(key.bechToBytes())

                    val d = tlv.firstAsString(Tlv.Type.SPECIAL) ?: ""
                    val relay = tlv.firstAsString(Tlv.Type.RELAY)
                    val author = tlv.firstAsHex(Tlv.Type.AUTHOR)
                    val kind = tlv.firstAsInt(Tlv.Type.KIND)

                    if (kind != null && author != null) {
                        return ATag(kind, author, d, relay)
                    }
                }
            } catch (e: Throwable) {
                Log.w("ATag", "Issue trying to Decode NIP19 $this: ${e.message}")
                // e.printStackTrace()
            }

            return null
        }
    }
}
