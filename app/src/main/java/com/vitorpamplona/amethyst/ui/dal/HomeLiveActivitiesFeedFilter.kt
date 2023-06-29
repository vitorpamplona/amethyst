package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.LiveActivitiesEvent
import java.util.Date

class HomeLiveActivitiesFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        val followingKeySet = account.selectedUsersFollowList(account.defaultHomeFollowList)?.size ?: 0
        val followingTagSet = account.selectedTagsFollowList(account.defaultHomeFollowList)?.size ?: 0

        return account.userProfile().pubkeyHex + "-" + account.defaultHomeFollowList + "-" + followingKeySet + "-" + followingTagSet
    }

    override fun feed(): List<Note> {
        val longFormNotes = innerApplyFilter(LocalCache.addressables.values)

        return sort(longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        checkNotInMainThread()

        val isGlobal = account.defaultHomeFollowList == GLOBAL_FOLLOWS

        val followingKeySet = account.selectedUsersFollowList(account.defaultHomeFollowList) ?: emptySet()
        val followingTagSet = account.selectedTagsFollowList(account.defaultHomeFollowList) ?: emptySet()

        val twoHrs = (Date().time / 1000) - 60 * 60 * 2 // hrs

        return collection
            .asSequence()
            .filter { it ->
                val noteEvent = it.event
                (noteEvent is LiveActivitiesEvent && noteEvent.createdAt > twoHrs && noteEvent.status() == "live" && OnlineChecker.isOnline(noteEvent.streaming())) &&
                    (isGlobal || it.author?.pubkeyHex in followingKeySet || noteEvent.isTaggedHashes(followingTagSet)) &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true
            }
            .toSet()
    }

    override fun limit() = 2

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
