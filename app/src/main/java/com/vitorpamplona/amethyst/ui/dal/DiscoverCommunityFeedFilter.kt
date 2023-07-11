package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.service.model.*

open class DiscoverCommunityFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultDiscoveryFollowList
    }

    override fun feed(): List<Note> {
        val allNotes = LocalCache.addressables.values

        val notes = innerApplyFilter(allNotes)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val now = TimeUtils.now()
        val isGlobal = account.defaultDiscoveryFollowList == GLOBAL_FOLLOWS

        val followingKeySet = account.selectedUsersFollowList(account.defaultDiscoveryFollowList) ?: emptySet()
        val followingTagSet = account.selectedTagsFollowList(account.defaultDiscoveryFollowList) ?: emptySet()

        val activities = collection
            .asSequence()
            .filter { it.event is CommunityDefinitionEvent }
            .filter { isGlobal || it.author?.pubkeyHex in followingKeySet || it.event?.isTaggedHashes(followingTagSet) == true }
            .filter { it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true }
            .filter { (it.createdAt() ?: 0) <= now }
            .toSet()

        return activities
    }

    override fun sort(collection: Set<Note>): List<Note> {
        val followingKeySet = account.selectedUsersFollowList(account.defaultDiscoveryFollowList)

        val counter = ParticipantListBuilder()
        val participantCounts = collection.associate {
            it to counter.countFollowsThatParticipateOn(it, followingKeySet)
        }

        return collection.sortedWith(
            compareBy(
                { participantCounts[it] },
                { it.createdAt() },
                { it.idHex }
            )
        ).reversed()
    }
}
