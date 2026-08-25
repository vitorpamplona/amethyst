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
package com.vitorpamplona.quartz.experimental.trustedLists.addressables.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.tags.MemberTagFields
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TrustedListMemberTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * An addressable-event member of a kind-30394 Trusted List:
 * `["a", <kind:pubkey:d>, <relay-hint>, <score>]`.
 *
 * A-coordinate members belong on 30394 and never on 30393: publishing them on
 * the event-id kind would tell a conformant reader "these are event ids" when
 * they are coordinates, breaking kind-keyed dispatch.
 */
@Immutable
data class AddressMemberTag(
    val address: String,
    val relayHint: NormalizedRelayUrl? = null,
    override val score: Int? = null,
) : TrustedListMemberTag {
    override val memberValue: String get() = address

    fun toAddress(): Address? = AddressSerializer.parse(address)

    fun toTagArray() = assemble(address, relayHint, score)

    companion object {
        const val TAG_NAME = "a"

        fun isTag(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun isTagged(
            tag: Tag,
            address: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == address

        fun parse(tag: Tag): AddressMemberTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            return AddressMemberTag(tag[1], MemberTagFields.relayHint(tag), MemberTagFields.score(tag))
        }

        fun parseAddressId(tag: Tag): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun parseAddress(tag: Tag): Address? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return AddressSerializer.parse(tag[1])
        }

        fun parseAsHint(tag: Tag): AddressHint? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            // only index a value that is actually a coordinate, as ATag does
            ensure(tag[1].contains(':')) { return null }

            val hint = MemberTagFields.relayHint(tag)

            ensure(hint != null) { return null }

            return AddressHint(tag[1], hint)
        }

        fun assemble(
            address: String,
            relayHint: NormalizedRelayUrl?,
            score: Int?,
        ) = arrayOfNotNull(TAG_NAME, address, relayHint?.url, score?.toString())

        fun assemble(member: AddressMemberTag) = assemble(member.address, member.relayHint, member.score)

        fun assemble(members: List<AddressMemberTag>) = members.map { assemble(it) }
    }
}
