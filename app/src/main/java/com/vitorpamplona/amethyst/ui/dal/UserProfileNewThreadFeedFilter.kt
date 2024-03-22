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
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteEvent

class UserProfileNewThreadFeedFilter(val user: User, val account: Account) :
    AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + user.pubkeyHex
    }

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

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        return collection.filterTo(HashSet()) { acceptableEvent(it) }
    }

    fun acceptableEvent(it: Note): Boolean {
        return it.author == user &&
            (
                it.event is TextNoteEvent ||
                    it.event is ClassifiedsEvent ||
                    it.event is RepostEvent ||
                    it.event is GenericRepostEvent ||
                    it.event is LongTextNoteEvent ||
                    it.event is PollNoteEvent ||
                    it.event is HighlightEvent ||
                    it.event is AudioTrackEvent ||
                    it.event is AudioHeaderEvent
            ) && it.isNewThread() && account.isAcceptable(it)
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(DefaultFeedOrder)
    }

    override fun limit() = 200
}
