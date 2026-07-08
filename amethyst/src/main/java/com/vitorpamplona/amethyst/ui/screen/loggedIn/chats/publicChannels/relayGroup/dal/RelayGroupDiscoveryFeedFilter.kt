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
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * The Relay Groups discovery feed. Rows are the relay-signed kind-39000 metadata notes; the
 * top-nav filter is resolved into a per-relay [GroupDiscoveryConstraint] ([toGroupConstraints])
 * that decides which groups qualify: every group for Global, relay-key / admin / member for the
 * people filters (rosters read from the group's [RelayGroupChannel]), or a `#t`/`#g` tag match
 * for topic/geo filters. Structurally identical to
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.dal.GitRepositoriesFeedFilter];
 * the only difference is that a group's author is its relay, so the match runs through the
 * roster-aware constraint rather than the author-based [com.vitorpamplona.amethyst.ui.dal.FilterByListParams].
 */
class RelayGroupDiscoveryFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 200

    fun followList(): TopFilter = account.settings.defaultRelayGroupsDiscoveryFollowList.value

    private fun constraints(): Map<NormalizedRelayUrl, GroupDiscoveryConstraint> = account.liveRelayGroupsDiscoveryFollowListsPerRelay.value.toGroupConstraints()

    override fun feed(): List<Note> {
        val byRelay = constraints()
        val notes =
            LocalCache.addressables.filterIntoSet(GroupMetadataEvent.KIND) { _, note ->
                matches(note, byRelay)
            }
        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val byRelay = constraints()
        return newItems.filterTo(HashSet()) { matches(it, byRelay) }
    }

    /**
     * The group qualifies when it was served by a relay in the filter's set AND that relay's
     * constraint accepts it. The 39000 note carries its own relay-signed metadata; its roster
     * (admins/members) lives on the [RelayGroupChannel] keyed by (serving relay + group id),
     * which [LocalCache.consume] created when the metadata arrived.
     */
    private fun matches(
        note: Note,
        byRelay: Map<NormalizedRelayUrl, GroupDiscoveryConstraint>,
    ): Boolean {
        if (byRelay.isEmpty()) return false
        val event = note.event as? GroupMetadataEvent ?: return false
        return note.relays.any { relay ->
            val constraint = byRelay[relay] ?: return@any false
            val channel = LocalCache.getRelayGroupChannelIfExists(GroupId(event.groupId(), relay))
            channel != null && constraint.matches(channel)
        }
    }

    override fun sort(items: Set<Note>): List<Note> {
        fun channelOf(note: Note): RelayGroupChannel? {
            val event = note.event as? GroupMetadataEvent ?: return null
            return note.relays.firstNotNullOfOrNull {
                LocalCache.getRelayGroupChannelIfExists(GroupId(event.groupId(), it))
            }
        }

        val memberCount = items.associateWith { channelOf(it)?.memberCount() ?: 0 }

        return items.sortedWith(
            compareByDescending<Note> { memberCount[it] }
                .thenByDescending { it.createdAt() ?: 0L }
                .thenBy { it.idHex },
        )
    }
}
