package com.vitorpamplona.amethyst.service.nip19

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.bechToBytes
import com.vitorpamplona.amethyst.service.toNEvent
import java.util.regex.Pattern

object Nip19 {
    enum class Type {
        USER, NOTE, EVENT, RELAY, ADDRESS
    }

    val nip19regex = Pattern.compile("(nostr:)?@?(nsec1|npub1|nevent1|naddr1|note1|nprofile1|nrelay1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)([\\S]*)", Pattern.CASE_INSENSITIVE)

    @Immutable
    data class Return(
        val type: Type,
        val hex: String,
        val relay: String? = null,
        val author: String? = null,
        val kind: Int? = null,
        val additionalChars: String = ""
    )

    fun uriToRoute(uri: String?): Return? {
        if (uri == null) return null

        try {
            val matcher = nip19regex.matcher(uri)
            if (!matcher.find()) {
                return null
            }

            val uriScheme = matcher.group(1) // nostr:
            val type = matcher.group(2) // npub1
            val key = matcher.group(3) // bech32
            val additionalChars = matcher.group(4) // additional chars

            return parseComponents(uriScheme, type, key, additionalChars)
        } catch (e: Throwable) {
            Log.e("NIP19 Parser", "Issue trying to Decode NIP19 $uri: ${e.message}", e)
        }

        return null
    }

    fun parseComponents(
        uriScheme: String?,
        type: String,
        key: String?,
        additionalChars: String?
    ): Return? {
        return try {
            val bytes = (type + key).bechToBytes()
            val parsed = when (type.lowercase()) {
                "npub1" -> npub(bytes)
                "note1" -> note(bytes)
                "nprofile1" -> nprofile(bytes)
                "nevent1" -> nevent(bytes)
                "nrelay1" -> nrelay(bytes)
                "naddr1" -> naddr(bytes)
                else -> null
            }
            parsed?.copy(additionalChars = additionalChars ?: "")
        } catch (e: Throwable) {
            Log.w("NIP19 Parser", "Issue trying to Decode NIP19 $key: ${e.message}", e)
            null
        }
    }

    private fun npub(bytes: ByteArray): Return {
        return Return(Type.USER, bytes.toHexKey())
    }

    private fun note(bytes: ByteArray): Return {
        return Return(Type.NOTE, bytes.toHexKey())
    }

    private fun nprofile(bytes: ByteArray): Return? {
        val tlv = Tlv.parse(bytes)

        val hex = tlv.firstAsHex(Tlv.Type.SPECIAL) ?: return null
        val relay = tlv.firstAsString(Tlv.Type.RELAY)

        return Return(Type.USER, hex, relay)
    }

    private fun nevent(bytes: ByteArray): Return? {
        val tlv = Tlv.parse(bytes)

        val hex = tlv.firstAsHex(Tlv.Type.SPECIAL) ?: return null
        val relay = tlv.firstAsString(Tlv.Type.RELAY)
        val author = tlv.firstAsHex(Tlv.Type.AUTHOR)
        val kind = tlv.firstAsInt(Tlv.Type.KIND.id)

        return Return(Type.EVENT, hex, relay, author, kind)
    }

    private fun nrelay(bytes: ByteArray): Return? {
        val relayUrl = Tlv.parse(bytes).firstAsString(Tlv.Type.SPECIAL.id) ?: return null

        return Return(Type.RELAY, relayUrl)
    }

    private fun naddr(bytes: ByteArray): Return? {
        val tlv = Tlv.parse(bytes)

        val d = tlv.firstAsString(Tlv.Type.SPECIAL.id) ?: ""
        val relay = tlv.firstAsString(Tlv.Type.RELAY.id)
        val author = tlv.firstAsHex(Tlv.Type.AUTHOR.id) ?: return null
        val kind = tlv.firstAsInt(Tlv.Type.KIND.id) ?: return null

        return Return(Type.ADDRESS, "$kind:$author:$d", relay, author, kind)
    }

    public fun createNEvent(idHex: String, author: String?, kind: Int?, relay: String?): String {
        return TlvBuilder().apply {
            addHex(Tlv.Type.SPECIAL, idHex)
            addStringIfNotNull(Tlv.Type.RELAY, relay)
            addHexIfNotNull(Tlv.Type.AUTHOR, author)
            addIntIfNotNull(Tlv.Type.KIND, kind)
        }.build().toNEvent()
    }
}
