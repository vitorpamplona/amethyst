package com.vitorpamplona.amethyst.service.nip19

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Bech32
import nostr.postr.bechToBytes
import nostr.postr.toByteArray
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
            Log.e("NIP19 Parser", "Issue trying to Decode NIP19 $key: ${e.message}", e)
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

        val hex = tlv.get(Tlv.Type.SPECIAL.id)
            ?.get(0)
            ?.toHexKey() ?: return null

        val relay = tlv.get(Tlv.Type.RELAY.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8)

        return Return(Type.USER, hex, relay)
    }

    private fun nevent(bytes: ByteArray): Return? {
        val tlv = Tlv.parse(bytes)

        val hex = tlv.get(Tlv.Type.SPECIAL.id)
            ?.get(0)
            ?.toHexKey() ?: return null

        val relay = tlv.get(Tlv.Type.RELAY.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8)

        val author = tlv.get(Tlv.Type.AUTHOR.id)
            ?.get(0)
            ?.toHexKey()

        val kind = tlv.get(Tlv.Type.KIND.id)
            ?.get(0)
            ?.let { Tlv.toInt32(it) }

        return Return(Type.EVENT, hex, relay, author, kind)
    }

    private fun nrelay(bytes: ByteArray): Return? {
        val relayUrl = Tlv.parse(bytes)
            .get(Tlv.Type.SPECIAL.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8) ?: return null

        return Return(Type.RELAY, relayUrl)
    }

    private fun naddr(bytes: ByteArray): Return? {
        val tlv = Tlv.parse(bytes)

        val d = tlv.get(Tlv.Type.SPECIAL.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8) ?: return null

        val relay = tlv.get(Tlv.Type.RELAY.id)
            ?.get(0)
            ?.toString(Charsets.UTF_8)

        val author = tlv.get(Tlv.Type.AUTHOR.id)
            ?.get(0)
            ?.toHexKey()

        val kind = tlv.get(Tlv.Type.KIND.id)
            ?.get(0)
            ?.let { Tlv.toInt32(it) }

        return Return(Type.ADDRESS, "$kind:$author:$d", relay, author, kind)
    }

    public fun createNEvent(idHex: String, author: String?, kind: Int?, relay: String?): String {
        val kind = kind?.toByteArray()
        val author = author?.hexToByteArray()
        val idHex = idHex.hexToByteArray()
        val relay = relay?.toByteArray(Charsets.UTF_8)

        var fullArray = byteArrayOf(Tlv.Type.SPECIAL.id, idHex.size.toByte()) + idHex

        if (relay != null) {
            fullArray = fullArray + byteArrayOf(Tlv.Type.RELAY.id, relay.size.toByte()) + relay
        }

        if (author != null) {
            fullArray = fullArray + byteArrayOf(Tlv.Type.AUTHOR.id, author.size.toByte()) + author
        }

        if (kind != null) {
            fullArray = fullArray + byteArrayOf(Tlv.Type.KIND.id, kind.size.toByte()) + kind
        }

        return Bech32.encodeBytes(hrp = "nevent", fullArray, Bech32.Encoding.Bech32)
    }
}
