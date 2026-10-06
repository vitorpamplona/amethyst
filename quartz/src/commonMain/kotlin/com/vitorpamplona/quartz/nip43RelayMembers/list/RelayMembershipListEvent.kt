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
package com.vitorpamplona.quartz.nip43RelayMembers.list

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip43RelayMembers.list.tags.RelayMember
import com.vitorpamplona.quartz.nip70ProtectedEvts.protect
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class RelayMembershipListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    /** `member` tags carry no relay slot, so there is nothing to hint. */
    override fun pubKeyHints() = emptyList<PubKeyHint>()

    // Deliberately links no one, although every member is listed. The list arrives from
    // the membership relay itself, and a provider's linked keys are recorded as reachable on the
    // relay the event came from: that would advertise a (often private) relay as a hint for
    // every member, and broadcasts would fall back to it for anyone without an inbox list. It
    // would also cost O(members) on every relay copy. Read the list with members().
    override fun linkedPubKeys() = emptyList<HexKey>()

    fun members() = tags.members()

    /** Members with the role ids (NIP-43 kind 33534 `d` tags) the relay assigned to each. */
    fun membersWithRoles() = tags.membersWithRoles()

    companion object {
        const val KIND = 13534

        fun build(
            members: List<HexKey>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayMembershipListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            protect()
            members(members)
            initializer()
        }

        /** Like [build], but each member may carry its assigned role ids. */
        fun buildWithRoles(
            members: List<RelayMember>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayMembershipListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            protect()
            membersWithRoles(members)
            initializer()
        }
    }
}
