/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.IsInPublicChatChannel
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent

open class DiscoverChatFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultDiscoveryFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultDiscoveryFollowList.value ==
            PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.defaultDiscoveryFollowList.value ==
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val allChannelNotes =
            LocalCache.channels.mapNotNullIntoSet { _, channel ->
                if (channel is PublicChatChannel) {
                    val note = LocalCache.getNoteIfExists(channel.idHex)
                    val noteEvent = note?.event

                    if (noteEvent == null || params.match(noteEvent)) {
                        note
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

        return sort(allChannelNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    fun buildFilterParams(account: Account): FilterByListParams {
        return FilterByListParams.create(
            userHex = account.userProfile().pubkeyHex,
            selectedListName = account.defaultDiscoveryFollowList.value,
            followLists = account.liveDiscoveryFollowLists.value,
            hiddenUsers = account.flowHiddenUsers.value,
        )
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return collection.mapNotNullTo(HashSet()) { note ->
            // note event here will never be null
            val noteEvent = note.event
            if (noteEvent is ChannelCreateEvent && params.match(noteEvent)) {
                if ((LocalCache.getChannelIfExists(noteEvent.id)?.notes?.size() ?: 0) > 0) {
                    note
                } else {
                    null
                }
            } else if (noteEvent is IsInPublicChatChannel) {
                val channel = noteEvent.channel()?.let { LocalCache.checkGetOrCreateNote(it) }
                if (channel != null &&
                    (channel.event == null || (channel.event is ChannelCreateEvent && params.match(channel.event)))
                ) {
                    if ((LocalCache.getChannelIfExists(channel.idHex)?.notes?.size() ?: 0) > 0) {
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

    override fun sort(collection: Set<Note>): List<Note> {
        val lastNote =
            collection.associateWith { note ->
                LocalCache.getChannelIfExists(note.idHex)?.lastNoteCreatedAt ?: 0
            }

        return collection
            .sortedWith(
                compareBy(
                    { lastNote[it] },
                    { it.createdAt() },
                    { it.idHex },
                ),
            )
            .reversed()
    }
}
