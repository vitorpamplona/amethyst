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
package com.vitorpamplona.quartz.nip19Bech32.entities

import addHex
import addStringIfNotNull
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip19Bech32.TlvTypes
import com.vitorpamplona.quartz.nip19Bech32.asStringList
import com.vitorpamplona.quartz.nip19Bech32.firstAsHex
import com.vitorpamplona.quartz.nip19Bech32.tlv.Tlv
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder
import com.vitorpamplona.quartz.nip19Bech32.toNProfile

@Immutable
data class NProfile(
    val hex: String,
    val relay: List<String>,
) : Entity {
    companion object {
        fun parse(bytes: ByteArray): NProfile? {
            if (bytes.isEmpty()) return null

            val tlv = Tlv.parse(bytes)

            val hex = tlv.firstAsHex(TlvTypes.SPECIAL) ?: return null
            val relay = tlv.asStringList(TlvTypes.RELAY) ?: emptyList()

            if (hex.isBlank()) return null

            return NProfile(hex, relay)
        }

        fun create(
            authorPubKeyHex: String,
            relay: List<String>,
        ): String =
            TlvBuilder()
                .apply {
                    addHex(TlvTypes.SPECIAL, authorPubKeyHex)
                    relay.forEach {
                        addStringIfNotNull(TlvTypes.RELAY, it)
                    }
                }.build()
                .toNProfile()
    }
}
