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
package com.vitorpamplona.quartz.nip18Reposts.quotes

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.utils.ensure

interface QTag {
    fun toTagArray(): Array<String>

    companion object {
        const val TAG_NAME = "q"

        @JvmStatic
        fun parse(tag: Array<String>): QTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            val relayHint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return if (tag[1].length == 64) {
                QEventTag(tag[1], relayHint, tag.getOrNull(3))
            } else {
                val address = Address.parse(tag[1]) ?: return null
                QAddressableTag(address, relayHint)
            }
        }

        @JvmStatic
        fun parseKey(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return tag[1]
        }

        @JvmStatic
        fun parseEventAsHint(tag: Array<String>): EventIdHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(relayHint != null) { return null }

            return EventIdHint(tag[1], relayHint)
        }

        @JvmStatic
        fun parseAddressAsHint(tag: Array<String>): AddressHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length != 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }
            ensure(!tag[1].contains(':')) { return null }

            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(relayHint != null) { return null }

            return AddressHint(tag[1], relayHint)
        }
    }
}
