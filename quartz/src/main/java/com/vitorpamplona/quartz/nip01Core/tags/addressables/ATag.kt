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
package com.vitorpamplona.quartz.nip01Core.tags.addressables

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.match
import com.vitorpamplona.quartz.nip01Core.core.name
import com.vitorpamplona.quartz.nip01Core.core.value
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import com.vitorpamplona.quartz.utils.removeTrailingNullsAndEmptyOthers

@Immutable
data class ATag(
    val kind: Int,
    val pubKeyHex: String,
    val dTag: String,
    val relay: String? = null,
) {
    constructor(address: Address, relayHint: String? = null) : this(address.kind, address.pubKeyHex, address.dTag, relayHint)

    fun countMemory(): Long =
        5 * pointerSizeInBytes + // 7 fields, 4 bytes each reference (32bit)
            8L + // kind
            pubKeyHex.bytesUsedInMemory() +
            dTag.bytesUsedInMemory() +
            (relay?.bytesUsedInMemory() ?: 0)

    fun toTag() = Address.assemble(kind, pubKeyHex, dTag)

    fun toATagArray() = removeTrailingNullsAndEmptyOthers(TAG_NAME, toTag(), relay)

    fun toQTagArray() = removeTrailingNullsAndEmptyOthers("q", toTag(), relay)

    companion object {
        const val TAG_NAME = "a"
        const val TAG_SIZE = 2

        @JvmStatic
        fun isSameAddress(
            tag1: Array<String>,
            tag2: Array<String>,
        ) = tag1.match(tag2.name(), tag2.value(), TAG_SIZE)

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            addressId: String,
        ) = tag.size >= TAG_SIZE && tag[0] == TAG_NAME && tag[1] == addressId

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            address: ATag,
        ) = tag.size >= TAG_SIZE && tag[0] == TAG_NAME && tag[1] == address.toTag()

        @JvmStatic
        fun isIn(
            tag: Array<String>,
            addressIds: Set<String>,
        ) = tag.size >= TAG_SIZE && tag[0] == TAG_NAME && tag[1] in addressIds

        @JvmStatic
        fun isTaggedWithKind(
            tag: Array<String>,
            kind: String,
        ) = tag.size >= TAG_SIZE && tag[0] == TAG_NAME && Address.isOfKind(tag[1], kind)

        @JvmStatic
        fun parseIfOfKind(
            tag: Array<String>,
            kind: String,
        ) = if (isTaggedWithKind(tag, kind)) parse(tag[1], tag.getOrNull(2)) else null

        @JvmStatic
        fun parseIfIsIn(
            tag: Array<String>,
            addresses: Set<String>,
        ) = if (isIn(tag, addresses)) parse(tag[1], tag.getOrNull(2)) else null

        @JvmStatic
        fun parse(
            aTagId: String,
            relay: String?,
        ) = Address.parse(aTagId)?.let { ATag(it.kind, it.pubKeyHex, it.dTag, relay) }

        @JvmStatic
        fun parse(tag: Array<String>): ATag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return parse(tag[1], tag.getOrNull(2))
        }

        @JvmStatic
        fun parseValidAddress(tag: Array<String>): String? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return Address.parse(tag[1])?.toValue()
        }

        @JvmStatic
        fun parseAddress(tag: Array<String>): Address? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return Address.parse(tag[1])
        }

        @JvmStatic
        fun parseAddressId(tag: Array<String>): String? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return tag[1]
        }

        @JvmStatic
        fun parseAsHint(tag: Array<String>): AddressHint? {
            if (tag.size < 3 || tag[0] != TAG_NAME || !tag[1].contains(':') || tag[2].isEmpty()) return null
            return AddressHint(tag[1], tag[2])
        }

        @JvmStatic
        fun assemble(
            aTagId: HexKey,
            relay: String?,
        ) = arrayOfNotNull(TAG_NAME, aTagId, relay)

        @JvmStatic
        fun assemble(
            kind: Int,
            pubKey: String,
            dTag: String,
            relay: String?,
        ) = assemble(Address.assemble(kind, pubKey, dTag), relay)
    }
}
