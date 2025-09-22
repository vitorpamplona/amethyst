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
package com.vitorpamplona.quartz.nip19Bech32.entities

import addHex
import addHexIfNotNull
import addIntIfNotNull
import addStringIfNotNull
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.TlvTypes
import com.vitorpamplona.quartz.nip19Bech32.asStringList
import com.vitorpamplona.quartz.nip19Bech32.firstAsHex
import com.vitorpamplona.quartz.nip19Bech32.tlv.Tlv
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder
import com.vitorpamplona.quartz.nip19Bech32.toNEvent

@Immutable
data class NEvent(
    val hex: String,
    val relay: List<NormalizedRelayUrl>,
    val author: String?,
    val kind: Int?,
) : Entity {
    companion object {
        fun parse(bytes: ByteArray): NEvent? {
            if (bytes.isEmpty()) return null

            val tlv = Tlv.parse(bytes)

            val hex = tlv.firstAsHex(TlvTypes.SPECIAL) ?: return null
            val relay = tlv.asStringList(TlvTypes.RELAY) ?: emptyList()
            val author = tlv.firstAsHex(TlvTypes.AUTHOR)
            val kind = tlv.firstAsInt(TlvTypes.KIND.id)

            if (hex.isBlank()) return null

            return NEvent(
                hex,
                relay.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) },
                author,
                kind,
            )
        }

        fun create(
            idHex: String,
            author: String?,
            kind: Int?,
            relay: NormalizedRelayUrl?,
        ): String =
            TlvBuilder()
                .apply {
                    addHex(TlvTypes.SPECIAL, idHex)
                    if (relay != null) {
                        addStringIfNotNull(TlvTypes.RELAY, relay.url)
                    }
                    addHexIfNotNull(TlvTypes.AUTHOR, author)
                    addIntIfNotNull(TlvTypes.KIND, kind)
                }.build()
                .toNEvent()

        fun create(
            idHex: String,
            author: String?,
            kind: Int?,
            relays: List<NormalizedRelayUrl>,
        ): String =
            TlvBuilder()
                .apply {
                    addHex(TlvTypes.SPECIAL, idHex)
                    relays.forEach {
                        addStringIfNotNull(TlvTypes.RELAY, it.url)
                    }
                    addHexIfNotNull(TlvTypes.AUTHOR, author)
                    addIntIfNotNull(TlvTypes.KIND, kind)
                }.build()
                .toNEvent()
    }
}
