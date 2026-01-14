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
package com.vitorpamplona.quartz.nip01Core.tags.aTag

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
data class ATag(
    val kind: Int,
    val pubKeyHex: HexKey,
    val dTag: String = "",
    val relay: NormalizedRelayUrl? = null,
) {
    constructor(address: Address, relayHint: NormalizedRelayUrl? = null) : this(address.kind, address.pubKeyHex, address.dTag, relayHint)

    fun toTag() = AddressSerializer.assemble(kind, pubKeyHex, dTag)

    fun toATagArray() = assemble(toTag(), relay)

    companion object {
        const val TAG_NAME = "a"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun isSameAddress(
            tag1: Array<String>,
            tag2: Array<String>,
        ): Boolean {
            ensure(tag1.has(1)) { return false }
            ensure(tag2.has(1)) { return false }
            ensure(tag1[0] == tag2[0]) { return false }
            ensure(tag1[1] == tag2[1]) { return false }
            return true
        }

        fun isTagged(
            tag: Array<String>,
            addressId: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == addressId

        fun isTagged(
            tag: Array<String>,
            address: ATag,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == address.toTag()

        fun isIn(
            tag: Array<String>,
            addressIds: Set<String>,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] in addressIds

        fun isTaggedWithKind(
            tag: Array<String>,
            kind: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && AddressSerializer.isOfKind(tag[1], kind)

        fun parseIfOfKind(
            tag: Array<String>,
            kind: String,
        ) = if (isTaggedWithKind(tag, kind)) parse(tag[1], tag.getOrNull(2)) else null

        fun parseIfIsIn(
            tag: Array<String>,
            addresses: Set<String>,
        ) = if (isIn(tag, addresses)) parse(tag[1], tag.getOrNull(2)) else null

        fun parse(
            aTagId: String,
            relay: String?,
        ) = AddressSerializer.parse(aTagId)?.let {
            ATag(
                it.kind,
                it.pubKeyHex,
                it.dTag,
                relay?.let { RelayUrlNormalizer.normalizeOrNull(it) },
            )
        }

        fun parse(tag: Array<String>): ATag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return parse(tag[1], tag.getOrNull(2))
        }

        fun parseValidAddress(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return AddressSerializer.parse(tag[1])?.toValue()
        }

        fun parseAddress(tag: Array<String>): Address? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return AddressSerializer.parse(tag[1])
        }

        fun parseAddressId(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Array<String>): AddressHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[1].contains(':')) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(relayHint != null) { return null }

            return AddressHint(tag[1], relayHint)
        }

        fun assemble(
            aTagId: HexKey,
            relay: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, aTagId, relay?.url)

        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, address.toValue(), relay?.url)

        fun assemble(
            kind: Int,
            pubKey: String,
            dTag: String,
            relay: NormalizedRelayUrl?,
        ) = assemble(AddressSerializer.assemble(kind, pubKey, dTag), relay)
    }
}

fun <T : Event> EventHintBundle<T>.toATag() = ATag(event.kind, event.pubKey, event.dTag(), relay)
