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
package com.vitorpamplona.quartz.nip89AppHandlers.recommendation.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.match
import com.vitorpamplona.quartz.nip01Core.core.valueIfMatches
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.utils.arrayOfNotNull

@Immutable
class RecommendationTag(
    val address: Address,
    val relay: String? = null,
    val platform: String? = null,
) {
    fun toTagArray() = assemble(address, relay, platform)

    companion object {
        const val TAG_NAME = "a"
        const val TAG_SIZE = 2

        @JvmStatic
        fun match(tag: Tag) = tag.match(TAG_NAME, TAG_SIZE)

        @JvmStatic
        fun parse(tag: Array<String>): RecommendationTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            val address = Address.parse(tag[1]) ?: return null
            return RecommendationTag(address, tag.getOrNull(2), tag.getOrNull(3))
        }

        @JvmStatic
        fun parseAddress(tag: Array<String>) = tag.valueIfMatches(TAG_NAME, TAG_SIZE)

        @JvmStatic
        fun assemble(
            addressId: String,
            relay: String?,
            platform: String?,
        ) = arrayOfNotNull(TAG_NAME, addressId, relay, platform)

        @JvmStatic
        fun assemble(
            address: Address,
            relay: String?,
            platform: String?,
        ) = arrayOfNotNull(TAG_NAME, address.toValue(), relay, platform)
    }
}
