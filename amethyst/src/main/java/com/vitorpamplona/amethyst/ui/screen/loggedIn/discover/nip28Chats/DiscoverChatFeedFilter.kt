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

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent

open class DiscoverChatFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultDiscoveryFollowList.value

    override fun showHiddenKey(): Boolean =
        account.settings.defaultDiscoveryFollowList.value ==
            PeopleListEvent.Companion.blockListFor(account.userProfile().pubkeyHex) ||
            account.settings.defaultDiscoveryFollowList.value ==
            MuteListEvent.Companion.blockListFor(account.userProfile().pubkeyHex)

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

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.Companion.create(
            userHex = account.userProfile().pubkeyHex,
            selectedListName = account.settings.defaultDiscoveryFollowList.value,
            followLists = account.liveDiscoveryFollowLists.value,
            hiddenUsers = account.flowHiddenUsers.value,
        )

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
                val channel = noteEvent.channelId()?.let { LocalCache.checkGetOrCreateNote(it) }
                val channelEvent = channel?.event

                if (channel != null &&
                    (channelEvent == null || (channelEvent is ChannelCreateEvent && params.match(channelEvent)))
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
            ).reversed()
    }
}
