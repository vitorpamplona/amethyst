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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.tags.geohash.isTaggedGeoHash
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent

class GeoHashFeedFilter(
    val tag: String,
    val account: Account,
    val cache: LocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + tag

    override fun feed(): List<Note> {
        val notes =
            cache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, tag)
            }

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> = collection.filterTo(HashSet<Note>()) { acceptableEvent(it, tag) }

    fun acceptableEvent(
        it: Note,
        geoTag: String,
    ): Boolean =
        (
            it.event is TextNoteEvent ||
                it.event is LongTextNoteEvent ||
                it.event is WikiNoteEvent ||
                it.event is ChannelMessageEvent ||
                it.event is PrivateDmEvent ||
                it.event is PollNoteEvent ||
                it.event is AudioHeaderEvent
        ) &&
            it.event?.isTaggedGeoHash(geoTag) == true &&
            !it.isHiddenFor(account.flowHiddenUsers.value) &&
            account.isAcceptable(it)

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(DefaultFeedOrder)
}
