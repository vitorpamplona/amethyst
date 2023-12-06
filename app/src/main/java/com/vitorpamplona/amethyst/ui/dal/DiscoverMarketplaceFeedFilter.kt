package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

open class DiscoverMarketplaceFeedFilter(
    val account: Account
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + followList()
    }

    open fun followList(): String {
        return account.defaultDiscoveryFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return followList() == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            followList() == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    override fun feed(): List<Note> {
        val classifieds = LocalCache.addressables
            .filter { it.value.event is ClassifiedsEvent }
            .map { it.value }

        val notes = innerApplyFilter(classifieds)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val now = TimeUtils.now()
        val isGlobal = account.defaultDiscoveryFollowList.value == GLOBAL_FOLLOWS
        val isHiddenList = showHiddenKey()

        val followingKeySet = account.liveDiscoveryFollowLists.value?.users ?: emptySet()
        val followingTagSet = account.liveDiscoveryFollowLists.value?.hashtags ?: emptySet()
        val followingGeohashSet = account.liveDiscoveryFollowLists.value?.geotags ?: emptySet()

        val activities = collection
            .asSequence()
            .filter {
                it.event is ClassifiedsEvent &&
                    it.event?.hasTagWithContent("image") == true &&
                    it.event?.hasTagWithContent("price") == true &&
                    it.event?.hasTagWithContent("title") == true
            }
            .filter {
                isGlobal || it.author?.pubkeyHex in followingKeySet || it.event?.isTaggedHashes(followingTagSet) == true || it.event?.isTaggedGeoHashes(followingGeohashSet) == true
            }
            .filter { isHiddenList || it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true }
            .filter { (it.createdAt() ?: 0) <= now }
            .toSet()

        return activities
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
