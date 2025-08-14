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
package com.vitorpamplona.quartz.nip65RelayList.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.utils.ensure

class AdvertisedRelayInfo(
    val relayUrl: NormalizedRelayUrl,
    val type: AdvertisedRelayType,
) {
    fun toTagArray() = assemble(relayUrl, type)

    companion object {
        const val TAG_NAME = "r"

        fun match(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun notMatch(tag: Array<String>) = !match(tag)

        @JvmStatic
        fun parse(tag: Array<String>): AdvertisedRelayInfo? {
            ensure(match(tag)) { return null }

            val normalizedUrl = RelayUrlNormalizer.normalizeOrNull(tag[1])

            ensure(normalizedUrl != null) { return null }

            val type =
                when (tag.getOrNull(2)) {
                    AdvertisedRelayType.READ.code -> AdvertisedRelayType.READ
                    AdvertisedRelayType.WRITE.code -> AdvertisedRelayType.WRITE
                    else -> AdvertisedRelayType.BOTH
                }

            return AdvertisedRelayInfo(normalizedUrl, type)
        }

        @JvmStatic
        fun parseRead(tag: Array<String>): String? {
            ensure(match(tag)) { return null }
            ensure(AdvertisedRelayType.isRead(tag.getOrNull(2))) { return null }

            return tag[1]
        }

        @JvmStatic
        fun parseWrite(tag: Array<String>): String? {
            ensure(match(tag)) { return null }
            ensure(AdvertisedRelayType.isWrite(tag.getOrNull(2))) { return null }

            return tag[1]
        }

        @JvmStatic
        fun parseReadNorm(tag: Array<String>): NormalizedRelayUrl? {
            ensure(tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()) { return null }

            if (tag.has(2)) {
                ensure(AdvertisedRelayType.isRead(tag[2])) { return null }
            }

            val relay = RelayUrlNormalizer.normalizeOrNull(tag[1])

            ensure(relay != null && !relay.isLocalHost()) { return null }

            return RelayUrlNormalizer.normalizeOrNull(tag[1])
        }

        @JvmStatic
        fun parseWriteNorm(tag: Array<String>): NormalizedRelayUrl? {
            ensure(tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()) { return null }

            if (tag.has(2)) {
                ensure(AdvertisedRelayType.isWrite(tag[2])) { return null }
            }

            val relay = RelayUrlNormalizer.normalizeOrNull(tag[1])

            ensure(relay != null && !relay.isLocalHost()) { return null }

            return relay
        }

        @JvmStatic
        fun assemble(
            relay: NormalizedRelayUrl,
            type: AdvertisedRelayType,
        ): Array<String> =
            if (type == AdvertisedRelayType.BOTH) {
                arrayOf(TAG_NAME, relay.url)
            } else {
                arrayOf(TAG_NAME, relay.url, type.code)
            }
    }
}

@Immutable
enum class AdvertisedRelayType(
    val code: String,
) {
    BOTH(""),
    READ("read"),
    WRITE("write"),
    ;

    fun isRead() = this == READ || this == BOTH

    fun isWrite() = this == WRITE || this == BOTH

    companion object {
        fun isRead(type: String?) = type == null || type.isBlank() || type == AdvertisedRelayType.READ.code

        fun isWrite(type: String?) = type == null || type.isBlank() || type == AdvertisedRelayType.WRITE.code
    }
}
