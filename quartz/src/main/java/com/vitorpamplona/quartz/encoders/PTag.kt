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
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import com.vitorpamplona.quartz.utils.removeTrailingNullsAndEmptyOthers

@Immutable
data class PTag(
    val pubKeyHex: HexKey,
) {
    var relay: String? = null

    constructor(pubKeyHex: HexKey, relayHint: String?) : this(pubKeyHex) {
        this.relay = relayHint?.ifBlank { null }
    }

    fun countMemory(): Long =
        2 * pointerSizeInBytes + // 2 fields, 4 bytes each reference (32bit)
            pubKeyHex.bytesUsedInMemory() +
            (relay?.bytesUsedInMemory() ?: 0)

    fun toNProfile(): String = Nip19Bech32.createNProfile(pubKeyHex, relay?.let { listOf(it) } ?: emptyList())

    fun toNPub(): String = Nip19Bech32.createNPub(pubKeyHex)

    fun toPTagArray() = removeTrailingNullsAndEmptyOthers("p", pubKeyHex, relay)

    companion object {
        fun parseNAddr(nprofile: String): PTag? {
            try {
                val parsed = Nip19Bech32.uriToRoute(nprofile)?.entity

                return when (parsed) {
                    is Nip19Bech32.NPub -> PTag(parsed.hex)
                    is Nip19Bech32.NProfile -> PTag(parsed.hex, parsed.relay.firstOrNull())
                    else -> null
                }
            } catch (e: Throwable) {
                Log.w("PTag", "Issue trying to Decode NIP19 $this: ${e.message}")
                return null
            }
        }
    }
}
