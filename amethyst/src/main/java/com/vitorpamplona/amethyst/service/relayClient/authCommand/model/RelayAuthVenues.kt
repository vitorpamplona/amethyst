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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * The rooms an account joined that the NIP-51 venue lists don't describe: NIP-29 relay groups (the
 * kind-10009 list), Concord communities (the kind-13302 list) and Buzz workspaces.
 *
 * All three are venues in the "…it's my relay, or a room I joined" sense, and all three are invisible
 * to every other signal in the auth path — a NIP-29 group's content is `#h`-scoped and never names the
 * user, and a Concord plane is authored by and addressed to derived stream keys — so this is what
 * feeds them to [RelayAuthPermissionLedger] and [RelayAuthFirstParty]. Pure, so the id/url matching is
 * testable without an [com.vitorpamplona.amethyst.model.Account].
 */
object RelayAuthVenues {
    /**
     * The relays these rooms live on. A NIP-29 group id is only meaningful together with its host, so
     * the [GroupId] carries an already-normalized url; a Concord entry stores raw url strings written
     * by whoever created the community, so those are normalized here — comparing them verbatim
     * against a challenge's relay url is what makes a trailing slash or a `wss://` case difference
     * quietly drop the whole community.
     *
     * [joinedWorkspaces] are Buzz workspaces, the third joined-room shape: one relay each, joined by
     * redeeming an HTTP invite rather than by publishing a list event, so the relay url *is* the
     * membership record.
     */
    fun hostRelays(
        joinedGroups: Set<GroupId>,
        joinedCommunities: List<ConcordCommunityListEntry>,
        joinedWorkspaces: Set<NormalizedRelayUrl> = emptySet(),
    ): Set<NormalizedRelayUrl> =
        buildSet {
            joinedGroups.mapTo(this) { it.relayUrl }
            joinedCommunities.forEach { entry ->
                entry.relays.forEach { url -> RelayUrlNormalizer.normalizeOrNull(url)?.let(::add) }
            }
            addAll(joinedWorkspaces)
        }

    /**
     * True when [venueId] names one of these rooms — a NIP-29 group id or a Concord community id.
     * These are the ids the subscription assemblers declare as their filters' entity ids, so this is
     * what turns a `READ_VENUE`/`POST_VENUE` on a joined room into a trusted venue.
     */
    fun isJoinedRoom(
        venueId: String,
        joinedGroups: Set<GroupId>,
        joinedCommunities: List<ConcordCommunityListEntry>,
    ): Boolean = joinedGroups.any { it.id == venueId } || joinedCommunities.any { it.id == venueId }
}
