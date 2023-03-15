package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Constants

object ServiceManager {
    private var account: Account? = null

    fun start(account: Account) {
        this.account = account
        start()
    }

    fun start() {
        val myAccount = account

        if (myAccount != null) {
            Client.connect(myAccount.activeRelays() ?: myAccount.convertLocalRelays())

            // start services
            NostrAccountDataSource.account = myAccount
            NostrHomeDataSource.account = myAccount
            NostrChatroomListDataSource.account = myAccount

            // Notification Elements
            NostrAccountDataSource.start()
            NostrHomeDataSource.start()
            NostrChatroomListDataSource.start()

            // More Info Data Sources
            NostrSingleEventDataSource.start()
            NostrSingleChannelDataSource.start()
            NostrSingleUserDataSource.start()
        } else {
            // if not logged in yet, start a basic service wit default relays
            Client.connect(Constants.convertDefaultRelays())
        }
    }

    fun pause() {
        NostrAccountDataSource.stop()
        NostrHomeDataSource.stop()
        NostrChannelDataSource.stop()
        NostrChatroomListDataSource.stop()

        NostrGlobalDataSource.stop()
        NostrSingleChannelDataSource.stop()
        NostrSingleEventDataSource.stop()
        NostrSingleUserDataSource.stop()
        NostrThreadDataSource.stop()
        NostrUserProfileDataSource.stop()

        Client.disconnect()
    }

    fun cleanUp() {
        LocalCache.cleanObservers()

        account?.let {
            LocalCache.pruneOldAndHiddenMessages(it)
            LocalCache.pruneHiddenMessages(it)
            LocalCache.pruneContactLists(it)
            // LocalCache.pruneNonFollows(it)
        }
    }
}
