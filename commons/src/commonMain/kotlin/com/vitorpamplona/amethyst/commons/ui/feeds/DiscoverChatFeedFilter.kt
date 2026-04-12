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
package com.vitorpamplona.amethyst.commons.ui.feeds

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel

/**
 * Feed filter for discovering public chat channels (NIP-28).
 *
 * Lists public chat channels filtered by the user's selected discovery follow list.
 * Channels are sorted by last activity (most recent first).
 *
 * @param userPubkeyHex The current user's public key hex
 * @param followListCode Provider for the current follow list code (for feed key)
 * @param showHidden Provider for whether to show hidden content
 * @param filterParamsFactory Factory to create filter parameters from the current account state
 * @param cacheProvider The cache provider for accessing channels and notes
 */
open class DiscoverChatFeedFilter(
    val userPubkeyHex: String,
    val followListCode: () -> String,
    val showHidden: () -> Boolean,
    val filterParamsFactory: () -> IFilterByListParams,
    val cacheProvider: ICacheProvider,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = userPubkeyHex + "-" + followListCode()

    override fun limit() = 100

    override fun showHiddenKey(): Boolean = showHidden()

    override fun feed(): List<Note> {
        val params = filterParamsFactory()

        val allChannelNotes =
            cacheProvider.publicChatChannels.mapNotNullIntoSet { _, channel ->
                val note = cacheProvider.getNoteIfExists(channel.idHex)
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

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = filterParamsFactory()

        return collection.mapNotNullTo(HashSet()) { note ->
            // note event here will never be null
            val noteEvent = note.event
            if (noteEvent is ChannelCreateEvent && params.match(noteEvent, note.relays)) {
                if ((cacheProvider.getPublicChatChannelIfExists(noteEvent.id)?.notes?.size() ?: 0) > 0) {
                    note
                } else {
                    null
                }
            } else if (noteEvent is IsInPublicChatChannel) {
                val channel = noteEvent.channelId()?.let { cacheProvider.checkGetOrCreateNote(it) }
                val channelEvent = channel?.event

                if (channel != null &&
                    (channelEvent == null || (channelEvent is ChannelCreateEvent && params.match(channelEvent, note.relays)))
                ) {
                    if ((cacheProvider.getPublicChatChannelIfExists(channel.idHex)?.notes?.size() ?: 0) > 0) {
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
                cacheProvider.getPublicChatChannelIfExists(note.idHex)?.lastNote?.createdAt() ?: 0L
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
