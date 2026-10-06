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
package com.vitorpamplona.quartz.experimental.trustedLists.users

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.TrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.tags.PubKeyMemberTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Trusted List whose members are **pubkeys** -- the list analog of the
 * NIP-85 kind-30382 contact card (30382 + 10).
 *
 * Example: the pubkeys trusted-tagged with a given tag under one observer.
 */
@Immutable
class UserTrustedListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : TrustedListEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    AddressHintProvider {
    override fun members(): List<PubKeyMemberTag> = tags.members()

    override fun isMemberTag(tag: Tag) = PubKeyMemberTag.isTag(tag)

    override fun memberValueOf(tag: Tag) = PubKeyMemberTag.parseKey(tag)

    override fun pubKeyHints() = tags.mapNotNull(PubKeyMemberTag::parseAsHint)

    // [memberValues] plus the provenance keys. Dense: nearly every tag is a member, so presize, with
    // room for the two provenance keys, and read each tag through [memberValueOf] instead of copying.
    override fun linkedPubKeys(): List<HexKey> {
        val keys = ArrayList<HexKey>(tags.size + 2)
        for (tag in tags) memberValueOf(tag)?.let(keys::add)
        addProvenancePubKeys(keys)
        return keys
    }

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = aboutAddressIds()

    /** What this list is about, for relay-side discovery. Never its members. */
    fun aboutAddresses() = tags.aboutAddresses()

    /** [aboutAddresses] as shape-checked address ids, in tag order. */
    fun aboutAddressIds(): List<String> = tags.aboutAddressIds()

    companion object {
        const val KIND = 30392

        fun build(
            listId: String,
            members: List<PubKeyMemberTag> = emptyList(),
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<UserTrustedListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            dTag(listId)
            // metadata first: it keeps the header tags ahead of a membership
            // that can run to thousands of entries
            initializer()
            members(members)
        }
    }
}
