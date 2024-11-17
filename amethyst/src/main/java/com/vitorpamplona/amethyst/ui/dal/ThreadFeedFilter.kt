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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LevelSignature
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ThreadAssembler
import com.vitorpamplona.amethyst.model.ThreadLevelCalculator
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class ThreadFeedFilter(
    val account: Account,
    private val noteId: String,
) : FeedFilter<Note>() {
    override fun feedKey(): String = noteId

    override fun feed(): List<Note> {
        val cachedSignatures: MutableMap<Note, LevelSignature> = mutableMapOf()
        val followingKeySet = account.liveKind3Follows.value.authors
        val eventsToWatch = ThreadAssembler().findThreadFor(noteId) ?: return emptyList()

        // Filter out drafts made by other accounts on device
        val filteredEvents =
            eventsToWatch.allNotes
                .filter { !it.isDraft() || (it.author?.pubkeyHex == account.userProfile().pubkeyHex) }
                .toImmutableSet()
        val filteredThreadInfo = ThreadAssembler.ThreadInfo(eventsToWatch.root, filteredEvents)

        val eventsInHex = filteredThreadInfo.allNotes.map { it.idHex }.toSet()
        val now = TimeUtils.now()

        // Currently orders by date of each event, descending, at each level of the reply stack
        val order =
            compareByDescending<Note> {
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

        return filteredThreadInfo.allNotes.sortedWith(order)
    }
}
