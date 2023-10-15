package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE

class DiscoverLiveNowFeedFilter(
    account: Account
) : DiscoverLiveFeedFilter(account) {
    override fun followList(): String {
        // uses follows by default, but other lists if they were selected in the top bar
        val currentList = super.followList()
        return if (currentList == GLOBAL_FOLLOWS) {
            KIND3_FOLLOWS
        } else {
            currentList
        }
    }

    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val allItems = super.innerApplyFilter(collection)

        val onlineOnly = allItems.filter {
            val noteEvent = it.event as? LiveActivitiesEvent
            noteEvent?.status() == STATUS_LIVE && OnlineChecker.isOnline(noteEvent.streaming())
        }

        return onlineOnly.toSet()
    }
}
