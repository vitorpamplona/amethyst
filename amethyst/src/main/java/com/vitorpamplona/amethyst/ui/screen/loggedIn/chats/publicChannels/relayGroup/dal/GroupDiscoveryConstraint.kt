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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.dal

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * The per-type match a discovery filter applies to a [RelayGroupChannel] on ONE relay.
 *
 * A NIP-29 group's kind-39000 is relay-signed, so the naive "author = a follow" match the
 * note feeds use never fires (the author is the relay). But the *people* dimension is still
 * meaningful — it just lives in the roster events and the relay's own key:
 *  - the relay signing the 39000 may be a followed key ([ByPeople] relay-key path);
 *  - a follow may be a group **admin** (kind 39001) or **member** (kind 39002).
 *
 * Topics/geo aren't defined by NIP-29, but a cooperating relay can copy requested `t`/`g`
 * tags onto the 39000 ([ByHashtags]/[ByGeohashes]).
 */
sealed interface GroupDiscoveryConstraint {
    fun matches(channel: RelayGroupChannel): Boolean

    /** Every group the relay hosts (Global, or a specific relay chip). */
    data object AllGroups : GroupDiscoveryConstraint {
        override fun matches(channel: RelayGroupChannel) = channel.event != null
    }

    /** I follow the relay key, or a follow is an admin/member of the group. */
    data class ByPeople(
        val pubkeys: Set<HexKey>,
    ) : GroupDiscoveryConstraint {
        override fun matches(channel: RelayGroupChannel): Boolean {
            if (pubkeys.isEmpty()) return false
            val relayKey = channel.event?.pubKey
            return (relayKey != null && relayKey in pubkeys) ||
                channel.admins.any { it.pubKey in pubkeys } ||
                channel.members.any { it in pubkeys }
        }
    }

    /** The 39000 carries a `t` tag matching one of these topics (compared lowercase). */
    data class ByHashtags(
        val hashtags: Set<String>,
    ) : GroupDiscoveryConstraint {
        private val lower = hashtags.mapTo(mutableSetOf()) { it.lowercase() }

        override fun matches(channel: RelayGroupChannel): Boolean {
            if (lower.isEmpty()) return false
            return channel.event?.hashtags()?.any { it.lowercase() in lower } == true
        }
    }

    /** The 39000 carries a `g` tag matching one of these geohashes (mip-map prefixes intersect). */
    data class ByGeohashes(
        val geohashes: Set<String>,
    ) : GroupDiscoveryConstraint {
        private val lower = geohashes.mapTo(mutableSetOf()) { it.lowercase() }

        override fun matches(channel: RelayGroupChannel): Boolean {
            if (lower.isEmpty()) return false
            return channel.event?.geohashes()?.any { it.lowercase() in lower } == true
        }
    }

    /** All-follows big-OR: a group matches if ANY of its people/topic/geo lenses match. */
    data class AnyOf(
        val constraints: List<GroupDiscoveryConstraint>,
    ) : GroupDiscoveryConstraint {
        override fun matches(channel: RelayGroupChannel) = constraints.any { it.matches(channel) }
    }
}

/**
 * Resolve the selected top-nav filter into a per-relay [GroupDiscoveryConstraint]. The relays
 * are exactly the relays the filter routes to; each relay's constraint carries only that relay's
 * slice of the follow/topic/geo set (matching how the note feeds shard per relay). Community
 * selections have no people/topic/geo dimension, so they fall back to
 * [GroupDiscoveryConstraint.AllGroups] — still constraining the RELAY set, just not the people.
 * Each branch mirrors what the datasource actually REQs for that filter, so fetch and display agree.
 */
fun IFeedTopNavPerRelayFilterSet.toGroupConstraints(): Map<NormalizedRelayUrl, GroupDiscoveryConstraint> =
    when (this) {
        is GlobalTopNavPerRelayFilterSet -> set.keys.associateWith { GroupDiscoveryConstraint.AllGroups }
        is RelayTopNavPerRelayFilterSet -> mapOf(relayUrl to GroupDiscoveryConstraint.AllGroups)
        is AuthorsTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) -> GroupDiscoveryConstraint.ByPeople(f.authors) }
        is HashtagTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) -> GroupDiscoveryConstraint.ByHashtags(f.hashtags) }
        is LocationTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) -> GroupDiscoveryConstraint.ByGeohashes(f.geotags) }
        is AllFollowsTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) ->
                val lenses =
                    buildList {
                        f.authors?.takeIf { it.isNotEmpty() }?.let { add(GroupDiscoveryConstraint.ByPeople(it)) }
                        f.hashtags?.takeIf { it.isNotEmpty() }?.let { add(GroupDiscoveryConstraint.ByHashtags(it)) }
                        f.geotags?.takeIf { it.isNotEmpty() }?.let { add(GroupDiscoveryConstraint.ByGeohashes(it)) }
                    }
                if (lenses.isEmpty()) GroupDiscoveryConstraint.AllGroups else GroupDiscoveryConstraint.AnyOf(lenses)
            }
        // Muted-authors is a "show me the muted" lens: keep groups where a muted user is the relay
        // key / an admin / a member (matching the #p roster REQ that fetches them).
        is MutedAuthorsTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) -> GroupDiscoveryConstraint.ByPeople(f.authors) }
        // Community selections carry no group-hosting dimension; show every group the resolved
        // relays host (matching the broad directory REQ). Empty for DVM/anything with no relays.
        is AllCommunitiesTopNavPerRelayFilterSet -> set.keys.associateWith { GroupDiscoveryConstraint.AllGroups }
        is SingleCommunityTopNavPerRelayFilterSet -> set.keys.associateWith { GroupDiscoveryConstraint.AllGroups }
        else -> emptyMap()
    }
