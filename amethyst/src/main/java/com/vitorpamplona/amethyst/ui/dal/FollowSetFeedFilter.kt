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
package com.vitorpamplona.amethyst.ui.dal

import android.util.Log
import com.vitorpamplona.amethyst.model.nip51Lists.followSets.FollowSet
import com.vitorpamplona.amethyst.model.nip51Lists.followSets.FollowSetState
import kotlinx.coroutines.runBlocking

class FollowSetFeedFilter(
    val followSetState: FollowSetState,
) : FeedFilter<FollowSet>() {
    override fun feedKey(): String = followSetState.user.pubkeyHex + "-followsets"

    override fun feed(): List<FollowSet> =
        runBlocking(followSetState.scope.coroutineContext) {
            try {
                val fetchedSets = followSetState.getFollowSetNotes()
                val followSets = fetchedSets.map { followSetState.mapNoteToFollowSet(it) }
                println("Updated follow set size for feed filter: ${followSets.size}")
                followSets
            } catch (e: Exception) {
                // if (e is CancellationException) throw e
                Log.e(this@FollowSetFeedFilter.javaClass.simpleName, "Failed to load follow lists: ${e.message}")
                throw e
            }
        }
}
