package com.vitorpamplona.quartz.encoders

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.Hex
import java.util.regex.Pattern

object Nip19 {
    enum class Type {
        USER, NOTE, EVENT, RELAY, ADDRESS
    }

    enum class TlvTypes(val id: Byte) {
        SPECIAL(0),
        RELAY(1),
        AUTHOR(2),
        KIND(3);
    }

    val nip19regex = Pattern.compile(
        "(nostr:)?@?(nsec1|npub1|nevent1|naddr1|note1|nprofile1|nrelay1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)([\\S]*)",
        Pattern.CASE_INSENSITIVE
    )

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

        val hex = tlv.firstAsHex(TlvTypes.SPECIAL) ?: return null
        val relay = tlv.firstAsString(TlvTypes.RELAY)

        return Return(Type.USER, hex, relay)
    }

    private fun nevent(bytes: ByteArray): Return? {
        val tlv = Tlv.parse(bytes)

        val hex = tlv.firstAsHex(TlvTypes.SPECIAL) ?: return null
        val relay = tlv.firstAsString(TlvTypes.RELAY)
        val author = tlv.firstAsHex(TlvTypes.AUTHOR)
        val kind = tlv.firstAsInt(TlvTypes.KIND.id)

        return Return(Type.EVENT, hex, relay, author, kind)
    }

    private fun nrelay(bytes: ByteArray): Return? {
        val relayUrl = Tlv.parse(bytes).firstAsString(TlvTypes.SPECIAL.id) ?: return null

        return Return(Type.RELAY, relayUrl)
    }

    private fun naddr(bytes: ByteArray): Return? {
        val tlv = Tlv.parse(bytes)

        val d = tlv.firstAsString(TlvTypes.SPECIAL.id) ?: ""
        val relay = tlv.firstAsString(TlvTypes.RELAY.id)
        val author = tlv.firstAsHex(TlvTypes.AUTHOR.id) ?: return null
        val kind = tlv.firstAsInt(TlvTypes.KIND.id) ?: return null

        return Return(Type.ADDRESS, "$kind:$author:$d", relay, author, kind)
    }

    public fun createNEvent(idHex: String, author: String?, kind: Int?, relay: String?): String {
        return TlvBuilder().apply {
            addHex(TlvTypes.SPECIAL, idHex)
            addStringIfNotNull(TlvTypes.RELAY, relay)
            addHexIfNotNull(TlvTypes.AUTHOR, author)
            addIntIfNotNull(TlvTypes.KIND, kind)
        }.build().toNEvent()
    }
}

fun decodePublicKey(key: String): ByteArray {
    val parsed = Nip19.uriToRoute(key)
    val pubKeyParsed = parsed?.hex?.hexToByteArray()

    return if (key.startsWith("nsec")) {
        KeyPair(privKey = key.bechToBytes()).pubKey
    } else if (pubKeyParsed != null) {
        pubKeyParsed
    } else {
        Hex.decode(key)
    }
}

fun decodePublicKeyAsHexOrNull(key: String): HexKey? {
    return try {
        val parsed = Nip19.uriToRoute(key)
        val pubKeyParsed = parsed?.hex

        if (key.startsWith("nsec")) {
            KeyPair(privKey = key.bechToBytes()).pubKey.toHexKey()
        } else if (pubKeyParsed != null) {
            pubKeyParsed
        } else {
            Hex.decode(key).toHexKey()
        }
    } catch (e: Exception) {
        null
    }
}


fun TlvBuilder.addString(type: Nip19.TlvTypes, string: String) = addString(type.id, string)
fun TlvBuilder.addHex(type: Nip19.TlvTypes, key: HexKey) = addHex(type.id, key)
fun TlvBuilder.addInt(type: Nip19.TlvTypes, data: Int) = addInt(type.id, data)

fun TlvBuilder.addStringIfNotNull(type: Nip19.TlvTypes, data: String?) = addStringIfNotNull(type.id, data)
fun TlvBuilder.addHexIfNotNull(type: Nip19.TlvTypes, data: HexKey?) = addHexIfNotNull(type.id, data)
fun TlvBuilder.addIntIfNotNull(type: Nip19.TlvTypes, data: Int?) = addIntIfNotNull(type.id, data)

fun Tlv.firstAsInt(type: Nip19.TlvTypes) = firstAsInt(type.id)
fun Tlv.firstAsHex(type: Nip19.TlvTypes) = firstAsHex(type.id)
fun Tlv.firstAsString(type: Nip19.TlvTypes) = firstAsString(type.id)