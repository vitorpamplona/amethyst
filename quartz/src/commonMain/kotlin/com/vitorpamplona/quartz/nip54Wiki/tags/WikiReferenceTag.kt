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
package com.vitorpamplona.quartz.nip54Wiki.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.AddressReferenceTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * An `e` or `a` tag of a NIP-54 event, with the marker NIP-54 and the clients extending it put in
 * the fourth slot: `fork` and `defer` on articles, `source` (or `fork`) on merge requests,
 * `result` and `request` on merge acceptances.
 *
 *     ["e", "<id>", "<relay>", "<marker>"]
 *     ["a", "<kind>:<pubkey>:<d>", "<relay>", "<marker>"]
 *
 * Not [com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag]: these markers are NIP-54 vocabulary
 * and take no part in NIP-10 threading, and the slot is read as written, never searched for.
 */
@Immutable
sealed interface WikiReferenceTag {
    val marker: String?

    @Immutable
    data class EventRef(
        override val eventId: HexKey,
        override val relay: NormalizedRelayUrl? = null,
        override val marker: String? = null,
    ) : WikiReferenceTag,
        GenericETag {
        override val author: HexKey? get() = null

        override fun toTagArray() = arrayOfNotNull(ETag.TAG_NAME, eventId, relay?.url ?: if (marker != null) "" else null, marker)
    }

    @Immutable
    data class AddressRef(
        val address: ATag,
        override val marker: String? = null,
    ) : WikiReferenceTag,
        AddressReferenceTag {
        override fun toAddressId() = address.toAddressId()

        override val relayHint get() = address.relayHint

        fun toTagArray() = arrayOfNotNull(ATag.TAG_NAME, address.toTag(), address.relay?.url ?: if (marker != null) "" else null, marker)
    }

    companion object {
        const val MARKER_SLOT = 3
        const val FORK_MARKER = "fork"
        const val DEFER_MARKER = "defer"

        /** Either kind of reference, in one walk, so a class keeps its tags' order. */
        fun parse(tag: Array<String>): WikiReferenceTag? = parseEvent(tag) ?: parseAddress(tag)

        fun parseEvent(tag: Array<String>): EventRef? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == ETag.TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val relay = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return EventRef(tag[1], relay, pickMarker(tag))
        }

        fun parseAddress(tag: Array<String>): AddressRef? {
            val address = ATag.parse(tag) ?: return null
            return AddressRef(address, pickMarker(tag))
        }

        private fun pickMarker(tag: Array<String>) = tag.getOrNull(MARKER_SLOT)?.ifEmpty { null }
    }
}
