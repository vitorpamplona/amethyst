/*
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
package com.vitorpamplona.quartz.experimental.trustedLists.users.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.tags.MemberTagFields
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TrustedListMemberTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * A pubkey member of a kind-30392 Trusted List:
 * `["p", <pubkey>, <relay-hint>, <score>]`.
 *
 * The score sits at index 3 across the whole family, so publishers that have a
 * score but no relay hint pad index 2 with an empty string.
 */
@Immutable
data class PubKeyMemberTag(
    override val pubKey: HexKey,
    override val relayHint: NormalizedRelayUrl? = null,
    override val score: Int? = null,
) : PubKeyReferenceTag,
    TrustedListMemberTag {
    override val memberValue: String get() = pubKey

    fun toTagArray() = assemble(pubKey, relayHint, score)

    companion object {
        const val TAG_NAME = "p"

        fun isTag(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].length == 64

        fun isTagged(
            tag: Tag,
            pubKey: HexKey,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == pubKey

        fun parse(tag: Tag): PubKeyMemberTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            return PubKeyMemberTag(tag[1], MemberTagFields.relayHint(tag), MemberTagFields.score(tag))
        }

        fun parseKey(tag: Tag): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Tag): PubKeyHint? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = MemberTagFields.relayHint(tag)

            ensure(hint != null) { return null }

            return PubKeyHint(tag[1], hint)
        }

        fun assemble(
            pubKey: HexKey,
            relayHint: NormalizedRelayUrl?,
            score: Int?,
        ) = arrayOfNotNull(TAG_NAME, pubKey, relayHint?.url, score?.toString())

        fun assemble(member: PubKeyMemberTag) = assemble(member.pubKey, member.relayHint, member.score)

        fun assemble(members: List<PubKeyMemberTag>) = members.map { assemble(it) }
    }
}
