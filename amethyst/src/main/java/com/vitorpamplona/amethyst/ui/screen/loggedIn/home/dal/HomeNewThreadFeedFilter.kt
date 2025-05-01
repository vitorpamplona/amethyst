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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent

class HomeNewThreadFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultHomeFollowList.value

    override fun showHiddenKey(): Boolean =
        account.settings.defaultHomeFollowList.value == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.settings.defaultHomeFollowList.value == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            userHex = account.userProfile().pubkeyHex,
            selectedListName = account.settings.defaultHomeFollowList.value,
            followLists = account.liveHomeFollowLists.value,
            hiddenUsers = account.flowHiddenUsers.value,
        )

    override fun feed(): List<Note> {
        val gRelays = account.activeGlobalRelays().toSet()
        val filterParams = buildFilterParams(account)

        val notes =
            LocalCache.notes.filterIntoSet { _, note ->
                // Avoids processing addressables twice.
                (note.event?.kind ?: 99999) < 10000 && acceptableEvent(note, gRelays, filterParams)
            }

        val longFormNotes =
            LocalCache.addressables.filterIntoSet { _, note ->
                acceptableEvent(note, gRelays, filterParams)
            }

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

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
                (noteEvent is LongTextNoteEvent && noteEvent.content.isNotEmpty()) ||
                (noteEvent is WikiNoteEvent && noteEvent.content.isNotEmpty()) ||
                noteEvent is PollNoteEvent ||
                noteEvent is HighlightEvent ||
                noteEvent is InteractiveStoryPrologueEvent ||
                noteEvent is CommentEvent ||
                noteEvent is AudioTrackEvent ||
                noteEvent is AudioHeaderEvent
        ) &&
            filterParams.match(noteEvent, isGlobalRelay) &&
            it.isNewThread()
    }

    override fun sort(collection: Set<Note>): List<Note> =
        collection
            .distinctBy {
                if (it.event is RepostEvent || it.event is GenericRepostEvent) {
                    it.replyTo?.lastOrNull()?.idHex ?: it.idHex // only the most recent repost per feed.
                } else {
                    it.idHex
                }
            }.sortedWith(DefaultFeedOrder)
}
