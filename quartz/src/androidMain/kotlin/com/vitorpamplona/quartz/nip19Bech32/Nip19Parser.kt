/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip19Bech32

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CancellationException
import java.util.regex.Pattern

object Nip19Parser {
    private val nip19PlusNip46regex: Pattern =
        Pattern.compile(
            "(nostr:)?@?((nsec1|npub1|note1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]{58})|(nevent1|naddr1|nprofile1|nrelay1|nembed1|ncryptsec1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+))([\\S]*)",
            Pattern.CASE_INSENSITIVE,
        )

    val nip19regex: Pattern =
        Pattern.compile(
            "(nostr:)?@?((nsec1|npub1|note1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]{58})|(nevent1|naddr1|nprofile1|nrelay1|nembed1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+))([\\S]*)",
            Pattern.CASE_INSENSITIVE,
        )

    val nip19regexEvents: Pattern =
        Pattern.compile(
            "(nostr:)?@?(nevent1|naddr1|note1|nrelay1|nembed1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)([\\S]*)",
            Pattern.CASE_INSENSITIVE,
        )

    @Immutable
    data class ParseReturn(
        val entity: Entity,
        val nip19raw: String,
        val additionalChars: String? = null,
    )

    fun tryParseAndClean(uri: String?): String? {
        if (uri == null) return null

        try {
            val matcher = nip19PlusNip46regex.matcher(uri)
            if (!matcher.find()) {
                return null
            }

            val type = matcher.group(3) ?: matcher.group(5) // npub1
            val key = matcher.group(4) ?: matcher.group(6) // bech32

            return type!! + key
        } catch (e: Throwable) {
            Log.e("NIP19 Parser", "Issue trying to Decode NIP19 $uri: ${e.message}", e)
        }

        return null
    }

    fun uriToRoute(uri: String?): ParseReturn? {
        if (uri == null) return null

        try {
            val matcher = nip19regex.matcher(uri)
            if (!matcher.find()) {
                return null
            }

            val type = matcher.group(3) ?: matcher.group(5) // npub1
            val key = matcher.group(4) ?: matcher.group(6) // bech32
            val additionalChars = matcher.group(7) // additional chars

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
    ): ParseReturn? =
        try {
            val nip19 = (type + key)
            val bytes = nip19.bechToBytes()

            when (type.lowercase()) {
                "nsec1" -> NSec.parse(bytes)
                "npub1" -> NPub.parse(bytes)
                "note1" -> NNote.parse(bytes)
                "nprofile1" -> NProfile.parse(bytes)
                "nevent1" -> NEvent.parse(bytes)
                "nrelay1" -> NRelay.parse(bytes)
                "naddr1" -> NAddress.parse(bytes)
                "nembed1" -> NEmbed.parse(bytes)
                else -> null
            }?.let {
                ParseReturn(it, nip19, additionalChars)
            }
        } catch (e: Throwable) {
            Log.w("NIP19 Parser", "Issue trying to Decode NIP19 $key: ${e.message}", e)
            null
        }

    fun parseAll(content: String): List<Entity> {
        val matcher = nip19regex.matcher(content)
        val returningList = mutableListOf<Entity>()
        while (matcher.find()) {
            val type = matcher.group(3) ?: matcher.group(5) // npub1
            val key = matcher.group(4) ?: matcher.group(6) // bech32
            val additionalChars = matcher.group(7) // additional chars

            if (type != null) {
                val parsed = parseComponents(type, key, additionalChars)?.entity

                if (parsed != null) {
                    returningList.add(parsed)
                }
            }
        }
        return returningList
    }

    fun parseAllEvents(content: String): List<Entity> {
        val matcher2 = nip19regexEvents.matcher(content)
        val returningList = mutableListOf<Entity>()
        while (matcher2.find()) {
            val type = matcher2.group(2) // npub1
            val key = matcher2.group(3) // bech32
            val additionalChars = matcher2.group(4) // additional chars

            if (type != null) {
                val parsed = parseComponents(type, key, additionalChars)?.entity

                if (parsed != null) {
                    returningList.add(parsed)
                }
            }
        }
        return returningList
    }
}

fun decodePublicKey(key: String): ByteArray =
    when (val parsed = Nip19Parser.uriToRoute(key)?.entity) {
        is NSec -> Nip01.pubKeyCreate(parsed.hex.hexToByteArray())
        is NPub -> parsed.hex.hexToByteArray()
        is NProfile -> parsed.hex.hexToByteArray()
        else -> Hex.decode(key) // crashes on purpose
    }

fun decodePrivateKeyAsHexOrNull(key: String): HexKey? =
    try {
        when (val parsed = Nip19Parser.uriToRoute(key)?.entity) {
            is NSec -> parsed.hex
            is NPub -> null
            is NProfile -> null
            is NNote -> null
            is NEvent -> null
            is NEmbed -> null
            is NRelay -> null
            is NAddress -> null
            else -> Hex.decode(key).toHexKey()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

fun decodePublicKeyAsHexOrNull(key: String): HexKey? =
    try {
        when (val parsed = Nip19Parser.uriToRoute(key)?.entity) {
            is NSec -> Nip01.pubKeyCreate(parsed.hex.hexToByteArray()).toHexKey()
            is NPub -> parsed.hex
            is NProfile -> parsed.hex
            is NNote -> null
            is NEvent -> null
            is NEmbed -> null
            is NRelay -> null
            is NAddress -> null
            else -> Hex.decode(key).toHexKey()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

fun decodeEventIdAsHexOrNull(key: String): HexKey? =
    try {
        when (val parsed = Nip19Parser.uriToRoute(key)?.entity) {
            is NSec -> null
            is NPub -> null
            is NProfile -> null
            is NNote -> parsed.hex
            is NEvent -> parsed.hex
            is NAddress -> parsed.aTag()
            is NEmbed -> null
            is NRelay -> null
            else -> Hex.decode(key).toHexKey()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
