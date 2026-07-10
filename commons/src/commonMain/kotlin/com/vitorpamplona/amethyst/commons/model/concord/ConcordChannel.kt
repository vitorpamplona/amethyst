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
package com.vitorpamplona.amethyst.commons.model.concord

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * A Concord channel — one encrypted chat room inside a community — as a
 * [Channel] the shared chat UI can render, mirroring NIP-29's
 * [com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel].
 *
 * The key difference: a Concord channel has no single relay-signed metadata
 * event. Its name/flags come from the community's **folded Control Plane**, its
 * decrypted messages are fed in by a subscription that holds the channel key, and
 * it is addressed by the derived plane pubkey rather than a host relay — so
 * [relays] is the *community's* relay set (a channel may be mirrored on several),
 * not a single host. Membership derives from the owner-rooted authority resolver.
 */
@Stable
class ConcordChannel(
    val channelId: ConcordChannelId,
) : Channel() {
    /** Channel display name from the folded ChannelMetadata, when known. */
    var channelName: String? = null
        private set

    var isVoice: Boolean = false
        private set

    var isPrivate: Boolean = false
        private set

    /** The parent community's display name, from its folded metadata. */
    var communityName: String? = null
        private set

    /** The parent community's icon URL, from its folded metadata (null if unset). */
    var communityIcon: String? = null
        private set

    /** The community's bootstrap relays — a channel plane may be mirrored on all of them. */
    var communityRelays: Set<NormalizedRelayUrl> = emptySet()
        private set

    /** This account's standing in the community (from the authority resolver + banlist). */
    var membership: ConcordMembership = ConcordMembership.MEMBER
        private set

    /**
     * Refresh this channel's metadata from a freshly-folded community [state] plus
     * the community's [relays] and this account's [myPubKey]. Cheap and idempotent
     * — called whenever the Control Plane re-folds.
     */
    fun updateFrom(
        state: ConcordCommunityState,
        relays: Set<NormalizedRelayUrl>,
        myPubKey: HexKey,
    ) {
        state.channels[channelId.channelId]?.definition?.let {
            channelName = it.name
            isVoice = it.voice
            isPrivate = it.private
        }
        communityName = state.metadata?.name
        communityIcon = state.metadata?.icon
        communityRelays = relays
        membership = ConcordMembership.of(state.authority, myPubKey)
    }

    /** A Concord channel is reachable on any of its community's relays. */
    override fun relays(): Set<NormalizedRelayUrl> = communityRelays

    override fun toBestDisplayName(): String = channelName ?: channelId.channelId

    fun canPost(): Boolean = membership.isMember()

    // Synthetic note representing this channel in the Messages list before any
    // message has loaded (so a just-joined channel appears immediately). Mirrors
    // RelayGroupChannel.placeholderNote().
    private val placeholderLock = KmpLock()
    private var cachedPlaceholder: Note? = null

    fun placeholderNote(): Note =
        placeholderLock.withLock {
            cachedPlaceholder ?: Note(placeholderIdHex(channelId)).apply {
                addGatherer(this@ConcordChannel)
                cachedPlaceholder = this
            }
        }

    companion object {
        fun placeholderIdHex(channelId: ConcordChannelId): HexKey = "concord-empty-${channelId.toKey()}"
    }
}
