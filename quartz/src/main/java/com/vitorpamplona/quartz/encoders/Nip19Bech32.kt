/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.encoders

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.events.Event
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Nip19Bech32 {
    enum class Type {
        USER,
        NOTE,
        EVENT,
        RELAY,
        ADDRESS,
    }

    enum class TlvTypes(val id: Byte) {
        SPECIAL(0),
        RELAY(1),
        AUTHOR(2),
        KIND(3),
    }

    val nip19regex =
        Pattern.compile(
            "(nostr:)?@?(nsec1|npub1|nevent1|naddr1|note1|nprofile1|nrelay1|nembed1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)([\\S]*)",
            Pattern.CASE_INSENSITIVE,
        )

    @Immutable
    data class ParseReturn(val entity: Entity, val additionalChars: String? = null)

    interface Entity

    @Immutable
    data class NSec(val hex: String) : Entity

    @Immutable
    data class NPub(val hex: String) : Entity

    @Immutable
    data class Note(val hex: String) : Entity

    @Immutable
    data class NProfile(val hex: String, val relay: List<String>) : Entity

    @Immutable
    data class NEvent(val hex: String, val relay: List<String>, val author: String?, val kind: Int?) : Entity

    @Immutable
    data class NAddress(val atag: String, val relay: List<String>, val author: String, val kind: Int) : Entity

    @Immutable
    data class NRelay(val relay: List<String>) : Entity

    @Immutable
    data class NEmbed(val event: Event) : Entity

    fun uriToRoute(uri: String?): ParseReturn? {
        if (uri == null) return null

        try {
            val matcher = nip19regex.matcher(uri)
            if (!matcher.find()) {
                return null
            }

            val type = matcher.group(2) // npub1
            val key = matcher.group(3) // bech32
            val additionalChars = matcher.group(4) // additional chars

            if (type == null) return null

            return parseComponents(type, key, additionalChars.ifEmpty { null })
        } catch (e: Throwable) {
            Log.e("NIP19 Parser", "Issue trying to Decode NIP19 $uri: ${e.message}", e)
        }

        return null
    }

    fun parseComponents(
        type: String,
        key: String?,
        additionalChars: String?,
    ): ParseReturn? {
        return try {
            val bytes = (type + key).bechToBytes()

            when (type.lowercase()) {
                "nsec1" -> nsec(bytes)
                "npub1" -> npub(bytes)
                "note1" -> note(bytes)
                "nprofile1" -> nprofile(bytes)
                "nevent1" -> nevent(bytes)
                "nrelay1" -> nrelay(bytes)
                "naddr1" -> naddr(bytes)
                "nembed1" -> nembed(bytes)
                else -> null
            }?.let {
                ParseReturn(it, additionalChars)
            }
        } catch (e: Throwable) {
            Log.w("NIP19 Parser", "Issue trying to Decode NIP19 $key: ${e.message}", e)
            null
        }
    }

    private fun nembed(bytes: ByteArray): NEmbed? {
        if (bytes.isEmpty()) return null
        return NEmbed(Event.fromJson(ungzip(bytes)))
    }

    private fun nsec(bytes: ByteArray): NSec? {
        if (bytes.isEmpty()) return null
        return NSec(bytes.toHexKey())
    }

    private fun npub(bytes: ByteArray): NPub? {
        if (bytes.isEmpty()) return null
        return NPub(bytes.toHexKey())
    }

    private fun note(bytes: ByteArray): Note? {
        if (bytes.isEmpty()) return null
        return Note(bytes.toHexKey())
    }

    private fun nprofile(bytes: ByteArray): NProfile? {
        if (bytes.isEmpty()) return null

        val tlv = Tlv.parse(bytes)

        val hex = tlv.firstAsHex(TlvTypes.SPECIAL) ?: return null
        val relay = tlv.asStringList(TlvTypes.RELAY) ?: emptyList()

        if (hex.isBlank()) return null

        return NProfile(hex, relay)
    }

    private fun nevent(bytes: ByteArray): NEvent? {
        if (bytes.isEmpty()) return null

        val tlv = Tlv.parse(bytes)

        val hex = tlv.firstAsHex(TlvTypes.SPECIAL) ?: return null
        val relay = tlv.asStringList(TlvTypes.RELAY) ?: emptyList()
        val author = tlv.firstAsHex(TlvTypes.AUTHOR)
        val kind = tlv.firstAsInt(TlvTypes.KIND.id)

        if (hex.isBlank()) return null

        return NEvent(hex, relay, author, kind)
    }

    private fun nrelay(bytes: ByteArray): NRelay? {
        if (bytes.isEmpty()) return null

        val relayUrl = Tlv.parse(bytes).asStringList(TlvTypes.SPECIAL.id) ?: return null

        return NRelay(relayUrl)
    }

    private fun naddr(bytes: ByteArray): NAddress? {
        if (bytes.isEmpty()) return null

        val tlv = Tlv.parse(bytes)

        val d = tlv.firstAsString(TlvTypes.SPECIAL.id) ?: ""
        val relay = tlv.asStringList(TlvTypes.RELAY.id) ?: emptyList()
        val author = tlv.firstAsHex(TlvTypes.AUTHOR.id) ?: return null
        val kind = tlv.firstAsInt(TlvTypes.KIND.id) ?: return null

        return NAddress("$kind:$author:$d", relay, author, kind)
    }

    fun createNEvent(
        idHex: String,
        author: String?,
        kind: Int?,
        relay: String?,
    ): String {
        return TlvBuilder()
            .apply {
                addHex(TlvTypes.SPECIAL, idHex)
                addStringIfNotNull(TlvTypes.RELAY, relay)
                addHexIfNotNull(TlvTypes.AUTHOR, author)
                addIntIfNotNull(TlvTypes.KIND, kind)
            }
            .build()
            .toNEvent()
    }

    fun createNEmbed(event: Event): String {
        return gzip(event.toJson()).toNEmbed()
    }

    fun gzip(content: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        val array = bos.toByteArray()
        return array
    }

    fun ungzip(content: ByteArray): String = GZIPInputStream(content.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

fun ByteArray.toNsec() = Bech32.encodeBytes(hrp = "nsec", this, Bech32.Encoding.Bech32)

fun ByteArray.toNpub() = Bech32.encodeBytes(hrp = "npub", this, Bech32.Encoding.Bech32)

fun ByteArray.toNote() = Bech32.encodeBytes(hrp = "note", this, Bech32.Encoding.Bech32)

fun ByteArray.toNEvent() = Bech32.encodeBytes(hrp = "nevent", this, Bech32.Encoding.Bech32)

fun ByteArray.toNAddress() = Bech32.encodeBytes(hrp = "naddr", this, Bech32.Encoding.Bech32)

fun ByteArray.toLnUrl() = Bech32.encodeBytes(hrp = "lnurl", this, Bech32.Encoding.Bech32)

fun ByteArray.toNEmbed() = Bech32.encodeBytes(hrp = "nembed", this, Bech32.Encoding.Bech32)

fun decodePublicKey(key: String): ByteArray {
    return when (val parsed = Nip19Bech32.uriToRoute(key)?.entity) {
        is Nip19Bech32.NSec -> KeyPair(privKey = key.bechToBytes()).pubKey
        is Nip19Bech32.NPub -> parsed.hex.hexToByteArray()
        is Nip19Bech32.NProfile -> parsed.hex.hexToByteArray()
        else -> Hex.decode(key) // crashes on purpose
    }
}

fun decodePrivateKeyAsHexOrNull(key: String): HexKey? {
    return try {
        when (val parsed = Nip19Bech32.uriToRoute(key)?.entity) {
            is Nip19Bech32.NSec -> parsed.hex
            is Nip19Bech32.NPub -> null
            is Nip19Bech32.NProfile -> null
            is Nip19Bech32.Note -> null
            is Nip19Bech32.NEvent -> null
            is Nip19Bech32.NEmbed -> null
            is Nip19Bech32.NRelay -> null
            is Nip19Bech32.NAddress -> null
            else -> Hex.decode(key).toHexKey()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

fun decodePublicKeyAsHexOrNull(key: String): HexKey? {
    return try {
        when (val parsed = Nip19Bech32.uriToRoute(key)?.entity) {
            is Nip19Bech32.NSec -> KeyPair(privKey = key.bechToBytes()).pubKey.toHexKey()
            is Nip19Bech32.NPub -> parsed.hex
            is Nip19Bech32.NProfile -> parsed.hex
            is Nip19Bech32.Note -> null
            is Nip19Bech32.NEvent -> null
            is Nip19Bech32.NEmbed -> null
            is Nip19Bech32.NRelay -> null
            is Nip19Bech32.NAddress -> null
            else -> Hex.decode(key).toHexKey()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

fun decodeEventIdAsHexOrNull(key: String): HexKey? {
    return try {
        when (val parsed = Nip19Bech32.uriToRoute(key)?.entity) {
            is Nip19Bech32.NSec -> null
            is Nip19Bech32.NPub -> null
            is Nip19Bech32.NProfile -> null
            is Nip19Bech32.Note -> parsed.hex
            is Nip19Bech32.NEvent -> parsed.hex
            is Nip19Bech32.NAddress -> parsed.atag
            is Nip19Bech32.NEmbed -> null
            is Nip19Bech32.NRelay -> null
            else -> Hex.decode(key).toHexKey()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

fun TlvBuilder.addString(
    type: Nip19Bech32.TlvTypes,
    string: String,
) = addString(type.id, string)

fun TlvBuilder.addHex(
    type: Nip19Bech32.TlvTypes,
    key: HexKey,
) = addHex(type.id, key)

fun TlvBuilder.addInt(
    type: Nip19Bech32.TlvTypes,
    data: Int,
) = addInt(type.id, data)

fun TlvBuilder.addStringIfNotNull(
    type: Nip19Bech32.TlvTypes,
    data: String?,
) = addStringIfNotNull(type.id, data)

fun TlvBuilder.addHexIfNotNull(
    type: Nip19Bech32.TlvTypes,
    data: HexKey?,
) = addHexIfNotNull(type.id, data)

fun TlvBuilder.addIntIfNotNull(
    type: Nip19Bech32.TlvTypes,
    data: Int?,
) = addIntIfNotNull(type.id, data)

fun Tlv.firstAsInt(type: Nip19Bech32.TlvTypes) = firstAsInt(type.id)

fun Tlv.firstAsHex(type: Nip19Bech32.TlvTypes) = firstAsHex(type.id)

fun Tlv.firstAsString(type: Nip19Bech32.TlvTypes) = firstAsString(type.id)

fun Tlv.asStringList(type: Nip19Bech32.TlvTypes) = asStringList(type.id)
