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
package com.vitorpamplona.quartz.nip72ModCommunities.follow.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.value
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.utils.TagParsingUtils
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.ensure
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

class CommunityTag(
    val address: Address,
    val relayHint: NormalizedRelayUrl? = null,
) {
    fun countMemory(): Long = 2 * pointerSizeInBytes + address.countMemory() + (relayHint?.url?.bytesUsedInMemory() ?: 0)

    fun toTag() = Address.assemble(address.kind, address.pubKeyHex, address.dTag)

    fun toTagArray() = assemble(address, relayHint)

    fun toTagIdOnly() = assemble(address, null)

    companion object {
        const val TAG_NAME = "a"

        @JvmStatic
        fun isTagged(tag: Array<String>) = TagParsingUtils.matchesTag(tag, TAG_NAME)

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            addressId: String,
        ) = TagParsingUtils.isTaggedWith(tag, TAG_NAME, addressId)

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            address: CommunityTag,
        ) = TagParsingUtils.isTaggedWith(tag, TAG_NAME, address.toTag())

        @JvmStatic
        fun isIn(
            tag: Array<String>,
            addressIds: Set<String>,
        ) = TagParsingUtils.isTaggedWithAny(tag, TAG_NAME, addressIds)

        @JvmStatic
        fun isTaggedWithKind(
            tag: Array<String>,
            kind: String,
        ) = TagParsingUtils.validateBasicTag(tag, TAG_NAME) && Address.isOfKind(tag.value(), kind)

        @JvmStatic
        fun parse(
            aTagId: String,
            relay: String?,
        ) = Address.parse(aTagId)?.let {
            CommunityTag(it, relay?.let { RelayUrlNormalizer.normalizeOrNull(it) })
        }

        @JvmStatic
        fun parse(tag: Array<String>): CommunityTag? {
            if (!TagParsingUtils.validateBasicTag(tag, TAG_NAME)) return null
            return parse(tag.value(), tag.getOrNull(2))
        }

        @JvmStatic
        fun parseValidAddress(tag: Array<String>): String? {
            if (!TagParsingUtils.validateBasicTag(tag, TAG_NAME)) return null
            return Address.parse(tag.value())?.toValue()
        }

        @JvmStatic
        fun parseAddress(tag: Array<String>): Address? {
            if (!TagParsingUtils.validateBasicTag(tag, TAG_NAME)) return null
            return Address.parse(tag.value())
        }

        @JvmStatic
        fun parseAddressId(tag: Array<String>): String? {
            if (!TagParsingUtils.validateBasicTag(tag, TAG_NAME)) return null
            return tag.value()
        }

        @JvmStatic
        fun parseAsHint(tag: Array<String>): AddressHint? {
            ensure(tag.has(2)) { return null }
            if (!TagParsingUtils.validateBasicTag(tag, TAG_NAME)) return null
            ensure(tag.value().contains(':')) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(relayHint != null) { return null }

            return AddressHint(tag.value(), relayHint)
        }

        @JvmStatic
        fun assemble(
            aTagId: HexKey,
            relay: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, aTagId, relay?.url)

        @JvmStatic
        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, address.toValue(), relay?.url)

        @JvmStatic
        fun assemble(
            kind: Int,
            pubKey: String,
            dTag: String,
            relay: NormalizedRelayUrl?,
        ) = assemble(Address.assemble(kind, pubKey, dTag), relay)
    }
}
