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
package com.vitorpamplona.quartz.concord.cord05Invites.bundle

import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VskTag
import com.vitorpamplona.quartz.concord.cord04Roles.control.vsk
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The public invite bundle (CORD-05): a kind-33301 **addressable** event whose
 * content is a [com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite]
 * NIP-44-encrypted under the bundle key derived from the link's 16-byte unlock
 * token. Tagged `["d",""]` (so `link_signer` has exactly one live bundle) and
 * `["vsk","6"]` ([ControlEntityKind.INVITE_LIVE]).
 *
 * A relay that indexes the naddr never holds the token, so it can never open the
 * bundle. Minting/parsing/validation live in
 * [com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteBundle]; this is the
 * wire event it builds and that [com.vitorpamplona.quartz.utils.EventFactory] parses.
 */
class ConcordInviteBundleEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    /** The versioned sub-kind marker (`vsk`), expected to be [ControlEntityKind.INVITE_LIVE]. */
    fun versionedSubKind() = tags.vsk()

    companion object {
        const val KIND = 33301

        /** Builds the addressable bundle template carrying the already-encrypted [encryptedInvite]. */
        fun build(
            encryptedInvite: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ConcordInviteBundleEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, encryptedInvite, createdAt) {
            dTag("")
            addUnique(VskTag.assemble(ControlEntityKind.INVITE_LIVE))
            initializer()
        }
    }
}
