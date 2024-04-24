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
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteEvent

class HomeNewThreadFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultHomeFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultHomeFollowList.value == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.defaultHomeFollowList.value == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    fun buildFilterParams(account: Account): FilterByListParams {
        return FilterByListParams.create(
            userHex = account.userProfile().pubkeyHex,
            selectedListName = account.defaultHomeFollowList.value,
            followLists = account.liveHomeFollowLists.value,
            hiddenUsers = account.flowHiddenUsers.value,
        )
    }

    override fun feed(): List<Note> {
        val gRelays = account.activeGlobalRelays().toSet()
        val filterParams = buildFilterParams(account)

        val notes =
            LocalCache.notes.filterIntoSet { _, note ->
                // Avoids processing addressables twice.
                (note.event?.kind() ?: 99999) < 10000 && acceptableEvent(note, gRelays, filterParams)
            }

        val longFormNotes =
            LocalCache.addressables.filterIntoSet { _, note ->
                acceptableEvent(note, gRelays, filterParams)
            }

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val gRelays = account.activeGlobalRelays().toSet()
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) {
            acceptableEvent(it, gRelays, filterParams)
        }
    }

    private fun acceptableEvent(
        it: Note,
        globalRelays: Set<String>,
        filterParams: FilterByListParams,
    ): Boolean {
        val noteEvent = it.event
        val isGlobalRelay = it.relays.any { globalRelays.contains(it.url) }
        return (
            noteEvent is TextNoteEvent ||
                noteEvent is ClassifiedsEvent ||
                noteEvent is RepostEvent ||
                noteEvent is GenericRepostEvent ||
                noteEvent is LongTextNoteEvent ||
                noteEvent is PollNoteEvent ||
                noteEvent is HighlightEvent ||
                noteEvent is AudioTrackEvent ||
                noteEvent is AudioHeaderEvent
        ) && filterParams.match(noteEvent, isGlobalRelay) && it.isNewThread()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.distinctBy {
            if (it.event is RepostEvent || it.event is GenericRepostEvent) {
                it.replyTo?.lastOrNull()?.idHex ?: it.idHex // only the most recent repost per feed.
            } else {
                it.idHex
            }
        }.sortedWith(DefaultFeedOrder)
    }
}
