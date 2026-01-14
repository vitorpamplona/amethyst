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
package com.vitorpamplona.quartz.nip01Core.tags.people

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
data class PTag(
    override val pubKey: HexKey,
    override val relayHint: NormalizedRelayUrl? = null,
) : PubKeyReferenceTag {
    fun toNProfile(): String = NProfile.create(pubKey, relayHint?.let { listOf(it) } ?: emptyList())

    fun toNPub(): String = pubKey.hexToByteArray().toNpub()

    fun toTagArray() = assemble(pubKey, relayHint)

    companion object {
        const val TAG_NAME = "p"

        fun isTagged(
            tag: Array<String>,
            key: HexKey,
        ): Boolean = tag.has(1) && tag[0] == TAG_NAME && tag[1] == key

        fun parse(tag: Tag): PTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = pickRelayHint(tag)

            return PTag(tag[1], hint)
        }

        private fun pickRelayHint(tag: Array<String>): NormalizedRelayUrl? {
            if (tag.has(2) && tag[2].length > 7 && RelayUrlNormalizer.isRelayUrl(tag[2])) return RelayUrlNormalizer.normalizeOrNull(tag[2])
            return null
        }

        fun parseKey(tag: Array<String>): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Array<String>): PubKeyHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val hint = pickRelayHint(tag)

            ensure(hint != null) { return null }

            return PubKeyHint(tag[1], hint)
        }

        fun assemble(
            pubkey: HexKey,
            relayHint: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, pubkey, relayHint?.url)
    }
}

fun <T : Event> EventHintBundle<T>.toPTag() = PTag(event.pubKey, authorHomeRelay)
