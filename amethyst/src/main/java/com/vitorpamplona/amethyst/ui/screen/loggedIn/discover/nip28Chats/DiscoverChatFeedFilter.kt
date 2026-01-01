/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip28Chats

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent

open class DiscoverChatFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultDiscoveryFollowList.value

    override fun limit() = 100

    override fun showHiddenKey(): Boolean =
        account.settings.defaultDiscoveryFollowList.value ==
            PeopleListEvent.Companion.blockListFor(account.userProfile().pubkeyHex) ||
            account.settings.defaultDiscoveryFollowList.value ==
            MuteListEvent.Companion.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val allChannelNotes =
            LocalCache.publicChatChannels.mapNotNullIntoSet { _, channel ->
                val note = LocalCache.getNoteIfExists(channel.idHex)
                val noteEvent = note?.event

                if (noteEvent == null || params.match(noteEvent, note.relays)) {
                    note
                } else {
                    null
                }
            }

        return sort(allChannelNotes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveDiscoveryFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return collection.mapNotNullTo(HashSet()) { note ->
            // note event here will never be null
            val noteEvent = note.event
            if (noteEvent is ChannelCreateEvent && params.match(noteEvent, note.relays)) {
                if ((LocalCache.getPublicChatChannelIfExists(noteEvent.id)?.notes?.size() ?: 0) > 0) {
                    note
                } else {
                    null
                }
            } else if (noteEvent is IsInPublicChatChannel) {
                val channel = noteEvent.channelId()?.let { LocalCache.checkGetOrCreateNote(it) }
                val channelEvent = channel?.event

                if (channel != null &&
                    (channelEvent == null || (channelEvent is ChannelCreateEvent && params.match(channelEvent, note.relays)))
                ) {
                    if ((LocalCache.getPublicChatChannelIfExists(channel.idHex)?.notes?.size() ?: 0) > 0) {
                        channel
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    override fun sort(items: Set<Note>): List<Note> {
        // precache to avoid breaking the contract
        val lastNote =
            items.associateWith { note ->
                LocalCache.getPublicChatChannelIfExists(note.idHex)?.lastNote?.createdAt() ?: 0L
            }

        val createdNote =
            items.associateWith { note ->
                note.createdAt() ?: 0L
            }

        val comparator: Comparator<Note> =
            compareByDescending<Note> {
                lastNote[it]
            }.thenByDescending {
                createdNote[it]
            }.thenBy {
                it.idHex
            }

        return items.sortedWith(comparator)
    }
}
