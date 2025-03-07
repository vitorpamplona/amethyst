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
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent

class UserProfileMutualFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + user.pubkeyHex

    override fun feed(): List<Note> {
        val notes =
            LocalCache.notes.filterIntoSet { _, it ->
                it !is AddressableNote && it.event !is AddressableEvent && acceptableEvent(it)
            }

        val longFormNotes =
            LocalCache.addressables.filterIntoSet { _, it ->
                acceptableEvent(it)
            }

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> = collection.filterTo(HashSet()) { acceptableEvent(it) }

    fun acceptableEvent(it: Note): Boolean =
        it.author == account.userProfile() &&
            (
                it.event is TextNoteEvent ||
                    it.event is ClassifiedsEvent ||
                    it.event is RepostEvent ||
                    it.event is GenericRepostEvent ||
                    it.event is LongTextNoteEvent ||
                    it.event is WikiNoteEvent ||
                    it.event is PollNoteEvent ||
                    it.event is HighlightEvent ||
                    it.event is InteractiveStoryPrologueEvent ||
                    it.event is AudioTrackEvent ||
                    it.event is AudioHeaderEvent ||
                    it.event is TorrentEvent
            ) &&
            it.event?.isTaggedUser(user.pubkeyHex) == true

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(DefaultFeedOrder)

    override fun limit() = 200
}
