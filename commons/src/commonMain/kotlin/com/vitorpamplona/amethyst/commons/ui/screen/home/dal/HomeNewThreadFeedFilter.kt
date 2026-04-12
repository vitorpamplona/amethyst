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
package com.vitorpamplona.amethyst.commons.ui.screen.home.dal

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.ui.dal.FilterByListParams
import com.vitorpamplona.amethyst.commons.ui.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.ui.feeds.DefaultFeedOrder
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent

open class HomeNewThreadFeedFilter(
    val account: IAccount,
    val cache: ICacheProvider,
) : AdditiveFeedFilter<Note>() {
    companion object {
        val ADDRESSABLE_KINDS =
            listOf(
                AudioTrackEvent.KIND,
                InteractiveStoryPrologueEvent.KIND,
                WikiNoteEvent.KIND,
                ClassifiedsEvent.KIND,
                LongTextNoteEvent.KIND,
                LiveChessGameEndEvent.KIND,
                AttestationEvent.KIND,
            )
    }

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.accountSettings.defaultHomeFollowListCode.value

    override fun showHiddenKey(): Boolean = account.getHomeFollowList()?.isMutedFilter == true

    fun buildFilterParams(account: IAccount): FilterByListParams =
        FilterByListParams.create(
            followLists = account.getHomeFollowList(),
            hiddenUsers = account.getLiveHiddenUsers(),
        )

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)

        val notes =
            cache.notes.filterIntoSet { _, note ->
                // Avoids processing addressables twice.
                (note.event?.kind ?: 99999) < 10000 && acceptableEvent(note, filterParams)
            }

        val longFormNotes =
            cache.addressables.filterIntoSet(
                kinds = ADDRESSABLE_KINDS,
            ) { _, note ->
                acceptableEvent(note, filterParams)
            }

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) {
            acceptableEvent(it, filterParams)
        }
    }

    private fun acceptableEvent(
        it: Note,
        filterParams: FilterByListParams,
    ): Boolean {
        val noteEvent = it.event
        return (
            noteEvent is TextNoteEvent ||
                noteEvent is ClassifiedsEvent ||
                noteEvent is RepostEvent ||
                noteEvent is GenericRepostEvent ||
                (noteEvent is LongTextNoteEvent && noteEvent.content.isNotEmpty()) ||
                (noteEvent is WikiNoteEvent && noteEvent.content.isNotEmpty()) ||
                noteEvent is ZapPollEvent ||
                noteEvent is PollEvent ||
                noteEvent is HighlightEvent ||
                noteEvent is InteractiveStoryPrologueEvent ||
                noteEvent is CommentEvent ||
                noteEvent is AudioTrackEvent ||
                noteEvent is VoiceEvent ||
                noteEvent is AudioHeaderEvent ||
                noteEvent is ChessGameEvent ||
                noteEvent is LiveChessGameEndEvent ||
                noteEvent is AttestationEvent ||
                noteEvent is AttestationRequestEvent ||
                noteEvent is AttestorRecommendationEvent ||
                noteEvent is AttestorProficiencyEvent
        ) &&
            filterParams.match(noteEvent, it.relays) &&
            it.isNewThread()
    }

    override fun sort(items: Set<Note>): List<Note> =
        items
            .distinctBy {
                if (it.event is RepostEvent || it.event is GenericRepostEvent) {
                    it.replyTo?.lastOrNull()?.idHex ?: it.idHex // only the most recent repost per feed.
                } else {
                    it.idHex
                }
            }.sortedWith(DefaultFeedOrder)
}
