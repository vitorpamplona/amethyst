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
package com.vitorpamplona.quartz.experimental.nipsOnNostr.tags

import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

class ForkTag(
    val address: Address,
    val relayHint: NormalizedRelayUrl? = null,
) {
    fun toTag() = Address.assemble(address.kind, address.pubKeyHex, address.dTag)

    fun toTagArray() = assemble(address, relayHint)

    fun toTagIdOnly() = assemble(address, null)

    companion object {
        const val TAG_NAME = "a"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && Address.isOfKind(tag[1], NipTextEvent.KIND_STR)

        fun isTagged(
            tag: Array<String>,
            addressId: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == addressId

        fun isTagged(
            tag: Array<String>,
            address: ForkTag,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == address.toTag()

        fun isIn(
            tag: Array<String>,
            addressIds: Set<String>,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] in addressIds

        fun parse(tag: Array<String>): ForkTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(
                Address.Companion.isOfKind(
                    tag[1],
                    CommunityDefinitionEvent.Companion.KIND_STR,
                ),
            ) { return null }

            val address = Address.Companion.parse(tag[1]) ?: return null
            val relayHint = tag.getOrNull(2)?.let { RelayUrlNormalizer.Companion.normalizeOrNull(it) }
            return ForkTag(address, relayHint)
        }

        fun parseValidAddress(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(
                Address.Companion.isOfKind(
                    tag[1],
                    CommunityDefinitionEvent.Companion.KIND_STR,
                ),
            ) { return null }
            return Address.Companion.parse(tag[1])?.toValue()
        }

        fun parseAddress(tag: Array<String>): Address? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val address = Address.parse(tag[1]) ?: return null
            ensure(address.kind == NipTextEvent.KIND) { return null }
            return address
        }

        fun parseAddressId(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(
                Address.isOfKind(
                    tag[1],
                    NipTextEvent.KIND_STR,
                ),
            ) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Array<String>): AddressHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(
                Address.isOfKind(
                    tag[1],
                    NipTextEvent.KIND_STR,
                ),
            ) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(relayHint != null) { return null }

            return AddressHint(tag[1], relayHint)
        }

        fun assemble(
            aTagId: String,
            relay: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, aTagId, relay?.url, "fork")

        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl?,
        ) = assemble(address.toValue(), relay)

        fun assemble(
            kind: Int,
            pubKey: String,
            dTag: String,
            relay: NormalizedRelayUrl?,
        ) = assemble(Address.assemble(kind, pubKey, dTag), relay)
    }
}
