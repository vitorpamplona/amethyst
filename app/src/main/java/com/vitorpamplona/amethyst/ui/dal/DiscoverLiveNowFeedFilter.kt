package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.quartz.events.*
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE

class DiscoverLiveNowFeedFilter(account: Account) : DiscoverLiveFeedFilter(account) {
    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val allItems = super.innerApplyFilter(collection)

        val onlineOnly = allItems.filter {
            val noteEvent = it.event as? LiveActivitiesEvent
            noteEvent?.status() == STATUS_LIVE && OnlineChecker.isOnline(noteEvent.streaming())
        }

        return onlineOnly.toSet()
    }
}
