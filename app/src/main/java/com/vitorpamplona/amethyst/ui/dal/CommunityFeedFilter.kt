package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent

class CommunityFeedFilter(val note: AddressableNote, val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + note.idHex
    }

    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.notes.values))
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val myUnapprovedPosts = collection.asSequence()
            .filter { it.event is CommunityPostApprovalEvent } // Only Approvals
            .filter { it.author?.pubkeyHex == account.userProfile().pubkeyHex } // made by the logged in user
            .filter { it.event?.isTaggedAddressableNote(note.idHex) == true } // for this community
            .filter { it.isNewThread() } // check if it is a new thread
            .toSet()

        val approvedPosts = collection
            .asSequence()
            .filter { it.event is CommunityPostApprovalEvent } // Only Approvals
            .filter { it.event?.isTaggedAddressableNote(note.idHex) == true } // Of the given community
            .mapNotNull { it.replyTo }.flatten() // get approved posts
            .filter { it.isNewThread() } // check if it is a new thread
            .toSet()

        return myUnapprovedPosts + approvedPosts
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
