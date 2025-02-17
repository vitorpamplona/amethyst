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

import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isNotName
import com.vitorpamplona.quartz.nip01Core.core.match
import com.vitorpamplona.quartz.utils.arrayOfNotNull

class ATagParser {
    companion object {
        const val TAG_SIZE = 2

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            tagName: String,
            addressId: String,
        ) = tag.match(tagName, addressId, TAG_SIZE)

        @JvmStatic
        fun isIn(
            tag: Array<String>,
            tagName: String,
            addressIds: Set<String>,
        ) = tag.match(tagName, addressIds, TAG_SIZE)

        @JvmStatic
        fun isTaggedWithKind(
            tag: Array<String>,
            tagName: String,
            kind: String,
        ) = tag.match(tagName, TAG_SIZE) && Address.isOfKind(tag[1], kind)

        @JvmStatic
        fun assembleATagId(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String,
        ) = Address.assemble(kind, pubKeyHex, dTag)

        @JvmStatic
        fun parseIfOfKind(
            tag: Array<String>,
            tagName: String,
            kind: String,
        ) = if (isTaggedWithKind(tag, tagName, kind)) parse(tag[1], tag.getOrNull(2)) else null

        @JvmStatic
        fun parseIfIsIn(
            tag: Array<String>,
            tagName: String,
            addresses: Set<String>,
        ) = if (isIn(tag, tagName, addresses)) parse(tag[1], tag.getOrNull(2)) else null

        @JvmStatic
        fun parse(
            aTagId: String,
            relay: String?,
        ) = Address.parse(aTagId)?.let { ATag(it.kind, it.pubKeyHex, it.dTag, relay) }

        @JvmStatic
        fun parse(
            tagName: String,
            tag: Array<String>,
        ): ATag? {
            if (tag.isNotName(tagName, TAG_SIZE)) return null
            return parse(tag[1], tag.getOrNull(2))
        }

        @JvmStatic
        fun parseValidAddress(
            tagName: String,
            tag: Array<String>,
        ): String? {
            if (tag.isNotName(tagName, TAG_SIZE)) return null
            return Address.parse(tag[1])?.toValue()
        }

        @JvmStatic
        fun parseAddress(
            tagName: String,
            tag: Array<String>,
        ): Address? {
            if (tag.isNotName(tagName, TAG_SIZE)) return null
            return Address.parse(tag[1])
        }

        @JvmStatic
        fun parseAddressId(
            tagName: String,
            tag: Array<String>,
        ): String? {
            if (tag.isNotName(tagName, TAG_SIZE)) return null
            return tag[1]
        }

        @JvmStatic
        fun assemble(
            tagName: String,
            aTagId: HexKey,
            relay: String?,
        ) = arrayOfNotNull(tagName, aTagId, relay)

        @JvmStatic
        fun assemble(
            tagName: String,
            kind: Int,
            pubKeyHex: String,
            dTag: String,
            relay: String?,
        ) = arrayOfNotNull(tagName, assembleATagId(kind, pubKeyHex, dTag), relay)
    }
}
