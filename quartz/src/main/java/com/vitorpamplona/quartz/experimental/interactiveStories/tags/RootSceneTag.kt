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
package com.vitorpamplona.quartz.experimental.interactiveStories.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.ensure
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import com.vitorpamplona.quartz.utils.removeTrailingNullsAndEmptyOthers

@Immutable
data class RootSceneTag(
    val kind: Int,
    val pubKeyHex: String,
    val dTag: String,
) {
    var relay: String? = null

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

    fun toTagArray() = removeTrailingNullsAndEmptyOthers(TAG_NAME, toTag(), relay)

    companion object {
        const val TAG_NAME = "A"
        const val TAG_SIZE = 2

        fun assembleATagId(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String,
        ) = Address.assemble(kind, pubKeyHex, dTag)

        @JvmStatic
        fun parse(tag: Array<String>): RootSceneTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val address = Address.parse(tag[1]) ?: return null
            return RootSceneTag(address.kind, address.pubKeyHex, address.dTag, tag.getOrNull(2))
        }

        @JvmStatic
        fun assemble(
            aTagId: HexKey,
            relay: String?,
        ) = arrayOfNotNull(TAG_NAME, aTagId, relay)

        @JvmStatic
        fun assemble(
            kind: Int,
            pubKeyHex: String,
            dTag: String,
            relay: String?,
        ) = arrayOfNotNull(TAG_NAME, assembleATagId(kind, pubKeyHex, dTag), relay)
    }
}
