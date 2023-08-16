package com.vitorpamplona.quartz.encoders

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.Hex

@Immutable
data class ATag(val kind: Int, val pubKeyHex: String, val dTag: String, val relay: String?) {
    fun toTag() = "$kind:$pubKeyHex:$dTag"

    fun toNAddr(): String {
        return TlvBuilder().apply {
            addString(Nip19.TlvTypes.SPECIAL, dTag)
            addStringIfNotNull(Nip19.TlvTypes.RELAY, relay)
            addHex(Nip19.TlvTypes.AUTHOR, pubKeyHex)
            addInt(Nip19.TlvTypes.KIND, kind)
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

                    val d = tlv.firstAsString(Nip19.TlvTypes.SPECIAL) ?: ""
                    val relay = tlv.firstAsString(Nip19.TlvTypes.RELAY)
                    val author = tlv.firstAsHex(Nip19.TlvTypes.AUTHOR)
                    val kind = tlv.firstAsInt(Nip19.TlvTypes.KIND)

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
