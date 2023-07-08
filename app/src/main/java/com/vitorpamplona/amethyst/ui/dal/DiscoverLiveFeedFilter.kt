package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.model.LiveActivitiesEvent.Companion.STATUS_ENDED
import com.vitorpamplona.amethyst.service.model.LiveActivitiesEvent.Companion.STATUS_LIVE
import com.vitorpamplona.amethyst.service.model.LiveActivitiesEvent.Companion.STATUS_PLANNED

open class DiscoverLiveFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultDiscoveryFollowList
    }

    override fun feed(): List<Note> {
        val allChannelNotes =
            LocalCache.channels.values.mapNotNull { LocalCache.getNoteIfExists(it.idHex) }
        val allMessageNotes = LocalCache.channels.values.map { it.notes.values }.flatten()

        val notes = innerApplyFilter(allChannelNotes + allMessageNotes)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val now = System.currentTimeMillis() / 1000
        val isGlobal = account.defaultDiscoveryFollowList == GLOBAL_FOLLOWS

        val followingKeySet =
            account.selectedUsersFollowList(account.defaultDiscoveryFollowList) ?: emptySet()
        val followingTagSet =
            account.selectedTagsFollowList(account.defaultDiscoveryFollowList) ?: emptySet()

        val activities = collection
            .asSequence()
            .filter { it.event is LiveActivitiesEvent }
            .filter {
                isGlobal || it.author?.pubkeyHex in followingKeySet || it.event?.isTaggedHashes(
                    followingTagSet
                ) == true
            }
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
                { convertStatusToOrder((it.event as? LiveActivitiesEvent)?.status()) },
                { participantCounts[it] },
                { (it.event as? LiveActivitiesEvent)?.starts() ?: it.createdAt() },
                { it.idHex }
            )
        ).reversed()
    }

    fun convertStatusToOrder(status: String?): Int {
        return when (status) {
            STATUS_LIVE -> 2
            STATUS_PLANNED -> 1
            STATUS_ENDED -> 0
            else -> 0
        }
    }
}
