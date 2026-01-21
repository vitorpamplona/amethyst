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
package com.vitorpamplona.amethyst.commons.viewmodels.thread

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.LevelSignature
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ThreadAssembler
import com.vitorpamplona.amethyst.commons.model.ThreadLevelCalculator
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedFilter
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableSet

/**
 * Filter for assembling and sorting thread feeds.
 *
 * This filter uses ThreadAssembler to find all notes in a thread and
 * ThreadLevelCalculator to sort them by reply level and relevance.
 *
 * @param account The current user's account (provides user profile and following set)
 * @param noteId The root note ID of the thread to display
 * @param cacheProvider The cache provider for accessing notes
 */
@Immutable
class ThreadFeedFilter(
    val account: IAccount,
    private val noteId: String,
    private val cacheProvider: ICacheProvider,
) : FeedFilter<Note>() {
    override fun feedKey(): String = noteId

    override fun feed(): List<Note> {
        val cachedSignatures: MutableMap<Note, LevelSignature> = mutableMapOf()
        val followingKeySet = account.followingKeySet()
        val eventsToWatch = ThreadAssembler(cacheProvider).findThreadFor(noteId) ?: return emptyList()

        // Filter out drafts made by other accounts on device
        val filteredEvents =
            eventsToWatch.allNotes
                .filter { !it.isDraft() || (it.author?.pubkeyHex == account.pubKey) }
                .toImmutableSet()
        val filteredThreadInfo = ThreadAssembler.ThreadInfo(eventsToWatch.root, filteredEvents)

        val eventsInHex = filteredThreadInfo.allNotes.map { it.idHex }.toSet()
        val now = TimeUtils.now()

        val signatures =
            filteredThreadInfo.allNotes.associateWith {
                ThreadLevelCalculator
                    .replyLevelSignature(
                        it,
                        eventsInHex,
                        cachedSignatures,
                        account.userProfile(),
                        followingKeySet,
                        now,
                    ).signature
            }

        // Currently orders by date of each event, descending, at each level of the reply stack
        val order =
            compareByDescending<Note> { signatures[it] }
                .thenBy { it.idHex }

        return filteredThreadInfo.allNotes.sortedWith(order)
    }
}
