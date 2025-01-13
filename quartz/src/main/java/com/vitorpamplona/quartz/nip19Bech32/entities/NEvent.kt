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
import addHexIfNotNull
import addIntIfNotNull
import addStringIfNotNull
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip19Bech32Entities.TlvTypes
import com.vitorpamplona.quartz.nip19Bech32Entities.asStringList
import com.vitorpamplona.quartz.nip19Bech32Entities.firstAsHex
import com.vitorpamplona.quartz.nip19Bech32Entities.tlv.Tlv
import com.vitorpamplona.quartz.nip19Bech32Entities.tlv.TlvBuilder
import com.vitorpamplona.quartz.nip19Bech32Entities.toNEvent

@Immutable
data class NEvent(
    val hex: String,
    val relay: List<String>,
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

            return NEvent(hex, relay, author, kind)
        }

        fun create(
            idHex: String,
            author: String?,
            kind: Int?,
            relay: String?,
        ): String =
            TlvBuilder()
                .apply {
                    addHex(TlvTypes.SPECIAL, idHex)
                    addStringIfNotNull(TlvTypes.RELAY, relay)
                    addHexIfNotNull(TlvTypes.AUTHOR, author)
                    addIntIfNotNull(TlvTypes.KIND, kind)
                }.build()
                .toNEvent()
    }
}
