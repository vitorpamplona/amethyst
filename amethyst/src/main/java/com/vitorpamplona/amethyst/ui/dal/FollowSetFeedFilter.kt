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

import android.util.Log
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.FollowSet
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class FollowSetFeedFilter(
    val account: Account,
) : FeedFilter<FollowSet>() {
    private val followSetEventPairs: MutableMap<AddressableNote, FollowSet> = mutableMapOf()

    override fun feedKey(): String = account.userProfile().pubkeyHex

    override fun feed(): List<FollowSet> {
        val userFollowSets = account.userProfile().followSets
        if (userFollowSets.isEmpty()) {
            account.scope.launch {
                try {
                    account.getFollowSetNotes()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("HiddenAccountsFeedFilter", "Failed to load follow lists: ${e.message}")
                    null
                }
            }
        }
        updateFollowSetEventPairs(userFollowSets)
        return followSetEventPairs.values.toList()
    }

    private fun updateFollowSetEventPairs(notes: Set<AddressableNote>) {
        notes.forEach { note ->
            val noteAndSetPair = note to account.mapNoteToFollowSet(note)
            followSetEventPairs.putIfAbsent(noteAndSetPair.first, noteAndSetPair.second)
        }
    }
}
