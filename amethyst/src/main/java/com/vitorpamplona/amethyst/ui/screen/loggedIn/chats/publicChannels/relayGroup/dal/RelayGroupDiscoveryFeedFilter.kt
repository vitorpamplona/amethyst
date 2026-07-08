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
import com.vitorpamplona.amethyst.ui.dal.sortedByDefaultFeedOrder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * Resolves a relay-signed kind-39000 metadata note to the live [RelayGroupChannel] it belongs to.
 * A group is keyed by (host relay + group id); the SAME resolver is used by the feed filter's
 * match/sort AND the discovery row's renderer, so a note seen on more than one relay always binds
 * to one channel (no more "sorted by relay B's roster, rendered with relay A's empty one").
 */
fun relayGroupDiscoveryChannelFor(note: Note): RelayGroupChannel? {
    val groupId = (note.event as? GroupMetadataEvent)?.groupId() ?: return null
    return note.relays.firstNotNullOfOrNull { LocalCache.getRelayGroupChannelIfExists(GroupId(groupId, it)) }
}

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
        return newItems.flatMapTo(HashSet()) { note ->
            when (note.event) {
                is GroupMetadataEvent -> if (matches(note, byRelay)) listOf(note) else emptyList()
                // A roster change (39001/39002) can newly qualify a group whose 39000 already
                // arrived (a follow just became an admin/member). The roster note itself isn't a
                // feed row, so re-test the group's metadata note and inject it if it now matches —
                // otherwise the group would stay hidden (or frozen at 0 members) until a refresh.
                is GroupAdminsEvent, is GroupMembersEvent ->
                    rosterMetadataNote(note)?.takeIf { matches(it, byRelay) }?.let { listOf(it) } ?: emptyList()
                else -> emptyList()
            }
        }
    }

    /**
     * The group qualifies when its host relay is in the filter's set AND that relay's constraint
     * accepts it. [relayGroupDiscoveryChannelFor] picks the one channel (metadata + roster); the
     * roster lives on the [RelayGroupChannel] [LocalCache.consume] created when the metadata arrived.
     */
    private fun matches(
        note: Note,
        byRelay: Map<NormalizedRelayUrl, GroupDiscoveryConstraint>,
    ): Boolean {
        if (byRelay.isEmpty()) return false
        val channel = relayGroupDiscoveryChannelFor(note) ?: return false
        return byRelay[channel.groupId.relayUrl]?.matches(channel) == true
    }

    /** The 39000 metadata note for the group a roster (39001/39002) note belongs to, if cached. */
    private fun rosterMetadataNote(note: Note): Note? {
        val groupId =
            when (val event = note.event) {
                is GroupAdminsEvent -> event.groupId()
                is GroupMembersEvent -> event.groupId()
                else -> null
            } ?: return null
        return note.relays
            .firstNotNullOfOrNull { LocalCache.getRelayGroupChannelIfExists(GroupId(groupId, it)) }
            ?.metadataNote
    }

    override fun sort(items: Set<Note>): List<Note> {
        // Snapshot the member count once per note (stable key), then order by it descending with
        // the shared default feed order (createdAt snapshot + id) as a stable tie-break. Reading
        // createdAt live inside a comparator risks TimSort's "contract violated" crash when a
        // newer 39000 replaces a note's event mid-sort — sortedByDefaultFeedOrder avoids that.
        val memberCount = items.associateWith { relayGroupDiscoveryChannelFor(it)?.memberCount() ?: 0 }
        return items.sortedByDefaultFeedOrder().sortedByDescending { memberCount[it] }
    }
}
