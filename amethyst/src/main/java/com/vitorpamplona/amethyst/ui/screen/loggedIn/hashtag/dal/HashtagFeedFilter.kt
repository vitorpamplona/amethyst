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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.dal

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHash
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId

class HashtagFeedFilter(
    val tag: String,
    val relays: Set<NormalizedRelayUrl>,
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

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> = collection.filterTo(HashSet<Note>()) { acceptableEvent(it, tag) }

    fun acceptableEvent(
        it: Note,
        hashTag: String,
    ): Boolean =
        (acceptableViaHashtag(it.event, hashTag) || acceptableViaScope(it.event, hashTag)) &&
            !it.isHiddenFor(account.hiddenUsers.flow.value) &&
            account.isAcceptable(it)

    fun acceptableViaHashtag(
        event: Event?,
        hashTag: String,
    ): Boolean =
        (
            event is TextNoteEvent ||
                event is RepostEvent ||
                event is GenericRepostEvent ||
                event is LongTextNoteEvent ||
                event is WikiNoteEvent ||
                event is ChannelMessageEvent ||
                event is PrivateDmEvent ||
                event is PollNoteEvent ||
                event is AudioHeaderEvent
        ) &&
            event.isTaggedHash(hashTag) == true

    fun acceptableViaScope(
        event: Event?,
        hashTag: String,
    ): Boolean = event is CommentEvent && event.isTaggedScope(hashTag, HashtagId::match)

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)
}
