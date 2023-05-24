package com.vitorpamplona.amethyst.service.relays

import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.service.HttpClient

object Constants {
    val activeTypes = setOf(FeedType.FOLLOWS, FeedType.PRIVATE_DMS)
    val activeTypesChats = setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS)
    val activeTypesGlobalChats = setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS, FeedType.GLOBAL)
    val activeTypesSearch = setOf(FeedType.SEARCH)

    fun convertDefaultRelays(): Array<Relay> {
        return defaultRelays.map {
            Relay(it.url, it.read, it.write, it.feedTypes, HttpClient.getProxy())
        }.toTypedArray()
    }

    val defaultRelays = arrayOf(
        // Free relays for DMs and Follows
        RelaySetupInfo("wss://no.str.cr", read = true, write = true, feedTypes = activeTypes),
        RelaySetupInfo("wss://relay.snort.social", read = true, write = true, feedTypes = activeTypes),
        RelaySetupInfo("wss://relay.damus.io", read = true, write = true, feedTypes = activeTypes),

        // Chats
        RelaySetupInfo("wss://nostr.bitcoiner.social", read = true, write = true, feedTypes = activeTypesChats),
        RelaySetupInfo("wss://relay.nostr.bg", read = true, write = true, feedTypes = activeTypesChats),
        RelaySetupInfo("wss://nostr.oxtr.dev", read = true, write = true, feedTypes = activeTypesChats),
        RelaySetupInfo("wss://nostr-pub.wellorder.net", read = true, write = true, feedTypes = activeTypesChats),
        RelaySetupInfo("wss://nostr.mom", read = true, write = true, feedTypes = activeTypesChats),
        RelaySetupInfo("wss://nos.lol", read = true, write = true, feedTypes = activeTypesChats),

        // Less Reliable
        // NewRelayListViewModel.Relay("wss://nostr.orangepill.dev", read = true, write = true, feedTypes = activeTypes),
        // NewRelayListViewModel.Relay("wss://nostr.onsats.org", read = true, write = true, feedTypes = activeTypes),
        // NewRelayListViewModel.Relay("wss://nostr.sandwich.farm", read = true, write = true, feedTypes = activeTypes),
        // NewRelayListViewModel.Relay("wss://relay.nostr.ch", read = true, write = true, feedTypes = activeTypes),
        // NewRelayListViewModel.Relay("wss://nostr.zebedee.cloud", read = true, write = true, feedTypes = activeTypes),
        // NewRelayListViewModel.Relay("wss://nostr.rocks", read = true, write = true, feedTypes = activeTypes),
        // NewRelayListViewModel.Relay("wss://nostr.fmt.wiz.biz", read = true, write = true, feedTypes = activeTypes),
        // NewRelayListViewModel.Relay("wss://brb.io", read = true, write = true, feedTypes = activeTypes),

        // Paid relays
        RelaySetupInfo("wss://relay.nostr.com.au", read = true, write = false, feedTypes = activeTypesGlobalChats),
        RelaySetupInfo("wss://eden.nostr.land", read = true, write = false, feedTypes = activeTypesGlobalChats),
        RelaySetupInfo("wss://nostr.milou.lol", read = true, write = false, feedTypes = activeTypesGlobalChats),
        RelaySetupInfo("wss://puravida.nostr.land", read = true, write = false, feedTypes = activeTypesGlobalChats),
        RelaySetupInfo("wss://nostr.wine", read = true, write = false, feedTypes = activeTypesGlobalChats),
        RelaySetupInfo("wss://nostr.inosta.cc", read = true, write = false, feedTypes = activeTypesGlobalChats),
        RelaySetupInfo("wss://atlas.nostr.land", read = true, write = false, feedTypes = activeTypesGlobalChats),
        RelaySetupInfo("wss://relay.orangepill.dev", read = true, write = false, feedTypes = activeTypesGlobalChats),
        RelaySetupInfo("wss://relay.nostrati.com", read = true, write = false, feedTypes = activeTypesGlobalChats),

        // Supporting NIP-50
        RelaySetupInfo("wss://relay.nostr.band", read = true, write = false, feedTypes = activeTypesSearch)
    )

    val forcedRelayForSearch = RelaySetupInfo("wss://relay.nostr.band", read = true, write = false, feedTypes = activeTypesSearch)
}
