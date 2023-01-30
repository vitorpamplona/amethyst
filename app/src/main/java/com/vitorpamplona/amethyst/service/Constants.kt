package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.relays.Relay

object Constants {
    val defaultRelays = arrayOf(
      Relay("wss://nostr.bitcoiner.social", read = true, write = true),
      Relay("wss://relay.nostr.bg", read = true, write = true),
      //Relay("wss://brb.io", read = true, write = true),
      Relay("wss://relay.snort.social", read = true, write = true),
      Relay("wss://nostr.rocks", read = true, write = true),
      Relay("wss://relay.damus.io", read = true, write = true),
      Relay("wss://nostr.fmt.wiz.biz", read = true, write = true),
      Relay("wss://nostr.oxtr.dev", read = true, write = true),
      Relay("wss://eden.nostr.land", read = true, write = true),
      //Relay("wss://nostr-2.zebedee.cloud", read = true, write = true),
      Relay("wss://nostr-pub.wellorder.net", read = true, write = true),
      Relay("wss://nostr.mom", read = true, write = true),
      Relay("wss://nostr.orangepill.dev", read = true, write = true),
      Relay("wss://nostr-pub.semisol.dev", read = true, write = true),
      Relay("wss://nostr.onsats.org", read = true, write = true),
      Relay("wss://nostr.sandwich.farm", read = true, write = true),
      Relay("wss://nostr.swiss-enigma.ch", read = true, write = true),
      Relay("wss://relay.nostr.ch", read = true, write = true)
    )
}
