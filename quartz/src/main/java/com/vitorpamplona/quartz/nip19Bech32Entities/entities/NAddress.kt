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
package com.vitorpamplona.quartz.nip19Bech32Entities.entities

import addHex
import addInt
import addString
import addStringIfNotNull
import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.addressables.ATag
import com.vitorpamplona.quartz.nip19Bech32Entities.TlvTypes
import com.vitorpamplona.quartz.nip19Bech32Entities.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32Entities.tlv.Tlv
import com.vitorpamplona.quartz.nip19Bech32Entities.tlv.TlvBuilder
import com.vitorpamplona.quartz.nip19Bech32Entities.toNAddress

@Immutable
data class NAddress(
    val kind: Int,
    val author: String,
    val dTag: String,
    val relay: List<String>,
) : Entity {
    fun aTag(): String = ATag.assembleATag(kind, author, dTag)

    companion object {
        fun parse(naddr: String): NAddress? {
            try {
                val key = naddr.removePrefix("nostr:")

                if (key.startsWith("naddr")) {
                    return parse(key.bechToBytes())
                }
            } catch (e: Throwable) {
                Log.w("NAddress", "Issue trying to Decode NIP19 $this: ${e.message}")
                // e.printStackTrace()
            }

            return null
        }

        fun parse(bytes: ByteArray): NAddress? {
            if (bytes.isEmpty()) return null

            val tlv = Tlv.parse(bytes)

            val d = tlv.firstAsString(TlvTypes.SPECIAL.id) ?: ""
            val relay = tlv.asStringList(TlvTypes.RELAY.id) ?: emptyList()
            val author = tlv.firstAsHex(TlvTypes.AUTHOR.id) ?: return null
            val kind = tlv.firstAsInt(TlvTypes.KIND.id) ?: return null

            return NAddress(kind, author, d, relay)
        }

        fun create(
            kind: Int,
            pubKeyHex: String,
            dTag: String,
            relay: String?,
        ): String =
            TlvBuilder()
                .apply {
                    addString(TlvTypes.SPECIAL, dTag)
                    addStringIfNotNull(TlvTypes.RELAY, relay)
                    addHex(TlvTypes.AUTHOR, pubKeyHex)
                    addInt(TlvTypes.KIND, kind)
                }.build()
                .toNAddress()
    }
}
