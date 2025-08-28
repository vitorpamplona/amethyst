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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isForCommunity
import kotlin.collections.toSet

class CommunityFeedFilter(
    val communityDefNote: AddressableNote,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    val communityDefEvent = communityDefNote.event as? CommunityDefinitionEvent
    val moderators = communityDefEvent?.moderatorKeys()?.toSet() ?: emptySet()

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + communityDefNote.idHex

    override fun feed(): List<Note> {
        if (communityDefEvent == null) return emptyList()

        val result =
            LocalCache.notes.mapFlattenIntoSet { _, it ->
                filterMap(it)
            }

        return sort(result)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        if (communityDefEvent == null) return emptySet()

        return collection
            .mapNotNull {
                filterMap(it)
            }.flatten()
            .toSet()
    }

    private fun filterMap(note: Note): List<Note>? {
        val noteEvent = note.event ?: return null

        return if (
            // Only Approvals
            (noteEvent is TextNoteEvent || noteEvent is CommentEvent || noteEvent is CommunityPostApprovalEvent) &&
            noteEvent.pubKey in moderators &&
            noteEvent.isForCommunity(this.communityDefNote.idHex)
        ) {
            if (noteEvent is CommunityPostApprovalEvent) {
                note.replyTo?.filter {
                    it.isNewThread()
                }
            } else {
                if (note.isNewThread()) {
                    listOf(note)
                } else {
                    null
                }
            }
        } else {
            null
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)
}
