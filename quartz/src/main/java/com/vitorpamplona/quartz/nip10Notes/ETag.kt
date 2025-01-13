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
package com.vitorpamplona.quartz.nip10Notes

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.Note
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import com.vitorpamplona.quartz.utils.removeTrailingNullsAndEmptyOthers

@Immutable
data class ETag(
    val eventId: HexKey,
) {
    var relay: String? = null
    var authorPubKeyHex: HexKey? = null

    constructor(eventId: HexKey, relayHint: String? = null, authorPubKeyHex: HexKey? = null) : this(eventId) {
        this.relay = relayHint
        this.authorPubKeyHex = authorPubKeyHex
    }

    fun countMemory(): Long =
        2 * pointerSizeInBytes + // 2 fields, 4 bytes each reference (32bit)
            eventId.bytesUsedInMemory() +
            (relay?.bytesUsedInMemory() ?: 0)

    fun toNEvent(): String = NEvent.create(eventId, authorPubKeyHex, null, relay)

    fun toETagArray() = removeTrailingNullsAndEmptyOthers("e", eventId, relay, authorPubKeyHex)

    fun toQTagArray() = removeTrailingNullsAndEmptyOthers("q", eventId, relay, authorPubKeyHex)

    companion object {
        fun parseNIP19(nevent: String): ETag? {
            try {
                val parsed = Nip19Parser.uriToRoute(nevent)?.entity

                return when (parsed) {
                    is Note -> ETag(parsed.hex)
                    is NEvent -> ETag(parsed.hex, parsed.author, parsed.relay.firstOrNull())
                    else -> null
                }
            } catch (e: Throwable) {
                Log.w("PTag", "Issue trying to Decode NIP19 $this: ${e.message}")
                return null
            }
        }
    }
}
