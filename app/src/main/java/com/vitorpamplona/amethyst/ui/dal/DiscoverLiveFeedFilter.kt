package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_ENDED
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_PLANNED
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

open class DiscoverLiveFeedFilter(
    val account: Account
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + followList()
    }

    open fun followList(): String {
        return account.defaultDiscoveryFollowList
    }

    override fun showHiddenKey(): Boolean {
        return followList() == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex)
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
        val now = TimeUtils.now()
        val isGlobal = account.defaultDiscoveryFollowList == GLOBAL_FOLLOWS
        val isHiddenList = showHiddenKey()

        val followingKeySet = account.selectedUsersFollowList(followList()) ?: emptySet()
        val followingTagSet = account.selectedTagsFollowList(followList()) ?: emptySet()
        val followingGeohashSet = account.selectedGeohashesFollowList(followList()) ?: emptySet()

        val activities = collection
            .asSequence()
            .filter { it.event is LiveActivitiesEvent }
            .filter {
                isGlobal || (it.event as LiveActivitiesEvent).participantsIntersect(followingKeySet) || it.event?.isTaggedHashes(
                    followingTagSet
                ) == true || it.event?.isTaggedGeoHashes(
                    followingGeohashSet
                ) == true
            }
            .filter { isHiddenList || it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true }
            .filter { (it.createdAt() ?: 0) <= now }
            .toSet()

        return activities
    }

    override fun sort(collection: Set<Note>): List<Note> {
        val followingKeySet = account.selectedUsersFollowList(followList())

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
