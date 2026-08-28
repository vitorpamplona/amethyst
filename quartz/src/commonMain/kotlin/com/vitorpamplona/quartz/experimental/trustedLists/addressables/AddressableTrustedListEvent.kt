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
package com.vitorpamplona.quartz.experimental.trustedLists.addressables

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.TrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.addressables.tags.AddressMemberTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastMapNotNullDense
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Trusted List whose members are **addressable events**, by a-coordinate --
 * the list analog of the NIP-85 kind-30384 addressable assertion (30384 + 10).
 *
 * Example: the tag-applicability lists, whose members are tag coordinates.
 */
@Immutable
class AddressableTrustedListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : TrustedListEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    PubKeyHintProvider {
    override fun members(): List<AddressMemberTag> = tags.members()

    override fun isMemberTag(tag: Tag) = AddressMemberTag.isTag(tag)

    override fun memberValueOf(tag: Tag) = AddressMemberTag.parseAddressId(tag)

    override fun addressHints() = tags.mapNotNull(AddressMemberTag::parseAsHint)

    override fun linkedAddressIds() = tags.fastMapNotNullDense(AddressMemberTag::parseAddressId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    /** What this list is about, for relay-side discovery. Never its members. */
    fun aboutPubKeys() = tags.aboutPubKeys()

    companion object {
        const val KIND = 30394

        fun build(
            listId: String,
            members: List<AddressMemberTag> = emptyList(),
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AddressableTrustedListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            dTag(listId)
            // metadata first: it keeps the header tags ahead of a membership
            // that can run to thousands of entries
            initializer()
            members(members)
        }
    }
}
