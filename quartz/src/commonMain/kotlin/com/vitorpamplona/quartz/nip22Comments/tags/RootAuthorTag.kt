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
package com.vitorpamplona.quartz.nip22Comments.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.PUBKEY_LENGTH
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
data class RootAuthorTag(
    override val pubKey: HexKey,
    override val relayHint: NormalizedRelayUrl? = null,
) : PubKeyReferenceTag {
    fun toTagArray() = assemble(pubKey, relayHint)

    companion object {
        const val TAG_NAME = "P"

        fun match(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Tag): ReplyAuthorTag? {
            if (tag.size < 2 || tag[0] != TAG_NAME || tag[1].length != PUBKEY_LENGTH) return null

            val relayHint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return ReplyAuthorTag(tag[1], relayHint)
        }

        fun parse3(tag: Tag): ReplyAuthorTag? {
            ensure(tag.size >= 2) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == PUBKEY_LENGTH) { return null }

            val relayHint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return ReplyAuthorTag(tag[1], relayHint)
        }

        fun parseKey(tag: Tag): HexKey? {
            if (tag.size < 2 || tag[0] != TAG_NAME || tag[1].length != PUBKEY_LENGTH) return null
            return tag[1]
        }

        fun parseAsHint(tag: Array<String>): PubKeyHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(relayHint != null) { return null }

            return PubKeyHint(tag[1], relayHint)
        }

        fun assemble(
            pubkey: HexKey,
            relayHint: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, pubkey, relayHint?.url)
    }
}
