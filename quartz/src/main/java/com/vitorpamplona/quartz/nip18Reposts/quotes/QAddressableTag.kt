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
package com.vitorpamplona.quartz.nip18Reposts.quotes

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
data class QAddressableTag(
    val address: Address,
) : QTag {
    var relay: String? = null

    constructor(
        address: Address,
        relayHint: String?,
    ) : this(address) {
        this.relay = relayHint
    }

    constructor(
        kind: Int,
        pubKeyHex: HexKey,
        dTag: String,
        relayHint: String?,
    ) : this(Address(kind, pubKeyHex, dTag)) {
        this.relay = relayHint
    }

    fun countMemory(): Long =
        2 * pointerSizeInBytes +
            address.countMemory() +
            (relay?.bytesUsedInMemory() ?: 0)

    override fun toTagArray() = assemble(address, relay)

    companion object {
        const val TAG_SIZE = 2

        @JvmStatic
        fun parse(tag: Array<String>): QAddressableTag? {
            if (tag.size < TAG_SIZE || tag[0] != QTag.TAG_NAME) return null
            val address = Address.parse(tag[1]) ?: return null
            return QAddressableTag(address, tag.getOrNull(2))
        }

        @JvmStatic
        fun assemble(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String,
            relay: String?,
        ) = arrayOfNotNull(QTag.TAG_NAME, Address.assemble(kind, pubKeyHex, dTag), relay)

        @JvmStatic
        fun assemble(
            address: Address,
            relay: String?,
        ) = arrayOfNotNull(QTag.TAG_NAME, address.toValue(), relay)
    }
}
