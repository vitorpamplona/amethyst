/*
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
package com.vitorpamplona.quartz.nip60Cashu.history

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Represents a reference to a token event in the spending history.
 *
 * Markers:
 * - "created" - A new token event was created
 * - "destroyed" - A token event was destroyed
 * - "redeemed" - A NIP-61 nutzap was redeemed
 */
@Immutable
data class TokenReference(
    val eventId: HexKey,
    val relay: String?,
    val marker: String,
) {
    companion object {
        const val MARKER_CREATED = "created"
        const val MARKER_DESTROYED = "destroyed"
        const val MARKER_REDEEMED = "redeemed"

        fun parseFromTag(tag: Array<String>): TokenReference? {
            if (tag.size < 4 || tag[0] != "e") return null
            val marker = tag[3]
            if (marker !in listOf(MARKER_CREATED, MARKER_DESTROYED, MARKER_REDEEMED)) return null
            return TokenReference(
                eventId = tag[1],
                relay = tag.getOrNull(2)?.ifEmpty { null },
                marker = marker,
            )
        }

        fun assemble(
            eventId: HexKey,
            relay: String?,
            marker: String,
        ) = arrayOf("e", eventId, relay ?: "", marker)
    }
}
