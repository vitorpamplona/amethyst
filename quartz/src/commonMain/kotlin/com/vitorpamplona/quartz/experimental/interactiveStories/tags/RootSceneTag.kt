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
package com.vitorpamplona.quartz.experimental.interactiveStories.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
data class RootSceneTag(
    val kind: Int,
    val pubKeyHex: String,
    val dTag: String,
) {
    var relay: NormalizedRelayUrl? = null

    constructor(
        kind: Int,
        pubKeyHex: HexKey,
        dTag: String,
        relayHint: NormalizedRelayUrl?,
    ) : this(kind, pubKeyHex, dTag) {
        this.relay = relayHint
    }

    fun toTag() = assembleATagId(kind, pubKeyHex, dTag)

    fun toTagArray() = assemble(kind, pubKeyHex, dTag, relay)

    companion object {
        const val TAG_NAME = "A"

        fun assembleATagId(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String,
        ) = Address.assemble(kind, pubKeyHex, dTag)

        fun parse(tag: Array<String>): RootSceneTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val address = Address.parse(tag[1]) ?: return null
            val hint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }
            return RootSceneTag(address.kind, address.pubKeyHex, address.dTag, hint)
        }

        fun assemble(
            aTagId: HexKey,
            relay: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, aTagId, relay?.url)

        fun assemble(
            kind: Int,
            pubKeyHex: String,
            dTag: String,
            relay: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, assembleATagId(kind, pubKeyHex, dTag), relay?.url)
    }
}
