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

@Immutable
class ThreadFeedFilter(
    val account: Account,
    val noteId: String,
) : FeedFilter<Note>() {
    override fun feedKey(): String = noteId

    override fun feed(): List<Note> {
        val cachedSignatures: MutableMap<Note, LevelSignature> = mutableMapOf()
        val followingKeySet = account.liveKind3Follows.value.users
        val eventsToWatch = ThreadAssembler().findThreadFor(noteId)
        val eventsInHex = eventsToWatch.map { it.idHex }.toSet()
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

        return eventsToWatch.sortedWith(order)
    }
}
