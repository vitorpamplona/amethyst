package com.vitorpamplona.amethyst.service.relays

import com.vitorpamplona.amethyst.ui.actions.NewRelayListViewModel

object Constants {
  val activeTypes = setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS)
  val activeTypesGlobal = setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.PRIVATE_DMS, FeedType.GLOBAL)

  fun convertDefaultRelays(): Array<Relay> {
    return defaultRelays.map {
      Relay(it.url, it.read, it.write, it.feedTypes)
    }.toTypedArray()
  }

  val defaultRelays = arrayOf(
    // Free relays
    NewRelayListViewModel.Relay("wss://nostr.bitcoiner.social", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://relay.nostr.bg", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://brb.io", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://relay.snort.social", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://relay.damus.io", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://nostr.oxtr.dev", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://nostr-pub.wellorder.net", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://nostr.mom", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://no.str.cr", read = true, write = true, feedTypes = activeTypes),
    NewRelayListViewModel.Relay("wss://nos.lol", read = true, write = true, feedTypes = activeTypes),

    // Less Reliable
    //NewRelayListViewModel.Relay("wss://nostr.orangepill.dev", read = true, write = true, feedTypes = activeTypes),
    //NewRelayListViewModel.Relay("wss://nostr.onsats.org", read = true, write = true, feedTypes = activeTypes),
    //NewRelayListViewModel.Relay("wss://nostr.sandwich.farm", read = true, write = true, feedTypes = activeTypes),
    //NewRelayListViewModel.Relay("wss://relay.nostr.ch", read = true, write = true, feedTypes = activeTypes),
    //NewRelayListViewModel.Relay("wss://nostr.zebedee.cloud", read = true, write = true, feedTypes = activeTypes),
    //NewRelayListViewModel.Relay("wss://nostr.rocks", read = true, write = true, feedTypes = activeTypes),
    //NewRelayListViewModel.Relay("wss://nostr.fmt.wiz.biz", read = true, write = true, feedTypes = activeTypes),

    // Paid relays
    NewRelayListViewModel.Relay("wss://relay.nostr.com.au", read = true, write = false, feedTypes = activeTypesGlobal),
    NewRelayListViewModel.Relay("wss://eden.nostr.land", read = true, write = false, feedTypes = activeTypesGlobal),
    NewRelayListViewModel.Relay("wss://nostr.milou.lol", read = true, write = false, feedTypes = activeTypesGlobal),
    NewRelayListViewModel.Relay("wss://puravida.nostr.land", read = true, write = false, feedTypes = activeTypesGlobal),
    NewRelayListViewModel.Relay("wss://nostr.wine", read = true, write = false, feedTypes = activeTypesGlobal),
    NewRelayListViewModel.Relay("wss://nostr.inosta.cc", read = true, write = false, feedTypes = activeTypesGlobal),
    NewRelayListViewModel.Relay("wss://nostr-pub.semisol.dev", read = true, write = false, feedTypes = activeTypesGlobal),
    NewRelayListViewModel.Relay("wss://relay.orangepill.dev", read = true, write = false, feedTypes = activeTypesGlobal),
    NewRelayListViewModel.Relay("wss://relay.nostrati.com", read = true, write = false, feedTypes = activeTypesGlobal),
  )
}