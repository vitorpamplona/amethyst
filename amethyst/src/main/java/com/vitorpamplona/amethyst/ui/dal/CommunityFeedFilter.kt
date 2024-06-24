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
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent

class CommunityFeedFilter(val note: AddressableNote, val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + note.idHex
    }

    override fun feed(): List<Note> {
        val myPubKey = account.userProfile().pubkeyHex
        val result =
            LocalCache.notes.mapFlattenIntoSet { _, it ->
                filterMap(it, myPubKey)
            }

        return sort(result)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val myPubKey = account.userProfile().pubkeyHex

        return collection.mapNotNull {
            filterMap(it, myPubKey)
        }.flatten().toSet()
    }

    private fun filterMap(
        note: Note,
        myPubKey: HexKey,
    ): List<Note>? {
        return if (
            // Only Approvals
            note.event is CommunityPostApprovalEvent &&
            // Of the given community
            note.event?.isTaggedAddressableNote(this.note.idHex) == true
        ) {
            // if it is my post, bring on
            if (note.author?.pubkeyHex == myPubKey && note.isNewThread()) {
                listOf(note)
            } else {
                // brings the actual posts, not the approvals
                note.replyTo?.filter { it.isNewThread() }
            }
        } else {
            null
        }
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(DefaultFeedOrder)
    }
}
