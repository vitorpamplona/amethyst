package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.events.AppRecommendationEvent

class UserProfileAppRecommendationsFeedFilter(val user: User) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return user.pubkeyHex
    }

    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.addressables.values))
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val recommendations = collection.asSequence()
            .filter { it.event is AppRecommendationEvent }
            .mapNotNull {
                val noteEvent = it.event as? AppRecommendationEvent
                if (noteEvent != null && noteEvent.pubKey == user.pubkeyHex) {
                    noteEvent.recommendations()
                } else {
                    null
                }
            }.flatten().map {
                LocalCache.getOrCreateAddressableNote(it)
            }.toSet()

        return recommendations
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
    }
}
