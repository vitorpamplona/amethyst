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
package com.vitorpamplona.quartz.nip10Notes.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.AddressReferenceTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * An `a` tag in a thread, marked the way NIP-10 marks an `e` ([MarkedETag.MARKER]):
 * `["a", <kind:pubkey:d>, <relay>, <marker>]`. A note replying to (or forking) an addressable
 * event says so with the marker; an unmarked `a` is a plain reference. Unlike an `e`, the marker
 * is only read from its own slot: an `a` has no author slot to shift it.
 */
@Immutable
data class MarkedATag(
    val address: Address,
    val relay: NormalizedRelayUrl? = null,
    val marker: MarkedETag.MARKER? = null,
) : AddressReferenceTag {
    override val relayHint get() = relay

    override fun toAddressId() = address.toValue()

    fun toTagArray() = assemble(address, relay, marker)

    companion object {
        const val TAG_NAME = "a"

        const val ORDER_ADDRESS = 1
        const val ORDER_RELAY = 2
        const val ORDER_MARKER = 3

        fun parse(tag: Array<String>): MarkedATag? {
            ensure(tag.has(ORDER_ADDRESS)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[ORDER_ADDRESS].isNotEmpty()) { return null }

            val address = Address.parse(tag[ORDER_ADDRESS]) ?: return null
            val relay = tag.getOrNull(ORDER_RELAY)?.let { RelayUrlNormalizer.normalizeOrNull(it) }
            val marker = tag.getOrNull(ORDER_MARKER)?.let { MarkedETag.MARKER.parse(it) }

            return MarkedATag(address, relay, marker)
        }

        fun parseRoot(tag: Array<String>): MarkedATag? {
            ensure(tag.has(ORDER_MARKER)) { return null }
            ensure(tag[ORDER_MARKER] == MarkedETag.MARKER.ROOT.code) { return null }
            return parse(tag)
        }

        fun parseReply(tag: Array<String>): MarkedATag? {
            ensure(tag.has(ORDER_MARKER)) { return null }
            ensure(tag[ORDER_MARKER] == MarkedETag.MARKER.REPLY.code) { return null }
            return parse(tag)
        }

        /** A marker keeps its relay slot, blank without a hint, so it stays in slot 3. */
        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl?,
            marker: MarkedETag.MARKER?,
        ) = if (marker != null) {
            arrayOf(TAG_NAME, address.toValue(), relay?.url ?: "", marker.code)
        } else {
            arrayOfNotNull(TAG_NAME, address.toValue(), relay?.url)
        }
    }
}
