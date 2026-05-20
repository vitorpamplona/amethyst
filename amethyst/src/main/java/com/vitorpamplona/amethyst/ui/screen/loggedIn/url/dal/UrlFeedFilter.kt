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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.url.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip01Core.tags.references.isTaggedReferences
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent

class UrlFeedFilter(
    val url: String,
    val relays: Set<NormalizedRelayUrl>,
    val account: Account,
    val cache: LocalCache,
) : AdditiveFeedFilter<Note>() {
    private val normalizedUrl = UrlId.toScope(url)
    private val referenceUrls = setOf(normalizedUrl, HttpUrlFormatter.normalize(url))

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + normalizedUrl

    override fun feed(): List<Note> {
        val notes =
            cache.notes.filterIntoSet { _, note ->
                acceptableEvent(note)
            }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { acceptableEvent(it) }

    private fun acceptableEvent(note: Note): Boolean =
        (acceptableViaReference(note.event) || acceptableViaScope(note.event)) &&
            !note.isHiddenFor(account.hiddenUsers.flow.value) &&
            account.isAcceptable(note)

    private fun acceptableViaReference(event: Event?): Boolean =
        (
            event is TextNoteEvent ||
                event is RepostEvent ||
                event is GenericRepostEvent ||
                event is LongTextNoteEvent ||
                event is WikiNoteEvent ||
                event is ChannelMessageEvent ||
                event is CommentEvent ||
                event is HighlightEvent
        ) &&
            event.tags.isTaggedReferences(referenceUrls)

    private fun acceptableViaScope(event: Event?): Boolean = event is CommentEvent && event.isTaggedScope(normalizedUrl, UrlId::match)

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)
}
