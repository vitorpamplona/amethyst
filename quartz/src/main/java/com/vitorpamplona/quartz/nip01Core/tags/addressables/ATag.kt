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
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.match
import com.vitorpamplona.quartz.nip01Core.core.name
import com.vitorpamplona.quartz.nip01Core.core.value
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import com.vitorpamplona.quartz.utils.removeTrailingNullsAndEmptyOthers

@Immutable
data class ATag(
    val kind: Int,
    val pubKeyHex: String,
    val dTag: String,
) {
    var relay: String? = null

    constructor(address: Address) : this(address.kind, address.pubKeyHex, address.dTag)

    constructor(
        kind: Int,
        pubKeyHex: HexKey,
        dTag: String,
        relayHint: String?,
    ) : this(kind, pubKeyHex, dTag) {
        this.relay = relayHint
    }

    fun countMemory(): Long =
        5 * pointerSizeInBytes + // 7 fields, 4 bytes each reference (32bit)
            8L + // kind
            pubKeyHex.bytesUsedInMemory() +
            dTag.bytesUsedInMemory() +
            (relay?.bytesUsedInMemory() ?: 0)

    fun toTag() = assembleATagId(kind, pubKeyHex, dTag)

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
        ) = ATagParser.isTagged(tag, TAG_NAME, addressId)

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            address: ATag,
        ) = ATagParser.isTagged(tag, TAG_NAME, address.toTag())

        @JvmStatic
        fun isIn(
            tag: Array<String>,
            addressIds: Set<String>,
        ) = ATagParser.isIn(tag, TAG_NAME, addressIds)

        @JvmStatic
        fun isTaggedWithKind(
            tag: Array<String>,
            kind: String,
        ) = ATagParser.isTaggedWithKind(tag, TAG_NAME, kind)

        @JvmStatic
        fun assembleATagId(
            kind: Int,
            pubKey: HexKey,
            dTag: String,
        ) = ATagParser.assembleATagId(kind, pubKey, dTag)

        @JvmStatic
        fun parseIfOfKind(
            tag: Array<String>,
            kind: String,
        ) = ATagParser.parseIfOfKind(tag, TAG_NAME, kind)

        @JvmStatic
        fun parseIfIsIn(
            tag: Array<String>,
            addresses: Set<String>,
        ) = ATagParser.parseIfIsIn(tag, TAG_NAME, addresses)

        @JvmStatic
        fun parse(
            aTagId: String,
            relay: String?,
        ) = ATagParser.parse(aTagId, relay)

        @JvmStatic
        fun parse(tag: Array<String>) = ATagParser.parse(TAG_NAME, tag)

        @JvmStatic
        fun parseValidAddress(tag: Array<String>) = ATagParser.parseValidAddress(TAG_NAME, tag)

        @JvmStatic
        fun parseAddress(tag: Array<String>) = ATagParser.parseAddress(TAG_NAME, tag)

        @JvmStatic
        fun assemble(
            aTagId: HexKey,
            relay: String?,
        ) = ATagParser.assemble(TAG_NAME, aTagId, relay)

        @JvmStatic
        fun assemble(
            kind: Int,
            pubKey: String,
            dTag: String,
            relay: String?,
        ) = ATagParser.assemble(TAG_NAME, kind, pubKey, dTag, relay)
    }
}
