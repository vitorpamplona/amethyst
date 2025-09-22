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
package com.vitorpamplona.quartz.nip89AppHandlers.recommendation.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
class RecommendationTag(
    val address: Address,
    val relay: NormalizedRelayUrl? = null,
    val platform: String? = null,
) {
    fun toTagArray() = assemble(address, relay, platform)

    companion object {
        const val TAG_NAME = "a"

        fun match(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): RecommendationTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val address = Address.parse(tag[1]) ?: return null
            val relayHint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }
            return RecommendationTag(address, relayHint, tag.getOrNull(3))
        }

        fun parseAddressId(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Array<String>): AddressHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == ATag.Companion.TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[1].contains(':')) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2]) ?: return null

            return AddressHint(tag[1], relayHint)
        }

        fun assemble(
            addressId: String,
            relay: NormalizedRelayUrl?,
            platform: String?,
        ) = arrayOfNotNull(TAG_NAME, addressId, relay?.url, platform)

        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl?,
            platform: String?,
        ) = arrayOfNotNull(TAG_NAME, address.toValue(), relay?.url, platform)
    }
}
