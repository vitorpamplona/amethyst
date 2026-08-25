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
package com.vitorpamplona.quartz.experimental.trustedLists.events.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.tags.MemberTagFields
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TrustedListMemberTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * An event member of a kind-30393 Trusted List:
 * `["e", <event-id>, <relay-hint>, <score>]`.
 *
 * Index 3 is the score across the whole family, not a NIP-10 marker: these
 * lists enumerate membership, they do not thread.
 */
@Immutable
data class EventMemberTag(
    val eventId: HexKey,
    val relayHint: NormalizedRelayUrl? = null,
    override val score: Int? = null,
) : TrustedListMemberTag {
    override val memberValue: String get() = eventId

    fun toTagArray() = assemble(eventId, relayHint, score)

    companion object {
        const val TAG_NAME = "e"

        fun isTag(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].length == 64

        fun isTagged(
            tag: Tag,
            eventId: HexKey,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == eventId

        fun parse(tag: Tag): EventMemberTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            return EventMemberTag(tag[1], MemberTagFields.relayHint(tag), MemberTagFields.score(tag))
        }

        fun parseId(tag: Tag): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Tag): EventIdHint? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = MemberTagFields.relayHint(tag)

            ensure(hint != null) { return null }

            return EventIdHint(tag[1], hint)
        }

        fun assemble(
            eventId: HexKey,
            relayHint: NormalizedRelayUrl?,
            score: Int?,
        ) = arrayOfNotNull(TAG_NAME, eventId, relayHint?.url, score?.toString())

        fun assemble(member: EventMemberTag) = assemble(member.eventId, member.relayHint, member.score)

        fun assemble(members: List<EventMemberTag>) = members.map { assemble(it) }
    }
}
